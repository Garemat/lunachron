package com.garemat.moonstone_companion

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Room-backed implementation of [CharacterRepository].
 * All asset parsing and DAO access is confined here so the ViewModel
 * has no direct dependency on Room or [CharacterData].
 *
 * When migrating to an API, implement [CharacterRepository] with a
 * Ktor/Retrofit-based class and swap it in at the injection site in MainActivity.
 */
class LocalCharacterRepository(
    private val dao: CharacterDAO
) : CharacterRepository {

    // --- Read streams ---

    override fun getCharacters(): Flow<List<Character>> =
        dao.getCharactersOrderedByName()

    override fun getCharactersByIds(ids: List<Int>): Flow<List<Character>> =
        dao.getCharactersByIds(ids)

    override fun getUpgradeCards(): Flow<List<UpgradeCard>> =
        dao.getUpgradeCards()

    override fun getCampaignCards(): Flow<List<CampaignCard>> =
        dao.getCampaignCards()

    override fun getCampaigns(): Flow<List<Campaign>> =
        dao.getCampaigns()

    override fun getTroupes(): Flow<List<Troupe>> =
        dao.getTroupes()

    override fun getGameResults(): Flow<List<GameResult>> =
        dao.getGameResults()

    // --- Troupe writes ---

    override suspend fun upsertTroupe(troupe: Troupe): Long =
        dao.upsertTroupe(troupe)

    override suspend fun deleteTroupe(troupe: Troupe) =
        dao.deleteTroupe(troupe)

    override suspend fun getTroupeById(id: Int): Troupe? =
        dao.getTroupeById(id)

    override suspend fun getTroupeByShareCode(code: String): Troupe? =
        dao.getTroupeByShareCode(code)

    // --- Campaign writes ---

    override suspend fun upsertCampaign(campaign: Campaign): Long =
        dao.upsertCampaign(campaign)

    override suspend fun deleteCampaign(campaign: Campaign) =
        dao.deleteCampaign(campaign)

    // --- Game result writes ---

    override suspend fun upsertGameResult(result: GameResult) =
        dao.upsertGameResult(result)

    // --- Asset seeding ---

    /**
     * Upserts all JSON asset data into Room. Called once on DB open.
     * When migrating to the API, this method is replaced by a network fetch.
     * JSON field names that change in the API are handled here — no changes
     * needed in the ViewModel or domain models.
     */
    suspend fun seedFromAssets(context: Context) {
        dao.upsertCharacters(CharacterData.getCharactersFromAssets(context))
        dao.upsertUpgradeCards(CharacterData.getUpgradesFromAssets(context))
        dao.upsertCampaignCards(CharacterData.getCampaignCardsFromAssets(context))
    }

    /**
     * Upserts game data from downloaded JSON files in [dir].
     * Called after a data update is downloaded to internal storage.
     */
    suspend fun seedFromFiles(dir: java.io.File) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val chars = dir.resolve("characters.json").takeIf { it.exists() }?.readText()
            ?.let { json.decodeFromString<List<Character>>(it) } ?: emptyList()
        val upgrades = dir.resolve("upgrades.json").takeIf { it.exists() }?.readText()
            ?.let { json.decodeFromString<List<UpgradeCard>>(it) } ?: emptyList()
        val campaign = dir.resolve("campaign.json").takeIf { it.exists() }?.readText()
            ?.let { json.decodeFromString<List<CampaignCard>>(it) } ?: emptyList()
        dao.upsertCharacters(chars)
        dao.upsertUpgradeCards(upgrades)
        dao.upsertCampaignCards(campaign)
    }
}
