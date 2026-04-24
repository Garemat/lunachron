package io.github.garemat.lunachron.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
    var showRegisterDialog by remember { mutableStateOf(false) }
    val theme = LocalAppThemeProperties.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val availableThemes = remember(context) { ThemeRepository(context).listAvailable() }

    val autoSynchronise = state.autoFetchNews &&
            state.autoCheckDataUpdates &&
            state.imageDownloadPreference == ImageDownloadPreference.ENABLED

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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(CharacterEvent.UpdateUserName(name)); onNavigateBack() },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text("Save", style = theme.buttonTextStyle) }
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
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = theme.cardShape
            )

            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            if (state.isRegistered) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = theme.verticalSpacing / 4)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "Device registered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { showRegisterDialog = true },
                    enabled = name.isNotBlank() && !state.isRegisteringDevice,
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape
                ) {
                    if (state.isRegisteringDevice) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registering…", style = theme.buttonTextStyle)
                    } else {
                        Text("Register device", style = theme.buttonTextStyle)
                    }
                }
            }

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

            Text("Game View", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newMode = if (state.gameTrackingMode == GameTrackingMode.FULL_TRACKING)
                            GameTrackingMode.LOW_DETAIL else GameTrackingMode.FULL_TRACKING
                        onEvent(CharacterEvent.ChangeGameTrackingMode(newMode))
                    }
                    .padding(vertical = theme.verticalSpacing / 4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable resource tracking", style = theme.headerStyle.copy(fontSize = 18.sp))
                    Text("Track energy, moonstones, and ability used markers in the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.gameTrackingMode == GameTrackingMode.FULL_TRACKING,
                    onCheckedChange = { enabled ->
                        onEvent(CharacterEvent.ChangeGameTrackingMode(if (enabled) GameTrackingMode.FULL_TRACKING else GameTrackingMode.LOW_DETAIL))
                    }
                )
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("Performance", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(CharacterEvent.SetEnableAnimations(!state.enableAnimations)) }
                    .padding(vertical = theme.verticalSpacing / 4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable animations", style = theme.headerStyle.copy(fontSize = 18.sp))
                    Text("Disable card flip and expand animations on lower-end devices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.enableAnimations,
                    onCheckedChange = { onEvent(CharacterEvent.SetEnableAnimations(it)) }
                )
            }
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 4))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(CharacterEvent.SetAutoHideNavBar(!state.autoHideNavBar)) }
                    .padding(vertical = theme.verticalSpacing / 4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-hide navigation bar", style = theme.headerStyle.copy(fontSize = 18.sp))
                    Text("Hides the bottom nav bar when a card is expanded or when editing a troupe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.autoHideNavBar,
                    onCheckedChange = { onEvent(CharacterEvent.SetAutoHideNavBar(it)) }
                )
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("App", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 4))
            Text("Default start page", style = theme.headerStyle.copy(fontSize = 18.sp))
            Text("The screen shown when the app first opens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            val startPageOptions = listOf(
                "home" to "Home",
                "compendium" to "Compendium",
                "characters" to "Character List",
                "game_setup" to "Play",
                "troupes" to "My Troupes",
                "campaign_hub" to "Campaigns"
            )
            Column {
                startPageOptions.forEach { (route, label) ->
                    SelectionOption(
                        title = label,
                        selected = state.defaultStartPage == route,
                        onSelect = { onEvent(CharacterEvent.SetDefaultStartPage(route)) },
                        theme = theme
                    )
                }
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

            Text("Sync & Updates", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 4))
            Text(
                text = "Game data version: ${state.installedDataVersion.ifEmpty { "bundled" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(CharacterEvent.SetAutoSynchronise(!autoSynchronise)) }
                    .padding(vertical = theme.verticalSpacing / 4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Synchronise updates", style = theme.headerStyle.copy(fontSize = 18.sp))
                    Text("Auto-check for news, game data, and portrait updates on startup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoSynchronise, onCheckedChange = { onEvent(CharacterEvent.SetAutoSynchronise(it)) })
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            OutlinedButton(
                onClick = { onEvent(CharacterEvent.CheckForDataUpdate) },
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape
            ) {
                Text("Check for updates now", style = theme.buttonTextStyle)
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            OutlinedButton(
                onClick = { onEvent(CharacterEvent.DownloadCharacterImages) },
                enabled = !state.isDownloadingImages,
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape
            ) {
                if (state.isDownloadingImages) {
                    PortraitDownloadProgress(
                        downloaded = state.imageDownloadedBytes,
                        total = state.imageTotalBytes,
                        speedBps = state.imageDownloadSpeedBps
                    )
                } else {
                    Text("Download portraits now", style = theme.buttonTextStyle)
                }
            }

            // Extra bottom padding so the FAB never covers the last item
            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    if (showRegisterDialog) {
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Register this device?", style = theme.headerStyle) },
            text = {
                Text(
                    "A pseudonymous hash of your device ID — unique to this app install — and " +
                    "your username \"$name\" will be stored on a private Oracle Cloud database. " +
                    "This data is used solely for Lunachron campaign coordination and is never " +
                    "shared with third parties.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRegisterDialog = false
                    onEvent(CharacterEvent.RegisterDevice(name))
                }) {
                    Text("Register")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.registrationError != null) {
        AlertDialog(
            onDismissRequest = { onEvent(CharacterEvent.DismissRegistrationError) },
            title = { Text("Registration failed", style = theme.headerStyle) },
            text = { Text(state.registrationError, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { onEvent(CharacterEvent.DismissRegistrationError) }) {
                    Text("OK")
                }
            }
        )
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
