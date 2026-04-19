package io.github.garemat.lunachron

import kotlinx.serialization.Serializable

/** Legacy enum kept only as a bridge while composables are migrated off AppTheme checks.
 *  Use [CharacterState.activeThemeId] and [AppThemeProperties] flags going forward. */
enum class AppTheme { DEFAULT, MOONSTONE }

@Serializable
enum class LayoutDensity {
    COMPACT, COZY, SPACIOUS
}

@Serializable
enum class GameTrackingMode { LOW_DETAIL, FULL_TRACKING }


@Serializable
enum class ImageDownloadPreference { PROMPT, ENABLED, DISABLED }

enum class InstallerSource { FDROID, DIRECT, PLAY_STORE }

data class AppRelease(
    val tagName: String,
    val htmlUrl: String
)

@Serializable
data class GitHubRelease(
    val tagName: String,
    val assets: List<GitHubAsset> = emptyList(),
    val schemaIncompatible: Boolean = false
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browserDownloadUrl: String
)

@Serializable
data class SummonEntry(
    val characterId: Int,
    val summonedByCharacterId: Int? = null
)

@Serializable
data class NewsItem(
    val title: String,
    val url: String,
    val date: String,
    val imageUrl: String? = null,
    val summary: String? = null
)

@Serializable
enum class TroupeSizeSetting {
    V6_10, V5_8
}

@Serializable
data class TournamentSettings(
    val tournamentName: String = "",
    val troupeSize: TroupeSizeSetting = TroupeSizeSetting.V6_10,
    val roundTimerMinutes: Int = 90,
    val hostParticipating: Boolean = true,
    val sessionId: String = "",
    val passcode: String = ""
)

@Serializable
data class TournamentPlayer(
    val name: String,
    val deviceId: String,
    val troupe: Troupe? = null,
    val isReady: Boolean = false,
    val score: Int = 0
)

@Serializable
data class TournamentPairing(
    val player1Id: String,
    val player2Id: String,
    val player1CharacterIds: List<Int> = emptyList(),
    val player2CharacterIds: List<Int> = emptyList(),
    val player1Confirmed: Boolean = false,
    val player2Confirmed: Boolean = false,
    val player1InitiativeSelection: String? = null,
    val player2InitiativeSelection: String? = null,
    val initiativePlayerId: String? = null, // Set only when both selections match
    val player1DeploymentReady: Boolean = false,
    val player2DeploymentReady: Boolean = false,
    val winnerId: String? = null, // "draw" for a tie
    val isScored: Boolean = false
)

@Serializable
enum class TournamentRoundStatus {
    SELECTION, DEPLOYMENT, ACTIVE_GAME
}

@Serializable
data class TournamentRound(
    val roundNumber: Int,
    val pairings: List<TournamentPairing> = emptyList(),
    val status: TournamentRoundStatus = TournamentRoundStatus.SELECTION
)

// ── Online campaign models ────────────────────────────────────────────────────

@Serializable
data class OnlineCampaignSettings(
    val attacksEnabled: Boolean = true,
    val startingCharacters: Int = 4,
    /** A new character is earned every this many rounds (0 = never). */
    val characterGrowthEvery: Int = 2,
    /** A new upgrade card is earned every this many rounds (0 = never). */
    val upgradeGrowthEvery: Int = 1
)

@Serializable
data class OnlineCampaignSummary(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,                        // "OPEN", "LOCKED", "DISBANDED"
    val joinCode: String? = null,              // only for host of an OPEN campaign
    val isHost: Boolean,
    val memberCount: Int,
    val membershipStatus: String = "APPROVED", // "APPROVED" or "PENDING"
    val settings: OnlineCampaignSettings? = null
)

@Serializable
data class OnlineCampaignMember(
    val id: String,                         // campaign_members.id — used for approve/reject
    val deviceId: String,
    val username: String,
    val role: String,                       // "HOST" or "MEMBER"
    val status: String,                     // "PENDING", "APPROVED", "REJECTED"
    val troupeData: String? = null,
    val troupeUpdatedAt: String? = null,
    val isReady: Boolean = false,
    val isLocal: Boolean = false,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val victoryPoints: Int = 0,
    val matchPoints: Int = 0,
    val powerPoints: Int = 0
)

@Serializable
data class OnlineCampaignDetail(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val joinCode: String? = null,
    val currentUserRole: String = "MEMBER",  // "HOST" or "MEMBER"
    val settings: OnlineCampaignSettings? = null,
    val members: List<OnlineCampaignMember> = emptyList(),
    // round1 → { game1 → [deviceId1, deviceId2], ... }
    val schedule: Map<String, Map<String, List<String>>>? = null,
    val currentRound: Int = 1,
    val phase: String = "GAME",
    val machinations: List<OnlineMachinationEntry> = emptyList(),
    val matchResults: List<OnlineMatchResult> = emptyList()
)

/** A single SUPPORT/SABOTAGE machination choice within a submitted entry. */
@Serializable
data class OnlineMachinationChoice(
    val targetDeviceId: String,
    val type: String   // "SUPPORT" or "SABOTAGE"
)

/** An optional attack declaration submitted alongside machinations. */
@Serializable
data class OnlineMachinationAttack(
    val targetDeviceId: String,
    val targetCharId: Int,
    val type: String   // "ASSAULT" or "ABDUCTION"
)

/** A single member's machination submission for a round. */
@Serializable
data class OnlineMachinationEntry(
    val deviceId: String,
    val username: String,
    val choices: List<OnlineMachinationChoice> = emptyList(),
    val attack: OnlineMachinationAttack? = null
)

/** Result data embedded within a match result. */
@Serializable
data class OnlineMatchResultData(
    val playerStats: List<OnlinePlayerStat> = emptyList(),
    val winnerId: String? = null
)

/** A match result record returned inside campaign detail. */
@Serializable
data class OnlineMatchResult(
    val id: String,
    val roundNumber: Int,
    val gameNumber: Int,
    val player1Id: String,
    val player2Id: String,
    val submittedBy: String,
    val resultData: OnlineMatchResultData? = null,
    val winnerId: String? = null,
    val verifyStatus: String   // "PENDING", "VERIFIED", "DISPUTED"
)

/** One player's stats in an online match result. */
@Serializable
data class OnlinePlayerStat(
    val deviceId: String,
    val playerName: String,
    val moonstones: Int = 0,
    /** VP adjustment from campaign cards (can be negative). */
    val campaignCardVp: Int = 0,
    /** Share codes of campaign cards used this game. */
    val campaignCardCodes: List<String> = emptyList()
)

/** Returned to the UI after a successful campaign creation. */
data class CreatedCampaignResult(val id: String, val joinCode: String)

// ─────────────────────────────────────────────────────────────────────────────

data class CharacterState(
    val characters: List<Character> = emptyList(),
    val troupes: List<Troupe> = emptyList(),
    val upgradeCards: List<UpgradeCard> = emptyList(),
    val campaignCards: List<CampaignCard> = emptyList(),
    val campaigns: List<Campaign> = emptyList(),
    
    val name: String = "",
    val deviceId: String = "",
    val activeThemeId: String = "moonstone",
    val layoutDensity: LayoutDensity = LayoutDensity.COZY,
    val useLocalModeByDefault: Boolean = false,
    val useSinglePlayerMode: Boolean = false,
    val isAddingCharacter: Boolean = false,
    val isAddingTroupe: Boolean = false,
    val sortType: SortType = SortType.NAME,
    val errorMessage: String? = null,
    
    // News Feed
    val newsItems: List<NewsItem> = emptyList(),
    val isFetchingNews: Boolean = false,
    val autoFetchNews: Boolean = false,

    // Tutorial State
    val hasSeenGlobalTutorial: Boolean = false,
    
    // Game Session State (Nearby)
    val gameSession: GameSession? = null,

    // Tournament State
    val tournamentSettings: TournamentSettings? = null,
    val isTournamentHost: Boolean = false,
    val tournamentPlayers: List<TournamentPlayer> = emptyList(),
    val isLeaving: Boolean = false,
    val currentTournamentRound: TournamentRound? = null,
    val tournamentHistory: List<TournamentRound> = emptyList(),

    val gameTrackingMode: GameTrackingMode = GameTrackingMode.LOW_DETAIL,

    // Active Game Play State
    val currentTurn: Int = 1,
    // Key: "playerIndex_characterIndex"
    val characterPlayStates: Map<String, CharacterPlayState> = emptyMap(),
    val activeTroupes: List<Troupe> = emptyList(),
    // History for rewind: List of (TurnNumber, characterPlayStates)
    val turnHistory: List<Map<String, CharacterPlayState>> = emptyList(),
    // playerIndex -> list of active summons (ordered)
    val activeSummons: Map<Int, List<SummonEntry>> = emptyMap(),
    // playerIndex -> (resourceName -> currentCount in pool)
    val poolResourceCounts: Map<Int, Map<String, Int>> = emptyMap(),

    // Ready states for multi-player actions
    val readyForNextTurn: Set<String> = emptySet(),
    val readyForRewind: Set<String> = emptySet(),

    // Game End State
    val winnerName: String? = null,
    val isTie: Boolean = false,

    // Campaign Management State
    val activeCampaign: Campaign? = null,

    // Data / image update state
    val autoCheckDataUpdates: Boolean = false,
    val installedDataVersion: String = "",
    val pendingDataUpdate: GitHubRelease? = null,
    val isInstallingDataUpdate: Boolean = false,
    val imageDownloadPreference: ImageDownloadPreference = ImageDownloadPreference.PROMPT,
    val pendingImageUpdate: String? = null,
    val isDownloadingImages: Boolean = false,
    val imageDownloadedBytes: Long = 0L,
    val imageTotalBytes: Long = -1L,
    val imageDownloadSpeedBps: Long = 0L,

    // App update state (opt-in, off by default — F-Droid manages updates for F-Droid installs)
    val autoCheckAppUpdates: Boolean = false,
    val pendingAppUpdate: AppRelease? = null,
    val installerSource: InstallerSource = InstallerSource.DIRECT,
    val isDownloadingApk: Boolean = false,
    val apkDownloadProgress: Float = 0f,
    val pendingApkInstall: String? = null,

    // LunaChron API registration
    val isRegistered: Boolean = false,
    val isRegisteringDevice: Boolean = false,
    val registrationError: String? = null,
    /** Backend's internal device UUID (devices.id) — used to identify this device in schedules/results. */
    val backendDeviceId: String = "",

    // Online campaign state
    val onlineCampaigns: List<OnlineCampaignSummary> = emptyList(),
    val isLoadingOnlineCampaigns: Boolean = false,
    val isCreatingCampaign: Boolean = false,
    val isJoiningCampaign: Boolean = false,
    val onlineCampaignError: String? = null,
    val createdCampaignResult: CreatedCampaignResult? = null,
    val pendingJoinCampaignId: String? = null,

    // Online campaign detail
    val selectedOnlineCampaign: OnlineCampaignDetail? = null,
    val isLoadingCampaignDetail: Boolean = false,
    val isApprovingMember: Boolean = false,

    // Online campaign schedule flow
    val isLockingCampaign: Boolean = false,
    val isPublishingSchedule: Boolean = false,
    val pendingOnlineSchedule: List<CampaignRound>? = null,
    val onlineScheduleRoundCount: Int = 0,

    // Online campaign troupe + ready
    val isUploadingTroupe: Boolean = false,
    val isSettingReady: Boolean = false,
    val isAddingLocalMember: Boolean = false,

    // Online campaign rankings + round management
    val isUpdatingRankings: Boolean = false,
    val isAdvancingRound: Boolean = false,

    // Online match result submission
    val isSubmittingMatchResult: Boolean = false,
    val matchResultSubmitted: Boolean = false,
    val isVerifyingMatchResult: Boolean = false,

    // Online machinations phase
    val isSubmittingMachination: Boolean = false,

    // Online campaign deletion
    val isDeletingCampaign: Boolean = false,
    val onlineCampaignDeleted: Boolean = false
)

@Serializable
data class CharacterPlayState(
    val currentHealth: Int,
    val currentEnergy: Int = 0,
    val moonstones: Int = 0,
    val usedAbilities: Map<String, Boolean> = emptyMap(),
    val isFlipped: Boolean = false,
    val isExpanded: Boolean = false,
    val heldPoolResources: Map<String, Int> = emptyMap()
)

@Serializable
data class GameSession(
    val players: List<GamePlayer> = emptyList(),
    val isHost: Boolean = false,
    val sessionId: String = ""
)

@Serializable
data class GamePlayer(
    val name: String,
    val troupe: Troupe? = null,
    val isReady: Boolean = false,
    val deviceId: String = ""
)
