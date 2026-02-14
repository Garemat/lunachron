package com.garemat.moonstone_companion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    
    // Tutorial Helper: Create example characters if needed
    val tutorialPlayers = remember(isTutorialActive, state.characters) {
        if (isTutorialActive && state.characters.isNotEmpty()) {
            val grub = state.characters.find { it.name.equals("Grub", ignoreCase = true) } ?: state.characters[0]
            val gotchgut = state.characters.find { it.name.equals("Gotchgut", ignoreCase = true) } ?: state.characters.getOrNull(1) ?: state.characters[0]
            val boulder = state.characters.find { it.name.equals("Boulder", ignoreCase = true) } ?: state.characters.getOrNull(2) ?: state.characters[0]
            
            val exampleTroupe = Troupe(troupeName = "Example Dominion", faction = Faction.DOMINION, characterIds = listOf(grub.id, gotchgut.id, boulder.id), shareCode = "")
            listOf(exampleTroupe to listOf(grub, gotchgut, boulder))
        } else if (isTutorialActive) {
            // Placeholder while loading
            val loadingTroupe = Troupe(troupeName = "Example Dominion", faction = Faction.DOMINION, characterIds = emptyList(), shareCode = "")
            listOf(loadingTroupe to emptyList<Character>())
        } else null
    }

    val activePlayers = tutorialPlayers ?: players
    
    // Drag and Drop State
    var draggingStoneSource by remember { mutableStateOf<StoneSource?>(null) }
    var draggingStoneIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    
    val characterBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val potBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val stateRef = rememberUpdatedState(state)

    // Side Drawer State
    var isDrawerOpen by rememberSaveable { mutableStateOf(false) }
    var showEndGameConfirm by remember { mutableStateOf(false) }

    // Tutorial logic for drawer
    LaunchedEffect(currentTutorialStep) {
        if (currentTutorialStep?.targetName == "CharacterDrawerButton") {
            isDrawerOpen = true
        } else if (isTutorialActive && currentTutorialStep?.targetName != "CharacterDrawerButton") {
            isDrawerOpen = false
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { if (!isTutorialActive) isDrawerOpen = !isDrawerOpen },
                            modifier = Modifier.onGloballyPositioned { onTargetPositioned("CharacterDrawerButton", it) }
                        ) {
                            Icon(
                                if (isDrawerOpen) Icons.Default.MenuOpen else Icons.Default.Menu,
                                contentDescription = "Toggle Character Drawer"
                            )
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Rewind Section
                            val isRewindReady = state.readyForRewind.contains(state.deviceId)
                            val rewindCount = state.readyForRewind.size
                            val totalPlayers = state.gameSession?.players?.size ?: 1
                            val canRewind = state.currentTurn > 1 || isTutorialActive
                            
                            IconButton(
                                onClick = { if (!isTutorialActive) viewModel.onEvent(CharacterEvent.RewindTurn) },
                                enabled = canRewind,
                                modifier = Modifier.onGloballyPositioned { onTargetPositioned("RewindButton", it) }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.History, 
                                        contentDescription = "Rewind Turn",
                                        tint = if (isRewindReady) MaterialTheme.colorScheme.primary 
                                               else if (canRewind) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                               else MaterialTheme.colorScheme.outline
                                    )
                                    if (state.gameSession != null) {
                                        Text("($rewindCount/$totalPlayers)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                                    }
                                }
                            }
                            
                            Text(
                                text = if (state.currentTurn > 4) "Sudden Death" else "Round: " + state.currentTurn,
                                style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // Next Turn Section
                            val isNextReady = state.readyForNextTurn.contains(state.deviceId)
                            val nextCount = state.readyForNextTurn.size

                            IconButton(
                                onClick = { if (!isTutorialActive) viewModel.onEvent(CharacterEvent.NextTurn) },
                                modifier = Modifier.onGloballyPositioned { onTargetPositioned("NextTurnButton", it) }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.SkipNext, 
                                        contentDescription = "Next Turn",
                                        tint = if (isNextReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    if (state.gameSession != null) {
                                        Text("($nextCount/$totalPlayers)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                                    }
                                }
                            }

                            // Quick End Game
                            IconButton(
                                onClick = { if (!isTutorialActive) showEndGameConfirm = true },
                                modifier = Modifier.onGloballyPositioned { onTargetPositioned("EndGameQuickButton", it) }
                            ) {
                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription = "End Game Now",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { if (!isTutorialActive) onQuitGame() },
                            modifier = Modifier.onGloballyPositioned { onTargetPositioned("CloseGameButton", it) }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "End Game")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    windowInsets = WindowInsets(top = 0.dp)
                )
                
                if (activePlayers.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 16.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        activePlayers.forEachIndexed { index, (troupe, _) ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { 
                                    Text(
                                        text = troupe.troupeName,
                                        style = if (isMoonstone) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold) else LocalTextStyle.current
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            val isLocalPlayer = remember(state.gameSession, pagerState.currentPage, state.deviceId, isTutorialActive) {
                if (isTutorialActive) true
                else {
                    val session = state.gameSession
                    if (session == null) true
                    else session.players.getOrNull(pagerState.currentPage)?.deviceId == state.deviceId
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isMoonstone) 0.dp else 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Moonstone Pool
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .onGloballyPositioned { 
                                potBounds.value = it.boundsInWindow()
                                onTargetPositioned("MoonstonePool", it)
                            }
                            .pointerInput(isLocalPlayer) {
                                if (isLocalPlayer) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            draggingStoneSource = StoneSource.Pot
                                            dragPosition = (potBounds.value?.topLeft ?: Offset.Zero) + offset
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragPosition += dragAmount
                                        },
                                        onDragEnd = {
                                            if (!isTutorialActive) {
                                                val dropTarget = characterBounds.entries.find { it.value.contains(dragPosition) }
                                                if (dropTarget != null) {
                                                    val (pIdx, cIdx) = dropTarget.key.split("_").map { it.toInt() }
                                                    val currentState = stateRef.value.characterPlayStates[dropTarget.key]?.moonstones ?: 0
                                                    if (currentState < 7) {
                                                        viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(pIdx, cIdx, currentState + 1))
                                                    }
                                                }
                                            }
                                            draggingStoneSource = null
                                        },
                                        onDragCancel = { draggingStoneSource = null }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             MoonstoneIcon(size = 32.dp, modifier = Modifier.alpha(if (isLocalPlayer) 1f else 0.3f))
                            Text("POOL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootLayoutCoordinates = it }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) { pageIndex ->
                val currentPair = activePlayers.getOrNull(pageIndex)
                if (currentPair != null) {
                    val characters = currentPair.second
                    val listState = rememberLazyListState()

                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Single Drawer definition inside Pager page
                            AnimatedVisibility(
                                visible = isDrawerOpen,
                                enter = expandHorizontally(),
                                exit = shrinkHorizontally()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(70.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    characters.forEachIndexed { charIndex, character ->
                                        CharacterPortraitJump(
                                            character = character,
                                            onClick = {
                                                if (!isTutorialActive) {
                                                    scope.launch {
                                                        listState.animateScrollToItem(charIndex)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(characters) { charIndex, character ->
                                    val stateKey = "" + pageIndex + "_" + charIndex
                                    val playState = if (isTutorialActive) {
                                        CharacterPlayState(currentHealth = character.health, moonstones = if (charIndex == 0) 2 else 0)
                                    } else {
                                        state.characterPlayStates[stateKey] ?: CharacterPlayState(currentHealth = character.health)
                                    }

                                    DisposableEffect(stateKey) {
                                        onDispose { characterBounds.remove(stateKey) }
                                    }

                                    Box(
                                        modifier = Modifier.onGloballyPositioned { 
                                            characterBounds[stateKey] = it.boundsInWindow()
                                        }
                                    ) {
                                        CharacterGameCard(
                                            character = character,
                                            currentHealth = playState.currentHealth,
                                            currentEnergy = playState.currentEnergy,
                                            moonstones = playState.moonstones,
                                            isExpanded = playState.isExpanded,
                                            isFlipped = playState.isFlipped,
                                            usedAbilities = playState.usedAbilities,
                                            isEditable = !isTutorialActive,
                                            draggingStoneIndex = if (draggingStoneSource is StoneSource.Character && 
                                                (draggingStoneSource as StoneSource.Character).playerIndex == pageIndex && 
                                                (draggingStoneSource as StoneSource.Character).charIndex == charIndex) draggingStoneIndex else -1,
                                            onHealthChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterHealth(pageIndex, charIndex, it)) },
                                            onEnergyChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterEnergy(pageIndex, charIndex, it)) },
                                            onExpandToggle = { viewModel.onEvent(CharacterEvent.ToggleCharacterExpanded(pageIndex, charIndex, !playState.isExpanded)) },
                                            onFlippedChange = { viewModel.onEvent(CharacterEvent.ToggleCharacterFlipped(pageIndex, charIndex, it)) },
                                            onAbilityToggle = { abilityName, used ->
                                                viewModel.onEvent(CharacterEvent.ToggleAbilityUsed(pageIndex, charIndex, abilityName, used))
                                            },
                                            onStoneDragStart = { index, globalPos ->
                                                draggingStoneIndex = index
                                                draggingStoneSource = StoneSource.Character(pageIndex, charIndex)
                                                dragPosition = globalPos
                                            },
                                            onStoneDrag = { amount -> dragPosition += amount },
                                            onStoneDragEnd = {
                                                val source = draggingStoneSource as? StoneSource.Character
                                                if (source != null && !isTutorialActive) {
                                                    val currentState = stateRef.value
                                                    
                                                    if (potBounds.value?.contains(dragPosition) == true) {
                                                        val sourceStones = currentState.characterPlayStates["" + source.playerIndex + "_" + source.charIndex]?.moonstones ?: 0
                                                        if (sourceStones > 0) {
                                                            viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(source.playerIndex, source.charIndex, sourceStones - 1))
                                                        }
                                                    } 
                                                    else {
                                                        val dropTarget = characterBounds.entries.find { it.value.contains(dragPosition) }
                                                        if (dropTarget != null) {
                                                            val (targetPIdx, targetCIdx) = dropTarget.key.split("_").map { it.toInt() }
                                                            if (targetPIdx != source.playerIndex || targetCIdx != source.charIndex) {
                                                                val sourceStones = currentState.characterPlayStates["" + source.playerIndex + "_" + source.charIndex]?.moonstones ?: 0
                                                                val targetStones = currentState.characterPlayStates[dropTarget.key]?.moonstones ?: 0
                                                                
                                                                if (targetStones < 7) {
                                                                    viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(source.playerIndex, source.charIndex, sourceStones - 1))
                                                                    viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(targetPIdx, targetCIdx, targetStones + 1))
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
        }
    }

    // --- Confirmation Dialogs ---

    if (showEndGameConfirm) {
        AlertDialog(
            onDismissRequest = { showEndGameConfirm = false },
            title = { Text("End Game?") },
            text = { Text("Are you sure you want to end the game now and calculate the winner based on current Moonstones?") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndGameConfirm = false
                        viewModel.onEvent(CharacterEvent.EndGame)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm End")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndGameConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Victory/Tie Dialogs ---

    if (state.winnerName != null && !isTutorialActive) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) },
            title = { Text("Victory!", style = if (isMoonstone) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium) },
            text = { 
                Text(
                    text = "${state.winnerName} has collected the most Moonstones and wins the game!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) }) {
                    Text("New Game")
                }
            }
        )
    }

    if (state.isTie && !isTutorialActive) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) },
            title = { Text("It's a Tie!", style = if (isMoonstone) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium) },
            text = { 
                Text(
                    text = "No single player collected more Moonstones than the others. The game ends in a draw!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.onEvent(CharacterEvent.ResetGamePlayState) }) {
                    Text("New Game")
                }
            }
        )
    }
}

@Composable
fun CharacterPortraitJump(
    character: Character,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRes = remember(character.imageName) { 
        if (character.imageName != null) 
            context.resources.getIdentifier(character.imageName.substringBeforeLast("."), "drawable", context.packageName) 
        else 0 
    }

    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (imageRes != 0) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = character.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = character.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

sealed class StoneSource {
    data object Pot : StoneSource()
    data class Character(val playerIndex: Int, val charIndex: Int) : StoneSource()
}

@Composable
fun MoonstoneIcon(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val path = Path().apply {
            moveTo(size.toPx() / 2f, 0f)
            lineTo(size.toPx(), size.toPx())
            lineTo(0f, size.toPx())
            close()
        }
        drawPath(path, color = Color(0xFF2196F3))
    }
}

@Composable
fun CharacterGameCard(
    character: Character,
    currentHealth: Int,
    currentEnergy: Int,
    moonstones: Int,
    isExpanded: Boolean,
    isFlipped: Boolean,
    usedAbilities: Map<String, Boolean>,
    isEditable: Boolean,
    draggingStoneIndex: Int,
    onHealthChange: (Int) -> Unit,
    onEnergyChange: (Int) -> Unit,
    onExpandToggle: () -> Unit,
    onFlippedChange: (Boolean) -> Unit,
    onAbilityToggle: (String, Boolean) -> Unit,
    onStoneDragStart: (Int, Offset) -> Unit,
    onStoneDrag: (Offset) -> Unit,
    onStoneDragEnd: () -> Unit
) {
    val currentOnDragStart by rememberUpdatedState(onStoneDragStart)
    val currentOnDrag by rememberUpdatedState(onStoneDrag)
    val currentOnDragEnd by rememberUpdatedState(onStoneDragEnd)
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = if (isMoonstone) RoundedCornerShape(0.dp) else CardDefaults.shape,
        colors = CardDefaults.cardColors(
            containerColor = if (currentHealth <= 0) Color.DarkGray else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!isFlipped) {
                    FrontSide(
                        character = character,
                        currentHealth = currentHealth,
                        currentEnergy = currentEnergy,
                        isExpanded = isExpanded,
                        usedAbilities = usedAbilities,
                        isEditable = isEditable,
                        onHealthChange = onHealthChange,
                        onEnergyChange = onEnergyChange,
                        onExpandToggle = onExpandToggle,
                        onAbilityToggle = onAbilityToggle,
                        onFlip = { onFlippedChange(true) }
                    )
                } else {
                    CharacterBack(character = character, searchQuery = "", onFlip = { onFlippedChange(false) })
                }
            }

            // Moonstone Pinned View
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val stoneCoords = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
                
                repeat(moonstones) { i ->
                    val isThisStoneDragging = draggingStoneIndex == i
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { stoneCoords[i] = it }
                            .alpha(if (isThisStoneDragging) 0f else 1f)
                            .pointerInput(i, isEditable) {
                                if (isEditable) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val globalPos = (stoneCoords[i]?.boundsInWindow()?.topLeft ?: Offset.Zero) + offset
                                            currentOnDragStart(i, globalPos)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragAmount.let { currentOnDrag(it) }
                                        },
                                        onDragEnd = { currentOnDragEnd() },
                                        onDragCancel = { currentOnDragEnd() }
                                    )
                                }
                            }
                    ) {
                        MoonstoneIcon(size = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun FrontSide(
    character: Character,
    currentHealth: Int,
    currentEnergy: Int,
    isExpanded: Boolean,
    usedAbilities: Map<String, Boolean>,
    isEditable: Boolean,
    onHealthChange: (Int) -> Unit,
    onEnergyChange: (Int) -> Unit,
    onExpandToggle: () -> Unit,
    onAbilityToggle: (String, Boolean) -> Unit,
    onFlip: () -> Unit
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).clickable { onFlip() }.padding(start = 24.dp)) {
                Text(
                    text = character.name,
                    style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp) else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (character.signatureMove.upgradeFrom.isNotEmpty()) {
                    Text(
                        text = character.signatureMove.upgradeFrom,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            IconButton(onClick = onFlip) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Show Signature Move",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(2f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    CommonStatBox("MELEE", character.melee.toString() + " / " + character.meleeRange + "\"", modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start)
                    CommonStatBox("ARCANE", character.arcane.toString(), modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally)
                    CommonStatBox("EVADE", character.evade, modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModifierDisplay(character, isOffense = true, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    ModifierDisplay(character, isOffense = false, modifier = Modifier.weight(1f))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ENERGY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (currentEnergy > 0) onEnergyChange(currentEnergy - 1) }, 
                        modifier = Modifier.size(32.dp),
                        enabled = isEditable
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Use", modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = currentEnergy.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onEnergyChange(currentEnergy + 1) }, 
                        modifier = Modifier.size(32.dp),
                        enabled = isEditable
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Gain", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            HealthTracker(
                totalHealth = character.health,
                currentHealth = currentHealth,
                energyTrack = character.energyTrack,
                onHealthChange = onHealthChange,
                isEditable = isEditable,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = onExpandToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand abilities"
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            if (character.passiveAbilities.isNotEmpty()) {
                Text("PASSIVE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                character.passiveAbilities.forEach { passive ->
                    CommonAbilityItem(
                        name = passive.name,
                        description = passive.description,
                        oncePerTurn = passive.oncePerTurn,
                        oncePerGame = passive.oncePerGame
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (character.activeAbilities.isNotEmpty()) {
                Text("ACTIVE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                character.activeAbilities.forEach { ability ->
                    val rangeText = if (ability.range.isNotEmpty()) " " + ability.range else ""
                    val header = ability.name + " (" + ability.cost + ")" + rangeText
                    CommonAbilityItem(
                        name = header, 
                        description = ability.description,
                        oncePerTurn = ability.oncePerTurn,
                        oncePerGame = ability.oncePerGame,
                        isUsed = usedAbilities[ability.name] ?: false,
                        onUsedChange = { onAbilityToggle(ability.name, it) },
                        isEditable = isEditable
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (character.arcaneAbilities.isNotEmpty()) {
                Text("ARCANE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                character.arcaneAbilities.forEach { ability ->
                    val rangeText = if (ability.range.isNotEmpty()) " " + ability.range else ""
                    val header = ability.name + " (" + ability.cost + ")" + rangeText
                    CommonAbilityItem(
                        name = header, 
                        description = ability.description,
                        oncePerTurn = ability.oncePerTurn,
                        oncePerGame = ability.oncePerGame,
                        reloadable = ability.reloadable,
                        isUsed = usedAbilities[ability.name] ?: false,
                        onUsedChange = { onAbilityToggle(ability.name, it) },
                        isEditable = isEditable
                    )
                }
            }
        }
    }
}
