package com.garemat.moonstone_companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties
import kotlinx.coroutines.launch

enum class TroupeEditStage {
    SETUP, DASHBOARD, CHARACTER_SELECTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTroupeScreen(
    viewModel: CharacterViewModel,
    state: CharacterState,
    onNavigateBack: () -> Unit,
    currentTutorialStep: TutorialStep? = null,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    var editStage by remember { 
        mutableStateOf(if (viewModel.editingTroupeId == null) TroupeEditStage.SETUP else TroupeEditStage.DASHBOARD) 
    }
    
    val theme = LocalAppThemeProperties.current
    val originalName = remember { viewModel.newTroupeName }
    val originalFaction = remember { viewModel.selectedTroupeFaction }
    val originalCharacterIds = remember { viewModel.selectedCharacterIds }
    val originalIsTournament = remember { viewModel.isTournamentList }

    var expandedCharacterId by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSaveValidationDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showSelectionDiscardConfirmation by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    
    var temporarySelectedIds by remember { mutableStateOf(viewModel.selectedCharacterIds) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isTutorialActive = currentTutorialStep != null

    val hasDashboardChanges = viewModel.newTroupeName != originalName ||
            viewModel.selectedTroupeFaction != originalFaction ||
            viewModel.selectedCharacterIds != originalCharacterIds ||
            viewModel.isTournamentList != originalIsTournament

    val hasSelectionChanges = temporarySelectedIds != viewModel.selectedCharacterIds

    LaunchedEffect(currentTutorialStep) {
        if (currentTutorialStep?.targetName == "AutoSelectSwitch") showSettingsDialog = true
        else if (isTutorialActive && currentTutorialStep?.targetName != "SettingsCog" && showSettingsDialog) {
            if (currentTutorialStep?.targetName != "AutoSelectSwitch") showSettingsDialog = false
        }
    }

    val availableTags = remember(state.characters, viewModel.selectedTroupeFaction) {
        state.characters
            .filter { it.factions.contains(viewModel.selectedTroupeFaction) }
            .flatMap { it.tags }.distinct().sorted()
    }
    val selectedTags = remember { mutableStateListOf<String>() }

    LaunchedEffect(availableTags) {
        selectedTags.filter { it !in availableTags }.forEach { selectedTags.remove(it) }
    }

    BackHandler {
        when (editStage) {
            TroupeEditStage.SETUP -> onNavigateBack()
            TroupeEditStage.DASHBOARD -> { if (hasDashboardChanges) showDiscardConfirmation = true else onNavigateBack() }
            TroupeEditStage.CHARACTER_SELECTION -> { if (hasSelectionChanges) showSelectionDiscardConfirmation = true else editStage = TroupeEditStage.DASHBOARD }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when (editStage) {
                TroupeEditStage.SETUP -> SetupStage(viewModel, theme, isNameError, onNameChange = { isNameError = false }, onNext = {
                    if (viewModel.newTroupeName.isBlank()) {
                        isNameError = true
                        scope.launch { snackbarHostState.showSnackbar("Troupe name can't be empty") }
                    } else editStage = TroupeEditStage.DASHBOARD
                }, onBack = onNavigateBack, onTargetPositioned = onTargetPositioned)
                TroupeEditStage.DASHBOARD -> DashboardStage(viewModel, state, theme, expandedCharacterId, onExpandClick = { expandedCharacterId = if (expandedCharacterId == it) null else it }, onTargetPositioned = onTargetPositioned)
                TroupeEditStage.CHARACTER_SELECTION -> CharacterSelectionStage(state, theme, searchQuery, onSearchQueryChange = { searchQuery = it }, selectedTags, availableTags, temporarySelectedIds, onSelectionChange = { temporarySelectedIds = it }, expandedCharacterId, onExpandClick = { expandedCharacterId = if (expandedCharacterId == it) null else it }, onTargetPositioned, viewModel.selectedTroupeFaction)
            }
        }

        if (editStage == TroupeEditStage.DASHBOARD) {
            DashboardFabColumn(viewModel, theme, onAddCharacters = { temporarySelectedIds = viewModel.selectedCharacterIds; editStage = TroupeEditStage.CHARACTER_SELECTION }, onShowSettings = { showSettingsDialog = true }, onSave = {
                if (viewModel.newTroupeName.isBlank()) {
                    isNameError = true
                    scope.launch { snackbarHostState.showSnackbar("Troupe name can't be empty") }
                } else if (!viewModel.isTournamentList) showSaveValidationDialog = true
                else { viewModel.onEvent(CharacterEvent.SaveTroupe); onNavigateBack() }
            }, onTargetPositioned)
        } else if (editStage == TroupeEditStage.CHARACTER_SELECTION) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
                FloatingActionButton(onClick = { viewModel.selectedCharacterIds = temporarySelectedIds; editStage = TroupeEditStage.DASHBOARD }, shape = theme.navItemShape, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.onGloballyPositioned { onTargetPositioned("ConfirmSelectionButton", it) }) {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))

        if (showSettingsDialog) SettingsOverlay(viewModel, theme, isTutorialActive, onClose = { showSettingsDialog = false }, onTargetPositioned)
        if (showSaveValidationDialog) SaveValidationDialog(viewModel, theme, onDismiss = { showSaveValidationDialog = false }, onConfirm = { viewModel.onEvent(CharacterEvent.SaveTroupe); showSaveValidationDialog = false; onNavigateBack() })
        if (showDiscardConfirmation) DiscardConfirmationDialog(theme, onDismiss = { showDiscardConfirmation = false }, onConfirm = { showDiscardConfirmation = false; onNavigateBack() })
        if (showSelectionDiscardConfirmation) DiscardConfirmationDialog(theme, title = "Discard Selection?", onDismiss = { showSelectionDiscardConfirmation = false }, onConfirm = { showSelectionDiscardConfirmation = false; editStage = TroupeEditStage.DASHBOARD })
    }
}

@Composable
private fun SetupStage(viewModel: CharacterViewModel, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties, isNameError: Boolean, onNameChange: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit, onTargetPositioned: (String, LayoutCoordinates) -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("New Troupe", style = theme.headerStyle, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = viewModel.newTroupeName, onValueChange = { viewModel.newTroupeName = it; onNameChange(it) }, label = { Text("Troupe Name") }, modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("TroupeName", it) }, singleLine = true, isError = isNameError, shape = theme.cardShape)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Select Faction", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).onGloballyPositioned { onTargetPositioned("FactionSymbols", it) }, horizontalArrangement = Arrangement.SpaceEvenly) {
            Faction.entries.forEach { faction ->
                val isSelected = viewModel.selectedTroupeFaction == faction
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isSelected) getFactionColor(faction) else Color.Transparent).border(2.dp, getFactionColor(faction), CircleShape).clickable { viewModel.selectedTroupeFaction = faction; viewModel.selectedCharacterIds = emptySet() }, contentAlignment = Alignment.Center) {
                    FactionSymbol(faction = faction, modifier = Modifier.fillMaxSize().padding(8.dp), tint = if (isSelected) Color.White else getFactionColor(faction))
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = theme.cardShape) { Text("Next", style = MaterialTheme.typography.titleMedium) }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun DashboardStage(viewModel: CharacterViewModel, state: CharacterState, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties, expandedCharacterId: Int?, onExpandClick: (Int) -> Unit, onTargetPositioned: (String, LayoutCoordinates) -> Unit) {
    val selectedCharacters = remember(state.characters, viewModel.selectedCharacterIds) { state.characters.filter { viewModel.selectedCharacterIds.contains(it.id) } }
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FactionCircle(faction = viewModel.selectedTroupeFaction, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = viewModel.newTroupeName, style = theme.titleStyle.copy(fontSize = 24.sp), color = MaterialTheme.colorScheme.primary)
        }
        Text(text = "${selectedCharacters.size} Characters Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 44.dp, top = 2.dp))
        Spacer(modifier = Modifier.height(16.dp))
        if (selectedCharacters.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No characters added yet. Tap '+' to add some!", color = MaterialTheme.colorScheme.outline) }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(selectedCharacters, key = { it.id }) { character ->
                    CharacterSelectionCard(character = character, searchQuery = "", isSelected = true, isExpanded = expandedCharacterId == character.id, onExpandClick = { onExpandClick(character.id) }, onSelectionChange = { selected ->
                        val current = viewModel.selectedCharacterIds.toMutableSet()
                        if (selected) current.add(character.id) else current.remove(character.id)
                        viewModel.selectedCharacterIds = current
                    })
                }
            }
        }
    }
}

@Composable
private fun CharacterSelectionStage(state: CharacterState, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties, searchQuery: String, onSearchQueryChange: (String) -> Unit, selectedTags: MutableList<String>, availableTags: List<String>, selectedIds: Set<Int>, onSelectionChange: (Set<Int>) -> Unit, expandedCharacterId: Int?, onExpandClick: (Int) -> Unit, onTargetPositioned: (String, LayoutCoordinates) -> Unit, selectedTroupeFaction: Faction) {
    val factionCharacters = remember(state.characters, selectedTroupeFaction, searchQuery, selectedTags.toList()) {
        state.characters.filter { it.factions.contains(selectedTroupeFaction) }.filter { char ->
            val matchesSearch = searchQuery.isEmpty() || char.name.contains(searchQuery, ignoreCase = true) || char.tags.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in char.tags }
            matchesSearch && matchesTags
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Add Characters", style = theme.titleStyle.copy(fontSize = 24.sp), modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(value = searchQuery, onValueChange = onSearchQueryChange, placeholder = { Text("Search by name...") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Default.Clear, contentDescription = null) } }, singleLine = true, shape = theme.cardShape)
        if (availableTags.isNotEmpty()) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).onGloballyPositioned { onTargetPositioned("CharacterTags", it) }, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(availableTags) { tag ->
                    val isSelected = selectedTags.contains(tag)
                    FilterChip(selected = isSelected, onClick = { if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag) }, label = { Text(tag) }, leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null, shape = theme.cardShape)
                }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            items(factionCharacters, key = { it.id }) { character ->
                val isSelected = selectedIds.contains(character.id)
                CharacterSelectionCard(character = character, searchQuery = searchQuery, isSelected = isSelected, isExpanded = expandedCharacterId == character.id, onExpandClick = { onExpandClick(character.id) }, onSelectionChange = { selected ->
                    val current = selectedIds.toMutableSet()
                    if (selected) current.add(character.id) else current.remove(character.id)
                    onSelectionChange(current)
                })
            }
        }
    }
}

@Composable
private fun DiscardConfirmationDialog(theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties, title: String = "Discard Changes?", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = theme.cardShape, title = { Text(title) }, text = { Text("Unsaved changes will be discarded. Are you sure you want to go back?") }, confirmButton = { TextButton(onClick = onConfirm) { Text("Discard", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun DashboardFabColumn(viewModel: CharacterViewModel, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties, onAddCharacters: () -> Unit, onShowSettings: () -> Unit, onSave: () -> Unit, onTargetPositioned: (String, LayoutCoordinates) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallFloatingActionButton(onClick = onAddCharacters, shape = theme.navItemShape, modifier = Modifier.onGloballyPositioned { onTargetPositioned("AddCharactersButton", it) }) { Icon(Icons.Default.PersonAdd, contentDescription = null) }
            SmallFloatingActionButton(onClick = onShowSettings, shape = theme.navItemShape, modifier = Modifier.onGloballyPositioned { onTargetPositioned("SettingsCog", it) }) { Icon(Icons.Default.Settings, contentDescription = null) }
            FloatingActionButton(onClick = onSave, shape = theme.navItemShape, containerColor = if (viewModel.newTroupeName.isNotBlank() && viewModel.selectedCharacterIds.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.onGloballyPositioned { onTargetPositioned("SaveButton", it) }) { Icon(Icons.Default.Check, contentDescription = null) }
        }
    }
}

@Composable
private fun SettingsOverlay(viewModel: CharacterViewModel, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties, isTutorialActive: Boolean, onClose: () -> Unit, onTargetPositioned: (String, LayoutCoordinates) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().clickable(enabled = !isTutorialActive) { onClose() }, contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp).clickable(enabled = false) { }, shape = theme.cardShape, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, shadowElevation = theme.surfaceElevation) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Troupe Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tournament List", style = MaterialTheme.typography.bodyLarge)
                        Text("This troupe will use tournament settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = viewModel.isTournamentList, onCheckedChange = { if (!isTutorialActive) viewModel.isTournamentList = it }, enabled = !isTutorialActive, modifier = Modifier.onGloballyPositioned { onTargetPositioned("AutoSelectSwitch", it) })
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { if (!isTutorialActive) onClose() }) { Text("Close") } }
            }
        }
    }
}

@Composable
private fun SaveValidationDialog(viewModel: CharacterViewModel, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val count = viewModel.selectedCharacterIds.size
    AlertDialog(onDismissRequest = { onDismiss() }, shape = theme.cardShape, title = { Text("Save Troupe") }, text = {
        Column {
            Text("This troupe will automatically select all members. It will be valid for:")
            Spacer(modifier = Modifier.height(8.dp))
            Text("• 2 Players: ${if (count <= 6) "Valid" else "Invalid (Max 6)"}", color = if (count <= 6) Color(0xFF2E7D32) else Color.Red)
            Text("• 3 Players: ${if (count <= 4) "Valid" else "Invalid (Max 4)"}", color = if (count <= 4) Color(0xFF2E7D32) else Color.Red)
            Text("• 4 Players: ${if (count <= 3) "Valid" else "Invalid (Max 3)"}", color = if (count <= 3) Color(0xFF2E7D32) else Color.Red)
            Spacer(modifier = Modifier.height(16.dp)); Text("Do you want to save anyway?")
        }
    }, confirmButton = { Button(onClick = onConfirm, shape = theme.cardShape) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Back to Edit") } })
}

@Composable
fun CharacterSelectionCard(character: Character, searchQuery: String, isSelected: Boolean, isExpanded: Boolean, onExpandClick: () -> Unit, onSelectionChange: (Boolean) -> Unit) {
    CommonCharacterCard(character = character, searchQuery = searchQuery, isExpanded = isExpanded, onExpandClick = onExpandClick, selectionControl = { Checkbox(checked = isSelected, onCheckedChange = { if (!character.isUnselectableInTroupe) onSelectionChange(it) }, enabled = !character.isUnselectableInTroupe) })
}
