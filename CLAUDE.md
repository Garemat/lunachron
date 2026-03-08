# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This is an Android project. Use Gradle wrapper from the project root:

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires env vars: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "com.garemat.moonstone_companion.ExampleUnitTest"

# Clean build
./gradlew clean
```

The app targets SDK 36, min SDK 24, Java 17.

## Architecture Overview

This is a single-`Activity` Android app using Jetpack Compose with a unidirectional data flow pattern:

**Core pattern:** `CharacterEvent` (sealed interface) → `CharacterViewModel.onEvent()` → `CharacterState` (state holder data class) → UI composables

### Key Files

- `CharacterEvent.kt` — All user actions/intents as a sealed interface
- `CharacterState.kt` — Single immutable state object holding all UI state (characters, troupes, campaigns, tournament state, active game state, etc.)
- `CharacterViewModel.kt` — Central ViewModel; processes events, manages `NearbyManager`, Ktor HTTP client (for news/rules), and exposes `state: StateFlow<CharacterState>`
- `Character.kt` — All domain model data classes (`Character`, `Troupe`, `Campaign`, `GameResult`, `UpgradeCard`, `CampaignCard`, etc.)
- `CharacterDatabase.kt` — Room database singleton; auto-populates from assets on create/open
- `CharacterDAO.kt` — Room DAO with Flow-returning queries
- `CharacterData.kt` — Parses JSON assets into domain objects
- `NearbyManager.kt` — Wraps Google Play Services Nearby Connections API (P2P_STAR strategy) for multiplayer
- `Converters.kt` — Room TypeConverters for serializing complex types (lists, maps, enums) as JSON strings
- `MainActivity.kt` — Sets up NavHost with all routes, bottom nav bar (Home/Compendium/Play/Troupes/Campaigns), and side drawer (Rules/Tutorial/Settings/Tournament/Stats)
- `ui/Screen.kt` — Sealed class defining all navigation routes

### Navigation Structure

Bottom nav tabs: Home, Compendium, Play (GameSetup), Troupes, Campaigns
Side drawer: Rules, Tutorial Help, Settings, Local Tournament, Stats

Compendium sub-screens: Characters list, Upgrades list, Campaign Cards list
Campaign screens: CampaignManagement → CampaignDetails / AddEditCampaign / EditCampaign

### Data / Assets

Game data (characters, upgrades, campaign cards) lives in `app/src/main/assets/`:
- `characters/` — One JSON file per character (named `{id}_{name}.json`)
- `upgrades/` — One JSON file per upgrade card
- `campaign/` — One JSON file per campaign card
- `rules.json` — Rules reference data
- `moonstone-data.json` — Additional game data

The database uses `fallbackToDestructiveMigration()` — incrementing `version` in `CharacterDatabase.kt` will wipe and recreate the DB.

`IntOrStringSerializer` in `Character.kt` handles JSON fields that may be either int or string.

### Multiplayer

`NearbyManager` handles peer-to-peer connections for:
- Casual games (GameSetup → ActiveGame)
- Tournaments (TournamentSetup → TournamentWaitingRoom → TournamentRound)

The ViewModel processes JSON payloads from Nearby via `SessionMessages.kt` message types.

### Factions

Four factions: COMMONWEALTH, DOMINION, LESHAVULT, SHADES
Characters can belong to multiple factions; the `shareCode` field on `Character` encodes faction combination (A=Commonwealth only, B=Dominion only, etc. — see README.md).

### Theme

Two app themes: `AppTheme.DEFAULT` and `AppTheme.MOONSTONE`. Theme properties are exposed via `LocalAppThemeProperties` composition local. Three layout densities: COMPACT, COZY, SPACIOUS.

Theme properties live in `ui/theme/ThemeProperties.kt` (`AppThemeProperties` data class). Always use these instead of hardcoded values:
- `theme.cardShape` — card corner radius (0dp square in Moonstone, rounded in Default)
- `theme.cardBackground` — card container color (`surfaceVariant` in Moonstone, `surface` in Default)
- `theme.cardContentPadding` — inner card padding
- `theme.screenPadding` / `theme.verticalSpacing` — layout spacing
- `theme.titleStyle`, `theme.headerStyle`, `theme.labelStyle`, `theme.buttonTextStyle` — text styles
- `theme.moonstoneColor`, `theme.positiveColor` — semantic game colors
- `MaterialTheme.colorScheme.*` — for all other colors (primary, secondary, surface, etc.)

### UI Component Conventions

**Always use `ThemedCard` instead of `Card`** (`ui/CommonComponents.kt`). It automatically applies `theme.cardShape` and `theme.cardBackground`. Only pass `containerColor` when overriding for a specific state (e.g. dead character):
```kotlin
ThemedCard(modifier = Modifier.fillMaxWidth()) { ... }                          // normal
ThemedCard(containerColor = Color.DarkGray, modifier = ...) { ... }            // override
```

**`HealthPipsChunked`** (`ui/CommonComponents.kt`) — canonical health display: groups of 5 with `|` separator, new row after 10. Use this instead of `HealthTracker` in game contexts. `compact = true` uses smaller pips (9dp) for grid cards; `compact = false` for full cards.

**`SelectionOption`** (`ui/SettingsScreen.kt`) — canonical radio-button option row with optional subtitle. `ThemeOption` and `DensityOption` are aliases that delegate to it.

**`SummonerIndicator`** (`ui/ActiveGameScreen.kt`) — shared composable for displaying which character summoned whom. Used by both `COMPACT_GRID` and `DETAILED_LIST` layouts.

**`TroupeListItem`** (`ui/TroupeListScreen.kt`) — canonical troupe card used in all troupe selection contexts (solo, campaign, tournament). Key params:
- `selectionMode = true` — shows edit button instead of share; also pass `characters` to display the character list below the header
- `showDelete = false` — hides the delete button (used in solo troupe select)
- `characters: List<Character>?` — when non-null, renders a name list below the card header; omit faction suffix (redundant — all members share the troupe faction)

`TroupeListScreen` automatically passes `characters` to `TroupeListItem` when `selectionMode = true`, so campaign and tournament selection get the character list for free. `SoloTroupeSelectScreen` calls `TroupeListItem` directly with `showDelete = false`.

### Active Game Screen

`ActiveGameScreen.kt` supports two layout modes controlled by `GameLayoutMode` (persisted in SharedPreferences):
- `COMPACT_GRID` — 2-column `LazyVerticalGrid` with `GameCharacterGridCard`
- `DETAILED_LIST` — single-column `LazyColumn` with `FrontSide` (full card)

`GameCharacterGridCard` layout (top to bottom):
1. Character name + signature move in italics (`Name - *Signature Move*`)
2. Portrait | stats (melee/evade/damage modifiers) — side by side
3. Trackable resources row (FULL_TRACKING only): energy `−/E:n/+` then compact circle toggles for each `oncePerTurn`/`oncePerGame` ability
4. Moonstones
5. `HealthPipsChunked`
6. `SummonerIndicator`

Two tracking modes controlled by `GameTrackingMode` (persisted in SharedPreferences):
- `LOW_DETAIL` — health pips only; energy/moonstones/abilities tracked physically
- `FULL_TRACKING` — energy counters, moonstone drag-and-drop, ability used markers, collapsible resource pool bar

`FrontSide` accepts `trackingMode: GameTrackingMode = FULL_TRACKING`. When `LOW_DETAIL`, the energy section is hidden. Health always uses `HealthPipsChunked`.

Settings for both are in `SettingsScreen.kt` under "Game View" and "Game Layout" sections.
