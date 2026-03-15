package io.github.garemat.lunachron

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the data layer. Swap [LocalCharacterRepository] for a remote
 * implementation when migrating to the API. All field name mapping (JSON → domain)
 * stays inside the implementation, keeping the ViewModel API stable.
 */
interface CharacterRepository {
    // Read streams
    fun getCharacters(): Flow<List<Character>>
    fun getCharactersByIds(ids: List<Int>): Flow<List<Character>>
    fun getUpgradeCards(): Flow<List<UpgradeCard>>
    fun getCampaignCards(): Flow<List<CampaignCard>>
    fun getCampaigns(): Flow<List<Campaign>>
    fun getTroupes(): Flow<List<Troupe>>
    fun getGameResults(): Flow<List<GameResult>>

    // Troupe writes
    suspend fun upsertTroupe(troupe: Troupe): Long
    suspend fun deleteTroupe(troupe: Troupe)
    suspend fun getTroupeById(id: Int): Troupe?
    suspend fun getTroupeByShareCode(code: String): Troupe?

    // Campaign writes
    suspend fun upsertCampaign(campaign: Campaign): Long
    suspend fun deleteCampaign(campaign: Campaign)

    // Game result writes
    suspend fun upsertGameResult(result: GameResult)
}
