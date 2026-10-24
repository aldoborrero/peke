package dev.aldoborrero.peke.kids.setup

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.gate.mostRecentKidServer
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The setup steps of a TV, in order. A [TvSetupEntity] holds the first one not finished yet.
 */
enum class TvSetupStep {
    SERVER,
    ADMIN,
    CODE,
    KIDS,
    SIGN_IN,
    DONE,
}

/**
 * This TV's setup: the one server it belongs to and how far its setup went. There is at most one row, and it is the
 * single source of truth for kids mode (spec FR-016), read through [TvSetupRepository].
 *
 * It lives in the kids database, apart from the app database that holds the server and user rows, so [serverId] is
 * not a foreign key, and the server's URL is kept here too: the TV can still tell it's set up if the app database is
 * lost.
 */
@Entity(tableName = "tv_setup")
@TypeConverters(TvSetupConverters::class)
data class TvSetupEntity(
    /**
     * Always [ID]
     */
    @PrimaryKey val id: Int = ID,
    /**
     * The server this TV belongs to (`servers.id` in the app database)
     */
    val serverId: UUID,
    /**
     * The server's URL; empty for a row made by the migration from version 1, which can't read the app database
     */
    val serverUrl: String,
    /**
     * The first unfinished step
     */
    val step: TvSetupStep,
    /**
     * The `users.rowId` of the last kid who became current, for the picker's rule on a stopped kid (spec FR-012a)
     */
    val lastKidRowId: Int? = null,
    /**
     * When [step] became [TvSetupStep.DONE]
     */
    val completedAt: Instant? = null,
) {
    companion object {
        const val ID = 1
    }
}

class TvSetupConverters {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)
}

@Dao
interface TvSetupDao {
    @Query("SELECT * FROM tv_setup WHERE id = ${TvSetupEntity.ID}")
    fun observe(): Flow<TvSetupEntity?>

    @Query("SELECT * FROM tv_setup WHERE id = ${TvSetupEntity.ID}")
    suspend fun get(): TvSetupEntity?

    @Upsert
    suspend fun upsert(setup: TvSetupEntity)

    @Query("DELETE FROM tv_setup")
    suspend fun delete()
}

/**
 * What this TV is, as far as the first screen goes (data-model.md). Every kids-mode check reads it.
 */
sealed interface TvState {
    /**
     * Not read yet: treated as kids mode, never as a reason to show setup or a list of users
     */
    data object Loading : TvState

    /**
     * No server stored anywhere: setup starts from [TvSetupStep.SERVER]
     */
    data object NotSetUp : TvState

    /**
     * Setup resumes at [step]
     */
    data class InSetup(
        val step: TvSetupStep,
    ) : TvState

    /**
     * Set up, so always in kids mode. [kids] are this TV's kids, the users of [server] with a cached kid profile, in
     * the order they were added; they may be none.
     */
    data class SetUp(
        val server: JellyfinServer,
        val kids: List<JellyfinUser>,
        val lastKidRowId: Int? = null,
    ) : TvState
}

/**
 * The single source of truth for "this TV is set up" (spec FR-016, research R3)
 *
 * It reads the `tv_setup` row together with the server and user rows of the app database and the kids cache, and
 * fails closed: a missing or unreadable row with a stored server is [TvState.SetUp], and only no server at all is
 * [TvState.NotSetUp] (FR-018).
 */
@Singleton
class TvSetupRepository
    @Inject
    constructor(
        private val tvSetupDao: TvSetupDao,
        private val serverDao: JellyfinServerDao,
        private val kidProfileDao: KidProfileDao,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        val state: StateFlow<TvState> =
            combine(
                tvSetupDao
                    .observe()
                    .map<TvSetupEntity?, Row> { Row.Read(it) }
                    .catch { ex ->
                        Timber.e(ex, "Could not read this TV's setup")
                        emit(Row.Unreadable)
                    },
                serverDao
                    .observeServers()
                    .map<List<JellyfinServerUsers>, List<JellyfinServerUsers>?> { it }
                    .catch { ex ->
                        Timber.e(ex, "Could not read the servers")
                        emit(null)
                    },
                kidProfileDao.observeUserIds().map { it.toSet() },
            ) { row, servers, kidIds -> tvState(row, servers, kidIds) }
                .distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, TvState.Loading)

        /**
         * Store that setup for [server] has reached [step]
         */
        suspend fun markStep(
            server: JellyfinServer,
            step: TvSetupStep,
        ) {
            val row = existingRow()
            tvSetupDao.upsert(
                TvSetupEntity(
                    serverId = server.id,
                    serverUrl = server.url,
                    step = step,
                    lastKidRowId = row?.takeIf { it.serverId == server.id }?.lastKidRowId,
                    completedAt = if (step == TvSetupStep.DONE) row?.completedAt ?: Instant.now() else null,
                ),
            )
        }

        suspend fun markDone(server: JellyfinServer) = markStep(server, TvSetupStep.DONE)

        /**
         * Remember the kid who became current. Without a row, which counts as set up, a finished one is stored.
         */
        suspend fun setLastKid(
            server: JellyfinServer,
            rowId: Int,
        ) {
            val row = existingRow()
            tvSetupDao.upsert(
                row?.copy(lastKidRowId = rowId)
                    ?: TvSetupEntity(
                        serverId = server.id,
                        serverUrl = server.url,
                        step = TvSetupStep.DONE,
                        lastKidRowId = rowId,
                        completedAt = Instant.now(),
                    ),
            )
        }

        /**
         * The server this TV's setup is for, to resume a setup in progress: the stored server, or else the one the row
         * names. Null without a readable row.
         */
        suspend fun server(): JellyfinServer? {
            val row = existingRow() ?: return null
            val stored = withContext(PekeDispatchers.IO) { serverDao.getServer(row.serverId) }?.server
            return stored
                ?: JellyfinServer(id = row.serverId, name = null, url = row.serverUrl, version = null).takeIf { row.serverUrl.isNotBlank() }
        }

        /**
         * Forget this TV's setup, for a reset
         */
        suspend fun erase() {
            tvSetupDao.delete()
        }

        /**
         * The stored row, or null if there is none or it can't be read; then it is written anew
         */
        private suspend fun existingRow(): TvSetupEntity? =
            try {
                tvSetupDao.get()
            } catch (ex: Exception) {
                Timber.e(ex, "Could not read this TV's setup")
                null
            }
    }

private sealed interface Row {
    data class Read(
        val row: TvSetupEntity?,
    ) : Row

    data object Unreadable : Row
}

/**
 * @param servers null when they could not be read
 */
private fun tvState(
    row: Row,
    servers: List<JellyfinServerUsers>?,
    kidIds: Set<Int>,
): TvState {
    val setup = (row as? Row.Read)?.row
    if (setup != null) {
        if (setup.step != TvSetupStep.DONE) return TvState.InSetup(setup.step)
        val stored = servers?.firstOrNull { it.server.id == setup.serverId }
        // The app database may have lost the server: the row alone keeps the TV set up
        val server = stored?.server ?: JellyfinServer(id = setup.serverId, name = null, url = setup.serverUrl, version = null)
        return TvState.SetUp(server, kidsOf(stored, kidIds), setup.lastKidRowId)
    }
    // No row, or one that can't be read: set up if any server is stored (FR-018)
    if (servers == null) return TvState.Loading
    if (servers.isEmpty()) return TvState.NotSetUp
    val stored =
        mostRecentKidServer(servers, kidIds)
            ?: servers.maxBy { it.server.lastUsed?.toEpochSecond() ?: Long.MIN_VALUE }
    return TvState.SetUp(stored.server, kidsOf(stored, kidIds))
}

private fun kidsOf(
    server: JellyfinServerUsers?,
    kidIds: Set<Int>,
): List<JellyfinUser> =
    server
        ?.users
        .orEmpty()
        .filter { it.rowId in kidIds }
        .sortedBy { it.rowId }
