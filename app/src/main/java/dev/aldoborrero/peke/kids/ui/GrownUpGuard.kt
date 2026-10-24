package dev.aldoborrero.peke.kids.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.setup.TvLauncher
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.components.LicenseInfo
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.nav.Destination
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GrownUpGuardViewModel
    @Inject
    constructor(
        private val parentLockService: ParentLockService,
        private val navigationManager: NavigationManager,
        private val tvLauncher: TvLauncher,
    ) : ViewModel() {
        /**
         * Whether the page may show: a parent grant was live when it last showed, or the gate passed since. Lives as long
         * as the page, and not across the app being killed.
         */
        var allowed by mutableStateOf(parentLockService.hasLiveGrant())
            private set

        fun passed() {
            allowed = true
        }

        /**
         * The page shows again, such as when the app comes back from the background or a page above it closed: it
         * stays only while a grant is live, otherwise the gate asks for the code again
         */
        fun recheck() {
            allowed = parentLockService.hasLiveGrant()
        }

        /**
         * Leave a guarded page whose gate was dismissed: back in the kid's pages, or to the launch rule's first
         * screen for a page outside them
         */
        fun leave(inKidPages: Boolean) {
            if (inKidPages) {
                navigationManager.goBack()
            } else {
                viewModelScope.launch { tvLauncher.launch() }
            }
        }
    }

/**
 * The grown-up destinations a kid could otherwise reach: settings, debug, kids settings, the license and the update
 * page (C12)
 */
fun Destination.isGrownUp(): Boolean =
    when (this) {
        is Destination.Settings,
        is Destination.SubtitleSettings,
        Destination.UserAppPreferences,
        Destination.KidsSettings,
        Destination.UpdateApp,
        Destination.License,
        Destination.Debug,
        -> true

        else -> false
    }

/**
 * Shows [content], a grown-up page, only while a parent grant is live ([ParentLockService.hasLiveGrant]): right after
 * the parent gate passed. Otherwise, such as for a page restored after the app was killed or opened some other way,
 * the gate asks for the code first, and dismissing it leaves the page. Checked whenever the page resumes, so a page
 * left open when the app went to the background, which ends every grant, asks again; a grant that runs out while the
 * grown-up is on the page doesn't throw them out until then.
 *
 * @param inKidPages whether the page is one of the kid's pages, which Back leaves, or a setup page outside them
 */
@Composable
fun GrownUpGuard(
    inKidPages: Boolean,
    modifier: Modifier = Modifier,
    viewModel: GrownUpGuardViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    LifecycleResumeEffect(viewModel) {
        viewModel.recheck()
        onPauseOrDispose { }
    }
    if (viewModel.allowed) {
        content()
    } else {
        LoadingPage(modifier)
        KidsParentGate(
            withKid = inKidPages,
            onPassed = viewModel::passed,
            // Also called right after onPassed
            onDismissRequest = { if (!viewModel.allowed) viewModel.leave(inKidPages) },
        )
    }
}

/**
 * The license screen from the parent menu when no kid is current; Back returns to the picker or the "no kids" page
 */
@Composable
fun SetupLicensePage(
    modifier: Modifier = Modifier,
    viewModel: GrownUpGuardViewModel = hiltViewModel(),
) {
    BackHandler { viewModel.leave(inKidPages = false) }
    LicenseInfo(modifier)
}
