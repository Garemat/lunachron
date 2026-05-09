package io.github.garemat.lunachron.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun CharacterListScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onExpansionChanged: (Boolean) -> Unit = {},
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    // Track expansion order so we can drop the oldest when >3 are open
    var expandedCharacterIdsList by remember { mutableStateOf(listOf<Int>()) }
    val expandedCharacterIds = expandedCharacterIdsList.toSet()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFactions by remember { mutableStateOf(setOf<Faction>()) }
    val theme = LocalAppThemeProperties.current

    val availableTags = remember(state.characters, selectedFactions) {
        state.characters
            .filter { char -> selectedFactions.isEmpty() || char.factions.any { it in selectedFactions } }
            .flatMap { it.keywords }
            .distinct()
            .sorted()
    }

    var includedTags by remember { mutableStateOf(setOf<String>()) }
    var orTags by remember { mutableStateOf(setOf<String>()) }
    var excludedTags by remember { mutableStateOf(setOf<String>()) }
    var showFilterExpanded by remember { mutableStateOf(true) }
    val filtersActive = selectedFactions.isNotEmpty() || includedTags.isNotEmpty() || orTags.isNotEmpty() || excludedTags.isNotEmpty() || searchQuery.isNotEmpty()

    LaunchedEffect(expandedCharacterIds) {
        onExpansionChanged(expandedCharacterIds.isNotEmpty())
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        listState.scrollToItem(0)
        if (searchQuery.isEmpty()) {
            expandedCharacterIdsList = emptyList()
        } else {
            val matchingIds = state.characters.filter { character ->
                character.name.contains(searchQuery, ignoreCase = true) ||
                character.abilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
            }.map { it.id }
            if (matchingIds.size in 1..3) expandedCharacterIdsList = matchingIds
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(availableTags) {
        includedTags = includedTags.filter { it in availableTags }.toSet()
        orTags = orTags.filter { it in availableTags }.toSet()
        excludedTags = excludedTags.filter { it in availableTags }.toSet()
    }

    val filteredCharacters = remember(state.characters, searchQuery, selectedFactions, includedTags, orTags, excludedTags) {
        filterCharacters(state.characters, searchQuery, selectedFactions, includedTags, orTags, excludedTags)
            .sortedBy { it.name }
    }

    val letterIndex = remember(filteredCharacters) {
        buildList {
            var lastLetter: Char? = null
            filteredCharacters.forEachIndexed { index, character ->
                val letter = character.name.first().uppercaseChar()
                if (letter != lastLetter) { add(letter to index); lastLetter = letter }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = showFilterExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
                shadowElevation = theme.surfaceElevation
            ) {
                CharacterFilterHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedFactions = selectedFactions,
                    onFactionsChange = { selectedFactions = it },
                    selectedTags = includedTags,
                    onTagsChange = { includedTags = it },
                    excludedTags = excludedTags,
                    onExcludedTagsChange = { excludedTags = it },
                    orTags = orTags,
                    onOrTagsChange = { orTags = it },
                    availableTags = availableTags,
                    showCollapseAll = expandedCharacterIds.isNotEmpty(),
                    onCollapseAll = { expandedCharacterIdsList = emptyList() },
                    onClearAll = {
                        searchQuery = ""
                        selectedFactions = emptySet()
                        includedTags = emptySet()
                        orTags = emptySet()
                        excludedTags = emptySet()
                        expandedCharacterIdsList = emptyList()
                    },
                    onTargetPositioned = onTargetPositioned
                )
            }
        }

        val hasStrip = letterIndex.size > 1
        var isDraggingStrip by remember { mutableStateOf(false) }
        var activeLetter by remember { mutableStateOf<Char?>(null) }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { onTargetPositioned("CharacterList", it) },
                verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing / 2),
                contentPadding = PaddingValues(top = theme.verticalSpacing / 2, bottom = screenHeight * 0.6f, start = theme.screenPadding, end = theme.screenPadding),
                state = listState
            ) {
                if (filteredCharacters.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No characters match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(filteredCharacters, key = { it.id }) { character ->
                    val isFirst = filteredCharacters.firstOrNull()?.id == character.id
                    CommonCharacterCard(
                        character = character,
                        searchQuery = searchQuery,
                        isExpanded = expandedCharacterIds.contains(character.id),
                        cardDisplayMode = state.cardDisplayMode,
                        onExpandClick = {
                            val isCurrentlyExpanded = character.id in expandedCharacterIds
                            if (isCurrentlyExpanded) {
                                expandedCharacterIdsList = expandedCharacterIdsList - character.id
                            } else {
                                val newList = expandedCharacterIdsList + character.id
                                // Cap at 3 open cards — drop the oldest if needed
                                expandedCharacterIdsList = if (newList.size > 3) newList.drop(newList.size - 3) else newList
                                val index = filteredCharacters.indexOfFirst { it.id == character.id }
                                if (index >= 0) coroutineScope.launch { listState.animateScrollToItem(index) }
                            }
                        },
                        cardTargetName = if (isFirst) "FirstCharacterCard" else "CharacterCard",
                        onPositioned = { name, coords -> if (isFirst || name == "FlipButton") onTargetPositioned(name, coords) }
                    )
                }
            }

            // Alphabetical fast-scroll: thin drag handle on the left
            if (hasStrip) {
                // Letter carousel — visible only while dragging
                if (isDraggingStrip) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 28.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.width(40.dp).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            letterIndex.forEach { (letter, _) ->
                                val isActive = letter == activeLetter
                                Text(
                                    text = letter.toString(),
                                    style = if (isActive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelSmall,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // Thin handle bar — touch target is 28dp wide, visual bar is 4dp
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .width(28.dp)
                        .pointerInput(letterIndex) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                isDraggingStrip = true
                                fun jumpTo(y: Float) {
                                    val fraction = (y / size.height.toFloat()).coerceIn(0f, 1f)
                                    val idx = (fraction * letterIndex.size).toInt().coerceIn(0, letterIndex.size - 1)
                                    activeLetter = letterIndex[idx].first
                                    coroutineScope.launch { listState.scrollToItem(letterIndex[idx].second) }
                                }
                                jumpTo(down.position.y)
                                drag(down.id) { change -> change.consume(); jumpTo(change.position.y) }
                                isDraggingStrip = false
                                activeLetter = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight(0.5f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }
            }

            // Small floating toggle button — collapses to just this when filter is hidden
            Surface(
                onClick = { showFilterExpanded = !showFilterExpanded },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 8.dp)
                    .onGloballyPositioned { onTargetPositioned("FilterButtonOpen", it) },
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                color = if (filtersActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = if (showFilterExpanded) "Collapse filters" else "Expand filters",
                    modifier = Modifier.padding(8.dp).size(20.dp),
                    tint = if (filtersActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }
    }
}
