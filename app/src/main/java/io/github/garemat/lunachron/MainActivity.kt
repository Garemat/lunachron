package io.github.garemat.lunachron

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.garemat.lunachron.ui.theme.AppThemeProperties
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.garemat.lunachron.ui.*
import io.github.garemat.lunachron.ui.theme.LocalAnimationsEnabled
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import io.github.garemat.lunachron.ui.theme.LunachronTheme
import androidx.compose.ui.platform.LocalContext
import io.github.garemat.lunachron.BuildConfig
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val gameDb by lazy { GameDatabase.getDatabase(applicationContext) }
    private val userDb by lazy { UserDatabase.getDatabase(applicationContext) }

    private val viewModel by viewModels<CharacterViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repo = LocalCharacterRepository(gameDb.dao, userDb.dao)
                    val dataUpdateRepo = DataUpdateRepository(applicationContext, repo)
                    @Suppress("UNCHECKED_CAST")
                    return CharacterViewModel(application, repo, dataUpdateRepo) as T
                }
            }
        }
    )

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsState()
            var showTutorialForcefully by remember { mutableStateOf(false) }
            
            // Global coordinate tracking for tutorial
            val tutorialCoords = remember { mutableStateMapOf<String, LayoutCoordinates>() }
            val isTutorialActive = false // disabled
            var currentTutorialStep by remember { mutableStateOf<TutorialStep?>(null) }

            // State for manual troupe selection
            var targetManualPlayerId by remember { mutableStateOf<String?>(null) }
            var isSelectingForCampaign by remember { mutableStateOf(false) }

            if (BuildConfig.CAN_SELF_UPDATE) {
                val context = LocalContext.current
                val pendingApk = state.pendingApkInstall
                LaunchedEffect(pendingApk) {
                    if (pendingApk == null) return@LaunchedEffect
                    val apkFile = java.io.File(pendingApk)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()
                    ) {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", apkFile
                        )
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                    viewModel.onEvent(CharacterEvent.ClearPendingApkInstall)
                }
            }

            LunachronTheme(
                activeThemeId = state.activeThemeId,
                layoutDensity = state.layoutDensity
            ) {
                CompositionLocalProvider(LocalAnimationsEnabled provides state.enableAnimations) {
                val theme = LocalAppThemeProperties.current
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                var anyCharacterExpanded by remember { mutableStateOf(false) }

                // Reset expansion tracking when leaving the character list
                LaunchedEffect(currentDestination?.route) {
                    if (currentDestination?.route != Screen.Characters.route) anyCharacterExpanded = false
                }

                val showNavBars = remember(currentDestination) {
                    currentDestination?.route?.split("/")?.firstOrNull() in listOf(
                        Screen.Home.route,
                        Screen.Compendium.route,
                        Screen.Characters.route,
                        Screen.Upgrades.route,
                        Screen.CampaignCards.route,
                        Screen.Troupes.route,
                        Screen.AddEditTroupe.route,
                        Screen.GameSetup.route,
                        Screen.SoloTroupeSelect.route,
                        Screen.CampaignHub.route,
                        Screen.CampaignManagement.route,
                        Screen.AddEditCampaign.route,
                        Screen.HostOnlineCampaign.route,
                        Screen.JoinOnlineCampaign.route,
                        Screen.ActiveOnlineCampaigns.route,
                        "online_campaign_detail",
                        "edit_campaign",
                        "campaign_details"
                    )
                }

                val showBottomBar = showNavBars &&
                    !(state.autoHideNavBar && currentDestination?.route == Screen.Characters.route && anyCharacterExpanded) &&
                    !(state.autoHideNavBar && currentDestination?.route == Screen.AddEditTroupe.route)

                LaunchedEffect(viewModel.uiEvent) {
                    viewModel.uiEvent.collect { event ->
                        if (event is CharacterViewModel.UiEvent.TournamentDisbanded) {
                            navController.navigate(Screen.GameSetup.route) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                            }
                        } else if (event is CharacterViewModel.UiEvent.TroupeCreated) {
                            // If we were creating a troupe specifically for a campaign, navigate back past
                            // both AddEditTroupe and SelectTroupe to EditCampaign in one step (no flash)
                            if (event.campaignPlayerId != null) {
                                navController.safePopBackStack()
                                navController.safePopBackStack()
                            }
                        }
                    }
                }

                // Data update dialogs
                DataUpdateDialogs(state = state, onEvent = viewModel::onEvent, theme = theme)

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = showNavBars,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerShape = theme.drawerShape,
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            drawerContentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Lunachron",
                                modifier = Modifier.padding(16.dp),
                                style = theme.titleStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                                label = { Text("Rules", style = theme.headerStyle.copy(fontSize = 18.sp)) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(Screen.Rules.route)
                                },
                                shape = theme.navItemShape,
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedTextColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                label = { Text("Stats", style = theme.headerStyle.copy(fontSize = 18.sp)) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(Screen.Stats.route)
                                },
                                shape = theme.navItemShape,
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedTextColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Settings", style = theme.headerStyle.copy(fontSize = 18.sp)) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(Screen.Settings.route)
                                },
                                shape = theme.navItemShape,
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedTextColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (showNavBars) {
                                val isTroupeDashboard = currentDestination?.route == Screen.AddEditTroupe.route && viewModel.troupeDashboardActive
                                CenterAlignedTopAppBar(
                                    title = {
                                        if (isTroupeDashboard) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FactionIcon(
                                                    faction = viewModel.selectedTroupeFaction,
                                                    size = 26.dp
                                                )
                                                Text(
                                                    text = viewModel.newTroupeName.ifBlank { if (viewModel.editingTroupeId == null) "New Troupe" else "Edit Troupe" },
                                                    style = theme.titleStyle
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = when {
                                                    currentDestination?.route == Screen.Home.route -> "Lunachron"
                                                    currentDestination?.route == Screen.Compendium.route -> "Compendium"
                                                    currentDestination?.route == Screen.Characters.route -> "Character List"
                                                    currentDestination?.route == Screen.Upgrades.route -> "Upgrades"
                                                    currentDestination?.route == Screen.CampaignCards.route -> "Campaign Cards"
                                                    currentDestination?.route == Screen.Troupes.route -> "My Troupes"
                                                    currentDestination?.route == Screen.AddEditTroupe.route -> viewModel.newTroupeName.ifBlank { if (viewModel.editingTroupeId == null) "New Troupe" else "Edit Troupe" }
                                                    currentDestination?.route == Screen.GameSetup.route -> "Game Setup"
                                                    currentDestination?.route == Screen.CampaignHub.route -> "Campaigns"
                                                    currentDestination?.route == Screen.CampaignManagement.route -> "Local Tracking"
                                                    currentDestination?.route == Screen.HostOnlineCampaign.route -> "New Campaign"
                                                    currentDestination?.route == Screen.JoinOnlineCampaign.route -> "Join Campaign"
                                                    currentDestination?.route == Screen.ActiveOnlineCampaigns.route -> "Active Campaigns"
                                                    currentDestination?.route?.startsWith("online_campaign_detail") == true -> state.selectedOnlineCampaign?.name ?: "Campaign"
                                                    currentDestination?.route == Screen.AddEditCampaign.route -> "New Campaign"
                                                    currentDestination?.route?.startsWith("edit_campaign") == true -> "Campaign Settings"
                                                    currentDestination?.route?.startsWith("campaign_details") == true -> {
                                                        val cId = navController.currentBackStackEntry?.arguments?.getInt("campaignId")
                                                        val camp = state.campaigns.find { it.id == cId }
                                                        val r = camp?.currentRound
                                                        when (viewModel.currentCampaignSubScreen) {
                                                            CampaignSubScreen.RANKINGS -> "Rankings"
                                                            CampaignSubScreen.GAMES -> if (r != null) "Round $r" else "Games"
                                                            CampaignSubScreen.SCHEDULE -> "Schedule"
                                                            CampaignSubScreen.HISTORY -> "History"
                                                            null -> camp?.name ?: "Campaign"
                                                        }
                                                    }
                                                    else -> "Lunachron"
                                                },
                                                style = theme.titleStyle
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        if (currentDestination?.route in listOf(Screen.AddEditTroupe.route, Screen.Characters.route, Screen.Upgrades.route, Screen.CampaignCards.route, Screen.Rules.route, Screen.Settings.route, Screen.TournamentSetup.route, Screen.AddEditCampaign.route, Screen.CampaignManagement.route, Screen.HostOnlineCampaign.route, Screen.JoinOnlineCampaign.route, Screen.ActiveOnlineCampaigns.route) || currentDestination?.route?.startsWith("edit_campaign") == true || currentDestination?.route?.startsWith("campaign_details") == true || currentDestination?.route?.startsWith("online_campaign_detail") == true) {
                                            IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { scope.launch { drawerState.open() } },
                                                modifier = Modifier.onGloballyPositioned { tutorialCoords["MenuButton"] = it }
                                            ) {
                                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                                            }
                                        }
                                    },
                                    actions = {
                                        if (isTroupeDashboard) {
                                            val typeLabel = if (viewModel.isCampaignTroupe) "Campaign" else "Normal"
                                            TextButton(onClick = { viewModel.showTroupeTypeSheet = true }) {
                                                Text(
                                                    text = "$typeLabel ✎",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    expandedHeight = 48.dp,
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        titleContentColor = MaterialTheme.colorScheme.primary,
                                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                                        navigationIconContentColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        },
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar(
                                    containerColor = if (theme.navigationBarElevation == 0.dp) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = theme.navigationBarElevation
                                ) {
                                    val playRoute = if (state.useLocalModeByDefault && state.useSinglePlayerMode)
                                        Screen.SoloTroupeSelect.route else Screen.GameSetup.route
                                    val items = listOf(
                                        BottomNavItem("Home", Screen.Home.route, Icons.Default.Home),
                                        BottomNavItem("Compendium", Screen.Compendium.route, Icons.Default.MenuBook),
                                        BottomNavItem("Play", playRoute, Icons.Default.PlayArrow),
                                        BottomNavItem("Troupes", Screen.Troupes.route, Icons.Default.Groups),
                                        BottomNavItem("Campaigns", Screen.CampaignHub.route, Icons.Default.HistoryEdu)
                                    )
                                    items.forEach { item ->
                                        val isPlayButton = item.label == "Play"
                                        val isSelected = if (isPlayButton) {
                                            currentDestination?.hierarchy?.any {
                                                it.route == Screen.GameSetup.route || it.route == Screen.SoloTroupeSelect.route
                                            } == true
                                        } else {
                                            currentDestination?.hierarchy?.any { it.route == item.route } == true
                                        }
                                        
                                        NavigationBarItem(
                                            modifier = Modifier.onGloballyPositioned { 
                                                when(item.label) {
                                                    "Home" -> tutorialCoords["HomeNav"] = it
                                                    "Compendium" -> tutorialCoords["CompendiumNav"] = it
                                                    "Troupes" -> tutorialCoords["TroupesNav"] = it
                                                    "Play" -> tutorialCoords["PlayNav"] = it
                                                    "Campaigns" -> tutorialCoords["CampaignsNav"] = it
                                                }
                                            },
                                            icon = { 
                                                if (isPlayButton) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (theme.navigationBarElevation == 0.dp) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
                                                        modifier = Modifier.size(48.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                item.icon, 
                                                                contentDescription = item.label,
                                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else theme.unselectedNavColor
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Icon(item.icon, contentDescription = item.label)
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = item.label,
                                                    style = theme.labelStyle,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            },
                                            selected = isSelected,
                                            onClick = {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = if (isPlayButton) Color.Transparent else if (theme.navigationBarElevation == 0.dp) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            val startDestination = remember { state.defaultStartPage }
                        NavHost(
                                navController = navController,
                                startDestination = startDestination,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable(Screen.Home.route) {
                                    HomeScreen(
                                        state = state,
                                        onEvent = viewModel::onEvent,
                                        onQuickStartTroupe = { troupe ->
                                            viewModel.startNewGame(listOf(troupe))
                                            navController.navigate(Screen.ActiveGame.route)
                                        },
                                        onTargetPositioned = { name, coords -> tutorialCoords[name] = coords }
                                    )
                                }
                                composable(Screen.Settings.route) {
                                    SettingsScreen(
                                        state = state,
                                        onEvent = viewModel::onEvent,
                                        onNavigateBack = { navController.safePopBackStack() }
                                    )
                                }
                                composable(Screen.Rules.route) {
                                    RulesScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.safePopBackStack() }
                                    )
                                }
                                composable(Screen.Compendium.route) {
                                    CompendiumScreen(
                                        onNavigateToCharacters = { navController.navigate(Screen.Characters.route) },
                                        onNavigateToUpgrades = { navController.navigate(Screen.Upgrades.route) },
                                        onNavigateToCampaignCards = { navController.navigate(Screen.CampaignCards.route) }
                                    )
                                }
                                composable(Screen.Characters.route) {
                                    CharacterListScreen(
                                        state = state,
                                        onEvent = viewModel::onEvent,
                                        onNavigateBack = { navController.safePopBackStack() },
                                        onExpansionChanged = { anyCharacterExpanded = it },
                                        onTargetPositioned = { name, coords -> tutorialCoords[name] = coords }
                                    )
                                }
                                composable(Screen.Upgrades.route) {
                                    UpgradeListScreen(
                                        state = state,
                                        onNavigateBack = { navController.safePopBackStack() }
                                    )
                                }
                                composable(Screen.CampaignCards.route) {
                                    CampaignCardListScreen(
                                        state = state,
                                        onNavigateBack = { navController.safePopBackStack() }
                                    )
                                }
                                composable(Screen.Troupes.route) {
                                    TroupeListScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.safePopBackStack() },
                                        onAddTroupe = { 
                                            viewModel.resetNewTroupeFields(isTournament = false) 
                                            navController.navigate(Screen.AddEditTroupe.route) 
                                        },
                                        onEditTroupe = { navController.navigate(Screen.AddEditTroupe.route) },
                                        isTutorialActive = isTutorialActive,
                                        onTargetPositioned = { name, coords -> tutorialCoords[name] = coords }
                                    )
                                }
                                composable(Screen.SelectTroupe.route) {
                                    TroupeListScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        onNavigateBack = { 
                                            navController.safePopBackStack()
                                            isSelectingForCampaign = false
                                        },
                                        onAddTroupe = { 
                                            viewModel.resetNewTroupeFields(isTournament = !isSelectingForCampaign, isCampaign = isSelectingForCampaign) 
                                            // Handle special case for campaign troupe creation
                                            if (isSelectingForCampaign) {
                                                viewModel.pendingCampaignPlayerId = targetManualPlayerId
                                            }
                                            navController.navigate(Screen.AddEditTroupe.route) 
                                        },
                                        onEditTroupe = { navController.navigate(Screen.AddEditTroupe.route) },
                                        selectionMode = true,
                                        tournamentCriteria = if (isSelectingForCampaign) null else state.tournamentSettings,
                                        isCampaignSelection = isSelectingForCampaign,
                                        onTroupeSelected = { troupe ->
                                            if (isSelectingForCampaign) {
                                                viewModel.broadcastTroupeSelectionForCampaign(troupe, targetManualPlayerId!!)
                                            } else {
                                                viewModel.broadcastTroupeSelection(troupe, targetManualPlayerId)
                                            }
                                            targetManualPlayerId = null
                                            isSelectingForCampaign = false
                                            navController.safePopBackStack()
                                        },
                                        onTargetPositioned = { name, coords -> tutorialCoords[name] = coords }
                                    )
                                }
                                composable(Screen.AddEditTroupe.route) {
                                    AddEditTroupeScreen(
                                        viewModel = viewModel,
                                        state = state,
                                        onNavigateBack = { navController.safePopBackStack() },
                                        currentTutorialStep = currentTutorialStep,
                                        onTargetPositioned = { name, coords -> tutorialCoords[name] = coords }
                                    )
                                }
                                composable(Screen.SoloTroupeSelect.route) {
                                    SoloTroupeSelectScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        onTroupeSelected = {
                                            navController.navigate(Screen.ActiveGame.route)
                                        },
                                        onEditTroupe = {
                                            navController.navigate(Screen.AddEditTroupe.route)
                                        }
                                    )
                                }
                                composable(Screen.GameSetup.route) {
                                    GameSetupScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.safePopBackStack() },
                                        onStartGame = { 
                                            navController.navigate(Screen.ActiveGame.route)
                                        },
                                        onNavigateToAddEditTroupe = {
                                            navController.navigate(Screen.AddEditTroupe.route)
                                        },
                                        onJoinTournament = {
                                            navController.navigate(Screen.TournamentWaitingRoom.route)
                                        },
                                        currentTutorialStep = currentTutorialStep,
                                        onTargetPositioned = { name, coords -> tutorialCoords[name] = coords }
                                    )
                                }
                                composable(Screen.ActiveGame.route) {
                                    val playersWithCharacters by viewModel.playersWithCharacters.collectAsState()
                                    ActiveGameScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        players = playersWithCharacters,
                                        onQuitGame = { 
                                            navController.popBackStack(Screen.Home.route, inclusive = false)
                                        },
                                        isTutorialActive = isTutorialActive,
                                        currentTutorialStep = currentTutorialStep,
                                        onTargetPositioned = { name, coords -> tutorialCoords[name] = coords }
                                    )
                                }
                                composable(Screen.Stats.route) {
                                    StatsScreen(viewModel = viewModel)
                                }
                                composable(Screen.TournamentSetup.route) {
                                    TournamentSetupScreen(
                                        state = state,
                                        onNavigateBack = { 
                                            if (state.isTournamentHost) {
                                                navController.navigate(Screen.TournamentWaitingRoom.route) {
                                                    popUpTo(Screen.TournamentSetup.route) { inclusive = true }
                                                }
                                            } else {
                                                navController.safePopBackStack() 
                                            }
                                        },
                                        onStartTournament = { name, size, timer, hostParticipating, passcode, hostMode ->
                                            viewModel.onEvent(CharacterEvent.CreateTournament(name, size, timer, hostParticipating, passcode, hostMode))
                                            navController.navigate(Screen.TournamentWaitingRoom.route)
                                        },
                                        isEditMode = state.isTournamentHost,
                                        onUpdateSettings = { name, size, timer, hostParticipating ->
                                            viewModel.updateTournamentSettings(name, size, timer, hostParticipating)
                                            navController.navigate(Screen.TournamentWaitingRoom.route) {
                                                popUpTo(Screen.TournamentSetup.route) { inclusive = true }
                                            }
                                        },
                                        onDisband = { viewModel.disbandTournament() }
                                    )
                                }
                                composable(Screen.TournamentWaitingRoom.route) {
                                    TournamentWaitingRoomScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        onNavigateBack = { 
                                            if (state.isTournamentHost) {
                                                navController.navigate(Screen.TournamentSetup.route)
                                            } else {
                                                navController.safePopBackStack() 
                                            }
                                        },
                                        onSelectTroupe = {
                                            targetManualPlayerId = null
                                            isSelectingForCampaign = false
                                            navController.navigate(Screen.SelectTroupe.route)
                                        },
                                        onSelectTroupeForManual = { manualPlayerId ->
                                            targetManualPlayerId = manualPlayerId
                                            isSelectingForCampaign = false
                                            navController.navigate(Screen.SelectTroupe.route)
                                        },
                                        onBeginTournament = {
                                            viewModel.startTournamentFirstRound()
                                        },
                                        onRoundStarted = {
                                            navController.navigate(Screen.TournamentRound.route)
                                        }
                                    )
                                }
                                composable(Screen.TournamentRound.route) {
                                    TournamentRoundScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.safePopBackStack() }
                                    )
                                }
                                composable(Screen.CampaignHub.route) {
                                    CampaignHubScreen(
                                        state = state,
                                        onLocalTrackingSelected = { navController.navigate(Screen.CampaignManagement.route) },
                                        onHostCampaignSelected = { navController.navigate(Screen.HostOnlineCampaign.route) },
                                        onJoinCampaignSelected = { navController.navigate(Screen.JoinOnlineCampaign.route) },
                                        onActiveCampaignsSelected = { navController.navigate(Screen.ActiveOnlineCampaigns.route) }
                                    )
                                }
                                composable(Screen.CampaignManagement.route) {
                                    CampaignManagementScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.safePopBackStack() },
                                        onNavigateToDetails = { campaignId ->
                                            navController.navigate(Screen.CampaignDetails.createRoute(campaignId))
                                        },
                                        onNavigateToAddCampaign = {
                                            viewModel.resetNewCampaignFields()
                                            navController.navigate(Screen.AddEditCampaign.route)
                                        }
                                    )
                                }
                                composable(Screen.HostOnlineCampaign.route) {
                                    HostOnlineCampaignScreen(
                                        state = state,
                                        onEvent = viewModel::onEvent,
                                        onNavigateBack = { navController.safePopBackStack() }
                                    )
                                }
                                composable(Screen.JoinOnlineCampaign.route) {
                                    JoinOnlineCampaignScreen(
                                        state = state,
                                        onEvent = viewModel::onEvent,
                                        onNavigateBack = { navController.safePopBackStack() }
                                    )
                                }
                                composable(Screen.ActiveOnlineCampaigns.route) {
                                    ActiveOnlineCampaignsScreen(
                                        state = state,
                                        onEvent = viewModel::onEvent,
                                        onNavigateBack = { navController.safePopBackStack() },
                                        onCampaignSelected = { id ->
                                            navController.navigate(Screen.OnlineCampaignDetail.createRoute(id))
                                        }
                                    )
                                }
                                composable(
                                    route = Screen.OnlineCampaignDetail.route,
                                    arguments = listOf(navArgument("campaignId") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val campaignId = backStackEntry.arguments?.getString("campaignId") ?: return@composable
                                    OnlineCampaignDetailScreen(
                                        campaignId = campaignId,
                                        state = state,
                                        onEvent = viewModel::onEvent,
                                        onNavigateBack = { navController.safePopBackStack() },
                                        onDecodeTroupe = { code -> viewModel.importTroupe(code, state.characters, state.upgradeCards) },
                                        onEncodeTroupe = { troupe -> viewModel.generateFullShareCode(troupe, state.characters, state.upgradeCards) }
                                    )
                                }
                                composable(Screen.AddEditCampaign.route) {
                                    AddEditCampaignScreen(
                                        viewModel = viewModel,
                                        state = state,
                                        onNavigateBack = { 
                                            navController.safePopBackStack() 
                                            isSelectingForCampaign = false
                                        },
                                        onSelectTroupeForPlayer = { playerId ->
                                            targetManualPlayerId = playerId
                                            isSelectingForCampaign = true
                                            navController.navigate(Screen.SelectTroupe.route)
                                        }
                                    )
                                }
                                composable(
                                    route = Screen.EditCampaign.route,
                                    arguments = listOf(navArgument("campaignId") { type = NavType.IntType })
                                ) { backStackEntry ->
                                    val campaignId = backStackEntry.arguments?.getInt("campaignId") ?: return@composable
                                    EditCampaignScreen(
                                        campaignId = campaignId,
                                        viewModel = viewModel,
                                        state = state,
                                        onNavigateBack = { 
                                            navController.safePopBackStack() 
                                            isSelectingForCampaign = false
                                        },
                                        onSelectTroupeForPlayer = { playerId ->
                                            targetManualPlayerId = playerId
                                            isSelectingForCampaign = true
                                            navController.navigate(Screen.SelectTroupe.route)
                                        }
                                    )
                                }
                                composable(
                                    route = Screen.CampaignDetails.route,
                                    arguments = listOf(navArgument("campaignId") { type = NavType.IntType })
                                ) { backStackEntry ->
                                    val campaignId = backStackEntry.arguments?.getInt("campaignId") ?: return@composable
                                    CampaignDetailsScreen(
                                        campaignId = campaignId,
                                        state = state,
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.safePopBackStack() },
                                        onNavigateToSettings = { campaignId ->
                                            navController.navigate(Screen.EditCampaign.createRoute(campaignId))
                                        }
                                    )
                                }
                            }

                            if (isTutorialActive) {
                                TutorialOverlay(
                                    steps = fullAppTutorialSteps,
                                    targetCoordinates = tutorialCoords,
                                    navController = navController,
                                    onStepChange = { currentTutorialStep = fullAppTutorialSteps.getOrNull(it) },
                                    onComplete = {
                                        viewModel.onEvent(CharacterEvent.SetHasSeenTutorial("global", true))
                                        showTutorialForcefully = false
                                        currentTutorialStep = null
                                    },
                                    onSkip = {
                                        viewModel.onEvent(CharacterEvent.SetHasSeenTutorial("global", true))
                                        showTutorialForcefully = false
                                        currentTutorialStep = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        } // CompositionLocalProvider
    }
}

@Composable
private fun DataUpdateDialogs(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    theme: AppThemeProperties
) {
    val context = LocalContext.current

    // App update available
    val appRelease = state.pendingAppUpdate
    if (appRelease != null) {
        AlertDialog(
            onDismissRequest = { if (!state.isDownloadingApk) onEvent(CharacterEvent.DismissAppUpdate) },
            title = { Text("App Update Available") },
            text = {
                when {
                    state.installerSource == InstallerSource.FDROID ->
                        Text("Version ${appRelease.tagName} is available. Open the F-Droid client to update.")
                    BuildConfig.CAN_SELF_UPDATE && state.isDownloadingApk -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Downloading ${appRelease.tagName}…")
                            LinearProgressIndicator(
                                progress = { state.apkDownloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${(state.apkDownloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    BuildConfig.CAN_SELF_UPDATE ->
                        Text("Version ${appRelease.tagName} is available. Download and install it now?")
                    else ->
                        Text("Version ${appRelease.tagName} is available on GitHub.")
                }
            },
            confirmButton = {
                when {
                    state.installerSource == InstallerSource.FDROID ->
                        Button(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, "https://f-droid.org/packages/io.github.garemat.lunachron/".toUri()))
                                onEvent(CharacterEvent.DismissAppUpdate)
                            },
                            shape = theme.cardShape
                        ) { Text("Open F-Droid") }
                    BuildConfig.CAN_SELF_UPDATE ->
                        Button(
                            onClick = { onEvent(CharacterEvent.InstallAppUpdate) },
                            enabled = !state.isDownloadingApk,
                            shape = theme.cardShape
                        ) { Text("Download & Install") }
                    else ->
                        Button(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, appRelease.htmlUrl.toUri()))
                                onEvent(CharacterEvent.DismissAppUpdate)
                            },
                            shape = theme.cardShape
                        ) { Text("View Release") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(CharacterEvent.DismissAppUpdate) },
                    enabled = !state.isDownloadingApk
                ) { Text("Later") }
            },
            shape = theme.cardShape
        )
    }

    // First-launch image download prompt
    if (state.pendingImageUpdate == "FIRST_LAUNCH") {
        AlertDialog(
            onDismissRequest = { if (!state.isDownloadingImages) onEvent(CharacterEvent.DismissImageUpdate) },
            title = { Text("Download Character Portraits?") },
            text = {
                Text("Portrait images enhance the Compendium and game screen. They're optional — the app works fully without them.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEvent(CharacterEvent.SetImageDownloadPreference(ImageDownloadPreference.ENABLED))
                        onEvent(CharacterEvent.DownloadCharacterImages)
                    },
                    enabled = !state.isDownloadingImages,
                    shape = theme.cardShape
                ) {
                    if (state.isDownloadingImages) {
                        PortraitDownloadProgress(
                            downloaded = state.imageDownloadedBytes,
                            total = state.imageTotalBytes,
                            speedBps = state.imageDownloadSpeedBps,
                            tintOnPrimary = true
                        )
                    } else {
                        Text("Download")
                    }
                }
            },
            dismissButton = {
                if (!state.isDownloadingImages) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            onEvent(CharacterEvent.SetImageDownloadPreference(ImageDownloadPreference.DISABLED))
                            onEvent(CharacterEvent.DismissImageUpdate)
                        }) { Text("Never") }
                        TextButton(onClick = { onEvent(CharacterEvent.DismissImageUpdate) }) { Text("Not Now") }
                    }
                }
            },
            shape = theme.cardShape
        )
    }

    // Image version update prompt
    val imgTag = state.pendingImageUpdate
    if (imgTag != null && imgTag != "FIRST_LAUNCH") {
        AlertDialog(
            onDismissRequest = { if (!state.isDownloadingImages) onEvent(CharacterEvent.DismissImageUpdate) },
            title = { Text("Character Portrait Update Available") },
            text = { Text("Version $imgTag includes updated or new character portraits.") },
            confirmButton = {
                Button(
                    onClick = { onEvent(CharacterEvent.DownloadCharacterImages) },
                    enabled = !state.isDownloadingImages,
                    shape = theme.cardShape
                ) {
                    if (state.isDownloadingImages) {
                        PortraitDownloadProgress(
                            downloaded = state.imageDownloadedBytes,
                            total = state.imageTotalBytes,
                            speedBps = state.imageDownloadSpeedBps,
                            tintOnPrimary = true
                        )
                    } else {
                        Text("Download")
                    }
                }
            },
            dismissButton = {
                if (!state.isDownloadingImages) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEvent(CharacterEvent.SkipImageVersion(imgTag)) }) { Text("Skip Version") }
                        TextButton(onClick = { onEvent(CharacterEvent.DismissImageUpdate) }) { Text("Later") }
                    }
                }
            },
            shape = theme.cardShape
        )
    }

    // Data update prompt
    val dataRelease = state.pendingDataUpdate
    if (dataRelease != null) {
        if (dataRelease.schemaIncompatible) {
            AlertDialog(
                onDismissRequest = { onEvent(CharacterEvent.DismissDataUpdate) },
                title = { Text("App Update Required") },
                text = { Text("Data version ${dataRelease.tagName} requires a newer version of the app. Please update Lunachron to access the latest game data.") },
                confirmButton = {
                    Button(onClick = { onEvent(CharacterEvent.DismissDataUpdate) }, shape = theme.cardShape) {
                        Text("OK")
                    }
                },
                shape = theme.cardShape
            )
        } else {
            AlertDialog(
                onDismissRequest = { if (!state.isInstallingDataUpdate) onEvent(CharacterEvent.DismissDataUpdate) },
                title = { Text("Game Data Update Available") },
                text = { Text("Version ${dataRelease.tagName} includes updated game data (characters, upgrades, campaign cards).") },
                confirmButton = {
                    Button(
                        onClick = { onEvent(CharacterEvent.InstallDataUpdate(dataRelease)) },
                        enabled = !state.isInstallingDataUpdate,
                        shape = theme.cardShape
                    ) {
                        if (state.isInstallingDataUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Install Now")
                        }
                    }
                },
                dismissButton = {
                    if (!state.isInstallingDataUpdate) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onEvent(CharacterEvent.SkipDataVersion(dataRelease.tagName)) }) { Text("Skip Version") }
                            TextButton(onClick = { onEvent(CharacterEvent.DismissDataUpdate) }) { Text("Later") }
                        }
                    }
                },
                shape = theme.cardShape
            )
        }
    }
}

data class BottomNavItem(val label: String, val route: String, val icon: ImageVector)

fun NavController.safePopBackStack() {
    if (previousBackStackEntry != null) {
        popBackStack()
    }
}
