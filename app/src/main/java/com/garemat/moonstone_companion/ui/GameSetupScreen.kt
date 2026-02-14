package com.garemat.moonstone_companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppTheme

enum class SetupMode {
    LOCAL, MULTIPLAYER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSetupScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit,
    onStartGame: () -> Unit,
    onNavigateToAddEditTroupe: () -> Unit,
    currentTutorialStep: TutorialStep? = null,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val showScannerDialogForPlayer = remember { mutableStateOf<Int?>(null) }
    var showDiscoveryDialog by remember { mutableStateOf(false) }

    // Navigation state within Setup
    val setupMode = remember { mutableStateOf<SetupMode?>(null) }

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

    val session = state.gameSession
    val activeSession = session
    val discoveredEndpoints by viewModel.discoveredEndpoints.collectAsState()
    val context = LocalContext.current

    val nearbyPermissions = remember {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
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
                if (state.activeTroupes.isNotEmpty()) {
                    GameInProgressContent(
                        isMultiplayer = state.gameSession != null,
                        onContinue = onStartGame,
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
                                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) showScannerDialogForPlayer.value = index
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
                                onHostSelected = {
                                    checkAndRunNearbyAction {
                                        viewModel.startHosting(state.name.ifEmpty { "Host" })
                                    }
                                },
                                onJoinSelected = {
                                    checkAndRunNearbyAction {
                                        viewModel.startDiscovering()
                                        showDiscoveryDialog = true
                                    }
                                },
                                onTargetPositioned = onTargetPositioned
                            )
                        }
                    }
                }
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
                                                viewModel.connectToHost(id, state.name.ifEmpty { "Player" })
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

            if (showScannerDialogForPlayer.value != null) {
                val playerIndex = showScannerDialogForPlayer.value!!
                Dialog(onDismissRequest = { showScannerDialogForPlayer.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        QrScanner(onResult = { result ->
                            val importedTroupe = viewModel.importTroupe(result, state.characters)
                            if (importedTroupe != null) {
                                viewModel.onTroupeScanned(playerIndex, importedTroupe)
                                showScannerDialogForPlayer.value = null
                            }
                        })
                        IconButton(onClick = { showScannerDialogForPlayer.value = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
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
                            viewModel.onEvent(CharacterEvent.AbandonGame)
                            showAbandonConfirmation = false
                            setupMode.value = null // Reset setup mode on abandon
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
    }
}

@Composable
fun SetupModeSelection(
    onLocalSelected: () -> Unit,
    onHostSelected: () -> Unit,
    onJoinSelected: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Game Mode",
            style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp) else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SetupOptionCard(
            title = "Local Game",
            description = "Play on a single device with 2-4 players.",
            icon = Icons.Default.Smartphone,
            backgroundType = "commonwealth",
            onClick = onLocalSelected,
            modifier = Modifier.onGloballyPositioned { onTargetPositioned("LocalGameOption", it) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SetupOptionCard(
            title = "Host Game",
            description = "Start a multiplayer session for others to join.",
            icon = Icons.Default.WifiTethering,
            backgroundType = "dominion",
            onClick = onHostSelected
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SetupOptionCard(
            title = "Join Game",
            description = "Connect to an existing multiplayer session nearby.",
            icon = Icons.Default.Wifi,
            backgroundType = "leshavult",
            onClick = onJoinSelected
        )
    }
}

@Composable
fun SetupOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundType: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    val context = LocalContext.current
    val backgroundRes = remember(backgroundType) { context.resources.getIdentifier(backgroundType, "drawable", context.packageName) }
    
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(100.dp),
        shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMoonstone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isMoonstone) 2.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isMoonstone && backgroundRes != 0) {
                Image(
                    painter = painterResource(id = backgroundRes),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().alpha(0.1f),
                    contentScale = ContentScale.Crop
                )
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title, 
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold,
                        color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
                    )
                    Text(
                        text = description, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GameInProgressContent(
    isMultiplayer: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (isMultiplayer) "Multiplayer Game in Progress" else "Game in Progress",
            style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp) else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = if (isMultiplayer) 
                "You have an active session. Would you like to rejoin it or start fresh?" 
                else "A local game is currently active. You can continue where you left off or start a new game.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isMultiplayer) "REJOIN SESSION" else "CONTINUE GAME", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("START NEW GAME", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun OfflineSetupUI(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onStartGame: () -> Unit,
    onScanRequest: (Int) -> Unit,
    onNavigateToAddEditTroupe: () -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    var playerCount by remember { mutableIntStateOf(2) }
    val selectedTroupes = remember { mutableStateListOf<Troupe?>(null, null, null, null) }
    var troupeToPrune by remember { mutableStateOf<Pair<Int, Troupe>?>(null) }
    var troupeToSaveWithName by remember { mutableStateOf<Troupe?>(null) }
    var customTroupeName by remember { mutableStateOf("") }
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    
    LaunchedEffect(Unit) {
        viewModel.scannedTroupeEvent.collect { (index, troupe) ->
            if (index < selectedTroupes.size) {
                selectedTroupes[index] = troupe
            }
        }
    }

    LaunchedEffect(playerCount) {
        viewModel.uiEvent.collect { event ->
            if (event is CharacterViewModel.UiEvent.TroupeCreated) {
                val index = event.playerIndex
                if (index != null && index >= 0 && index < 4) {
                    val maxAllowed = when(playerCount) {
                        3 -> 4
                        4 -> 3
                        else -> 6
                    }
                    if (event.troupe.autoSelectMembers && event.troupe.characterIds.size <= maxAllowed) {
                        selectedTroupes[index] = event.troupe
                    } else {
                        troupeToPrune = index to event.troupe
                    }
                }
            }
        }
    }

    var showQrForTroupe by remember { mutableStateOf<Troupe?>(null) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .onGloballyPositioned { onPositioned("PlayerCount", it) }
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Players:", 
                style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp) else MaterialTheme.typography.titleMedium,
                color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Row {
                listOf(2, 3, 4).forEach { count ->
                    FilterChip(
                        selected = playerCount == count,
                        onClick = { 
                            playerCount = count
                            for (i in count until 4) selectedTroupes[i] = null
                        },
                        label = { Text(count.toString()) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        shape = if (isMoonstone) RoundedCornerShape(0.dp) else FilterChipDefaults.shape
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(playerCount) { index ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            val name = if (index == 0) state.name.ifEmpty { "Player 1" } else "Player ${index + 1}"
                            Text(name, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            
                            val troupe = selectedTroupes[index]
                            
                            val showSave = troupe != null && state.troupes.none { it.shareCode == troupe.shareCode && troupe.shareCode.isNotEmpty() }
                            
                            if (showSave) {
                                IconButton(
                                    onClick = { 
                                        troupe?.let {
                                            troupeToSaveWithName = it
                                            customTroupeName = it.troupeName
                                        }
                                    },
                                    modifier = Modifier.onGloballyPositioned { if (index == 0) onPositioned("SaveTroupeSetup", it) }
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save Troupe")
                                }
                            }
                            
                            if (troupe != null) {
                                IconButton(
                                    onClick = { showQrForTroupe = troupe },
                                    modifier = Modifier.onGloballyPositioned { if (index == 0) onPositioned("QrCodeDisplayButton", it) }
                                ) {
                                    Icon(Icons.Default.QrCode, contentDescription = "Show QR")
                                }
                            }

                            IconButton(
                                onClick = { onScanRequest(index) },
                                modifier = Modifier.onGloballyPositioned { if (index == 0) onPositioned("ScanQR", it) }
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                            }
                        }

                        val displayTroupes = state.troupes

                        TroupeSelector(
                            troupes = displayTroupes,
                            selectedTroupe = selectedTroupes[index],
                            allCharacters = state.characters,
                            onTroupeSelected = { t ->
                                val maxAllowed = when(playerCount) {
                                    3 -> 4
                                    4 -> 3
                                    else -> 6
                                }
                                // Only show pruning dialog if AutoSelect is off OR troupe is over the player count limit
                                if (!t.autoSelectMembers || t.characterIds.size > maxAllowed) {
                                    troupeToPrune = index to t
                                } else {
                                    selectedTroupes[index] = t
                                }
                            },
                            onCreateNewTroupe = {
                                viewModel.editingTroupeId = null
                                viewModel.pendingTroupePlayerIndex = index
                                onNavigateToAddEditTroupe()
                            },
                            onEditTroupe = { t ->
                                viewModel.onEvent(CharacterEvent.EditTroupe(t))
                                viewModel.pendingTroupePlayerIndex = index
                                onNavigateToAddEditTroupe()
                            },
                            modifier = Modifier.onGloballyPositioned { if (index == 0) onPositioned("TroupeSelector", it) },
                            onPositioned = onPositioned
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val nonNullTroupes = selectedTroupes.take(playerCount).filterNotNull()
                if (nonNullTroupes.size == playerCount) {
                    viewModel.startNewGame(nonNullTroupes)
                    onStartGame()
                }
            },
            enabled = selectedTroupes.take(playerCount).all { it != null },
            modifier = Modifier.fillMaxWidth().height(56.dp).onGloballyPositioned { onPositioned("StartBattleButton", it) },
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
        ) {
            Text("BATTLE!", fontSize = if (isMoonstone) 20.sp else 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
    
    if (showQrForTroupe != null) {
        val shareCode = viewModel.generateFullShareCode(showQrForTroupe!!, state.characters)
        QrCodeDialog(
            troupeName = showQrForTroupe!!.troupeName,
            shareCode = shareCode,
            onDismiss = { showQrForTroupe = null }
        )
    }

    if (troupeToPrune != null) {
        val (index, troupe) = troupeToPrune!!
        val maxAllowed = when(playerCount) {
            3 -> 4
            4 -> 3
            else -> 6
        }
        TroupeSelectionDialog(
            troupe = troupe,
            maxSelection = maxAllowed,
            allCharacters = state.characters,
            onConfirmed = { selectedChars ->
                selectedTroupes[index] = troupe.copy(characterIds = selectedChars.map { it.id })
                troupeToPrune = null
            },
            onDismiss = { troupeToPrune = null }
        )
    }

    if (troupeToSaveWithName != null) {
        AlertDialog(
            onDismissRequest = { troupeToSaveWithName = null },
            title = { Text("Save Troupe") },
            text = {
                Column {
                    Text("Enter a name for this troupe:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customTroupeName,
                        onValueChange = { customTroupeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Troupe Name") },
                        singleLine = true,
                        shape = if (isMoonstone) RoundedCornerShape(0.dp) else OutlinedTextFieldDefaults.shape
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        troupeToSaveWithName?.let {
                            viewModel.saveTroupe(it.copy(troupeName = customTroupeName))
                        }
                        troupeToSaveWithName = null
                    },
                    enabled = customTroupeName.isNotBlank(),
                    shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { troupeToSaveWithName = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SessionSetupUI(
    state: CharacterState,
    viewModel: CharacterViewModel,
    session: GameSession,
    troupes: List<Troupe>,
    onSelectTroupe: (Troupe) -> Unit,
    onStartGame: () -> Unit,
    onNavigateToAddEditTroupe: () -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var troupeToPrune by remember { mutableStateOf<Troupe?>(null) }
    val playerCount = session.players.size
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE

    LaunchedEffect(playerCount) {
        viewModel.uiEvent.collect { event ->
            if (event is CharacterViewModel.UiEvent.TroupeCreated) {
                if (event.playerIndex == -1) { // Special marker for local session user
                    val maxAllowed = when(playerCount) {
                        3 -> 4
                        4 -> 3
                        else -> 6
                    }
                    if (event.troupe.autoSelectMembers && event.troupe.characterIds.size <= maxAllowed) {
                        onSelectTroupe(event.troupe)
                    } else {
                        troupeToPrune = event.troupe
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.leaveSession() }, modifier = Modifier.onGloballyPositioned { onPositioned("LeaveButton", it) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave Session")
            }
            Text(
                "Session ID: ${session.sessionId}", 
                style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp) else MaterialTheme.typography.titleSmall,
                color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified,
                modifier = Modifier.onGloballyPositioned { onPositioned("SessionId", it) }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text("${session.players.size}/4 Players", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(session.players.indices.toList()) { index ->
                val player = session.players[index]

                // Identify if this slot belongs to the local user
                val isActuallyLocal = player.deviceId == state.deviceId

                PlayerSlotCard(
                    player = player,
                    troupes = troupes,
                    allCharacters = state.characters,
                    isEditable = isActuallyLocal,
                    onSelectTroupe = { troupe ->
                        val maxAllowed = when(playerCount) {
                            3 -> 4
                            4 -> 3
                            else -> 6
                        }
                        // Skip pruning if AutoSelect is on AND it fits the limits
                        if (!troupe.autoSelectMembers || troupe.characterIds.size > maxAllowed) {
                            troupeToPrune = troupe
                        } else {
                            onSelectTroupe(troupe)
                        }
                    },
                    onCreateNewTroupe = {
                        viewModel.editingTroupeId = null
                        viewModel.pendingTroupePlayerIndex = -1 // Marker for local session user
                        onNavigateToAddEditTroupe()
                    },
                    onEditTroupe = { troupe ->
                        viewModel.onEvent(CharacterEvent.EditTroupe(troupe))
                        viewModel.pendingTroupePlayerIndex = -1
                        onNavigateToAddEditTroupe()
                    },
                    modifier = Modifier.onGloballyPositioned {
                        if (index == 0) onPositioned("FirstPlayerSlot", it)
                        if (index == 1) onPositioned("SecondPlayerSlot", it)
                    }
                )
            }
        }

        Button(
            onClick = onStartGame,
            enabled = (session.isHost && session.players.size >= 2 && session.players.all { it.troupe != null }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(56.dp)
                .onGloballyPositioned { onPositioned("StartBattleButton", it) },
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
        ) {
            Text(if (session.isHost) "START BATTLE" else "WAITING FOR HOST...", fontSize = if (isMoonstone) 18.sp else 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    if (troupeToPrune != null) {
        val maxAllowed = when(playerCount) {
            3 -> 4
            4 -> 3
            else -> 6
        }
        TroupeSelectionDialog(
            troupe = troupeToPrune!!,
            maxSelection = maxAllowed,
            allCharacters = state.characters,
            onConfirmed = { selectedChars ->
                onSelectTroupe(troupeToPrune!!.copy(characterIds = selectedChars.map { it.id }))
                troupeToPrune = null
            },
            onDismiss = { troupeToPrune = null }
        )
    }
}

@Composable
fun PlayerSlotCard(
    player: GamePlayer,
    troupes: List<Troupe>,
    allCharacters: List<Character>,
    isEditable: Boolean,
    onSelectTroupe: (Troupe) -> Unit,
    onCreateNewTroupe: () -> Unit,
    onEditTroupe: (Troupe) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(player.name, fontWeight = FontWeight.Bold)

                if (player.troupe != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(getFactionColor(player.troupe.faction)),
                        contentAlignment = Alignment.Center
                    ) {
                        FactionSymbol(
                            faction = player.troupe.faction,
                            modifier = Modifier.fillMaxSize(),
                            tint = Color.White
                        )
                    }
                }
            }

            if (isEditable) {
                TroupeSelector(
                    troupes = troupes,
                    selectedTroupe = player.troupe,
                    allCharacters = allCharacters,
                    onTroupeSelected = onSelectTroupe,
                    onCreateNewTroupe = onCreateNewTroupe,
                    onEditTroupe = onEditTroupe
                )
            } else if (player.troupe != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = player.troupe.troupeName,
                    style = if (isMoonstone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Show characters even if we can't edit
                val names = player.troupe.characterIds.mapNotNull { id ->
                    allCharacters.find { it.id == id }?.name
                }
                if (names.isNotEmpty()) {
                    Text(
                        text = names.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choosing troupe...", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun QrCodeDialog(
    troupeName: String,
    shareCode: String,
    onDismiss: () -> Unit
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    troupeName, 
                    style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.headlineSmall, 
                    textAlign = TextAlign.Center,
                    color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                Spacer(modifier = Modifier.height(16.dp))

                val bitmap = BarcodeUtils.generateQrCode(shareCode)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(250.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun TroupeSelector(
    troupes: List<Troupe>,
    selectedTroupe: Troupe?,
    allCharacters: List<Character>,
    onTroupeSelected: (Troupe) -> Unit,
    onCreateNewTroupe: () -> Unit,
    onEditTroupe: (Troupe) -> Unit,
    modifier: Modifier = Modifier,
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE

    Box(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedTroupe?.troupeName ?: "Select a troupe",
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 18.sp) else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (selectedTroupe == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )

                    if (selectedTroupe != null) {
                        IconButton(
                            onClick = { onEditTroupe(selectedTroupe) },
                            modifier = Modifier.size(24.dp).onGloballyPositioned {
                                onPositioned("EditTroupeButton", it)
                            }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Troupe",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }

                if (selectedTroupe != null) {
                    val names = selectedTroupe.characterIds.mapNotNull { id ->
                        allCharacters.find { it.id == id }?.name
                    }
                    if (names.isNotEmpty()) {
                        Text(
                            text = names.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = if (isMoonstone) RoundedCornerShape(0.dp) else MenuDefaults.shape,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DropdownMenuItem(
                text = { 
                    Text(
                        "Create New Troupe", 
                        color = MaterialTheme.colorScheme.primary,
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 16.sp) else MaterialTheme.typography.labelLarge
                    ) 
                },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onCreateNewTroupe()
                    expanded = false
                },
                modifier = Modifier.onGloballyPositioned {
                    onPositioned("CreateNewTroupe", it)
                }
            )
            if (troupes.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            troupes.forEach { troupe ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            troupe.troupeName,
                            style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 16.sp) else MaterialTheme.typography.bodyLarge
                        ) 
                    },
                    onClick = {
                        onTroupeSelected(troupe)
                        expanded = false
                    },
                    modifier = Modifier.onGloballyPositioned {
                        if (troupe.troupeName == "Example Troupe Name") {
                            onPositioned("ExampleTroupeItem", it)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TroupeSelectionDialog(
    troupe: Troupe,
    maxSelection: Int,
    allCharacters: List<Character>,
    onConfirmed: (List<Character>) -> Unit,
    onDismiss: () -> Unit
) {
    val troupeCharacters = remember(troupe) {
        troupe.characterIds.mapNotNull { id -> allCharacters.find { it.id == id } }
    }
    val selectedCharacters = remember { mutableStateListOf<Character>() }
    var expandedCharacterId by remember { mutableStateOf<Int?>(null) }
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().padding(if (isMoonstone) 0.dp else 16.dp),
        shape = if (isMoonstone) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp),
        title = {
            Column {
                Text(
                    "Select Your Team", 
                    style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.headlineSmall,
                    color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                Text(
                    "Select up to $maxSelection characters for this game.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(troupeCharacters) { character ->
                    val isSelected = selectedCharacters.contains(character)
                    val isExpanded = expandedCharacterId == character.id

                    SelectionCharacterCard(
                        character = character,
                        isSelected = isSelected,
                        isExpanded = isExpanded,
                        onToggleSelect = {
                            if (isSelected) {
                                selectedCharacters.remove(character)
                            } else if (selectedCharacters.size < maxSelection) {
                                selectedCharacters.add(character)
                            }
                        },
                        onExpandClick = {
                            expandedCharacterId = if (isExpanded) null else character.id
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmed(selectedCharacters.toList()) },
                enabled = selectedCharacters.isNotEmpty() && selectedCharacters.size <= maxSelection,
                shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
            ) {
                Text("Confirm (${selectedCharacters.size}/$maxSelection)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SelectionCharacterCard(
    character: Character,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleSelect: () -> Unit,
    onExpandClick: () -> Unit
) {
    var isFlipped by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    val imageRes = remember(character.imageName) {
        if (character.imageName != null) {
            val cleanName = character.imageName.substringBeforeLast(".")
            context.resources.getIdentifier(cleanName, "drawable", context.packageName)
        } else 0
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onExpandClick() }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageRes != 0) {
                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = character.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(text = character.name.take(1), style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = character.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = character.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }

                IconButton(onClick = onExpandClick) {
                    Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Box(modifier = Modifier.padding(16.dp)) {
                    if (!isFlipped) {
                        CharacterFront(character = character, searchQuery = "", onFlip = { isFlipped = true })
                    } else {
                        CharacterBack(character = character, searchQuery = "", onFlip = { isFlipped = false })
                    }
                }
            }
        }
    }
}
