package io.github.garemat.lunachron

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDataDAO {

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

    @Upsert
    suspend fun upsertCampaign(campaign: Campaign): Long

    @Delete
    suspend fun deleteCampaign(campaign: Campaign)

    @Query("SELECT * FROM campaign ORDER BY name ASC")
    fun getCampaigns(): Flow<List<Campaign>>

    @Upsert
    suspend fun upsertGameResult(result: GameResult)

    @Query("SELECT * FROM gameresult ORDER BY timestamp DESC")
    fun getGameResults(): Flow<List<GameResult>>
}
