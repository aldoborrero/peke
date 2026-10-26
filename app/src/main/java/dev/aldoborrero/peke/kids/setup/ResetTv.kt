package dev.aldoborrero.peke.kids.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.data.KidsDataCleaner
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import dev.aldoborrero.peke.ui.components.CircularProgress
import dev.aldoborrero.peke.ui.components.TextButton
import dev.aldoborrero.peke.ui.tryRequestFocus
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

enum class ResetOutcome {
    /**
     * Every kid's session was ended on the server, and everything erased here
     */
    DONE,

    /**
     * Everything was erased here, but the server couldn't end every kid's session: they stay in the dashboard's
     * devices until they expire or are removed there
     */
    OFFLINE,
}

/**
 * Resets this TV (spec FR-015): signs every kid out, best effort and each with their own device id, then erases the
 * server, the users, the kids data and cached statuses, the grants and this TV's setup, so the launch rule opens setup
 * at the server step. There is no unlocked state: this is the only way out of kids mode.
 */
class ResetTv
    @Inject
    constructor(
        private val serverDao: JellyfinServerDao,
        private val serverRepository: ServerRepository,
        private val tvSetupRepository: TvSetupRepository,
        private val kidsDataCleaner: KidsDataCleaner,
        private val allowanceService: KidsAllowanceService,
        private val parentLockService: ParentLockService,
        private val kidSignOut: KidSignOut,
        private val navigationManager: NavigationManager,
    ) {
        suspend fun reset(): ResetOutcome =
            withContext(NonCancellable) {
                val servers = withContext(PekeDispatchers.IO) { serverDao.getServers() }
                val users = servers.flatMap { s -> s.users.map { s.server to it } }
                var signedOut = true
                users.forEach { (server, user) -> if (!kidSignOut.signOut(server, user)) signedOut = false }
                // The users go with their server; the setup row last, so a reset cut short still counts as set up and
                // shows "This TV has no kids", from where it can be reset again
                servers.forEach { serverRepository.removeServer(it.server) }
                kidsDataCleaner.eraseAll()
                allowanceService.forget(users.map { it.second.rowId })
                parentLockService.forgetGrants()
                navigationManager.reloadHome()
                tvSetupRepository.erase()
                Timber.i("This TV was reset; %d kid sessions, signed out: %s", users.size, signedOut)
                if (signedOut) ResetOutcome.DONE else ResetOutcome.OFFLINE
            }
    }

sealed interface ResetScreen {
    data object Confirm : ResetScreen

    data object Working : ResetScreen

    /**
     * Reset, but the server couldn't be reached to end every kid's session
     */
    data object Offline : ResetScreen
}

@HiltViewModel
class ResetTvViewModel
    @Inject
    constructor(
        private val resetTv: ResetTv,
        private val launcher: TvLauncher,
        @param:DefaultCoroutineScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val _screen = MutableStateFlow<ResetScreen>(ResetScreen.Confirm)
        val screen: StateFlow<ResetScreen> = _screen

        /**
         * Reset, in the app's scope so leaving the page doesn't cut it short, then setup at the server step
         */
        fun reset(): Job {
            _screen.value = ResetScreen.Working
            return appScope.launch {
                val outcome = resetTv.reset()
                if (outcome == ResetOutcome.OFFLINE) {
                    _screen.value = ResetScreen.Offline
                } else {
                    toSetup()
                }
            }
        }

        fun toSetup(): Job = appScope.launch { launcher.launch { it == TvState.NotSetUp } }

        /**
         * Not reset: back to the kid whose menu opened this page, or else to the picker
         */
        fun cancel(fromKid: Boolean): Job = viewModelScope.launch { if (fromKid) launcher.resume() else launcher.launch() }
    }

/**
 * Confirming Reset this TV. Cancel is focused first.
 *
 * @param fromKid opened from a kid's parent menu, so Cancel returns to that kid
 */
@Composable
fun ResetTvPage(
    fromKid: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ResetTvViewModel = hiltViewModel(),
) {
    val screen by viewModel.screen.collectAsState()
    BackHandler(enabled = screen != ResetScreen.Working) {
        if (screen == ResetScreen.Offline) viewModel.toSetup() else viewModel.cancel(fromKid)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(screen) { focusRequester.tryRequestFocus() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(.6f),
        ) {
            Text(
                text = stringResource(if (screen == ResetScreen.Offline) R.string.peke_reset_done else R.string.peke_reset_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(if (screen == ResetScreen.Offline) R.string.peke_reset_offline else R.string.peke_reset_hint),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            when (screen) {
                ResetScreen.Confirm -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(
                            stringRes = R.string.cancel,
                            onClick = { viewModel.cancel(fromKid) },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                        TextButton(
                            stringRes = R.string.peke_menu_reset,
                            onClick = { viewModel.reset() },
                        )
                    }
                }

                ResetScreen.Working -> {
                    CircularProgress(Modifier.size(48.dp))
                }

                ResetScreen.Offline -> {
                    TextButton(
                        stringRes = R.string.peke_setup_continue,
                        onClick = { viewModel.toSetup() },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
            }
        }
    }
}
