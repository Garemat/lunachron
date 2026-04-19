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
    data class ToggleTroupeFavourite(val troupeId: Int) : CharacterEvent

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

    // Sync / auto-update (collapses news, data, and portrait checks into one toggle)
    data class SetAutoSynchronise(val enabled: Boolean) : CharacterEvent

    // Image download events
    data class SetImageDownloadPreference(val pref: ImageDownloadPreference) : CharacterEvent
    data object DownloadCharacterImages : CharacterEvent
    data class SkipImageVersion(val tag: String) : CharacterEvent
    data object DismissImageUpdate : CharacterEvent

    // Tournament Events
    data class CreateTournament(val tournamentName: String, val troupeSize: TroupeSizeSetting, val timer: Int, val hostParticipating: Boolean, val passcode: String, val hostMode: HostMode = HostMode.WIFI_NSD) : CharacterEvent
    data class JoinTournament(val sessionId: String) : CharacterEvent

    // LunaChron API — device registration
    data class RegisterDevice(val username: String) : CharacterEvent
    data object DismissRegistrationError : CharacterEvent

    // LunaChron API — online campaigns
    data object LoadOnlineCampaigns : CharacterEvent
    data class CreateOnlineCampaign(
        val name: String,
        val description: String?,
        val settings: OnlineCampaignSettings
    ) : CharacterEvent
    data class RequestJoinCampaign(val joinCode: String) : CharacterEvent
    data object DismissOnlineCampaignError : CharacterEvent
    data object DismissCreatedCampaignResult : CharacterEvent

    // LunaChron API — campaign detail & member management
    data class LoadOnlineCampaign(val campaignId: String) : CharacterEvent
    data class ApproveMember(val campaignId: String, val memberId: String) : CharacterEvent
    data class RejectMember(val campaignId: String, val memberId: String) : CharacterEvent
    data object ClearSelectedOnlineCampaign : CharacterEvent

    // LunaChron API — campaign lock / schedule
    data class LockOnlineCampaign(val campaignId: String) : CharacterEvent
    data class UnlockOnlineCampaign(val campaignId: String) : CharacterEvent
    data class SetOnlineScheduleRoundCount(val count: Int) : CharacterEvent
    data class GenerateOnlineSchedule(val campaignId: String, val totalRounds: Int) : CharacterEvent
    data class PublishOnlineSchedule(val campaignId: String) : CharacterEvent

    // LunaChron API — troupe sharing + ready
    data class UploadOnlineTroupe(val campaignId: String, val troupeData: String) : CharacterEvent
    data class SetOnlineCampaignReady(val campaignId: String, val isReady: Boolean) : CharacterEvent

    // LunaChron API — local player management (host only)
    data class AddLocalCampaignMember(val campaignId: String, val name: String) : CharacterEvent
    data class UploadLocalMemberTroupe(val campaignId: String, val targetDeviceId: String, val troupeData: String) : CharacterEvent
    data class SetLocalMemberReady(val campaignId: String, val targetDeviceId: String, val isReady: Boolean) : CharacterEvent

    // LunaChron API — rankings + round advancement
    data class UpdateOnlineRankings(val campaignId: String) : CharacterEvent
    data class AdvanceOnlineRound(val campaignId: String) : CharacterEvent

    // LunaChron API — match result recording
    data class SubmitOnlineMatchResult(
        val campaignId: String,
        val roundNumber: Int,
        val gameNumber: Int,
        val playerStats: List<OnlinePlayerStat>,
        val winnerId: String?       // device ID of winner, null for draw
    ) : CharacterEvent
    data object DismissMatchResultSubmitted : CharacterEvent

    // LunaChron API — campaign deletion (host only)
    data class DeleteOnlineCampaign(val campaignId: String) : CharacterEvent
    data object DismissCampaignDeleted : CharacterEvent

    // LunaChron API — match result verification (opponent approves or contests)
    data class VerifyMatchResult(val campaignId: String, val resultId: String, val agree: Boolean) : CharacterEvent

    // LunaChron API — machinations phase
    data class SubmitMachination(
        val campaignId: String,
        val machinations: List<OnlineMachinationChoice>,
        val attack: OnlineMachinationAttack? = null
    ) : CharacterEvent
    data class SubmitLocalMachination(
        val campaignId: String,
        val targetDeviceId: String,
        val machinations: List<OnlineMachinationChoice>,
        val attack: OnlineMachinationAttack? = null
    ) : CharacterEvent
}
