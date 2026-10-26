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
import dev.aldoborrero.peke.kids.setup.AdminSession
import dev.aldoborrero.peke.kids.setup.AdminSessions
import dev.aldoborrero.peke.kids.setup.AdminSignIn
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.use
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
     * administrator can sign in instead, only to reset this TV.
     */
    data object PluginMissing : LockState

    /**
     * No kid on this TV has a token the server accepts and no verifier is cached, so no code can be checked: only
     * recovery with a server administrator is left (research R7)
     */
    data object RecoveryOnly : LockState
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
        private val tvSetupRepository: TvSetupRepository,
        private val kidsApi: KidsApi,
        private val pluginService: KidsPluginService,
        private val adminSessions: AdminSessions,
        private val clock: MonotonicClock,
        @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        private data class Grant(
            val value: String,
            val validUntil: Long,
        )

        private val grants = ConcurrentHashMap<Pair<UUID, Int>, Grant>()

        /**
         * Until when, on [clock], the last code that passed the gate counts, also when it was checked offline and gave
         * no grant: what grown-up screens check before they show ([hasLiveGrant])
         */
        @Volatile
        private var passedUntil: Long? = null

        /**
         * Kid tokens the server refused in this run of the app, which the gate no longer checks codes with
         */
        private val refusedTokens = ConcurrentHashMap.newKeySet<String>()

        /**
         * Per server, when the server's last Retry-After ends, on [clock]
         */
        private val serverRetryAt = ConcurrentHashMap<UUID, Long>()

        private val offlineMutex = Mutex()

        /**
         * The server the gate checks codes for, and this TV's kids whose tokens it may check them with, the current kid
         * first (research R7). With no kid, or none whose token works, only the cached verifier can check a code.
         */
        private data class GateTarget(
            val server: JellyfinServer,
            val users: List<JellyfinUser>,
        )

        /**
         * The current kid's server, or else this TV's (the one its setup names, then the most recently used kid's),
         * with this TV's kids that have a token the server hasn't refused, the current kid first. Only kids: a row
         * without a kid profile, such as an administrator's left by an older version, is never used.
         */
        private suspend fun gateTarget(): GateTarget? {
            val current = serverRepository.current.value
            val kidIds = kidProfileDao.allUserIds().toSet()
            val server =
                current?.server
                    ?: tvSetupRepository.server()
                    ?: mostRecentKidServer(withContext(PekeDispatchers.IO) { serverDao.getServers() }, kidIds)?.server
                    ?: return null
            val stored = withContext(PekeDispatchers.IO) { serverDao.getServer(server.id) }?.users.orEmpty()
            val users =
                (listOfNotNull(current?.user?.takeIf { current.server.id == server.id }) + stored)
                    .distinctBy { it.rowId }
                    .filter { it.rowId in kidIds && it.tokenWorks() }
            return GateTarget(server, users)
        }

        /**
         * Ask the server whether it has the plugin, if it didn't answer in this run of the app: the cached status alone
         * may be missing, as with no kid current or after the kids data was lost, and the gate would never ask the
         * plugin then
         */
        private suspend fun checkPlugin(server: JellyfinServer) {
            if (!pluginService.answered(server.id)) pluginService.check(server)
        }

        /**
         * The server the gate checks codes for, for recovery with an administrator
         */
        suspend fun gateServer(): JellyfinServer? = gateTarget()?.server

        private fun JellyfinUser.tokenWorks(): Boolean = accessToken?.let { it !in refusedTokens } == true

        private fun refused(user: JellyfinUser) {
            Timber.i("The server refused the token of %s; the gate tries another kid or the cached verifier", user.rowId)
            user.accessToken?.let { refusedTokens += it }
        }

        suspend fun lockState(): LockState {
            val target = gateTarget() ?: return LockState.Unknown
            val server = target.server
            checkPlugin(server)
            if (pluginService.state(server.id).status == KidsServerStatus.AVAILABLE) {
                for (user in target.users) {
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

                        KidsResult.Forbidden -> {
                            // This kid's session no longer works: try the next kid
                            refused(user)
                            continue
                        }

                        KidsResult.NotFound -> {
                            // The plugin's routes are gone: ask again, so the status below is up to date
                            pluginService.check(server)
                        }

                        else -> {
                            Timber.d("Could not read the household lock: %s", result)
                        }
                    }
                    break
                }
            }
            val cached = kidsServerDao.get(server.id)
            if (cached?.verifier == null && pluginService.state(server.id).status == KidsServerStatus.GONE) {
                return LockState.PluginMissing
            }
            val type = parseType(cached?.lockType)
            if (target.users.none { it.tokenWorks() }) {
                // No kid's token works: only the cached verifier can check a code, and without it only recovery is left
                return if (cached?.verifier != null && type != null) LockState.Code(type) else LockState.RecoveryOnly
            }
            return type?.let { LockState.Code(it) } ?: LockState.Unknown
        }

        /**
         * Sign in to the lock's server as [username], only to check that it is an administrator, then sign out. The
         * password and the session are never kept. For a server whose plugin went away before this TV cached the code:
         * an administrator then passes the gate, only to reset this TV ([hasLiveGrant]).
         */
        suspend fun checkAdmin(
            username: String,
            password: String,
        ): AdminCheck {
            val server = gateServer() ?: return AdminCheck.UNREACHABLE
            return when (val result = adminSessions.open(server, username, password)) {
                is AdminSignIn.Opened -> {
                    // Only the check was needed: the session ends right away
                    result.session.close()
                    passedUntil = clock.elapsedMillis() + grantMillis(null)
                    AdminCheck.ADMIN
                }

                AdminSignIn.NotAdmin -> {
                    AdminCheck.NOT_ADMIN
                }

                AdminSignIn.Wrong -> {
                    AdminCheck.WRONG
                }

                AdminSignIn.Unreachable -> {
                    AdminCheck.UNREACHABLE
                }

                AdminSignIn.QuickConnectOff, is AdminSignIn.Failed -> {
                    Timber.w("Administrator sign-in failed: %s", result)
                    AdminCheck.FAILED
                }
            }
        }

        /**
         * Check the household code with the plugin, as the current kid or another kid of this TV whose token works, or
         * else against the cached verifier (research R7)
         */
        suspend fun verify(code: String): VerifyOutcome {
            val target = gateTarget() ?: return VerifyOutcome.CannotCheck
            val server = target.server
            checkPlugin(server)
            if (pluginService.state(server.id).status == KidsServerStatus.AVAILABLE) {
                // The server asked to wait: don't send codes it would refuse unchecked (FR-014)
                serverWaitSeconds(server.id)?.let { return VerifyOutcome.Throttled(it) }
                for (user in target.users) {
                    val caller = KidsCaller(user.accessToken!!, user.id)
                    when (val result = kidsApi.verify(server.url, caller, code)) {
                        is KidsResult.Ok -> {
                            val answer = result.value
                            return when {
                                answer.valid && answer.grant != null -> {
                                    val receivedAt = clock.elapsedMillis()
                                    val validUntil = receivedAt + grantMillis(answer.expiresAt)
                                    grants[server.id to user.rowId] = Grant(answer.grant, validUntil)
                                    passedUntil = validUntil
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

                        KidsResult.Forbidden -> {
                            // This kid's session no longer works: try the next kid
                            refused(user)
                            continue
                        }

                        else -> {
                            // The server could not answer, so it is unknown whether the code is right: use the cache
                            Timber.d("Could not verify with the server: %s", result)
                        }
                    }
                    break
                }
            }
            val outcome = verifyOffline(server.id, code)
            if (outcome == VerifyOutcome.ValidOffline) passedUntil = clock.elapsedMillis() + grantMillis(null)
            return outcome
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
         * How to make a change on the current server: the current user's token with their grant. Null without a grant:
         * an administrator's token is no grant (research R7), the administrator acts only through an
         * [dev.aldoborrero.peke.kids.setup.AdminSession].
         */
        fun auth(): KidsAuth? {
            val current = serverRepository.current.value ?: return null
            val token = current.user.accessToken ?: return null
            val grant = grantFor(current.server.id, current.user.rowId) ?: return null
            return KidsAuth(current.server, current.user, token, grant)
        }

        /**
         * Whether a code passed the gate a moment ago, checked by the server or offline: grown-up screens show only
         * then, and otherwise ask for the code (C12). Kept only in memory.
         */
        fun hasLiveGrant(): Boolean = passedUntil?.let { clock.elapsedMillis() < it } == true

        /**
         * Forget every grant and pass, for a reset of this TV
         */
        fun forgetGrants() {
            grants.clear()
            passedUntil = null
            settingsGrant = false
            refusedTokens.clear()
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
         * Change the household code, with a grant and the current code; recovery without the current one is [recover].
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
            val request = SetLockRequest(type.name, code, current)
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

        /**
         * Recover a forgotten code (spec FR-017): set a new one with [admin], a server administrator's in-memory
         * session, without the current code, which the plugin allows administrators. [admin] is closed afterwards,
         * however this ends. The administrator, who just proved who they are, then passes the gate ([hasLiveGrant]),
         * so the parent menu opens with Kids on this TV and Reset this TV.
         */
        suspend fun recover(
            admin: AdminSession,
            type: ParentLockType,
            code: String,
        ): LockChange =
            admin.use { session ->
                if (!PasscodeHasher.isValid(code, type)) return@use LockChange.Invalid
                val server = session.server
                when (val result = kidsApi.setLock(server.url, session.caller, null, SetLockRequest(type.name, code))) {
                    is KidsResult.Ok -> {
                        // The change ended every grant, and the cached verifier would still open the gate with the old code
                        grants.keys.removeIf { it.first == server.id }
                        // The plugin gives an administrator the verifier without a grant: with it the gate checks the new
                        // code even when no kid's token works
                        val verifier = (kidsApi.verifier(server.url, session.caller, null) as? KidsResult.Ok)?.value
                        pluginService.cacheLock(
                            server.id,
                            verifier?.lockType ?: result.value.lockType ?: type.name,
                            verifier?.verifier,
                            result.value.changedAt,
                        )
                        resetOfflineThrottle(server.id)
                        // The administrator just proved who they are: the gate counts as passed, so the parent menu,
                        // with Kids on this TV and Reset this TV, opens
                        passedUntil = clock.elapsedMillis() + grantMillis(null)
                        LockChange.Done
                    }

                    is KidsResult.Throttled -> {
                        LockChange.Throttled(result.retryAfterSeconds)
                    }

                    KidsResult.Unreachable -> {
                        LockChange.Unreachable
                    }

                    else -> {
                        Timber.w("Could not recover the household code: %s", result)
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
