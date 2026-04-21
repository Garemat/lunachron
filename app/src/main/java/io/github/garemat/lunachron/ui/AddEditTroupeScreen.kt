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
import androidx.compose.foundation.ExperimentalFoundationApi
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

// TODO(dual-layout): Reserved for future dual-layout update — currently unused.
// private enum class TroupeLayoutMode { SINGLE_COLUMN, PORTRAIT_COLUMN }

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
    LaunchedEffect(editStage) {
        viewModel.troupeDashboardActive = (editStage == TroupeEditStage.DASHBOARD)
    }
    DisposableEffect(Unit) { onDispose { viewModel.troupeDashboardActive = false } }

    val theme = LocalAppThemeProperties.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    val originalName = remember { viewModel.newTroupeName }
    val originalFaction = remember { viewModel.selectedTroupeFaction }
    val originalCharacterIds = remember { viewModel.selectedCharacterIds }
    val originalIsCampaign = remember { viewModel.isCampaignTroupe }

    var expandedCharacterId by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showCampaignCardSheet by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showSelectionDiscardConfirmation by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    // TODO(dual-layout): Reserved for future dual-layout update — currently unused.
    // LaunchedEffect(Unit) {
    //     viewModel.troupeLayoutSingleColumn = (prefs.getString("troupe_layout_mode", "SINGLE_COLUMN") != "PORTRAIT_COLUMN")
    // }
    // LaunchedEffect(viewModel.troupeLayoutSingleColumn) {
    //     prefs.edit().putString("troupe_layout_mode", if (viewModel.troupeLayoutSingleColumn) "SINGLE_COLUMN" else "PORTRAIT_COLUMN").apply()
    // }
    // val troupeLayout = if (viewModel.troupeLayoutSingleColumn) TroupeLayoutMode.SINGLE_COLUMN else TroupeLayoutMode.PORTRAIT_COLUMN
    // val onToggleLayout = { viewModel.troupeLayoutSingleColumn = !viewModel.troupeLayoutSingleColumn }

    var temporarySelectedIds by remember { mutableStateOf(viewModel.selectedCharacterIds) }

    // Campaign specific state
    var equippedUpgrades by remember { mutableStateOf(emptyMap<Int, List<Int>>()) }
    var campaignCards by remember { mutableStateOf(emptyList<TroupeCampaignCard>()) }
    var victoryPoints by remember { mutableIntStateOf(0) }

    var targetCharIdForUpgrade by remember { mutableIntStateOf(-1) }
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
            viewModel.isCampaignTroupe != originalIsCampaign

    val hasSelectionChanges = temporarySelectedIds != viewModel.selectedCharacterIds

    val availableTags = remember(state.characters, viewModel.selectedTroupeFaction) {
        state.characters
            .filter { it.factions.contains(viewModel.selectedTroupeFaction) }
            .flatMap { it.keywords }.distinct().sorted()
    }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(availableTags) {
        selectedTags = selectedTags.filter { it in availableTags }.toSet()
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
                    equippedUpgrades = equippedUpgrades,
                    campaignCards = campaignCards,
                    victoryPoints = victoryPoints,
                    onVictoryPointsChange = { victoryPoints = it },
                    onManageUpgrades = { charId -> targetCharIdForUpgrade = charId; showUpgradeDialog = true },
                    onManageCampaignCards = { showCampaignCardSheet = true },
                    onTargetPositioned = onTargetPositioned
                )

                TroupeEditStage.CHARACTER_SELECTION -> CharacterSelectionStage(
                    state, theme, searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedTags = selectedTags,
                    onTagsChange = { selectedTags = it },
                    availableTags = availableTags,
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
                onAddCharacters = { temporarySelectedIds = viewModel.selectedCharacterIds; editStage = TroupeEditStage.CHARACTER_SELECTION },
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

    if (showUpgradeDialog) {
        val targetChar = state.characters.find { it.id == targetCharIdForUpgrade }
        if (targetChar != null) {
            UpgradeManagementSheet(
                character = targetChar,
                state = state,
                theme = theme,
                selectedIds = equippedUpgrades[targetChar.id]?.toSet() ?: emptySet(),
                onSelectionChange = { ids ->
                    val newMap = equippedUpgrades.toMutableMap()
                    if (ids.isEmpty()) newMap.remove(targetChar.id)
                    else newMap[targetChar.id] = ids.toList()
                    equippedUpgrades = newMap
                },
                onDismiss = { showUpgradeDialog = false; targetCharIdForUpgrade = -1 }
            )
        }
    }

    if (viewModel.showTroupeTypeSheet) {
        TroupeTypeEditSheet(
            currentIsCampaign = viewModel.isCampaignTroupe,
            hasCampaignData = campaignCards.isNotEmpty() || equippedUpgrades.isNotEmpty() || victoryPoints > 0,
            theme = theme,
            onConfirm = { isCampaign ->
                val wasCampaign = viewModel.isCampaignTroupe
                viewModel.isTournamentList = false
                viewModel.isCampaignTroupe = isCampaign
                if (wasCampaign && !isCampaign) {
                    equippedUpgrades = emptyMap()
                    campaignCards = emptyList()
                    victoryPoints = 0
                }
                viewModel.showTroupeTypeSheet = false
            },
            onDismiss = { viewModel.showTroupeTypeSheet = false }
        )
    }

    if (showCampaignCardSheet) {
        CampaignCardManagementSheet(
            campaignCards = campaignCards,
            allCampaignCards = state.campaignCards,
            troupeFaction = viewModel.selectedTroupeFaction,
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

        // Campaign troupe checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.isTournamentList = false; viewModel.isCampaignTroupe = !viewModel.isCampaignTroupe },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = viewModel.isCampaignTroupe,
                onCheckedChange = { viewModel.isTournamentList = false; viewModel.isCampaignTroupe = it }
            )
            Column {
                Text("Campaign Troupe", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Tracks upgrades, victory points and campaign cards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Select Faction", style = MaterialTheme.typography.labelMedium)
        FactionSelector(
            selectedFactions = setOf(viewModel.selectedTroupeFaction),
            onFactionsChange = { if (it.isNotEmpty()) { viewModel.selectedTroupeFaction = it.first(); viewModel.selectedCharacterIds = emptySet() } },
            singleSelect = true,
            onPositioned = { onTargetPositioned("FactionSymbols", it) }
        )
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
    equippedUpgrades: Map<Int, List<Int>>,
    campaignCards: List<TroupeCampaignCard>,
    victoryPoints: Int,
    onVictoryPointsChange: (Int) -> Unit,
    onManageUpgrades: (Int) -> Unit,
    onManageCampaignCards: () -> Unit
) {
    val selectedCharacters = remember(state.characters, viewModel.selectedCharacterIds) {
        state.characters.filter { viewModel.selectedCharacterIds.contains(it.id) }
    }

    SingleColumnDashboard(
        viewModel = viewModel,
        state = state,
        theme = theme,
        selectedCharacters = selectedCharacters,
        expandedCharacterId = expandedCharacterId,
        onExpandClick = onExpandClick,
        equippedUpgrades = equippedUpgrades,
        campaignCards = campaignCards,
        victoryPoints = victoryPoints,
        onVictoryPointsChange = onVictoryPointsChange,
        onManageUpgrades = onManageUpgrades,
        onManageCampaignCards = onManageCampaignCards,
        onTargetPositioned = onTargetPositioned
    )
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
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    if (!viewModel.isCampaignTroupe) return
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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

        HorizontalDivider()
    }
}

// ── Single Column Dashboard ───────────────────────────────────────────────────

@Composable
private fun SingleColumnDashboard(
    viewModel: CharacterViewModel,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    selectedCharacters: List<Character>,
    expandedCharacterId: Int?,
    onExpandClick: (Int) -> Unit,
    equippedUpgrades: Map<Int, List<Int>>,
    campaignCards: List<TroupeCampaignCard>,
    victoryPoints: Int,
    onVictoryPointsChange: (Int) -> Unit,
    onManageUpgrades: (Int) -> Unit,
    onManageCampaignCards: () -> Unit,
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

            }
        }
    }
}

@Composable
private fun SwipeRevealCharacterItem(
    modifier: Modifier = Modifier,
    character: Character,
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
        // Background — only visible while the card is actually swiped open
        if (offsetX.value < 0f) {
            Box(
                modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(end = 20.dp).clickable { onRemove() }
                )
            }
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
            CommonCharacterCard(
                character = character,
                searchQuery = "",
                isExpanded = isExpanded,
                onExpandClick = onExpandClick,
                bottomContent = if (isCampaignTroupe) {
                    {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        UpgradeStrip(
                            characterId = character.id,
                            equippedUpgrades = equippedUpgrades,
                            upgradeCards = upgradeCards,
                            onManageUpgrades = onManageUpgrades
                        )
                    }
                } else null
            )
        }
    }
}


@Composable
private fun UpgradeStrip(
    characterId: Int,
    equippedUpgrades: Map<Int, List<Int>>,
    upgradeCards: List<UpgradeCard>,
    onManageUpgrades: (Int) -> Unit
) {
    val charUpgrades = equippedUpgrades[characterId] ?: emptyList()
    val maxUpgrades = 2
    val upgradeNames = charUpgrades.mapNotNull { id -> upgradeCards.find { it.id == id } }

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        when {
            charUpgrades.isEmpty() -> {
                // Centred "+ Add Upgrade"
                Box(
                    modifier = Modifier.fillMaxWidth().clickable { onManageUpgrades(characterId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Add Upgrade",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            else -> {
                upgradeNames.forEachIndexed { index, upgrade ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = upgrade.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    val ability = upgrade.abilities.firstOrNull()
                    if (ability != null) {
                        Text(
                            text = "${ability.name}: ${ability.description}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().clickable { onManageUpgrades(characterId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (charUpgrades.size < maxUpgrades) "+ Add Upgrade" else "Manage Upgrades",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ── Portrait Column Dashboard ──────────────────────────────────────────────────
// TODO(dual-layout): This composable is currently unused. It is kept here as a starting point
// for a future dual-layout update that lets the user switch between a single-column card list
// and a portrait-sidebar + detail-panel view. Re-enable by restoring TroupeLayoutMode,
// troupeLayoutSingleColumn (ViewModel), and the routing logic in DashboardStage.

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
            }

            VerticalDivider()

            // Right panel — character detail
            if (selectedChar != null) {
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
                                characterId = selectedChar.id,
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
    onAddCharacters: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = onAddCharacters,
                shape = theme.navItemShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add character")
            }
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
}

// ── Troupe Type Edit Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TroupeTypeEditSheet(
    currentIsCampaign: Boolean,
    hasCampaignData: Boolean,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onConfirm: (isCampaign: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCampaign by remember { mutableStateOf(currentIsCampaign) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isLeavingCampaign = currentIsCampaign && !selectedCampaign

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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
                selected = !selectedCampaign,
                onSelect = { selectedCampaign = false },
                theme = theme
            )
            SelectionOption(
                title = "Campaign",
                subtitle = "Ongoing story play. Tracks upgrades, victory points and campaign cards.",
                selected = selectedCampaign,
                onSelect = { selectedCampaign = true },
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
                    onClick = { onConfirm(selectedCampaign) },
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
    troupeFaction: Faction,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onCampaignCardsChange: (List<TroupeCampaignCard>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val readyIds = campaignCards.filter { it.usedInGame == null }.map { it.cardId }.toSet()
    val usedTroupeCards = campaignCards.filter { it.usedInGame != null }.sortedBy { it.usedInGame }
    val nextGameNumber = (campaignCards.maxOfOrNull { it.usedInGame ?: 0 } ?: 0) + 1
    val selectedReadyCount = readyIds.size

    val usedCardIds = campaignCards.filter { it.usedInGame != null }.map { it.cardId }.toSet()
    val availableCards = remember(allCampaignCards, campaignCards, troupeFaction, searchQuery) {
        allCampaignCards
            .filter { card -> (card.factions == null || troupeFaction in card.factions) && card.id !in usedCardIds }
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }
    val usedCardDetails = remember(allCampaignCards, campaignCards, searchQuery) {
        usedTroupeCards.mapNotNull { troupeCard ->
            allCampaignCards.find { it.id == troupeCard.cardId }
                ?.takeIf { it.name.contains(searchQuery, ignoreCase = true) }
                ?.let { card -> card to troupeCard }
        }
    }
    val unavailableCards = remember(allCampaignCards, campaignCards, troupeFaction, searchQuery) {
        allCampaignCards
            .filter { card -> card.factions != null && troupeFaction !in card.factions && card.id !in usedCardIds }
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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
                "Select up to 3 cards for this troupe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search cards...") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                },
                singleLine = true,
                shape = theme.cardShape
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                // Available section
                if (availableCards.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "AVAILABLE  ($selectedReadyCount / 3 selected)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(availableCards, key = { "avail_${it.id}" }) { card ->
                        val isSelected = card.id in readyIds
                        val atMax = selectedReadyCount >= 3
                        val canToggle = isSelected || !atMax
                        val contentAlpha = if (!canToggle) 0.45f else 1f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = canToggle) {
                                    if (isSelected) {
                                        onCampaignCardsChange(campaignCards.filter { it.cardId != card.id })
                                    } else {
                                        onCampaignCardsChange(campaignCards + TroupeCampaignCard(card.id))
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            SelectionCircle(isSelected = isSelected, contentAlpha = contentAlpha)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    card.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                                )
                                Text(
                                    card.timing,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                                )
                                Text(
                                    card.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                                    maxLines = 3
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                // Used section
                if (usedCardDetails.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = "USED THIS CAMPAIGN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(usedCardDetails, key = { "used_${it.second.cardId}" }) { (card, troupeCard) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier.size(22.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        card.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Game ${troupeCard.usedInGame}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                onCampaignCardsChange(campaignCards.map {
                                                    if (it.cardId == troupeCard.cardId) it.copy(usedInGame = null) else it
                                                })
                                            },
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text("↩ undo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Text(
                                    card.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                // Unavailable section
                if (unavailableCards.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = "UNAVAILABLE FOR THIS FACTION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(unavailableCards, key = { "unavail_${it.id}" }) { card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            SelectionCircle(isSelected = false, isCompatible = false, contentAlpha = 0.35f)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    card.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                                Text(
                                    card.timing,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                                Text(
                                    card.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    maxLines = 2
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onCampaignCardsChange(campaignCards.map {
                            if (it.usedInGame == null) it.copy(usedInGame = nextGameNumber) else it
                        })
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedReadyCount > 0,
                    shape = theme.cardShape
                ) {
                    Text("Transfer Used Cards", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = theme.cardShape
                ) {
                    Text("Save")
                }
            }
        }
    }
}

// ── Upgrade Management Dialog ──────────────────────────────────────────────────

private fun upgradeCompatible(upgrade: UpgradeCard, character: Character): Boolean {
    val factionOk = upgrade.factions == null || upgrade.factions.any { it in character.factions }
    val keywordOk = upgrade.allowedKeywords.isEmpty() || upgrade.allowedKeywords.any { it in character.keywords }
    val notRestricted = upgrade.restrictedKeywords.isEmpty() || upgrade.restrictedKeywords.none { it in character.keywords }
    return factionOk && keywordOk && notRestricted
}

private fun upgradeIncompatibilityReason(upgrade: UpgradeCard, character: Character): String {
    val reasons = mutableListOf<String>()
    if (upgrade.factions != null && upgrade.factions.none { it in character.factions }) {
        reasons.add("Faction: ${upgrade.factions.joinToString(" / ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }} only")
    }
    if (upgrade.allowedKeywords.isNotEmpty() && upgrade.allowedKeywords.none { it in character.keywords }) {
        reasons.add("Requires: ${upgrade.allowedKeywords.joinToString(", ")}")
    }
    if (upgrade.restrictedKeywords.isNotEmpty() && upgrade.restrictedKeywords.any { it in character.keywords }) {
        val bad = upgrade.restrictedKeywords.filter { it in character.keywords }
        reasons.add("Excluded keyword: ${bad.joinToString(", ")}")
    }
    return reasons.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun UpgradeManagementSheet(
    character: Character,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    selectedIds: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val bgImageFile = remember(character.imageName) {
        character.imageName?.let { name ->
            val dir = File(context.filesDir, "images")
            listOf("$name-head.jpg", "$name-head.png", "$name-head.webp",
                   "$name.jpg", "$name.png", "$name.webp", name).firstNotNullOfOrNull { candidate ->
                File(dir, candidate).takeIf { it.exists() }
            }
        }
    }

    // Auto-deselect incompatible upgrades
    val compatibleIds = remember(state.upgradeCards, character.id) {
        state.upgradeCards.filter { upgradeCompatible(it, character) }.map { it.id }.toSet()
    }
    LaunchedEffect(character.id) {
        val cleaned = selectedIds.filter { it in compatibleIds }.toSet()
        if (cleaned != selectedIds) onSelectionChange(cleaned)
    }

    // Capture selection at open time so sort order doesn't shift while the user taps
    val initialSelectedIds = remember(character.id) { selectedIds }

    val (compatibleUpgrades, incompatibleUpgrades) = remember(state.upgradeCards, searchQuery, character.id) {
        val all = if (searchQuery.isEmpty()) state.upgradeCards
                  else state.upgradeCards.filter { it.name.contains(searchQuery, ignoreCase = true) }
        val compatible = all.filter { upgradeCompatible(it, character) }
            .sortedWith(compareByDescending<UpgradeCard> { it.id in initialSelectedIds }.thenBy { it.name })
        val incompatible = all.filter { !upgradeCompatible(it, character) }.sortedBy { it.name }
        compatible to incompatible
    }

    val atMax = selectedIds.size >= 2

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // ── Header image / initials banner ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(getFactionColor(character.factions.firstOrNull() ?: Faction.COMMONWEALTH).copy(alpha = 0.8f))
            ) {
                if (bgImageFile != null) {
                    AsyncImage(
                        model = bgImageFile,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize().alpha(0.55f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Initials fallback centred in upper portion
                    Text(
                        text = character.name.firstOrNull()?.uppercase() ?: "?",
                        style = theme.titleStyle.copy(fontSize = 48.sp),
                        color = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                // Overlay: name + selection count
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Upgrades for ${character.name}",
                        style = theme.titleStyle.copy(fontSize = 17.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "${compatibleUpgrades.size} compatible · ${selectedIds.size}/2 selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // ── Search bar ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search upgrades…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                },
                singleLine = true,
                shape = theme.cardShape
            )

            // ── Single scrollable list ─────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Compatible section header
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "COMPATIBLE (${compatibleUpgrades.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    }
                }
                items(compatibleUpgrades, key = { it.id }) { upgrade ->
                    val isSelected = upgrade.id in selectedIds
                    val canSelect = isSelected || !atMax
                    UpgradeRow(
                        upgrade = upgrade,
                        isSelected = isSelected,
                        isCompatible = true,
                        enabled = canSelect,
                        incompatibilityReason = "",
                        onClick = {
                            if (canSelect) {
                                val newIds = selectedIds.toMutableSet()
                                if (isSelected) newIds.remove(upgrade.id) else newIds.add(upgrade.id)
                                onSelectionChange(newIds)
                            }
                        }
                    )
                }

                // Incompatible section header
                if (incompatibleUpgrades.isNotEmpty()) {
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "INCOMPATIBLE WITH ${character.name.uppercase()} (${incompatibleUpgrades.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    items(incompatibleUpgrades, key = { it.id }) { upgrade ->
                        UpgradeRow(
                            upgrade = upgrade,
                            isSelected = false,
                            isCompatible = false,
                            enabled = false,
                            incompatibilityReason = upgradeIncompatibilityReason(upgrade, character),
                            onClick = {}
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // ── Done button ────────────────────────────────────────────────────
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                shape = theme.cardShape
            ) {
                Text("Done (${selectedIds.size} / 2)")
            }
        }
    }
}

@Composable
private fun SelectionCircle(
    isSelected: Boolean,
    isCompatible: Boolean = true,
    contentAlpha: Float = 1f
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.secondary
                    isCompatible -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                }
            )
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = if (isCompatible) MaterialTheme.colorScheme.outline.copy(alpha = contentAlpha)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isSelected -> Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(14.dp))
            !isCompatible -> Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun UpgradeRow(
    upgrade: UpgradeCard,
    isSelected: Boolean,
    isCompatible: Boolean,
    enabled: Boolean,
    incompatibilityReason: String,
    onClick: () -> Unit
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isCompatible -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.secondary
        isCompatible -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val contentAlpha = if (!isCompatible) 0.45f else if (!enabled) 0.6f else 1f

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionCircle(
                isSelected = isSelected,
                isCompatible = isCompatible,
                contentAlpha = contentAlpha
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = upgrade.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                if (upgrade.abilities.isNotEmpty()) {
                    val ability = upgrade.abilities.first()
                    Text(
                        text = "${ability.name}: ${ability.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        maxLines = 6,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val restriction = buildList {
                    if (!upgrade.factions.isNullOrEmpty()) add(upgrade.factions.joinToString(" / ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } } + " only")
                    if (upgrade.allowedKeywords.isNotEmpty()) add("Requires: ${upgrade.allowedKeywords.joinToString(", ")}")
                }.joinToString(" · ")
                val footerText = if (!isCompatible && incompatibilityReason.isNotEmpty()) "⚠ $incompatibilityReason"
                                 else restriction
                if (footerText.isNotEmpty()) {
                    Text(
                        text = footerText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!isCompatible) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Badge
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "● Owned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else if (isCompatible && !enabled) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "Max 2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
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
    selectedTags: Set<String>,
    onTagsChange: (Set<String>) -> Unit,
    availableTags: List<String>,
    selectedIds: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    expandedCharacterId: Int?,
    onExpandClick: (Int) -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit,
    selectedTroupeFaction: Faction
) {
    val factionCharacters = remember(state.characters, selectedTroupeFaction, searchQuery, selectedTags) {
        state.characters.filter { it.factions.contains(selectedTroupeFaction) }.filter { char ->
            val matchesSearch = searchQuery.isEmpty() || char.name.contains(searchQuery, ignoreCase = true) || char.keywords.any { it.contains(searchQuery, ignoreCase = true) } || char.abilities.any { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
            val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in char.keywords }
            matchesSearch && matchesTags
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Add Characters", style = theme.titleStyle.copy(fontSize = 24.sp), modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        CharacterFilterHeader(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            selectedFactions = emptySet(),
            onFactionsChange = {},
            selectedTags = selectedTags,
            onTagsChange = onTagsChange,
            availableTags = availableTags,
            isFactionFixed = true,
            onClearAll = { onSearchQueryChange(""); onTagsChange(emptySet()) },
            onTargetPositioned = { name, coords -> onTargetPositioned(name, coords) }
        )
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
