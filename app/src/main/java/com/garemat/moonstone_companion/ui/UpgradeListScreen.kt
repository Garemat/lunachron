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
fun UpgradeListScreen(
    state: CharacterState,
    onNavigateBack: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var expandedCardIds by remember { mutableStateOf(setOf<Int>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFactions by remember { mutableStateOf(setOf<Faction>()) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    val theme = LocalAppThemeProperties.current
    
    val availableTags = remember(state.upgradeCards) {
        state.upgradeCards
            .flatMap { it.tags ?: emptyList() }
            .distinct()
            .sorted()
    }
    
    var showFilterBar by remember { mutableStateOf(true) }

    val filteredUpgrades = remember(state.upgradeCards, searchQuery, selectedFactions, selectedTags) {
        state.upgradeCards.filter { card ->
            // If a card doesn't mention a tag/faction, assume it applies for all (restriction logic)
            val matchesFaction = selectedFactions.isEmpty() || card.factions == null || card.factions.any { it in selectedFactions }
            val matchesTags = selectedTags.isEmpty() || card.tags == null || card.tags.any { it in selectedTags }
            val matchesSearch = searchQuery.isEmpty() || 
                card.name.contains(searchQuery, ignoreCase = true) ||
                card.abilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
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
                        isFactionFixed = false,
                        showCollapseAll = expandedCardIds.isNotEmpty(),
                        onCollapseAll = { expandedCardIds = emptySet() },
                        onClearAll = {
                            searchQuery = ""
                            selectedFactions = emptySet()
                            selectedTags = emptySet()
                            expandedCardIds = emptySet()
                        },
                        onTargetPositioned = onTargetPositioned
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing / 2),
                contentPadding = PaddingValues(top = theme.verticalSpacing / 2, bottom = 100.dp, start = theme.screenPadding, end = theme.screenPadding),
                state = rememberLazyListState()
            ) {
                if (filteredUpgrades.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No upgrades match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(filteredUpgrades, key = { it.id }) { card ->
                    UpgradeCardUI(
                        card = card,
                        searchQuery = searchQuery,
                        isExpanded = expandedCardIds.contains(card.id),
                        onExpandClick = {
                            expandedCardIds = if (expandedCardIds.contains(card.id)) expandedCardIds - card.id else expandedCardIds + card.id
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showFilterBar = !showFilterBar },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = if (selectedFactions.isNotEmpty() || selectedTags.isNotEmpty() || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(imageVector = if (showFilterBar) Icons.Default.FilterListOff else Icons.Default.FilterList, contentDescription = null)
        }
    }
}
