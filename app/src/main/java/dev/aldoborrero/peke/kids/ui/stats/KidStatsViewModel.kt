package dev.aldoborrero.peke.kids.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.server.KidsStats
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class KidStatsState(
    val loading: Boolean = true,
    /**
     * One per kid of this TV, in its order
     */
    val kids: List<StatsKid> = emptyList(),
    /**
     * The plugin has the stats endpoint, so the cards show the last 7 days
     */
    val statsSupported: Boolean = false,
    /**
     * Whether the household turned advanced stats on, from the stats answers; null until one came
     */
    val advanced: Boolean? = null,
    /**
     * The plugin has the stats endpoint but no kid's week came (unreachable, or the plugin failed): today only, with a
     * line that says so
     */
    val weekFailed: Boolean = false,
    /**
     * The server no longer offers the Kids plugin, or it is too old ([KidsServerStatus.GONE]): the server may answer,
     * but the plugin doesn't. The cards show what was cached, with the menu's notice instead of "can't reach the server"
     */
    val pluginGone: Boolean = false,
    /**
     * With [pluginGone], the plugin is there but too old
     */
    val pluginOutdated: Boolean = false,
    /**
     * No kid's status came from the server just now: the page shows that it can't reach it, instead of the cards
     */
    val offline: Boolean = false,
    /**
     * The stats asked for the parent code (403): the gate shows, then they load again
     */
    val needsGrant: Boolean = false,
    /**
     * The grown-up left the code: today only
     */
    val weekRefused: Boolean = false,
)

/**
 * "Stats" (spec 033 D7–D11): every kid of this TV, read only. Today comes from each kid's status, asked with their own
 * token or else the last one cached; the last 7 days from the plugin's stats with the grown-up's grant.
 */
@HiltViewModel
class KidStatsViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val tvSetupRepository: TvSetupRepository,
        private val pluginService: KidsPluginService,
        private val allowanceService: KidsAllowanceService,
        private val parentLockService: ParentLockService,
        private val kidsApi: KidsApi,
        private val kidAvatars: KidAvatars,
    ) : ViewModel() {
        private val _state = MutableStateFlow(KidStatsState())
        val state: StateFlow<KidStatsState> = _state

        private var avatars: Map<Int, PekeAvatar> = emptyMap()
        private var kids: List<JellyfinUser> = emptyList()

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val current = serverRepository.current.value
            val server = current?.server
            if (server == null) {
                _state.update { it.copy(loading = false) }
                return
            }
            val onTv = (tvSetupRepository.state.value as? TvState.SetUp)?.kids.orEmpty()
            kids = if (current.user.let { u -> onTv.any { it.rowId == u.rowId } }) onTv else listOf(current.user) + onTv
            val plugin = pluginService.state(server.id)
            val gone = plugin.status != KidsServerStatus.AVAILABLE
            val supported = !gone && plugin.has(KidsFeatures.STATS)
            _state.update {
                it.copy(
                    statsSupported = supported,
                    pluginGone = gone,
                    pluginOutdated = gone && plugin.outdated,
                    kids = kids.map { kid -> StatsKid(kid.id, kid.name, avatars[kid.rowId], watching = kid.rowId == current.user.rowId) },
                )
            }
            viewModelScope.launch {
                kidAvatars.observe(server.id).collect { found ->
                    avatars = found
                    _state.update { s ->
                        s.copy(kids = s.kids.mapIndexed { i, kid -> kid.copy(avatar = kids.getOrNull(i)?.let { found[it.rowId] }) })
                    }
                }
            }
            val today =
                kids
                    .map { kid -> viewModelScope.async { allowanceService.lastKnownStatus(server, kid) } }
                    .awaitAll()
            _state.update {
                it.copy(
                    loading = false,
                    // Without the plugin no status is ever fresh, though the server may answer: not "offline"
                    offline = !gone && today.isNotEmpty() && today.none { known -> known?.fresh == true },
                    kids =
                        it.kids.mapIndexed { i, kid ->
                            kid.copy(today = today[i]?.status?.let(KidToday::from), stale = today[i]?.fresh != true)
                        },
                )
            }
            if (supported) loadWeek()
        }

        /**
         * The last 7 days of every kid, with the grown-up's grant. Without one the code is asked once; offline, or again
         * after the code (a code checked offline gives no grant), the page keeps today only instead of asking in a loop.
         *
         * @param retry the code just passed
         */
        private suspend fun loadWeek(retry: Boolean = false) {
            val auth = parentLockService.auth()
            if (auth == null) {
                giveUpOrAsk(retry)
                return
            }
            val results =
                kids
                    .map { kid -> viewModelScope.async { kidsApi.stats(auth.server.url, auth.caller, auth.grant, kid.id, WEEK_DAYS) } }
                    .awaitAll()
            if (results.any { it == KidsResult.Forbidden }) {
                parentLockService.dropGrant()
                giveUpOrAsk(retry)
                return
            }
            val weeks: List<KidsStats?> =
                results.map { result ->
                    (result as? KidsResult.Ok)?.value.also { if (it == null) Timber.d("No stats for a kid: %s", result) }
                }
            _state.update {
                it.copy(
                    weekFailed = weeks.isNotEmpty() && weeks.all { w -> w == null },
                    advanced = weeks.firstNotNullOfOrNull { w -> w?.advancedStats } ?: it.advanced,
                    kids = it.kids.mapIndexed { i, kid -> kid.copy(week = weeks.getOrNull(i)) },
                )
            }
        }

        private fun giveUpOrAsk(retry: Boolean) {
            if (retry || _state.value.offline) {
                _state.update { it.copy(needsGrant = false, weekRefused = true) }
            } else {
                _state.update { it.copy(needsGrant = true) }
            }
        }

        /**
         * The code passed: the week again
         */
        fun retryWeek() {
            _state.update { it.copy(needsGrant = false, weekRefused = false, weekFailed = false) }
            viewModelScope.launch { loadWeek(retry = true) }
        }

        /**
         * The grown-up left the code: today only
         */
        fun dropWeek() {
            _state.update { it.copy(needsGrant = false, weekRefused = true) }
        }

        companion object {
            const val WEEK_DAYS = 7
        }
    }
