package com.garemat.moonstone_companion.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroupeListScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit,
    onAddTroupe: () -> Unit,
    onEditTroupe: () -> Unit,
    isTutorialActive: Boolean = false,
    selectionMode: Boolean = false,
    tournamentCriteria: TournamentSettings? = null,
    onTroupeSelected: (Troupe) -> Unit = {},
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var troupeToDelete by remember { mutableStateOf<Troupe?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    val theme = LocalAppThemeProperties.current
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (selectionMode) {
                    CenterAlignedTopAppBar(
                        title = { Text("Select Tournament Troupe", style = theme.titleStyle) },
                        navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddTroupe, modifier = Modifier.onGloballyPositioned { onTargetPositioned("AddTroupe", it) }) { Icon(Icons.Default.Add, contentDescription = null) }
            }
        ) { padding ->
            val troupesToShow = if (state.troupes.isEmpty() && isTutorialActive) {
                listOf(Troupe(id = -1, troupeName = "Example Troupe", faction = Faction.COMMONWEALTH, characterIds = emptyList(), shareCode = "DUMMY"))
            } else state.troupes

            if (troupesToShow.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No troupes yet. Create or import one!") }
            } else {
                LazyColumn(
                    contentPadding = padding,
                    modifier = Modifier.fillMaxSize().onGloballyPositioned { onTargetPositioned("TroupeList", it) },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(troupesToShow) { troupe ->
                        val isValid = if (tournamentCriteria != null) {
                            val req = if (tournamentCriteria.troupeSize == TroupeSizeSetting.V6_10) 10 else 8
                            troupe.isTournamentList && troupe.characterIds.size == req
                        } else true

                        TroupeListItem(
                            troupe = troupe,
                            onClick = { 
                                if (selectionMode) {
                                    if (isValid) onTroupeSelected(troupe)
                                    else { viewModel.onEvent(CharacterEvent.EditTroupe(troupe)); onEditTroupe() }
                                } else if (troupe.id != -1) { viewModel.onEvent(CharacterEvent.EditTroupe(troupe)); onEditTroupe() }
                            },
                            onDelete = { if (troupe.id != -1) troupeToDelete = troupe },
                            onShare = { 
                                val code = if (troupe.id != -1) viewModel.generateFullShareCode(troupe, state.characters) else "DUMMY_CODE"
                                shareTroupe(context, troupe.troupeName, code)
                            },
                            onEdit = { viewModel.onEvent(CharacterEvent.EditTroupe(troupe)); onEditTroupe() },
                            isDimmed = selectionMode && !isValid,
                            selectionMode = selectionMode,
                            onPositioned = onTargetPositioned
                        )
                    }
                }
            }
            
            if (troupeToDelete != null) {
                AlertDialog(onDismissRequest = { troupeToDelete = null }, title = { Text("Delete Troupe") }, text = { Text("Are you sure?") }, confirmButton = { TextButton(onClick = { troupeToDelete?.let { viewModel.onEvent(CharacterEvent.DeleteTroupe(it)) }; troupeToDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { troupeToDelete = null }) { Text("Cancel") } })
            }

            if (showImportDialog) {
                AlertDialog(onDismissRequest = { showImportDialog = false }, title = { Text("Import") }, text = { OutlinedTextField(value = importCode, onValueChange = { importCode = it }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { viewModel.importTroupe(importCode, state.characters); if (viewModel.state.value.errorMessage == null) { showImportDialog = false; importCode = ""; onAddTroupe() } }, enabled = importCode.isNotBlank()) { Text("Import") } }, dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text("Cancel") } })
            }

            if (state.errorMessage != null) {
                AlertDialog(onDismissRequest = { viewModel.onEvent(CharacterEvent.DismissError) }, title = { Text("Failed") }, text = { Text(state.errorMessage) }, confirmButton = { TextButton(onClick = { viewModel.onEvent(CharacterEvent.DismissError) }) { Text("OK") } })
            }
        }
    }
}

@Composable
fun TroupeListItem(
    troupe: Troupe,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    isDimmed: Boolean = false,
    selectionMode: Boolean = false,
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val theme = LocalAppThemeProperties.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).alpha(if (isDimmed) 0.5f else 1.0f).clickable { onClick() },
        shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(theme.cardContentPadding), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(getFactionColor(troupe.faction)), contentAlignment = Alignment.Center) {
                FactionSymbol(faction = troupe.faction, modifier = Modifier.fillMaxSize().padding(4.dp), tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = troupe.troupeName, 
                        style = theme.titleStyle.copy(fontSize = 18.sp, lineHeight = 22.sp), 
                        maxLines = 1, 
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (troupe.isTournamentList) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = "${troupe.characterIds.size} Characters", 
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectionMode) IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) }
            else IconButton(onClick = onShare, modifier = Modifier.size(36.dp).onGloballyPositioned { onPositioned("ShareTroupe", it) }) { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).onGloballyPositioned { onPositioned("DeleteTroupe", it) }) { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun shareTroupe(context: Context, name: String, code: String) {
    val intent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, code); type = "text/plain" }
    context.startActivity(Intent.createChooser(intent, null))
}
