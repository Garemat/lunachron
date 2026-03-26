package io.github.garemat.lunachron

sealed interface CharacterEvent {
    data object SaveCharacter : CharacterEvent
    data class DeleteCharacter(val character: Character) : CharacterEvent
    
    data object SaveTroupe : CharacterEvent
    data class SaveTroupeWithMetadata(
        val victoryPoints: Int,
        val equippedUpgrades: Map<Int, List<Int>>,
        val campaignCards: List<TroupeCampaignCard>
    ) : CharacterEvent
    
    data class DeleteTroupe(val troupe: Troupe) : CharacterEvent
    data class EditTroupe(val troupe: Troupe) : CharacterEvent

    data class SortCharacters(val sortType: SortType) : CharacterEvent
    
    // UI visibility events
    data object ShowCharacterDialog : CharacterEvent
    data object HideCharacterDialog : CharacterEvent
    data object ShowTroupeDialog : CharacterEvent
    data object HideTroupeDialog : CharacterEvent

    data object DismissError : CharacterEvent

    data class UpdateUserName(val name: String) : CharacterEvent
    data class SetActiveTheme(val themeId: String) : CharacterEvent
    data class ChangeLayoutDensity(val density: LayoutDensity) : CharacterEvent
    data class SetLocalModeDefault(val useLocal: Boolean) : CharacterEvent
    data class SetSinglePlayerMode(val enabled: Boolean) : CharacterEvent
    
    // Tutorial
    data class SetHasSeenTutorial(val tutorialKey: String, val seen: Boolean) : CharacterEvent

    // News
    data object RefreshNews : CharacterEvent
    data class SetAutoFetchNews(val enabled: Boolean) : CharacterEvent

    // Gameplay Events
    data class UpdateCharacterHealth(val playerIndex: Int, val charIndex: Int, val health: Int) : CharacterEvent
    data class UpdateCharacterEnergy(val playerIndex: Int, val charIndex: Int, val energy: Int) : CharacterEvent
    data class ToggleAbilityUsed(val playerIndex: Int, val charIndex: Int, val abilityName: String, val used: Boolean) : CharacterEvent
    data class ToggleCharacterFlipped(val playerIndex: Int, val charIndex: Int, val flipped: Boolean) : CharacterEvent
    data class ToggleCharacterExpanded(val playerIndex: Int, val charIndex: Int, val expanded: Boolean) : CharacterEvent
    data object ResetGamePlayState : CharacterEvent
    
    // Turn and Moonstone Events
    data object NextTurn : CharacterEvent
    data object RewindTurn : CharacterEvent
    data class UpdateCharacterMoonstones(val playerIndex: Int, val charIndex: Int, val stones: Int) : CharacterEvent

    // Game Lifecycle
    data object AbandonGame : CharacterEvent
    data object EndGame : CharacterEvent

    // Game View / Tracking
    data class ChangeGameTrackingMode(val mode: GameTrackingMode) : CharacterEvent
    data class ChangeGameLayoutMode(val mode: GameLayoutMode) : CharacterEvent
    data class AddSummonedCharacter(val playerIndex: Int, val characterId: Int, val summonedByCharacterId: Int?) : CharacterEvent
    data class RemoveSummonedCharacter(val playerIndex: Int, val characterId: Int) : CharacterEvent
    data class UpdatePoolResource(val playerIndex: Int, val resourceName: String, val count: Int) : CharacterEvent
    data class UpdateCharacterPoolResource(val playerIndex: Int, val charIndex: Int, val resourceName: String, val count: Int) : CharacterEvent
    data class TransformCharacter(val playerIndex: Int, val charIndex: Int, val targetCharacterId: Int) : CharacterEvent

    // Data update events
    data class SetAutoCheckDataUpdates(val enabled: Boolean) : CharacterEvent
    data object CheckForDataUpdate : CharacterEvent
    data class SkipDataVersion(val tag: String) : CharacterEvent
    data object DismissDataUpdate : CharacterEvent
    data class InstallDataUpdate(val release: GitHubRelease) : CharacterEvent

    // App update events (opt-in; action opens browser, never installs APK in-app)
    data class SetAutoCheckAppUpdates(val enabled: Boolean) : CharacterEvent
    data object CheckForAppUpdate : CharacterEvent
    data object DismissAppUpdate : CharacterEvent

    // Image download events
    data class SetImageDownloadPreference(val pref: ImageDownloadPreference) : CharacterEvent
    data object DownloadCharacterImages : CharacterEvent
    data class SkipImageVersion(val tag: String) : CharacterEvent
    data object DismissImageUpdate : CharacterEvent

    // Tournament Events
    data class CreateTournament(val tournamentName: String, val troupeSize: TroupeSizeSetting, val timer: Int, val hostParticipating: Boolean, val passcode: String, val hostMode: HostMode = HostMode.WIFI_NSD) : CharacterEvent
    data class JoinTournament(val sessionId: String) : CharacterEvent
}
