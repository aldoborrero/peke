package dev.aldoborrero.peke.kids.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.data.KidsHomeSection
import dev.aldoborrero.peke.kids.data.KidsHomeSectionEntry
import dev.aldoborrero.peke.kids.data.SaveResult
import dev.aldoborrero.peke.kids.gate.LockChange
import dev.aldoborrero.peke.kids.gate.LockState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.VerifyOutcome
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.timer.KidsTimerBarSetting
import dev.aldoborrero.peke.services.HomeSettingsService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.UserPolicy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class KidUserRow(
    val user: JellyfinUser,
    val profile: KidProfile?,
)

/**
 * What the current user's server policy restricts, for display only
 */
data class PolicySummary(
    val maxParentalRating: Int?,
    val blocksUnrated: Boolean,
    val allLibraries: Boolean,
    val libraryCount: Int,
    val hasSchedule: Boolean,
) {
    val unrestricted: Boolean get() = maxParentalRating == null && !blocksUnrated

    companion object {
        fun from(policy: UserPolicy): PolicySummary =
            PolicySummary(
                maxParentalRating = policy.maxParentalRating,
                blocksUnrated = !policy.blockUnratedItems.isNullOrEmpty(),
                allLibraries = policy.enableAllFolders == true,
                libraryCount = policy.enabledFolders?.size ?: 0,
                hasSchedule = !policy.accessSchedules.isNullOrEmpty(),
            )
    }
}

/**
 * A short message for the grown-up: a string, or a plural with [count]
 */
data class KidsMessage(
    val id: Int,
    val count: Int? = null,
)

data class KidsSettingsState(
    val loading: Boolean = true,
    /**
     * The household lock, null while loading
     */
    val lock: LockState? = null,
    val verified: Boolean = false,
    /**
     * Whether the current user is a server administrator, who sets the first household code
     */
    val isAdmin: Boolean = false,
    val users: List<KidUserRow> = emptyList(),
    val pickedNames: Map<UUID, String> = emptyMap(),
    // Only known when the current user is a kid profile, since policies of other users can't be read
    val currentPolicy: PolicySummary? = null,
    val allowanceSupported: Boolean = false,
    val sessionLimitSupported: Boolean = false,
    /**
     * The plugin has kids live TV; channels are picked on its dashboard page
     */
    val liveTvSupported: Boolean = false,
    /**
     * The server's Kids plugin is too old for kids mode: only a message to update it is shown
     */
    val pluginOutdated: Boolean = false,
) {
    val lockType: ParentLockType? get() = (lock as? LockState.Code)?.type

    /**
     * Without a household code only an administrator can set kids mode up
     */
    val needsAdmin: Boolean get() = lock == LockState.NotSet && !isAdmin
}

@HiltViewModel
class KidsSettingsViewModel
    @Inject
    constructor(
        private val kidProfileDao: KidProfileDao,
        private val kidProfileRepository: KidProfileRepository,
        private val parentLockService: ParentLockService,
        private val pluginService: KidsPluginService,
        private val serverRepository: ServerRepository,
        private val homeSettingsService: HomeSettingsService,
        private val api: ApiClient,
        private val timerBarSetting: KidsTimerBarSetting,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(KidsSettingsState())
        val state: StateFlow<KidsSettingsState> = _state

        private val _messages = MutableSharedFlow<KidsMessage>(extraBufferCapacity = 4)

        /**
         * Short messages for the grown-up
         */
        val messages: SharedFlow<KidsMessage> = _messages

        val timerBarPosition = timerBarSetting.position

        fun setTimerBarPosition(position: KidsTimerBarPosition) {
            viewModelScope.launch { timerBarSetting.set(position) }
        }

        init {
            val alreadyVerified = parentLockService.consumeSettingsGrant()
            viewModelScope.launch {
                val outdated = serverRepository.currentServer?.let { pluginService.state(it.id).outdated } == true
                val lock = parentLockService.lockState()
                val isAdmin = parentLockService.isAdmin()
                _state.update {
                    it.copy(
                        pluginOutdated = outdated,
                        lock = lock,
                        isAdmin = isAdmin,
                        // An administrator sets the first code, which needs no other check
                        verified = alreadyVerified || (lock == LockState.NotSet && isAdmin),
                    )
                }
                serverRepository.current.value?.let { kidProfileRepository.refreshServer(it.server, it.user) }
                refresh()
            }
        }

        private fun message(
            @StringRes id: Int,
        ) {
            _messages.tryEmit(KidsMessage(id))
        }

        /**
         * Check the household code
         *
         * @return whether it was right
         */
        suspend fun verify(input: String): Boolean =
            when (val outcome = parentLockService.verify(input)) {
                VerifyOutcome.Valid, VerifyOutcome.ValidOffline -> {
                    _state.update { it.copy(verified = true) }
                    true
                }

                is VerifyOutcome.Throttled -> {
                    _messages.tryEmit(KidsMessage(R.plurals.kids_gate_wait, outcome.seconds))
                    false
                }

                VerifyOutcome.CannotCheck -> {
                    message(R.string.kids_gate_cannot_check)
                    false
                }

                VerifyOutcome.Wrong -> {
                    false
                }
            }

        /**
         * Set or change the household code
         *
         * @param current the current code, needed unless the current user is an administrator
         */
        suspend fun setPasscode(
            newPasscode: String,
            type: ParentLockType,
            current: String?,
        ): Boolean =
            when (val change = parentLockService.setCode(type, newPasscode, current)) {
                LockChange.Done -> {
                    _state.update { it.copy(lock = LockState.Code(type), verified = true) }
                    true
                }

                LockChange.WrongCurrent -> {
                    message(R.string.kids_gate_wrong_passcode)
                    false
                }

                is LockChange.Throttled -> {
                    _messages.tryEmit(KidsMessage(R.plurals.kids_gate_wait, change.seconds))
                    false
                }

                LockChange.NeedsGrant -> {
                    needsGrant()
                    false
                }

                LockChange.Unreachable -> {
                    message(R.string.kids_cant_reach_server)
                    false
                }

                LockChange.Invalid -> {
                    message(R.string.kids_save_failed)
                    false
                }
            }

        private fun needsGrant() {
            _state.update { it.copy(verified = it.lock == LockState.NotSet && it.isAdmin) }
        }

        fun setKid(
            user: JellyfinUser,
            isKid: Boolean,
        ) {
            viewModelScope.launch {
                handle(if (isKid) kidProfileRepository.mark(user) else kidProfileRepository.unmark(user))
                refresh()
            }
        }

        /**
         * Apply [change] to the kid's profile and save it. Changes are saved one at a time, each made to the profile
         * as last saved, so quick edits don't send an old revision or undo each other.
         */
        fun update(
            userId: Int,
            change: (KidProfile) -> KidProfile,
        ) {
            val user =
                _state.value.users
                    .firstOrNull { it.user.rowId == userId }
                    ?.user ?: return
            viewModelScope.launch {
                val saved =
                    editMutex.withLock {
                        val latest = kidProfileDao.get(userId) ?: return@withLock false
                        val changed = change(latest)
                        if (changed == latest) return@withLock false
                        handle(kidProfileRepository.save(user, changed))
                        true
                    }
                refresh()
                // Rebuild the kids home right away if it is the current user
                if (saved) {
                    serverRepository.currentUser?.takeIf { it.rowId == userId }?.let {
                        homeSettingsService.loadCurrentSettings(it.id)
                    }
                }
            }
        }

        private val editMutex = Mutex()

        private fun handle(result: SaveResult) {
            when (result) {
                SaveResult.SAVED -> Unit
                SaveResult.CONFLICT -> message(R.string.kids_changed_elsewhere)
                SaveResult.NEEDS_GRANT -> needsGrant()
                SaveResult.UNREACHABLE -> message(R.string.kids_cant_reach_server)
                SaveResult.FAILED -> message(R.string.kids_save_failed)
            }
        }

        fun addPick(
            profile: KidProfile,
            item: BaseItem,
        ) {
            _state.update { it.copy(pickedNames = it.pickedNames + (item.id to (item.name ?: ""))) }
            update(profile.userId) { latest ->
                if (item.id in latest.pickedItemIds) latest else latest.copy(pickedItemIds = latest.pickedItemIds + item.id)
            }
        }

        fun removePick(
            profile: KidProfile,
            itemId: UUID,
        ) = update(profile.userId) { it.copy(pickedItemIds = it.pickedItemIds - itemId) }

        /**
         * Move the section shown at [index] by [delta]. The section is found again in the latest profile, so an edit
         * still being saved doesn't make it move another one.
         */
        fun moveSection(
            profile: KidProfile,
            index: Int,
            delta: Int,
        ) {
            val section = profile.sections.getOrNull(index)?.section ?: return
            update(profile.userId) { latest -> latest.copy(sections = moveSection(latest.sections, section, delta)) }
        }

        fun toggleSection(
            profile: KidProfile,
            index: Int,
        ) {
            val section = profile.sections.getOrNull(index)?.section ?: return
            update(profile.userId) { latest ->
                val sections =
                    latest.sections.map { entry ->
                        if (entry.section == section) entry.copy(visible = !entry.visible) else entry
                    }
                latest.copy(sections = KidsHomeSectionEntry.normalize(sections))
            }
        }

        private suspend fun refresh() = withContext(PekeDispatchers.IO) { refreshInternal() }

        private suspend fun refreshInternal() {
            val server = serverRepository.currentServer
            val plugin = server?.let { pluginService.state(it.id) }
            val users =
                server
                    ?.let { serverRepository.serverDao.getServer(it.id)?.users }
                    .orEmpty()
                    .filter { it.accessToken != null }
                    .map { KidUserRow(it, kidProfileDao.get(it.rowId)) }
            val current = serverRepository.currentUser
            val currentIsKid = users.any { it.user.rowId == current?.rowId && it.profile != null }
            val policy =
                if (currentIsKid) serverRepository.currentUserDto?.policy?.let(PolicySummary::from) else null
            val missingNames =
                users
                    .flatMap { it.profile?.pickedItemIds.orEmpty() }
                    .filter { it !in _state.value.pickedNames }
                    .distinct()
            val names =
                if (missingNames.isEmpty()) {
                    emptyMap()
                } else {
                    try {
                        api.itemsApi
                            .getItems(GetItemsRequest(userId = current?.id, ids = missingNames))
                            .content.items
                            .associate { it.id to (it.name ?: "") }
                            // Items that no longer exist are not asked for again
                            .let { found -> missingNames.associateWith { "" } + found }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not get names of picked items")
                        emptyMap()
                    }
                }
            val sessionLimit =
                plugin?.has(KidsFeatures.SESSION_LIMIT) == true ||
                    users.any { row -> row.profile?.let(KidProfileMapper::hasSessionLimitField) == true }
            _state.update {
                it.copy(
                    loading = false,
                    users = users,
                    currentPolicy = policy,
                    pickedNames = it.pickedNames + names,
                    allowanceSupported = plugin?.has(KidsFeatures.ALLOWANCE) == true,
                    sessionLimitSupported = sessionLimit,
                    liveTvSupported = plugin?.has(KidsFeatures.LIVE_TV) == true,
                )
            }
        }

        companion object {
            val ALLOWANCE_OPTIONS: List<Int?> = listOf(null, 5, 10, 15, 30, 45, 60, 90, 120, 180, 240)
            val SESSION_LIMIT_OPTIONS: List<Int?> = listOf(null, 10, 15, 20, 30, 45, 60, 90, 120)

            /**
             * [sections] with [section] moved by [delta], unchanged if it would leave the list
             */
            fun moveSection(
                sections: List<KidsHomeSectionEntry>,
                section: KidsHomeSection,
                delta: Int,
            ): List<KidsHomeSectionEntry> {
                val index = sections.indexOfFirst { it.section == section }
                val target = index + delta
                if (index < 0 || target !in sections.indices) return sections
                return sections.toMutableList().apply { add(target, removeAt(index)) }
            }

            /**
             * [options] with [current] added in order when it is not one of them, so a value set elsewhere shows
             */
            fun withCurrent(
                options: List<Int?>,
                current: Int?,
            ): List<Int?> =
                if (current in options) {
                    options
                } else {
                    listOf<Int?>(null) + (options.filterNotNull() + current!!).sorted()
                }
        }
    }
