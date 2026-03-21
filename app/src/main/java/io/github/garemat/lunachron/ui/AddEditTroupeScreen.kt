package io.github.garemat.lunachron.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import kotlinx.coroutines.launch
import java.io.File

private enum class TroupeLayoutMode { SINGLE_COLUMN, PORTRAIT_COLUMN }

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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    val originalName = remember { viewModel.newTroupeName }
    val originalFaction = remember { viewModel.selectedTroupeFaction }
    val originalCharacterIds = remember { viewModel.selectedCharacterIds }
    val originalIsTournament = remember { viewModel.isTournamentList }
    val originalIsCampaign = remember { viewModel.isCampaignTroupe }

    var expandedCharacterId by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showTroupeTypeSheet by remember { mutableStateOf(false) }
    var showCampaignCardSheet by remember { mutableStateOf(false) }
    var showSaveValidationDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showSelectionDiscardConfirmation by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    var troupeLayout by remember {
        mutableStateOf(
            TroupeLayoutMode.valueOf(
                prefs.getString("troupe_layout_mode", TroupeLayoutMode.SINGLE_COLUMN.name)
                    ?: TroupeLayoutMode.SINGLE_COLUMN.name
            )
        )
    }

    var temporarySelectedIds by remember { mutableStateOf(viewModel.selectedCharacterIds) }

    // Campaign specific state
    var equippedUpgrades by remember { mutableStateOf(emptyMap<Int, List<Int>>()) }
    var campaignCards by remember { mutableStateOf(emptyList<TroupeCampaignCard>()) }
    var victoryPoints by remember { mutableIntStateOf(0) }

    var targetCharIdxForUpgrade by remember { mutableIntStateOf(-1) }
    var showUpgradeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.editingTroupeId) {
        if (viewModel.editingTroupeId != null) {
            val troupe = state.troupes.find { it.id == viewModel.editingTroupeId }
            if (troupe != null) {
                equippedUpgrades = troupe.equippedUpgrades
                campaignCards = troupe.campaignCards
                victoryPoints = troupe.victoryPoints
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val hasDashboardChanges = viewModel.newTroupeName != originalName ||
            viewModel.selectedTroupeFaction != originalFaction ||
            viewModel.selectedCharacterIds != originalCharacterIds ||
            viewModel.isTournamentList != originalIsTournament ||
            viewModel.isCampaignTroupe != originalIsCampaign

    val hasSelectionChanges = temporarySelectedIds != viewModel.selectedCharacterIds

    val availableTags = remember(state.characters, viewModel.selectedTroupeFaction) {
        state.characters
            .filter { it.factions.contains(viewModel.selectedTroupeFaction) }
            .flatMap { it.keywords }.distinct().sorted()
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

    fun doSave() {
        if (viewModel.newTroupeName.isBlank()) {
            isNameError = true
            scope.launch { snackbarHostState.showSnackbar("Troupe name can't be empty") }
        } else if (!viewModel.isTournamentList && !viewModel.isCampaignTroupe) {
            showSaveValidationDialog = true
        } else {
            val isForCampaign = viewModel.pendingCampaignPlayerId != null
            viewModel.onEvent(
                CharacterEvent.SaveTroupeWithMetadata(
                    victoryPoints = victoryPoints,
                    equippedUpgrades = equippedUpgrades,
                    campaignCards = campaignCards
                )
            )
            if (!isForCampaign) onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when (editStage) {
                TroupeEditStage.SETUP -> SetupStage(
                    viewModel = viewModel,
                    state = state,
                    theme = theme,
                    isNameError = isNameError,
                    onNameChange = { isNameError = false },
                    onNext = {
                        if (viewModel.newTroupeName.isBlank()) {
                            isNameError = true
                            scope.launch { snackbarHostState.showSnackbar("Troupe name can't be empty") }
                        } else editStage = TroupeEditStage.DASHBOARD
                    },
                    onBack = onNavigateBack,
                    onImportSuccess = onNavigateBack,
                    onTargetPositioned = onTargetPositioned
                )

                TroupeEditStage.DASHBOARD -> DashboardStage(
                    viewModel = viewModel,
                    state = state,
                    theme = theme,
                    expandedCharacterId = expandedCharacterId,
                    onExpandClick = { expandedCharacterId = if (expandedCharacterId == it) null else it },
                    layoutMode = troupeLayout,
                    onToggleLayout = {
                        val next = if (troupeLayout == TroupeLayoutMode.SINGLE_COLUMN)
                            TroupeLayoutMode.PORTRAIT_COLUMN else TroupeLayoutMode.SINGLE_COLUMN
                        troupeLayout = next
                        prefs.edit().putString("troupe_layout_mode", next.name).apply()
                    },
                    equippedUpgrades = equippedUpgrades,
                    campaignCards = campaignCards,
                    victoryPoints = victoryPoints,
                    onVictoryPointsChange = { victoryPoints = it },
                    onManageUpgrades = { idx -> targetCharIdxForUpgrade = idx; showUpgradeDialog = true },
                    onManageCampaignCards = { showCampaignCardSheet = true },
                    onShowTroupeTypeSheet = { showTroupeTypeSheet = true },
                    onAddCharacters = { temporarySelectedIds = viewModel.selectedCharacterIds; editStage = TroupeEditStage.CHARACTER_SELECTION },
                    onTargetPositioned = onTargetPositioned
                )

                TroupeEditStage.CHARACTER_SELECTION -> CharacterSelectionStage(
                    state, theme, searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedTags, availableTags,
                    temporarySelectedIds,
                    onSelectionChange = { temporarySelectedIds = it },
                    expandedCharacterId,
                    onExpandClick = { expandedCharacterId = if (expandedCharacterId == it) null else it },
                    onTargetPositioned,
                    viewModel.selectedTroupeFaction
                )
            }
        }

        if (editStage == TroupeEditStage.DASHBOARD) {
            DashboardFabColumn(
                viewModel = viewModel,
                theme = theme,
                onSave = { doSave() },
                onTargetPositioned = onTargetPositioned
            )
        } else if (editStage == TroupeEditStage.CHARACTER_SELECTION) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
                FloatingActionButton(
                    onClick = { viewModel.selectedCharacterIds = temporarySelectedIds; editStage = TroupeEditStage.DASHBOARD },
                    shape = theme.navItemShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.onGloballyPositioned { onTargetPositioned("ConfirmSelectionButton", it) }
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )

        if (showUpgradeDialog && targetCharIdxForUpgrade >= 0) {
            UpgradeManagementDialog(
                state = state,
                theme = theme,
                selectedIds = equippedUpgrades[targetCharIdxForUpgrade]?.toSet() ?: emptySet(),
                onSelectionChange = { ids ->
                    val newMap = equippedUpgrades.toMutableMap()
                    if (ids.isEmpty()) newMap.remove(targetCharIdxForUpgrade)
                    else newMap[targetCharIdxForUpgrade] = ids.toList()
                    equippedUpgrades = newMap
                },
                onDismiss = { showUpgradeDialog = false }
            )
        }

        if (showSaveValidationDialog) {
            SaveValidationDialog(
                viewModel = viewModel,
                theme = theme,
                onDismiss = { showSaveValidationDialog = false },
                onConfirm = {
                    viewModel.onEvent(
                        CharacterEvent.SaveTroupeWithMetadata(
                            victoryPoints = victoryPoints,
                            equippedUpgrades = equippedUpgrades,
                            campaignCards = campaignCards
                        )
                    )
                    showSaveValidationDialog = false
                    onNavigateBack()
                }
            )
        }

        if (showDiscardConfirmation) {
            DiscardConfirmationDialog(
                theme = theme,
                onDismiss = { showDiscardConfirmation = false },
                onConfirm = { showDiscardConfirmation = false; onNavigateBack() }
            )
        }

        if (showSelectionDiscardConfirmation) {
            DiscardConfirmationDialog(
                theme = theme,
                title = "Discard Selection?",
                onDismiss = { showSelectionDiscardConfirmation = false },
                onConfirm = { showSelectionDiscardConfirmation = false; editStage = TroupeEditStage.DASHBOARD }
            )
        }
    }

    if (showTroupeTypeSheet) {
        TroupeTypeEditSheet(
            currentIsTournament = viewModel.isTournamentList,
            currentIsCampaign = viewModel.isCampaignTroupe,
            hasCampaignData = campaignCards.isNotEmpty() || equippedUpgrades.isNotEmpty() || victoryPoints > 0,
            theme = theme,
            onConfirm = { isTournament, isCampaign ->
                val wasCampaign = viewModel.isCampaignTroupe
                viewModel.isTournamentList = isTournament
                viewModel.isCampaignTroupe = isCampaign
                if (wasCampaign && !isCampaign) {
                    equippedUpgrades = emptyMap()
                    campaignCards = emptyList()
                    victoryPoints = 0
                }
                showTroupeTypeSheet = false
            },
            onDismiss = { showTroupeTypeSheet = false }
        )
    }

    if (showCampaignCardSheet) {
        CampaignCardManagementSheet(
            campaignCards = campaignCards,
            allCampaignCards = state.campaignCards,
            theme = theme,
            onCampaignCardsChange = { campaignCards = it },
            onDismiss = { showCampaignCardSheet = false }
        )
    }
}

// ── Setup Stage ────────────────────────────────────────────────────────────────

@Composable
private fun SetupStage(
    viewModel: CharacterViewModel,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    isNameError: Boolean,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onImportSuccess: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    val tabs = listOf("Create", "Import")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "New Troupe",
            style = theme.headerStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> CreateTroupeTab(viewModel, theme, isNameError, onNameChange, onNext, onBack, onTargetPositioned)
                1 -> ImportTroupeTab(viewModel, state, theme, onBack, onImportSuccess)
            }
        }
    }
}

@Composable
private fun CreateTroupeTab(
    viewModel: CharacterViewModel,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    isNameError: Boolean,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = viewModel.newTroupeName,
            onValueChange = { viewModel.newTroupeName = it; onNameChange(it) },
            label = { Text("Troupe Name") },
            modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("TroupeName", it) },
            singleLine = true,
            isError = isNameError,
            shape = theme.cardShape
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Troupe type selection
        Text("Troupe Type", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.Start))
        SelectionOption(
            title = "Normal",
            subtitle = "Standard casual games.",
            selected = !viewModel.isTournamentList && !viewModel.isCampaignTroupe,
            onSelect = { viewModel.isTournamentList = false; viewModel.isCampaignTroupe = false },
            theme = theme
        )
        SelectionOption(
            title = "Tournament",
            subtitle = "Structured competitive play. No upgrade or campaign card tracking.",
            selected = viewModel.isTournamentList && !viewModel.isCampaignTroupe,
            onSelect = { viewModel.isTournamentList = true; viewModel.isCampaignTroupe = false },
            theme = theme
        )
        SelectionOption(
            title = "Campaign",
            subtitle = "Ongoing story play. Tracks upgrades, victory points and campaign cards.",
            selected = viewModel.isCampaignTroupe,
            onSelect = { viewModel.isTournamentList = false; viewModel.isCampaignTroupe = true },
            theme = theme
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Select Faction", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                .onGloballyPositioned { onTargetPositioned("FactionSymbols", it) },
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Faction.entries.forEach { faction ->
                val isSelected = viewModel.selectedTroupeFaction == faction
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(if (isSelected) getFactionColor(faction) else Color.Transparent)
                        .border(2.dp, getFactionColor(faction), CircleShape)
                        .clickable { viewModel.selectedTroupeFaction = faction; viewModel.selectedCharacterIds = emptySet() },
                    contentAlignment = Alignment.Center
                ) {
                    FactionSymbol(
                        faction = faction,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        tint = if (isSelected) Color.White else getFactionColor(faction)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = theme.cardShape) {
            Text("Next", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ImportTroupeTab(
    viewModel: CharacterViewModel,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onBack: () -> Unit,
    onImportSuccess: () -> Unit
) {
    val context = LocalContext.current
    var importCode by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showScanner = true
        else Toast.makeText(context, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
    }

    fun requestScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun tryImport(code: String) {
        val troupe = viewModel.importTroupe(code.trim(), state.characters, state.upgradeCards)
        if (troupe != null) {
            viewModel.saveTroupe(troupe)
            onImportSuccess()
        } else {
            importError = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = importCode,
            onValueChange = { importCode = it; importError = false },
            label = { Text("Paste Share Code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = importError,
            supportingText = if (importError) {
                { Text("Invalid code — check the text and try again.", color = MaterialTheme.colorScheme.error) }
            } else null,
            shape = theme.cardShape
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { requestScan() },
                modifier = Modifier.weight(1f),
                shape = theme.cardShape
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan QR")
            }
            Button(
                onClick = { tryImport(importCode) },
                modifier = Modifier.weight(1f),
                enabled = importCode.isNotBlank(),
                shape = theme.cardShape
            ) {
                Text("Import")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = MaterialTheme.colorScheme.secondary)
        }
    }

    if (showScanner) {
        Dialog(
            onDismissRequest = { showScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                QrScanner(onResult = { result ->
                    showScanner = false
                    tryImport(result)
                })
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = Color.White)
                }
            }
        }
    }
}

// ── Dashboard Stage ────────────────────────────────────────────────────────────

@Composable
private fun DashboardStage(
    viewModel: CharacterViewModel,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    expandedCharacterId: Int?,
    onExpandClick: (Int) -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit,
    layoutMode: TroupeLayoutMode,
    onToggleLayout: () -> Unit,
    equippedUpgrades: Map<Int, List<Int>>,
    campaignCards: List<TroupeCampaignCard>,
    victoryPoints: Int,
    onVictoryPointsChange: (Int) -> Unit,
    onManageUpgrades: (Int) -> Unit,
    onManageCampaignCards: () -> Unit,
    onShowTroupeTypeSheet: () -> Unit,
    onAddCharacters: () -> Unit
) {
    val selectedCharacters = remember(state.characters, viewModel.selectedCharacterIds) {
        state.characters.filter { viewModel.selectedCharacterIds.contains(it.id) }
    }

    if (layoutMode == TroupeLayoutMode.SINGLE_COLUMN) {
        SingleColumnDashboard(
            viewModel = viewModel,
            state = state,
            theme = theme,
            selectedCharacters = selectedCharacters,
            expandedCharacterId = expandedCharacterId,
            onExpandClick = onExpandClick,
            onToggleLayout = onToggleLayout,
            onAddCharacters = onAddCharacters,
            equippedUpgrades = equippedUpgrades,
            campaignCards = campaignCards,
            victoryPoints = victoryPoints,
            onVictoryPointsChange = onVictoryPointsChange,
            onManageUpgrades = onManageUpgrades,
            onManageCampaignCards = onManageCampaignCards,
            onShowTroupeTypeSheet = onShowTroupeTypeSheet,
            onTargetPositioned = onTargetPositioned
        )
    } else {
        PortraitColumnDashboard(
            viewModel = viewModel,
            state = state,
            theme = theme,
            selectedCharacters = selectedCharacters,
            onToggleLayout = onToggleLayout,
            equippedUpgrades = equippedUpgrades,
            campaignCards = campaignCards,
            victoryPoints = victoryPoints,
            onVictoryPointsChange = onVictoryPointsChange,
            onManageUpgrades = onManageUpgrades,
            onManageCampaignCards = onManageCampaignCards,
            onShowTroupeTypeSheet = onShowTroupeTypeSheet,
            onAddCharacters = onAddCharacters,
            onTargetPositioned = onTargetPositioned
        )
    }
}

// ── Shared Troupe Header ───────────────────────────────────────────────────────

@Composable
private fun TroupeHeader(
    viewModel: CharacterViewModel,
    allCampaignCards: List<CampaignCard>,
    campaignCards: List<TroupeCampaignCard>,
    victoryPoints: Int,
    onVictoryPointsChange: (Int) -> Unit,
    onManageCampaignCards: () -> Unit,
    onShowTroupeTypeSheet: () -> Unit,
    onToggleLayout: () -> Unit,
    layoutMode: TroupeLayoutMode,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FactionCircle(faction = viewModel.selectedTroupeFaction, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = viewModel.newTroupeName,
                style = LocalAppThemeProperties.current.titleStyle.copy(fontSize = 24.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            // Layout toggle
            IconButton(onClick = onToggleLayout) {
                Icon(
                    imageVector = if (layoutMode == TroupeLayoutMode.SINGLE_COLUMN)
                        Icons.AutoMirrored.Filled.ViewSidebar else Icons.Default.ViewAgenda,
                    contentDescription = "Toggle layout",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (viewModel.isCampaignTroupe) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Victory Points:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onVictoryPointsChange((victoryPoints - 1).coerceAtLeast(0)) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = null)
                }
                Text(
                    text = victoryPoints.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = { onVictoryPointsChange(victoryPoints + 1) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }

        // Troupe type badge with pencil
        Row(
            modifier = Modifier.padding(start = 44.dp, top = 4.dp, bottom = 4.dp).clickable { onShowTroupeTypeSheet() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val typeLabel = when {
                viewModel.isCampaignTroupe -> "Campaign Troupe"
                viewModel.isTournamentList -> "Tournament List"
                else -> "Normal Troupe"
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.onGloballyPositioned { onTargetPositioned("TroupeTypeBadge", it) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Edit type", modifier = Modifier.size(12.dp))
                }
            }
        }

        if (viewModel.isCampaignTroupe) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // Campaign cards strip
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Campaign Cards",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManageCampaignCards, modifier = Modifier.height(28.dp)) {
                    Text("Manage", style = MaterialTheme.typography.labelSmall)
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                val readyCards = campaignCards.filter { it.usedInGame == null }
                if (readyCards.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                            color = Color.Transparent
                        ) {
                            Text(
                                "No cards yet",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    items(readyCards) { troupeCard ->
                        val card = allCampaignCards.find { it.id == troupeCard.cardId }
                        if (card != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    "● ${card.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
    }
}

// ── Single Column Dashboard ────────────────────────────────────────────────────

@Composable
private fun SingleColumnDashboard(
    viewModel: CharacterViewModel,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    selectedCharacters: List<Character>,
    expandedCharacterId: Int?,
    onExpandClick: (Int) -> Unit,
    onToggleLayout: () -> Unit,
    onAddCharacters: () -> Unit,
    equippedUpgrades: Map<Int, List<Int>>,
    campaignCards: List<TroupeCampaignCard>,
    victoryPoints: Int,
    onVictoryPointsChange: (Int) -> Unit,
    onManageUpgrades: (Int) -> Unit,
    onManageCampaignCards: () -> Unit,
    onShowTroupeTypeSheet: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TroupeHeader(
            viewModel = viewModel,
            allCampaignCards = state.campaignCards,
            campaignCards = campaignCards,
            victoryPoints = victoryPoints,
            onVictoryPointsChange = onVictoryPointsChange,
            onManageCampaignCards = onManageCampaignCards,
            onShowTroupeTypeSheet = onShowTroupeTypeSheet,
            onToggleLayout = onToggleLayout,
            layoutMode = TroupeLayoutMode.SINGLE_COLUMN,
            onTargetPositioned = onTargetPositioned
        )

        if (selectedCharacters.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No characters added yet. Tap '+' to add some!", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
            ) {
                items(
                    items = selectedCharacters.indices.toList(),
                    key = { idx -> selectedCharacters[idx].id }
                ) { idx ->
                    val character = selectedCharacters[idx]
                    SwipeRevealCharacterItem(
                        modifier = Modifier.animateItem(),
                        character = character,
                        charIdx = idx,
                        isExpanded = expandedCharacterId == character.id,
                        onExpandClick = { onExpandClick(character.id) },
                        isCampaignTroupe = viewModel.isCampaignTroupe,
                        equippedUpgrades = equippedUpgrades,
                        upgradeCards = state.upgradeCards,
                        onManageUpgrades = onManageUpgrades,
                        onRemove = { viewModel.selectedCharacterIds = viewModel.selectedCharacterIds - character.id },
                        theme = theme
                    )
                }

                item {
                    OutlinedCard(
                        onClick = onAddCharacters,
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape,
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+ Add Character",
                                style = theme.headerStyle.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeRevealCharacterItem(
    modifier: Modifier = Modifier,
    character: Character,
    charIdx: Int,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    isCampaignTroupe: Boolean,
    equippedUpgrades: Map<Int, List<Int>>,
    upgradeCards: List<UpgradeCard>,
    onManageUpgrades: (Int) -> Unit,
    onRemove: () -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { 72.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var isRevealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(theme.cardShape)
    ) {
        // Background — error color with trash icon on the right
        Box(
            modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .padding(end = 20.dp)
                    .clickable { onRemove() }
            )
        }

        // Foreground card — slides left to reveal background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealWidthPx, 0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            val target = when {
                                velocity < -300f -> -revealWidthPx   // fast left swipe → reveal
                                velocity > 300f -> 0f                // fast right swipe → close
                                offsetX.value < -revealWidthPx * 0.4f -> -revealWidthPx
                                else -> 0f
                            }
                            offsetX.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
                            isRevealed = target < 0f
                        }
                    }
                )
        ) {
            DashboardCharacterItem(
                character = character,
                charIdx = charIdx,
                isExpanded = isExpanded,
                onExpandClick = onExpandClick,
                isCampaignTroupe = isCampaignTroupe,
                equippedUpgrades = equippedUpgrades,
                upgradeCards = upgradeCards,
                onManageUpgrades = onManageUpgrades,
                isRevealed = isRevealed,
                onRemove = onRemove,
                theme = theme
            )
        }
    }
}

@Composable
private fun DashboardCharacterItem(
    character: Character,
    charIdx: Int,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    isCampaignTroupe: Boolean,
    equippedUpgrades: Map<Int, List<Int>>,
    upgradeCards: List<UpgradeCard>,
    onManageUpgrades: (Int) -> Unit,
    isRevealed: Boolean,
    onRemove: () -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    var isFlipped by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val bgImageFile = remember(character.imageName) {
        character.imageName?.let { name ->
            val dir = File(context.filesDir, "images")
            listOf("", ".jpg", ".png", ".webp").firstNotNullOfOrNull { ext ->
                File(dir, "$name$ext").takeIf { it.exists() }
            }
        }
    }

    ThemedCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column {
            // Header row (no checkbox - swipe to remove)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onExpandClick() }.padding(theme.cardContentPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CharacterPortrait(character = character, size = 40.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = character.keywords.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isRevealed) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove character",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp).clickable { onRemove() }
                    )
                } else {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (isExpanded) {
                if (theme.showCardDivider) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (theme.showBackgroundImageOverlay && bgImageFile != null) {
                        AsyncImage(
                            model = bgImageFile,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize().alpha(0.25f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(modifier = Modifier.padding(theme.cardContentPadding)) {
                        if (!isFlipped) CharacterFront(character = character, searchQuery = "", onFlip = { isFlipped = true })
                        else CharacterBack(character = character, searchQuery = "", onFlip = { isFlipped = false })
                    }
                }
            }

            // Upgrade strip — always at bottom of card when campaign troupe
            if (isCampaignTroupe) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                UpgradeStrip(
                    charIdx = charIdx,
                    equippedUpgrades = equippedUpgrades,
                    upgradeCards = upgradeCards,
                    onManageUpgrades = onManageUpgrades
                )
            }
        }
    }
}

@Composable
private fun UpgradeStrip(
    charIdx: Int,
    equippedUpgrades: Map<Int, List<Int>>,
    upgradeCards: List<UpgradeCard>,
    onManageUpgrades: (Int) -> Unit
) {
    val charUpgrades = equippedUpgrades[charIdx] ?: emptyList()
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (charUpgrades.isEmpty()) "No Upgrades" else "${charUpgrades.size} Upgrade${if (charUpgrades.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onManageUpgrades(charIdx) }, modifier = Modifier.height(28.dp)) {
                Text("Manage Upgrades", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (charUpgrades.isNotEmpty()) {
            val names = charUpgrades.mapNotNull { id -> upgradeCards.find { it.id == id }?.name }
            Text(
                text = names.joinToString("  •  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Portrait Column Dashboard ──────────────────────────────────────────────────

@Composable
private fun PortraitColumnDashboard(
    viewModel: CharacterViewModel,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    selectedCharacters: List<Character>,
    onToggleLayout: () -> Unit,
    equippedUpgrades: Map<Int, List<Int>>,
    campaignCards: List<TroupeCampaignCard>,
    victoryPoints: Int,
    onVictoryPointsChange: (Int) -> Unit,
    onManageUpgrades: (Int) -> Unit,
    onManageCampaignCards: () -> Unit,
    onShowTroupeTypeSheet: () -> Unit,
    onAddCharacters: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    var selectedCharId by remember { mutableStateOf(selectedCharacters.firstOrNull()?.id) }
    // Update selection if selected char was removed
    LaunchedEffect(selectedCharacters) {
        if (selectedCharId != null && selectedCharacters.none { it.id == selectedCharId }) {
            selectedCharId = selectedCharacters.firstOrNull()?.id
        }
    }
    val selectedChar = selectedCharacters.find { it.id == selectedCharId }
    var isFlipped by remember(selectedCharId) { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TroupeHeader(
            viewModel = viewModel,
            allCampaignCards = state.campaignCards,
            campaignCards = campaignCards,
            victoryPoints = victoryPoints,
            onVictoryPointsChange = onVictoryPointsChange,
            onManageCampaignCards = onManageCampaignCards,
            onShowTroupeTypeSheet = onShowTroupeTypeSheet,
            onToggleLayout = onToggleLayout,
            layoutMode = TroupeLayoutMode.PORTRAIT_COLUMN,
            onTargetPositioned = onTargetPositioned
        )

        Row(modifier = Modifier.weight(1f)) {
            // Left sidebar — portrait column
            LazyColumn(
                modifier = Modifier.width(80.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(selectedCharacters, key = { it.id }) { char ->
                    val isSelected = char.id == selectedCharId
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedCharId = char.id }
                    ) {
                        CharacterPortrait(character = char, size = 56.dp)
                    }
                }
                item {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onAddCharacters() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add character", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            VerticalDivider()

            // Right panel — character detail
            if (selectedChar != null) {
                val charIdx = selectedCharacters.indexOf(selectedChar)
                val bgImageFile = remember(selectedChar.imageName) {
                    selectedChar.imageName?.let { name ->
                        val dir = File(context.filesDir, "images")
                        listOf("", ".jpg", ".png", ".webp").firstNotNullOfOrNull { ext ->
                            File(dir, "$name$ext").takeIf { it.exists() }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 100.dp)
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (theme.showBackgroundImageOverlay && bgImageFile != null) {
                                AsyncImage(
                                    model = bgImageFile,
                                    contentDescription = null,
                                    modifier = Modifier.matchParentSize().alpha(0.25f),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (!isFlipped) {
                                CharacterFront(character = selectedChar, searchQuery = "", onFlip = { isFlipped = true })
                            } else {
                                CharacterBack(character = selectedChar, searchQuery = "", onFlip = { isFlipped = false })
                            }
                        }
                    }
                    if (viewModel.isCampaignTroupe) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 8.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            UpgradeStrip(
                                charIdx = charIdx,
                                equippedUpgrades = equippedUpgrades,
                                upgradeCards = state.upgradeCards,
                                onManageUpgrades = onManageUpgrades
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedCharacters.isEmpty()) "No characters added yet.\nTap '+' to add some!"
                        else "Select a character from the sidebar.",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// ── FAB Column ────────────────────────────────────────────────────────────────

@Composable
private fun DashboardFabColumn(
    viewModel: CharacterViewModel,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onSave: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = onSave,
            shape = theme.navItemShape,
            containerColor = if (viewModel.newTroupeName.isNotBlank() && viewModel.selectedCharacterIds.isNotEmpty())
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.onGloballyPositioned { onTargetPositioned("SaveButton", it) }
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
        }
    }
}

// ── Troupe Type Edit Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TroupeTypeEditSheet(
    currentIsTournament: Boolean,
    currentIsCampaign: Boolean,
    hasCampaignData: Boolean,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onConfirm: (isTournament: Boolean, isCampaign: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTournament by remember { mutableStateOf(currentIsTournament) }
    var selectedCampaign by remember { mutableStateOf(currentIsCampaign) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isLeavingCampaign = currentIsCampaign && !selectedCampaign

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text("Troupe Type", style = theme.headerStyle, color = MaterialTheme.colorScheme.primary)
            Text(
                "Choose how this troupe is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            SelectionOption(
                title = "Normal",
                subtitle = "Standard casual games.",
                selected = !selectedTournament && !selectedCampaign,
                onSelect = { selectedTournament = false; selectedCampaign = false },
                theme = theme
            )
            SelectionOption(
                title = "Tournament",
                subtitle = "Structured competitive play. No upgrade or campaign card tracking.",
                selected = selectedTournament && !selectedCampaign,
                onSelect = { selectedTournament = true; selectedCampaign = false },
                theme = theme
            )
            SelectionOption(
                title = "Campaign",
                subtitle = "Ongoing story play. Tracks upgrades, victory points and campaign cards.",
                selected = selectedCampaign,
                onSelect = { selectedTournament = false; selectedCampaign = true },
                theme = theme
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Universal info note
            Surface(
                shape = theme.cardShape,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ℹ  Troupes with 6 or fewer characters will auto select characters in game tracking — regardless of troupe type.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Contextual campaign warning
            if (isLeavingCampaign && hasCampaignData) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = theme.cardShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "⚠  Switching from Campaign",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "All equipped upgrade cards will be removed from this troupe. Victory points and campaign card history will also be cleared.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = theme.cardShape
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(selectedTournament, selectedCampaign) },
                    modifier = Modifier.weight(1f),
                    shape = theme.cardShape
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

// ── Campaign Card Management Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampaignCardManagementSheet(
    campaignCards: List<TroupeCampaignCard>,
    allCampaignCards: List<CampaignCard>,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onCampaignCardsChange: (List<TroupeCampaignCard>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddCardDialog by remember { mutableStateOf(false) }

    val readyCards = campaignCards.filter { it.usedInGame == null }
    val usedCards = campaignCards.filter { it.usedInGame != null }.sortedBy { it.usedInGame }
    val nextGameNumber = (campaignCards.maxOfOrNull { it.usedInGame ?: 0 } ?: 0) + 1
    val availableSlots = 3 - readyCards.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Text("Campaign Cards", style = theme.headerStyle, color = MaterialTheme.colorScheme.primary)
            Text(
                "Track your cards for each game of the campaign.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Ready section
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "READY TO USE NEXT GAME  (${readyCards.size} / 3)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                items(readyCards, key = { it.cardId }) { troupeCard ->
                    val card = allCampaignCards.find { it.id == troupeCard.cardId }
                    if (card != null) {
                        CampaignCardRow(
                            card = card,
                            isUsed = false,
                            gameNumber = null,
                            onRemove = {
                                onCampaignCardsChange(campaignCards.filter { it.cardId != troupeCard.cardId })
                            },
                            theme = theme
                        )
                    }
                }

                // Add card slot
                if (availableSlots > 0) {
                    item {
                        OutlinedCard(
                            onClick = { showAddCardDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = theme.cardShape,
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+ Add card ($availableSlots slot${if (availableSlots == 1) "" else "s"} remaining)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Transfer button between sections
                if (readyCards.isNotEmpty() || usedCards.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = {
                                    onCampaignCardsChange(
                                        campaignCards.map {
                                            if (it.usedInGame == null) it.copy(usedInGame = nextGameNumber) else it
                                        }
                                    )
                                },
                                enabled = readyCards.isNotEmpty(),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Transfer used cards ↓", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Used section
                if (usedCards.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "USED PREVIOUSLY THIS CAMPAIGN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items(usedCards, key = { it.cardId }) { troupeCard ->
                        val card = allCampaignCards.find { it.id == troupeCard.cardId }
                        if (card != null) {
                            CampaignCardRow(
                                card = card,
                                isUsed = true,
                                gameNumber = troupeCard.usedInGame,
                                onUndo = {
                                    onCampaignCardsChange(
                                        campaignCards.map {
                                            if (it.cardId == troupeCard.cardId) it.copy(usedInGame = null) else it
                                        }
                                    )
                                },
                                theme = theme
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape
            ) {
                Text("Done")
            }
        }
    }

    if (showAddCardDialog) {
        AddCampaignCardDialog(
            allCampaignCards = allCampaignCards,
            alreadySelected = campaignCards.map { it.cardId }.toSet(),
            theme = theme,
            onAdd = { cardId ->
                onCampaignCardsChange(campaignCards + TroupeCampaignCard(cardId))
                showAddCardDialog = false
            },
            onDismiss = { showAddCardDialog = false }
        )
    }
}

@Composable
private fun CampaignCardRow(
    card: CampaignCard,
    isUsed: Boolean,
    gameNumber: Int?,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onRemove: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null
) {
    ThemedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape)
                            .background(
                                if (isUsed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isUsed) "✓" else "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUsed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUsed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = card.timing,
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (gameNumber != null) {
                    Text(
                        text = "Game $gameNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove card",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Text(
                text = card.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isUsed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp, start = 30.dp)
            )
            if (isUsed && onUndo != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onUndo,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("↩ undo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCampaignCardDialog(
    allCampaignCards: List<CampaignCard>,
    alreadySelected: Set<Int>,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onAdd: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val available = remember(allCampaignCards, alreadySelected, searchQuery) {
        allCampaignCards
            .filter { it.id !in alreadySelected }
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp),
            shape = theme.cardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Add Campaign Card", style = theme.titleStyle.copy(fontSize = 22.sp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search cards...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    },
                    singleLine = true,
                    shape = theme.cardShape
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(available, key = { it.id }) { card ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(card.id) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(card.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(card.timing, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(card.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

// ── Upgrade Management Dialog ──────────────────────────────────────────────────

@Composable
private fun UpgradeManagementDialog(
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    selectedIds: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredUpgrades = remember(state.upgradeCards, searchQuery) {
        state.upgradeCards.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp),
            shape = theme.cardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Upgrades", style = theme.titleStyle.copy(fontSize = 22.sp))
                Text(
                    "Track which upgrades this character has earned in the campaign.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search upgrades...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    },
                    singleLine = true,
                    shape = theme.cardShape
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredUpgrades, key = { it.id }) { upgrade ->
                        val isSelected = selectedIds.contains(upgrade.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val newIds = selectedIds.toMutableSet()
                                if (isSelected) newIds.remove(upgrade.id) else newIds.add(upgrade.id)
                                onSelectionChange(newIds)
                            }.padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(upgrade.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                val restriction = buildList {
                                    if (upgrade.allowedKeywords.isNotEmpty()) add(upgrade.allowedKeywords.joinToString(", "))
                                    if (upgrade.restrictedKeywords.isNotEmpty()) add("Not: " + upgrade.restrictedKeywords.joinToString(", "))
                                }.joinToString(" · ")
                                if (restriction.isNotEmpty()) {
                                    Text(restriction, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = theme.cardShape
                ) {
                    Text("Done (${selectedIds.size})")
                }
            }
        }
    }
}

// ── Character Selection Stage ──────────────────────────────────────────────────

@Composable
private fun CharacterSelectionStage(
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTags: MutableList<String>,
    availableTags: List<String>,
    selectedIds: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    expandedCharacterId: Int?,
    onExpandClick: (Int) -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit,
    selectedTroupeFaction: Faction
) {
    val factionCharacters = remember(state.characters, selectedTroupeFaction, searchQuery, selectedTags.toList()) {
        state.characters.filter { it.factions.contains(selectedTroupeFaction) }.filter { char ->
            val matchesSearch = searchQuery.isEmpty() || char.name.contains(searchQuery, ignoreCase = true) || char.keywords.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in char.keywords }
            matchesSearch && matchesTags
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Add Characters", style = theme.titleStyle.copy(fontSize = 24.sp), modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search by name...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                }
            },
            singleLine = true,
            shape = theme.cardShape
        )
        if (availableTags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    .onGloballyPositioned { onTargetPositioned("CharacterTags", it) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(availableTags) { tag ->
                    val isSelected = selectedTags.contains(tag)
                    FilterChip(
                        selected = isSelected,
                        onClick = { if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag) },
                        label = { Text(tag) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = theme.cardShape
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(factionCharacters, key = { it.id }) { character ->
                val isSelected = selectedIds.contains(character.id)
                CharacterSelectionCard(
                    character = character,
                    searchQuery = searchQuery,
                    isSelected = isSelected,
                    isExpanded = expandedCharacterId == character.id,
                    onExpandClick = { onExpandClick(character.id) },
                    onSelectionChange = { selected ->
                        val current = selectedIds.toMutableSet()
                        if (selected) current.add(character.id) else current.remove(character.id)
                        onSelectionChange(current)
                    }
                )
            }
        }
    }
}

// ── Common Dialogs ─────────────────────────────────────────────────────────────

@Composable
private fun DiscardConfirmationDialog(
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    title: String = "Discard Changes?",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = theme.cardShape,
        title = { Text(title) },
        text = { Text("Unsaved changes will be discarded. Are you sure you want to go back?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SaveValidationDialog(
    viewModel: CharacterViewModel,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val count = viewModel.selectedCharacterIds.size
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = theme.cardShape,
        title = { Text("Save Troupe") },
        text = {
            Column {
                Text("This troupe will automatically select all members. It will be valid for:")
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 2 Players: ${if (count <= 6) "Valid" else "Invalid (Max 6)"}", color = if (count <= 6) theme.readyColor else MaterialTheme.colorScheme.error)
                Text("• 3 Players: ${if (count <= 4) "Valid" else "Invalid (Max 4)"}", color = if (count <= 4) theme.readyColor else MaterialTheme.colorScheme.error)
                Text("• 4 Players: ${if (count <= 3) "Valid" else "Invalid (Max 3)"}", color = if (count <= 3) theme.readyColor else MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Do you want to save anyway?")
            }
        },
        confirmButton = { Button(onClick = onConfirm, shape = theme.cardShape) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back to Edit") } }
    )
}

// ── Character Selection Card (used in CharacterSelectionStage) ─────────────────

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
                onCheckedChange = { if (!character.isUnselectableInTroupe) onSelectionChange(it) },
                enabled = !character.isUnselectableInTroupe
            )
        }
    )
}
