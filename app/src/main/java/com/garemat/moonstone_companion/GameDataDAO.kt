package com.garemat.moonstone_companion

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDataDAO {

    @Upsert
    suspend fun upsertCharacters(characters: List<Character>)

    @Delete
    suspend fun deleteCharacter(character: Character)

    @Query("SELECT * FROM character ORDER BY name ASC")
    fun getCharactersOrderedByName(): Flow<List<Character>>

    @Query("SELECT * FROM character WHERE id IN (:ids)")
    fun getCharactersByIds(ids: List<Int>): Flow<List<Character>>

    @Upsert
    suspend fun upsertUpgradeCards(cards: List<UpgradeCard>)

    @Query("SELECT * FROM upgradecard ORDER BY name ASC")
    fun getUpgradeCards(): Flow<List<UpgradeCard>>

    @Query("SELECT * FROM upgradecard WHERE id IN (:ids)")
    fun getUpgradeCardsByIds(ids: List<Int>): Flow<List<UpgradeCard>>

    @Upsert
    suspend fun upsertCampaignCards(cards: List<CampaignCard>)

    @Query("SELECT * FROM campaigncard ORDER BY name ASC")
    fun getCampaignCards(): Flow<List<CampaignCard>>

    @Query("SELECT * FROM campaigncard WHERE id IN (:ids)")
    fun getCampaignCardsByIds(ids: List<Int>): Flow<List<CampaignCard>>
}
