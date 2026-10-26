package dev.aldoborrero.peke.kids.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.setup.TvLauncher
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.nav.DestinationPage
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
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

        /**
         * Pages from [start] outside a kid's pages, with the kid's pages set aside
         */
        fun openApart(start: Destination): MutableList<Destination> = navigationManager.openApart(start)

        fun closeApart() = navigationManager.closeApart()

        /**
         * Leave pages shown outside a kid's pages: the kid's pages come back first, then the launch rule's first screen
         */
        fun leaveApart() {
            closeApart()
            leave(inKidPages = false)
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
 * Settings from the parent menu when no kid is current: its settings, and the pages they open, outside a kid's pages.
 * The kid's pages are set aside meanwhile ([NavigationManager.openApart]); Back from the first page returns to the
 * picker or the "no kids" page.
 */
@Composable
fun SetupThisTvPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: GrownUpGuardViewModel = hiltViewModel(),
) {
    val pages = remember { viewModel.openApart(Destination.Settings(PreferenceScreenOption.PEKE)) }
    DisposableEffect(Unit) { onDispose { viewModel.closeApart() } }
    BackHandler(enabled = pages.size <= 1) { viewModel.leaveApart() }
    NavDisplay(
        backStack = pages,
        onBack = { if (pages.size > 1) pages.removeLastOrNull() else viewModel.leaveApart() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = { key ->
            NavEntry(key) {
                // Only grown-up pages, which this page's guard covers; a kid's page never shows here
                if (key.isGrownUp()) DestinationPage(key, preferences, onClearBackdrop = {}, modifier = Modifier.fillMaxSize())
            }
        },
        modifier = modifier,
    )
}
