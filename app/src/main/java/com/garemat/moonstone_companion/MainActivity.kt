package com.garemat.moonstone_companion

import android.os.Bundle
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
import com.garemat.moonstone_companion.ui.theme.AppThemeProperties
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
import com.garemat.moonstone_companion.ui.*
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties
import com.garemat.moonstone_companion.ui.theme.MoonstonecompanionTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val db by lazy {
        CharacterDatabase.getDatabase(applicationContext)
    }

    private val viewModel by viewModels<CharacterViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repo = LocalCharacterRepository(db.dao)
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
            val isTutorialActive = !state.hasSeenGlobalTutorial || showTutorialForcefully
            var currentTutorialStep by remember { mutableStateOf<TutorialStep?>(null) }

            // State for manual troupe selection
            var targetManualPlayerId by remember { mutableStateOf<String?>(null) }
            var isSelectingForCampaign by remember { mutableStateOf(false) }

            MoonstonecompanionTheme(
                appTheme = state.theme,
                layoutDensity = state.layoutDensity
            ) {
                val theme = LocalAppThemeProperties.current
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

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
                        Screen.CampaignManagement.route,
                        Screen.AddEditCampaign.route,
                        "edit_campaign",
                        "campaign_details"
                    )
                }

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
                                "Moonstone Companion",
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
                                icon = { Icon(Icons.Default.Help, contentDescription = null) },
                                label = { Text("Tutorial Help", style = theme.headerStyle.copy(fontSize = 18.sp)) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    showTutorialForcefully = true
                                },
                                shape = theme.navItemShape,
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedTextColor = MaterialTheme.colorScheme.primary
                                )
                            )
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
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                                label = { Text("Setup Local Tournament", style = theme.headerStyle.copy(fontSize = 18.sp)) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(Screen.TournamentSetup.route)
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
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (showNavBars) {
                                CenterAlignedTopAppBar(
                                    title = { Text(
                                        text = when {
                                            currentDestination?.route == Screen.Home.route -> "Moonstone Companion"
                                            currentDestination?.route == Screen.Compendium.route -> "Compendium"
                                            currentDestination?.route == Screen.Characters.route -> "Character List"
                                            currentDestination?.route == Screen.Upgrades.route -> "Upgrades"
                                            currentDestination?.route == Screen.CampaignCards.route -> "Campaign Cards"
                                            currentDestination?.route == Screen.Troupes.route -> "My Troupes"
                                            currentDestination?.route == Screen.AddEditTroupe.route -> if (viewModel.editingTroupeId == null) "Build Troupe" else "Edit Troupe"
                                            currentDestination?.route == Screen.GameSetup.route -> "Game Setup"
                                            currentDestination?.route == Screen.CampaignManagement.route -> "Wizard Chamberlain"
                                            currentDestination?.route == Screen.AddEditCampaign.route -> "New Campaign"
                                            currentDestination?.route?.startsWith("edit_campaign") == true -> "Campaign Settings"
                                            currentDestination?.route?.startsWith("campaign_details") == true -> {
                                                val cId = navController.currentBackStackEntry?.arguments?.getInt("campaignId")
                                                val camp = state.campaigns.find { it.id == cId }
                                                val r = camp?.currentRound
                                                when (viewModel.currentCampaignSubScreen) {
                                                    CampaignSubScreen.RANKINGS -> "Rankings"
                                                    CampaignSubScreen.GAMES -> if (r != null) "Round $r — Games" else "Games"
                                                    CampaignSubScreen.MACHINATIONS -> if (r != null) "Round $r — Machinations" else "Machinations"
                                                    CampaignSubScreen.ATTACKS -> if (r != null) "Round $r — Attacks" else "Attacks"
                                                    CampaignSubScreen.HISTORY -> "Round History"
                                                    null -> camp?.name ?: "Campaign"
                                                }
                                            }
                                            else -> "Moonstone Companion"
                                        },
                                        style = theme.titleStyle
                                    ) },
                                    navigationIcon = {
                                        if (currentDestination?.route in listOf(Screen.AddEditTroupe.route, Screen.Characters.route, Screen.Upgrades.route, Screen.CampaignCards.route, Screen.Rules.route, Screen.Settings.route, Screen.TournamentSetup.route, Screen.AddEditCampaign.route) || currentDestination?.route?.startsWith("edit_campaign") == true || currentDestination?.route?.startsWith("campaign_details") == true) {
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
                                        // actions removed as tutorial button was migrated to drawer
                                    },
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
                            if (showNavBars) {
                                NavigationBar(
                                    containerColor = if (state.theme == AppTheme.MOONSTONE) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = theme.navigationBarElevation
                                ) {
                                    val playRoute = if (state.useLocalModeByDefault && state.useSinglePlayerMode)
                                        Screen.SoloTroupeSelect.route else Screen.GameSetup.route
                                    val items = listOf(
                                        BottomNavItem("Home", Screen.Home.route, Icons.Default.Home),
                                        BottomNavItem("Compendium", Screen.Compendium.route, Icons.Default.MenuBook),
                                        BottomNavItem("Play", playRoute, Icons.Default.PlayArrow),
                                        BottomNavItem("Troupes", Screen.Troupes.route, Icons.Default.Groups),
                                        BottomNavItem("Campaigns", Screen.CampaignManagement.route, Icons.Default.HistoryEdu)
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
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (state.theme == AppTheme.MOONSTONE) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
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
                                                    style = theme.labelStyle
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
                                                indicatorColor = if (isPlayButton) Color.Transparent else if (state.theme == AppTheme.MOONSTONE) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Home.route,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable(Screen.Home.route) {
                                    HomeScreen(
                                        state = state,
                                        onEvent = viewModel::onEvent,
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
    }
}

@Composable
private fun DataUpdateDialogs(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    theme: AppThemeProperties
) {
    // First-launch image download prompt
    if (state.pendingImageUpdate == "FIRST_LAUNCH") {
        AlertDialog(
            onDismissRequest = { onEvent(CharacterEvent.DismissImageUpdate) },
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
                    shape = theme.cardShape
                ) { Text("Download") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        onEvent(CharacterEvent.SetImageDownloadPreference(ImageDownloadPreference.DISABLED))
                        onEvent(CharacterEvent.DismissImageUpdate)
                    }) { Text("Never") }
                    TextButton(onClick = { onEvent(CharacterEvent.DismissImageUpdate) }) { Text("Not Now") }
                }
            },
            shape = theme.cardShape
        )
    }

    // Image version update prompt
    val imgTag = state.pendingImageUpdate
    if (imgTag != null && imgTag != "FIRST_LAUNCH") {
        AlertDialog(
            onDismissRequest = { onEvent(CharacterEvent.DismissImageUpdate) },
            title = { Text("Character Portrait Update Available") },
            text = { Text("Version $imgTag includes updated or new character portraits.") },
            confirmButton = {
                Button(
                    onClick = { onEvent(CharacterEvent.DownloadCharacterImages) },
                    shape = theme.cardShape
                ) { Text("Download") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEvent(CharacterEvent.SkipImageVersion(imgTag)) }) { Text("Skip Version") }
                    TextButton(onClick = { onEvent(CharacterEvent.DismissImageUpdate) }) { Text("Later") }
                }
            },
            shape = theme.cardShape
        )
    }

    // Data update prompt
    val dataRelease = state.pendingDataUpdate
    if (dataRelease != null) {
        AlertDialog(
            onDismissRequest = { onEvent(CharacterEvent.DismissDataUpdate) },
            title = { Text("Game Data Update Available") },
            text = { Text("Version ${dataRelease.tagName} includes updated game data (characters, upgrades, campaign cards).") },
            confirmButton = {
                Button(
                    onClick = { onEvent(CharacterEvent.InstallDataUpdate(dataRelease)) },
                    shape = theme.cardShape
                ) { Text("Install Now") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEvent(CharacterEvent.SkipDataVersion(dataRelease.tagName)) }) { Text("Skip Version") }
                    TextButton(onClick = { onEvent(CharacterEvent.DismissDataUpdate) }) { Text("Later") }
                }
            },
            shape = theme.cardShape
        )
    }
}

data class BottomNavItem(val label: String, val route: String, val icon: ImageVector)

fun NavController.safePopBackStack() {
    if (previousBackStackEntry != null) {
        popBackStack()
    }
}
