package io.github.garemat.lunachron.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import io.github.garemat.lunachron.CharacterEvent
import io.github.garemat.lunachron.ui.theme.ThemeRepository
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.GameLayoutMode
import io.github.garemat.lunachron.GameTrackingMode
import io.github.garemat.lunachron.ImageDownloadPreference
import io.github.garemat.lunachron.InstallerSource
import io.github.garemat.lunachron.LayoutDensity
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf(state.name) }
    val theme = LocalAppThemeProperties.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val availableThemes = remember(context) { ThemeRepository(context).listAvailable() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = theme.titleStyle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(scrollState)
                .padding(theme.screenPadding)
        ) {
            Text("User Profile", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Player Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = theme.cardShape
            )
            
            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            
            Text("App Theme", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            
            Column {
                availableThemes.forEach { definition ->
                    ThemeOption(
                        title = definition.name,
                        subtitle = definition.fonts?.display?.replaceFirstChar { it.uppercase() }
                            ?.takeIf { it != "Default" },
                        selected = state.activeThemeId == definition.id,
                        onSelect = { onEvent(CharacterEvent.SetActiveTheme(definition.id)) },
                        theme = theme
                    )
                }
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            Text("Layout Density", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Column {
                LayoutDensity.entries.forEach { density ->
                    DensityOption(
                        title = density.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = state.layoutDensity == density,
                        onSelect = { onEvent(CharacterEvent.ChangeLayoutDensity(density)) },
                        theme = theme
                    )
                }
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("Gameplay", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(CharacterEvent.SetLocalModeDefault(!state.useLocalModeByDefault)) }
                    .padding(vertical = theme.verticalSpacing / 4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Skip Game Mode Selection", style = theme.headerStyle.copy(fontSize = 18.sp))
                    Text("Always jump straight to Local Game setup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.useLocalModeByDefault, onCheckedChange = { onEvent(CharacterEvent.SetLocalModeDefault(it)) })
            }

            if (state.useLocalModeByDefault) {
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEvent(CharacterEvent.SetSinglePlayerMode(!state.useSinglePlayerMode)) }
                        .padding(vertical = theme.verticalSpacing / 4)
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Only track 1 player", style = theme.headerStyle.copy(fontSize = 18.sp))
                        Text("Skip setup and pick a troupe directly for a solo session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.useSinglePlayerMode, onCheckedChange = { onEvent(CharacterEvent.SetSinglePlayerMode(it)) })
                }
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("Game View", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Column {
                SelectionOption(
                    title = "No tracking",
                    selected = state.gameTrackingMode == GameTrackingMode.LOW_DETAIL,
                    onSelect = { onEvent(CharacterEvent.ChangeGameTrackingMode(GameTrackingMode.LOW_DETAIL)) },
                    theme = theme,
                    subtitle = "Stats at a glance, track resources physically"
                )
                SelectionOption(
                    title = "Track Resources in App",
                    selected = state.gameTrackingMode == GameTrackingMode.FULL_TRACKING,
                    onSelect = { onEvent(CharacterEvent.ChangeGameTrackingMode(GameTrackingMode.FULL_TRACKING)) },
                    theme = theme,
                    subtitle = "Energy, moonstones, and ability used markers"
                )
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("Game Layout", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Column {
                SelectionOption(
                    title = "Low Detail",
                    selected = state.gameLayoutMode == GameLayoutMode.COMPACT_GRID,
                    onSelect = { onEvent(CharacterEvent.ChangeGameLayoutMode(GameLayoutMode.COMPACT_GRID)) },
                    theme = theme,
                    subtitle = "2-column grid, quick overview of all characters"
                )
                SelectionOption(
                    title = "Detailed",
                    selected = state.gameLayoutMode == GameLayoutMode.DETAILED_LIST,
                    onSelect = { onEvent(CharacterEvent.ChangeGameLayoutMode(GameLayoutMode.DETAILED_LIST)) },
                    theme = theme,
                    subtitle = "Single column, expandable cards with full stats"
                )
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("App Updates", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 4))
            if (state.installerSource == InstallerSource.FDROID) {
                Text(
                    text = "Installed via F-Droid — updates are managed by the F-Droid client.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (state.installerSource == InstallerSource.PLAY_STORE) {
                Text(
                    text = "Installed via Google Play — updates are managed by the Play Store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEvent(CharacterEvent.SetAutoCheckAppUpdates(!state.autoCheckAppUpdates)) }
                        .padding(vertical = theme.verticalSpacing / 4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-check for app updates", style = theme.headerStyle.copy(fontSize = 18.sp))
                        Text("Check for a new version of Lunachron on startup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.autoCheckAppUpdates, onCheckedChange = { onEvent(CharacterEvent.SetAutoCheckAppUpdates(it)) })
                }
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                OutlinedButton(
                    onClick = { onEvent(CharacterEvent.CheckForAppUpdate) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape
                ) {
                    Text("Check for app update now", style = theme.buttonTextStyle)
                }
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("Data Updates", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 4))
            Text(
                text = "Installed version: ${state.installedDataVersion.ifEmpty { "bundled" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(CharacterEvent.SetAutoCheckDataUpdates(!state.autoCheckDataUpdates)) }
                    .padding(vertical = theme.verticalSpacing / 4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-check for updates", style = theme.headerStyle.copy(fontSize = 18.sp))
                    Text("Check for new game data on startup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.autoCheckDataUpdates, onCheckedChange = { onEvent(CharacterEvent.SetAutoCheckDataUpdates(it)) })
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            OutlinedButton(
                onClick = { onEvent(CharacterEvent.CheckForDataUpdate) },
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape
            ) {
                Text("Check for updates now", style = theme.buttonTextStyle)
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newPref = if (state.imageDownloadPreference == ImageDownloadPreference.ENABLED)
                            ImageDownloadPreference.DISABLED else ImageDownloadPreference.ENABLED
                        onEvent(CharacterEvent.SetImageDownloadPreference(newPref))
                    }
                    .padding(vertical = theme.verticalSpacing / 4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download character portraits", style = theme.headerStyle.copy(fontSize = 18.sp))
                    Text("Automatically check for new portrait images.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.imageDownloadPreference == ImageDownloadPreference.ENABLED,
                    onCheckedChange = { enabled ->
                        onEvent(CharacterEvent.SetImageDownloadPreference(if (enabled) ImageDownloadPreference.ENABLED else ImageDownloadPreference.DISABLED))
                    }
                )
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            OutlinedButton(
                onClick = { onEvent(CharacterEvent.DownloadCharacterImages) },
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape
            ) {
                Text("Download portraits now", style = theme.buttonTextStyle)
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing * 2))

            Button(
                onClick = { onEvent(CharacterEvent.UpdateUserName(name)); onNavigateBack() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = theme.cardShape
            ) {
                Text(
                    text = "Save Settings",
                    style = theme.buttonTextStyle
                )
            }
        }
    }
}

@Composable
fun SelectionOption(title: String, selected: Boolean, onSelect: () -> Unit, theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties, subtitle: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = theme.verticalSpacing / 4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = title, style = theme.headerStyle.copy(fontSize = 18.sp))
            if (subtitle != null) Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Aliases kept for backward compatibility within this file
@Composable
fun ThemeOption(title: String, selected: Boolean, onSelect: () -> Unit, theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties, subtitle: String? = null) =
    SelectionOption(title, selected, onSelect, theme, subtitle)

@Composable
fun DensityOption(title: String, selected: Boolean, onSelect: () -> Unit, theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties) =
    SelectionOption(title, selected, onSelect, theme)
