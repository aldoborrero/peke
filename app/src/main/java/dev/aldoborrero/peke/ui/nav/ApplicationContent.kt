package dev.aldoborrero.peke.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.setup.TvLauncher
import dev.aldoborrero.peke.kids.ui.KidsTimerEffects
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.launchIO
import javax.inject.Inject

// Top scrim configuration for text readability (clock, season tabs)
const val TOP_SCRIM_ALPHA = 0.55f
const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
        private val kidsModeService: KidsModeService,
        private val tvLauncher: TvLauncher,
    ) : ViewModel() {
        /**
         * Whether the server couldn't say if the current user is a kid, see [KidsModeService.unanswered]
         */
        val unanswered = kidsModeService.unanswered

        /**
         * A user who isn't one of this TV's kids: back to the kids side's first screen, the picker or the only kid,
         * never a list of users or a grown-up shell (spec 004)
         */
        fun toKidsSide() {
            viewModelScope.launchIO { tvLauncher.launch() }
        }

        fun clearBackdrop() {
            viewModelScope.launchIO { backdropService.clearBackdrop() }
        }
    }

/**
 * What a kid's pages show for the current user
 */
internal enum class KidPagesRoute {
    /**
     * Their pages: the user is a kid
     */
    SHOW,

    /**
     * Nothing yet: it isn't known whether the user is a kid
     */
    WAIT,

    /**
     * The launch rule's first screen: the server says the user isn't a kid, or couldn't say. Peke has no grown-up
     * shell, and a user with no cached kid profile is none of this TV's kids.
     */
    LAUNCH_RULE,
}

internal fun kidPagesRoute(
    state: KidsModeState,
    unanswered: Boolean,
): KidPagesRoute =
    when (state) {
        is KidsModeState.Active -> KidPagesRoute.SHOW
        KidsModeState.Inactive -> KidPagesRoute.LAUNCH_RULE
        KidsModeState.Loading -> if (unanswered) KidPagesRoute.LAUNCH_RULE else KidPagesRoute.WAIT
    }

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer,
    user: JellyfinUser,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    Box(
        modifier = modifier,
    ) {
        val backdropStyle = preferences.appPreferences.interfacePreferences.backdropStyle
        Backdrop(
            drawerIsOpen = drawerState.isOpen,
            backdropStyle = backdropStyle,
            enableTopScrim = enableTopScrim,
            viewModel = viewModel,
        )
        val navDrawerListState = rememberLazyListState()
        // Peke is for kids: until it is known that the user is a kid nothing is shown, and anyone else goes back to the
        // launch rule
        val unanswered by viewModel.unanswered.collectAsState()
        val route = kidPagesRoute(LocalKidsMode.current, unanswered)
        NavDisplay(
            backStack = navigationManager.backStack,
            onBack = { navigationManager.goBack() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = { key ->
                key as Destination
                val contentKey = "${key}_${server?.id}_${user?.id}"
                NavEntry(key, contentKey = contentKey) {
                    if (route == KidPagesRoute.WAIT) {
                        LoadingPage(Modifier.fillMaxSize())
                    } else if (route == KidPagesRoute.LAUNCH_RULE) {
                        LaunchedEffect(Unit) { viewModel.toKidsSide() }
                        LoadingPage(Modifier.fillMaxSize())
                    } else if (key.fullScreen) {
                        DestinationContent(
                            destination = key,
                            preferences = preferences,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (user != null && server != null) {
                        NavDrawer(
                            destination = key,
                            preferences = preferences,
                            user = user,
                            server = server,
                            drawerState = drawerState,
                            navDrawerListState = navDrawerListState,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ErrorMessage("Trying to go to $key without a user logged in", null)
                    }
                }
            },
        )
        KidsTimerEffects()
    }
}
