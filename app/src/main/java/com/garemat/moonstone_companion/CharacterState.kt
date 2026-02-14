package com.garemat.moonstone_companion

import kotlinx.serialization.Serializable

enum class AppTheme {
    DEFAULT, MOONSTONE
}

@Serializable
data class NewsItem(
    val title: String,
    val url: String,
    val date: String,
    val imageUrl: String? = null,
    val summary: String? = null
)

data class CharacterState(
    val characters: List<Character> = emptyList(),
    val troupes: List<Troupe> = emptyList(),
    val name: String = "",
    val deviceId: String = "",
    val theme: AppTheme = AppTheme.MOONSTONE,
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

    // Active Game Play State
    val currentTurn: Int = 1,
    // Key: "playerIndex_characterIndex"
    val characterPlayStates: Map<String, CharacterPlayState> = emptyMap(),
    val activeTroupes: List<Troupe> = emptyList(),
    // History for rewind: List of (TurnNumber, characterPlayStates)
    val turnHistory: List<Map<String, CharacterPlayState>> = emptyList(),

    // Ready states for multi-player actions
    val readyForNextTurn: Set<String> = emptySet(),
    val readyForRewind: Set<String> = emptySet(),

    // Game End State
    val winnerName: String? = null,
    val isTie: Boolean = false
)

@Serializable
data class CharacterPlayState(
    val currentHealth: Int,
    val currentEnergy: Int = 0,
    val moonstones: Int = 0,
    val usedAbilities: Map<String, Boolean> = emptyMap(),
    val isFlipped: Boolean = false,
    val isExpanded: Boolean = false
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
