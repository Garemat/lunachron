package io.github.garemat.lunachron

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for [MessageParser].
 *
 * Every message type that travels over the P2P socket is encoded to JSON and
 * decoded on the other end. If encode/decode breaks for any type, the affected
 * multiplayer feature silently fails (wrong state, ignored messages, or crash).
 * These tests catch serialization regressions before they reach production.
 */
class MessageParserTest {

    private fun roundTrip(msg: SessionMessage): SessionMessage =
        MessageParser.decode(MessageParser.encode(msg))

    @Test fun joinRequest_roundTrip() {
        val original = SessionMessage.JoinRequest("Alice", "device-1", "secret")
        assertEquals(original, roundTrip(original))
    }

    @Test fun joinRequest_nullPasscode_roundTrip() {
        val original = SessionMessage.JoinRequest("Bob", "device-2", null)
        assertEquals(original, roundTrip(original))
    }

    @Test fun troupeSelected_roundTrip() {
        val original = SessionMessage.TroupeSelected(
            deviceId = "device-3",
            troupeName = "Iron Wolves",
            faction = Faction.COMMONWEALTH,
            characterIds = listOf(1, 5, 12)
        )
        assertEquals(original, roundTrip(original))
    }

    @Test fun startGame_roundTrip() {
        assertEquals(SessionMessage.StartGame, roundTrip(SessionMessage.StartGame))
    }

    @Test fun gameplayUpdate_allFields_roundTrip() {
        val original = SessionMessage.GameplayUpdate(
            playerIndex = 0,
            charIndex = 1,
            health = 5,
            energy = 2,
            moonstones = 1,
            abilityName = "Slash",
            abilityUsed = true
        )
        assertEquals(original, roundTrip(original))
    }

    @Test fun gameplayUpdate_nullOptionals_roundTrip() {
        val original = SessionMessage.GameplayUpdate(playerIndex = 1, charIndex = 0)
        assertEquals(original, roundTrip(original))
    }

    @Test fun turnUpdate_roundTrip() {
        val state = CharacterPlayState(currentHealth = 10, currentEnergy = 3, moonstones = 2)
        val original = SessionMessage.TurnUpdate(
            turn = 3,
            characterPlayStates = mapOf(
                "0_0" to state,
                "1_1" to state.copy(currentHealth = 6, isFlipped = true)
            )
        )
        assertEquals(original, roundTrip(original))
    }

    @Test fun readyForAction_nextTurn_roundTrip() {
        val original = SessionMessage.ReadyForAction(GameAction.NEXT_TURN, "device-4", true)
        assertEquals(original, roundTrip(original))
    }

    @Test fun readyForAction_rewind_roundTrip() {
        val original = SessionMessage.ReadyForAction(GameAction.REWIND, "device-5", false)
        assertEquals(original, roundTrip(original))
    }

    @Test fun leaveMessage_roundTrip() {
        val original = SessionMessage.LeaveMessage("device-6")
        assertEquals(original, roundTrip(original))
    }

    @Test fun playerInfoUpdate_roundTrip() {
        val original = SessionMessage.PlayerInfoUpdate("device-7", "NewName")
        assertEquals(original, roundTrip(original))
    }

    @Test fun tournamentPlayerReady_roundTrip() {
        val original = SessionMessage.TournamentPlayerReady("device-8", true)
        assertEquals(original, roundTrip(original))
    }

    @Test fun tournamentDisbanded_roundTrip() {
        val original = SessionMessage.TournamentDisbanded("Tournament has been cancelled")
        assertEquals(original, roundTrip(original))
    }

    @Test fun decode_ignoresUnknownKeys() {
        // Encode a known message, inject a spurious field (simulates a future app version
        // sending a message this build doesn't know about).
        val encoded = MessageParser.encode(SessionMessage.TournamentDisbanded("cancelled"))
        val withExtraField = encoded.trimEnd('}') + ""","unknownFutureField":"value"}"""
        val decoded = MessageParser.decode(withExtraField)
        assertTrue(decoded is SessionMessage.TournamentDisbanded)
    }
}
