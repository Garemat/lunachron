package com.garemat.moonstone_companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.AppTheme
import com.garemat.moonstone_companion.CharacterEvent
import com.garemat.moonstone_companion.CharacterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf(state.name) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val isMoonstone = state.theme == AppTheme.MOONSTONE

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Settings",
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime > 500) {
                            onNavigateBack()
                            lastBackPressTime = currentTime
                        }
                    }) {
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
                .padding(16.dp)
        ) {
            Text(
                "User Profile", 
                style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp) else MaterialTheme.typography.titleMedium,
                color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Player Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = if (isMoonstone) RoundedCornerShape(0.dp) else OutlinedTextFieldDefaults.shape
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "App Theme", 
                style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp) else MaterialTheme.typography.titleMedium,
                color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Column {
                ThemeOption(
                    title = "Default",
                    selected = state.theme == AppTheme.DEFAULT,
                    onSelect = { onEvent(CharacterEvent.ChangeTheme(AppTheme.DEFAULT)) },
                    isMoonstone = isMoonstone
                )
                ThemeOption(
                    title = "Moonstone",
                    selected = state.theme == AppTheme.MOONSTONE,
                    onSelect = { onEvent(CharacterEvent.ChangeTheme(AppTheme.MOONSTONE)) },
                    isMoonstone = isMoonstone
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Gameplay", 
                style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp) else MaterialTheme.typography.titleMedium,
                color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(CharacterEvent.SetLocalModeDefault(!state.useLocalModeByDefault)) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Skip Game Mode Selection",
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 18.sp) else MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Always jump straight to Local Game setup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.useLocalModeByDefault,
                    onCheckedChange = { onEvent(CharacterEvent.SetLocalModeDefault(it)) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { 
                    onEvent(CharacterEvent.UpdateUserName(name))
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = if (isMoonstone) RoundedCornerShape(0.dp) else ButtonDefaults.shape
            ) {
                Text(
                    "Save Settings",
                    style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 18.sp) else LocalTextStyle.current
                )
            }
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    isMoonstone: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Text(
            text = title,
            style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 18.sp) else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
