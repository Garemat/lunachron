package com.garemat.moonstone_companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentWaitingRoomScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit,
    onSelectTroupe: () -> Unit,
    onSelectTroupeForManual: (String) -> Unit = {},
    onBeginTournament: () -> Unit,
    onRoundStarted: () -> Unit
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    val settings = state.tournamentSettings ?: return
    var validationError by remember { mutableStateOf<String?>(null) }
    
    var showAddManualPlayerDialog by remember { mutableStateOf(false) }
    var manualPlayerName by remember { mutableStateOf("") }
    
    var playerToEditName by remember { mutableStateOf<Pair<String, String>?>(null) } // deviceId to currentName
    var newManualName by remember { mutableStateOf("") }

    LaunchedEffect(state.currentTournamentRound) {
        if (state.currentTournamentRound != null) {
            onRoundStarted()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        settings.tournamentName.ifEmpty { "Tournament Waiting Room" },
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Tournament Info Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = if (isMoonstone) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EVENT PIN",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = settings.passcode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )

                    Text(
                        text = "Format: ${if (settings.troupeSize == TroupeSizeSetting.V6_10) "6v6 (10 Characters)" else "5v5 (8 Characters)"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Round Time: ${settings.roundTimerMinutes} Minutes",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Player List Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Joined Players (${state.tournamentPlayers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state.isTournamentHost) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAddManualPlayerDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Manual")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.tournamentPlayers) { player ->
                    TournamentPlayerItem(
                        player = player, 
                        isLocal = player.deviceId == state.deviceId, 
                        isMoonstone = isMoonstone,
                        isHost = state.isTournamentHost,
                        onEditName = { id, name -> 
                            playerToEditName = id to name
                            newManualName = name
                        },
                        onChangeTroupe = { onSelectTroupeForManual(player.deviceId) },
                        onRemove = { viewModel.removeTournamentPlayer(player.deviceId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Section
            val localPlayer = state.tournamentPlayers.find { it.deviceId == state.deviceId }
            
            if (localPlayer != null) {
                val hasSelectedTroupe = localPlayer.troupe != null
                val isReady = localPlayer.isReady

                if (!hasSelectedTroupe) {
                    Text(
                        "Select a troupe that matches the tournament format to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSelectTroupe,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = if (isMoonstone) RoundedCornerShape(0.dp) else ButtonDefaults.shape,
                        enabled = !isReady
                    ) {
                        Text(if (hasSelectedTroupe) "Change Troupe" else "Select Troupe")
                    }

                    if (hasSelectedTroupe) {
                        Button(
                            onClick = { 
                                if (!isReady) {
                                    val requiredSize = if (settings.troupeSize == TroupeSizeSetting.V6_10) 10 else 8
                                    val currentSize = localPlayer.troupe?.characterIds?.size ?: 0
                                    if (currentSize != requiredSize) {
                                        validationError = "Your troupe must have exactly $requiredSize characters. You currently have $currentSize."
                                    } else {
                                        viewModel.toggleTournamentReady(true)
                                    }
                                } else {
                                    viewModel.toggleTournamentReady(false)
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = if (isMoonstone) RoundedCornerShape(0.dp) else ButtonDefaults.shape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isReady) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isReady) "READY" else "CONFIRM READY")
                        }
                    }
                }
            } else if (state.isTournamentHost) {
                // Non-participating host info
                Text(
                    "You are managing this tournament as the organizer. You will be able to begin once all players are ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.isTournamentHost) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onBeginTournament,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = if (isMoonstone) RoundedCornerShape(0.dp) else ButtonDefaults.shape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    enabled = state.tournamentPlayers.isNotEmpty() && state.tournamentPlayers.all { it.isReady && it.troupe != null }
                ) {
                    Text("Begin Tournament")
                }
            }
        }

        // --- Dialogs ---

        if (showAddManualPlayerDialog) {
            AlertDialog(
                onDismissRequest = { showAddManualPlayerDialog = false },
                title = { Text("Add Manual Player") },
                text = {
                    OutlinedTextField(
                        value = manualPlayerName,
                        onValueChange = { manualPlayerName = it },
                        label = { Text("Player Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.addManualTournamentPlayer(manualPlayerName)
                            manualPlayerName = ""
                            showAddManualPlayerDialog = false
                        },
                        enabled = manualPlayerName.isNotBlank()
                    ) {
                        Text("Add Player")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddManualPlayerDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (playerToEditName != null) {
            AlertDialog(
                onDismissRequest = { playerToEditName = null },
                title = { Text("Edit Player Name") },
                text = {
                    OutlinedTextField(
                        value = newManualName,
                        onValueChange = { newManualName = it },
                        label = { Text("New Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateManualPlayerName(playerToEditName!!.first, newManualName)
                            playerToEditName = null
                        },
                        enabled = newManualName.isNotBlank()
                    ) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { playerToEditName = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (validationError != null) {
            AlertDialog(
                onDismissRequest = { validationError = null },
                title = { Text("Invalid Troupe") },
                text = { Text(validationError!!) },
                confirmButton = {
                    TextButton(onClick = { validationError = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun TournamentPlayerItem(
    player: TournamentPlayer, 
    isLocal: Boolean, 
    isMoonstone: Boolean,
    isHost: Boolean = false,
    onEditName: (String, String) -> Unit = { _, _ -> },
    onChangeTroupe: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    val isManual = player.deviceId.startsWith("manual_")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isMoonstone) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocal) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLocal) "${player.name} (You)" else player.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (isHost && isManual) {
                            IconButton(onClick = { onEditName(player.deviceId, player.name) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (player.troupe != null) {
                        Text(
                            text = "Troupe: ${player.troupe.troupeName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic
                        )
                    } else {
                        Text(
                            text = "Selecting troupe...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                if (isHost) {
                    if (isManual) {
                        TextButton(onClick = onChangeTroupe) {
                            Text("Set Troupe", fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove Player", tint = MaterialTheme.colorScheme.error)
                    }
                }

                if (player.isReady) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = "Ready", 
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
