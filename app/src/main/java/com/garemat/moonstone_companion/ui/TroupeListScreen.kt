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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.CharacterEvent
import com.garemat.moonstone_companion.CharacterState
import com.garemat.moonstone_companion.CharacterViewModel
import com.garemat.moonstone_companion.Troupe
import com.garemat.moonstone_companion.Faction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroupeListScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit,
    onAddTroupe: () -> Unit,
    onEditTroupe: () -> Unit,
    isTutorialActive: Boolean = false,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var troupeToDelete by remember { mutableStateOf<Troupe?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddTroupe,
                    modifier = Modifier.onGloballyPositioned { onTargetPositioned("AddTroupe", it) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Troupe")
                }
            }
        ) { padding ->
            // If no troupes exist AND tutorial is active, show a dummy one for the tutorial
            val troupesToShow = if (state.troupes.isEmpty() && isTutorialActive) {
                listOf(Troupe(id = -1, troupeName = "Example Troupe", faction = Faction.COMMONWEALTH, characterIds = emptyList(), shareCode = "DUMMY"))
            } else {
                state.troupes
            }

            if (troupesToShow.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No troupes yet. Create or import one!")
                }
            } else {
                LazyColumn(
                    contentPadding = padding,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { onTargetPositioned("TroupeList", it) },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(troupesToShow) { troupe ->
                        TroupeListItem(
                            troupe = troupe,
                            onClick = { 
                                if (troupe.id != -1) {
                                    viewModel.onEvent(CharacterEvent.EditTroupe(troupe))
                                    onEditTroupe()
                                }
                            },
                            onDelete = { if (troupe.id != -1) troupeToDelete = troupe },
                            onShare = { 
                                if (troupe.id != -1) {
                                    val fullCode = viewModel.generateFullShareCode(troupe, state.characters)
                                    shareTroupe(context, troupe.troupeName, fullCode)
                                } else {
                                    shareTroupe(context, troupe.troupeName, "DUMMY_CODE")
                                }
                            },
                            onPositioned = onTargetPositioned
                        )
                    }
                }
            }
            
            // Delete Dialog
            if (troupeToDelete != null) {
                AlertDialog(
                    onDismissRequest = { troupeToDelete = null },
                    title = { Text("Delete Troupe") },
                    text = { Text("Are you sure you want to delete '${troupeToDelete?.troupeName}'?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                troupeToDelete?.let { viewModel.onEvent(CharacterEvent.DeleteTroupe(it)) }
                                troupeToDelete = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { troupeToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Import Dialog
            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("Import Troupe") },
                    text = {
                        Column {
                            Text("Paste the shared troupe code below:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = importCode,
                                onValueChange = { importCode = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Paste code here") }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.importTroupe(importCode, state.characters)
                                if (viewModel.state.value.errorMessage == null) {
                                    showImportDialog = false
                                    importCode = ""
                                    onAddTroupe() // Navigate to editor with imported data
                                }
                            },
                            enabled = importCode.isNotBlank()
                        ) {
                            Text("Import")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Error Dialog
            if (state.errorMessage != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.onEvent(CharacterEvent.DismissError) },
                    title = { Text("Import Failed") },
                    text = { Text(state.errorMessage) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onEvent(CharacterEvent.DismissError) }) {
                            Text("OK")
                        }
                    }
                )
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
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getFactionColor(troupe.faction)),
                contentAlignment = Alignment.Center
            ) {
                FactionSymbol(
                    faction = troupe.faction,
                    modifier = Modifier.fillMaxSize(),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = troupe.troupeName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${troupe.characterIds.size} Characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.onGloballyPositioned { onPositioned("ShareTroupe", it) }
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share Code")
            }
            
            IconButton(
                onClick = onDelete,
                modifier = Modifier.onGloballyPositioned { onPositioned("DeleteTroupe", it) }
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun shareTroupe(context: Context, name: String, code: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "$code")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}
