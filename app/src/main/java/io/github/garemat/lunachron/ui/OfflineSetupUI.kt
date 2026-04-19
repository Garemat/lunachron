package io.github.garemat.lunachron.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

// ── Slot state ────────────────────────────────────────────────────────────────

private data class SlotData(
    val troupe: Troupe? = null,
    val selectedCharIds: List<Int> = emptyList(),
    val pickerOpen: Boolean = false
)

private enum class LocalSetupStep { PLAYER_COUNT, TROUPE_SELECTION, READY }

private fun maxCharsForCount(playerCount: Int) = when (playerCount) {
    3 -> 4; 4 -> 3; else -> 6
}

private fun slotStatus(slot: SlotData, max: Int): String = when {
    slot.troupe == null -> "empty"
    slot.troupe.characterIds.size <= max -> "confirmed"
    slot.selectedCharIds.size >= max -> "confirmed"
    else -> "trimming"
}

private fun finalCharIds(slot: SlotData, max: Int): List<Int> {
    val t = slot.troupe ?: return emptyList()
    return if (t.characterIds.size <= max) t.characterIds else slot.selectedCharIds
}

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun OfflineSetupUI(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onStartGame: () -> Unit,
    onScanRequest: (Int) -> Unit,
    onNavigateToAddEditTroupe: () -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    var step by rememberSaveable { mutableStateOf(LocalSetupStep.PLAYER_COUNT) }
    var playerCount by rememberSaveable { mutableIntStateOf(2) }
    val slots = remember { mutableStateListOf(SlotData(), SlotData(), SlotData(), SlotData()) }
    var troupeToSave by remember { mutableStateOf<Pair<Int, Troupe>?>(null) }
    var saveNameDraft by remember { mutableStateOf("") }
    var showQrForTroupe by remember { mutableStateOf<Troupe?>(null) }

    LaunchedEffect(Unit) {
        viewModel.scannedTroupeEvent.collect { (index, troupe) ->
            if (index < slots.size) {
                val max = maxCharsForCount(playerCount)
                slots[index] = if (troupe.characterIds.size <= max)
                    SlotData(troupe = troupe)
                else
                    SlotData(troupe = troupe, pickerOpen = true)
            }
        }
    }

    LaunchedEffect(playerCount) {
        viewModel.uiEvent.collect { event ->
            if (event is CharacterViewModel.UiEvent.TroupeCreated) {
                val index = event.playerIndex ?: return@collect
                if (index < 0 || index >= 4) return@collect
                val t = event.troupe
                if (!t.isTournamentList) {
                    val max = maxCharsForCount(playerCount)
                    slots[index] = if (t.characterIds.size <= max)
                        SlotData(troupe = t)
                    else
                        SlotData(troupe = t, pickerOpen = true)
                }
            }
        }
    }

    when (step) {
        LocalSetupStep.PLAYER_COUNT -> PlayerCountStep(
            playerCount = playerCount,
            onCountSelected = { count ->
                playerCount = count
                for (i in count until 4) slots[i] = SlotData()
            },
            onBack = onBack,
            onContinue = { step = LocalSetupStep.TROUPE_SELECTION },
            onPositioned = onPositioned
        )
        LocalSetupStep.TROUPE_SELECTION -> TroupeSelectionStep(
            state = state,
            viewModel = viewModel,
            playerCount = playerCount,
            slots = slots,
            onScanRequest = onScanRequest,
            onNavigateToAddEditTroupe = onNavigateToAddEditTroupe,
            onBack = { step = LocalSetupStep.PLAYER_COUNT },
            onContinue = { step = LocalSetupStep.READY },
            onPositioned = onPositioned,
            onRequestSave = { index, troupe ->
                troupeToSave = index to troupe
                saveNameDraft = troupe.troupeName
            },
            onShowQr = { troupe -> showQrForTroupe = troupe }
        )
        LocalSetupStep.READY -> ReadyStep(
            state = state,
            playerCount = playerCount,
            slots = slots,
            onBack = { step = LocalSetupStep.TROUPE_SELECTION },
            onStartGame = {
                val max = maxCharsForCount(playerCount)
                val troupes = (0 until playerCount).map { i ->
                    val slot = slots[i]
                    slot.troupe!!.copy(characterIds = finalCharIds(slot, max))
                }
                viewModel.startNewGame(troupes)
                onStartGame()
            },
            onPositioned = onPositioned
        )
    }

    if (showQrForTroupe != null) {
        val shareCode = viewModel.generateFullShareCode(showQrForTroupe!!, state.characters, state.upgradeCards)
        QrCodeDialog(
            troupeName = showQrForTroupe!!.troupeName,
            shareCode = shareCode,
            onDismiss = { showQrForTroupe = null }
        )
    }

    if (troupeToSave != null) {
        AlertDialog(
            onDismissRequest = { troupeToSave = null },
            title = { Text("Save Troupe") },
            text = {
                Column {
                    Text("Enter a name for this troupe:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = saveNameDraft,
                        onValueChange = { saveNameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Troupe Name") },
                        singleLine = true,
                        shape = LocalAppThemeProperties.current.cardShape
                    )
                }
            },
            confirmButton = {
                val theme = LocalAppThemeProperties.current
                Button(
                    onClick = {
                        troupeToSave?.let { (_, troupe) ->
                            viewModel.saveTroupe(troupe.copy(troupeName = saveNameDraft))
                        }
                        troupeToSave = null
                    },
                    enabled = saveNameDraft.isNotBlank(),
                    shape = LocalAppThemeProperties.current.cardShape
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { troupeToSave = null }) { Text("Cancel") } }
        )
    }
}

// ── Step pips ─────────────────────────────────────────────────────────────────

@Composable
private fun StepPips(activeIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .height(5.dp)
                    .width(if (i == activeIndex) 14.dp else 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (i == activeIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

// ── Step 1: Player Count ──────────────────────────────────────────────────────

@Composable
private fun PlayerCountStep(
    playerCount: Int,
    onCountSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            StepPips(0)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = theme.screenPadding)
        ) {
            Text("How many players?", style = theme.titleStyle.copy(fontSize = 22.sp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Sets the character limit per troupe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(theme.verticalSpacing))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { onPositioned("PlayerCount", it) },
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayerCountCard(
                        count = 1, label = "Solo", limit = "up to 6 chars",
                        selected = playerCount == 1, onClick = { onCountSelected(1) },
                        modifier = Modifier.weight(1f)
                    )
                    PlayerCountCard(
                        count = 2, label = "Duel", limit = "up to 6 chars",
                        selected = playerCount == 2, onClick = { onCountSelected(2) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayerCountCard(
                        count = 3, label = "Three-way", limit = "up to 4 chars",
                        selected = playerCount == 3, onClick = { onCountSelected(3) },
                        modifier = Modifier.weight(1f)
                    )
                    PlayerCountCard(
                        count = 4, label = "Full Table", limit = "up to 3 chars",
                        selected = playerCount == 4, onClick = { onCountSelected(4) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = theme.cardShape
            ) {
                Text("Continue", style = theme.buttonTextStyle)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(theme.verticalSpacing))
        }
    }
}

@Composable
private fun PlayerCountCard(
    count: Int,
    label: String,
    limit: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeProperties.current
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        onClick = onClick,
        modifier = modifier.border(if (selected) 2.dp else 1.dp, borderColor, theme.cardShape),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = count.toString(),
                style = theme.titleStyle.copy(fontSize = 32.sp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            PlayerCountShape(count = count, selected = selected)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = limit,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlayerCountShape(count: Int, selected: Boolean) {
    val barColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                   else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Box(modifier = Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.BottomCenter) {
        when (count) {
            4 -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(2) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(barColor))
                    }
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(2) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(barColor))
                    }
                }
            }
            else -> {
                val heights = when (count) {
                    1 -> listOf(36f)
                    2 -> listOf(36f, 36f)
                    3 -> listOf(28f, 36f, 28f)
                    else -> listOf(36f)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    heights.forEach { h ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(h.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor)
                        )
                    }
                }
            }
        }
    }
}

// ── Step 2: Troupe Selection ──────────────────────────────────────────────────

@Composable
private fun TroupeSelectionStep(
    state: CharacterState,
    viewModel: CharacterViewModel,
    playerCount: Int,
    slots: SnapshotStateList<SlotData>,
    onScanRequest: (Int) -> Unit,
    onNavigateToAddEditTroupe: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit,
    onRequestSave: (Int, Troupe) -> Unit,
    onShowQr: (Troupe) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val max = maxCharsForCount(playerCount)
    val allConfirmed = (0 until playerCount).all { slotStatus(slots[it], max) == "confirmed" }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            StepPips(1)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = theme.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = theme.verticalSpacing)
        ) {
            item {
                Text(
                    text = "Each player needs a troupe  ·  $max char${if (max > 1) "s" else ""} max this game",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            itemsIndexed(List(playerCount) { slots[it] }) { index, slot ->
                val playerName = if (index == 0) state.name.ifEmpty { "Player 1" } else "Player ${index + 1}"
                val showSave = slot.troupe != null &&
                    state.troupes.none { it.shareCode == slot.troupe.shareCode && slot.troupe.shareCode.isNotEmpty() }
                TroupeSlotCard(
                    index = index,
                    slot = slot,
                    maxAllowed = max,
                    characters = state.characters,
                    troupes = state.troupes,
                    playerName = playerName,
                    showSave = showSave,
                    onTroupeSelected = { t ->
                        slots[index] = if (t.characterIds.size <= max)
                            SlotData(troupe = t)
                        else
                            SlotData(troupe = t, pickerOpen = true)
                    },
                    onToggleChar = { charId ->
                        val s = slots[index]
                        val current = s.selectedCharIds.toMutableList()
                        if (charId in current) {
                            current.remove(charId)
                            slots[index] = s.copy(selectedCharIds = current)
                        } else if (current.size < max) {
                            current.add(charId)
                            val doneNow = current.size >= max
                            slots[index] = s.copy(selectedCharIds = current, pickerOpen = !doneNow)
                        }
                    },
                    onTogglePicker = { slots[index] = slots[index].copy(pickerOpen = !slots[index].pickerOpen) },
                    onScanRequest = { onScanRequest(index) },
                    onCreateNew = {
                        viewModel.editingTroupeId = null
                        viewModel.pendingTroupePlayerIndex = index
                        onNavigateToAddEditTroupe()
                    },
                    onEditTroupe = { t ->
                        viewModel.onEvent(CharacterEvent.EditTroupe(t))
                        viewModel.pendingTroupePlayerIndex = index
                        onNavigateToAddEditTroupe()
                    },
                    onShowQr = { t -> onShowQr(t) },
                    onRequestSave = { t -> onRequestSave(index, t) },
                    onPositioned = if (index == 0) onPositioned else { _, _ -> }
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = theme.screenPadding).padding(bottom = theme.verticalSpacing)) {
            Button(
                onClick = onContinue,
                enabled = allConfirmed,
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .onGloballyPositioned { onPositioned("StartBattleButton", it) },
                shape = theme.cardShape
            ) {
                Text("Review & Start", style = theme.buttonTextStyle)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TroupeSlotCard(
    index: Int,
    slot: SlotData,
    maxAllowed: Int,
    characters: List<Character>,
    troupes: List<Troupe>,
    playerName: String,
    showSave: Boolean,
    onTroupeSelected: (Troupe) -> Unit,
    onToggleChar: (Int) -> Unit,
    onTogglePicker: () -> Unit,
    onScanRequest: () -> Unit,
    onCreateNew: () -> Unit,
    onEditTroupe: (Troupe) -> Unit,
    onShowQr: (Troupe) -> Unit,
    onRequestSave: (Troupe) -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val status = slotStatus(slot, maxAllowed)
    val factionColor = slot.troupe?.let { getFactionColor(it.faction) }
    var showBrowseSheet by remember { mutableStateOf(false) }

    // Pulsing amber border for trimming
    val infiniteTransition = rememberInfiniteTransition(label = "slotBorder$index")
    val amberAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amberAlpha$index"
    )

    val borderColor = when (status) {
        "confirmed" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        "trimming" -> Color(0xFFF57C00).copy(alpha = amberAlpha)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(
            width = if (status == "empty") 1.dp else 1.5.dp,
            color = borderColor,
            shape = theme.cardShape
        ),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // ── Slot header ───────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "PLAYER ${index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (factionColor != null && slot.troupe != null) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(factionColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = slot.troupe.troupeName,
                        style = theme.headerStyle.copy(fontSize = 14.sp),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "No troupe selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Save / QR actions on confirmed slots
                if (slot.troupe != null) {
                    if (showSave) {
                        IconButton(
                            onClick = { onRequestSave(slot.troupe) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(
                        onClick = { onShowQr(slot.troupe) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR", modifier = Modifier.size(16.dp))
                    }
                }
                // Status icon
                when (status) {
                    "confirmed" -> Icon(
                        Icons.Default.CheckCircle, contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    "trimming" -> Text("⚠", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp))
                    else -> Icon(
                        Icons.Default.RadioButtonUnchecked, contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }

            // ── Slot body ─────────────────────────────────────────────────────
            when (status) {
                "empty" -> SlotEmptyBody(
                    onBrowse = { showBrowseSheet = true },
                    onScanRequest = onScanRequest,
                    onPositioned = onPositioned
                )
                "trimming" -> SlotTrimmingBody(
                    slot = slot,
                    maxAllowed = maxAllowed,
                    characters = characters,
                    onTogglePicker = onTogglePicker,
                    onToggleChar = onToggleChar
                )
                "confirmed" -> SlotConfirmedBody(
                    slot = slot,
                    maxAllowed = maxAllowed,
                    characters = characters,
                    onTogglePicker = onTogglePicker,
                    onToggleChar = onToggleChar
                )
            }
        }
    }

    if (showBrowseSheet) {
        TroupeBrowseSheet(
            troupes = troupes,
            characters = characters,
            onTroupeSelected = { t ->
                onTroupeSelected(t)
                showBrowseSheet = false
            },
            onCreateNew = {
                onCreateNew()
                showBrowseSheet = false
            },
            onDismiss = { showBrowseSheet = false }
        )
    }
}

@Composable
private fun SlotEmptyBody(
    onBrowse: () -> Unit,
    onScanRequest: () -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    Row(
        modifier = Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .onGloballyPositioned { onPositioned("TroupeSelector", it) },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onBrowse,
            modifier = Modifier.weight(1f),
            shape = theme.cardShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Browse", style = theme.buttonTextStyle.copy(fontSize = 13.sp))
        }
        OutlinedButton(
            onClick = onScanRequest,
            modifier = Modifier.weight(1f),
            shape = theme.cardShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Scan QR", style = theme.buttonTextStyle.copy(fontSize = 13.sp))
        }
    }
}

@Composable
private fun SlotTrimmingBody(
    slot: SlotData,
    maxAllowed: Int,
    characters: List<Character>,
    onTogglePicker: () -> Unit,
    onToggleChar: (Int) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val totalChars = slot.troupe?.characterIds?.size ?: 0
    val amber = Color(0xFFF57C00)

    Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(theme.cardShape)
                .background(amber.copy(alpha = 0.1f))
                .border(1.dp, amber.copy(alpha = 0.35f), theme.cardShape)
                .clickable { onTogglePicker() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Select $maxAllowed of $totalChars characters",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFFFA040),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (slot.pickerOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFFFFA040)
            )
        }

        AnimatedVisibility(visible = slot.pickerOpen) {
            CharacterPickerRow(
                troupe = slot.troupe!!,
                selectedCharIds = slot.selectedCharIds,
                maxAllowed = maxAllowed,
                characters = characters,
                onToggleChar = onToggleChar
            )
        }
    }
}

@Composable
private fun SlotConfirmedBody(
    slot: SlotData,
    maxAllowed: Int,
    characters: List<Character>,
    onTogglePicker: () -> Unit,
    onToggleChar: (Int) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val wasTrimmed = (slot.troupe?.characterIds?.size ?: 0) > maxAllowed
    val charIds = finalCharIds(slot, maxAllowed)
    val charNames = charIds.mapNotNull { id -> characters.find { it.id == id }?.name }

    Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = charNames.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "✓ ${charIds.size}/$maxAllowed",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (wasTrimmed) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { onTogglePicker() }
                ) {
                    Text(
                        text = "✎ Edit",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Re-opened character picker (edit mode for already-trimmed slots)
        if (wasTrimmed && slot.pickerOpen) {
            CharacterPickerRow(
                troupe = slot.troupe!!,
                selectedCharIds = slot.selectedCharIds,
                maxAllowed = maxAllowed,
                characters = characters,
                onToggleChar = onToggleChar
            )
        }
    }
}

// ── Character picker row ──────────────────────────────────────────────────────

@Composable
private fun CharacterPickerRow(
    troupe: Troupe,
    selectedCharIds: List<Int>,
    maxAllowed: Int,
    characters: List<Character>,
    onToggleChar: (Int) -> Unit
) {
    val troupeChars = remember(troupe, characters) {
        troupe.characterIds.mapNotNull { id -> characters.find { it.id == id } }
    }
    val done = selectedCharIds.size >= maxAllowed

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tap to select",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${selectedCharIds.size}/$maxAllowed",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (done) MaterialTheme.colorScheme.primary else Color(0xFFF57C00)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(troupeChars) { char ->
                val isOn = char.id in selectedCharIds
                val isOff = !isOn && selectedCharIds.size >= maxAllowed
                CharacterBubble(
                    character = char,
                    isOn = isOn,
                    isOff = isOff,
                    onClick = { if (!isOff) onToggleChar(char.id) }
                )
            }
        }
    }
}

@Composable
private fun CharacterBubble(
    character: Character,
    isOn: Boolean,
    isOff: Boolean,
    onClick: () -> Unit
) {
    val initials = remember(character.name) {
        character.name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    }
    Column(
        modifier = Modifier.clickable(enabled = !isOff, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isOn -> MaterialTheme.colorScheme.primaryContainer
                        isOff -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .border(
                    width = 2.dp,
                    color = when {
                        isOn -> MaterialTheme.colorScheme.primary
                        isOff -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            CharacterPortrait(
                character = character,
                size = 44.dp,
                shape = CircleShape
            )
        }
        Text(
            text = character.name.split(" ").first(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isOff) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 44.dp),
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Step 3: Ready ─────────────────────────────────────────────────────────────

@Composable
private fun ReadyStep(
    state: CharacterState,
    playerCount: Int,
    slots: SnapshotStateList<SlotData>,
    onBack: () -> Unit,
    onStartGame: () -> Unit,
    onPositioned: (String, LayoutCoordinates) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val max = maxCharsForCount(playerCount)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            StepPips(2)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = theme.screenPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = theme.verticalSpacing)
        ) {
            item {
                Text(
                    "All Set!",
                    style = theme.titleStyle.copy(fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Review your lineup before kickoff",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(List(playerCount) { slots[it] }) { index, slot ->
                if (slot.troupe != null) {
                    val playerName = if (index == 0) state.name.ifEmpty { "Player 1" } else "Player ${index + 1}"
                    val charIds = finalCharIds(slot, max)
                    val charNames = charIds.mapNotNull { id -> state.characters.find { it.id == id }?.name }
                    val factionColor = getFactionColor(slot.troupe.faction)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .border(
                                    width = 3.dp,
                                    color = factionColor.copy(alpha = 0.6f),
                                    shape = theme.cardShape
                                )
                                .padding(14.dp, 12.dp)
                        ) {
                            Text(
                                text = playerName.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.8.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = slot.troupe.troupeName,
                                style = theme.titleStyle.copy(fontSize = 16.sp),
                                color = factionColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = charNames.joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = theme.screenPadding).padding(bottom = theme.verticalSpacing)) {
            Button(
                onClick = onStartGame,
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .onGloballyPositioned { onPositioned("StartBattleButton", it) },
                shape = theme.cardShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("⚔  Start Game", style = theme.buttonTextStyle.copy(fontSize = 15.sp))
            }
        }
    }
}

// ── Troupe browse bottom sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TroupeBrowseSheet(
    troupes: List<Troupe>,
    characters: List<Character>,
    onTroupeSelected: (Troupe) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val theme = LocalAppThemeProperties.current
        Text(
            "Choose a Troupe",
            style = theme.titleStyle.copy(fontSize = 18.sp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Create new option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCreateNew() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                "Create New Troupe",
                style = theme.headerStyle.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (troupes.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(troupes) { troupe ->
                    TroupeSheetRow(troupe = troupe, characters = characters, onClick = { onTroupeSelected(troupe) })
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No saved troupes yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TroupeSheetRow(
    troupe: Troupe,
    characters: List<Character>,
    onClick: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val factionColor = getFactionColor(troupe.faction)
    val troupeChars = remember(troupe.characterIds, characters) {
        troupe.characterIds.mapNotNull { id -> characters.find { it.id == id } }
    }
    val factionLabel = troupe.faction.name.lowercase().replaceFirstChar { it.uppercase() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Faction colour bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (troupeChars.isEmpty()) 36.dp else 56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(factionColor)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = troupe.troupeName,
                style = theme.headerStyle.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$factionLabel · ${troupe.characterIds.size} characters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (troupeChars.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    troupeChars.forEach { char ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            CharacterPortrait(character = char, size = 28.dp, shape = CircleShape)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
