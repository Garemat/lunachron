package io.github.garemat.lunachron

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "NearbyManager"
private const val NSD_SERVICE_TYPE = "_moonstone._tcp."

/** Port used by Wi-Fi Direct hosts for the TCP server. */
private const val WIFI_DIRECT_PORT = 8766

enum class HostMode {
    /** Both devices on the same Wi-Fi router. Uses NSD (mDNS) for discovery. */
    WIFI_NSD,
    /** No router required. Uses Wi-Fi Direct (P2P) to form a local group. */
    WIFI_DIRECT
}

/**
 * Handles peer discovery and messaging for multiplayer sessions.
 *
 * - Host chooses [HostMode]: NSD (same Wi-Fi router) or Wi-Fi Direct (no router needed).
 * - Clients always discover both types simultaneously so they can join either.
 * - After discovery, all communication is plain TCP + newline-framed JSON.
 */
class NearbyManager(private val context: Context) {

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val wifiP2pChannel: WifiP2pManager.Channel? =
        wifiP2pManager.initialize(context, context.mainLooper, null)

    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints = _connectedEndpoints.asStateFlow()

    private val _discoveredEndpoints = MutableStateFlow<Map<String, String>>(emptyMap())
    val discoveredEndpoints = _discoveredEndpoints.asStateFlow()

    private var onPayloadReceived: ((String, String) -> Unit)? = null
    private var onConnectionEstablished: ((String) -> Unit)? = null

    private val connections = ConcurrentHashMap<String, P2pConnection>()

    // Shared host fields (used by both modes)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    // NSD host
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null

    // NSD client
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private val nsdDiscoveredHosts = ConcurrentHashMap<String, Pair<String, Int>>()

    // Wi-Fi Direct
    private val wifiDirectDevices = ConcurrentHashMap<String, WifiP2pDevice>()
    private var wifiDirectReceiver: BroadcastReceiver? = null
    private var pendingWifiDirectEndpointId: String? = null

    fun setPayloadListener(listener: (String, String) -> Unit) { onPayloadReceived = listener }
    fun setConnectionListener(listener: (String) -> Unit) { onConnectionEstablished = listener }

    // ── Host ─────────────────────────────────────────────────────────────────

    fun startAdvertising(localName: String, mode: HostMode = HostMode.WIFI_NSD) {
        stopAll()
        when (mode) {
            HostMode.WIFI_NSD -> startNsdHost(localName)
            HostMode.WIFI_DIRECT -> startWifiDirectHost(localName)
        }
    }

    private fun startNsdHost(localName: String) {
        val ss = ServerSocket(0)
        serverSocket = ss
        val port = ss.localPort
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localName
            serviceType = NSD_SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD registered: ${info.serviceName} on port $port")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "NSD registration failed: $code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        nsdRegistrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        startAccepting(ss)
    }

    @SuppressLint("MissingPermission")
    private fun startWifiDirectHost(localName: String) {
        val ch = wifiP2pChannel ?: run {
            Log.e(TAG, "Wi-Fi Direct not supported on this device"); return
        }
        val ss = try {
            ServerSocket(WIFI_DIRECT_PORT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind Wi-Fi Direct port $WIFI_DIRECT_PORT", e); return
        }
        serverSocket = ss

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            localName, "_moonstone._tcp.", mapOf("port" to WIFI_DIRECT_PORT.toString())
        )
        wifiP2pManager.addLocalService(ch, serviceInfo, noopListener("addLocalService"))
        wifiP2pManager.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Wi-Fi Direct group created, listening on port $WIFI_DIRECT_PORT")
                startAccepting(ss)
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Wi-Fi Direct createGroup failed: $reason")
                ss.close(); serverSocket = null
            }
        })
    }

    private fun startAccepting(ss: ServerSocket) {
        acceptJob = scope.launch {
            while (isActive) {
                try {
                    val socket = ss.accept()
                    openConnection(UUID.randomUUID().toString(), socket)
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Accept loop error", e)
                    break
                }
            }
        }
    }

    // ── Client discovery ──────────────────────────────────────────────────────

    fun startDiscovery() {
        stopNsdDiscovery()
        stopWifiDirectDiscovery()
        _discoveredEndpoints.value = emptyMap()
        nsdDiscoveredHosts.clear()
        wifiDirectDevices.clear()
        startNsdDiscovery()
        startWifiDirectDiscovery()
    }

    private fun startNsdDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                Log.e(TAG, "NSD discovery start failed: $code")
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                        Log.w(TAG, "NSD resolve failed: $code")
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val addr = info.host ?: return
                        if (addr !is Inet4Address) {
                            Log.w(TAG, "NSD skipping non-IPv4: ${addr.hostAddress}"); return
                        }
                        val host = addr.hostAddress ?: return
                        val endpointId = "nsd_${UUID.randomUUID()}"
                        nsdDiscoveredHosts[endpointId] = host to info.port
                        _discoveredEndpoints.update { it + (endpointId to info.serviceName) }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val entry = _discoveredEndpoints.value.entries
                    .find { it.key.startsWith("nsd_") && it.value == serviceInfo.serviceName }
                if (entry != null) {
                    _discoveredEndpoints.update { it - entry.key }
                    nsdDiscoveredHosts.remove(entry.key)
                }
            }
        }
        nsdDiscoveryListener = listener
        nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @SuppressLint("MissingPermission")
    private fun startWifiDirectDiscovery() {
        val ch = wifiP2pChannel ?: return

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
                val targetId = pendingWifiDirectEndpointId ?: return
                wifiP2pManager.requestConnectionInfo(ch) { info ->
                    Log.d(TAG, "Wi-Fi Direct conn info: groupFormed=${info?.groupFormed}, isGroupOwner=${info?.isGroupOwner}")
                    if (info?.groupFormed == true && !info.isGroupOwner) {
                        val goAddress = info.groupOwnerAddress?.hostAddress ?: return@requestConnectionInfo
                        pendingWifiDirectEndpointId = null
                        scope.launch {
                            try {
                                val socket = Socket()
                                socket.connect(InetSocketAddress(goAddress, WIFI_DIRECT_PORT), 10_000)
                                openConnection(targetId, socket)
                            } catch (e: Exception) {
                                Log.e(TAG, "Wi-Fi Direct TCP connect failed to $goAddress", e)
                            }
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        wifiDirectReceiver = receiver

        wifiP2pManager.setDnsSdResponseListeners(ch,
            { instanceName, _, device ->
                val endpointId = "p2p_${device.deviceAddress}"
                wifiDirectDevices[endpointId] = device
                _discoveredEndpoints.update { it + (endpointId to instanceName) }
            },
            { _, _, _ -> }
        )
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        wifiP2pManager.addServiceRequest(ch, serviceRequest, noopListener("addServiceRequest"))
        wifiP2pManager.discoverServices(ch, noopListener("p2p discoverServices"))
    }

    // ── Client connection ─────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun requestConnection(localName: String, endpointId: String) {
        when {
            endpointId.startsWith("nsd_") -> {
                val (host, port) = nsdDiscoveredHosts[endpointId] ?: run {
                    Log.e(TAG, "Unknown NSD endpointId: $endpointId"); return
                }
                scope.launch {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(host, port), 10_000)
                        openConnection(endpointId, socket)
                    } catch (e: Exception) {
                        Log.e(TAG, "NSD TCP connect failed to $host:$port", e)
                    }
                }
            }
            endpointId.startsWith("p2p_") -> {
                val ch = wifiP2pChannel ?: run {
                    Log.e(TAG, "Wi-Fi Direct not supported"); return
                }
                val device = wifiDirectDevices[endpointId] ?: run {
                    Log.e(TAG, "Unknown Wi-Fi Direct endpointId: $endpointId"); return
                }
                pendingWifiDirectEndpointId = endpointId
                val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
                wifiP2pManager.connect(ch, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(reason: Int) {
                        Log.e(TAG, "Wi-Fi Direct connect failed: $reason")
                        pendingWifiDirectEndpointId = null
                    }
                })
                // TCP connection completes in the BroadcastReceiver on WIFI_P2P_CONNECTION_CHANGED_ACTION
            }
        }
    }

    // ── Messaging ─────────────────────────────────────────────────────────────

    fun sendPayload(endpointId: String, message: String) {
        connections[endpointId]?.send(message)
    }

    fun sendPayloadToAll(message: String) {
        connections.values.forEach { it.send(message) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun stopAll() {
        stopNsdDiscovery()
        stopWifiDirectDiscovery()
        stopNsdAdvertising()
        stopWifiDirectAdvertising()
        connections.values.forEach { it.close() }
        connections.clear()
        _connectedEndpoints.value = emptySet()
        _discoveredEndpoints.value = emptyMap()
        nsdDiscoveredHosts.clear()
        wifiDirectDevices.clear()
        pendingWifiDirectEndpointId = null
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private fun stopNsdAdvertising() {
        acceptJob?.cancel(); acceptJob = null
        nsdRegistrationListener?.let { try { nsdManager.unregisterService(it) } catch (_: Exception) {} }
        nsdRegistrationListener = null
        serverSocket?.close(); serverSocket = null
    }

    private fun stopNsdDiscovery() {
        nsdDiscoveryListener?.let { try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {} }
        nsdDiscoveryListener = null
    }

    @SuppressLint("MissingPermission")
    private fun stopWifiDirectAdvertising() {
        val ch = wifiP2pChannel ?: return
        wifiP2pManager.clearLocalServices(ch, noopListener("clearLocalServices"))
        wifiP2pManager.removeGroup(ch, noopListener("removeGroup"))
    }

    private fun stopWifiDirectDiscovery() {
        wifiDirectReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        wifiDirectReceiver = null
        val ch = wifiP2pChannel ?: return
        wifiP2pManager.clearServiceRequests(ch, noopListener("clearServiceRequests"))
        wifiP2pManager.stopPeerDiscovery(ch, noopListener("stopPeerDiscovery"))
    }

    private fun openConnection(endpointId: String, socket: Socket) {
        val conn = P2pConnection(endpointId, socket, scope)
        conn.onDisconnect = {
            _connectedEndpoints.update { it - endpointId }
            connections.remove(endpointId)
        }
        connections[endpointId] = conn
        conn.start { id, msg -> onPayloadReceived?.invoke(id, msg) }
        _connectedEndpoints.update { it + endpointId }
        onConnectionEstablished?.invoke(endpointId)
    }

    private fun noopListener(tag: String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {}
        override fun onFailure(reason: Int) { Log.w(TAG, "$tag failed: $reason") }
    }
}

private class P2pConnection(
    val endpointId: String,
    private val socket: Socket,
    private val scope: CoroutineScope
) {
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private val sendChannel = Channel<String>(Channel.UNLIMITED)

    var onDisconnect: (() -> Unit)? = null
    private var writeJob: Job? = null
    private var readJob: Job? = null

    fun start(onMessage: (String, String) -> Unit) {
        writeJob = scope.launch {
            try {
                for (msg in sendChannel) {
                    writer.write(msg); writer.newLine(); writer.flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Write error on $endpointId", e)
            }
        }
        readJob = scope.launch {
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    onMessage(endpointId, line)
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "Read error on $endpointId", e)
            } finally {
                close()
                onDisconnect?.invoke()
            }
        }
    }

    fun send(message: String) { sendChannel.trySend(message) }

    fun close() {
        sendChannel.close()
        readJob?.cancel()
        writeJob?.cancel()
        try { socket.close() } catch (_: Exception) {}
    }
}
