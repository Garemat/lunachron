package io.github.garemat.lunachron

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for [Converters].
 *
 * Room TypeConverters silently corrupt stored data if serialization regresses —
 * there is no crash, just garbled troupes/campaigns on next read. These tests
 * catch that class of regression without requiring a device.
 */
class ConvertersTest {

    private val c = Converters()

    // ── Faction ───────────────────────────────────────────────────────────────

    @Test
    fun faction_roundTrip() {
        Faction.entries.forEach { faction ->
            assertEquals(faction, c.toFaction(c.fromFaction(faction)))
        }
    }

    @Test
    fun factionList_roundTrip() {
        val list = listOf(Faction.COMMONWEALTH, Faction.SHADES, Faction.DOMINION)
        assertEquals(list, c.toFactionList(c.fromFactionList(list)))
    }

    @Test
    fun factionList_empty() {
        val list = emptyList<Faction>()
        assertEquals(list, c.toFactionList(c.fromFactionList(list)))
    }

    // ── String list ───────────────────────────────────────────────────────────

    @Test
    fun stringList_roundTrip() {
        val list = listOf("piercing", "slicing", "impact")
        assertEquals(list, c.toStringList(c.fromStringList(list)))
    }

    @Test
    fun stringList_empty() {
        assertEquals(emptyList<String>(), c.toStringList(c.fromStringList(emptyList())))
    }

    // ── Int list ──────────────────────────────────────────────────────────────

    @Test
    fun intList_roundTrip() {
        val list = listOf(1, 42, 99)
        assertEquals(list, c.toIntList(c.fromIntList(list)))
    }

    // ── Ability list ──────────────────────────────────────────────────────────

    @Test
    fun abilityList_roundTrip() {
        val list = listOf(
            Ability(
                name = "Shield Bash",
                description = "Deal 1 damage.",
                abilityType = "Active",
                energyCost = 2,
                oncePerTurn = true
            ),
            Ability(
                name = "Resilience",
                abilityType = "Passive"
            )
        )
        assertEquals(list, c.toAbilityList(c.fromAbilityList(list)))
    }

    @Test
    fun abilityList_empty() {
        assertEquals(emptyList<Ability>(), c.toAbilityList(c.fromAbilityList(emptyList())))
    }

    // ── SignatureMove (nullable) ───────────────────────────────────────────────

    @Test
    fun signatureMove_roundTrip() {
        val sig = SignatureMove(
            name = "Steel Tempest",
            upgradeFor = "Seraphina",
            possibleDamageTypes = listOf("slicing"),
            highGuard = SigMoveEntry("2"),
            thrust = SigMoveEntry("1", isFollowUp = true)
        )
        assertEquals(sig, c.toSignatureMove(c.fromSignatureMove(sig)))
    }

    @Test
    fun signatureMove_null_roundTrip() {
        assertNull(c.toSignatureMove(c.fromSignatureMove(null)))
    }

    // ── PassiveAbility list ───────────────────────────────────────────────────

    @Test
    fun passiveAbilityList_roundTrip() {
        val list = listOf(
            PassiveAbility(name = "Tough", description = "+1 health.", oncePerTurn = false),
            PassiveAbility(name = "Rally", description = "Heal nearby.", oncePerGame = true)
        )
        assertEquals(list, c.toPassiveAbilityList(c.fromPassiveAbilityList(list)))
    }

    // ── PlayerStat list ───────────────────────────────────────────────────────

    @Test
    fun playerStatList_roundTrip() {
        val list = listOf(
            PlayerStat(
                playerName = "Alice",
                troupeName = "Iron Vanguard",
                faction = Faction.COMMONWEALTH,
                totalStones = 5,
                characterStats = listOf(
                    CharacterGameStat(characterId = 1, name = "Riven", stones = 3, died = false),
                    CharacterGameStat(characterId = 2, name = "Cassia", stones = 2, died = true)
                )
            )
        )
        assertEquals(list, c.toPlayerStatList(c.fromPlayerStatList(list)))
    }

    // ── EquippedUpgrades (Map<Int, List<Int>>) ────────────────────────────────

    @Test
    fun equippedUpgrades_roundTrip() {
        val map = mapOf(1 to listOf(10, 11), 2 to listOf(20))
        assertEquals(map, c.toEquippedUpgrades(c.fromEquippedUpgrades(map)))
    }

    @Test
    fun equippedUpgrades_empty() {
        val map = emptyMap<Int, List<Int>>()
        assertEquals(map, c.toEquippedUpgrades(c.fromEquippedUpgrades(map)))
    }

    // ── TroupeCampaignCard list ───────────────────────────────────────────────

    @Test
    fun troupeCampaignCardList_roundTrip() {
        val list = listOf(
            TroupeCampaignCard(cardId = 5, usedInGame = null),
            TroupeCampaignCard(cardId = 7, usedInGame = 2)
        )
        assertEquals(list, c.toTroupeCampaignCardList(c.fromTroupeCampaignCardList(list)))
    }

    // ── CampaignPlayer list ───────────────────────────────────────────────────

    @Test
    fun campaignPlayerList_roundTrip() {
        val list = listOf(
            CampaignPlayer(id = "p1", name = "Alice", troupeId = 1, machinationPoints = 3),
            CampaignPlayer(id = "p2", name = "Bob", troupeId = 2)
        )
        assertEquals(list, c.toCampaignPlayerList(c.fromCampaignPlayerList(list)))
    }

    // ── CampaignRound list ────────────────────────────────────────────────────

    @Test
    fun campaignRoundList_roundTrip() {
        val list = listOf(
            CampaignRound(
                roundNumber = 1,
                games = listOf(CampaignGame(playerIds = listOf("p1", "p2"), winnerId = "p1", isPlayed = true)),
                mpDeltas = mapOf("p1" to 2, "p2" to -1)
            )
        )
        assertEquals(list, c.toCampaignRoundList(c.fromCampaignRoundList(list)))
    }

    @Test
    fun campaignRoundList_empty() {
        assertEquals(emptyList<CampaignRound>(), c.toCampaignRoundList(c.fromCampaignRoundList(emptyList())))
    }

    // ── MachinationDraft (nullable Map) ───────────────────────────────────────

    @Test
    fun machinationDraft_roundTrip() {
        val map = mapOf(
            "p1" to PlayerMachinationDraft(
                machType1 = MachinationType.SUPPORT,
                target1 = "p2",
                isAttacking = false
            )
        )
        assertEquals(map, c.toMachinationDraft(c.fromMachinationDraft(map)))
    }

    @Test
    fun machinationDraft_null_roundTrip() {
        assertNull(c.toMachinationDraft(c.fromMachinationDraft(null)))
    }

    // ── PoolResourceDefinition list ───────────────────────────────────────────

    @Test
    fun poolResourceDefinitionList_roundTrip() {
        val list = listOf(
            PoolResourceDefinition(name = "Moonstone", maxInPool = 10, maxPerCharacter = 3),
            PoolResourceDefinition(name = "Energy")
        )
        assertEquals(list, c.toPoolResourceDefinitionList(c.fromPoolResourceDefinitionList(list)))
    }

    @Test
    fun poolResourceDefinitionList_empty() {
        assertEquals(
            emptyList<PoolResourceDefinition>(),
            c.toPoolResourceDefinitionList(c.fromPoolResourceDefinitionList(emptyList()))
        )
    }
}
