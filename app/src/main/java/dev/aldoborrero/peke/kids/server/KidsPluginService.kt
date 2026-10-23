package dev.aldoborrero.peke.kids.server

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.timer.MonotonicClock
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether kids mode is available on a server
 */
enum class KidsServerStatus {
    /**
     * The server has the Kids plugin in a version this app speaks
     */
    AVAILABLE,

    /**
     * The server no longer offers the plugin, but this device still has kids for it: kids mode stays, from the cache
     */
    GONE,

    /**
     * No plugin and no cached kids: Peke offers only the grown-up shell
     */
    ABSENT,
}

data class KidsServerState(
    val server: KidsServer?,
    val status: KidsServerStatus,
) {
    fun has(feature: String): Boolean = server?.has(feature) == true

    /**
     * The server's plugin is too old for kids mode, see [KidsServer.outdated]
     */
    val outdated: Boolean get() = server?.outdated == true

    companion object {
        val ABSENT = KidsServerState(null, KidsServerStatus.ABSENT)

        fun of(
            server: KidsServer?,
            cachedKids: Int,
        ): KidsServerState =
            KidsServerState(
                server,
                when {
                    server?.available == true -> KidsServerStatus.AVAILABLE
                    cachedKids > 0 -> KidsServerStatus.GONE
                    else -> KidsServerStatus.ABSENT
                },
            )
    }
}

/**
 * Finds out which servers have the Jellyfin Kids plugin, with the anonymous `GET /Kids/Info`
 *
 * The answer is remembered per server. Only a definite answer changes it: a network error or a server error keeps
 * what was known, so a flaky connection can neither turn kids mode on nor off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class KidsPluginService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val kidsApi: KidsApi,
        private val kidsServerDao: KidsServerDao,
        private val kidProfileDao: KidProfileDao,
        private val clock: MonotonicClock,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) : DefaultLifecycleObserver {
        private val lastChecked = ConcurrentHashMap<UUID, Long>()

        /**
         * Kids mode on the current server
         */
        val current: StateFlow<KidsServerState> =
            serverRepository.current
                .map { it?.server?.id }
                .distinctUntilChanged()
                .flatMapLatest { serverId ->
                    if (serverId == null) {
                        flowOf(KidsServerState.ABSENT)
                    } else {
                        combine(kidsServerDao.observe(serverId), kidProfileDao.observeCountForServer(serverId)) { server, kids ->
                            KidsServerState.of(server, kids)
                        }
                    }
                }.stateIn(scope, SharingStarted.Eagerly, KidsServerState.ABSENT)

        init {
            serverRepository.current
                .map { it?.server }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .onEach { server -> if (server != null) check(server) }
                .launchIn(scope)
        }

        override fun onResume(owner: LifecycleOwner) {
            val server = serverRepository.currentServer ?: return
            val last = lastChecked[server.id]
            if (last == null || clock.elapsedMillis() - last >= RECHECK_MILLIS) {
                scope.launch { check(server) }
            }
        }

        suspend fun state(serverId: UUID): KidsServerState =
            KidsServerState.of(kidsServerDao.get(serverId), kidProfileDao.userIdsForServer(serverId).size)

        /**
         * Whether [serverId] gave a definite answer about the plugin in this run of the app, so that [state] is what
         * the server said and not only what was cached
         */
        fun answered(serverId: UUID): Boolean = lastChecked.containsKey(serverId)

        /**
         * Ask [server] whether it has the plugin, and remember a definite answer
         *
         * @return what is now known about the server
         */
        suspend fun check(server: JellyfinServer): KidsServer? {
            ask(server)
            return kidsServerDao.get(server.id)
        }

        /**
         * Ask [server] again, and whether it definitely has the plugin right now. A missing answer counts as no.
         */
        suspend fun confirmAvailable(server: JellyfinServer): Boolean = ask(server) == true

        /**
         * @return whether the server has the plugin in a version this app speaks, or null without a definite answer
         */
        private suspend fun ask(server: JellyfinServer): Boolean? {
            val result = kidsApi.info(server.url)
            val (apiVersion, features) =
                when (result) {
                    is KidsResult.Ok -> {
                        // The server's live TV guard is its own business; a degraded one is only worth a log line
                        result.value.liveTv?.takeIf { it.gate != null && it.gate != "ok" }?.let {
                            Timber.w("Kids live TV gate on %s: %s, missing %s", server.id, it.gate, it.missing)
                        }
                        result.value.apiVersion to result.value.features.joinToString(",")
                    }

                    KidsResult.NotFound -> {
                        null to ""
                    }

                    else -> {
                        Timber.d("No definite answer about the Kids plugin on %s: %s", server.id, result)
                        return null
                    }
                }
            lastChecked[server.id] = clock.elapsedMillis()
            val known = kidsServerDao.get(server.id)
            // A server that never had the plugin gets no row, so Peke stores nothing about kids
            if (known == null && result !is KidsResult.Ok) return false
            if (known == null || known.apiVersion != apiVersion || known.features != features) {
                Timber.i("Kids plugin on %s: version=%s features=%s", server.id, apiVersion, features)
            }
            // Only the plugin columns are written, so a lock cached meanwhile is not overwritten
            kidsServerDao.insertIfAbsent(KidsServer(server.id))
            kidsServerDao.updateInfo(server.id, apiVersion, features, System.currentTimeMillis())
            return KidsServer(server.id, apiVersion, features).available
        }

        /**
         * Remember the household lock for unlocking offline
         *
         * @param changedAt the lock's `ChangedAt` that [verifier] belongs to
         */
        suspend fun cacheLock(
            serverId: UUID,
            lockType: String?,
            verifier: String?,
            changedAt: String?,
        ) {
            kidsServerDao.insertIfAbsent(KidsServer(serverId))
            kidsServerDao.updateLock(serverId, lockType, verifier, changedAt)
        }

        /**
         * Forget everything about kids on [serverId]: its cached kids and lock. Only after the parent gate.
         */
        suspend fun forget(serverId: UUID) {
            kidProfileDao.deleteForServer(serverId)
            kidsServerDao.delete(serverId)
            lastChecked.remove(serverId)
        }

        companion object {
            private const val RECHECK_MILLIS = 5 * 60 * 1000L
        }
    }
