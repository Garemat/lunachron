package com.garemat.moonstone_companion.ui

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
import com.garemat.moonstone_companion.AppTheme
import com.garemat.moonstone_companion.CharacterEvent
import com.garemat.moonstone_companion.CharacterState
import com.garemat.moonstone_companion.GameTrackingMode
import com.garemat.moonstone_companion.LayoutDensity
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties

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
                ThemeOption(
                    title = "Default",
                    selected = state.theme == AppTheme.DEFAULT,
                    onSelect = { onEvent(CharacterEvent.ChangeTheme(AppTheme.DEFAULT)) },
                    theme = theme
                )
                ThemeOption(
                    title = "Moonstone",
                    selected = state.theme == AppTheme.MOONSTONE,
                    onSelect = { onEvent(CharacterEvent.ChangeTheme(AppTheme.MOONSTONE)) },
                    theme = theme
                )
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

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Text("Game View", style = theme.titleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))

            Column {
                SelectionOption(
                    title = "Low Detail",
                    selected = state.gameTrackingMode == GameTrackingMode.LOW_DETAIL,
                    onSelect = { onEvent(CharacterEvent.ChangeGameTrackingMode(GameTrackingMode.LOW_DETAIL)) },
                    theme = theme,
                    subtitle = "Stats at a glance, no resource tracking"
                )
                SelectionOption(
                    title = "Track Resources in App",
                    selected = state.gameTrackingMode == GameTrackingMode.FULL_TRACKING,
                    onSelect = { onEvent(CharacterEvent.ChangeGameTrackingMode(GameTrackingMode.FULL_TRACKING)) },
                    theme = theme,
                    subtitle = "Energy, moonstones, and ability used markers"
                )
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
fun SelectionOption(title: String, selected: Boolean, onSelect: () -> Unit, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties, subtitle: String? = null) {
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
fun ThemeOption(title: String, selected: Boolean, onSelect: () -> Unit, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties) =
    SelectionOption(title, selected, onSelect, theme)

@Composable
fun DensityOption(title: String, selected: Boolean, onSelect: () -> Unit, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties) =
    SelectionOption(title, selected, onSelect, theme)
