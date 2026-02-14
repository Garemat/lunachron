package com.garemat.moonstone_companion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTroupeScreen(
    viewModel: CharacterViewModel,
    state: CharacterState,
    onNavigateBack: () -> Unit,
    currentTutorialStep: TutorialStep? = null,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var expandedCharacterId by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isHeaderVisible by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSaveValidationDialog by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    
    val isTutorialActive = currentTutorialStep != null

    // Auto-open settings dialog during tutorial step
    LaunchedEffect(currentTutorialStep) {
        if (currentTutorialStep?.targetName == "AutoSelectSwitch") {
            showSettingsDialog = true
        } else if (currentTutorialStep?.targetName != "SettingsCog" && showSettingsDialog) {
            if (currentTutorialStep?.targetName != "AutoSelectSwitch") {
                showSettingsDialog = false
            }
        }
    }

    val availableTags = remember(state.characters, viewModel.selectedTroupeFaction) {
        state.characters
            .filter { it.factions.contains(viewModel.selectedTroupeFaction) }
            .flatMap { it.tags }
            .distinct()
            .sorted()
    }
    
    val selectedTags = remember { mutableStateListOf<String>() }

    LaunchedEffect(availableTags) {
        val toRemove = selectedTags.filter { it !in availableTags }
        toRemove.forEach { selectedTags.remove(it) }
    }

    val availableCharacters = remember(state.characters, viewModel.selectedTroupeFaction, searchQuery, selectedTags.toList()) {
        state.characters
            .filter { it.factions.contains(viewModel.selectedTroupeFaction) }
            .filter { character ->
                val matchesSearch = searchQuery.isEmpty() || 
                    character.name.contains(searchQuery, ignoreCase = true) ||
                    character.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
                
                val matchesTags = selectedTags.isEmpty() || 
                    selectedTags.all { tag -> character.tags.contains(tag) }
                
                matchesSearch && matchesTags
            }
    }

    val (selectedCharacters, unselectedCharacters) = remember(availableCharacters, viewModel.selectedCharacterIds) {
        availableCharacters.partition { viewModel.selectedCharacterIds.contains(it.id) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            AnimatedVisibility(visible = isHeaderVisible) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = viewModel.newTroupeName,
                        onValueChange = { 
                            viewModel.newTroupeName = it 
                            if (it.isNotBlank()) isNameError = false
                        },
                        label = { Text("Troupe Name") },
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("TroupeName", it) },
                        singleLine = true,
                        isError = isNameError,
                        shape = if (isMoonstone) RoundedCornerShape(0.dp) else OutlinedTextFieldDefaults.shape
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Select Faction Symbol", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .onGloballyPositioned { onTargetPositioned("FactionSymbols", it) },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Faction.entries.forEach { faction ->
                            val isSelected = viewModel.selectedTroupeFaction == faction
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) getFactionColor(faction) else Color.Transparent)
                                    .border(2.dp, getFactionColor(faction), CircleShape)
                                    .clickable { 
                                        viewModel.selectedTroupeFaction = faction
                                        viewModel.selectedCharacterIds = emptySet()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                FactionSymbol(
                                    faction = faction,
                                    modifier = Modifier.fillMaxSize(),
                                    tint = if (isSelected) Color.White else getFactionColor(faction)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = if (isMoonstone) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableTags.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("CharacterTags", it) },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(availableTags) { tag ->
                                val isSelected = selectedTags.contains(tag)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                                    },
                                    label = { Text(tag) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = if (isMoonstone) RoundedCornerShape(0.dp) else FilterChipDefaults.shape
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Text(
                text = "Add Characters (${viewModel.selectedCharacterIds.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            if (availableCharacters.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isEmpty() && selectedTags.isEmpty()) "No characters found for this faction." else "No characters match search/tags.",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
                ) {
                    if (selectedCharacters.isNotEmpty()) {
                        item(key = "selected_header") {
                            Text(
                                "Selected Characters",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(selectedCharacters, key = { it.id }) { character ->
                            CharacterSelectionCard(
                                character = character,
                                searchQuery = searchQuery,
                                isSelected = true,
                                isExpanded = expandedCharacterId == character.id,
                                onExpandClick = {
                                    expandedCharacterId = if (expandedCharacterId == character.id) null else character.id
                                },
                                onSelectionChange = { selected ->
                                    val current = viewModel.selectedCharacterIds.toMutableSet()
                                    if (selected) current.add(character.id) else current.remove(character.id)
                                    viewModel.selectedCharacterIds = current
                                }
                            )
                        }
                    }

                    if (unselectedCharacters.isNotEmpty()) {
                        item(key = "available_header") {
                            Text(
                                if (selectedCharacters.isEmpty()) "Available Characters" else "Other Characters",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(unselectedCharacters, key = { it.id }) { character ->
                            CharacterSelectionCard(
                                character = character,
                                searchQuery = searchQuery,
                                isSelected = false,
                                isExpanded = expandedCharacterId == character.id,
                                onExpandClick = {
                                    expandedCharacterId = if (expandedCharacterId == character.id) null else character.id
                                },
                                onSelectionChange = { selected ->
                                    val current = viewModel.selectedCharacterIds.toMutableSet()
                                    if (selected) current.add(character.id) else current.remove(character.id)
                                    viewModel.selectedCharacterIds = current
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Toggle Visibility
                SmallFloatingActionButton(
                    onClick = { isHeaderVisible = !isHeaderVisible },
                    shape = if (isMoonstone) RoundedCornerShape(0.dp) else FloatingActionButtonDefaults.smallShape
                ) {
                    Icon(
                        imageVector = if (isHeaderVisible) Icons.Default.FilterListOff else Icons.Default.FilterList,
                        contentDescription = "Toggle Header"
                    )
                }

                // Settings
                SmallFloatingActionButton(
                    onClick = { showSettingsDialog = true },
                    shape = if (isMoonstone) RoundedCornerShape(0.dp) else FloatingActionButtonDefaults.smallShape,
                    modifier = Modifier.onGloballyPositioned { onTargetPositioned("SettingsCog", it) }
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Troupe Settings")
                }

                // Save
                FloatingActionButton(
                    onClick = {
                        if (viewModel.newTroupeName.isBlank()) {
                            isNameError = true
                            scope.launch {
                                snackbarHostState.showSnackbar("Troupe name can't be empty")
                            }
                        } else if (viewModel.autoSelectMembers) {
                            isNameError = false
                            showSaveValidationDialog = true
                        } else {
                            isNameError = false
                            viewModel.onEvent(CharacterEvent.SaveTroupe)
                            onNavigateBack()
                        }
                    },
                    shape = if (isMoonstone) RoundedCornerShape(0.dp) else FloatingActionButtonDefaults.shape,
                    containerColor = if (viewModel.newTroupeName.isNotBlank() && viewModel.selectedCharacterIds.isNotEmpty())
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save Troupe")
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )

        // Use custom overlay for settings instead of AlertDialog to keep it behind Tutorial Dialog
        if (showSettingsDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = !isTutorialActive) { showSettingsDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp)
                        .clickable(enabled = false) { /* Prevent dismissing when clicking content */ },
                    shape = if (isMoonstone) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(text = "Troupe Settings", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Select Members", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Skips the team selection prompt before games.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = viewModel.autoSelectMembers,
                                onCheckedChange = { if (!isTutorialActive) viewModel.autoSelectMembers = it },
                                enabled = !isTutorialActive,
                                modifier = Modifier.onGloballyPositioned { onTargetPositioned("AutoSelectSwitch", it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { if (!isTutorialActive) showSettingsDialog = false }) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }

        if (showSaveValidationDialog) {
            val count = viewModel.selectedCharacterIds.size
            AlertDialog(
                onDismissRequest = { showSaveValidationDialog = false },
                shape = if (isMoonstone) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp),
                title = { Text("Save Troupe") },
                text = {
                    Column {
                        Text("Auto Select is enabled. This troupe will be valid for:")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• 2 Players: ${if (count <= 6) "Valid" else "Invalid (Max 6)"}", color = if (count <= 6) Color(0xFF2E7D32) else Color.Red)
                        Text("• 3 Players: ${if (count <= 4) "Valid" else "Invalid (Max 4)"}", color = if (count <= 4) Color(0xFF2E7D32) else Color.Red)
                        Text("• 4 Players: ${if (count <= 3) "Valid" else "Invalid (Max 3)"}", color = if (count <= 3) Color(0xFF2E7D32) else Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Do you want to save anyway?")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.onEvent(CharacterEvent.SaveTroupe)
                            showSaveValidationDialog = false
                            onNavigateBack()
                        },
                        shape = if (isMoonstone) RoundedCornerShape(0.dp) else ButtonDefaults.shape
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveValidationDialog = false }) {
                        Text("Back to Edit")
                    }
                }
            )
        }
    }
}

@Composable
fun CharacterSelectionCard(
    character: Character,
    searchQuery: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onSelectionChange: (Boolean) -> Unit
) {
    CommonCharacterCard(
        character = character,
        searchQuery = searchQuery,
        isExpanded = isExpanded,
        onExpandClick = onExpandClick,
        selectionControl = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = {
                    if (!character.isUnselectableInTroupe) {
                        onSelectionChange(it)
                    }
                },
                enabled = !character.isUnselectableInTroupe
            )
        }
    )
}
