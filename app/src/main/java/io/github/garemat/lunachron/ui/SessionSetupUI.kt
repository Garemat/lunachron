package io.github.garemat.lunachron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun SessionSetupUI(
    state: CharacterState,
    viewModel: CharacterViewModel,
    session: GameSession,
    troupes: List<Troupe>,
    onSelectTroupe: (Troupe) -> Unit,
    onStartGame: () -> Unit,
    onLeave: () -> Unit,
    onNavigateToAddEditTroupe: () -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var troupeToPrune by remember { mutableStateOf<Troupe?>(null) }
    val playerCount = session.players.size
    val isMoonstone = LocalAppThemeProperties.current.showExpandedStatsHeader

    LaunchedEffect(playerCount) {
        viewModel.uiEvent.collect { event ->
            if (event is CharacterViewModel.UiEvent.TroupeCreated) {
                if (event.playerIndex == -1) { // Special marker for local session user
                    val maxAllowed = when(playerCount) {
                        3 -> 4
                        4 -> 3
                        else -> 6
                    }
                    if (!event.troupe.isTournamentList && event.troupe.characterIds.size <= maxAllowed) {
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
            IconButton(onClick = onLeave, modifier = Modifier.onGloballyPositioned { onPositioned("LeaveButton", it) }) {
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
                        if (troupe.isTournamentList || troupe.characterIds.size > maxAllowed) {
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
    val isMoonstone = LocalAppThemeProperties.current.showExpandedStatsHeader
    
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
