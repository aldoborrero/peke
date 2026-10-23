package dev.aldoborrero.peke.kids

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.timer.MonotonicClock
import dev.aldoborrero.peke.services.KeyValueService
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface KidsModeState {
    /**
     * Not known yet whether the current user is a kid, so nothing that differs for kids is shown
     */
    data object Loading : KidsModeState

    data object Inactive : KidsModeState

    data class Active(
        val profile: KidProfile,
        val user: JellyfinUser,
    ) : KidsModeState
}

val KidsModeState.isActive: Boolean get() = this is KidsModeState.Active

val KidsModeState.profile: KidProfile? get() = (this as? KidsModeState.Active)?.profile

/**
 * Tracks whether the current user is a kid profile, and the device's kids lock
 *
 * Who is a kid comes from the Jellyfin Kids plugin, through the [KidProfileRepository] cache. The kids lock is set
 * when a kid becomes the current profile (also at app start) and is only cleared by [unlock], which callers must only
 * invoke after the parent passcode was verified. Profile refreshes don't set it again. While locked, only kid
 * profiles may be selected.
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
                        kidProfileDao
                            .observe(user.rowId)
                            .map<KidProfile?, KidsModeState> { profile ->
                                if (profile != null) KidsModeState.Active(profile, user) else KidsModeState.Inactive
                            }
                            // Until the new user's profile is read, the previous user's state must not show
                            .onStart { emit(KidsModeState.Loading) }
                    }
                }.stateIn(scope, SharingStarted.Eagerly, KidsModeState.Loading)

        val isActive: Boolean get() = state.value.isActive

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

        @Volatile
        private var lastProfileRefresh: Long? = null

        init {
            // Lock when a kid becomes the current profile, including at app start, but not when their profile is
            // refreshed, so an explicit unlock stays until the next switch to a kid
            state
                .filter { it != KidsModeState.Loading }
                .map { (it as? KidsModeState.Active)?.user?.rowId }
                .distinctUntilChanged()
                .filterNotNull()
                .onEach { lock() }
                .launchIn(scope)

            // Read the current user's profile from the server once kids mode is known to be available there
            combine(serverRepository.current, pluginService.current) { current, plugin ->
                current?.takeIf { plugin.status == KidsServerStatus.AVAILABLE }
            }.distinctUntilChanged { a, b -> a?.user?.rowId == b?.user?.rowId }
                .onEach { current ->
                    if (current != null) {
                        lastProfileRefresh = clock.elapsedMillis()
                        kidProfileRepository.refresh(current.server, current.user)
                    }
                }.launchIn(scope)
        }

        /**
         * Read the current kid's profile again when the app comes back after a while, so changes made in the
         * dashboard reach the kids home
         */
        override fun onResume(owner: LifecycleOwner) {
            val current = serverRepository.current.value ?: return
            if (!isActive || pluginService.current.value.status != KidsServerStatus.AVAILABLE) return
            val last = lastProfileRefresh
            if (last != null && clock.elapsedMillis() - last < PROFILE_REFRESH_MILLIS) return
            lastProfileRefresh = clock.elapsedMillis()
            scope.launch { kidProfileRepository.refresh(current.server, current.user) }
        }

        /**
         * Lock the device to kid profiles if [user] is a kid, for when a kid is picked from the user list. Picking the
         * kid who is already current doesn't change [state], so this is called explicitly.
         */
        suspend fun lockIfKid(user: JellyfinUser) {
            if (profileFor(user) != null) lock()
        }

        private suspend fun lock() {
            keyValueService.save(LOCK_KEY, true)
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
