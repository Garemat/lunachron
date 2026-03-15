package io.github.garemat.lunachron

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Room-backed implementation of [CharacterRepository].
 * Game data (characters, upgrades, campaign cards) is served from [gameDao] backed by [GameDatabase].
 * User data (troupes, campaigns, game results) is served from [userDao] backed by [UserDatabase].
 */
class LocalCharacterRepository(
    private val gameDao: GameDataDAO,
    private val userDao: UserDataDAO
) : CharacterRepository {

    // --- Read streams ---

    override fun getCharacters(): Flow<List<Character>> =
        gameDao.getCharactersOrderedByName()

    override fun getCharactersByIds(ids: List<Int>): Flow<List<Character>> =
        gameDao.getCharactersByIds(ids)

    override fun getUpgradeCards(): Flow<List<UpgradeCard>> =
        gameDao.getUpgradeCards()

    override fun getCampaignCards(): Flow<List<CampaignCard>> =
        gameDao.getCampaignCards()

    override fun getCampaigns(): Flow<List<Campaign>> =
        userDao.getCampaigns()

    override fun getTroupes(): Flow<List<Troupe>> =
        userDao.getTroupes()

    override fun getGameResults(): Flow<List<GameResult>> =
        userDao.getGameResults()

    // --- Troupe writes ---

    override suspend fun upsertTroupe(troupe: Troupe): Long =
        userDao.upsertTroupe(troupe)

    override suspend fun deleteTroupe(troupe: Troupe) =
        userDao.deleteTroupe(troupe)

    override suspend fun getTroupeById(id: Int): Troupe? =
        userDao.getTroupeById(id)

    override suspend fun getTroupeByShareCode(code: String): Troupe? =
        userDao.getTroupeByShareCode(code)

    // --- Campaign writes ---

    override suspend fun upsertCampaign(campaign: Campaign): Long =
        userDao.upsertCampaign(campaign)

    override suspend fun deleteCampaign(campaign: Campaign) =
        userDao.deleteCampaign(campaign)

    // --- Game result writes ---

    override suspend fun upsertGameResult(result: GameResult) =
        userDao.upsertGameResult(result)

    // --- Asset seeding ---

    suspend fun seedFromAssets(context: Context) {
        val compendium = CharacterData.readCompendium(context)
        gameDao.upsertCharacters(compendium.characters)
        gameDao.upsertUpgradeCards(compendium.upgrades)
        gameDao.upsertCampaignCards(compendium.campaign)
    }

    suspend fun seedFromFiles(dir: java.io.File) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val compendium = dir.resolve("compendium.json").takeIf { it.exists() }?.readText()
            ?.let { json.decodeFromString<CompendiumData>(it) } ?: return
        gameDao.upsertCharacters(compendium.characters)
        gameDao.upsertUpgradeCards(compendium.upgrades)
        gameDao.upsertCampaignCards(compendium.campaign)
    }
}
