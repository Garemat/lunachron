package com.garemat.moonstone_companion

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDAO {

    // Character Operations
    @Upsert
    suspend fun upsertCharacter(character: Character)

    @Upsert
    suspend fun upsertCharacters(characters: List<Character>)

    @Delete
    suspend fun deleteCharacter(character: Character)

    @Query("SELECT * FROM character ORDER BY name ASC")
    fun getCharactersOrderedByName(): Flow<List<Character>>

    @Query("SELECT * FROM character WHERE id IN (:ids)")
    fun getCharactersByIds(ids: List<Int>): Flow<List<Character>>

    // Upgrade Card Operations
    @Upsert
    suspend fun upsertUpgradeCard(card: UpgradeCard)

    @Upsert
    suspend fun upsertUpgradeCards(cards: List<UpgradeCard>)

    @Query("SELECT * FROM upgradecard ORDER BY name ASC")
    fun getUpgradeCards(): Flow<List<UpgradeCard>>

    @Query("SELECT * FROM upgradecard WHERE id IN (:ids)")
    fun getUpgradeCardsByIds(ids: List<Int>): Flow<List<UpgradeCard>>

    // Campaign Card Operations
    @Upsert
    suspend fun upsertCampaignCard(card: CampaignCard)

    @Upsert
    suspend fun upsertCampaignCards(cards: List<CampaignCard>)

    @Query("SELECT * FROM campaigncard ORDER BY name ASC")
    fun getCampaignCards(): Flow<List<CampaignCard>>

    @Query("SELECT * FROM campaigncard WHERE id IN (:ids)")
    fun getCampaignCardsByIds(ids: List<Int>): Flow<List<CampaignCard>>

    // Campaign Operations
    @Upsert
    suspend fun upsertCampaign(campaign: Campaign): Long

    @Delete
    suspend fun deleteCampaign(campaign: Campaign)

    @Query("SELECT * FROM campaign ORDER BY name ASC")
    fun getCampaigns(): Flow<List<Campaign>>

    // Troupe Operations
    @Upsert
    suspend fun upsertTroupe(troupe: Troupe): Long

    @Delete
    suspend fun deleteTroupe(troupe: Troupe)

    @Query("SELECT * FROM troupe ORDER BY troupeName ASC")
    fun getTroupes(): Flow<List<Troupe>>

    @Query("SELECT * FROM troupe WHERE id = :id")
    suspend fun getTroupeById(id: Int): Troupe?

    @Query("SELECT * FROM troupe WHERE shareCode = :code")
    suspend fun getTroupeByShareCode(code: String): Troupe?

    // Game Result Operations
    @Upsert
    suspend fun upsertGameResult(result: GameResult)

    @Query("SELECT * FROM gameresult ORDER BY timestamp DESC")
    fun getGameResults(): Flow<List<GameResult>>
}
