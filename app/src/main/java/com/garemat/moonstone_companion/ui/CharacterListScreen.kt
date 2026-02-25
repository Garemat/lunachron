package com.garemat.moonstone_companion.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties

@Composable
fun CharacterListScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var expandedCharacterIds by remember { mutableStateOf(setOf<Int>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFactions by remember { mutableStateOf(setOf<Faction>()) }
    val theme = LocalAppThemeProperties.current
    
    val availableTags = remember(state.characters, selectedFactions) {
        state.characters
            .filter { char -> selectedFactions.isEmpty() || char.factions.any { it in selectedFactions } }
            .flatMap { it.tags }
            .distinct()
            .sorted()
    }
    
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showFilterBar by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty()) {
            expandedCharacterIds = emptySet()
        } else {
            val matchingIds = state.characters.filter { character ->
                character.name.contains(searchQuery, ignoreCase = true) ||
                character.passiveAbilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) } ||
                character.activeAbilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) } ||
                character.arcaneAbilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
            }.map { it.id }.toSet()
            if (matchingIds.size in 1..3) expandedCharacterIds = matchingIds
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(availableTags) {
        selectedTags = selectedTags.filter { it in availableTags }.toSet()
    }

    val filteredCharacters = remember(state.characters, searchQuery, selectedFactions, selectedTags) {
        state.characters.filter { character ->
            val matchesFaction = selectedFactions.isEmpty() || character.factions.any { it in selectedFactions }
            val matchesTags = selectedTags.isEmpty() || character.tags.containsAll(selectedTags)
            val matchesSearch = searchQuery.isEmpty() || 
                character.name.contains(searchQuery, ignoreCase = true) ||
                character.passiveAbilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) } ||
                character.activeAbilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) } ||
                character.arcaneAbilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
            matchesFaction && matchesTags && matchesSearch
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showFilterBar) {
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
                        selectedTags = selectedTags,
                        onTagsChange = { selectedTags = it },
                        availableTags = availableTags,
                        showCollapseAll = expandedCharacterIds.isNotEmpty(),
                        onCollapseAll = { expandedCharacterIds = emptySet() },
                        onClearAll = {
                            searchQuery = ""
                            selectedFactions = emptySet()
                            selectedTags = emptySet()
                            expandedCharacterIds = emptySet()
                        },
                        onTargetPositioned = onTargetPositioned
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing / 2),
                contentPadding = PaddingValues(top = theme.verticalSpacing / 2, bottom = 100.dp, start = theme.screenPadding, end = theme.screenPadding),
                state = listState
            ) {
                if (filteredCharacters.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No characters match your search.", color = Color.Gray)
                        }
                    }
                }
                items(filteredCharacters, key = { it.id }) { character ->
                    val isFirst = filteredCharacters.firstOrNull()?.id == character.id
                    CommonCharacterCard(
                        character = character,
                        searchQuery = searchQuery,
                        isExpanded = expandedCharacterIds.contains(character.id),
                        onExpandClick = {
                            expandedCharacterIds = if (expandedCharacterIds.contains(character.id)) expandedCharacterIds - character.id else expandedCharacterIds + character.id
                        },
                        cardTargetName = if (isFirst) "FirstCharacterCard" else "CharacterCard",
                        onPositioned = { name, coords -> if (isFirst || name == "FlipButton") onTargetPositioned(name, coords) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showFilterBar = !showFilterBar },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .onGloballyPositioned { onTargetPositioned("FilterButtonOpen", it) },
            containerColor = if (selectedFactions.isNotEmpty() || selectedTags.isNotEmpty() || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(imageVector = if (showFilterBar) Icons.Default.FilterListOff else Icons.Default.FilterList, contentDescription = null)
        }
    }
}
