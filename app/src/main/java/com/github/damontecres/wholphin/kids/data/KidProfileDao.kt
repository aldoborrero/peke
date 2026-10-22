package com.github.damontecres.wholphin.kids.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface KidProfileDao {
    @Query("SELECT * FROM kid_profiles WHERE userId=:userId")
    fun observe(userId: Int): Flow<KidProfile?>

    @Query("SELECT * FROM kid_profiles WHERE userId=:userId")
    suspend fun get(userId: Int): KidProfile?

    @Query("SELECT userId FROM kid_profiles")
    suspend fun allUserIds(): List<Int>

    @Query("SELECT COUNT(*) FROM kid_profiles")
    fun observeCount(): Flow<Int>

    @Query("SELECT k.userId FROM kid_profiles k JOIN users u ON u.rowId = k.userId WHERE u.serverId=:serverId")
    suspend fun userIdsForServer(serverId: UUID): List<Int>

    @Query("SELECT COUNT(*) FROM kid_profiles k JOIN users u ON u.rowId = k.userId WHERE u.serverId=:serverId")
    fun observeCountForServer(serverId: UUID): Flow<Int>

    /**
     * The server of the kid profile used most recently on this device
     */
    @Query(
        "SELECT u.serverId FROM kid_profiles k JOIN users u ON u.rowId = k.userId " +
            "ORDER BY u.lastUsed IS NULL, u.lastUsed DESC LIMIT 1",
    )
    suspend fun mostRecentServerId(): UUID?

    @Upsert
    suspend fun upsert(profile: KidProfile)

    @Query("DELETE FROM kid_profiles WHERE userId=:userId")
    suspend fun delete(userId: Int)

    @Query("DELETE FROM kid_profiles WHERE userId IN (SELECT rowId FROM users WHERE serverId=:serverId)")
    suspend fun deleteForServer(serverId: UUID)
}
