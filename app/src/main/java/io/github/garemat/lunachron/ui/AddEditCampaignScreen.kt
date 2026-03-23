package io.github.garemat.lunachron.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCampaignScreen(
    viewModel: CharacterViewModel,
    state: CharacterState,
    onNavigateBack: () -> Unit,
    onSelectTroupeForPlayer: (String) -> Unit
) {
    val originalCampaign = state.campaigns.find { it.id == viewModel.editingCampaignId }
    val hasChanges = viewModel.newCampaignName != (originalCampaign?.name ?: "") ||
            viewModel.newCampaignDescription != (originalCampaign?.description ?: "") ||
            viewModel.newCampaignAttacksEnabled != (originalCampaign?.attacksEnabled ?: false) ||
            viewModel.newCampaignTotalRounds != (originalCampaign?.totalRounds ?: 0) ||
            viewModel.newCampaignGameSize != (originalCampaign?.gameSize ?: 6) ||
            viewModel.newCampaignStartingCharacters != (originalCampaign?.startingCharacters ?: 6) ||
            viewModel.newCampaignCharacterGrowthEvery != (originalCampaign?.characterGrowthEvery ?: 1) ||
            viewModel.newCampaignUpgradeGrowthEvery != (originalCampaign?.upgradeGrowthEvery ?: 3) ||
            viewModel.selectedCampaignPlayers.toList() != (originalCampaign?.players ?: emptyList<CampaignPlayer>())

    CampaignFormContent(
        title = if (viewModel.editingCampaignId == null) "New Campaign" else "Campaign Settings",
        isNewCampaign = viewModel.editingCampaignId == null,
        campaignName = viewModel.newCampaignName,
        onNameChange = { viewModel.newCampaignName = it },
        description = viewModel.newCampaignDescription,
        onDescriptionChange = { viewModel.newCampaignDescription = it },
        attacksEnabled = viewModel.newCampaignAttacksEnabled,
        onAttacksEnabledChange = { viewModel.newCampaignAttacksEnabled = it },
        totalRounds = viewModel.newCampaignTotalRounds,
        onTotalRoundsChange = { viewModel.newCampaignTotalRounds = it },
        gameSize = viewModel.newCampaignGameSize,
        onGameSizeChange = { viewModel.newCampaignGameSize = it },
        startingCharacters = viewModel.newCampaignStartingCharacters,
        onStartingCharactersChange = { viewModel.newCampaignStartingCharacters = it },
        characterGrowthEvery = viewModel.newCampaignCharacterGrowthEvery,
        onCharacterGrowthEveryChange = { viewModel.newCampaignCharacterGrowthEvery = it },
        upgradeGrowthEvery = viewModel.newCampaignUpgradeGrowthEvery,
        onUpgradeGrowthEveryChange = { viewModel.newCampaignUpgradeGrowthEvery = it },
        players = viewModel.selectedCampaignPlayers,
        troupes = state.troupes,
        onSelectTroupeForPlayer = onSelectTroupeForPlayer,
        onPlayerAdded = { viewModel.selectedCampaignPlayers.add(it) },
        onPlayerRemoved = { viewModel.selectedCampaignPlayers.remove(it) },
        hasChanges = hasChanges,
        onSave = {
            viewModel.createCampaign(
                viewModel.newCampaignName,
                viewModel.newCampaignDescription,
                viewModel.selectedCampaignPlayers.toList(),
                viewModel.newCampaignAttacksEnabled,
                viewModel.newCampaignTotalRounds,
                viewModel.newCampaignGameSize,
                viewModel.newCampaignStartingCharacters,
                viewModel.newCampaignCharacterGrowthEvery,
                viewModel.newCampaignUpgradeGrowthEvery
            )
            onNavigateBack()
        },
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignFormContent(
    title: String,
    isNewCampaign: Boolean = false,
    campaignName: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    attacksEnabled: Boolean,
    onAttacksEnabledChange: (Boolean) -> Unit,
    totalRounds: Int,
    onTotalRoundsChange: (Int) -> Unit,
    gameSize: Int,
    onGameSizeChange: (Int) -> Unit,
    startingCharacters: Int,
    onStartingCharactersChange: (Int) -> Unit,
    characterGrowthEvery: Int,
    onCharacterGrowthEveryChange: (Int) -> Unit,
    upgradeGrowthEvery: Int,
    onUpgradeGrowthEveryChange: (Int) -> Unit,
    players: List<CampaignPlayer>,
    troupes: List<Troupe>,
    onSelectTroupeForPlayer: (String) -> Unit,
    onPlayerAdded: (CampaignPlayer) -> Unit,
    onPlayerRemoved: (CampaignPlayer) -> Unit,
    hasChanges: Boolean,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isNameError by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    // Track whether startingCharacters has been manually overridden from game size
    var startingCharactersOverridden by remember { mutableStateOf(!isNewCampaign) }

    BackHandler {
        if (hasChanges) showDiscardConfirmation = true else onNavigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(theme.screenPadding),
            verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
        ) {
            item {
                Text(
                    text = title,
                    style = theme.headerStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                OutlinedTextField(
                    value = campaignName,
                    onValueChange = {
                        onNameChange(it)
                        if (it.isNotBlank()) isNameError = false
                    },
                    label = { Text("Campaign Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = theme.cardShape,
                    isError = isNameError
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape = theme.cardShape
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Attacks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Optional rules for Assaults and Abductions (2AP per campaign)", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = attacksEnabled, onCheckedChange = onAttacksEnabledChange)
                    }
                }
            }

            // ── Game Settings ──────────────────────────────────────────────────────
            item {
                Text("Game Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
                ) {
                    CampaignStepperField(
                        label = "Game Size",
                        subtitle = "Characters per player per game",
                        value = gameSize,
                        minValue = 1,
                        onValueChange = { newSize ->
                            onGameSizeChange(newSize)
                            if (!startingCharactersOverridden) onStartingCharactersChange(newSize)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CampaignStepperField(
                        label = "Starting Characters",
                        subtitle = "Initial troupe size",
                        value = startingCharacters,
                        minValue = 1,
                        onValueChange = {
                            startingCharactersOverridden = true
                            onStartingCharactersChange(it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text("Troupe Growth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
                ) {
                    CampaignStepperField(
                        label = "New Character Every",
                        subtitle = "Rounds between recruits",
                        value = characterGrowthEvery,
                        minValue = 1,
                        onValueChange = onCharacterGrowthEveryChange,
                        modifier = Modifier.weight(1f)
                    )
                    CampaignStepperField(
                        label = "New Upgrade Every",
                        subtitle = "Rounds between upgrades",
                        value = upgradeGrowthEvery,
                        minValue = 1,
                        onValueChange = onUpgradeGrowthEveryChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Schedule ───────────────────────────────────────────────────────────
            item {
                Text("Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                val autoRounds = if (players.size < 2) 0
                    else if (players.size % 2 == 0) players.size - 1
                    else players.size
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Rounds", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "All games scheduled upfront at creation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (autoRounds > 0) {
                                TextButton(
                                    onClick = { onTotalRoundsChange(autoRounds) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Auto ($autoRounds)")
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (totalRounds > 1) onTotalRoundsChange(totalRounds - 1) },
                                enabled = totalRounds > 1
                            ) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
                            OutlinedTextField(
                                value = if (totalRounds == 0) "" else totalRounds.toString(),
                                onValueChange = { raw ->
                                    val v = raw.filter { it.isDigit() }.toIntOrNull() ?: 0
                                    onTotalRoundsChange(v.coerceAtLeast(0))
                                },
                                placeholder = { Text("0") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = theme.cardShape,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyLarge
                            )
                            IconButton(onClick = { onTotalRoundsChange(totalRounds + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                        if (players.size >= 2 && totalRounds > 0) {
                            val cycleLength = if (players.size % 2 == 0) players.size - 1 else players.size
                            val fullCycles = totalRounds / cycleLength
                            val remainder = totalRounds % cycleLength
                            val infoText = buildString {
                                append("${players.size} players · ${players.size / 2} game(s) per round")
                                if (players.size % 2 != 0) append(" · 1 bye per round")
                                append("\n")
                                if (fullCycles > 0) append("$fullCycles full round-robin cycle(s)")
                                if (fullCycles > 0 && remainder > 0) append(" + $remainder extra round(s)")
                                if (fullCycles == 0 && remainder > 0) append("Partial round-robin ($remainder/$cycleLength rounds)")
                            }
                            Text(infoText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Players ────────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Players", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAddPlayerDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Player")
                    }
                }
            }

            items(players) { player ->
                val troupe = troupes.find { it.id == player.troupeId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(player.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = troupe?.troupeName ?: "No troupe selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (troupe == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onSelectTroupeForPlayer(player.id) }) {
                            Icon(Icons.Default.Groups, contentDescription = "Select Troupe")
                        }
                        IconButton(onClick = { onPlayerRemoved(player) }) {
                            Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
            FloatingActionButton(
                onClick = {
                    if (campaignName.isNotBlank()) {
                        onSave()
                    } else {
                        isNameError = true
                        scope.launch { snackbarHostState.showSnackbar("Please enter a campaign name") }
                    }
                },
                shape = theme.navItemShape,
                containerColor = if (campaignName.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save Campaign")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirmation = false; onNavigateBack() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddPlayerDialog) {
        var newPlayerName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPlayerDialog = false },
            title = { Text("Add Manual Player") },
            text = {
                OutlinedTextField(
                    value = newPlayerName,
                    onValueChange = { newPlayerName = it },
                    label = { Text("Player Name") },
                    singleLine = true,
                    shape = LocalAppThemeProperties.current.cardShape
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPlayerAdded(CampaignPlayer(id = UUID.randomUUID().toString(), name = newPlayerName, troupeId = -1))
                        showAddPlayerDialog = false
                    },
                    enabled = newPlayerName.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddPlayerDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CampaignStepperField(
    label: String,
    subtitle: String,
    value: Int,
    minValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeProperties.current
    Card(
        modifier = modifier,
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (value > minValue) onValueChange(value - 1) },
                    enabled = value > minValue,
                    modifier = Modifier.size(32.dp)
                ) { Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp)) }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(
                    onClick = { onValueChange(value + 1) },
                    modifier = Modifier.size(32.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
