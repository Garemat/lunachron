package com.garemat.moonstone_companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TournamentRoundScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val round = state.currentTournamentRound ?: return
    val settings = state.tournamentSettings ?: return
    
    val myPairing = round.pairings.find { it.player1Id == state.deviceId || it.player2Id == state.deviceId }
    val isPlayer1 = myPairing?.player1Id == state.deviceId
    
    // Find the player profiles
    val players = state.tournamentPlayers
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Tournament Round ${round.roundNumber}", style = theme.titleStyle) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(theme.screenPadding),
            verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing * 1.5f)
        ) {
            if (myPairing != null) {
                item {
                    Text(
                        text = "Your Pairing",
                        style = theme.titleStyle.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    MyPairingCard(
                        pairing = myPairing,
                        isPlayer1 = isPlayer1,
                        players = players,
                        settings = settings,
                        allCharacters = state.characters,
                        theme = theme,
                        isHost = state.isTournamentHost,
                        deviceId = state.deviceId,
                        onConfirmSelection = { selectedIds, targetId ->
                            viewModel.confirmTournamentCharacterSelection(selectedIds, targetId)
                        },
                        onConfirmInitiative = { winnerId ->
                            viewModel.setTournamentInitiative(myPairing, winnerId)
                        },
                        onConfirmDeployment = {
                            viewModel.confirmTournamentDeployment(myPairing)
                        }
                    )
                }
            }

            item {
                Text(
                    text = "All Round Pairings",
                    style = theme.headerStyle.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            items(round.pairings) { pairing ->
                PairingListItem(
                    pairing = pairing,
                    players = players,
                    theme = theme,
                    isMyPairing = pairing == myPairing
                )
            }

            if (state.isTournamentHost) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.startTournamentActiveGames() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = theme.cardShape,
                        enabled = round.pairings.all { it.player1DeploymentReady && it.player2DeploymentReady }
                    ) {
                        Text("BEGIN ALL BATTLES", style = theme.buttonTextStyle)
                    }
                }
            }
        }
    }
}

@Composable
fun MyPairingCard(
    pairing: TournamentPairing,
    isPlayer1: Boolean,
    players: List<TournamentPlayer>,
    settings: TournamentSettings,
    allCharacters: List<Character>,
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
    isHost: Boolean,
    deviceId: String,
    onConfirmSelection: (List<Int>, String?) -> Unit,
    onConfirmInitiative: (String) -> Unit,
    onConfirmDeployment: () -> Unit
) {
    val opponentId = if (isPlayer1) pairing.player2Id else pairing.player1Id
    val me = players.find { it.deviceId == (if (isPlayer1) pairing.player1Id else pairing.player2Id) }
    val opponent = players.find { it.deviceId == opponentId }
    
    val myTroupe = me?.troupe ?: return
    val requiredCount = if (settings.troupeSize == TroupeSizeSetting.V6_10) 6 else 5
    
    var selectedIds by remember { mutableStateOf(if (isPlayer1) pairing.player1CharacterIds else pairing.player2CharacterIds) }
    var expandedCharacterId by remember { mutableStateOf<Int?>(null) }
    
    val isConfirmed = if (isPlayer1) pairing.player1Confirmed else pairing.player2Confirmed
    val opponentConfirmed = if (isPlayer1) pairing.player2Confirmed else pairing.player1Confirmed
    val bothConfirmed = isConfirmed && opponentConfirmed
    
    val myDeploymentReady = if (isPlayer1) pairing.player1DeploymentReady else pairing.player2DeploymentReady
    val opponentDeploymentReady = if (isPlayer1) pairing.player2DeploymentReady else pairing.player1DeploymentReady

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = me.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                FactionCircle(faction = myTroupe.faction, modifier = Modifier.size(24.dp))
                
                Text(
                    text = " vs ",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                if (opponent?.troupe != null) {
                    FactionCircle(faction = opponent.troupe.faction, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = opponent?.name ?: "Unknown",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                if (myDeploymentReady && opponentDeploymentReady) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = "Ready", tint = theme.readyColor)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!bothConfirmed) {
                SelectionView(
                    isConfirmed = isConfirmed,
                    opponentConfirmed = opponentConfirmed,
                    requiredCount = requiredCount,
                    myTroupe = myTroupe,
                    opponentTroupe = opponent?.troupe,
                    opponentId = opponentId,
                    allCharacters = allCharacters,
                    selectedIds = selectedIds,
                    expandedCharacterId = expandedCharacterId,
                    theme = theme,
                    isHost = isHost,
                    onSelectionChange = { selectedIds = it },
                    onExpandChange = { expandedCharacterId = it },
                    onConfirm = { onConfirmSelection(selectedIds, null) },
                    onConfirmManual = { ids, manualId -> onConfirmSelection(ids, manualId) }
                )
            } else {
                DeploymentView(
                    pairing = pairing,
                    isPlayer1 = isPlayer1,
                    players = players,
                    allCharacters = allCharacters,
                    isHost = isHost,
                    deviceId = deviceId,
                    theme = theme,
                    myDeploymentReady = myDeploymentReady,
                    opponentDeploymentReady = opponentDeploymentReady,
                    onConfirmInitiative = onConfirmInitiative,
                    onConfirmDeployment = onConfirmDeployment
                )
            }
        }
    }
}

@Composable
private fun SelectionView(
    isConfirmed: Boolean,
    opponentConfirmed: Boolean,
    requiredCount: Int,
    myTroupe: Troupe,
    opponentTroupe: Troupe?,
    opponentId: String,
    allCharacters: List<Character>,
    selectedIds: List<Int>,
    expandedCharacterId: Int?,
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
    isHost: Boolean,
    onSelectionChange: (List<Int>) -> Unit,
    onExpandChange: (Int?) -> Unit,
    onConfirm: () -> Unit,
    onConfirmManual: (List<Int>, String) -> Unit
) {
    val isOpponentManual = opponentId.startsWith("manual_")
    
    if (!isConfirmed) {
        Text(
            text = "Select your $requiredCount characters for this round:",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        val troupeCharacters = remember(myTroupe) {
            myTroupe.characterIds.mapNotNull { id -> allCharacters.find { it.id == id } }
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            troupeCharacters.forEach { character ->
                val isSelected = selectedIds.contains(character.id)
                SelectionCharacterCard(
                    character = character,
                    isSelected = isSelected,
                    isExpanded = expandedCharacterId == character.id,
                    onToggleSelect = {
                        if (isSelected) onSelectionChange(selectedIds - character.id)
                        else if (selectedIds.size < requiredCount) onSelectionChange(selectedIds + character.id)
                    },
                    onExpandClick = {
                        onExpandChange(if (expandedCharacterId == character.id) null else character.id)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedIds.size == requiredCount,
            shape = theme.cardShape
        ) {
            Text("Finalise Selection (${selectedIds.size}/$requiredCount)", style = theme.buttonTextStyle)
        }
    } else if (isOpponentManual && isHost && !opponentConfirmed) {
        // Host selects for manual player after themselves
        var manualSelectedIds by remember { mutableStateOf<List<Int>>(emptyList()) }
        var manualExpandedId by remember { mutableStateOf<Int?>(null) }
        
        Text(
            text = "Select characters for manual player:",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        val opponentChars = remember(opponentTroupe) {
            opponentTroupe?.characterIds?.mapNotNull { id -> allCharacters.find { it.id == id } } ?: emptyList()
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opponentChars.forEach { character ->
                val isSelected = manualSelectedIds.contains(character.id)
                SelectionCharacterCard(
                    character = character,
                    isSelected = isSelected,
                    isExpanded = manualExpandedId == character.id,
                    onToggleSelect = {
                        if (isSelected) manualSelectedIds = manualSelectedIds - character.id
                        else if (manualSelectedIds.size < requiredCount) manualSelectedIds = manualSelectedIds + character.id
                    },
                    onExpandClick = {
                        manualExpandedId = if (manualExpandedId == character.id) null else character.id
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { onConfirmManual(manualSelectedIds, opponentId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = manualSelectedIds.size == requiredCount,
            shape = theme.cardShape
        ) {
            Text("Finalise Manual Selection (${manualSelectedIds.size}/$requiredCount)", style = theme.buttonTextStyle)
        }
    } else {
        Text(
            text = "Waiting for opponent to finalise...",
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DeploymentView(
    pairing: TournamentPairing,
    isPlayer1: Boolean,
    players: List<TournamentPlayer>,
    allCharacters: List<Character>,
    isHost: Boolean,
    deviceId: String,
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
    myDeploymentReady: Boolean,
    opponentDeploymentReady: Boolean,
    onConfirmInitiative: (String) -> Unit,
    onConfirmDeployment: () -> Unit
) {
    val me = players.find { it.deviceId == (if (isPlayer1) pairing.player1Id else pairing.player2Id) }
    val opponent = players.find { it.deviceId == (if (isPlayer1) pairing.player2Id else pairing.player1Id) }
    
    val myCharIds = if (isPlayer1) pairing.player1CharacterIds else pairing.player2CharacterIds
    val opponentCharIds = if (isPlayer1) pairing.player2CharacterIds else pairing.player1CharacterIds

    val myInitiativeSelection = if (isPlayer1) pairing.player1InitiativeSelection else pairing.player2InitiativeSelection
    val opponentInitiativeSelection = if (isPlayer1) pairing.player2InitiativeSelection else pairing.player1InitiativeSelection
    val consensusMismatch = myInitiativeSelection != null && opponentInitiativeSelection != null && myInitiativeSelection != opponentInitiativeSelection

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Deployment Phase",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // My Team
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Your Team", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                myCharIds.forEach { id ->
                    val char = allCharacters.find { it.id == id }
                    Text("• ${char?.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            // Opponent Team
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${opponent?.name}'s Team", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                opponentCharIds.forEach { id ->
                    val char = allCharacters.find { it.id == id }
                    Text("• ${char?.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Roll for Initiative",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (pairing.initiativePlayerId == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p1 = players.find { it.deviceId == pairing.player1Id }
                    val p2 = players.find { it.deviceId == pairing.player2Id }
                    
                    Button(
                        onClick = { onConfirmInitiative(pairing.player1Id) },
                        modifier = Modifier.weight(1f),
                        shape = theme.cardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (myInitiativeSelection == pairing.player1Id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ),
                        enabled = !myDeploymentReady
                    ) {
                        Text("${p1?.name ?: "P1"} Won", style = theme.buttonTextStyle.copy(fontSize = 12.sp))
                    }
                    Button(
                        onClick = { onConfirmInitiative(pairing.player2Id) },
                        modifier = Modifier.weight(1f),
                        shape = theme.cardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (myInitiativeSelection == pairing.player2Id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ),
                        enabled = !myDeploymentReady
                    ) {
                        Text("${p2?.name ?: "P2"} Won", style = theme.buttonTextStyle.copy(fontSize = 12.sp))
                    }
                }
                
                if (consensusMismatch) {
                    Text(
                        text = "Selection mismatch! Please agree on who won.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (myInitiativeSelection != null) {
                    Text(
                        text = "Waiting for opponent's selection...",
                        fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            val iWon = pairing.initiativePlayerId == deviceId
            
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (iWon) "You have INITIATIVE" else "You are the EARLY BIRD",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (iWon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        if (myDeploymentReady) {
                            Text(
                                text = if (opponentDeploymentReady) "Both players ready!" else "Waiting for opponent...",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (!myDeploymentReady) {
                    Button(
                        onClick = onConfirmDeployment,
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape
                    ) {
                        Text("CONFIRM DEPLOYMENT", style = theme.buttonTextStyle)
                    }
                    
                    TextButton(onClick = { onConfirmInitiative("") }) { // Clear selection
                        Text("Change Initiative Selection", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PairingListItem(
    pairing: TournamentPairing,
    players: List<TournamentPlayer>,
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
    isMyPairing: Boolean
) {
    val p1 = players.find { it.deviceId == pairing.player1Id }
    val p2 = players.find { it.deviceId == pairing.player2Id }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isMyPairing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = p1?.name ?: "Unknown", fontWeight = FontWeight.Bold)
                    if (pairing.player1DeploymentReady) Text("Ready", style = MaterialTheme.typography.labelSmall, color = theme.readyColor)
                    else if (pairing.player1Confirmed) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (p1?.troupe != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FactionCircle(faction = p1.troupe.faction, modifier = Modifier.size(16.dp))
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("vs", style = MaterialTheme.typography.bodySmall)
                if (pairing.initiativePlayerId != null) {
                    Icon(Icons.Default.Info, contentDescription = "Initiative Set", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                if (p2?.troupe != null) {
                    FactionCircle(faction = p2.troupe.faction, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = p2?.name ?: "Unknown", fontWeight = FontWeight.Bold)
                    if (pairing.player2DeploymentReady) Text("Ready", style = MaterialTheme.typography.labelSmall, color = theme.readyColor)
                    else if (pairing.player2Confirmed) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
