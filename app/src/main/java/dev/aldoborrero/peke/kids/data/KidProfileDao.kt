package dev.aldoborrero.peke.kids.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface KidProfileDao {
    @Query("SELECT * FROM kid_profiles WHERE userId=:userId")
    fun observe(userId: Int): Flow<KidProfile?>

    @Query("SELECT * FROM kid_profiles")
    fun observeAll(): Flow<List<KidProfile>>

    @Query("SELECT * FROM kid_profiles WHERE userId=:userId")
    suspend fun get(userId: Int): KidProfile?

    @Query("SELECT userId FROM kid_profiles")
    suspend fun allUserIds(): List<Int>

    @Query("SELECT userId FROM kid_profiles")
    fun observeUserIds(): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM kid_profiles")
    fun observeCount(): Flow<Int>

    @Query("SELECT userId FROM kid_profiles WHERE serverId=:serverId")
    suspend fun userIdsForServer(serverId: UUID): List<Int>

    @Query("SELECT COUNT(*) FROM kid_profiles WHERE serverId=:serverId")
    fun observeCountForServer(serverId: UUID): Flow<Int>

    @Upsert
    suspend fun upsert(profile: KidProfile)

    @Query("DELETE FROM kid_profiles WHERE userId=:userId")
    suspend fun delete(userId: Int)

    @Query("DELETE FROM kid_profiles WHERE serverId=:serverId")
    suspend fun deleteForServer(serverId: UUID)
}
