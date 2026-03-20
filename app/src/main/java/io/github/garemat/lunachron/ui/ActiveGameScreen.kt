package io.github.garemat.lunachron.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.R
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


data class SummonerPickerState(
    val playerIndex: Int,
    val character: Character,
    val possibleSummoners: List<Character>
)

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
    val pagerState = rememberPagerState(pageCount = { if (isTutorialActive) 1 else players.size })
    val scope = rememberCoroutineScope()
    val theme = LocalAppThemeProperties.current

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

    var draggingStoneSource by remember { mutableStateOf<StoneSource?>(null) }
    var draggingStoneIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val characterBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val potBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val stateRef = rememberUpdatedState(state)

    var isSummonPanelOpen by rememberSaveable { mutableStateOf(false) }
    var showEndGameConfirm by remember { mutableStateOf(false) }
    var selectedChar by remember { mutableStateOf<Pair<Character, CharacterPlayState>?>(null) }
    var summonerPickerState by remember { mutableStateOf<SummonerPickerState?>(null) }

    val trackingMode = state.gameTrackingMode
    val layoutMode = state.gameLayoutMode

    // Compute current page display chars for the bottom bar pool
    val currentPageIndex = pagerState.currentPage
    val currentPageAllDisplayChars = remember(currentPageIndex, activePlayers, state.activeSummons, state.characters) {
        val currentPair = activePlayers.getOrNull(currentPageIndex) ?: return@remember emptyList<Character>()
        val troupeCharIds = currentPair.first.characterIds.toSet()
        val baseChars = currentPair.second.filter { it.id in troupeCharIds }
        val summonEntries = state.activeSummons[currentPageIndex] ?: emptyList()
        val summonedChars = summonEntries.mapNotNull { e -> state.characters.find { it.id == e.characterId } }
        baseChars + summonedChars
    }

    LaunchedEffect(currentTutorialStep) {
        if (currentTutorialStep?.targetName == "CharacterDrawerButton") isSummonPanelOpen = true
        else if (isTutorialActive && currentTutorialStep?.targetName != "CharacterDrawerButton") isSummonPanelOpen = false
    }

    Scaffold(
        topBar = {
            ActiveGameTopBar(
                state, viewModel, isTutorialActive, isSummonPanelOpen,
                onDrawerToggle = { isSummonPanelOpen = !isSummonPanelOpen },
                onEndGameClick = { showEndGameConfirm = true },
                onQuitGame = onQuitGame,
                onTargetPositioned = onTargetPositioned,
                pagerState = pagerState,
                activePlayers = activePlayers
            )
        },
        bottomBar = {
            Column {
                if (trackingMode == GameTrackingMode.FULL_TRACKING) {
                    CollapsiblePoolBar(
                        state = state,
                        pageIndex = currentPageIndex,
                        allDisplayChars = currentPageAllDisplayChars,
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
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { rootLayoutCoordinates = it }) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { pageIndex ->
                val currentPair = activePlayers.getOrNull(pageIndex)
                if (currentPair != null) {
                    val troupeCharIds = currentPair.first.characterIds.toSet()
                    val baseChars = currentPair.second.filter { it.id in troupeCharIds }
                    val summonEntries = state.activeSummons[pageIndex] ?: emptyList()
                    val summonedChars = summonEntries.mapNotNull { e -> state.characters.find { it.id == e.characterId } }
                    val allDisplayChars = baseChars + summonedChars

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (layoutMode) {
                            GameLayoutMode.COMPACT_GRID -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(
                                        start = theme.screenPadding,
                                        end = theme.screenPadding,
                                        top = theme.screenPadding,
                                        bottom = theme.screenPadding
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
                                    horizontalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    gridItemsIndexed(allDisplayChars, key = { _, char -> char.id }) { charIndex, character ->
                                        val stateKey = "${pageIndex}_${charIndex}"
                                        val playState = if (isTutorialActive) {
                                            CharacterPlayState(currentHealth = character.health, moonstones = if (charIndex == 0) 2 else 0)
                                        } else {
                                            state.characterPlayStates[stateKey] ?: CharacterPlayState(currentHealth = character.health)
                                        }
                                        val isSummoned = charIndex >= baseChars.size
                                        val summonEntry = if (isSummoned) summonEntries.getOrNull(charIndex - baseChars.size) else null
                                        val summoner = summonEntry?.summonedByCharacterId?.let { id -> baseChars.find { it.id == id } }

                                        DisposableEffect(stateKey) { onDispose { characterBounds.remove(stateKey) } }
                                        Box(modifier = Modifier.onGloballyPositioned { characterBounds[stateKey] = it.boundsInWindow() }) {
                                            GameCharacterGridCard(
                                                character = character,
                                                playState = playState,
                                                trackingMode = trackingMode,
                                                summoner = summoner,
                                                isEditable = !isTutorialActive,
                                                draggingStoneInfo = if (draggingStoneSource is StoneSource.Character &&
                                                    (draggingStoneSource as StoneSource.Character).playerIndex == pageIndex &&
                                                    (draggingStoneSource as StoneSource.Character).charIndex == charIndex
                                                ) draggingStoneIndex else -1,
                                                onHealthChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterHealth(pageIndex, charIndex, it)) },
                                                onEnergyChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterEnergy(pageIndex, charIndex, it)) },
                                                onAbilityToggle = { name, used -> viewModel.onEvent(CharacterEvent.ToggleAbilityUsed(pageIndex, charIndex, name, used)) },
                                                onFlippedChange = { viewModel.onEvent(CharacterEvent.ToggleCharacterFlipped(pageIndex, charIndex, it)) },
                                                onTap = { selectedChar = character to playState },
                                                onTransform = if (character.transformsInto != null) { targetId -> viewModel.onEvent(CharacterEvent.TransformCharacter(pageIndex, charIndex, targetId)) } else null,
                                                onStoneDragStart = { index, pos ->
                                                    draggingStoneIndex = index
                                                    draggingStoneSource = StoneSource.Character(pageIndex, charIndex)
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

                            GameLayoutMode.DETAILED_LIST -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(
                                        start = theme.screenPadding,
                                        end = theme.screenPadding,
                                        top = theme.screenPadding,
                                        bottom = theme.screenPadding
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(allDisplayChars, key = { _, char -> char.id }) { charIndex, character ->
                                        val stateKey = "${pageIndex}_${charIndex}"
                                        val playState = if (isTutorialActive) {
                                            CharacterPlayState(currentHealth = character.health, moonstones = if (charIndex == 0) 2 else 0)
                                        } else {
                                            state.characterPlayStates[stateKey] ?: CharacterPlayState(currentHealth = character.health)
                                        }
                                        val isEditable = !isTutorialActive
                                        val isSummoned = charIndex >= baseChars.size
                                        val summonEntry = if (isSummoned) summonEntries.getOrNull(charIndex - baseChars.size) else null
                                        val summoner = summonEntry?.summonedByCharacterId?.let { id -> baseChars.find { it.id == id } }

                                        ThemedCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(theme.cardContentPadding)) {
                                                if (playState.isFlipped) {
                                                    CharacterBack(
                                                        character = character,
                                                        searchQuery = "",
                                                        onFlip = { viewModel.onEvent(CharacterEvent.ToggleCharacterFlipped(pageIndex, charIndex, false)) }
                                                    )
                                                } else {
                                                    FrontSide(
                                                        character = character,
                                                        playState = playState,
                                                        isEditable = isEditable,
                                                        trackingMode = trackingMode,
                                                        onHealthChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterHealth(pageIndex, charIndex, it)) },
                                                        onEnergyChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterEnergy(pageIndex, charIndex, it)) },
                                                        onExpandToggle = { viewModel.onEvent(CharacterEvent.ToggleCharacterExpanded(pageIndex, charIndex, !playState.isExpanded)) },
                                                        onAbilityToggle = { name, used -> viewModel.onEvent(CharacterEvent.ToggleAbilityUsed(pageIndex, charIndex, name, used)) },
                                                        onFlip = { viewModel.onEvent(CharacterEvent.ToggleCharacterFlipped(pageIndex, charIndex, true)) },
                                                        onTransform = if (character.transformsInto != null) { targetId -> viewModel.onEvent(CharacterEvent.TransformCharacter(pageIndex, charIndex, targetId)) } else null
                                                    )
                                                }
                                                if (playState.moonstones > 0) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                                        MoonstoneIcon(size = 12.dp)
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("×${playState.moonstones}", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                                SummonerIndicator(summoner)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isSummonPanelOpen,
                            enter = expandHorizontally(),
                            exit = shrinkHorizontally(),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            SummonPanel(
                                allChars = state.characters,
                                baseChars = baseChars,
                                summonEntries = summonEntries,
                                trackingMode = trackingMode,
                                onAdd = { char ->
                                    handleSummonAdd(pageIndex, char, baseChars, trackingMode, viewModel) {
                                        summonerPickerState = it
                                    }
                                },
                                onRemove = { char ->
                                    viewModel.onEvent(CharacterEvent.RemoveSummonedCharacter(pageIndex, char.id))
                                }
                            )
                        }
                    }
                }
            }

            if (draggingStoneSource != null) {
                val localPos = rootLayoutCoordinates?.windowToLocal(dragPosition) ?: dragPosition
                Box(modifier = Modifier.offset { IntOffset(localPos.x.roundToInt() - 20.dp.toPx().toInt(), localPos.y.roundToInt() - 20.dp.toPx().toInt()) }.zIndex(1000f)) {
                    MoonstoneIcon(size = 40.dp)
                }
            }

            selectedChar?.let { (char, ps) ->
                FullScreenCharacterModal(character = char, playState = ps, scaffoldPadding = padding, onDismiss = { selectedChar = null })
            }

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

    if (showEndGameConfirm) {
        AlertDialog(
            onDismissRequest = { showEndGameConfirm = false },
            title = { Text("End Game?") },
            text = { Text("Are you sure you want to end the game now and calculate the winner based on current Moonstones?") },
            confirmButton = {
                Button(onClick = { showEndGameConfirm = false; viewModel.onEvent(CharacterEvent.EndGame) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = theme.cardShape) {
                    Text("Confirm End", style = theme.buttonTextStyle)
                }
            },
            dismissButton = { TextButton(onClick = { showEndGameConfirm = false }) { Text("Cancel") } },
            shape = theme.cardShape
        )
    }
    GameEndDialogs(state, viewModel, isTutorialActive)
}

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
    onTargetPositioned: (String, LayoutCoordinates) -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState,
    activePlayers: List<Pair<Troupe, List<Character>>>
) {
    val theme = LocalAppThemeProperties.current
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        CenterAlignedTopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = { if (!isTutorialActive) onDrawerToggle() },
                    modifier = Modifier.onGloballyPositioned { onTargetPositioned("CharacterDrawerButton", it) }
                ) {
                    Icon(if (isSummonPanelOpen) Icons.Default.MenuOpen else Icons.Default.Menu, contentDescription = null)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rReady = state.readyForRewind.contains(state.deviceId)
                    val rCount = state.readyForRewind.size
                    val totalP = state.gameSession?.players?.size ?: 1
                    val canR = state.currentTurn > 1 || isTutorialActive
                    IconButton(onClick = { if (!isTutorialActive) viewModel.onEvent(CharacterEvent.RewindTurn) }, enabled = canR, modifier = Modifier.onGloballyPositioned { onTargetPositioned("RewindButton", it) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = if (rReady) MaterialTheme.colorScheme.primary else if (canR) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline)
                            if (state.gameSession != null) Text("($rCount/$totalP)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    }
                    Text(text = if (state.currentTurn > 4) "Sudden Death" else "Round: ${state.currentTurn}", style = theme.titleStyle, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = theme.screenPadding))
                    val nReady = state.readyForNextTurn.contains(state.deviceId)
                    val nCount = state.readyForNextTurn.size
                    IconButton(onClick = { if (!isTutorialActive) viewModel.onEvent(CharacterEvent.NextTurn) }, modifier = Modifier.onGloballyPositioned { onTargetPositioned("NextTurnButton", it) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = if (nReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            if (state.gameSession != null) Text("($nCount/$totalP)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    }
                    IconButton(onClick = { if (!isTutorialActive) onEndGameClick() }, modifier = Modifier.onGloballyPositioned { onTargetPositioned("EndGameQuickButton", it) }) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }
            },
            actions = {
                IconButton(onClick = { if (!isTutorialActive) onQuitGame() }, modifier = Modifier.onGloballyPositioned { onTargetPositioned("CloseGameButton", it) }) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(top = 0.dp)
        )
        if (activePlayers.size > 1) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = theme.screenPadding, containerColor = Color.Transparent, divider = {}) {
                activePlayers.forEachIndexed { index, (troupe, _) ->
                    Tab(selected = pagerState.currentPage == index, onClick = { scope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(text = troupe.troupeName, style = theme.labelStyle) })
                }
            }
        }
    }
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
                modifier = Modifier.fillMaxWidth().clickable { poolExpanded = !poolExpanded }.padding(horizontal = theme.screenPadding, vertical = theme.verticalSpacing / 4),
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
                            modifier = Modifier.size(60.dp)
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
        modifier = Modifier.fillMaxWidth().padding(vertical = theme.verticalSpacing / 4),
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
private fun GameCharacterGridCard(
    character: Character,
    playState: CharacterPlayState,
    trackingMode: GameTrackingMode,
    summoner: Character?,
    isEditable: Boolean,
    draggingStoneInfo: Int,
    onHealthChange: (Int) -> Unit,
    onEnergyChange: (Int) -> Unit,
    onAbilityToggle: (String, Boolean) -> Unit,
    onFlippedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onTransform: ((Int) -> Unit)? = null,
    onStoneDragStart: (Int, Offset) -> Unit,
    onStoneDrag: (Offset) -> Unit,
    onStoneDragEnd: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val isFullTracking = trackingMode == GameTrackingMode.FULL_TRACKING
    val isDead = playState.currentHealth <= 0
    val stoneCoords = remember { mutableStateMapOf<Int, LayoutCoordinates>() }

    val sigName = remember(character) {
        character.signatureMove?.let { if (it.upgradeFor.isNotEmpty()) it.upgradeFor else it.name } ?: ""
    }
    val trackableAbilities = remember(character) {
        character.abilities.filter { it.abilityType in listOf("Active", "Arcane") && (it.oncePerTurn || it.oncePerGame) }.map { it.name }
    }

    ThemedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
        containerColor = if (isDead) Color.DarkGray else null
    ) {
        Column(modifier = Modifier.padding(theme.cardContentPadding)) {
            // Name + signature move
            Text(
                text = buildAnnotatedString {
                    append(character.name)
                    if (sigName.isNotEmpty()) {
                        append(" - ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(sigName) }
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium
            )
            // Portrait + stats/damage row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                CharacterPortrait(character, 52.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("⚔ ${character.melee} | ${character.meleeRange}\"", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("Evade ${character.evade.toString()}", style = MaterialTheme.typography.bodySmall)
                    ModifierDisplay(character, isOffense = true)
                    ModifierDisplay(character, isOffense = false)
                }
            }
            // Trackable resources row: energy + once-per-turn/game ability toggles
            if (isFullTracking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    IconButton(onClick = { if (playState.currentEnergy > 0) onEnergyChange(playState.currentEnergy - 1) }, modifier = Modifier.size(24.dp), enabled = isEditable) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Text("E:${playState.currentEnergy}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onEnergyChange(playState.currentEnergy + 1) }, modifier = Modifier.size(24.dp), enabled = isEditable) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    trackableAbilities.forEach { abilityName ->
                        val isUsed = playState.usedAbilities[abilityName] ?: false
                        Spacer(Modifier.width(6.dp))
                        Text(
                            abilityName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).widthIn(max = 64.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isUsed) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent)
                                .border(1.dp, if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .then(if (isEditable) Modifier.clickable { onAbilityToggle(abilityName, !isUsed) } else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUsed) Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(8.dp), tint = Color.White)
                        }
                    }
                }
            }

            // Moonstones
            if (playState.moonstones > 0 || isFullTracking) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFullTracking) {
                        repeat(playState.moonstones) { i ->
                            Box(
                                modifier = Modifier
                                    .onGloballyPositioned { stoneCoords[i] = it }
                                    .alpha(if (draggingStoneInfo == i) 0f else 1f)
                                    .pointerInput(i, isEditable) {
                                        if (isEditable) detectDragGestures(
                                            onDragStart = { offset -> onStoneDragStart(i, (stoneCoords[i]?.boundsInWindow()?.topLeft ?: Offset.Zero) + offset) },
                                            onDrag = { change, amount -> change.consume(); onStoneDrag(amount) },
                                            onDragEnd = { onStoneDragEnd() },
                                            onDragCancel = { onStoneDragEnd() }
                                        )
                                    }
                            ) { MoonstoneIcon(size = 12.dp) }
                        }
                    } else if (playState.moonstones > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MoonstoneIcon(size = 10.dp)
                            Spacer(Modifier.width(2.dp))
                            Text("×${playState.moonstones}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HealthPipsChunked(
                total = character.health,
                current = playState.currentHealth,
                energyTrack = character.energyTrack,
                compact = trackingMode == GameTrackingMode.LOW_DETAIL,
                isEditable = isFullTracking && isEditable,
                onHealthChange = onHealthChange
            )

            SummonerIndicator(summoner)

            if (onTransform != null && character.transformsInto != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = isEditable) { onTransform(character.transformsInto) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Transform", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}




// CharacterPortrait is defined in CommonComponents.kt

@Composable
private fun SummonerIndicator(summoner: Character?) {
    summoner ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        CharacterPortrait(summoner, 18.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            "by ${summoner.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
            Text("No summons", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(theme.screenPadding), textAlign = TextAlign.Center)
        } else {
            summonableChars.forEach { char ->
                val isAlreadySummoned = char.id in summonedIds
                Box(
                    modifier = Modifier.clickable { if (isAlreadySummoned) onRemove(char) else onAdd(char) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.alpha(if (isAlreadySummoned) 1f else 0.45f).padding(horizontal = 4.dp)
                    ) {
                        CharacterPortrait(char, 44.dp)
                        Text(char.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    if (isAlreadySummoned) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
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
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(summoner.id) }.padding(vertical = 4.dp),
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
private fun FullScreenCharacterModal(
    character: Character,
    playState: CharacterPlayState,
    scaffoldPadding: PaddingValues,
    onDismiss: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val isFlipped = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().zIndex(100f)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable { onDismiss() })
        ThemedCard(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .padding(
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() + 8.dp
                )
                .fillMaxHeight()
                .clickable {}
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(theme.cardContentPadding)
            ) {
                if (!isFlipped.value) {
                    CharacterFront(character = character, searchQuery = "", onFlip = { isFlipped.value = true })
                } else {
                    CharacterBack(character = character, searchQuery = "", onFlip = { isFlipped.value = false })
                }
            }
        }
    }
}

@Composable
private fun GameEndDialogs(state: CharacterState, viewModel: CharacterViewModel, isTutorialActive: Boolean) {
    val isMoonstone = LocalAppThemeProperties.current.showExpandedStatsHeader
    val theme = LocalAppThemeProperties.current
    if (state.winnerName != null && !isTutorialActive) {
        AlertDialog(onDismissRequest = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) }, title = { Text("Victory!", style = if (isMoonstone) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium) }, text = { Text(text = "${state.winnerName} has collected the most Moonstones and wins the game!", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) }, shape = theme.cardShape) { Text("New Game", style = theme.buttonTextStyle) } }, shape = theme.cardShape)
    }
    if (state.isTie && !isTutorialActive) {
        AlertDialog(onDismissRequest = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) }, title = { Text("It's a Tie!", style = if (isMoonstone) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium) }, text = { Text(text = "No single player collected more Moonstones than the others. The game ends in a draw!", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) }, shape = theme.cardShape) { Text("New Game", style = theme.buttonTextStyle) } }, shape = theme.cardShape)
    }
}

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

sealed class StoneSource {
    data object Pot : StoneSource()
    data class Character(val playerIndex: Int, val charIndex: Int) : StoneSource()
}

@Composable
fun MoonstoneIcon(size: Dp, modifier: Modifier = Modifier) {
    val moonstoneColor = LocalAppThemeProperties.current.moonstoneColor
    Canvas(modifier = modifier.size(size)) {
        val path = Path().apply { moveTo(size.toPx() / 2f, 0f); lineTo(size.toPx(), size.toPx()); lineTo(0f, size.toPx()); close() }
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
                        IconButton(onClick = { if (playState.currentEnergy > 0) onEnergyChange(playState.currentEnergy - 1) }, modifier = Modifier.size(32.dp), enabled = isEditable) { Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        Text(text = playState.currentEnergy.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = { onEnergyChange(playState.currentEnergy + 1) }, modifier = Modifier.size(32.dp), enabled = isEditable) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)) }
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
            IconButton(onClick = onExpandToggle, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
        }
        if (playState.isExpanded) {
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2)); HorizontalDivider(); Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
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
                activeAbilities.forEach { ability -> CommonAbilityItem(name = "${ability.name} (${ability.energyCost ?: 0})${if ((ability.range ?: "").isNotEmpty()) " " + ability.range else ""}", description = ability.description, oncePerTurn = ability.oncePerTurn, oncePerGame = ability.oncePerGame, isUsed = playState.usedAbilities[ability.name] ?: false, onUsedChange = { u -> onAbilityToggle(ability.name, u) }, isEditable = isEditable) }
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            }
            if (arcaneAbilities.isNotEmpty()) {
                Text("ARCANE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                arcaneAbilities.forEach { ability -> CommonAbilityItem(name = "${ability.name} (${ability.energyCost ?: 0})${if ((ability.range ?: "").isNotEmpty()) " " + ability.range else ""}", description = buildArcaneDescription(ability), oncePerTurn = ability.oncePerTurn, oncePerGame = ability.oncePerGame, isUsed = playState.usedAbilities[ability.name] ?: false, onUsedChange = { u -> onAbilityToggle(ability.name, u) }, isEditable = isEditable) }
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
