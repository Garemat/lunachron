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
./gradlew test --tests "io.github.garemat.lunachron.ExampleUnitTest"

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
- `GameDatabase.kt` — Room database for game data (Character, UpgradeCard, CampaignCard); uses `fallbackToDestructiveMigration()`, always reseedable from compendium
- `UserDatabase.kt` — Room database for user data (Troupe, Campaign, GameResult); **never** uses `fallbackToDestructiveMigration()` — all schema changes require explicit `Migration` objects to preserve user data
- `GameDataDAO.kt` — DAO for game data entities
- `UserDataDAO.kt` — DAO for user data entities
- `CharacterData.kt` — Parses JSON assets into domain objects
- `NearbyManager.kt` — P2P multiplayer via Android NSD (mDNS) + TCP sockets; supports `WIFI_NSD` (same router) and `WIFI_DIRECT` (no router) host modes
- `Converters.kt` — Room TypeConverters for serializing complex types (lists, maps, enums) as JSON strings
- `MainActivity.kt` — Sets up NavHost with all routes, bottom nav bar (Home/Compendium/Play/Troupes/Campaigns), and side drawer (Rules/Tutorial/Settings/Tournament/Stats)
- `ui/Screen.kt` — Sealed class defining all navigation routes

### Navigation Structure

Bottom nav tabs: Home, Compendium, Play (GameSetup), Troupes, Campaigns
Side drawer: Rules, Tutorial Help, Settings, Local Tournament, Stats

Compendium sub-screens: Characters list, Upgrades list, Campaign Cards list
Campaign screens: CampaignManagement → CampaignDetails / AddEditCampaign / EditCampaign

### Data / Assets

Game data (characters, upgrades, campaign cards) is sourced from the `lunachron-data` repo and bundled as consolidated JSON files in `app/src/main/assets/`:
- `characters.json` — All characters (aggregated from data repo)
- `upgrades.json` — All upgrade cards
- `campaign.json` — All campaign cards
- `rules.json` — Rules reference data
- `moonstone-data.json` — Additional game data

`CharacterData.kt` reads from `filesDir/data/` first (downloaded updates), falling back to bundled assets. This means the app always works offline with bundled data, and updates are applied transparently when downloaded.

**Database migration rules:**
- `GameDatabase` uses `fallbackToDestructiveMigration()` — incrementing `version` in `GameDatabase.kt` will wipe and recreate game data, which is fine because it is always reseedable from the compendium.
- `UserDatabase` must **never** use `fallbackToDestructiveMigration()` — incrementing `version` in `UserDatabase.kt` **requires** an explicit `Migration(from, to)` object with the SQL changes. Room will crash on launch rather than silently wipe user data if a migration is missing.

`IntOrStringSerializer` in `Character.kt` handles JSON fields that may be either int or string.

### Multiplayer

`NearbyManager` handles peer-to-peer connections for:
- Casual games (GameSetup → ActiveGame)
- Tournaments (TournamentSetup → TournamentWaitingRoom → TournamentRound)

The ViewModel processes JSON payloads from Nearby via `SessionMessages.kt` message types.

### Factions

Four factions: COMMONWEALTH, DOMINION, LESHAVULT, SHADES
Characters can belong to multiple factions.

**Share codes** — every compendium item has a `shareCode` field (5 chars): `[type][faction][id0][id1][id2]`
- Type: `A` = character, `B` = upgrade card, `C` = campaign card
- Faction: `A`=Commonwealth, `B`=Dominion, `C`=Leshavult, `D`=Shades, `E`=Commonwealth+Dominion, `F`=Commonwealth+Leshavult, `G`=Commonwealth+Shades, `H`=Dominion+Leshavult, `I`=Dominion+Shades, `J`=Leshavult+Shades, `K`=All four factions (also used for items with no faction — upgrades/campaign cards)
- ID digits: each digit encoded `A`=0 … `J`=9 (e.g. ID 42 → `EC`)
- Share codes are stored directly in source JSON files in the data repo and validated by `scripts/lint_data.py`

**Troupe share string** — `base64(faction_letter + char_code + upgrade_codes... + char_code + upgrade_codes...)` where items are concatenated in order with no delimiter (each code is always 5 chars).

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
