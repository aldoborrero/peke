package dev.aldoborrero.peke.kids.gate

import dev.aldoborrero.peke.kids.setup.AdminSessions
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything a grown-up holds open while using the TV: the parent grants and the administrator sessions. All of it
 * ends when the app goes to the background ([end], on the activity's `ON_STOP`), the Home button or standby: the TV is
 * left to a kid then, and on a TV standby keeps the process alive, so nothing would end it otherwise.
 *
 * Pages and dialogs that hold grown-up state watch [epoch]: when it moves on they drop that state, close or ask for the
 * code again. The launch rule then shows the kid's own pages, without grown-up ones.
 *
 * A grown-up who approves a Quick Connect code on their phone keeps the TV in the foreground, so that doesn't end
 * anything.
 */
@Singleton
class GrownUpSessions
    @Inject
    constructor(
        private val parentLockService: ParentLockService,
        private val adminSessions: AdminSessions,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        private val _epoch = MutableStateFlow(0)

        /**
         * How many times the grown-up's things were ended, so a page can tell they were since it opened
         */
        val epoch: StateFlow<Int> = _epoch

        /**
         * End every grant and administrator session now. The sessions are ended on the server in the background.
         */
        fun end(): Job {
            Timber.i("The app went to the background: ending the grown-up's grants and sessions")
            parentLockService.forgetGrants()
            _epoch.update { it + 1 }
            return scope.launch { adminSessions.closeAll() }
        }
    }
