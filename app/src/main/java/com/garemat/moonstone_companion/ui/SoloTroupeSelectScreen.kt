package com.garemat.moonstone_companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.garemat.moonstone_companion.CharacterEvent
import com.garemat.moonstone_companion.CharacterState
import com.garemat.moonstone_companion.CharacterViewModel
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoloTroupeSelectScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onTroupeSelected: () -> Unit,
    onEditTroupe: () -> Unit
) {
    val theme = LocalAppThemeProperties.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Select Your Troupe", style = theme.titleStyle) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        if (state.troupes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No troupes yet. Create one in the Troupes tab!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(theme.verticalSpacing / 2)
            ) {
                item {
                    Text(
                        "Tap a troupe to start a solo session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = theme.screenPadding, vertical = theme.verticalSpacing / 2)
                    )
                }
                items(state.troupes, key = { it.id }) { troupe ->
                    val troupeCharacters = remember(troupe, state.characters) {
                        state.characters.filter { it.id in troupe.characterIds }
                    }
                    TroupeListItem(
                        troupe = troupe,
                        onClick = {
                            viewModel.startNewGame(listOf(troupe))
                            onTroupeSelected()
                        },
                        onEdit = {
                            viewModel.onEvent(CharacterEvent.EditTroupe(troupe))
                            onEditTroupe()
                        },
                        onDelete = {},
                        onShare = {},
                        selectionMode = true,
                        showDelete = false,
                        characters = troupeCharacters
                    )
                }
            }
        }
    }
}
