package io.github.garemat.lunachron.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
            viewModel.selectedCampaignPlayers.toList() != (originalCampaign?.players ?: emptyList<CampaignPlayer>())

    CampaignFormContent(
        title = if (viewModel.editingCampaignId == null) "New Campaign" else "Campaign Settings",
        campaignName = viewModel.newCampaignName,
        onNameChange = { viewModel.newCampaignName = it },
        description = viewModel.newCampaignDescription,
        onDescriptionChange = { viewModel.newCampaignDescription = it },
        attacksEnabled = viewModel.newCampaignAttacksEnabled,
        onAttacksEnabledChange = { viewModel.newCampaignAttacksEnabled = it },
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
                viewModel.newCampaignAttacksEnabled
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
    campaignName: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    attacksEnabled: Boolean,
    onAttacksEnabledChange: (Boolean) -> Unit,
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
