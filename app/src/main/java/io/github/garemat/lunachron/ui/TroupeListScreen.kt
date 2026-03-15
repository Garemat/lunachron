package io.github.garemat.lunachron.ui

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
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

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
    isCampaignSelection: Boolean = false,
    onTroupeSelected: (Troupe) -> Unit = {},
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var troupeToDelete by remember { mutableStateOf<Troupe?>(null) }
    var showQrForTroupe by remember { mutableStateOf<Troupe?>(null) }
    val context = LocalContext.current
    val theme = LocalAppThemeProperties.current
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (selectionMode) {
                    CenterAlignedTopAppBar(
                        title = { Text(if (isCampaignSelection) "Select Campaign Troupe" else "Select Tournament Troupe", style = theme.titleStyle) },
                        navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
                    )
                }
            },
            floatingActionButton = {
                if (!selectionMode) {
                    FloatingActionButton(onClick = onAddTroupe, modifier = Modifier.onGloballyPositioned { onTargetPositioned("AddTroupe", it) }) { Icon(Icons.Default.Add, contentDescription = null) }
                }
            }
        ) { padding ->
            val troupesToShow = remember(state.troupes, isCampaignSelection) {
                if (isCampaignSelection) {
                    state.troupes.filter { it.isCampaignTroupe }
                } else if (state.troupes.isEmpty() && isTutorialActive) {
                    listOf(Troupe(id = -1, troupeName = "Example Troupe", faction = Faction.COMMONWEALTH, characterIds = emptyList(), shareCode = "DUMMY"))
                } else state.troupes
            }

            if (troupesToShow.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { 
                    Text(if (isCampaignSelection) "No campaign troupes found. Create one first!" else "No troupes yet. Create or import one!") 
                }
            } else {
                LazyColumn(
                    contentPadding = padding,
                    modifier = Modifier.fillMaxSize().onGloballyPositioned { onTargetPositioned("TroupeList", it) },
                    verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing / 2)
                ) {
                    items(troupesToShow) { troupe ->
                        val isValid = if (tournamentCriteria != null) {
                            val req = if (tournamentCriteria.troupeSize == TroupeSizeSetting.V6_10) 10 else 8
                            troupe.isTournamentList && troupe.characterIds.size == req
                        } else true

                        val troupeCharacters = if (selectionMode) {
                            state.characters.filter { it.id in troupe.characterIds }
                        } else null

                        TroupeListItem(
                            troupe = troupe,
                            onClick = {
                                if (selectionMode) {
                                    if (isValid) onTroupeSelected(troupe)
                                    else { viewModel.onEvent(CharacterEvent.EditTroupe(troupe)); onEditTroupe() }
                                } else if (troupe.id != -1) { viewModel.onEvent(CharacterEvent.EditTroupe(troupe)); onEditTroupe() }
                            },
                            onDelete = { if (troupe.id != -1) troupeToDelete = troupe },
                            onQr = { if (troupe.id != -1) showQrForTroupe = troupe },
                            onShare = {
                                val code = if (troupe.id != -1) viewModel.generateFullShareCode(troupe, state.characters, state.upgradeCards) else "DUMMY_CODE"
                                shareTroupe(context, troupe.troupeName, code)
                            },
                            onEdit = { viewModel.onEvent(CharacterEvent.EditTroupe(troupe)); onEditTroupe() },
                            isDimmed = selectionMode && !isValid,
                            selectionMode = selectionMode,
                            characters = troupeCharacters,
                            onPositioned = onTargetPositioned
                        )
                    }
                }
            }
            
            if (troupeToDelete != null) {
                AlertDialog(onDismissRequest = { troupeToDelete = null }, title = { Text("Delete Troupe") }, text = { Text("Are you sure?") }, confirmButton = { TextButton(onClick = { troupeToDelete?.let { viewModel.onEvent(CharacterEvent.DeleteTroupe(it)) }; troupeToDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { troupeToDelete = null }) { Text("Cancel") } })
            }

            if (showQrForTroupe != null) {
                val shareCode = viewModel.generateFullShareCode(showQrForTroupe!!, state.characters, state.upgradeCards)
                QrCodeDialog(troupeName = showQrForTroupe!!.troupeName, shareCode = shareCode, onDismiss = { showQrForTroupe = null })
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
    onQr: () -> Unit = {},
    onShare: () -> Unit,
    onEdit: () -> Unit,
    isDimmed: Boolean = false,
    selectionMode: Boolean = false,
    showDelete: Boolean = true,
    characters: List<Character>? = null,
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val theme = LocalAppThemeProperties.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = theme.screenPadding, vertical = theme.verticalSpacing / 8).alpha(if (isDimmed) 0.5f else 1.0f).clickable { onClick() },
        shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(theme.cardContentPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (troupe.isCampaignTroupe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.HistoryEdu, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text(
                        text = "${troupe.characterIds.size} Characters",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectionMode) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) }
                } else {
                    IconButton(onClick = onQr, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.QrCode, contentDescription = "Show QR", modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp).onGloballyPositioned { onPositioned("ShareTroupe", it) }) { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp)) }
                }
                if (showDelete) IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).onGloballyPositioned { onPositioned("DeleteTroupe", it) }) { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
            }
            if (!characters.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))
                characters.forEach { character ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun shareTroupe(context: Context, name: String, code: String) {
    val intent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, code); type = "text/plain" }
    context.startActivity(Intent.createChooser(intent, null))
}
