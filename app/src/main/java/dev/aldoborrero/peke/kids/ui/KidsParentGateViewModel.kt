package dev.aldoborrero.peke.kids.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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
     * Signing in as a server administrator
     *
     * @param error why the last attempt failed
     * @param checking while the server is asked
     * @param recovery to set a new household code (Forgot the code?), rather than to reset a TV whose plugin went away
     */
    data class AdminSignIn(
        val error: AdminCheck? = null,
        val checking: Boolean = false,
        val recovery: Boolean = false,
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
     * Choosing how many minutes to add to today's allowance
     */
    data object Minutes : GateStage
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
         * "no kids" page there is no current kid: the menu leaves out adding time, the kid's settings and This TV, which
         * live in a kid's pages.
         */
        var withKid = true

        /**
         * The administrator signed in to recover the code, only in memory and only until the new code is set or the
         * gate closes
         */
        private var admin: AdminSession? = null

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
            if (_stage.value == GateStage.PluginMissing) _stage.value = GateStage.AdminSignIn()
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
         * Check the household code
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
            viewModelScope.launch {
                val refusal =
                    when (val outcome = parentLockService.verify(input)) {
                        VerifyOutcome.Valid, VerifyOutcome.ValidOffline -> null
                        VerifyOutcome.Wrong -> GateRefusal.Wrong
                        is VerifyOutcome.Throttled -> GateRefusal.Wait(outcome.seconds)
                        VerifyOutcome.CannotCheck -> GateRefusal.CannotCheck
                    }
                if (refusal == null) {
                    if (onPassed != null) {
                        onPassed.invoke()
                        onDone.invoke()
                    } else {
                        // The menu shows the time left: read it from the server now, since a grown-up may have just
                        // changed the allowance on the dashboard. Offline, the last known status stays.
                        timeLimits.allowanceService.check()
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

        fun chooseMinutes() {
            _stage.value = GateStage.Minutes
        }

        /**
         * Add [minutes] to today's allowance on the server. The sitting and its break, and bedtime, are the server's
         * alone: the app has no way around them.
         */
        fun addTime(
            minutes: Int,
            onBonusResult: (BonusResult) -> Unit,
        ) {
            // Leaving time's up then resumes what was playing
            val timesUp = timeLimits.state.value.denial == KidsDenial.TIME_UP
            if (timesUp) timeLimits.interrupted.timeAdded = true
            viewModelScope.launch {
                val result = timeLimits.allowanceService.addBonus(minutes)
                // Choosing the minutes left the menu: the grant ends once the server has them
                parentLockService.forgetGrants()
                if (result != BonusResult.ADDED && timesUp) timeLimits.interrupted.timeAdded = false
                onBonusResult(result)
            }
        }

        /**
         * The grown-up closed the menu without choosing anything: the grant ends here, so the kid behind it can't open
         * grown-up pages with it while it would still be live
         */
        fun leaveMenu() {
            parentLockService.forgetGrants()
        }

        fun openSettings() {
            parentLockService.grantSettings()
            navigationManager.navigateTo(Destination.KidsSettings)
        }

        /**
         * The kids on this TV, to add or remove one
         */
        fun openKidsOnTv() {
            setupNavigationManager.navigateTo(SetupDestination.KidsOnTv(fromKid = withKid))
        }

        /**
         * This TV's player and screensaver settings, in the kid's pages
         */
        fun openThisTv() {
            navigationManager.navigateTo(Destination.Settings(PreferenceScreenOption.PEKE))
        }

        fun openLicense() {
            if (withKid) navigationManager.navigateTo(Destination.License) else setupNavigationManager.navigateTo(SetupDestination.License)
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
         * Forgot the code? (spec FR-017): a server administrator signs in, then sets a new code
         */
        fun forgotCode() {
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
            when (val result = adminSessions.open(server, username, password)) {
                is AdminSignIn.Opened -> {
                    closeAdmin()
                    admin = result.session
                    _stage.value = GateStage.RecoveryCode(lockType)
                }

                else -> {
                    _stage.value = GateStage.AdminSignIn(error = adminCheck(result), recovery = true)
                }
            }
        }

        /**
         * Set the recovered code, entered twice, with the administrator, whose session then ends. The parent menu then
         * opens; if the code couldn't be set, the gate asks for a code again.
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
                    // The administrator passed the gate: the parent menu opens, with Kids on this TV and Reset this TV
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
            closeAdmin()
        }

        /**
         * Start over, so every opening of the gate asks for the passcode again
         */
        fun reset() {
            closeAdmin()
            _stage.value = GateStage.Passcode()
            loadLock()
        }

        companion object {
            val MINUTE_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

            private fun adminCheck(result: AdminSignIn): AdminCheck =
                when (result) {
                    AdminSignIn.NotAdmin -> AdminCheck.NOT_ADMIN
                    AdminSignIn.Wrong -> AdminCheck.WRONG
                    AdminSignIn.Unreachable -> AdminCheck.UNREACHABLE
                    else -> AdminCheck.FAILED
                }
        }
    }
