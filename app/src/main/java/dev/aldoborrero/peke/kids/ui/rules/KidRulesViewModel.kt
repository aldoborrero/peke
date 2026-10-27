package dev.aldoborrero.peke.kids.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.data.SaveResult
import dev.aldoborrero.peke.kids.livetv.KidsLiveTvService
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.setup.VoiceSearchSetting
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.AccessSchedule
import org.jellyfin.sdk.model.api.UserPolicy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * What Jellyfin decides for the kid, for display only: it is changed in the Jellyfin dashboard
 */
data class PolicySummary(
    val maxParentalRating: Int?,
    val blocksUnrated: Boolean,
    val allLibraries: Boolean,
    val libraryCount: Int,
    val liveTvAccess: Boolean,
    val schedules: List<AccessSchedule>,
) {
    companion object {
        fun from(policy: UserPolicy): PolicySummary =
            PolicySummary(
                maxParentalRating = policy.maxParentalRating,
                blocksUnrated = !policy.blockUnratedItems.isNullOrEmpty(),
                allLibraries = policy.enableAllFolders == true,
                libraryCount = policy.enabledFolders?.size ?: 0,
                liveTvAccess = policy.enableLiveTvAccess == true,
                schedules = policy.accessSchedules.orEmpty(),
            )
    }
}

/**
 * A daily time chosen while the grant had run out, saved once the code passes again (F-K7)
 *
 * @param minutes null for unlimited
 */
data class PendingDailyTime(
    val minutes: Int?,
)

data class KidRulesState(
    val loading: Boolean = true,
    val kidId: UUID? = null,
    val kidName: String? = null,
    val profile: KidProfile? = null,
    /**
     * The plugin has daily allowances, so Daily time can be changed
     */
    val allowanceSupported: Boolean = false,
    val sessionLimitSupported: Boolean = false,
    val liveTvSupported: Boolean = false,
    /**
     * Today's use and allowance with bonus, in minutes, if the status is known
     */
    val usedMinutes: Int? = null,
    val todayMinutes: Int? = null,
    /**
     * The household's break after a sitting, from the status, if known
     */
    val breakMinutes: Int? = null,
    val policy: PolicySummary? = null,
    val channels: List<String> = emptyList(),
    val pickNames: List<String> = emptyList(),
    /**
     * Voice search is turned on for this TV (the microphone permission is checked on screen)
     */
    val voiceSearch: Boolean = false,
    val dashboardUrl: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val pending: PendingDailyTime? = null,
    /**
     * Why the last save failed, a string resource
     */
    val error: Int? = null,
)

/**
 * "{Kid}'s rules" (spec 006 US3): the current kid's daily time, the only rule changed on the TV, and the rest read
 * only, with where to change it. Everything comes from the cache, the last status and the kid's Jellyfin policy, plus
 * the names of their channels and picks.
 */
@HiltViewModel
class KidRulesViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val kidProfileDao: KidProfileDao,
        private val kidProfileRepository: KidProfileRepository,
        private val pluginService: KidsPluginService,
        private val allowanceService: KidsAllowanceService,
        private val liveTvService: KidsLiveTvService,
        private val voiceSearchSetting: VoiceSearchSetting,
        private val api: ApiClient,
    ) : ViewModel() {
        private val _state = MutableStateFlow(KidRulesState())
        val state: StateFlow<KidRulesState> = _state

        private val saveMutex = Mutex()
        private var savedJob: Job? = null

        private val user: JellyfinUser? get() = serverRepository.current.value?.user

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch { voiceSearchSetting.on.collect { on -> _state.update { it.copy(voiceSearch = on) } } }
            viewModelScope.launch {
                allowanceService.snapshot.collect { snapshot ->
                    val status = snapshot?.status
                    _state.update {
                        it.copy(
                            usedMinutes = status?.let { s -> (s.usedSeconds / 60).toInt() },
                            todayMinutes = status?.allowanceSeconds?.let { a -> ((a + status.bonusSeconds) / 60).toInt() },
                            breakMinutes = status?.breakSeconds?.let { b -> (b / 60).toInt() },
                        )
                    }
                }
            }
        }

        private suspend fun load() {
            val current = serverRepository.current.value
            val kid = current?.user
            val profile = kid?.let { kidProfileDao.get(it.rowId) }
            val plugin = current?.let { pluginService.state(it.server.id) }
            _state.update {
                it.copy(
                    loading = false,
                    kidId = kid?.id,
                    kidName = kid?.name,
                    profile = profile,
                    allowanceSupported = plugin?.has(KidsFeatures.ALLOWANCE) == true,
                    sessionLimitSupported =
                        plugin?.has(KidsFeatures.SESSION_LIMIT) == true ||
                            profile?.let(KidProfileMapper::hasSessionLimitField) == true,
                    liveTvSupported = plugin?.has(KidsFeatures.LIVE_TV) == true,
                    policy = serverRepository.currentUserDto?.policy?.let(PolicySummary::from),
                    dashboardUrl = current?.server?.url?.let(::dashboardUrl),
                )
            }
            if (kid == null || profile == null) return
            val channels = liveTvService.channelsFor(kid, profile).mapNotNull { it.name?.takeIf(String::isNotBlank) }
            _state.update { it.copy(channels = channels) }
            _state.update { it.copy(pickNames = pickNames(kid, profile.pickedItemIds)) }
        }

        private suspend fun pickNames(
            kid: JellyfinUser,
            ids: List<UUID>,
        ): List<String> {
            if (ids.isEmpty()) return emptyList()
            return try {
                val names =
                    api.itemsApi
                        .getItems(GetItemsRequest(userId = kid.id, ids = ids))
                        .content.items
                        .associate { it.id to it.name.orEmpty() }
                ids.mapNotNull { names[it]?.takeIf(String::isNotBlank) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not get the names of picks: %s", ex.javaClass.simpleName)
                emptyList()
            }
        }

        /**
         * Save the kid's daily time, [minutes] or null for unlimited, from the profile as last saved. A grant that ran
         * out keeps the value for [retryPending] after the code.
         */
        fun setDailyTime(minutes: Int?) {
            val kid = user ?: return
            viewModelScope.launch {
                saveMutex.withLock {
                    _state.update { it.copy(saving = true, saved = false, error = null, pending = null) }
                    val latest = kidProfileDao.get(kid.rowId)
                    if (latest == null) {
                        _state.update { it.copy(saving = false, error = R.string.kids_save_failed) }
                        return@withLock
                    }
                    if (latest.dailyAllowanceMinutes == minutes) {
                        _state.update { it.copy(saving = false, profile = latest) }
                        return@withLock
                    }
                    val result = kidProfileRepository.save(kid, latest.copy(dailyAllowanceMinutes = minutes))
                    val profile = kidProfileDao.get(kid.rowId) ?: latest
                    _state.update { it.copy(saving = false, profile = profile) }
                    handle(result, minutes)
                }
            }
        }

        private fun handle(
            result: SaveResult,
            minutes: Int?,
        ) {
            when (result) {
                SaveResult.SAVED -> {
                    allowanceService.refresh()
                    _state.update { it.copy(saved = true) }
                    savedJob?.cancel()
                    savedJob =
                        viewModelScope.launch {
                            delay(SAVED_MILLIS)
                            _state.update { it.copy(saved = false) }
                        }
                }

                SaveResult.NEEDS_GRANT -> {
                    _state.update { it.copy(pending = PendingDailyTime(minutes)) }
                }

                SaveResult.CONFLICT -> {
                    _state.update { it.copy(error = R.string.kids_changed_elsewhere) }
                }

                SaveResult.UNREACHABLE -> {
                    _state.update { it.copy(error = R.string.kids_cant_reach_server) }
                }

                SaveResult.FAILED -> {
                    _state.update { it.copy(error = R.string.kids_save_failed) }
                }
            }
        }

        /**
         * The code passed again: save the value that waited for it
         */
        fun retryPending() {
            val pending = _state.value.pending ?: return
            _state.update { it.copy(pending = null) }
            setDailyTime(pending.minutes)
        }

        /**
         * The grown-up left the code: the value is dropped, and the page says it wasn't saved
         */
        fun dropPending() {
            _state.update { it.copy(pending = null, error = R.string.kids_not_saved) }
        }

        companion object {
            val DAILY_TIME_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90, 120, 180, 240)
            private const val SAVED_MILLIS = 3_000L

            /**
             * The daily time choices: [DAILY_TIME_OPTIONS] with [current] in order when it isn't one of them, then
             * unlimited (null) last, so a hurried OK doesn't remove the limit (F-K3)
             */
            fun dailyTimeOptions(current: Int?): List<Int?> = ((DAILY_TIME_OPTIONS + listOfNotNull(current)).distinct().sorted()) + null

            /**
             * The Kids plugin's page in the server's dashboard
             */
            fun dashboardUrl(serverUrl: String): String = serverUrl.trimEnd('/') + "/web/#/configurationpage?name=Kids"
        }
    }
