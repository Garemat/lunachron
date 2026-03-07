package com.garemat.moonstone_companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.garemat.moonstone_companion.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SetupMode {
    LOCAL, MULTIPLAYER, TOURNAMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSetupScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit,
    onStartGame: () -> Unit,
    onNavigateToAddEditTroupe: () -> Unit,
    onJoinTournament: () -> Unit,
    currentTutorialStep: TutorialStep? = null,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var showScannerDialogForPlayer by remember { mutableStateOf<Int?>(null) }
    var showDiscoveryDialog by remember { mutableStateOf(false) }
    var showTournamentDiscoveryDialog by remember { mutableStateOf(false) }
    var showHostModeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Navigation state within Setup
    val setupMode = remember { mutableStateOf<SetupMode?>(if (state.useLocalModeByDefault) SetupMode.LOCAL else null) }
    var minimizeSession by rememberSaveable { mutableStateOf(false) }

    // Tutorial interaction logic
    LaunchedEffect(currentTutorialStep) {
        val target = currentTutorialStep?.targetName
        if (target in listOf("PlayerCount", "TroupeSelector", "StartBattleButton")) {
            setupMode.value = SetupMode.LOCAL
        } else if (target == "LocalGameOption") {
            setupMode.value = null
        }
    }

    // Logic for existing games confirmation
    var showAbandonConfirmation by remember { mutableStateOf(false) }
    var showLeaveTournamentConfirmation by remember { mutableStateOf(false) }

    val session = state.gameSession
    val activeSession = session
    val discoveredEndpoints by viewModel.discoveredEndpoints.collectAsState()
    val context = LocalContext.current

    // Reset minimizeSession if session becomes null
    LaunchedEffect(activeSession) {
        if (activeSession == null) minimizeSession = false
    }

    // Handle system back button for active sessions
    BackHandler(enabled = activeSession != null && !minimizeSession) {
        minimizeSession = true
    }

    val nearbyPermissions = remember {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.toTypedArray()
    }

    var pendingNearbyAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
        if (permissions.values.all { it }) {
            pendingNearbyAction?.invoke()
            pendingNearbyAction = null
        } else {
            Toast.makeText(context, "Nearby permissions are required for multiplayer", Toast.LENGTH_LONG).show()
            pendingNearbyAction = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndRunNearbyAction(action: () -> Unit) {
        if (nearbyPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            action()
        } else {
            pendingNearbyAction = action
            nearbyPermissionLauncher.launch(nearbyPermissions)
        }
    }

    LaunchedEffect(viewModel.uiEvent) {
        viewModel.uiEvent.collect { event ->
            if (event is CharacterViewModel.UiEvent.GameStarted) {
                onStartGame()
            } else if (event is CharacterViewModel.UiEvent.TournamentJoined) {
                showTournamentDiscoveryDialog = false
                onJoinTournament()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // TopBar title and navigation is handled by MainActivity
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                if (state.tournamentSettings != null) {
                    ActiveTournamentCard(
                        tournamentName = state.tournamentSettings.tournamentName,
                        onJoin = onJoinTournament,
                        onLeave = { showLeaveTournamentConfirmation = true }
                    )
                } else if (state.activeTroupes.isNotEmpty() || (activeSession != null && minimizeSession)) {
                    GameInProgressContent(
                        isMultiplayer = state.gameSession != null,
                        onContinue = {
                            if (state.activeTroupes.isNotEmpty()) {
                                onStartGame()
                            } else {
                                minimizeSession = false
                            }
                        },
                        onNewGame = { showAbandonConfirmation = true }
                    )
                } else if (activeSession != null) {
                    SessionSetupUI(
                        state = state,
                        viewModel = viewModel,
                        session = activeSession,
                        troupes = state.troupes,
                        onSelectTroupe = { troupe -> viewModel.broadcastTroupeSelection(troupe) },
                        onStartGame = { viewModel.broadcastStartGame() },
                        onLeave = { minimizeSession = true },
                        onNavigateToAddEditTroupe = onNavigateToAddEditTroupe,
                        onPositioned = onTargetPositioned
                    )
                } else {
                    when (setupMode.value) {
                        SetupMode.LOCAL -> {
                            OfflineSetupUI(
                                state = state,
                                viewModel = viewModel,
                                onStartGame = onStartGame,
                                onScanRequest = { index ->
                                    val permission = Manifest.permission.CAMERA
                                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) showScannerDialogForPlayer = index
                                    else cameraPermissionLauncher.launch(permission)
                                },
                                onNavigateToAddEditTroupe = onNavigateToAddEditTroupe,
                                onPositioned = onTargetPositioned,
                                onBack = { setupMode.value = null }
                            )
                        }
                        else -> {
                            SetupModeSelection(
                                onLocalSelected = { setupMode.value = SetupMode.LOCAL },
                                onHostSelected = { showHostModeDialog = true },
                                onJoinSelected = {
                                    checkAndRunNearbyAction {
                                        viewModel.startDiscovering()
                                        showDiscoveryDialog = true
                                    }
                                },
                                onJoinTournamentSelected = {
                                    checkAndRunNearbyAction {
                                        viewModel.startDiscovering()
                                        showTournamentDiscoveryDialog = true
                                    }
                                },
                                onTargetPositioned = onTargetPositioned
                            )
                        }
                    }
                }
            }

            if (showHostModeDialog) {
                HostModeDialog(
                    onSelectMode = { mode ->
                        showHostModeDialog = false
                        if (mode == com.garemat.moonstone_companion.HostMode.WIFI_DIRECT) {
                            checkAndRunNearbyAction {
                                viewModel.startHosting(state.name.ifEmpty { "Host" }, mode)
                            }
                        } else {
                            viewModel.startHosting(state.name.ifEmpty { "Host" }, mode)
                        }
                    },
                    onDismiss = { showHostModeDialog = false }
                )
            }

            if (showDiscoveryDialog && activeSession == null) {
                AlertDialog(
                    onDismissRequest = { showDiscoveryDialog = false; viewModel.leaveSession() },
                    title = { Text("Available Games") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            if (discoveredEndpoints.isEmpty()) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                                Text("Searching for nearby hosts...", modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                            } else {
                                LazyColumn {
                                    items(discoveredEndpoints.toList()) { (id, name) ->
                                        ListItem(
                                            headlineContent = { Text(name) },
                                            supportingContent = { Text("Tap to join session") },
                                            leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null) },
                                            modifier = Modifier.clickable {
                                                viewModel.requestTournamentJoin(id, "") // Normal join doesn't need passcode
                                                showDiscoveryDialog = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showDiscoveryDialog = false; viewModel.leaveSession() }) { Text("Cancel") }
                    }
                )
            }

            if (showTournamentDiscoveryDialog) {
                TournamentDiscoveryDialog(
                    discoveredEndpoints = discoveredEndpoints,
                    onJoinRequest = { id, passcode -> viewModel.requestTournamentJoin(id, passcode) },
                    onDismiss = { showTournamentDiscoveryDialog = false },
                    onLeaveSession = { viewModel.leaveSession() }
                )
            }

            if (showScannerDialogForPlayer != null) {
                val playerIndex = showScannerDialogForPlayer!!
                androidx.compose.ui.window.Dialog(onDismissRequest = { showScannerDialogForPlayer = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        QrScanner(onResult = { result ->
                            val importedTroupe = viewModel.importTroupe(result, state.characters)
                            if (importedTroupe != null) {
                                viewModel.onTroupeScanned(playerIndex, importedTroupe)
                                showScannerDialogForPlayer = null
                            }
                        })
                        IconButton(onClick = { showScannerDialogForPlayer = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (showAbandonConfirmation) {
            val isMultiplayer = state.gameSession != null
            val isHost = state.gameSession?.isHost == true
            
            AlertDialog(
                onDismissRequest = { showAbandonConfirmation = false },
                title = { Text("Are you sure?") },
                text = {
                    Text(when {
                        !isMultiplayer -> "This will delete the current game state and any tracked Moonstones."
                        isHost -> "Are you sure? This will close the game for all players."
                        else -> "Are you sure? You won't be able to rejoin a game in progress if you continue."
                    })
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                if (isMultiplayer) {
                                    viewModel.leaveSession()
                                    delay(300)
                                } else {
                                    viewModel.onEvent(CharacterEvent.AbandonGame)
                                }
                                showAbandonConfirmation = false
                                setupMode.value = null // Reset setup mode on abandon
                                minimizeSession = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAbandonConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showLeaveTournamentConfirmation) {
            AlertDialog(
                onDismissRequest = { showLeaveTournamentConfirmation = false },
                title = { Text("Leave Tournament?") },
                text = { Text("Are you sure you want to leave this tournament session?") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.leaveSession()
                                delay(300)
                                setupMode.value = null
                                showLeaveTournamentConfirmation = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Leave")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveTournamentConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
