package dev.aldoborrero.peke.kids.gate

import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsCaller
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.KidsServerDao
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.server.SetLockRequest
import dev.aldoborrero.peke.kids.timer.MonotonicClock
import dev.aldoborrero.peke.services.hilt.DefaultDispatcher
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What is known about the household lock
 */
sealed interface LockState {
    data class Code(
        val type: ParentLockType,
    ) : LockState

    /**
     * The server has no household code yet
     */
    data object NotSet : LockState

    /**
     * The server can't be reached and nothing is cached
     */
    data object Unknown : LockState

    /**
     * The server no longer has the plugin and this TV never cached the code, so no code can open the gate. A server
     * administrator can sign in instead, only to turn kids mode off for that server.
     */
    data object PluginMissing : LockState
}

/**
 * The outcome of the one-off administrator sign-in at the gate
 */
enum class AdminCheck {
    ADMIN,
    NOT_ADMIN,
    WRONG,
    UNREACHABLE,
    FAILED,
}

sealed interface VerifyOutcome {
    /**
     * Right code, checked by the server: changes are authorised for a few minutes
     */
    data object Valid : VerifyOutcome

    /**
     * Right code, checked against the cached verifier because the server could not be reached. No changes can be
     * made on the server.
     */
    data object ValidOffline : VerifyOutcome

    data object Wrong : VerifyOutcome

    data class Throttled(
        val seconds: Int,
    ) : VerifyOutcome

    /**
     * Neither the server nor a cached verifier can check the code
     */
    data object CannotCheck : VerifyOutcome
}

sealed interface LockChange {
    data object Done : LockChange

    data object WrongCurrent : LockChange

    data class Throttled(
        val seconds: Int,
    ) : LockChange

    /**
     * The grant expired or is missing; pass the gate again
     */
    data object NeedsGrant : LockChange

    data object Invalid : LockChange

    data object Unreachable : LockChange
}

/**
 * The server, the user whose token is used, and the parent grant if one is held, for a change on the server
 */
data class KidsAuth(
    val server: JellyfinServer,
    val user: JellyfinUser,
    val token: String,
    val grant: String?,
) {
    val caller: KidsCaller get() = KidsCaller(token, user.id)
}

/**
 * The household parent lock of the Jellyfin Kids plugin
 *
 * The server checks codes and answers a right one with a short-lived grant, kept only in memory, that authorises
 * the parent changes that follow. After each server unlock the household verifier is cached, so the gate still
 * opens while the server can't be reached.
 */
@Singleton
class ParentLockService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val serverDao: JellyfinServerDao,
        private val kidProfileDao: KidProfileDao,
        private val kidsServerDao: KidsServerDao,
        private val kidsApi: KidsApi,
        private val pluginService: KidsPluginService,
        private val clock: MonotonicClock,
        @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        private data class Grant(
            val value: String,
            val validUntil: Long,
        )

        private val grants = ConcurrentHashMap<Pair<UUID, Int>, Grant>()

        /**
         * Per server, when the server's last Retry-After ends, on [clock]
         */
        private val serverRetryAt = ConcurrentHashMap<UUID, Long>()

        private val offlineMutex = Mutex()

        /**
         * The server and user the lock is checked as: the current ones, or else the server of the most recently used
         * kid with one of its signed-in users, such as on the user list before anyone is signed in
         */
        suspend fun target(): Pair<JellyfinServer, JellyfinUser>? {
            val current = serverRepository.current.value
            if (current != null && current.user.accessToken != null) return current.server to current.user
            val kidIds = kidProfileDao.allUserIds().toSet()
            if (kidIds.isEmpty()) return null
            val server = mostRecentKidServer(withContext(PekeDispatchers.IO) { serverDao.getServers() }, kidIds) ?: return null
            val user =
                server.users
                    .filter { it.accessToken != null }
                    .sortedByDescending { it.rowId in kidIds }
                    .firstOrNull()
                    ?: return null
            return server.server to user
        }

        suspend fun lockState(): LockState {
            val (server, user) = target() ?: return LockState.Unknown
            if (pluginService.state(server.id).status == KidsServerStatus.AVAILABLE) {
                when (val result = kidsApi.lock(server.url, KidsCaller(user.accessToken!!, user.id))) {
                    is KidsResult.Ok -> {
                        val type = parseType(result.value.lockType)
                        if (type == null) {
                            pluginService.cacheLock(server.id, null, null, null)
                            return LockState.NotSet
                        }
                        val cached = kidsServerDao.get(server.id)
                        if (cached?.lockType != type.name || cached.lockChangedAt != result.value.changedAt) {
                            // The code changed since the verifier was cached, so it would open the gate with the old code
                            pluginService.cacheLock(server.id, type.name, null, result.value.changedAt)
                        }
                        return LockState.Code(type)
                    }

                    KidsResult.NotFound -> {
                        // The plugin's routes are gone: ask again, so the status below is up to date
                        pluginService.check(server)
                    }

                    else -> {
                        Timber.d("Could not read the household lock: %s", result)
                    }
                }
            }
            val cached = kidsServerDao.get(server.id)
            if (cached?.verifier == null && pluginService.state(server.id).status == KidsServerStatus.GONE) {
                return LockState.PluginMissing
            }
            return parseType(cached?.lockType)?.let { LockState.Code(it) } ?: LockState.Unknown
        }

        /**
         * Sign in to the lock's server as [username], only to check that it is an administrator, then sign out. The
         * password and the session are never kept.
         */
        suspend fun checkAdmin(
            username: String,
            password: String,
        ): AdminCheck {
            val (server, _) = target() ?: return AdminCheck.UNREACHABLE
            return when (val result = kidsApi.isAdministrator(server.url, username, password)) {
                is KidsResult.Ok -> {
                    if (result.value) AdminCheck.ADMIN else AdminCheck.NOT_ADMIN
                }

                KidsResult.Forbidden -> {
                    AdminCheck.WRONG
                }

                KidsResult.Unreachable -> {
                    AdminCheck.UNREACHABLE
                }

                else -> {
                    Timber.w("Administrator sign-in failed: %s", result)
                    AdminCheck.FAILED
                }
            }
        }

        suspend fun verify(code: String): VerifyOutcome {
            val (server, user) = target() ?: return VerifyOutcome.CannotCheck
            if (pluginService.state(server.id).status == KidsServerStatus.AVAILABLE) {
                // The server asked to wait: don't send codes it would refuse unchecked (FR-014)
                serverWaitSeconds(server.id)?.let { return VerifyOutcome.Throttled(it) }
                val caller = KidsCaller(user.accessToken!!, user.id)
                when (val result = kidsApi.verify(server.url, caller, code)) {
                    is KidsResult.Ok -> {
                        val answer = result.value
                        return when {
                            answer.valid && answer.grant != null -> {
                                val receivedAt = clock.elapsedMillis()
                                grants[server.id to user.rowId] = Grant(answer.grant, receivedAt + grantMillis(answer.expiresAt))
                                resetOfflineThrottle(server.id)
                                cacheVerifier(server, caller, answer.grant)
                                VerifyOutcome.Valid
                            }

                            answer.retryAfterSeconds != null -> {
                                serverThrottled(server.id, answer.retryAfterSeconds)
                            }

                            else -> {
                                VerifyOutcome.Wrong
                            }
                        }
                    }

                    is KidsResult.Throttled -> {
                        return serverThrottled(server.id, result.retryAfterSeconds)
                    }

                    else -> {
                        // The server could not answer, so it is unknown whether the code is right: use the cache
                        Timber.d("Could not verify with the server: %s", result)
                    }
                }
            }
            return verifyOffline(server.id, code)
        }

        /**
         * Check [code] against the cached verifier, with the same limit on wrong codes as the plugin: after
         * [OfflineThrottle.THRESHOLD] in a row, codes are refused unchecked for a wait that doubles from 30 s to 15
         * min. Kept in the database, so restarting the app doesn't end a wait.
         */
        private suspend fun verifyOffline(
            serverId: UUID,
            code: String,
        ): VerifyOutcome =
            offlineMutex.withLock {
                val cached = kidsServerDao.get(serverId)
                val verifier = cached?.verifier ?: return@withLock VerifyOutcome.CannotCheck
                val now = clock.elapsedMillis()
                val waitMillis = OfflineThrottle.remainingMillis(cached.offlineFailures, cached.offlineBlockedAt, now)
                if (waitMillis > 0) return@withLock VerifyOutcome.Throttled(ceilSeconds(waitMillis))
                val ok = withContext(dispatcher) { PasscodeHasher.verify(code, verifier) }
                if (ok) {
                    kidsServerDao.updateOfflineThrottle(serverId, 0, null)
                    VerifyOutcome.ValidOffline
                } else {
                    val failures = cached.offlineFailures + 1
                    val blockedAt = if (failures >= OfflineThrottle.THRESHOLD) now else cached.offlineBlockedAt
                    if (failures >= OfflineThrottle.THRESHOLD) {
                        val seconds = OfflineThrottle.waitMillis(failures) / 1000
                        Timber.w("Offline parent code attempts blocked for %d s after %d wrong codes", seconds, failures)
                    }
                    kidsServerDao.updateOfflineThrottle(serverId, failures, blockedAt)
                    VerifyOutcome.Wrong
                }
            }

        private suspend fun resetOfflineThrottle(serverId: UUID) {
            val cached = kidsServerDao.get(serverId) ?: return
            if (cached.offlineFailures != 0 || cached.offlineBlockedAt != null) {
                offlineMutex.withLock { kidsServerDao.updateOfflineThrottle(serverId, 0, null) }
            }
        }

        private fun serverThrottled(
            serverId: UUID,
            seconds: Int,
        ): VerifyOutcome.Throttled {
            serverRetryAt[serverId] = clock.elapsedMillis() + seconds.coerceAtLeast(1) * 1000L
            return VerifyOutcome.Throttled(seconds)
        }

        /**
         * Seconds left of the last wait the server asked for, or null if none
         */
        private fun serverWaitSeconds(serverId: UUID): Int? {
            val retryAt = serverRetryAt[serverId] ?: return null
            val left = retryAt - clock.elapsedMillis()
            if (left <= 0) {
                serverRetryAt.remove(serverId)
                return null
            }
            return ceilSeconds(left)
        }

        private suspend fun cacheVerifier(
            server: JellyfinServer,
            caller: KidsCaller,
            grant: String,
        ) {
            // The lock's ChangedAt is read first: if the code changes in between, the verifier is newer than it and is
            // dropped at the next check, rather than an old verifier being kept with a new ChangedAt
            val status = kidsApi.lock(server.url, caller)
            val changedAt = (status as? KidsResult.Ok)?.value?.changedAt
            val result = kidsApi.verifier(server.url, caller, grant)
            if (result is KidsResult.Ok && changedAt != null) {
                pluginService.cacheLock(server.id, result.value.lockType, result.value.verifier, changedAt)
            } else {
                Timber.w("Could not cache the household verifier: %s %s", status, result)
            }
        }

        /**
         * Whether the current user is a server administrator, which authorises changes without a grant
         */
        fun isAdmin(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        /**
         * How to make a change on the current server: the current user's token with the grant, if any. Null if
         * neither a grant nor an administrator account allows changes.
         */
        fun auth(): KidsAuth? {
            val current = serverRepository.current.value ?: return null
            val token = current.user.accessToken ?: return null
            val grant = grantFor(current.server.id, current.user.rowId)
            if (grant == null && !isAdmin()) return null
            return KidsAuth(current.server, current.user, token, grant)
        }

        fun hasGrant(): Boolean {
            val current = serverRepository.current.value ?: return false
            return grantFor(current.server.id, current.user.rowId) != null
        }

        private fun grantFor(
            serverId: UUID,
            userRowId: Int,
        ): String? {
            val key = serverId to userRowId
            val grant = grants[key] ?: return null
            if (clock.elapsedMillis() >= grant.validUntil) {
                grants.remove(key)
                return null
            }
            return grant.value
        }

        /**
         * Forget the grant of the current user, for example after the server refused it
         */
        fun dropGrant() {
            val current = serverRepository.current.value ?: return
            grants.remove(current.server.id to current.user.rowId)
        }

        /**
         * Set or change the household code. Administrators set it directly; others need a grant and the current code.
         * Every outstanding grant ends with the change, so the new code is checked right away for a fresh one.
         */
        suspend fun setCode(
            type: ParentLockType,
            code: String,
            current: String?,
        ): LockChange {
            if (!PasscodeHasher.isValid(code, type)) return LockChange.Invalid
            val auth = auth() ?: return LockChange.NeedsGrant
            serverWaitSeconds(auth.server.id)?.let { return LockChange.Throttled(it) }
            val request = SetLockRequest(type.name, code, current.takeIf { !isAdmin() })
            return when (val result = kidsApi.setLock(auth.server.url, auth.caller, auth.grant, request)) {
                is KidsResult.Ok -> {
                    dropGrant()
                    verify(code)
                    LockChange.Done
                }

                is KidsResult.Invalid -> {
                    if ("Current" in result.fields) LockChange.WrongCurrent else LockChange.Invalid
                }

                is KidsResult.Throttled -> {
                    LockChange.Throttled(serverThrottled(auth.server.id, result.retryAfterSeconds).seconds)
                }

                KidsResult.Forbidden -> {
                    dropGrant()
                    LockChange.NeedsGrant
                }

                KidsResult.Unreachable -> {
                    LockChange.Unreachable
                }

                else -> {
                    Timber.w("Could not change the household lock: %s", result)
                    LockChange.Invalid
                }
            }
        }

        @Volatile
        private var settingsGrant = false

        /**
         * Lets the next opening of the kids settings skip the lock, right after passing the parent gate.
         * Only kept in memory, so settings restored after the app was killed ask again.
         */
        fun grantSettings() {
            settingsGrant = true
        }

        fun consumeSettingsGrant(): Boolean = settingsGrant.also { settingsGrant = false }

        companion object {
            private const val GRANT_MILLIS = 5 * 60 * 1000L
            private const val GRANT_MARGIN_MILLIS = 15 * 1000L

            fun parseType(name: String?): ParentLockType? = ParentLockType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

            private fun ceilSeconds(millis: Long): Int = ((millis + 999) / 1000).toInt()

            /**
             * How long a grant is used from when it was received, timed on the monotonic clock: the plugin's 5 minutes
             * less a margin. `ExpiresAt` only shortens it when this TV's wall clock agrees with it, that is when it
             * leaves between 0 and 5 minutes; a TV clock that is off can then neither stretch nor end a grant.
             *
             * @param now this TV's wall clock, in epoch millis
             */
            internal fun grantMillis(
                expiresAt: String?,
                now: Long = System.currentTimeMillis(),
            ): Long {
                val left =
                    expiresAt
                        ?.let {
                            try {
                                Instant.parse(it).toEpochMilli() - now
                            } catch (_: Exception) {
                                null
                            }
                        }?.takeIf { it in 1..GRANT_MILLIS }
                        ?: GRANT_MILLIS
                return (left - GRANT_MARGIN_MILLIS).coerceAtLeast(0)
            }
        }
    }

/**
 * The wrong-code limit for codes checked on this device, the same as the plugin's per device: after [THRESHOLD]
 * wrong codes in a row, codes are refused unchecked for 30 seconds, doubled by each wrong code after a wait, up to
 * 15 minutes. A right code clears it.
 */
internal object OfflineThrottle {
    const val THRESHOLD = 5
    private const val FIRST_WAIT_MILLIS = 30 * 1000L
    private const val MAX_WAIT_MILLIS = 15 * 60 * 1000L

    /**
     * The wait that [failures] wrong codes in a row start
     */
    fun waitMillis(failures: Int): Long {
        if (failures < THRESHOLD) return 0
        val doublings = (failures - THRESHOLD).coerceAtMost(10)
        return (FIRST_WAIT_MILLIS shl doublings).coerceAtMost(MAX_WAIT_MILLIS)
    }

    /**
     * How much of the wait is left at [now]. Both times are on the monotonic clock, which restarts at 0 when the TV
     * restarts: then only the time since the restart is counted, so a restart can lengthen a wait but never end it.
     */
    fun remainingMillis(
        failures: Int,
        blockedAt: Long?,
        now: Long,
    ): Long {
        val wait = waitMillis(failures)
        if (wait == 0L || blockedAt == null) return 0
        val elapsed = if (now >= blockedAt) now - blockedAt else now
        return (wait - elapsed).coerceAtLeast(0)
    }
}

/**
 * Of [servers], the one whose kid (a user in [kidIds]) was used most recently on this device, null if none has a kid
 */
fun mostRecentKidServer(
    servers: List<JellyfinServerUsers>,
    kidIds: Set<Int>,
): JellyfinServerUsers? =
    servers
        .filter { s -> s.users.any { it.rowId in kidIds } }
        .maxByOrNull { s -> s.users.filter { it.rowId in kidIds }.maxOf { it.lastUsed?.toEpochSecond() ?: Long.MIN_VALUE } }
