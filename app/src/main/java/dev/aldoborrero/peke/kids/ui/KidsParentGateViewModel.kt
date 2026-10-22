package dev.aldoborrero.peke.kids.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.gate.AdminCheck
import dev.aldoborrero.peke.kids.gate.LockState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.VerifyOutcome
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.ui.nav.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
     * Signing in as a server administrator
     *
     * @param error why the last attempt failed
     * @param checking while the server is asked
     */
    data class AdminSignIn(
        val error: AdminCheck? = null,
        val checking: Boolean = false,
    ) : GateStage

    /**
     * An administrator signed in: only turning kids mode off for the server is offered
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
        private val kidsModeService: KidsModeService,
        private val parentLockService: ParentLockService,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val setupNavigationManager: SetupNavigationManager,
    ) : ViewModel() {
        private val _stage = MutableStateFlow<GateStage>(GateStage.Passcode())
        val stage: StateFlow<GateStage> = _stage

        /**
         * Only unlock kids mode (no menu), for screens outside the kids experience such as the server list
         */
        var unlockOnly = false

        init {
            loadLock()
        }

        private fun loadLock() {
            viewModelScope.launch {
                _stage.value =
                    when (val lock = parentLockService.lockState()) {
                        is LockState.Code -> GateStage.Passcode(type = lock.type)
                        LockState.NotSet -> GateStage.NoCode(notSet = true)
                        LockState.Unknown -> GateStage.NoCode(notSet = false)
                        LockState.PluginMissing -> GateStage.PluginMissing
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
            _stage.value = GateStage.AdminSignIn(checking = true)
            viewModelScope.launch {
                val result = parentLockService.checkAdmin(username, password)
                _stage.value = if (result == AdminCheck.ADMIN) GateStage.AdminMenu else GateStage.AdminSignIn(error = result)
            }
        }

        /**
         * Check the household code
         *
         * @param onDone called when the gate should close
         * @param onRefused called when the code was not accepted
         */
        fun submit(
            input: String,
            onDone: () -> Unit,
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
                    if (unlockOnly) {
                        kidsModeService.unlock()
                        onDone.invoke()
                    } else {
                        // The menu shows the time left: read it from the server now, since a grown-up may have just
                        // changed the allowance on the dashboard. Offline, the last known status stays.
                        timeLimits.allowanceService.check()
                        _stage.value = GateStage.Menu
                    }
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
                if (result != BonusResult.ADDED && timesUp) timeLimits.interrupted.timeAdded = false
                onBonusResult(result)
            }
        }

        fun openSettings() {
            parentLockService.grantSettings()
            navigationManager.navigateTo(Destination.KidsSettings)
        }

        fun leave() {
            viewModelScope.launch {
                kidsModeService.unlock()
                serverRepository.currentServer?.let {
                    setupNavigationManager.navigateTo(SetupDestination.UserList(it))
                }
            }
        }

        /**
         * Turn kids mode off for the current server, or the one the gate checked, whose plugin went away, and leave
         * kids mode
         */
        fun turnOffHere() {
            viewModelScope.launch {
                val server = serverRepository.currentServer ?: parentLockService.target()?.first ?: return@launch
                kidsModeService.turnOffForServer(server.id)
                setupNavigationManager.navigateTo(SetupDestination.UserList(server))
            }
        }

        /**
         * Start over, so every opening of the gate asks for the passcode again
         */
        fun reset() {
            _stage.value = GateStage.Passcode()
            loadLock()
        }

        companion object {
            val MINUTE_OPTIONS = listOf(15, 30, 45, 60, 90, 120)
        }
    }
