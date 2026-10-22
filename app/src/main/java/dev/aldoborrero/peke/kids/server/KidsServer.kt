package dev.aldoborrero.peke.kids.server

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import dev.aldoborrero.peke.data.model.JellyfinServer
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * What the device knows about one server's Kids plugin: what it last advertised, and the household lock cached for
 * unlocking offline
 */
@Entity(
    tableName = "kids_servers",
    foreignKeys = [
        ForeignKey(
            entity = JellyfinServer::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("serverId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class KidsServer(
    @PrimaryKey val serverId: UUID,
    /**
     * From the last successful `/Kids/Info`, null once the server said it has no plugin
     */
    val apiVersion: Int? = null,
    /**
     * Comma-separated features from the last successful `/Kids/Info`
     */
    val features: String = "",
    /**
     * When the server last gave a definite answer, in epoch millis
     */
    val checkedAt: Long? = null,
    val lockType: String? = null,
    /**
     * The household verifier, only ever used to check codes on this device
     */
    val verifier: String? = null,
    /**
     * The lock's `ChangedAt` when [verifier] was cached. A different one on the server means the code changed, so the
     * verifier is dropped.
     */
    val lockChangedAt: String? = null,
    /**
     * Consecutive wrong codes checked against [verifier] on this device
     */
    val offlineFailures: Int = 0,
    /**
     * When the last wrong offline code started a wait, in [dev.aldoborrero.peke.kids.timer.MonotonicClock]
     * millis
     */
    val offlineBlockedAt: Long? = null,
) {
    val featureSet: Set<String> get() = features.split(',').filter { it.isNotBlank() }.toSet()

    /**
     * Whether this server offers kids mode in a version this app speaks
     */
    val available: Boolean
        get() =
            apiVersion == KIDS_API_VERSION && featureSet.containsAll(KidsFeatures.REQUIRED)

    /**
     * The server has the plugin, but a version older than kids mode needs, so it has to be updated
     */
    val outdated: Boolean
        get() =
            !available &&
                apiVersion == KIDS_API_VERSION &&
                KidsFeatures.PROFILES in featureSet &&
                KidsFeatures.PARENT_LOCK in featureSet

    fun has(feature: String): Boolean = available && feature in featureSet
}

@Dao
interface KidsServerDao {
    @Query("SELECT * FROM kids_servers WHERE serverId=:serverId")
    suspend fun get(serverId: UUID): KidsServer?

    @Query("SELECT * FROM kids_servers WHERE serverId=:serverId")
    fun observe(serverId: UUID): Flow<KidsServer?>

    @Upsert
    suspend fun upsert(server: KidsServer)

    /**
     * Adds a row for [server] unless there is one, so the targeted updates below have a row to change
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(server: KidsServer)

    /**
     * Only the columns `/Kids/Info` answers, so a lock or throttle change made meanwhile is kept
     */
    @Query("UPDATE kids_servers SET apiVersion=:apiVersion, features=:features, checkedAt=:checkedAt WHERE serverId=:serverId")
    suspend fun updateInfo(
        serverId: UUID,
        apiVersion: Int?,
        features: String,
        checkedAt: Long,
    )

    @Query("UPDATE kids_servers SET lockType=:lockType, verifier=:verifier, lockChangedAt=:changedAt WHERE serverId=:serverId")
    suspend fun updateLock(
        serverId: UUID,
        lockType: String?,
        verifier: String?,
        changedAt: String?,
    )

    @Query("UPDATE kids_servers SET offlineFailures=:failures, offlineBlockedAt=:blockedAt WHERE serverId=:serverId")
    suspend fun updateOfflineThrottle(
        serverId: UUID,
        failures: Int,
        blockedAt: Long?,
    )

    @Query("DELETE FROM kids_servers WHERE serverId=:serverId")
    suspend fun delete(serverId: UUID)
}
