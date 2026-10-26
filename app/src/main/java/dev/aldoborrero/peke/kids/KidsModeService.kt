package dev.aldoborrero.peke.kids

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.timer.MonotonicClock
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber
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
 * Tracks whether the current user is a kid profile
 *
 * Who is a kid comes from the Jellyfin Kids plugin, through the [KidProfileRepository] cache. Whether this TV is in
 * kids mode is [TvSetupRepository.state]'s alone: a set-up TV has no unlocked state (spec 004 FR-016), and only
 * resetting it leaves kids mode.
 *
 * Each time a user becomes current their profile is read from the server once ([lookUp]); coming back to the app
 * shares that read instead of making its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class KidsModeService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val kidProfileDao: KidProfileDao,
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
         * plugin not known), while [state] is still [KidsModeState.Loading]. [onResume] asks again.
         */
        val unanswered: StateFlow<Boolean> =
            combine(state, serverRepository.current, failedLookups) { state, current, failed ->
                state == KidsModeState.Loading && current != null && current.user.rowId in failed
            }.distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, false)

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
            // Remember each kid who becomes the current profile, including at app start. A user still being read in
            // between (Loading) counts as a switch away, so switching back to the kid is remembered again.
            state
                .map { (it as? KidsModeState.Active)?.user?.rowId }
                .distinctUntilChanged()
                .filterNotNull()
                .onEach { rowId -> rememberLastKid(rowId) }
                .launchIn(scope)

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
         * Read the current user's profile again when the app comes back after a while, so changes made in the
         * dashboard reach the kids home, and a user the server couldn't answer for is asked again
         */
        override fun onResume(owner: LifecycleOwner) {
            serverRepository.current.value?.let { lookUp(it) }
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

        companion object {
            private const val PROFILE_REFRESH_MILLIS = 5 * 60 * 1000L
        }
    }
