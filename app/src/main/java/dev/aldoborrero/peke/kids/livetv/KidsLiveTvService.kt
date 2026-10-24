package dev.aldoborrero.peke.kids.livetv

import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsCaller
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsItemsResult
import dev.aldoborrero.peke.kids.server.KidsLiveTvItem
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A live TV channel the kid may watch, from `GET /Kids/Users/{id}/LiveTv/Channels`
 */
data class KidsChannel(
    val id: UUID,
    val name: String?,
    val number: String?,
)

object KidsLiveTv {
    private const val TV_CHANNEL = "TvChannel"

    /**
     * The channels in [result], in the server's order. Items that are not channels or have no id are left out.
     */
    fun channels(result: KidsItemsResult): List<KidsChannel> = result.items.mapNotNull(::channel)

    private fun channel(item: KidsLiveTvItem): KidsChannel? {
        if (item.type != null && !item.type.equals(TV_CHANNEL, ignoreCase = true)) return null
        val id = item.id?.toUUIDOrNull() ?: return null
        return KidsChannel(id = id, name = item.name, number = item.channelNumber)
    }

    /**
     * The kid's channels after [result], or null when the answer says nothing (keep what was known)
     *
     * Not a kid, a plugin without the route, or a refusal all mean no live TV.
     */
    fun channelsAfter(result: KidsResult<KidsItemsResult>): List<KidsChannel>? =
        when (result) {
            is KidsResult.Ok -> channels(result.value)
            KidsResult.NotAKid, KidsResult.NotFound, KidsResult.Forbidden -> emptyList()
            else -> null
        }

    /**
     * Whether kids mode shows live TV: the plugin offers it and the kid has at least one effective channel. Without
     * channels there is no Live TV entry and no home row; the grown-ups' kids settings say where to pick them.
     */
    fun shown(
        pluginHasLiveTv: Boolean,
        channels: List<KidsChannel>,
    ): Boolean = pluginHasLiveTv && channels.isNotEmpty()
}

/**
 * The current kid's live TV channels, read from the Kids plugin when the kid becomes current or their profile changes
 * (a grown-up picks channels in the dashboard, which changes the revision)
 *
 * Live TV is optional (`liveTv`): with an older plugin, a kid simply has no live TV.
 */
@Singleton
class KidsLiveTvService
    @Inject
    constructor(
        private val kidsApi: KidsApi,
        private val serverRepository: ServerRepository,
        private val kidsModeService: KidsModeService,
        private val pluginService: KidsPluginService,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        private data class Key(
            val userRowId: Int,
            val revision: Long,
        )

        private data class Loaded(
            val key: Key,
            val channels: List<KidsChannel>,
        )

        private val mutex = Mutex()
        private val loaded = MutableStateFlow<Loaded?>(null)

        private val liveTvOffered: Boolean get() = pluginService.current.value.has(KidsFeatures.LIVE_TV)

        /**
         * Whether the current kid has live TV, see [KidsLiveTv.shown]
         */
        val available: StateFlow<Boolean> =
            combine(kidsModeService.state, pluginService.current, loaded) { state, plugin, loaded ->
                // Any revision of the current kid: while a new one can't be read, what was known holds
                val userRowId = (state as? KidsModeState.Active)?.user?.rowId
                KidsLiveTv.shown(plugin.has(KidsFeatures.LIVE_TV), loaded?.takeIf { it.key.userRowId == userRowId }?.channels.orEmpty())
            }.distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, false)

        init {
            combine(kidsModeService.state, pluginService.current) { state, plugin ->
                (state as? KidsModeState.Active)?.takeIf { plugin.has(KidsFeatures.LIVE_TV) }
            }.distinctUntilChanged { a, b -> a?.user?.rowId == b?.user?.rowId && a?.profile?.revision == b?.profile?.revision }
                .onEach { active -> if (active != null) channelsFor(active.user, active.profile) }
                .launchIn(scope)
        }

        /**
         * The live TV channels of kid [user], read once per profile revision. Empty when the plugin has no live TV,
         * the kid has none, or [user] is not the current user.
         */
        suspend fun channelsFor(
            user: JellyfinUser,
            profile: KidProfile,
        ): List<KidsChannel> {
            if (!liveTvOffered) return emptyList()
            val key = Key(user.rowId, profile.revision)
            return mutex.withLock {
                loaded.value?.takeIf { it.key == key }?.let { return@withLock it.channels }
                val previous = loaded.value?.takeIf { it.key.userRowId == user.rowId }?.channels
                val fetched = fetch(user)
                if (fetched != null) {
                    loaded.value = Loaded(key, fetched)
                    fetched
                } else {
                    // Unknown: keep what was known for this kid, and ask again next time
                    previous.orEmpty()
                }
            }
        }

        private suspend fun fetch(user: JellyfinUser): List<KidsChannel>? {
            val current = serverRepository.current.value ?: return null
            if (current.user.rowId != user.rowId) return null
            val token = current.user.accessToken ?: return null
            val result = kidsApi.liveTvChannels(current.server.url, KidsCaller(token, current.user.id), current.user.id)
            return KidsLiveTv.channelsAfter(result).also {
                if (it == null) Timber.d("Keeping the kid's live TV channels: %s", result)
            }
        }
    }
