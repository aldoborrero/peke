package dev.aldoborrero.peke.kids.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.data.SaveResult
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

/**
 * A kid of this TV, for the kid switch in the header (spec 033 D5)
 */
data class RulesKid(
    val id: UUID,
    val name: String?,
    val avatar: PekeAvatar? = null,
)

/**
 * A rule a grown-up changes on the TV (spec 030): applied to the profile as last saved, so every other field goes back
 * to the server unchanged
 */
sealed interface RuleChange {
    fun apply(profile: KidProfile): KidProfile

    /**
     * @param minutes null for unlimited
     */
    data class DailyTime(
        val minutes: Int?,
    ) : RuleChange {
        override fun apply(profile: KidProfile) = profile.copy(dailyAllowanceMinutes = minutes)
    }

    /**
     * Time in a row before the break, only with the plugin's sitting limit
     *
     * @param minutes null for no limit
     */
    data class SittingLimit(
        val minutes: Int?,
    ) : RuleChange {
        override fun apply(profile: KidProfile) = profile.copy(sessionLimitMinutes = minutes)
    }

    /**
     * @param cap episodes in a row, held to 1–10, [KidProfile.UNLIMITED_AUTOPLAY_CAP] for no limit, or null for off
     * (the cap is kept for when it's on again)
     */
    data class Autoplay(
        val cap: Int?,
    ) : RuleChange {
        override fun apply(profile: KidProfile) =
            if (cap == null) {
                profile.copy(autoplayEnabled = false)
            } else if (cap == KidProfile.UNLIMITED_AUTOPLAY_CAP) {
                profile.copy(autoplayEnabled = true, autoplayCap = KidProfile.UNLIMITED_AUTOPLAY_CAP)
            } else {
                profile.copy(
                    autoplayEnabled = true,
                    autoplayCap = cap.coerceIn(KidProfile.MIN_AUTOPLAY_CAP, KidProfile.MAX_AUTOPLAY_CAP),
                )
            }
    }

    data class Search(
        val allowed: Boolean,
    ) : RuleChange {
        override fun apply(profile: KidProfile) = profile.copy(searchAllowed = allowed)
    }
}

data class KidRulesState(
    val loading: Boolean = true,
    /**
     * The kid whose rules are shown: the kid watching, or the one picked in the header
     */
    val kidId: UUID? = null,
    val kidName: String? = null,
    /**
     * This TV's kids, in its order; the switch shows with two or more
     */
    val kids: List<RulesKid> = emptyList(),
    val profile: KidProfile? = null,
    /**
     * The plugin has daily allowances, so Daily time can be changed
     */
    val allowanceSupported: Boolean = false,
    /**
     * The plugin advertises the sitting limit, so Breaks can be changed
     */
    val sessionLimitEditable: Boolean = false,
    /**
     * The plugin takes autoplay with no limit, so Autoplay offers it last
     */
    val unlimitedAutoplay: Boolean = false,
    /**
     * The household's break after a sitting, from the status, if known
     */
    val breakMinutes: Int? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /**
     * A change the grant ran out for, saved once the code passes again (F-K7)
     */
    val pending: RuleChange? = null,
    /**
     * Why the last save failed, a string resource
     */
    val error: Int? = null,
)

/**
 * "Rules" (spec 006 US3, spec 030, spec 033): a kid's daily time, breaks, autoplay and search changed on the TV, and
 * nothing else. It opens on the kid watching; with two kids or more the header switches to another kid of this TV,
 * whose profile comes from the cache and is refreshed with their own token. Saves use the grown-up's grant for the kid
 * shown.
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
        private val tvSetupRepository: TvSetupRepository,
        private val kidAvatars: KidAvatars,
    ) : ViewModel() {
        private val _state = MutableStateFlow(KidRulesState())
        val state: StateFlow<KidRulesState> = _state

        private val saveMutex = Mutex()
        private var savedJob: Job? = null
        private var refreshJob: Job? = null

        private var server: JellyfinServer? = null
        private var kids: List<JellyfinUser> = emptyList()

        /**
         * The kid whose rules are shown
         */
        private var shown: JellyfinUser? = null

        /**
         * The kid [KidRulesState.pending] is for
         */
        private var pendingKid: JellyfinUser? = null

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch {
                allowanceService.snapshot.collect { snapshot ->
                    val breakSeconds = snapshot?.status?.breakSeconds
                    _state.update { it.copy(breakMinutes = breakSeconds?.let { b -> (b / 60).toInt() }) }
                }
            }
        }

        private suspend fun load() {
            val current = serverRepository.current.value
            val kid = current?.user
            server = current?.server
            val onTv = (tvSetupRepository.state.value as? TvState.SetUp)?.kids.orEmpty()
            kids = if (kid == null || onTv.any { it.rowId == kid.rowId }) onTv else listOf(kid) + onTv
            shown = kid
            val profile = kid?.let { kidProfileDao.get(it.rowId) }
            val plugin = current?.let { pluginService.state(it.server.id) }
            _state.update {
                it.copy(
                    loading = false,
                    kidId = kid?.id,
                    kidName = kid?.name,
                    kids = kids.map { k -> RulesKid(k.id, k.name) },
                    profile = profile,
                    allowanceSupported = plugin?.has(KidsFeatures.ALLOWANCE) == true,
                    sessionLimitEditable = plugin?.has(KidsFeatures.SESSION_LIMIT) == true,
                    unlimitedAutoplay = plugin?.has(KidsFeatures.UNLIMITED_AUTOPLAY) == true,
                )
            }
            val serverId = current?.server?.id ?: return
            kidAvatars.observe(serverId).collect { avatars ->
                _state.update { it.copy(kids = kids.map { k -> RulesKid(k.id, k.name, avatars[k.rowId]) }) }
            }
        }

        /**
         * Show the rules of the kid of this TV with [id]: their cached profile at once, then the server's
         */
        fun select(id: UUID) {
            val kid = kids.firstOrNull { it.id == id } ?: return
            if (kid.rowId == shown?.rowId) return
            shown = kid
            refreshJob?.cancel()
            savedJob?.cancel()
            refreshJob =
                viewModelScope.launch {
                    _state.update {
                        it.copy(kidId = kid.id, kidName = kid.name, profile = kidProfileDao.get(kid.rowId), saved = false, error = null)
                    }
                    val server = server ?: return@launch
                    kidProfileRepository.refresh(server, kid)
                    if (shown?.rowId == kid.rowId) {
                        val fresh = kidProfileDao.get(kid.rowId)
                        _state.update { it.copy(profile = fresh) }
                    }
                }
        }

        /**
         * Save [change] on the shown kid's profile as last saved. A grant that ran out keeps the change for
         * [retryPending] after the code.
         */
        fun save(change: RuleChange) {
            shown?.let { save(it, change) }
        }

        private fun save(
            kid: JellyfinUser,
            change: RuleChange,
        ) {
            // Without the plugin's sitting limit the PUT wouldn't carry it
            if (change is RuleChange.SittingLimit && !_state.value.sessionLimitEditable) return
            // An older plugin refuses a cap of 0
            if (change == RuleChange.Autoplay(KidProfile.UNLIMITED_AUTOPLAY_CAP) && !_state.value.unlimitedAutoplay) return
            viewModelScope.launch {
                saveMutex.withLock {
                    _state.update { it.copy(saving = true, saved = false, error = null, pending = null) }
                    val latest = kidProfileDao.get(kid.rowId)
                    if (latest == null) {
                        _state.update { it.copy(saving = false, error = R.string.kids_save_failed) }
                        return@withLock
                    }
                    val changed = change.apply(latest)
                    if (changed == latest) {
                        showProfile(kid, latest)
                        _state.update { it.copy(saving = false) }
                        return@withLock
                    }
                    val result = kidProfileRepository.save(kid, changed)
                    showProfile(kid, kidProfileDao.get(kid.rowId) ?: latest)
                    _state.update { it.copy(saving = false) }
                    handle(result, kid, change)
                }
            }
        }

        /**
         * [profile] on the page, if [kid] is still the one shown
         */
        private fun showProfile(
            kid: JellyfinUser,
            profile: KidProfile,
        ) {
            if (shown?.rowId == kid.rowId) _state.update { it.copy(profile = profile) }
        }

        private fun handle(
            result: SaveResult,
            kid: JellyfinUser,
            change: RuleChange,
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
                    pendingKid = kid
                    _state.update { it.copy(pending = change) }
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
         * The code passed again: save the value that waited for it, for the kid it was made for
         */
        fun retryPending() {
            val pending = _state.value.pending ?: return
            val kid = pendingKid ?: shown ?: return
            _state.update { it.copy(pending = null) }
            pendingKid = null
            save(kid, pending)
        }

        /**
         * The grown-up left the code: the value is dropped, and the page says it wasn't saved
         */
        fun dropPending() {
            pendingKid = null
            _state.update { it.copy(pending = null, error = R.string.kids_not_saved) }
        }

        companion object {
            val DAILY_TIME_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90, 120, 180, 240)
            private const val SAVED_MILLIS = 3_000L

            /**
             * The daily time choices: [DAILY_TIME_OPTIONS] with [current] in order when it isn't one of them, then
             * no limit (null) last, so a hurried OK doesn't remove the limit (F-K3)
             */
            fun dailyTimeOptions(current: Int?): List<Int?> = withCurrent(DAILY_TIME_OPTIONS, current) + null

            val SITTING_OPTIONS = listOf(10, 15, 20, 30, 45, 60, 90, 120)

            /**
             * The breaks choices, as [dailyTimeOptions]: [SITTING_OPTIONS] with [current] in order, then no limit (null)
             * last
             */
            fun sittingOptions(current: Int?): List<Int?> = withCurrent(SITTING_OPTIONS, current) + null

            /**
             * The autoplay choices: off (null), then 1 to 10 episodes in a row
             */
            val AUTOPLAY_OPTIONS: List<Int?> = listOf<Int?>(null) + (KidProfile.MIN_AUTOPLAY_CAP..KidProfile.MAX_AUTOPLAY_CAP)

            /**
             * [AUTOPLAY_OPTIONS], then no limit ([KidProfile.UNLIMITED_AUTOPLAY_CAP]) last when the plugin takes it, or
             * when it is the saved value
             */
            fun autoplayOptions(
                unlimited: Boolean,
                current: Int?,
            ): List<Int?> =
                if (unlimited || current == KidProfile.UNLIMITED_AUTOPLAY_CAP) {
                    AUTOPLAY_OPTIONS + KidProfile.UNLIMITED_AUTOPLAY_CAP
                } else {
                    AUTOPLAY_OPTIONS
                }

            /**
             * The search choices: not allowed, then allowed
             */
            val SEARCH_OPTIONS = listOf(false, true)

            private fun withCurrent(
                options: List<Int>,
                current: Int?,
            ): List<Int> = (options + listOfNotNull(current)).distinct().sorted()
        }
    }
