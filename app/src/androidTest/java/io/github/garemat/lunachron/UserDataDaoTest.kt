package io.github.garemat.lunachron

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [UserDataDAO] using an in-memory [UserDatabase].
 *
 * Verifies that all troupe fields — including the serialised collections
 * (characterIds, equippedUpgrades, campaignCards) that go through Room
 * TypeConverters — survive a write/read round-trip without corruption.
 */
@RunWith(AndroidJUnit4::class)
class UserDataDaoTest {

    private lateinit var db: UserDatabase
    private lateinit var dao: UserDataDAO

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UserDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.dao
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Basic save/read ───────────────────────────────────────────────────────

    @Test
    fun saveTroupe_readBack_allScalarFieldsMatch() = runTest {
        val troupe = Troupe(
            troupeName = "Iron Vanguard",
            faction = Faction.COMMONWEALTH,
            characterIds = emptyList(),
            shareCode = "AABCD",
            isTournamentList = true,
            isCampaignTroupe = false,
            victoryPoints = 3
        )
        val id = dao.upsertTroupe(troupe)
        val saved = dao.getTroupeById(id.toInt())

        assertNotNull(saved)
        assertEquals("Iron Vanguard", saved!!.troupeName)
        assertEquals(Faction.COMMONWEALTH, saved.faction)
        assertEquals(true, saved.isTournamentList)
        assertEquals(false, saved.isCampaignTroupe)
        assertEquals(3, saved.victoryPoints)
        assertEquals("AABCD", saved.shareCode)
    }

    // ── Character IDs ─────────────────────────────────────────────────────────

    @Test
    fun saveTroupe_characterIds_surviveSerialization() = runTest {
        val ids = listOf(1, 5, 12, 99)
        val id = dao.upsertTroupe(baseTroupe().copy(characterIds = ids))
        assertEquals(ids, dao.getTroupeById(id.toInt())!!.characterIds)
    }

    @Test
    fun saveTroupe_emptyCharacterIds_savedCorrectly() = runTest {
        val id = dao.upsertTroupe(baseTroupe().copy(characterIds = emptyList()))
        assertEquals(emptyList<Int>(), dao.getTroupeById(id.toInt())!!.characterIds)
    }

    // ── Equipped upgrades ─────────────────────────────────────────────────────

    @Test
    fun saveTroupe_equippedUpgrades_surviveSerialization() = runTest {
        val upgrades = mapOf(1 to listOf(10, 11), 2 to listOf(20), 5 to emptyList())
        val id = dao.upsertTroupe(baseTroupe().copy(equippedUpgrades = upgrades))
        assertEquals(upgrades, dao.getTroupeById(id.toInt())!!.equippedUpgrades)
    }

    @Test
    fun saveTroupe_emptyEquippedUpgrades_savedCorrectly() = runTest {
        val id = dao.upsertTroupe(baseTroupe().copy(equippedUpgrades = emptyMap()))
        assertEquals(emptyMap<Int, List<Int>>(), dao.getTroupeById(id.toInt())!!.equippedUpgrades)
    }

    // ── Campaign cards ────────────────────────────────────────────────────────

    @Test
    fun saveTroupe_campaignCards_surviveSerialization() = runTest {
        val cards = listOf(
            TroupeCampaignCard(cardId = 5, usedInGame = null),   // not yet used
            TroupeCampaignCard(cardId = 7, usedInGame = 2)        // used in game 2
        )
        val id = dao.upsertTroupe(baseTroupe().copy(campaignCards = cards))
        assertEquals(cards, dao.getTroupeById(id.toInt())!!.campaignCards)
    }

    @Test
    fun saveTroupe_campaignCard_usedInGame_null_preservedAsNull() = runTest {
        val cards = listOf(TroupeCampaignCard(cardId = 3, usedInGame = null))
        val id = dao.upsertTroupe(baseTroupe().copy(campaignCards = cards))
        assertNull(dao.getTroupeById(id.toInt())!!.campaignCards.first().usedInGame)
    }

    @Test
    fun saveTroupe_emptyCampaignCards_savedCorrectly() = runTest {
        val id = dao.upsertTroupe(baseTroupe().copy(campaignCards = emptyList()))
        assertEquals(emptyList<TroupeCampaignCard>(), dao.getTroupeById(id.toInt())!!.campaignCards)
    }

    // ── Full troupe (all fields together) ─────────────────────────────────────

    @Test
    fun saveTroupe_fullCampaignTroupe_allFieldsSurvive() = runTest {
        val troupe = Troupe(
            troupeName = "Shadow Council",
            faction = Faction.SHADES,
            characterIds = listOf(3, 7, 11),
            shareCode = "DAABC",
            isTournamentList = false,
            isCampaignTroupe = true,
            victoryPoints = 7,
            equippedUpgrades = mapOf(3 to listOf(101, 102), 7 to listOf(200)),
            campaignCards = listOf(
                TroupeCampaignCard(cardId = 1, usedInGame = null),
                TroupeCampaignCard(cardId = 4, usedInGame = 1)
            )
        )
        val id = dao.upsertTroupe(troupe)
        val saved = dao.getTroupeById(id.toInt())!!

        assertEquals(troupe.troupeName, saved.troupeName)
        assertEquals(troupe.faction, saved.faction)
        assertEquals(troupe.characterIds, saved.characterIds)
        assertEquals(troupe.isCampaignTroupe, saved.isCampaignTroupe)
        assertEquals(troupe.victoryPoints, saved.victoryPoints)
        assertEquals(troupe.equippedUpgrades, saved.equippedUpgrades)
        assertEquals(troupe.campaignCards, saved.campaignCards)
    }

    // ── Upsert (update existing) ───────────────────────────────────────────────

    @Test
    fun upsertTroupe_updatesExistingRecord() = runTest {
        val id = dao.upsertTroupe(baseTroupe().copy(troupeName = "Old Name")).toInt()
        dao.upsertTroupe(baseTroupe().copy(id = id, troupeName = "New Name", characterIds = listOf(1, 2)))
        val updated = dao.getTroupeById(id)!!
        assertEquals("New Name", updated.troupeName)
        assertEquals(listOf(1, 2), updated.characterIds)
    }

    // ── getTroupes flow ───────────────────────────────────────────────────────

    @Test
    fun getTroupes_returnsAllSavedTroupes() = runTest {
        dao.upsertTroupe(baseTroupe().copy(troupeName = "Alpha"))
        dao.upsertTroupe(baseTroupe().copy(troupeName = "Beta"))
        val all = dao.getTroupes().first()
        assertEquals(2, all.size)
    }

    @Test
    fun deleteTroupe_removesFromDb() = runTest {
        val id = dao.upsertTroupe(baseTroupe()).toInt()
        val saved = dao.getTroupeById(id)!!
        dao.deleteTroupe(saved)
        assertNull(dao.getTroupeById(id))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun baseTroupe() = Troupe(
        troupeName = "Test Troupe",
        faction = Faction.DOMINION,
        characterIds = emptyList(),
        shareCode = ""
    )
}
