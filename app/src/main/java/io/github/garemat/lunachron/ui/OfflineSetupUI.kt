package io.github.garemat.lunachron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@OptIn(ExperimentalMaterial3Api::class)
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
    val theme = LocalAppThemeProperties.current
    
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
                    if (!event.troupe.isTournamentList && event.troupe.characterIds.size <= maxAllowed) {
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
            Text(text = "Players:", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
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
                        shape = theme.cardShape
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = theme.cardShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation)
                ) {
                    Column(modifier = Modifier.padding(theme.cardContentPadding)) {
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
                                ) { Icon(Icons.Default.Save, contentDescription = "Save Troupe") }
                            }
                            
                            if (troupe != null) {
                                IconButton(
                                    onClick = { showQrForTroupe = troupe },
                                    modifier = Modifier.onGloballyPositioned { if (index == 0) onPositioned("QrCodeDisplayButton", it) }
                                ) { Icon(Icons.Default.QrCode, contentDescription = "Show QR") }
                            }

                            IconButton(
                                onClick = { onScanRequest(index) },
                                modifier = Modifier.onGloballyPositioned { if (index == 0) onPositioned("ScanQR", it) }
                            ) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR") }
                        }

                        TroupeSelector(
                            troupes = state.troupes,
                            selectedTroupe = selectedTroupes[index],
                            allCharacters = state.characters,
                            onTroupeSelected = { t ->
                                val maxAllowed = when(playerCount) { 3 -> 4; 4 -> 3; else -> 6 }
                                if (t.isTournamentList || t.characterIds.size > maxAllowed) troupeToPrune = index to t
                                else selectedTroupes[index] = t
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
            shape = theme.cardShape
        ) {
            Text(
                "BATTLE!", 
                style = theme.buttonTextStyle
            )
        }
    }
    
    if (showQrForTroupe != null) {
        val shareCode = viewModel.generateFullShareCode(showQrForTroupe!!, state.characters, state.upgradeCards)
        QrCodeDialog(troupeName = showQrForTroupe!!.troupeName, shareCode = shareCode, onDismiss = { showQrForTroupe = null })
    }

    if (troupeToPrune != null) {
        val (index, troupe) = troupeToPrune!!
        val maxAllowed = when(playerCount) { 3 -> 4; 4 -> 3; else -> 6 }
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
                        shape = theme.cardShape
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        troupeToSaveWithName?.let { troupe: Troupe ->
                            viewModel.saveTroupe(troupe.copy(troupeName = customTroupeName))
                        }
                        troupeToSaveWithName = null
                    },
                    enabled = customTroupeName.isNotBlank(),
                    shape = theme.cardShape
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { troupeToSaveWithName = null }) { Text("Cancel") } }
        )
    }
}
