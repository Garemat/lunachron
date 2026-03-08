package com.garemat.moonstone_companion

import kotlinx.serialization.Serializable

enum class AppTheme {
    DEFAULT, MOONSTONE
}

@Serializable
enum class LayoutDensity {
    COMPACT, COZY, SPACIOUS
}

@Serializable
enum class GameTrackingMode { LOW_DETAIL, FULL_TRACKING }

@Serializable
enum class GameLayoutMode { COMPACT_GRID, DETAILED_LIST }

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

data class CharacterState(
    val characters: List<Character> = emptyList(),
    val troupes: List<Troupe> = emptyList(),
    val upgradeCards: List<UpgradeCard> = emptyList(),
    val campaignCards: List<CampaignCard> = emptyList(),
    val campaigns: List<Campaign> = emptyList(),
    
    val name: String = "",
    val deviceId: String = "",
    val theme: AppTheme = AppTheme.MOONSTONE,
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
    val gameLayoutMode: GameLayoutMode = GameLayoutMode.COMPACT_GRID,

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
    val activeCampaign: Campaign? = null
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
