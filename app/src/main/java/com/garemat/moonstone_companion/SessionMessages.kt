package com.garemat.moonstone_companion

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class SessionMessage {
    @Serializable
    data class JoinRequest(
        val playerName: String, 
        val deviceId: String,
        val tournamentPasscode: String? = null
    ) : SessionMessage()
    
    @Serializable
    data class Welcome(val deviceId: String) : SessionMessage()
    
    @Serializable
    data class TroupeSelected(
        val deviceId: String, 
        val troupeName: String, 
        val faction: Faction, 
        val characterIds: List<Int>
    ) : SessionMessage()
    
    @Serializable
    data class SessionSync(val players: List<GamePlayer>, val sessionId: String) : SessionMessage()
    
    @Serializable
    data class TournamentSync(
        val settings: TournamentSettings,
        val players: List<TournamentPlayer>,
        val currentRound: TournamentRound? = null,
        val history: List<TournamentRound> = emptyList()
    ) : SessionMessage()

    @Serializable
    data class PlayerInfoUpdate(
        val deviceId: String,
        val newName: String
    ) : SessionMessage()

    @Serializable
    data class LeaveMessage(
        val deviceId: String
    ) : SessionMessage()

    @Serializable
    data object StartGame : SessionMessage()

    @Serializable
    data class GameplayUpdate(
        val playerIndex: Int,
        val charIndex: Int,
        val health: Int? = null,
        val energy: Int? = null,
        val moonstones: Int? = null,
        val abilityName: String? = null,
        val abilityUsed: Boolean? = null
    ) : SessionMessage()

    @Serializable
    data class TurnUpdate(
        val turn: Int, 
        val characterPlayStates: Map<String, CharacterPlayState>
    ) : SessionMessage()

    @Serializable
    data class ReadyForAction(val action: GameAction, val deviceId: String, val isReady: Boolean) : SessionMessage()

    @Serializable
    data class TournamentPlayerReady(val deviceId: String, val isReady: Boolean) : SessionMessage()

    @Serializable
    data class TournamentDisbanded(val message: String) : SessionMessage()

    @Serializable
    data class TournamentPairingUpdate(val pairing: TournamentPairing) : SessionMessage()
}

@Serializable
enum class GameAction {
    NEXT_TURN, REWIND
}

object MessageParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(message: SessionMessage): String = json.encodeToString(message)
    fun decode(jsonString: String): SessionMessage = json.decodeFromString(jsonString)
}
