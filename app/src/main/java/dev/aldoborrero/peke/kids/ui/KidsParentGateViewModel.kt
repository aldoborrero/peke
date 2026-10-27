package dev.aldoborrero.peke.kids.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.gate.AdminCheck
import dev.aldoborrero.peke.kids.gate.GrownUpSessions
import dev.aldoborrero.peke.kids.gate.LockChange
import dev.aldoborrero.peke.kids.gate.LockState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.VerifyOutcome
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.setup.AdminSession
import dev.aldoborrero.peke.kids.setup.AdminSessions
import dev.aldoborrero.peke.kids.setup.AdminSignIn
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.kids.timer.AddTimeAmount
import dev.aldoborrero.peke.kids.timer.AddTimePreview
import dev.aldoborrero.peke.kids.timer.AddTimeSteps
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.kids.timer.TodayChange
import dev.aldoborrero.peke.services.MusicService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.AccessSchedule
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface GateStage {
    /**
     * @param type null while loading
     */
    data class Passcode(
        val type: ParentLockType? = null,
        val attempt: Int = 0,
    ) : GateStage

    /**
     * The household has no code yet, or it can't be known right now, so nothing can open the gate
     *
     * @param notSet true when the server said there is no code, false when it could not be reached
     */
    data class NoCode(
        val notSet: Boolean,
    ) : GateStage

    /**
     * The server no longer has the plugin and no code is cached: only a server administrator's sign-in opens the gate
     */
    data object PluginMissing : GateStage

    /**
     * No kid's token works and no verifier is cached, so no code can be checked: only recovery with an administrator
     */
    data object RecoveryOnly : GateStage

    /**
     * A wrong code: try again, or recover it with an administrator
     */
    data class WrongCode(
        val type: ParentLockType,
        val attempt: Int,
    ) : GateStage

    /**
     * Signing in as a server administrator, with a user name and password or with Quick Connect
     *
     * @param error why the last attempt failed
     * @param checking while the server is asked
     * @param recovery to set a new household code (Forgot the code?), rather than to reset a TV whose plugin went away
     * @param quickConnect signing in with Quick Connect rather than a password
     * @param quickConnectCode the code to approve in another Jellyfin app, once the server gave one
     */
    data class AdminSignIn(
        val error: AdminCheck? = null,
        val checking: Boolean = false,
        val recovery: Boolean = false,
        val quickConnect: Boolean = false,
        val quickConnectCode: String? = null,
    ) : GateStage

    /**
     * An administrator signed in to recover the code: the new code, entered twice
     */
    data class RecoveryCode(
        val type: ParentLockType,
        val saving: Boolean = false,
    ) : GateStage

    /**
     * An administrator signed in on a server whose plugin went away: only resetting this TV is offered
     */
    data object AdminMenu : GateStage

    data object Menu : GateStage

    /**
     * Choosing how much time to add, as a step of [AddTimeSteps.MINUTES]
     *
     * @param sending while the server is asked; OK does nothing meanwhile
     * @param error why the last try failed, for the inline error with Retry
     */
    data class AddTime(
        val index: Int,
        val sending: Boolean = false,
        val error: BonusResult? = null,
    ) : GateStage {
        val minutes: Int get() = AddTimeSteps.MINUTES[index]
    }

    /**
     * The time was added: [minutes] more, until [until]. With [bonusId] it can be undone for [undoSeconds].
     *
     * @param undoing while the undo is sent
     * @param undoError why the undo failed
     */
    data class Added(
        val minutes: Int,
        val until: LocalDateTime,
        val bonusId: Long?,
        val undoSeconds: Int = 0,
        val undoing: Boolean = false,
        val undoError: TodayChange? = null,
    ) : GateStage
}

/**
 * Why a code was not accepted, for the message shown to the grown-up
 */
sealed interface GateRefusal {
    data object Wrong : GateRefusal

    data class Wait(
        val seconds: Int,
    ) : GateRefusal

    data object CannotCheck : GateRefusal
}

@HiltViewModel
class KidsParentGateViewModel
    @Inject
    constructor(
        val timeLimits: KidsTimeLimits,
        val pluginService: KidsPluginService,
        private val parentLockService: ParentLockService,
        private val navigationManager: NavigationManager,
        private val setupNavigationManager: SetupNavigationManager,
        private val adminSessions: AdminSessions,
        grownUpSessions: GrownUpSessions,
        tvSetupRepository: TvSetupRepository,
        private val serverRepository: ServerRepository,
        private val musicService: MusicService,
        private val addTimeAmount: AddTimeAmount,
        @param:DefaultCoroutineScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val _stage = MutableStateFlow<GateStage>(GateStage.Passcode())
        val stage: StateFlow<GateStage> = _stage

        /**
         * Moves on when the app went to the background: the gate then closes ([KidsParentGate]) and starts over, so the
         * kid who comes back finds neither the menu nor a recovery in progress
         */
        val epoch: StateFlow<Int> = grownUpSessions.epoch

        /**
         * Whether the gate opened inside a kid's session, so the menu offers that kid's items. From the picker or the
         * "no kids" page there is no current kid: the code opens Settings straight away.
         */
        var withKid = true

        /**
         * The kid whose session is open, for the menu's header
         */
        val current: StateFlow<CurrentUser?> = serverRepository.current

        /**
         * Whether this TV has another kid to switch to
         */
        val canSwitchKid: StateFlow<Boolean> =
            tvSetupRepository.state
                .map(::severalKids)
                .stateIn(viewModelScope, SharingStarted.Eagerly, severalKids(tvSetupRepository.state.value))

        /**
         * The clock the add-time forecast reads
         */
        var now: () -> LocalDateTime = LocalDateTime::now

        /**
         * The current kid's allowed hours, from Jellyfin
         */
        val schedules: List<AccessSchedule>
            get() =
                serverRepository.currentUserDto
                    ?.policy
                    ?.accessSchedules
                    .orEmpty()

        /**
         * The administrator signed in to recover the code, only in memory and only until the new code is set or the
         * gate closes
         */
        private var admin: AdminSession? = null

        private var quickConnect: Job? = null

        /**
         * Where Back from the administrator sign-in returns: the code entry it was opened from, if any
         */
        private var signInReturn: GateStage? = null

        /**
         * The amount whose sending needed the code again: sent as soon as the code passes
         */
        private var pendingMinutes: Int? = null

        /**
         * The household's lock type as last read, which a recovered code keeps
         */
        private var lockType = ParentLockType.NUMERIC

        init {
            loadLock()
            viewModelScope.launch { epoch.drop(1).collect { reset() } }
        }

        private fun loadLock() {
            viewModelScope.launch {
                _stage.value =
                    when (val lock = parentLockService.lockState()) {
                        is LockState.Code -> GateStage.Passcode(type = lock.type).also { lockType = lock.type }
                        LockState.NotSet -> GateStage.NoCode(notSet = true)
                        LockState.Unknown -> GateStage.NoCode(notSet = false)
                        LockState.PluginMissing -> GateStage.PluginMissing
                        LockState.RecoveryOnly -> GateStage.RecoveryOnly
                    }
            }
        }

        fun startAdminSignIn() {
            if (_stage.value == GateStage.PluginMissing) {
                signInReturn = GateStage.PluginMissing
                _stage.value = GateStage.AdminSignIn()
            }
        }

        /**
         * Check that [username] is a server administrator. The credentials are only passed on, never kept.
         */
        fun adminSignIn(
            username: String,
            password: String,
        ) {
            val current = _stage.value as? GateStage.AdminSignIn ?: return
            if (current.checking) return
            _stage.value = current.copy(error = null, checking = true)
            viewModelScope.launch {
                if (current.recovery) {
                    recoverySignIn(username, password)
                    return@launch
                }
                val result = parentLockService.checkAdmin(username, password)
                _stage.value = if (result == AdminCheck.ADMIN) GateStage.AdminMenu else GateStage.AdminSignIn(error = result)
            }
        }

        /**
         * Sign the administrator in with Quick Connect: a code to approve in another Jellyfin app, as in setup (F-G4)
         */
        fun startQuickConnect() {
            val current = _stage.value as? GateStage.AdminSignIn ?: return
            quickConnect?.cancel()
            _stage.value = current.copy(error = null, checking = true, quickConnect = true, quickConnectCode = null)
            quickConnect =
                viewModelScope.launch {
                    val server = parentLockService.gateServer()
                    if (server == null) {
                        _stage.value = current.copy(error = AdminCheck.UNREACHABLE, checking = false, quickConnect = true)
                        return@launch
                    }
                    val result =
                        adminSessions.openWithQuickConnect(server) { code ->
                            _stage.update { s -> if (s is GateStage.AdminSignIn && s.quickConnect) s.copy(quickConnectCode = code) else s }
                        }
                    quickConnect = null
                    signedIn(result, current.recovery, quickConnect = true)
                }
        }

        /**
         * Back to the user name and password
         */
        fun usePassword() {
            quickConnect?.cancel()
            quickConnect = null
            _stage.update { s -> if (s is GateStage.AdminSignIn) GateStage.AdminSignIn(recovery = s.recovery) else s }
        }

        /**
         * Back on the administrator sign-in: the code entry or page it came from, if any
         *
         * @return whether the gate stays open
         */
        fun backFromSignIn(): Boolean {
            if (_stage.value !is GateStage.AdminSignIn) return false
            quickConnect?.cancel()
            quickConnect = null
            val back = signInReturn ?: return false
            signInReturn = null
            _stage.value = back
            return true
        }

        /**
         * Check the household code. OK with no code opens Forgot the code? without asking the server, so it costs
         * no attempt (F-G1).
         *
         * @param onDone called when the gate should close
         * @param onPassed called instead of opening the menu when the code is right, before [onDone]
         * @param onRefused called when the code was not accepted
         */
        fun submit(
            input: String,
            onDone: () -> Unit,
            onPassed: (() -> Unit)? = null,
            onRefused: (GateRefusal) -> Unit,
        ) {
            val current = _stage.value as? GateStage.Passcode ?: return
            if (current.type == null) return
            if (input.isEmpty()) {
                signInReturn = current
                _stage.value = GateStage.AdminSignIn(recovery = true)
                return
            }
            viewModelScope.launch {
                val refusal =
                    when (val outcome = parentLockService.verify(input)) {
                        VerifyOutcome.Valid, VerifyOutcome.ValidOffline -> null
                        VerifyOutcome.Wrong -> GateRefusal.Wrong
                        is VerifyOutcome.Throttled -> GateRefusal.Wait(outcome.seconds)
                        VerifyOutcome.CannotCheck -> GateRefusal.CannotCheck
                    }
                if (refusal == null) {
                    val pending = pendingMinutes
                    if (pending != null) {
                        pendingMinutes = null
                        _stage.value = GateStage.AddTime(AddTimeSteps.indexOf(pending))
                        sendAddTime()
                    } else if (onPassed != null) {
                        onPassed.invoke()
                        onDone.invoke()
                    } else {
                        // The menu shows the time left: read it from the server now, since a grown-up may have just
                        // changed the allowance on the dashboard. Offline, the last known status stays.
                        if (withKid) timeLimits.allowanceService.check()
                        _stage.value = GateStage.Menu
                    }
                } else if (refusal == GateRefusal.Wrong) {
                    _stage.value = GateStage.WrongCode(current.type, current.attempt + 1)
                } else {
                    _stage.value = current.copy(attempt = current.attempt + 1)
                    onRefused.invoke(refusal)
                }
            }
        }

        /**
         * Open the Add time stepper on the amount last given on this TV
         */
        fun openAddTime() {
            viewModelScope.launch {
                val minutes = addTimeAmount.get()
                if (_stage.value == GateStage.Menu) {
                    timeLimits.holdStop.value = true
                    _stage.value = GateStage.AddTime(AddTimeSteps.indexOf(minutes))
                }
            }
        }

        /**
         * Move the stepper by [delta] steps, held at the ends
         */
        fun stepAddTime(delta: Int) {
            _stage.update { s ->
                if (s is GateStage.AddTime && !s.sending) s.copy(index = AddTimeSteps.step(s.index, delta), error = null) else s
            }
        }

        /**
         * Back on the stepper: the menu again, with the grant still live
         */
        fun backToMenu() {
            val current = _stage.value as? GateStage.AddTime ?: return
            if (current.sending) return
            timeLimits.holdStop.value = false
            _stage.value = GateStage.Menu
        }

        /**
         * Add the stepper's amount to today's allowance on the server, once: further OKs while it's sent do nothing.
         * The sitting and its break, and bedtime, are the server's alone. The grant stays until the confirmation closes,
         * for its Undo.
         */
        fun sendAddTime() {
            val current = _stage.value as? GateStage.AddTime ?: return
            if (current.sending) return
            val minutes = current.minutes
            val until = AddTimePreview.of(timeLimits.state.value, minutes, now(), schedules).until
            _stage.value = current.copy(sending = true, error = null)
            timeLimits.holdStop.value = true
            // Leaving time's up then resumes what was playing
            val timesUp = timeLimits.state.value.denial == KidsDenial.TIME_UP
            if (timesUp) timeLimits.interrupted.timeAdded = true
            viewModelScope.launch {
                val outcome = timeLimits.allowanceService.addBonus(minutes)
                if (outcome.result != BonusResult.ADDED && timesUp) timeLimits.interrupted.timeAdded = false
                when (outcome.result) {
                    BonusResult.ADDED -> {
                        addTimeAmount.set(minutes)
                        // Undo is offered for a few seconds, and never past the server's own window
                        val window = outcome.undoSeconds?.coerceAtMost(UNDO_SECONDS.toLong())?.toInt() ?: UNDO_SECONDS
                        val bonusId = outcome.bonusId?.takeIf { window > 0 }
                        _stage.value = GateStage.Added(minutes, until, bonusId, undoSeconds = if (bonusId != null) window else 0)
                    }

                    BonusResult.NEEDS_GRANT -> {
                        // The grant ran out: the code again, then the same amount
                        pendingMinutes = minutes
                        _stage.value = GateStage.Passcode(lockType)
                        loadLock()
                    }

                    else -> {
                        _stage.value = current.copy(sending = false, error = outcome.result)
                    }
                }
            }
        }

        /**
         * Take back the time just added, keeping the kid where they were
         *
         * @param onUndone called with [TodayChange.DONE], or [TodayChange.ALREADY_UNDONE] when it was gone already, and the
         * gate should close
         */
        fun undoAddTime(onUndone: (TodayChange) -> Unit) {
            val current = _stage.value as? GateStage.Added ?: return
            val bonusId = current.bonusId ?: return
            if (current.undoing) return
            _stage.value = current.copy(undoing = true, undoError = null)
            viewModelScope.launch {
                when (val result = timeLimits.allowanceService.undoBonus(bonusId)) {
                    TodayChange.DONE, TodayChange.ALREADY_UNDONE -> {
                        timeLimits.interrupted.timeAdded = false
                        closeAdded()
                        onUndone(result)
                    }

                    else -> {
                        _stage.value =
                            current.copy(undoing = false, undoError = result, bonusId = bonusId.takeIf { result != TodayChange.TOO_LATE })
                    }
                }
            }
        }

        /**
         * The confirmation closed: the grant ends, and a stop screen the time lifted goes
         */
        fun closeAdded() {
            parentLockService.forgetGrants()
            timeLimits.holdStop.value = false
        }

        /**
         * Keep watching now: end the kid's break on the server, which gives no screen time
         *
         * @param onResult [TodayChange.DONE] when the gate should close
         */
        fun keepWatching(onResult: (TodayChange) -> Unit) {
            viewModelScope.launch {
                val result = timeLimits.allowanceService.endBreak()
                if (result == TodayChange.DONE) parentLockService.forgetGrants()
                onResult(result)
            }
        }

        /**
         * The grown-up closed the menu without choosing anything: the grant ends here, so the kid behind it can't open
         * grown-up pages with it while it would still be live
         */
        fun leaveMenu() {
            parentLockService.forgetGrants()
            timeLimits.holdStop.value = false
        }

        /**
         * The current kid's rules
         */
        fun openRules() {
            navigationManager.navigateTo(Destination.KidRules)
        }

        /**
         * Settings, with Kids on this TV, its About (the license) and Reset this TV: in the kid's pages, or outside them
         * from the picker
         */
        fun openThisTv() {
            if (withKid) {
                navigationManager.navigateTo(Destination.Settings(PreferenceScreenOption.PEKE))
            } else {
                setupNavigationManager.navigateTo(SetupDestination.ThisTv)
            }
        }

        /**
         * The kids picker, without the gate again: it just passed, so the picker lets a grown-up pick any kid, also one
         * stopped by a sibling's limits. Picking ends the grant.
         */
        fun switchKid() {
            parentLockService.grantPick()
            appScope.launch {
                // As when switching from the sidebar: the kid's music doesn't carry on behind the picker
                musicService.stopIfStarted()
                setupNavigationManager.navigateTo(SetupDestination.KidsPicker)
            }
        }

        /**
         * Reset this TV, after a confirmation. Nothing unlocks the TV: this is the only way out of kids mode.
         */
        fun openReset() {
            setupNavigationManager.navigateTo(SetupDestination.ResetTv(fromKid = withKid))
        }

        /**
         * After a wrong code, type it again
         */
        fun retryCode() {
            val wrong = _stage.value as? GateStage.WrongCode ?: return
            _stage.value = GateStage.Passcode(wrong.type, wrong.attempt)
        }

        /**
         * Forgot the code? (spec 004 FR-017): a server administrator signs in, then sets a new code
         */
        fun forgotCode() {
            val current = _stage.value
            signInReturn =
                when (current) {
                    is GateStage.WrongCode -> GateStage.Passcode(current.type, current.attempt)
                    is GateStage.NoCode, GateStage.RecoveryOnly -> current
                    else -> null
                }
            _stage.value = GateStage.AdminSignIn(recovery = true)
        }

        private suspend fun recoverySignIn(
            username: String,
            password: String,
        ) {
            val server = parentLockService.gateServer()
            if (server == null) {
                _stage.value = GateStage.AdminSignIn(error = AdminCheck.UNREACHABLE, recovery = true)
                return
            }
            signedIn(adminSessions.open(server, username, password), recovery = true, quickConnect = false)
        }

        /**
         * An administrator sign-in ended: for recovery the session is kept for the new code; for a TV whose plugin went
         * away it was only a check, and ends now
         */
        private suspend fun signedIn(
            result: AdminSignIn,
            recovery: Boolean,
            quickConnect: Boolean,
        ) {
            when {
                result is AdminSignIn.Opened && recovery -> {
                    closeAdmin()
                    admin = result.session
                    signInReturn = null
                    _stage.value = GateStage.RecoveryCode(lockType)
                }

                result is AdminSignIn.Opened -> {
                    result.session.close()
                    parentLockService.passedAsAdmin()
                    signInReturn = null
                    _stage.value = GateStage.AdminMenu
                }

                else -> {
                    _stage.value = GateStage.AdminSignIn(error = adminCheck(result), recovery = recovery, quickConnect = quickConnect)
                }
            }
        }

        /**
         * Set the recovered code, entered twice, with the administrator, whose session then ends. The parent menu then
         * opens (Settings, without a kid); if the code couldn't be set, the gate asks for a code again.
         */
        fun setRecoveredCode(
            code: String,
            onResult: (LockChange) -> Unit,
        ) {
            val current = _stage.value as? GateStage.RecoveryCode ?: return
            if (current.saving) return
            val session = admin ?: return
            admin = null
            _stage.value = current.copy(saving = true)
            viewModelScope.launch {
                val result = parentLockService.recover(session, current.type, code)
                onResult(result)
                if (result == LockChange.Done) {
                    // The administrator passed the gate: the parent menu opens, with Settings, which resets the TV
                    _stage.value = GateStage.Menu
                } else {
                    _stage.value = GateStage.Passcode()
                    loadLock()
                }
            }
        }

        private fun closeAdmin() {
            val session = admin ?: return
            admin = null
            appScope.launch { session.close() }
        }

        override fun onCleared() {
            quickConnect?.cancel()
            closeAdmin()
            timeLimits.holdStop.value = false
        }

        /**
         * Start over, so every opening of the gate asks for the passcode again
         */
        fun reset() {
            quickConnect?.cancel()
            quickConnect = null
            closeAdmin()
            signInReturn = null
            pendingMinutes = null
            timeLimits.holdStop.value = false
            _stage.value = GateStage.Passcode()
            loadLock()
        }

        companion object {
            private fun severalKids(tv: TvState): Boolean = (tv as? TvState.SetUp)?.kids.orEmpty().size > 1

            private fun adminCheck(result: AdminSignIn): AdminCheck =
                when (result) {
                    AdminSignIn.NotAdmin -> AdminCheck.NOT_ADMIN
                    AdminSignIn.Wrong -> AdminCheck.WRONG
                    AdminSignIn.QuickConnectOff -> AdminCheck.QUICK_CONNECT_OFF
                    AdminSignIn.Unreachable -> AdminCheck.UNREACHABLE
                    else -> AdminCheck.FAILED
                }
        }
    }
