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

## CI / Release Workflows

**Release trigger:** `release.yml` fires on `push: main` — every merge to main (including Dependabot) creates a tagged release. Do not change this to `pull_request: closed`; that event is unreliable and has silently dropped in the past. A `workflow_dispatch` trigger is also available for manual recovery.

**Versioning:** `versionName` and `versionCode` in `app/build.gradle.kts` are derived at build time from the latest git tag (via `git describe --tags`). Do not hardcode them. The release workflow computes the next version via git-cliff, creates the tag on the merge commit, pushes it, and then builds — Gradle reads the tag automatically.

**No bot commits to main:** The release workflow never pushes commits to main. It only pushes a tag. This means no branch-protection bypass is required and no `[skip ci]` tricks are needed.

**Concurrency:** `release.yml` uses `concurrency: group: release, cancel-in-progress: false`. Concurrent merges queue; each run fetches all tags via `fetch-depth: 0` on checkout, so it always sees the previous run's tag before computing its own version.

**`data.version`:** Pinned lunachron-data release tag used by Gradle to download the bundled asset snapshot. Update this file manually via PR when you want to bundle a newer data release. Do not automate it from the release workflow — the app fetches data updates at runtime anyway, so the bundled seed only needs updating occasionally.

**Commit messages:** Never include the literal string `[skip ci]` anywhere in a commit message (including the body) — GitHub scans the full message and will suppress all workflow runs for that commit.

## F-Droid Distribution

The app is distributed via [F-Droid](https://f-droid.org). The fdroiddata metadata lives in a separate fork at `gitlab.com/Garemat/fdroiddata` (MR: `fdroid/fdroiddata!34869`).

**Dependency policy:** All runtime dependencies must be free/open-source (Apache-2.0 or MIT). Do not add any Google Play Services, ML Kit, Firebase, or other non-free libraries — F-Droid's SUSS scanner will block the build.
- QR scanning uses **ZXing** (`com.google.zxing:core`, Apache-2.0) — not ML Kit.

**Local F-Droid build test** (requires `fdroidserver` and `gradlew-fdroid` on PATH):
```bash
# From the fdroiddata repo root
ANDROID_HOME=/home/bazzite/Android/Sdk fdroid build --latest io.github.garemat.lunachron
```
Run `fdroid readmeta` and `fdroid rewritemeta io.github.garemat.lunachron` to validate metadata formatting before pushing to the fdroiddata fork.

**Fastlane metadata** lives in `fastlane/metadata/android/en-US/` — screenshots, descriptions, and `images/icon.png` are read by F-Droid for the store listing.

**App icon source of truth:** `fastlane/metadata/android/en-US/images/icon.png` (512×512 PNG) is the canonical app icon. The mipmap `.webp` files in `app/src/main/res/mipmap-*/` are the Android adaptive launcher icon components (foreground/background layers) and cannot be replaced by the fastlane image — they serve different purposes. When the icon is updated, both `fastlane/metadata/android/en-US/images/icon.png` (flat composite, for store listings) and the mipmap assets (adaptive icon layers, for the device launcher) must be updated.

**Changelog:** `CHANGELOG.md` in the repo root follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format. Add an entry under `[Unreleased]` for every user-visible change. When cutting a release, move `[Unreleased]` entries to the new version section and update the comparison links at the bottom.

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

### Home Screen

`HomeScreen.kt` shows a "Quick Start" section above the news feed when the user has at least one favourite troupe. Tapping a Quick Start card calls `onQuickStartTroupe(troupe)` which is wired in `MainActivity.kt` to call `viewModel.startNewGame(listOf(troupe))` and navigate directly to `ActiveGame`.

The Quick Start section auto-hides with `AnimatedVisibility` when the user scrolls more than ~80px into the news `LazyColumn` (`derivedStateOf` on `LazyListState.firstVisibleItemScrollOffset`). It reappears when they scroll back to the top.

### Game Setup Flow

The Play tab (`GameSetupScreen.kt`) shows:
1. **`GameModeHeroUI`** (entry point, `SetupCommonUI.kt`) — a hero card for "Local Game" with animated faction-colour cycling gradient background. Three secondary mode rows (Host Game, Join Game, Local Tournament) are deliberately deprecated and show a "getting improved in an upcoming update" Toast when tapped.
2. **`OfflineSetupUI`** (`OfflineSetupUI.kt`) — a 3-step flow triggered by "Local Game":
   - **Step 1 — Player Count**: 1–4 player cards with per-count character limits and a visual shape indicator. `maxCharsForCount()`: 1–2 players → 6, 3 → 4, 4 → 3.
   - **Step 2 — Troupe Selection**: N slot cards (`TroupeSlotCard`). Each slot has a "Browse" button that opens `TroupeBrowseSheet` (a `ModalBottomSheet` listing saved troupes with overlapping character portrait circles). If a selected troupe has more characters than the game allows, the slot enters an amber "trimming" state with an inline `CharacterPickerRow` for the player to select the characters they'll play with.
   - **Step 3 — Ready**: Summary of all troupes/characters before starting.
   - Key types: `SlotData(troupe, selectedCharIds, pickerOpen)`, `LocalSetupStep` enum, `slotStatus()` helper returning `"empty"/"trimming"/"confirmed"`.

### Troupe Favourites

`Troupe.isFavourite: Boolean = false` field (added in `Character.kt`). `UserDatabase` is at **version 3** with `MIGRATION_2_3` adding the `isFavourite` column. Toggle via `CharacterEvent.ToggleTroupeFavourite(troupeId)`. Star button shown on `TroupeListItem` when not in selection mode — gold filled star (`Color(0xFFFFC107)`) when favourite, outlined star at 50% alpha otherwise.

**Bug to avoid:** `ToggleTroupeFavourite` must look up the troupe in `state.value.troupes` (the combined `StateFlow`), **not** `_state.value.troupes` (the internal `MutableStateFlow` whose `.troupes` is always empty).

### Settings

The "Gameplay" section (previously contained "Skip Game Mode Selection" and "Only track 1 player" toggles) has been removed. The setup flow now always starts at `GameModeHeroUI` and the player count / single-player behaviour is configured inline in the new setup steps. The underlying `useLocalModeByDefault` and `useSinglePlayerMode` state fields are retained but no longer exposed in Settings UI.

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

### LunaChron Backend (Online Campaigns)

Online campaign coordination is optional — the app is fully functional without it. Users opt in by registering a device in Settings.

**API client:** `api/LunaChronApi.kt` — Ktor HTTP client wrapping all backend endpoints. Key points:
- Base URL is a `BuildConfig` field: `http://10.0.2.2:3000` in debug (emulator loopback), `https://api.garemat.co.uk` in release.
- Session token and backend device UUID are persisted in `SharedPreferences` (`lunachron_prefs`). The backend UUID is distinct from the Android device ID — it is the `devices.id` primary key assigned by the server and is used to identify the device in campaign schedules and match results.
- Timeouts are set to 35s with one automatic retry on timeout. OCI Functions cold start (container spin-up + DB pool init) can take up to ~25s; the retry ensures a warm-container hit if the first attempt loses the race.

**Registration flow:** `CharacterEvent.RegisterDevice` → `apiClient.register(persistentDeviceId, username)`. If the device is already registered (`DEVICE_EXISTS`), the client automatically falls through to login. `isRegistered` / `backendDeviceId` in `CharacterState` reflect the persisted state.

**What the backend stores:** A keyed HMAC hash of the Android device ID (never the raw ID), the username, and 30-day session tokens. Campaigns, members, schedules, and match results are all server-side.

**Screens added:** `CampaignHubScreen` (entry point), `HostOnlineCampaignScreen`, `JoinOnlineCampaignScreen`, `ActiveOnlineCampaignsScreen`, `OnlineCampaignDetailScreen`. All online state lives in `CharacterState` under the `// LunaChron API` comment blocks.

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
- `onToggleFavourite: (() -> Unit)?` — when non-null (only in normal list mode, not selection mode), renders a star icon button; gold filled when `troupe.isFavourite`, outlined at 50% alpha otherwise

`TroupeListScreen` automatically passes `characters` to `TroupeListItem` when `selectionMode = true`, so campaign and tournament selection get the character list for free. `SoloTroupeSelectScreen` calls `TroupeListItem` directly with `showDelete = false`.

### Active Game Screen

`ActiveGameScreen.kt` uses a single unified portrait-grid layout. `GameLayoutMode` has been removed entirely.

**Layout:**
- **`RosterStrip`** — sticky `LazyRow` below the top bar; 36dp portrait circles for every character across all players. Tap to scroll the grid to that character and open their card modal. Players are separated by thin dividers.
- **`LazyVerticalGrid`** — adaptive column count based on total characters: ≤6→3 cols/80dp portraits, ≤10→4 cols/66dp, 11+→5 cols/54dp. Section headers (`GamePlayerSectionHeader`) span the full row between player groups (only shown for >1 player).
- **`CharacterPortraitCell`** — grid cell: portrait circle + stat line (`⚔ +melee  range"  ✦ +arcane  💨 +evade`) + character name. Stat values ≥ 0 display with a `+` prefix. The `💨` glyph is used for Evade.

**Tracking badges (FULL_TRACKING only):**
- Bottom-left green circle = current HP. Tap → HP − 1.
- Bottom-right blue circle = current Energy. Tap → Energy − 1.
- Top-centre gold triangle = Moonstones held. Tap → Moonstones + 1.
- Death (HP = 0): portrait fades to 40% alpha, skull ☠ overlay, badges hidden, moonstones auto-zeroed.
- Rapid taps accumulate into a single toast chip (e.g. "−3 HP") that auto-dismisses 2 s after the last tap.

**Two tracking modes** (`GameTrackingMode`, persisted in SharedPreferences):
- `LOW_DETAIL` — grid shows portraits + stats only; energy/moonstones/abilities tracked physically.
- `FULL_TRACKING` — HP/Energy/Moonstone badges on portrait, ability-used markers in card modal, collapsible resource pool bar in the bottom bar.

**Card modal (`GameCharacterCardModal`):**
- Tap any portrait (grid or roster strip) → full-screen overlay with 3D `graphicsLayer` flip animation.
- Front face uses `CharacterFront(showHealthTracker = false, abilityUsedStates = ..., onAbilityUsedChange = ...)` — compendium-style layout, no health pips, with ability tracking in FULL_TRACKING mode.
- FULL_TRACKING adds a compact energy counter row (−/value/+) between the name header and the card content.
- Back face uses `CharacterBack`. The signature-move link inside `CharacterFront` triggers the flip.
- No expand/collapse — all abilities always visible.

**`CharacterFront` (CommonComponents.kt):** now accepts optional parameters for game-mode use:
- `showHealthTracker: Boolean = true` — pass `false` in game modal to hide health pips.
- `abilityUsedStates: Map<String, Boolean>? = null` — when non-null, shows used/unused state on abilities.
- `onAbilityUsedChange: ((String, Boolean) -> Unit)? = null` — callback for toggling ability state.

**`FrontSide`** (ActiveGameScreen.kt) is retained for internal use but is no longer shown in any layout. It may be removed in a future cleanup.

**Settings:** Game View tracking mode toggle remains in `SettingsScreen.kt`. The "Game Layout" section has been removed.
