package io.github.garemat.lunachron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.CharacterEvent
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.OnlineCampaignSettings
import io.github.garemat.lunachron.ui.theme.AppThemeProperties
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun HostOnlineCampaignScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val clipboard = LocalClipboardManager.current

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attacksEnabled by remember { mutableStateOf(true) }
    var startingCharacters by remember { mutableIntStateOf(4) }
    var characterGrowthEvery by remember { mutableIntStateOf(2) }
    var upgradeGrowthEvery by remember { mutableIntStateOf(1) }

    // Clear the created result when leaving so stale data doesn't re-show
    DisposableEffect(Unit) { onDispose { onEvent(CharacterEvent.DismissCreatedCampaignResult) } }

    // Show join code dialog on successful creation
    if (state.createdCampaignResult != null) {
        val joinCode = state.createdCampaignResult.joinCode
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Campaign created!", style = theme.headerStyle) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Share this join code with players:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = joinCode,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(joinCode)) },
                        shape = theme.cardShape
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Copy code")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(CharacterEvent.DismissCreatedCampaignResult)
                    onNavigateBack()
                }) {
                    Text("Done")
                }
            }
        )
    }

    // Error dialog
    if (state.onlineCampaignError != null) {
        AlertDialog(
            onDismissRequest = { onEvent(CharacterEvent.DismissOnlineCampaignError) },
            title = { Text("Could not create campaign", style = theme.headerStyle) },
            text = { Text(state.onlineCampaignError, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { onEvent(CharacterEvent.DismissOnlineCampaignError) }) { Text("OK") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(theme.screenPadding)
    ) {
            // ── Campaign name ─────────────────────────────────────────────────
            Text("Campaign Details", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Campaign name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = theme.cardShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = theme.cardShape
            )

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            // ── Game rules ────────────────────────────────────────────────────
            Text("Rules", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Attacks enabled", style = theme.headerStyle.copy(fontSize = 18.sp))
                    Text("Allow campaign attack and assault actions between rounds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = attacksEnabled, onCheckedChange = { attacksEnabled = it })
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            StepperRow(
                label = "Starting characters",
                subtitle = "Number of characters each player begins the campaign with",
                value = startingCharacters,
                onDecrement = { if (startingCharacters > 3) startingCharacters-- },
                onIncrement = { if (startingCharacters < 10) startingCharacters++ },
                theme = theme
            )

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("Troupe growth", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            StepperRow(
                label = "New character every",
                subtitle = "Rounds between each character unlock (0 = never)",
                value = characterGrowthEvery,
                onDecrement = { if (characterGrowthEvery > 0) characterGrowthEvery-- },
                onIncrement = { characterGrowthEvery++ },
                unit = "rounds",
                theme = theme
            )
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            StepperRow(
                label = "New upgrade every",
                subtitle = "Rounds between each upgrade card unlock (0 = never)",
                value = upgradeGrowthEvery,
                onDecrement = { if (upgradeGrowthEvery > 0) upgradeGrowthEvery-- },
                onIncrement = { upgradeGrowthEvery++ },
                unit = "rounds",
                theme = theme
            )

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Button(
                onClick = {
                    onEvent(CharacterEvent.CreateOnlineCampaign(
                        name = name.trim(),
                        description = description.trim().takeIf { it.isNotEmpty() },
                        settings = OnlineCampaignSettings(
                            attacksEnabled = attacksEnabled,
                            startingCharacters = startingCharacters,
                            characterGrowthEvery = characterGrowthEvery,
                            upgradeGrowthEvery = upgradeGrowthEvery
                        )
                    ))
                },
                enabled = name.isNotBlank() && !state.isCreatingCampaign,
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape
            ) {
                if (state.isCreatingCampaign) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating…", style = theme.buttonTextStyle)
                } else {
                    Text("Create Campaign", style = theme.buttonTextStyle)
                }
            }

            Spacer(modifier = Modifier.height(88.dp))
        }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    theme: AppThemeProperties,
    subtitle: String? = null,
    unit: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = theme.headerStyle.copy(fontSize = 16.sp))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) {
                Text("−", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = if (unit != null) "$value $unit" else "$value",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = if (unit != null) 72.dp else 32.dp)
            )
            IconButton(onClick = onIncrement) {
                Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
