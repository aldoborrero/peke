package dev.aldoborrero.peke.kids

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.timer.MonotonicClock
import dev.aldoborrero.peke.services.KeyValueService
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface KidsModeState {
    /**
     * Not known yet whether the current user is a kid, so nothing that differs for kids is shown. A user without a
     * cached kid profile stays here until the server said they are not a kid; see [KidsModeService.unanswered] for
     * when the server couldn't be asked.
     */
    data object Loading : KidsModeState

    /**
     * The server said the current user is not a kid
     */
    data object Inactive : KidsModeState

    data class Active(
        val profile: KidProfile,
        val user: JellyfinUser,
    ) : KidsModeState
}

val KidsModeState.isActive: Boolean get() = this is KidsModeState.Active

val KidsModeState.profile: KidProfile? get() = (this as? KidsModeState.Active)?.profile

/**
 * Whether a user is a kid, from their cached profile and what the server said in this run of the app
 *
 * A cached profile makes them a kid, whatever the server says or fails to say (fail closed). Without one, only the
 * server's "not a kid" makes them a grown-up; until then it is not known.
 */
internal fun kidsModeState(
    user: JellyfinUser,
    profile: KidProfile?,
    notAKid: Boolean,
): KidsModeState =
    when {
        profile != null -> KidsModeState.Active(profile, user)
        notAKid -> KidsModeState.Inactive
        else -> KidsModeState.Loading
    }

/**
 * Tracks whether the current user is a kid profile, and the device's kids lock
 *
 * Who is a kid comes from the Jellyfin Kids plugin, through the [KidProfileRepository] cache. The kids lock is set
 * when a kid becomes the current profile (also at app start) and is only cleared by [unlock], which callers must only
 * invoke after the parent passcode was verified. Profile refreshes don't set it again. While locked, only kid
 * profiles may be selected.
 *
 * Each time a user becomes current their profile is read from the server once ([lookUp]); a sign-in and the user list
 * share that read instead of making their own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class KidsModeService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val kidProfileDao: KidProfileDao,
        private val keyValueService: KeyValueService,
        private val kidProfileRepository: KidProfileRepository,
        private val pluginService: KidsPluginService,
        private val clock: MonotonicClock,
        private val tvSetupRepository: TvSetupRepository,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) : DefaultLifecycleObserver {
        val state: StateFlow<KidsModeState> =
            serverRepository.current
                .map { it?.user }
                .distinctUntilChanged { a, b -> a?.rowId == b?.rowId }
                .flatMapLatest { user ->
                    if (user == null) {
                        flowOf(KidsModeState.Inactive)
                    } else {
                        combine(
                            kidProfileDao.observe(user.rowId),
                            kidProfileRepository.notKids.map { user.rowId in it }.distinctUntilChanged(),
                        ) { profile, notAKid -> kidsModeState(user, profile, notAKid) }
                            // Until the new user's profile is read, the previous user's state must not show
                            .onStart { emit(KidsModeState.Loading) }
                    }
                }.stateIn(scope, SharingStarted.Eagerly, KidsModeState.Loading)

        val isActive: Boolean get() = state.value.isActive

        private val failedLookups = MutableStateFlow<Set<Int>>(emptySet())

        /**
         * Whether the last read of the current user's profile got no definite answer (no connection, an error, the
         * plugin not known), while [state] is still [KidsModeState.Loading]. [retry] asks again.
         */
        val unanswered: StateFlow<Boolean> =
            combine(state, serverRepository.current, failedLookups) { state, current, failed ->
                state == KidsModeState.Loading && current != null && current.user.rowId in failed
            }.distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, false)

        /**
         * Whether the device is locked to kid profiles, null until known. Fails closed: with no stored value, any kid
         * profile locks.
         */
        val locked: StateFlow<Boolean?> =
            combine(keyValueService.get<Boolean>(LOCK_KEY), kidProfileDao.observeCount()) { flag, kidCount ->
                kidCount > 0 && flag != false
            }.stateIn(scope, SharingStarted.Eagerly, null)

        /**
         * Whether the kids lock is engaged as stored, null until read. Unlike [locked] it doesn't depend on the cached
         * kid profiles, so it stays engaged if they are lost: the grown-up shell is only shown when this is false.
         */
        val lockEngaged: StateFlow<Boolean?> =
            keyValueService
                .get<Boolean>(LOCK_KEY)
                .map { it == true }
                .stateIn(scope, SharingStarted.Eagerly, null)

        /**
         * The device's users that have a cached kid profile, updated as the cache changes
         */
        val kidRowIds: Flow<Set<Int>> = kidProfileDao.observeUserIds().map { it.toSet() }.distinctUntilChanged()

        /**
         * A read of one user's profile, with the token it ran as and when it started
         */
        private class Lookup(
            val token: String?,
            val startedAt: Long,
            val answered: Deferred<Boolean>,
        )

        private val lookups = ConcurrentHashMap<Int, Lookup>()

        init {
            // Lock when a kid becomes the current profile, including at app start, but not when their profile is
            // refreshed, so an explicit unlock stays until the next switch to a kid. A user still being read in between
            // (Loading) counts as a switch away, so switching back to the kid locks again.
            state
                .map { (it as? KidsModeState.Active)?.user?.rowId }
                .distinctUntilChanged()
                .filterNotNull()
                .onEach { rowId ->
                    lock()
                    rememberLastKid(rowId)
                }.launchIn(scope)

            // Read the profile of each user who becomes current
            serverRepository.current
                .distinctUntilChanged { a, b -> a?.user?.rowId == b?.user?.rowId }
                .onEach { current -> if (current != null) lookUp(current) }
                .launchIn(scope)
        }

        /**
         * Read [current]'s profile from the server, sharing a read that is running for the same user and token, or
         * one that got a definite answer less than [PROFILE_REFRESH_MILLIS] ago
         *
         * @return whether the server gave a definite answer
         */
        private fun lookUp(current: CurrentUser): Deferred<Boolean> {
            val user = current.user
            return lookups
                .compute(user.rowId) { _, last ->
                    val reuse =
                        last != null &&
                            last.token == user.accessToken &&
                            (
                                // Running, or about to start
                                !last.answered.isCompleted ||
                                    (
                                        !last.answered.isCancelled &&
                                            last.answered.getCompleted() &&
                                            clock.elapsedMillis() - last.startedAt < PROFILE_REFRESH_MILLIS
                                    )
                            )
                    if (reuse) {
                        last
                    } else {
                        failedLookups.update { it - user.rowId }
                        Lookup(user.accessToken, clock.elapsedMillis(), scope.async(start = CoroutineStart.LAZY) { ask(current) })
                    }
                }!!
                .answered
                .also { it.start() }
        }

        private suspend fun ask(current: CurrentUser): Boolean {
            val answered =
                try {
                    // The plugin's state has to come from the server, not only from the cache
                    if (!pluginService.answered(current.server.id)) pluginService.check(current.server)
                    kidProfileRepository.refresh(current.server, current.user)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not read the kid profile of %s", current.user.rowId)
                    false
                }
            if (!answered) failedLookups.update { it + current.user.rowId }
            return answered
        }

        /**
         * Ask the server again whether the current user is a kid, after [unanswered]
         */
        fun retry() {
            serverRepository.current.value?.let { lookUp(it) }
        }

        /**
         * Read the current user's profile again when the app comes back after a while, so changes made in the
         * dashboard reach the kids home, and a user the server couldn't answer for is asked again
         */
        override fun onResume(owner: LifecycleOwner) {
            serverRepository.current.value?.let { lookUp(it) }
        }

        /**
         * Lock the device to kid profiles if [user] is a kid, for when a kid is picked from the user list. Picking the
         * kid who is already current doesn't change [state], so this is called explicitly.
         */
        suspend fun lockIfKid(user: JellyfinUser) {
            if (profileFor(user) != null) lock()
        }

        /**
         * After a sign-in, wait for the user's profile to be read from the server before [lockIfKid], so a kid who is
         * new on this device also locks it. It is the same read that starts when they become current.
         */
        suspend fun lockIfKidAfterSignIn(current: CurrentUser) {
            lookUp(current).await()
            lockIfKid(current.user)
        }

        private suspend fun lock() {
            keyValueService.save(LOCK_KEY, true)
        }

        /**
         * Remember the kid of users row [rowId], who just became current, as this TV's last kid, for the picker's rule
         * on a stopped kid (spec FR-012a)
         */
        private suspend fun rememberLastKid(rowId: Int) {
            val server = serverRepository.current.value?.server ?: return
            try {
                tvSetupRepository.setLastKid(server, rowId)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Could not remember the last kid")
            }
        }

        /**
         * Look up the kid profile for a user directly, without waiting for [state] to update
         */
        suspend fun profileFor(user: JellyfinUser?): KidProfile? = user?.let { kidProfileDao.get(it.rowId) }

        suspend fun kidUserRowIds(): Set<Int> = kidProfileDao.allUserIds().toSet()

        /**
         * Reads the kids lock from storage, for use before [locked] has loaded such as during app start
         */
        suspend fun isLockedNow(): Boolean {
            val flag = keyValueService.get<Boolean>(LOCK_KEY).first()
            return flag != false && kidProfileDao.allUserIds().isNotEmpty()
        }

        /**
         * Check [server] for the Kids plugin and update which of its signed-in users are kids
         */
        suspend fun refreshServer(server: JellyfinServer) {
            pluginService.check(server)
            kidProfileRepository.refreshServer(server)
        }

        /**
         * Turn kids mode off for [serverId] on this device, for a server whose plugin went away. Only call after the
         * parent passcode was verified.
         */
        suspend fun turnOffForServer(serverId: UUID) {
            pluginService.forget(serverId)
        }

        /**
         * Clears the kids lock. Only call after the parent passcode was verified.
         */
        suspend fun unlock() {
            keyValueService.save(LOCK_KEY, false)
        }

        companion object {
            private const val LOCK_KEY = "kids_lock"
            private const val PROFILE_REFRESH_MILLIS = 5 * 60 * 1000L
        }
    }
