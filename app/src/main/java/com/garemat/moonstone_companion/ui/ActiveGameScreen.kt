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
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties
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

    var isDrawerOpen by rememberSaveable { mutableStateOf(false) }
    var showEndGameConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(currentTutorialStep) {
        if (currentTutorialStep?.targetName == "CharacterDrawerButton") isDrawerOpen = true
        else if (isTutorialActive && currentTutorialStep?.targetName != "CharacterDrawerButton") isDrawerOpen = false
    }

    Scaffold(
        topBar = {
            ActiveGameTopBar(state, viewModel, isTutorialActive, isDrawerOpen, onDrawerToggle = { isDrawerOpen = !isDrawerOpen }, onEndGameClick = { showEndGameConfirm = true }, onQuitGame = onQuitGame, onTargetPositioned = onTargetPositioned, pagerState = pagerState, activePlayers = activePlayers)
        },
        bottomBar = {
            ActiveGameBottomBar(state, pagerState, isTutorialActive, potBounds, onTargetPositioned, onDragStart = { offset ->
                draggingStoneSource = StoneSource.Pot
                dragPosition = (potBounds.value?.topLeft ?: Offset.Zero) + offset
            }, onDrag = { dragPosition += it }, onDragEnd = {
                if (!isTutorialActive) {
                    characterBounds.entries.find { it.value.contains(dragPosition) }?.let { target ->
                        val (tP, tC) = target.key.split("_").map { it.toInt() }
                        val currentStones = stateRef.value.characterPlayStates[target.key]?.moonstones ?: 0
                        if (currentStones < 7) viewModel.onEvent(CharacterEvent.UpdateCharacterMoonstones(tP, tC, currentStones + 1))
                    }
                }
                draggingStoneSource = null
            })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { rootLayoutCoordinates = it }) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { pageIndex ->
                val currentPair = activePlayers.getOrNull(pageIndex)
                if (currentPair != null) {
                    val characters = currentPair.second
                    val listState = rememberLazyListState()
                    Row(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(visible = isDrawerOpen, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                            Column(modifier = Modifier.width(70.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)).padding(vertical = theme.verticalSpacing / 2), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing / 2)) {
                                characters.forEachIndexed { charIndex, character ->
                                    CharacterPortraitJump(character = character, onClick = { if (!isTutorialActive) scope.launch { listState.animateScrollToItem(charIndex) } })
                                }
                            }
                        }
                        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(theme.screenPadding), verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)) {
                            itemsIndexed(characters) { charIndex, character ->
                                val stateKey = "${pageIndex}_${charIndex}"
                                val playState = if (isTutorialActive) CharacterPlayState(currentHealth = character.health, moonstones = if (charIndex == 0) 2 else 0) else state.characterPlayStates[stateKey] ?: CharacterPlayState(currentHealth = character.health)
                                DisposableEffect(stateKey) { onDispose { characterBounds.remove(stateKey) } }
                                Box(modifier = Modifier.onGloballyPositioned { characterBounds[stateKey] = it.boundsInWindow() }) {
                                    CharacterGameCard(character = character, playState = playState, isEditable = !isTutorialActive, draggingStoneInfo = if (draggingStoneSource is StoneSource.Character && (draggingStoneSource as StoneSource.Character).playerIndex == pageIndex && (draggingStoneSource as StoneSource.Character).charIndex == charIndex) draggingStoneIndex else -1, onHealthChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterHealth(pageIndex, charIndex, it)) }, onEnergyChange = { viewModel.onEvent(CharacterEvent.UpdateCharacterEnergy(pageIndex, charIndex, it)) }, onExpandToggle = { viewModel.onEvent(CharacterEvent.ToggleCharacterExpanded(pageIndex, charIndex, !playState.isExpanded)) }, onFlippedChange = { viewModel.onEvent(CharacterEvent.ToggleCharacterFlipped(pageIndex, charIndex, it)) }, onAbilityToggle = { name, used -> viewModel.onEvent(CharacterEvent.ToggleAbilityUsed(pageIndex, charIndex, name, used)) }, onStoneDragStart = { index, pos -> draggingStoneIndex = index; draggingStoneSource = StoneSource.Character(pageIndex, charIndex); dragPosition = pos }, onStoneDrag = { dragPosition += it }, onStoneDragEnd = {
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
                                        draggingStoneSource = null; draggingStoneIndex = -1
                                    })
                                }
                            }
                        }
                    }
                }
            }
            if (draggingStoneSource != null) {
                val localPos = rootLayoutCoordinates?.windowToLocal(dragPosition) ?: dragPosition
                Box(modifier = Modifier.offset { IntOffset(localPos.x.roundToInt() - 20.dp.toPx().toInt(), localPos.y.roundToInt() - 20.dp.toPx().toInt()) }.zIndex(1000f)) { MoonstoneIcon(size = 40.dp) }
            }
        }
    }

    if (showEndGameConfirm) {
        AlertDialog(onDismissRequest = { showEndGameConfirm = false }, title = { Text("End Game?") }, text = { Text("Are you sure you want to end the game now and calculate the winner based on current Moonstones?") }, confirmButton = { Button(onClick = { showEndGameConfirm = false; viewModel.onEvent(CharacterEvent.EndGame) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = theme.cardShape) { Text("Confirm End", style = theme.buttonTextStyle) } }, dismissButton = { TextButton(onClick = { showEndGameConfirm = false }) { Text("Cancel") } }, shape = theme.cardShape)
    }
    GameEndDialogs(state, viewModel, isTutorialActive)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveGameTopBar(state: CharacterState, viewModel: CharacterViewModel, isTutorialActive: Boolean, isDrawerOpen: Boolean, onDrawerToggle: () -> Unit, onEndGameClick: () -> Unit, onQuitGame: () -> Unit, onTargetPositioned: (String, LayoutCoordinates) -> Unit, pagerState: androidx.compose.foundation.pager.PagerState, activePlayers: List<Pair<Troupe, List<Character>>>) {
    val theme = LocalAppThemeProperties.current
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        CenterAlignedTopAppBar(
            navigationIcon = { IconButton(onClick = { if (!isTutorialActive) onDrawerToggle() }, modifier = Modifier.onGloballyPositioned { onTargetPositioned("CharacterDrawerButton", it) }) { Icon(if (isDrawerOpen) Icons.Default.MenuOpen else Icons.Default.Menu, contentDescription = null) } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rReady = state.readyForRewind.contains(state.deviceId); val rCount = state.readyForRewind.size; val totalP = state.gameSession?.players?.size ?: 1; val canR = state.currentTurn > 1 || isTutorialActive
                    IconButton(onClick = { if (!isTutorialActive) viewModel.onEvent(CharacterEvent.RewindTurn) }, enabled = canR, modifier = Modifier.onGloballyPositioned { onTargetPositioned("RewindButton", it) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.History, contentDescription = null, tint = if (rReady) MaterialTheme.colorScheme.primary else if (canR) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline); if (state.gameSession != null) Text("($rCount/$totalP)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp) }
                    }
                    Text(text = if (state.currentTurn > 4) "Sudden Death" else "Round: ${state.currentTurn}", style = theme.titleStyle, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = theme.screenPadding))
                    val nReady = state.readyForNextTurn.contains(state.deviceId); val nCount = state.readyForNextTurn.size
                    IconButton(onClick = { if (!isTutorialActive) viewModel.onEvent(CharacterEvent.NextTurn) }, modifier = Modifier.onGloballyPositioned { onTargetPositioned("NextTurnButton", it) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = if (nReady) MaterialTheme.colorScheme.primary else if (canR) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline); if (state.gameSession != null) Text("($nCount/$totalP)", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp) }
                    }
                    IconButton(onClick = { if (!isTutorialActive) onEndGameClick() }, modifier = Modifier.onGloballyPositioned { onTargetPositioned("EndGameQuickButton", it) }) { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) }
                }
            },
            actions = { IconButton(onClick = { if (!isTutorialActive) onQuitGame() }, modifier = Modifier.onGloballyPositioned { onTargetPositioned("CloseGameButton", it) }) { Icon(Icons.Default.Close, contentDescription = null) } },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent), windowInsets = WindowInsets(top = 0.dp)
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
private fun ActiveGameBottomBar(state: CharacterState, pagerState: androidx.compose.foundation.pager.PagerState, isTutorialActive: Boolean, potBounds: MutableState<androidx.compose.ui.geometry.Rect?>, onTargetPositioned: (String, LayoutCoordinates) -> Unit, onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit) {
    val theme = LocalAppThemeProperties.current
    val isLocal = if (isTutorialActive) true else state.gameSession?.players?.getOrNull(pagerState.currentPage)?.deviceId == state.deviceId || state.gameSession == null
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = theme.surfaceElevation) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = theme.screenPadding, vertical = theme.verticalSpacing / 2), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).onGloballyPositioned { potBounds.value = it.boundsInWindow(); onTargetPositioned("MoonstonePool", it) }.pointerInput(isLocal) {
                if (isLocal) detectDragGestures(onDragStart = onDragStart, onDrag = { change, amount -> change.consume(); onDrag(amount) }, onDragEnd = { onDragEnd() }, onDragCancel = { onDragEnd() })
            }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { MoonstoneIcon(size = 32.dp, modifier = Modifier.alpha(if (isLocal) 1f else 0.3f)); Text("POOL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun GameEndDialogs(state: CharacterState, viewModel: CharacterViewModel, isTutorialActive: Boolean) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
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
    val context = LocalContext.current
    val imageRes = remember(character.imageName) { if (character.imageName != null) context.resources.getIdentifier(character.imageName.substringBeforeLast("."), "drawable", context.packageName) else 0 }
    Box(modifier = Modifier.size(50.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        if (imageRes != 0) Image(painter = painterResource(id = imageRes), contentDescription = character.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(text = character.name.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

sealed class StoneSource {
    data object Pot : StoneSource()
    data class Character(val playerIndex: Int, val charIndex: Int) : StoneSource()
}

@Composable
fun MoonstoneIcon(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val moonstoneColor = LocalAppThemeProperties.current.moonstoneColor
    Canvas(modifier = modifier.size(size)) {
        val path = Path().apply { moveTo(size.toPx() / 2f, 0f); lineTo(size.toPx(), size.toPx()); lineTo(0f, size.toPx()); close() }
        drawPath(path, color = moonstoneColor)
    }
}

@Composable
fun CharacterGameCard(character: Character, playState: CharacterPlayState, isEditable: Boolean, draggingStoneInfo: Int, onHealthChange: (Int) -> Unit, onEnergyChange: (Int) -> Unit, onExpandToggle: () -> Unit, onFlippedChange: (Boolean) -> Unit, onAbilityToggle: (String, Boolean) -> Unit, onStoneDragStart: (Int, Offset) -> Unit, onStoneDrag: (Offset) -> Unit, onStoneDragEnd: () -> Unit) {
    val theme = LocalAppThemeProperties.current
    Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = theme.cardShape, colors = CardDefaults.cardColors(containerColor = if (playState.currentHealth <= 0) Color.DarkGray else MaterialTheme.colorScheme.surfaceVariant)) {
        Box {
            Column(modifier = Modifier.padding(theme.cardContentPadding)) {
                if (!playState.isFlipped) FrontSide(character, playState, isEditable, onHealthChange, onEnergyChange, onExpandToggle, onAbilityToggle, onFlip = { onFlippedChange(true) })
                else CharacterBack(character = character, searchQuery = "", onFlip = { onFlippedChange(false) })
            }
            Row(modifier = Modifier.padding(8.dp).align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val stoneCoords = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
                repeat(playState.moonstones) { i ->
                    Box(modifier = Modifier.onGloballyPositioned { stoneCoords[i] = it }.alpha(if (draggingStoneInfo == i) 0f else 1f).pointerInput(i, isEditable) {
                        if (isEditable) detectDragGestures(onDragStart = { offset -> onStoneDragStart(i, (stoneCoords[i]?.boundsInWindow()?.topLeft ?: Offset.Zero) + offset) }, onDrag = { change, amount -> change.consume(); onStoneDrag(amount) }, onDragEnd = { onStoneDragEnd() }, onDragCancel = { onStoneDragEnd() })
                    }) { MoonstoneIcon(size = 16.dp) }
                }
            }
        }
    }
}

@Composable
fun FrontSide(character: Character, playState: CharacterPlayState, isEditable: Boolean, onHealthChange: (Int) -> Unit, onEnergyChange: (Int) -> Unit, onExpandToggle: () -> Unit, onAbilityToggle: (String, Boolean) -> Unit, onFlip: () -> Unit) {
    val theme = LocalAppThemeProperties.current
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f).clickable { onFlip() }.padding(start = 24.dp)) {
                Text(text = character.name, style = theme.titleStyle, color = MaterialTheme.colorScheme.primary)
                val signature = if (character.signatureMove.upgradeFrom.isNotEmpty()) character.signatureMove.upgradeFrom else character.signatureMove.name
                if (signature.isNotEmpty()) {
                    Text(
                        text = signature,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.offset(y = (-8).dp)
                    )
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
                    CommonStatBox("EVADE", character.evade, modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally)
                }
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ModifierDisplay(character, isOffense = true, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    ModifierDisplay(character, isOffense = false, modifier = Modifier.weight(1f))
                }
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ENERGY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (playState.currentEnergy > 0) onEnergyChange(playState.currentEnergy - 1) }, modifier = Modifier.size(32.dp), enabled = isEditable) { Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    Text(text = playState.currentEnergy.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = { onEnergyChange(playState.currentEnergy + 1) }, modifier = Modifier.size(32.dp), enabled = isEditable) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            HealthTracker(totalHealth = character.health, currentHealth = playState.currentHealth, energyTrack = character.energyTrack, onHealthChange = onHealthChange, isEditable = isEditable, modifier = Modifier.weight(1f))
            IconButton(onClick = onExpandToggle, modifier = Modifier.size(32.dp)) { Icon(if (playState.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null) }
        }
        if (playState.isExpanded) {
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2)); HorizontalDivider(); Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            if (character.passiveAbilities.isNotEmpty()) {
                Text("PASSIVE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                character.passiveAbilities.forEach { CommonAbilityItem(name = it.name, description = it.description, oncePerTurn = it.oncePerTurn, oncePerGame = it.oncePerGame) }
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            }
            if (character.activeAbilities.isNotEmpty()) {
                Text("ACTIVE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                character.activeAbilities.forEach { it -> CommonAbilityItem(name = "${it.name} (${it.cost})${if (it.range.isNotEmpty()) " " + it.range else ""}", description = it.description, oncePerTurn = it.oncePerTurn, oncePerGame = it.oncePerGame, isUsed = playState.usedAbilities[it.name] ?: false, onUsedChange = { u -> onAbilityToggle(it.name, u) }, isEditable = isEditable) }
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            }
            if (character.arcaneAbilities.isNotEmpty()) {
                Text("ARCANE ABILITIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                character.arcaneAbilities.forEach { it -> CommonAbilityItem(name = "${it.name} (${it.cost})${if (it.range.isNotEmpty()) " " + it.range else ""}", description = it.description, oncePerTurn = it.oncePerTurn, oncePerGame = it.oncePerGame, reloadable = it.reloadable, isUsed = playState.usedAbilities[it.name] ?: false, onUsedChange = { u -> onAbilityToggle(it.name, u) }, isEditable = isEditable) }
            }
        }
    }
}
