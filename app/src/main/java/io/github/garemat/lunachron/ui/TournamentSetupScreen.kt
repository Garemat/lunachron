package io.github.garemat.lunachron.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.HostMode
import io.github.garemat.lunachron.TroupeSizeSetting
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentSetupScreen(
    state: CharacterState,
    onNavigateBack: () -> Unit,
    onStartTournament: (String, TroupeSizeSetting, Int, Boolean, String, HostMode) -> Unit,
    isEditMode: Boolean = false,
    onUpdateSettings: (String, TroupeSizeSetting, Int, Boolean) -> Unit = { _, _, _, _ -> },
    onDisband: () -> Unit = {}
) {
    val existingSettings = state.tournamentSettings
    
    var tournamentName by remember { mutableStateOf(existingSettings?.tournamentName ?: "") }
    var selectedTroupeSize by remember { mutableStateOf(existingSettings?.troupeSize ?: TroupeSizeSetting.V6_10) }
    var roundTimer by remember { mutableStateOf(existingSettings?.roundTimerMinutes?.toString() ?: "90") }
    var hostParticipating by remember { mutableStateOf(existingSettings?.hostParticipating ?: true) }
    var passcode by remember { mutableStateOf(existingSettings?.passcode ?: String.format(Locale.getDefault(), "%04d", Random.nextInt(10000))) }
    var selectedHostMode by remember { mutableStateOf(HostMode.WIFI_NSD) }

    var showDisbandConfirmation by remember { mutableStateOf(false) }

    val theme = LocalAppThemeProperties.current
    val scrollState = rememberScrollState()

    BackHandler(enabled = isEditMode) {
        showDisbandConfirmation = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (isEditMode) "Edit Tournament" else "Setup Local Tournament",
                        style = theme.titleStyle
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isEditMode) showDisbandConfirmation = true 
                        else onNavigateBack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tournament Name
            OutlinedTextField(
                value = tournamentName,
                onValueChange = { tournamentName = it },
                label = { Text("Tournament Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = theme.cardShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Passcode
            OutlinedTextField(
                value = passcode,
                onValueChange = { if (!isEditMode && it.length <= 4 && it.all { char -> char.isDigit() }) passcode = it },
                label = { Text("Event Pin (4 digits)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isEditMode,
                shape = theme.cardShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Tournament Rules",
                style = theme.headerStyle.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Troupe Size Selection
            Text("Troupe Size", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(horizontalAlignment = Alignment.Start) {
                TroupeSizeOption(
                    title = "6v6 (10 character selection)",
                    description = "Choose 6 characters from a pool of 10",
                    selected = selectedTroupeSize == TroupeSizeSetting.V6_10,
                    onSelect = { selectedTroupeSize = TroupeSizeSetting.V6_10 },
                    theme = theme
                )
                Spacer(modifier = Modifier.height(16.dp))
                TroupeSizeOption(
                    title = "5v5 (8 character selection)",
                    description = "Choose 5 characters from a pool of 8",
                    selected = selectedTroupeSize == TroupeSizeSetting.V5_8,
                    onSelect = { selectedTroupeSize = TroupeSizeSetting.V5_8 },
                    theme = theme
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Round Timer
            Text("Round Timer (Minutes)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = roundTimer,
                onValueChange = { if (it.all { char -> char.isDigit() }) roundTimer = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = theme.cardShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Host Participating Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Will host be participating?",
                        style = theme.headerStyle.copy(fontSize = 18.sp)
                    )
                    Text(
                        "If off, this device only tracks scores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hostParticipating,
                    onCheckedChange = { hostParticipating = it }
                )
            }

            if (!isEditMode) {
                Spacer(modifier = Modifier.height(24.dp))

                Text("Connection Method", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Column(horizontalAlignment = Alignment.Start) {
                    TroupeSizeOption(
                        title = "Same Wi-Fi",
                        description = "All players on the same router. Recommended for most venues.",
                        selected = selectedHostMode == HostMode.WIFI_NSD,
                        onSelect = { selectedHostMode = HostMode.WIFI_NSD },
                        theme = theme
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TroupeSizeOption(
                        title = "Wi-Fi Direct",
                        description = "No router needed — devices connect directly. Joining players may temporarily lose internet access.",
                        selected = selectedHostMode == HostMode.WIFI_DIRECT,
                        onSelect = { selectedHostMode = HostMode.WIFI_DIRECT },
                        theme = theme
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val timer = roundTimer.toIntOrNull() ?: 90
                    if (isEditMode) {
                        onUpdateSettings(tournamentName, selectedTroupeSize, timer, hostParticipating)
                    } else {
                        onStartTournament(tournamentName, selectedTroupeSize, timer, hostParticipating, passcode, selectedHostMode)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = theme.cardShape,
                enabled = tournamentName.isNotBlank() && passcode.length == 4
            ) {
                Text(
                    if (isEditMode) "Update Tournament" else "Create Tournament",
                    style = theme.buttonTextStyle
                )
            }
        }

        if (showDisbandConfirmation) {
            AlertDialog(
                onDismissRequest = { showDisbandConfirmation = false },
                title = { Text("Disband Tournament?") },
                text = { Text("Are you sure? This will disconnect all players and cancel the event.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDisbandConfirmation = false
                            onDisband()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = theme.cardShape
                    ) {
                        Text("Confirm Disband", style = theme.buttonTextStyle)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisbandConfirmation = false }) {
                        Text("Cancel")
                    }
                },
                shape = theme.cardShape
            )
        }
    }
}

@Composable
fun TroupeSizeOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = theme.headerStyle.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold
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
