package io.github.garemat.lunachron.ui

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
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun CampaignCardListScreen(
    state: CharacterState,
    onNavigateBack: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var expandedCardIds by remember { mutableStateOf(setOf<Int>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFactions by remember { mutableStateOf(setOf<Faction>()) }
    val theme = LocalAppThemeProperties.current
    
    var showFilterBar by remember { mutableStateOf(true) }

    val filteredCards = remember(state.campaignCards, searchQuery, selectedFactions) {
        state.campaignCards.filter { card ->
            val matchesFaction = selectedFactions.isEmpty() || card.factions == null || card.factions.any { it in selectedFactions }
            val matchesSearch = searchQuery.isEmpty() || 
                card.name.contains(searchQuery, ignoreCase = true) ||
                card.description.contains(searchQuery, ignoreCase = true) ||
                card.timing.contains(searchQuery, ignoreCase = true)
            matchesFaction && matchesSearch
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
                        selectedTags = emptySet(),
                        onTagsChange = { },
                        availableTags = emptyList(),
                        isFactionFixed = false,
                        showCollapseAll = expandedCardIds.isNotEmpty(),
                        onCollapseAll = { expandedCardIds = emptySet() },
                        onClearAll = {
                            searchQuery = ""
                            selectedFactions = emptySet()
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
                if (filteredCards.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No campaign cards match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(filteredCards, key = { it.id }) { card ->
                    CampaignCardUI(
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
            containerColor = if (selectedFactions.isNotEmpty() || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(imageVector = if (showFilterBar) Icons.Default.FilterListOff else Icons.Default.FilterList, contentDescription = null)
        }
    }
}
