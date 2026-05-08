package io.github.garemat.lunachron.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlin.math.max
import kotlin.math.roundToInt


// ─── Supporting types ────────────────────────────────────────────────────────

data class SummonerPickerState(
    val playerIndex: Int,
    val character: Character,
    val possibleSummoners: List<Character>
)

sealed class StoneSource {
    data object Pot : StoneSource()
    data class Character(val playerIndex: Int, val charIndex: Int) : StoneSource()
}

private sealed class GameGridItem {
    data class Header(val playerIndex: Int, val troupe: Troupe) : GameGridItem()
    data class CharItem(
        val playerIndex: Int,
        val charIndex: Int,
        val character: Character,
        val isSummoned: Boolean,
        val summoner: Character?
    ) : GameGridItem()
}

// ─── Adaptive grid helpers ───────────────────────────────────────────────────

private fun adaptiveCols(totalChars: Int) = if (totalChars <= 8) 2 else 3

private fun adaptivePortraitSize(totalChars: Int) = if (totalChars <= 8) 120.dp else 80.dp

/** Formats a stat value: positive values get a "+" prefix for quick visual scanning. */
private fun statSign(value: Int): String = if (value > 0) "+$value" else "$value"

/**
 * Fades the four edges of a portrait to transparent, mimicking the organic irregular edges
 * that naturally-trimmed character PNGs already have. Uses an offscreen compositing layer
 * so the four directional DstIn gradient passes combine correctly.
 */
private fun Modifier.portraitCrimpedEdge(fadePercent: Float = 0.15f): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fw = size.width * fadePercent
            val fh = size.height * fadePercent
            // Each pass fades one edge; the gradient clamps to full-opacity beyond its range
            // so it acts as a pure pass-through for the other edges.
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, fh), blendMode = BlendMode.DstIn)
            drawRect(brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - fh, size.height), blendMode = BlendMode.DstIn)
            drawRect(brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fw), blendMode = BlendMode.DstIn)
            drawRect(brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - fw, size.width), blendMode = BlendMode.DstIn)
        }

// ─── Main screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveGameScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    players: List<Pair<Troupe, List<Character>>>,
    onQuitGame: () -> Unit,
    isTutorialActive: Boolean = false,
    currentTutorialStep: TutorialStep? = null,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val theme = LocalAppThemeProperties.current
    val gridState = rememberLazyGridState()

    // Tutorial restricts to a single demo player
    val activePlayers = remember(isTutorialActive, state.characters, players) {
        if (isTutorialActive && state.characters.isNotEmpty()) {
            val grub = state.characters.find { it.name.equals("Grub", ignoreCase = true) } ?: state.characters[0]
            val gotchgut = state.characters.find { it.name.equals("Gotchgut", ignoreCase = true) } ?: state.characters.getOrNull(1) ?: state.characters[0]
            val boulder = state.characters.find { it.name.equals("Boulder", ignoreCase = true) } ?: state.characters.getOrNull(2) ?: state.characters[0]
            val exampleTroupe = Troupe(troupeName = "Example Dominion", faction = Faction.DOMINION, characterIds = listOf(grub.id, gotchgut.id, boulder.id), shareCode = "")
            listOf(exampleTroupe to listOf(grub, gotchgut, boulder))
        } else if (isTutorialActive) {
            listOf(Troupe(troupeName = "Example Dominion", faction = Faction.DOMINION, characterIds = emptyList(), shareCode = "") to emptyList<Character>())
        } else players
    }

    // Index of the local player (for pool bar and summon panel)
    val localPlayerIndex = remember(state.gameSession, state.deviceId) {
        if (state.gameSession == null) 0
        else state.gameSession.players.indexOfFirst { it.deviceId == state.deviceId }.coerceAtLeast(0)
    }

    // Build flat list of grid items (headers + characters including summons)
    val allGridItems: List<GameGridItem> = remember(activePlayers, state.activeSummons, state.characters) {
        buildList {
            activePlayers.forEachIndexed { pi, (troupe, chars) ->
                if (activePlayers.size > 1) add(GameGridItem.Header(pi, troupe))
                val troupeCharIds = troupe.characterIds.toSet()
                val baseChars = chars.filter { it.id in troupeCharIds }
                val summonEntries = state.activeSummons[pi] ?: emptyList()
                val summonedChars = summonEntries.mapNotNull { e -> state.characters.find { it.id == e.characterId } }
                (baseChars + summonedChars).forEachIndexed { ci, char ->
                    val isSummoned = ci >= baseChars.size
                    val summonEntry = if (isSummoned) summonEntries.getOrNull(ci - baseChars.size) else null
                    val summoner = summonEntry?.summonedByCharacterId?.let { id -> baseChars.find { it.id == id } }
                    add(GameGridItem.CharItem(pi, ci, char, isSummoned, summoner))
                }
            }
        }
    }

    val totalChars = allGridItems.filterIsInstance<GameGridItem.CharItem>().size
    val cols = adaptiveCols(totalChars)
    val portraitSize = adaptivePortraitSize(totalChars)

    val trackingMode = state.gameTrackingMode
    val isFullTracking = trackingMode == GameTrackingMode.FULL_TRACKING

    // Moonstone drag state
    var draggingStoneSource by remember { mutableStateOf<StoneSource?>(null) }
    var draggingStoneIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val characterBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val potBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val stateRef = rememberUpdatedState(state)

    // Summon panel state
    var isSummonPanelOpen by rememberSaveable { mutableStateOf(false) }
    var summonerPickerState by remember { mutableStateOf<SummonerPickerState?>(null) }

    // Card modal state
    var selectedCharInfo by remember { mutableStateOf<Triple<Int, Int, Character>?>(null) }
    var showEndGameConfirm by remember { mutableStateOf(false) }

    // Accumulating toast state
    val accMap = remember { mutableStateMapOf<String, Pair<Int, String>>() }
    val accJobs = remember { mutableMapOf<String, Job>() }
    var visibleToastKey by remember { mutableStateOf<String?>(null) }

    fun recordDelta(key: String, delta: Int, label: String) {
        val prev = accMap[key]?.first ?: 0
        accMap[key] = Pair(prev + delta, label)
        visibleToastKey = key
        accJobs[key]?.cancel()
        accJobs[key] = scope.launch {
            delay(2000)
            accMap.remove(key)
            if (visibleToastKey == key) visibleToastKey = null
            accJobs.remove(key)
        }
    }

    LaunchedEffect(currentTutorialStep) {
        if (currentTutorialStep?.targetTag == "CharacterDrawerButton") isSummonPanelOpen = true
        else if (isTutorialActive && currentTutorialStep?.targetTag != "CharacterDrawerButton") isSummonPanelOpen = false
    }

    // Local player display chars (for pool bar)
    val localDisplayChars = remember(localPlayerIndex, activePlayers, state.activeSummons, state.characters) {
        val pair = activePlayers.getOrNull(localPlayerIndex) ?: return@remember emptyList<Character>()
        val troupeCharIds = pair.first.characterIds.toSet()
        val baseChars = pair.second.filter { it.id in troupeCharIds }
        val summonEntries = state.activeSummons[localPlayerIndex] ?: emptyList()
        val summonedChars = summonEntries.mapNotNull { e -> state.characters.find { it.id == e.characterId } }
        baseChars + summonedChars
    }
    val localBaseChars = remember(localPlayerIndex, activePlayers) {
        val pair = activePlayers.getOrNull(localPlayerIndex) ?: return@remember emptyList<Character>()
        val troupeCharIds = pair.first.characterIds.toSet()
        pair.second.filter { it.id in troupeCharIds }
    }
    val localSummonEntries = state.activeSummons[localPlayerIndex] ?: emptyList()

    Scaffold(
        topBar = {
            Column {
                ActiveGameTopBar(
                    state = state,
                    viewModel = viewModel,
                    isTutorialActive = isTutorialActive,
                    isSummonPanelOpen = isSummonPanelOpen,
                    onDrawerToggle = { isSummonPanelOpen = !isSummonPanelOpen },
                    onEndGameClick = { showEndGameConfirm = true },
                    onQuitGame = onQuitGame,
                    onTargetPositioned = onTargetPositioned
                )
            }
        },
        bottomBar = {
            if (isFullTracking) {
                CollapsiblePoolBar(
                    state = state,
                    pageIndex = localPlayerIndex,
                    allDisplayChars = localDisplayChars,
                    isTutorialActive = isTutorialActive,
                    potBounds = potBounds,
                    onTargetPositioned = onTargetPositioned,
                    onDragStart = { offset ->
                        draggingStoneSource = StoneSource.Pot
                        dragPosition = (potBounds.value?.topLeft ?: Offset.Zero) + offset
                    },
                    onDrag = { dragPosition += it },
                    onDragEnd = {
                        if (!isTutorialActive) {
                            characterBounds.entries.find { it.value.contains(dragPosition) }?.let { target ->
                                val (tP, tC) = target.key.split("_").map { it.toInt() }
                                val currentStones = stateRef.value.characterPlayStates[target.key]?.moonstones ?: 0
                                if (currentStones < 7) viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(tP, tC, currentStones + 1))
                            }
                        }
                        draggingStoneSource = null
                    },
                    viewModel = viewModel
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).onGloballyPositioned { rootLayoutCoordinates = it }) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                state = gridState,
                contentPadding = PaddingValues(theme.screenPadding),
                verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
                horizontalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                for (gridItem in allGridItems) {
                    when (gridItem) {
                        is GameGridItem.Header -> item(span = { GridItemSpan(maxLineSpan) }) {
                            GamePlayerSectionHeader(troupe = gridItem.troupe)
                        }
                        is GameGridItem.CharItem -> {
                            val pi = gridItem.playerIndex
                            val ci = gridItem.charIndex
                            val char = gridItem.character
                            val stateKey = "${pi}_${ci}"
                            item(key = stateKey) {
                                val ps = if (isTutorialActive) {
                                    CharacterPlayState(currentHealth = char.health, moonstones = if (ci == 0) 2 else 0)
                                } else {
                                    state.characterPlayStates[stateKey] ?: CharacterPlayState(currentHealth = char.health)
                                }
                                DisposableEffect(stateKey) { onDispose { characterBounds.remove(stateKey) } }
                                Box(modifier = Modifier.onGloballyPositioned { characterBounds[stateKey] = it.boundsInWindow() }) {
                                    CharacterPortraitCell(
                                        character = char,
                                        playState = ps,
                                        trackingMode = trackingMode,
                                        summoner = gridItem.summoner,
                                        isEditable = !isTutorialActive,
                                        onTap = { selectedCharInfo = Triple(pi, ci, char) },
                                        onHpBadgeTap = {
                                            if (!isTutorialActive) {
                                                val newHp = max(0, ps.currentHealth - 1)
                                                viewModel.onEvent(CharacterEvent.UpdateCharacterHealth(pi, ci, newHp))
                                                if (newHp == 0 && ps.moonstones > 0) {
                                                    viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(pi, ci, 0))
                                                }
                                                recordDelta("${pi}_${ci}_hp", -1, "HP")
                                            }
                                        },
                                        onEnergyBadgeTap = {
                                            if (!isTutorialActive) {
                                                viewModel.onEvent(CharacterEvent.UpdateCharacterEnergy(pi, ci, max(0, ps.currentEnergy - 1)))
                                                recordDelta("${pi}_${ci}_en", -1, "Energy")
                                            }
                                        },
                                        onMoonstoneBadgeTap = {
                                            if (!isTutorialActive && ps.currentHealth > 0) {
                                                viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(pi, ci, ps.moonstones + 1))
                                                recordDelta("${pi}_${ci}_ms", +1, "Stones")
                                            }
                                        },
                                        onStoneDragStart = { index, pos ->
                                            draggingStoneIndex = index
                                            draggingStoneSource = StoneSource.Character(pi, ci)
                                            dragPosition = pos
                                        },
                                        onStoneDrag = { dragPosition += it },
                                        onStoneDragEnd = {
                                            val source = draggingStoneSource as? StoneSource.Character
                                            if (source != null && !isTutorialActive) {
                                                if (potBounds.value?.contains(dragPosition) == true) {
                                                    val s = stateRef.value.characterPlayStates["${source.playerIndex}_${source.charIndex}"]?.moonstones ?: 0
                                                    if (s > 0) viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(source.playerIndex, source.charIndex, s - 1))
                                                } else {
                                                    characterBounds.entries.find { it.value.contains(dragPosition) }?.let { target ->
                                                        val (tP, tC) = target.key.split("_").map { it.toInt() }
                                                        if (tP != source.playerIndex || tC != source.charIndex) {
                                                            val sStones = stateRef.value.characterPlayStates["${source.playerIndex}_${source.charIndex}"]?.moonstones ?: 0
                                                            val tStones = stateRef.value.characterPlayStates[target.key]?.moonstones ?: 0
                                                            if (tStones < 7) {
                                                                viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(source.playerIndex, source.charIndex, sStones - 1))
                                                                viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(tP, tC, tStones + 1))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            draggingStoneSource = null
                                            draggingStoneIndex = -1
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Summon panel slides in from the start edge
            AnimatedVisibility(
                visible = isSummonPanelOpen,
                enter = expandHorizontally(),
                exit = shrinkHorizontally(),
                modifier = Modifier.fillMaxHeight()
            ) {
                SummonPanel(
                    allChars = state.characters,
                    baseChars = localBaseChars,
                    summonEntries = localSummonEntries,
                    trackingMode = trackingMode,
                    onAdd = { char ->
                        handleSummonAdd(localPlayerIndex, char, localBaseChars, trackingMode, viewModel) {
                            summonerPickerState = it
                        }
                    },
                    onRemove = { char ->
                        viewModel.onEvent(CharacterEvent.RemoveSummonedCharacter(localPlayerIndex, char.id))
                    }
                )
            }

            // Dragging stone overlay
            if (draggingStoneSource != null) {
                val localPos = rootLayoutCoordinates?.windowToLocal(dragPosition) ?: dragPosition
                Box(
                    modifier = Modifier
                        .offset { IntOffset(localPos.x.roundToInt() - 20.dp.toPx().toInt(), localPos.y.roundToInt() - 20.dp.toPx().toInt()) }
                        .zIndex(1000f)
                ) {
                    MoonstoneIcon(size = 40.dp)
                }
            }

            // Accumulating toast chip
            val toastData = visibleToastKey?.let { accMap[it] }
            if (toastData != null) {
                val (delta, label) = toastData
                val sign = if (delta > 0) "+" else ""
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .zIndex(50f)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        tonalElevation = 6.dp
                    ) {
                        Text(
                            text = "$sign$delta $label",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Card modal
            selectedCharInfo?.let { (pi, ci, char) ->
                val ps = if (isTutorialActive) {
                    CharacterPlayState(currentHealth = char.health)
                } else {
                    state.characterPlayStates["${pi}_${ci}"] ?: CharacterPlayState(currentHealth = char.health)
                }
                GameCharacterCardModal(
                    character = char,
                    playState = ps,
                    trackingMode = trackingMode,
                    isEditable = !isTutorialActive,
                    onEnergyChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterEnergy(pi, ci, it)) },
                    onAbilityToggle = { name, used -> viewModel.onEvent(CharacterEvent.ToggleAbilityUsed(pi, ci, name, used)) },
                    onFlip = { viewModel.onEvent(CharacterEvent.ToggleCharacterFlipped(pi, ci, !ps.isFlipped)) },
                    onTransform = if (char.transformsInto != null) { targetId ->
                        viewModel.onEvent(CharacterEvent.TransformCharacter(pi, ci, targetId))
                        selectedCharInfo = null
                    } else null,
                    onDismiss = { selectedCharInfo = null }
                )
            }

            // Summoner picker dialog
            summonerPickerState?.let { pickerState ->
                SummonerPickerDialog(
                    character = pickerState.character,
                    possibleSummoners = pickerState.possibleSummoners,
                    onSelect = { summonerId ->
                        viewModel.onEvent(CharacterEvent.AddSummonedCharacter(pickerState.playerIndex, pickerState.character.id, summonerId))
                        summonerPickerState = null
                    },
                    onDismiss = { summonerPickerState = null }
                )
            }
        }
    }

    // End game confirm dialog
    if (showEndGameConfirm) {
        AlertDialog(
            onDismissRequest = { showEndGameConfirm = false },
            title = { Text("End Game?") },
            text = { Text("End the game now and calculate the winner based on current Moonstones?") },
            confirmButton = {
                Button(
                    onClick = { showEndGameConfirm = false; viewModel.onEvent(CharacterEvent.EndGame) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = theme.cardShape
                ) {
                    Text("Confirm End", style = theme.buttonTextStyle)
                }
            },
            dismissButton = { TextButton(onClick = { showEndGameConfirm = false }) { Text("Cancel") } },
            shape = theme.cardShape
        )
    }
    GameEndDialogs(state, viewModel, isTutorialActive)
}

// ─── Summon helper ────────────────────────────────────────────────────────────

private fun handleSummonAdd(
    pageIndex: Int,
    char: Character,
    baseChars: List<Character>,
    trackingMode: GameTrackingMode,
    viewModel: CharacterViewModel,
    onShowPicker: (SummonerPickerState) -> Unit
) {
    if (trackingMode == GameTrackingMode.LOW_DETAIL) {
        viewModel.onEvent(CharacterEvent.AddSummonedCharacter(pageIndex, char.id, null))
        return
    }
    val possibleSummoners = baseChars.filter { char.id in it.summonsCharacterIds }
    when (possibleSummoners.size) {
        0 -> viewModel.onEvent(CharacterEvent.AddSummonedCharacter(pageIndex, char.id, null))
        1 -> viewModel.onEvent(CharacterEvent.AddSummonedCharacter(pageIndex, char.id, possibleSummoners[0].id))
        else -> onShowPicker(SummonerPickerState(pageIndex, char, possibleSummoners))
    }
}

// ─── Top bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveGameTopBar(
    state: CharacterState,
    viewModel: CharacterViewModel,
    isTutorialActive: Boolean,
    isSummonPanelOpen: Boolean,
    onDrawerToggle: () -> Unit,
    onEndGameClick: () -> Unit,
    onQuitGame: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = theme.surfaceElevation) {
        CenterAlignedTopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = { if (!isTutorialActive) onDrawerToggle() },
                    modifier = Modifier.onGloballyPositioned { onTargetPositioned("CharacterDrawerButton", it) }
                ) {
                    Icon(
                        if (isSummonPanelOpen) Icons.Default.MenuOpen else Icons.Default.Menu,
                        contentDescription = null
                    )
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rReady = state.readyForRewind.contains(state.deviceId)
                    val rCount = state.readyForRewind.size
                    val totalP = state.gameSession?.players?.size ?: 1
                    val canRewind = state.currentTurn > 1 || isTutorialActive
                    IconButton(
                        onClick = { if (!isTutorialActive) viewModel.onEvent(CharacterEvent.RewindTurn) },
                        enabled = canRewind,
                        modifier = Modifier.onGloballyPositioned { onTargetPositioned("RewindButton", it) }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.History, contentDescription = null,
                                tint = if (rReady) MaterialTheme.colorScheme.primary
                                       else if (canRewind) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                       else MaterialTheme.colorScheme.outline
                            )
                            if (state.gameSession != null) Text("($rCount/$totalP)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    }
                    Text(
                        text = if (state.currentTurn > 4) "Sudden Death" else "Round: ${state.currentTurn}",
                        style = theme.titleStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = theme.screenPadding)
                    )
                    val nReady = state.readyForNextTurn.contains(state.deviceId)
                    val nCount = state.readyForNextTurn.size
                    IconButton(
                        onClick = { if (!isTutorialActive) viewModel.onEvent(CharacterEvent.NextTurn) },
                        modifier = Modifier.onGloballyPositioned { onTargetPositioned("NextTurnButton", it) }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SkipNext, contentDescription = null,
                                tint = if (nReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            if (state.gameSession != null) Text("($nCount/$totalP)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    }
                    IconButton(
                        onClick = { if (!isTutorialActive) onEndGameClick() },
                        modifier = Modifier.onGloballyPositioned { onTargetPositioned("EndGameQuickButton", it) }
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = { if (!isTutorialActive) onQuitGame() },
                    modifier = Modifier.onGloballyPositioned { onTargetPositioned("CloseGameButton", it) }
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            },
            expandedHeight = 48.dp,
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(top = 0.dp)
        )
    }
}

// ─── Roster strip ─────────────────────────────────────────────────────────────

// ─── Grid cell ────────────────────────────────────────────────────────────────

@Composable
private fun CharacterPortraitCell(
    character: Character,
    playState: CharacterPlayState,
    trackingMode: GameTrackingMode,
    summoner: Character?,
    isEditable: Boolean,
    onTap: () -> Unit,
    onHpBadgeTap: () -> Unit,
    onEnergyBadgeTap: () -> Unit,
    onMoonstoneBadgeTap: () -> Unit,
    onStoneDragStart: (Int, Offset) -> Unit,
    onStoneDrag: (Offset) -> Unit,
    onStoneDragEnd: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val isFullTracking = trackingMode == GameTrackingMode.FULL_TRACKING
    val isDead = playState.currentHealth <= 0
    val moonstoneColor = theme.moonstoneColor
    val stoneCoords = remember { mutableStateMapOf<Int, LayoutCoordinates>() }

    val hasCombatModifiers = remember(character) {
        (character.slicingDamageBuff ?: 0) > 0 ||
        (character.piercingDamageBuff ?: 0) > 0 ||
        (character.impactDamageBuff ?: 0) > 0 ||
        character.dealsMagicalDamage ||
        character.allDamageMitigation > 0 ||
        character.slicingDamageMitigation > 0 ||
        character.piercingDamageMitigation > 0 ||
        character.impactDamageMitigation > 0 ||
        character.magicalDamageMitigation > 0
    }

    var showCombatProfile by remember { mutableStateOf(false) }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (showCombatProfile) 1f else 0f,
        animationSpec = tween(200),
        label = "combatOverlay"
    )
    // Auto-dismiss after 3 s
    LaunchedEffect(showCombatProfile) {
        if (showCombatProfile) {
            delay(3000)
            showCombatProfile = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(theme.cardShape)
    ) {
        // ── Portrait area fills full cell width ──────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
        ) {
            val size = maxWidth
            val portraitHeight = size * 0.85f

            Box(
                modifier = Modifier
                    .width(size)
                    .height(portraitHeight)
                    .portraitCrimpedEdge()
                    .then(if (isDead) Modifier.alpha(0.4f) else Modifier)
            ) {
                CharacterPortrait(character, size, shape = RectangleShape)
            }

            if (isDead) {
                Text(
                    "☠",
                    fontSize = (size.value * 0.38f).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (isFullTracking) {
                // HP badge — bottom start, tap to decrement
                StatBadge(
                    value = playState.currentHealth,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(4.dp, (-4).dp)
                        .then(if (isEditable) Modifier.clickable(onClick = onHpBadgeTap) else Modifier)
                )
                // Energy badge — bottom end, tap to decrement
                StatBadge(
                    value = playState.currentEnergy,
                    color = Color(0xFF1565C0),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset((-4).dp, (-4).dp)
                        .then(if (isEditable) Modifier.clickable(onClick = onEnergyBadgeTap) else Modifier)
                )
                // Moonstone badge — top center, tap to increment
                if (playState.moonstones > 0 || isEditable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(0.dp, 4.dp)
                    ) {
                        StatBadge(
                            value = playState.moonstones,
                            color = moonstoneColor,
                            textColor = Color.Black,
                            modifier = if (isEditable) Modifier.clickable(onClick = onMoonstoneBadgeTap) else Modifier
                        )
                        // Support drag-off for moonstone transfer to pool
                        if (isEditable && playState.moonstones > 0) {
                            repeat(playState.moonstones) { i ->
                                Box(
                                    modifier = Modifier
                                        .size(1.dp)
                                        .onGloballyPositioned { stoneCoords[i] = it }
                                        .pointerInput(i) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    onStoneDragStart(i, (stoneCoords[i]?.boundsInWindow()?.topLeft ?: Offset.Zero) + offset)
                                                },
                                                onDrag = { change, amount -> change.consume(); onStoneDrag(amount) },
                                                onDragEnd = { onStoneDragEnd() },
                                                onDragCancel = { onStoneDragEnd() }
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Combat profile overlay — fades in over portrait on stat bundle tap
            if (overlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .width(size)
                        .height(portraitHeight)
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(Color(0xE6080812))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CombatProfileContent(character)
                }
            }
        }

        // ── Nameplate ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Stat bundle pill — tappable if character has combat modifiers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .then(
                        if (!isDead && hasCombatModifiers)
                            Modifier.clickable { showCombatProfile = !showCombatProfile }
                        else Modifier
                    )
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚔ ${character.melee}  ${character.meleeRange}\"  ✦ ${character.arcane}  💨 ${statSign(character.evade)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (isDead) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Character name
            Text(
                text = character.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (isDead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )

            SummonerIndicator(summoner)
        }
    }
}

// ─── Stat badge ───────────────────────────────────────────────────────────────

@Composable
private fun StatBadge(
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ─── Combat profile overlay content ─────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombatProfileContent(character: Character) {
    data class Chip(val label: String, val fg: Color, val bg: Color)

    val slicingBuff  = character.slicingDamageBuff ?: 0
    val piercingBuff = character.piercingDamageBuff ?: 0
    val impactBuff   = character.impactDamageBuff ?: 0

    val atkChips = buildList<Chip> {
        if (slicingBuff > 0)              add(Chip("⚔ +$slicingBuff Slicing",    Color(0xFFFF6B6B), Color(0x20E53935)))
        if (piercingBuff > 0)             add(Chip("▲ +$piercingBuff Piercing",  Color(0xFFFFB74D), Color(0x20F57C00)))
        if (impactBuff > 0)               add(Chip("⬤ +$impactBuff Impact",      Color(0xFFBCAAA4), Color(0x20795548)))
        if (character.dealsMagicalDamage) add(Chip("✦ Magical",                  Color(0xFFCE93D8), Color(0x209C27B0)))
    }
    val defChips = buildList<Chip> {
        val all = character.allDamageMitigation
        if (all > 0)                                   add(Chip("⬡ −$all All",                                        Color(0xFFB0BEC5), Color(0x2090A4AE)))
        if (character.slicingDamageMitigation > 0)     add(Chip("⚔ −${character.slicingDamageMitigation} Slicing",   Color(0xFFEF9A9A), Color(0x20E53935)))
        if (character.piercingDamageMitigation > 0)    add(Chip("▲ −${character.piercingDamageMitigation} Piercing", Color(0xFFFFE082), Color(0x20F57C00)))
        if (character.impactDamageMitigation > 0)      add(Chip("⬤ −${character.impactDamageMitigation} Impact",     Color(0xFFD7CCC8), Color(0x20795548)))
        if (character.magicalDamageMitigation > 0)     add(Chip("✦ −${character.magicalDamageMitigation} Magical",   Color(0xFFE1BEE7), Color(0x209C27B0)))
    }

    if (atkChips.isEmpty() && defChips.isEmpty()) {
        Text(
            "No modifiers",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic
        )
        return
    }

    @Composable
    fun ChipSection(label: String, chips: List<Chip>) {
        if (chips.isEmpty()) return
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                chips.forEach { chip ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(chip.bg)
                            .border(1.dp, chip.fg.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            chip.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = chip.fg
                        )
                    }
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ChipSection("OFFENSIVE", atkChips)
        ChipSection("DEFENSIVE", defChips)
    }
}

// ─── Section header ───────────────────────────────────────────────────────────

@Composable
private fun GamePlayerSectionHeader(troupe: Troupe) {
    val theme = LocalAppThemeProperties.current
    val factionColor = getFactionColor(troupe.faction)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(factionColor.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = factionColor.copy(alpha = 0.4f),
                shape = theme.cardShape
            )
            .padding(horizontal = theme.screenPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(getFactionIcon(troupe.faction), contentDescription = null, tint = factionColor, modifier = Modifier.size(16.dp))
        Text(
            text = troupe.troupeName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = factionColor
        )
    }
}

// ─── Card modal ───────────────────────────────────────────────────────────────

@Composable
private fun GameCharacterCardModal(
    character: Character,
    playState: CharacterPlayState,
    trackingMode: GameTrackingMode,
    isEditable: Boolean,
    onEnergyChange: (Int) -> Unit,
    onAbilityToggle: (String, Boolean) -> Unit,
    onFlip: () -> Unit,
    onTransform: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val isFullTracking = trackingMode == GameTrackingMode.FULL_TRACKING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        // Scrim — tap to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable { onDismiss() }
        )

        ThemedCard(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clickable {} // Absorb touches so scrim doesn't dismiss
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Energy controls row (FULL_TRACKING only)
                    if (isFullTracking) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = theme.screenPadding, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "ENERGY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { if (playState.currentEnergy > 0) onEnergyChange(playState.currentEnergy - 1) },
                                modifier = Modifier.size(32.dp),
                                enabled = isEditable
                            ) { Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            Text(
                                text = playState.currentEnergy.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(min = 28.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = { onEnergyChange(playState.currentEnergy + 1) },
                                modifier = Modifier.size(32.dp),
                                enabled = isEditable
                            ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        }
                        HorizontalDivider()
                    }

                    // Character name + keywords header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = theme.screenPadding, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            CharacterPortrait(character = character, size = 32.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = character.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (character.keywords.isNotEmpty()) {
                                Text(
                                    text = character.keywords.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    HorizontalDivider()

                    // Flip-animated card content (fills remaining space, pinned footer)
                    CharacterCardContent(
                        character = character,
                        searchQuery = "",
                        isFlipped = playState.isFlipped,
                        onFlip = onFlip,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        animateFlip = true,
                        showBackgroundImage = theme.showBackgroundImageOverlay,
                        showHealthTracker = true,
                        abilityUsedStates = if (isFullTracking && isEditable) playState.usedAbilities else null,
                        onAbilityUsedChange = if (isFullTracking && isEditable) onAbilityToggle else null,
                        pinnedFooter = true,
                        scrollable = true
                    )

                    // Transform button
                    if (onTransform != null && character.transformsInto != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = isEditable) { onTransform(character.transformsInto) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Transform", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

            }
        }

        Text(
            text = "Tap outside to close",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

// ─── Reusable composables ─────────────────────────────────────────────────────

@Composable
private fun SummonerIndicator(summoner: Character?) {
    summoner ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        CharacterPortrait(summoner, 14.dp)
        Spacer(Modifier.width(3.dp))
        Text(
            "↳ ${summoner.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun SummonPanel(
    allChars: List<Character>,
    baseChars: List<Character>,
    summonEntries: List<SummonEntry>,
    trackingMode: GameTrackingMode,
    onAdd: (Character) -> Unit,
    onRemove: (Character) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val troupeCharIds = baseChars.map { it.id }.toSet()
    val summonableIds = baseChars.flatMap { it.summonsCharacterIds }.toSet()
    val summonableChars = allChars.filter { it.id in summonableIds && it.id !in troupeCharIds }
    val summonedIds = summonEntries.map { it.characterId }.toSet()

    Column(
        modifier = Modifier
            .width(100.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .padding(vertical = theme.verticalSpacing / 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing / 2)
    ) {
        if (summonableChars.isEmpty()) {
            Text(
                "No summons",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(theme.screenPadding),
                textAlign = TextAlign.Center
            )
        } else {
            summonableChars.forEach { char ->
                val isAlreadySummoned = char.id in summonedIds
                Box(modifier = Modifier.clickable { if (isAlreadySummoned) onRemove(char) else onAdd(char) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .alpha(if (isAlreadySummoned) 1f else 0.45f)
                            .padding(horizontal = 4.dp)
                    ) {
                        CharacterPortrait(char, 44.dp)
                        Text(char.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    if (isAlreadySummoned) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummonerPickerDialog(
    character: Character,
    possibleSummoners: List<Character>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who summons ${character.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                possibleSummoners.forEach { summoner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(summoner.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CharacterPortrait(summoner, 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(summoner.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = theme.cardShape
    )
}

@Composable
private fun CollapsiblePoolBar(
    state: CharacterState,
    pageIndex: Int,
    allDisplayChars: List<Character>,
    isTutorialActive: Boolean,
    potBounds: MutableState<androidx.compose.ui.geometry.Rect?>,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    viewModel: CharacterViewModel
) {
    val theme = LocalAppThemeProperties.current
    val isLocal = if (isTutorialActive) true else {
        state.gameSession?.players?.getOrNull(pageIndex)?.deviceId == state.deviceId || state.gameSession == null
    }
    var poolExpanded by rememberSaveable { mutableStateOf(true) }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = theme.surfaceElevation) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { poolExpanded = !poolExpanded }
                    .padding(horizontal = theme.screenPadding, vertical = theme.verticalSpacing / 4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (poolExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Resources", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(visible = poolExpanded) {
                Column(modifier = Modifier.padding(horizontal = theme.screenPadding).padding(bottom = theme.verticalSpacing / 2)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .onGloballyPositioned { potBounds.value = it.boundsInWindow(); onTargetPositioned("MoonstonePool", it) }
                                .pointerInput(isLocal) {
                                    if (isLocal) detectDragGestures(
                                        onDragStart = onDragStart,
                                        onDrag = { change, amount -> change.consume(); onDrag(amount) },
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragEnd
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MoonstoneIcon(size = 32.dp, modifier = Modifier.alpha(if (isLocal) 1f else 0.3f))
                                Text("POOL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    val poolResources = allDisplayChars.flatMap { it.poolResources }.distinctBy { it.name }
                    poolResources.forEach { resDef ->
                        val currentCount = state.poolResourceCounts[pageIndex]?.get(resDef.name) ?: 0
                        PoolResourceRow(
                            definition = resDef,
                            currentCount = currentCount,
                            onCountChange = { viewModel.onEvent(CharacterEvent.UpdatePoolResource(pageIndex, resDef.name, it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolResourceRow(definition: PoolResourceDefinition, currentCount: Int, onCountChange: (Int) -> Unit) {
    val theme = LocalAppThemeProperties.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = theme.verticalSpacing / 4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(definition.name, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (currentCount > 0) onCountChange(currentCount - 1) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text("$currentCount/${definition.maxInPool}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = { if (currentCount < definition.maxInPool) onCountChange(currentCount + 1) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun GameEndDialogs(state: CharacterState, viewModel: CharacterViewModel, isTutorialActive: Boolean) {
    val isMoonstone = LocalAppThemeProperties.current.showExpandedStatsHeader
    val theme = LocalAppThemeProperties.current
    if (state.winnerName != null && !isTutorialActive) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) },
            title = { Text("Victory!", style = if (isMoonstone) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium) },
            text = { Text(text = "${state.winnerName} has collected the most Moonstones and wins the game!", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) }, shape = theme.cardShape) { Text("New Game", style = theme.buttonTextStyle) } },
            shape = theme.cardShape
        )
    }
    if (state.isTie && !isTutorialActive) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) },
            title = { Text("It's a Tie!", style = if (isMoonstone) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium) },
            text = { Text(text = "No single player collected more Moonstones than the others. The game ends in a draw!", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) }, shape = theme.cardShape) { Text("New Game", style = theme.buttonTextStyle) } },
            shape = theme.cardShape
        )
    }
}

// ─── Public utility composables ──────────────────────────────────────────────

@Composable
fun CharacterPortraitJump(character: Character, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        CharacterPortrait(character = character, size = 50.dp)
    }
}

@Composable
fun MoonstoneIcon(size: Dp, modifier: Modifier = Modifier) {
    val moonstoneColor = LocalAppThemeProperties.current.moonstoneColor
    Canvas(modifier = modifier.size(size)) {
        val path = Path().apply {
            moveTo(size.toPx() / 2f, 0f)
            lineTo(size.toPx(), size.toPx())
            lineTo(0f, size.toPx())
            close()
        }
        drawPath(path, color = moonstoneColor)
    }
}

@Composable
fun FrontSide(
    character: Character,
    playState: CharacterPlayState,
    isEditable: Boolean,
    onHealthChange: (Int) -> Unit,
    onEnergyChange: (Int) -> Unit,
    onExpandToggle: () -> Unit,
    onAbilityToggle: (String, Boolean) -> Unit,
    onFlip: () -> Unit,
    onTransform: ((Int) -> Unit)? = null,
    trackingMode: GameTrackingMode = GameTrackingMode.FULL_TRACKING
) {
    val theme = LocalAppThemeProperties.current
    val isFullTracking = trackingMode == GameTrackingMode.FULL_TRACKING
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f).clickable { onFlip() }.padding(start = 24.dp)) {
                Text(text = character.name, style = theme.titleStyle, color = MaterialTheme.colorScheme.primary)
                val signature = character.signatureMove?.let { if (it.upgradeFor.isNotEmpty()) it.upgradeFor else it.name } ?: ""
                if (signature.isNotEmpty()) {
                    Text(text = signature, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, fontStyle = FontStyle.Italic, modifier = Modifier.offset(y = (-8).dp))
                }
            }
            IconButton(onClick = onFlip) { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(2f)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    CommonStatBox("MELEE", "${character.melee} / ${character.meleeRange}\"", modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start)
                    CommonStatBox("ARCANE", character.arcane.toString(), modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally)
                    CommonStatBox("EVADE", character.evade.toString(), modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally)
                }
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ModifierDisplay(character, isOffense = true, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    ModifierDisplay(character, isOffense = false, modifier = Modifier.weight(1f))
                }
            }
            if (isFullTracking) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ENERGY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (playState.currentEnergy > 0) onEnergyChange(playState.currentEnergy - 1) }, modifier = Modifier.size(32.dp), enabled = isEditable) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Text(text = playState.currentEnergy.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = { onEnergyChange(playState.currentEnergy + 1) }, modifier = Modifier.size(32.dp), enabled = isEditable) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                HealthPipsChunked(
                    total = character.health,
                    current = playState.currentHealth,
                    energyTrack = character.energyTrack,
                    compact = false,
                    isEditable = isEditable,
                    onHealthChange = onHealthChange
                )
            }
            IconButton(onClick = onExpandToggle, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
        }
        if (playState.isExpanded) {
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            val passiveAbilities = character.abilities.filter { it.abilityType == "Passive" }
            val activeAbilities = character.abilities.filter { it.abilityType == "Active" }
            val arcaneAbilities = character.abilities.filter { it.abilityType == "Arcane" }
            if (passiveAbilities.isNotEmpty()) {
                Text("PASSIVE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                passiveAbilities.forEach { CommonAbilityItem(name = it.name, description = it.description, oncePerTurn = it.oncePerTurn, oncePerGame = it.oncePerGame) }
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            }
            if (activeAbilities.isNotEmpty()) {
                Text("ACTIVE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                activeAbilities.forEach { ability ->
                    CommonAbilityItem(
                        name = "${ability.name} (${ability.energyCost ?: 0})${if ((ability.range ?: "").isNotEmpty()) " " + ability.range else ""}",
                        description = ability.description,
                        oncePerTurn = ability.oncePerTurn, oncePerGame = ability.oncePerGame,
                        isUsed = playState.usedAbilities[ability.name] ?: false,
                        onUsedChange = { u -> onAbilityToggle(ability.name, u) },
                        isEditable = isEditable
                    )
                }
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            }
            if (arcaneAbilities.isNotEmpty()) {
                Text("ARCANE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                arcaneAbilities.forEach { ability ->
                    CommonAbilityItem(
                        name = "${ability.name} (${ability.energyCost ?: 0})${if ((ability.range ?: "").isNotEmpty()) " " + ability.range else ""}",
                        description = buildArcaneDescription(ability),
                        oncePerTurn = ability.oncePerTurn, oncePerGame = ability.oncePerGame,
                        isUsed = playState.usedAbilities[ability.name] ?: false,
                        onUsedChange = { u -> onAbilityToggle(ability.name, u) },
                        isEditable = isEditable
                    )
                }
            }
            if (onTransform != null && character.transformsInto != null) {
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = isEditable) { onTransform(character.transformsInto) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transform", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
