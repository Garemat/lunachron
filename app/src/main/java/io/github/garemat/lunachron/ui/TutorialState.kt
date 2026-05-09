package io.github.garemat.lunachron.ui

import androidx.compose.ui.layout.LayoutCoordinates

sealed class AdvanceCondition {
    /** User taps the "Got it" / labelled button in the tooltip. */
    data object Manual : AdvanceCondition()
    /** Auto-advances when NavController reaches the given route. */
    data class OnNavigation(val route: String) : AdvanceCondition()
    /** Auto-advances when a named state condition becomes true (resolved in TutorialOverlay). */
    data class OnStateChange(val key: String) : AdvanceCondition()
    /**
     * The overlay intercepts a tap within the spotlight and advances automatically.
     * The touch also passes through to the real UI beneath so the actual action fires.
     */
    data object OnSpotlightTap : AdvanceCondition()
}

/**
 * A single tutorial step.
 *
 * All fields are plain data — no lambdas, no composable references. Adding or reordering steps
 * only requires editing this list; no composable code needs to change unless a new [targetTag]
 * is introduced (which then needs one [Modifier.onGloballyPositioned] call at the target site).
 */
data class TutorialStep(
    /** Stable key registered via [Modifier.onGloballyPositioned] at the target call site. Null = no spotlight. */
    val targetTag: String? = null,
    val message: String,
    val advance: AdvanceCondition = AdvanceCondition.Manual,
    /** Label for the manual-advance button. */
    val buttonLabel: String = "Got it",
    /**
     * When set, the tutorial navigates to this route before showing the step.
     * Omit if the user is expected to navigate themselves (e.g. OnNavigation steps).
     */
    val requiredRoute: String? = null,
    /**
     * When non-null, [AddEditTroupeScreen] opens the Import tab with this code pre-filled.
     * Carry this on both the step that triggers FAB navigation AND the Import-button step so
     * the screen sees it regardless of which recomposition arrives first.
     */
    val importPrefill: String? = null,
)

private const val EXAMPLE_TROUPE_CODE = "RXhhbXBsZSBUcm91cGV8QTBBQUFBQkFBQUFDQUFBQUQ="

val appTutorialSteps: List<TutorialStep> = listOf(

    // 0 — Welcome (shown on Home so portrait downloads can complete before the user reaches the Compendium)
    TutorialStep(
        message = "Welcome to Lunachron! Let's take a quick tour of the key features — it'll only take a minute.",
        advance = AdvanceCondition.Manual,
        buttonLabel = "Let's go!",
        requiredRoute = Screen.Home.route
    ),

    // 1 — Navigate to Compendium (stepping stone; skips the landing page and goes straight to the character list)
    TutorialStep(
        targetTag = "CompendiumNav",
        message = "Tap the Compendium tab to explore every character available in Moonstone.",
        advance = AdvanceCondition.OnNavigation(Screen.Characters.route)
    ),

    // 2 — Character Compendium
    TutorialStep(
        targetTag = "CharacterList",
        message = "This is the Character Compendium — a full reference of every character available in Moonstone.",
        advance = AdvanceCondition.Manual
    ),

    // 3 — Filter button (the search panel is open by default on this screen)
    TutorialStep(
        targetTag = "FilterButtonOpen",
        message = "Tap the filter button to hide the search panel — you can reopen it anytime to filter by name, faction, or keywords.",
        advance = AdvanceCondition.OnSpotlightTap
    ),

    // 4 — Menu / drawer button
    // Uses OnStateChange("drawer_opened") rather than OnSpotlightTap so the tutorial waits for
    // the drawer to actually finish opening before advancing — that way the user sees its contents
    // before step 5 explains them.
    TutorialStep(
        targetTag = "MenuButton",
        message = "The menu button opens the side drawer. Tap it to take a look.",
        advance = AdvanceCondition.OnStateChange("drawer_opened"),
        requiredRoute = Screen.Compendium.route
    ),

    // 5 — Drawer contents (arrowless — drawer is open from step 4)
    TutorialStep(
        message = "From here you can access the Rules reference, your Stats, and Settings. " +
                "In Settings you can change your default start page — handy if you prefer to open straight to the Compendium.",
        advance = AdvanceCondition.Manual,
        buttonLabel = "Got it"
    ),

    // 6 — Troupes tab  (onStepChanged(6) in MainActivity closes the drawer)
    TutorialStep(
        targetTag = "TroupesNav",
        message = "Next, let's head to My Troupes to build your roster.",
        advance = AdvanceCondition.OnNavigation(Screen.Troupes.route)
    ),

    // 7 — Add / Import FAB  (importPrefill carried here so screen sees it even if state lags)
    TutorialStep(
        targetTag = "AddTroupe",
        message = "Tap + to create or import a troupe. We've queued up an example troupe — the Import tab will be ready for you on the next screen!",
        advance = AdvanceCondition.OnNavigation(Screen.AddEditTroupe.route),
        importPrefill = EXAMPLE_TROUPE_CODE
    ),

    // 8 — Import button inside AddEditTroupeScreen (Import tab pre-filled)
    TutorialStep(
        targetTag = "ImportButton",
        message = "The example share code is already filled in — tap Import to add it to your collection.",
        advance = AdvanceCondition.OnStateChange("troupe_added"),
        importPrefill = EXAMPLE_TROUPE_CODE
    ),

    // 9 — Tap troupe card to open the editor
    TutorialStep(
        targetTag = "TroupeCard0",
        message = "Tap a troupe card to open it in the editor.",
        advance = AdvanceCondition.OnNavigation(Screen.AddEditTroupe.route),
        requiredRoute = Screen.Troupes.route
    ),

    // 10 — Inside the editor: troupe types — advance when user navigates back to Troupes
    TutorialStep(
        message = "Inside the editor you can switch between Normal and Campaign troupe types — " +
                "Campaign troupes track upgrade cards and victory points. " +
                "When you're done exploring, tap the back arrow to return to your troupes.",
        advance = AdvanceCondition.OnNavigation(Screen.Troupes.route),
    ),

    // 11 — Back on Troupes: share codes and QR (no requiredRoute — user navigated here naturally)
    TutorialStep(
        targetTag = "TroupeCard0",
        message = "You can also tap the QR icon or share button on a troupe card to swap rosters with opponents — handy for quickly loading someone else's troupe.",
        advance = AdvanceCondition.Manual,
    ),

    // 12 — Favourite star
    TutorialStep(
        targetTag = "FavouriteStar0",
        message = "Star a troupe as a favourite for quick access from the home screen.",
        advance = AdvanceCondition.OnStateChange("troupe_favourited")
    ),

    // 13 — Quick Start on Home
    TutorialStep(
        targetTag = "QuickStartSection",
        message = "Favourited troupes appear here in Quick Start — tap one to launch a game immediately without any setup.",
        advance = AdvanceCondition.Manual,
        requiredRoute = Screen.Home.route
    ),

    // 14 — Play tab
    TutorialStep(
        targetTag = "PlayNav",
        message = "The Play tab is where you set up local games. Select your player count, pick your troupes, and go! " +
                "If a troupe has more characters than the game size allows, you'll choose which ones to field. " +
                "Synced troupe lists are coming in an upcoming update.",
        advance = AdvanceCondition.OnNavigation(Screen.GameSetup.route)
    ),

    // 15 — Campaigns tab
    TutorialStep(
        targetTag = "CampaignsNav",
        message = "The Campaigns tab is home to the Wizard Chamberlain — your campaign organiser. " +
                "Run a local tracking campaign, or register your device to host a fully synced online campaign that others can join.",
        advance = AdvanceCondition.OnNavigation(Screen.CampaignHub.route)
    ),

    // 16 — Cleanup (arrowless — example troupe position in list is unpredictable after re-runs)
    TutorialStep(
        message = "That's everything! If you'd like to remove the example troupe, tap its delete button (🗑) on the troupe card. Otherwise, feel free to keep it.",
        advance = AdvanceCondition.Manual,
        buttonLabel = "Done",
        requiredRoute = Screen.Troupes.route
    ),
)
