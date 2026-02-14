package com.garemat.moonstone_companion.ui

import androidx.compose.ui.layout.LayoutCoordinates

data class TutorialState(
    val showTutorial: Boolean = false,
    val currentStep: Int = 0,
    val targetCoordinates: Map<String, LayoutCoordinates> = emptyMap()
)

data class TutorialStep(
    val targetName: String,
    val text: String,
    val isArrowless: Boolean = false,
    val requiredRoute: String? = null
)

val fullAppTutorialSteps = listOf(
    // Welcome
    TutorialStep("", "Welcome to the Moonstone Companion app! Let's take a quick tour of the features.", isArrowless = true, requiredRoute = Screen.Home.route),
    
    // Home Screen
    TutorialStep("Latest News", "Here you can find the latest news and updates for Moonstone.", requiredRoute = Screen.Home.route),
    TutorialStep("MenuButton", "Access the Rules reference and App Settings from this menu.", requiredRoute = Screen.Home.route),
    
    // Bottom Nav - Characters
    TutorialStep("CharactersNav", "The Character Compendium contains a full reference of all available characters.", requiredRoute = Screen.Home.route),
    
    // Character List Screen
    TutorialStep("FilterButtonOpen", "Tap the filter button to search and narrow down the characters.", requiredRoute = Screen.Characters.route),
    TutorialStep("FirstCharacterCard", "Tap a character card to expand it and see their full stats and abilities.", requiredRoute = Screen.Characters.route),
    
    // Bottom Nav - Troupes
    TutorialStep("TroupesNav", "Manage your own custom troupes here.", requiredRoute = Screen.Characters.route),
    
    // Troupe List Screen
    TutorialStep("AddTroupe", "Create a brand new troupe from scratch!", requiredRoute = Screen.Troupes.route),
    
    // Build Troupe Screen
    TutorialStep("TroupeName", "Start by giving your troupe a name!", requiredRoute = Screen.AddEditTroupe.route),
    TutorialStep("SettingsCog", "Open settings to customize troupe behavior.", requiredRoute = Screen.AddEditTroupe.route),
    TutorialStep("AutoSelectSwitch", "Enable Auto Select to skip team selection before games!", requiredRoute = Screen.AddEditTroupe.route),

    // Back to Troupes for Sharing
    TutorialStep("ShareTroupe", "Share your troupe with others by generating a share code.", requiredRoute = Screen.Troupes.route),

    // Bottom Nav - Play
    TutorialStep("PlayNav", "When you're ready to play, head over to the Play section.", requiredRoute = Screen.Troupes.route),
    
    // Game Setup Screen
    TutorialStep("", "You can set up local games or join/host multiplayer sessions here.", isArrowless = true, requiredRoute = Screen.GameSetup.route),
    TutorialStep("LocalGameOption", "Choose 'Local Game' to play with friends on a single device.", requiredRoute = Screen.GameSetup.route),
    TutorialStep("PlayerCount", "Here you can select the number of players for your game.", requiredRoute = Screen.GameSetup.route),
    TutorialStep("TroupeSelector", "Select a troupe for each player. You can also create new ones or scan opponent's codes here.", requiredRoute = Screen.GameSetup.route),
    TutorialStep("StartBattleButton", "Once everyone has selected their troupes, hit BATTLE to begin tracking your game!", requiredRoute = Screen.GameSetup.route),

    // Active Game Screen
    TutorialStep("CharacterDrawerButton", "Pull out the character drawer for quick navigation between your troupe members.", requiredRoute = Screen.ActiveGame.route),
    TutorialStep("MoonstonePool", "The Moonstone Pool tracks unallocated stones. Drag them onto a character to give them one!", requiredRoute = Screen.ActiveGame.route),
    TutorialStep("AbilityLegend", "Quick Glance Icons:\n\nPiercing, Impact, Slicing Buffs\nReduce all damage (Defensive)", isArrowless = true, requiredRoute = Screen.ActiveGame.route),
    TutorialStep("NextTurnButton", "When everyone has finished their activations, hit the Next Turn button to refresh energy and clear once-per-turn abilities.", requiredRoute = Screen.ActiveGame.route),
    TutorialStep("RewindButton", "Accidentally hit next turn? You can rewind back to the previous state here.", requiredRoute = Screen.ActiveGame.route),
    TutorialStep("EndGameQuickButton", "If the game ends early, use this button to calculate the winner immediately.", requiredRoute = Screen.ActiveGame.route),
    TutorialStep("CloseGameButton", "You can close this screen to return to the rest of the app. Your game state will be saved so you can jump right back in!", requiredRoute = Screen.ActiveGame.route),
    
    // Bottom Nav - Stats
    TutorialStep("StatsNav", "Finally, track your win/loss records and other statistics here.", requiredRoute = Screen.GameSetup.route),
    
    TutorialStep("", "That's it! You're ready to start using the Moonstone Companion. Enjoy your games!", isArrowless = true, requiredRoute = Screen.Stats.route)
)
