package dev.aldoborrero.peke

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import dev.aldoborrero.peke.kids.setup.KidsOnTvPage
import dev.aldoborrero.peke.kids.setup.KidsPicker
import dev.aldoborrero.peke.kids.setup.NoKidsPage
import dev.aldoborrero.peke.kids.setup.ResetTvPage
import dev.aldoborrero.peke.kids.setup.SetupFlow
import dev.aldoborrero.peke.kids.setup.TvSetupStep
import dev.aldoborrero.peke.kids.ui.GrownUpGuard
import dev.aldoborrero.peke.kids.ui.ProvideKidsUi
import dev.aldoborrero.peke.kids.ui.SetupLicensePage
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ScreensaverService
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.ui.components.AppScreensaver
import dev.aldoborrero.peke.ui.nav.ApplicationContent
import dev.aldoborrero.peke.ui.util.InterfaceCustomization
import dev.aldoborrero.peke.ui.util.LocalInterfaceCustomization

@Composable
fun MainContent(
    backStack: MutableList<SetupDestination>,
    navigationManager: NavigationManager,
    userPreferences: UserPreferences,
    backdropService: BackdropService,
    screensaverService: ScreensaverService,
    modifier: Modifier = Modifier,
) {
    val preferences by rememberUpdatedState(userPreferences)
    Surface(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.background),
        shape = RectangleShape,
    ) {
//                            val backStack = rememberNavBackStack(SetupDestination.Loading)
//                            setupNavigationManager.backStack = backStack
        val interfaceCustomization =
            remember(userPreferences.appPreferences) { InterfaceCustomization(userPreferences.appPreferences) }
        CompositionLocalProvider(LocalInterfaceCustomization provides interfaceCustomization) {
            ProvideKidsUi {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider = { key ->
                        key as SetupDestination
                        NavEntry(key) {
                            when (key) {
                                SetupDestination.Loading -> {
                                    Box(
                                        modifier = Modifier.size(200.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.border,
                                            modifier = Modifier.align(Alignment.Center),
                                        )
                                    }
                                }

                                is SetupDestination.Setup -> {
                                    SetupFlow(step = key.step, modifier = Modifier.fillMaxSize())
                                }

                                is SetupDestination.SignInAgain -> {
                                    SetupFlow(
                                        step = TvSetupStep.SIGN_IN,
                                        againServer = key.server,
                                        againKid = key.kid,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                SetupDestination.KidsPicker -> {
                                    KidsPicker(Modifier.fillMaxSize())
                                }

                                SetupDestination.NoKids -> {
                                    NoKidsPage(Modifier.fillMaxSize())
                                }

                                // The parent menu's pages outside a kid's pages, only with a live parent grant (C12)
                                is SetupDestination.KidsOnTv -> {
                                    GrownUpGuard(inKidPages = false, modifier = Modifier.fillMaxSize()) {
                                        KidsOnTvPage(fromKid = key.fromKid, modifier = Modifier.fillMaxSize())
                                    }
                                }

                                is SetupDestination.AddKids -> {
                                    GrownUpGuard(inKidPages = false, modifier = Modifier.fillMaxSize()) {
                                        SetupFlow(
                                            step = TvSetupStep.KIDS,
                                            addTo = key.server,
                                            addFromKid = key.fromKid,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }

                                is SetupDestination.ResetTv -> {
                                    GrownUpGuard(inKidPages = false, modifier = Modifier.fillMaxSize()) {
                                        ResetTvPage(fromKid = key.fromKid, modifier = Modifier.fillMaxSize())
                                    }
                                }

                                SetupDestination.License -> {
                                    GrownUpGuard(inKidPages = false, modifier = Modifier.fillMaxSize()) {
                                        SetupLicensePage(Modifier.fillMaxSize())
                                    }
                                }

                                is SetupDestination.AppContent -> {
                                    LaunchedEffect(Unit) {
                                        backdropService.clearBackdrop()
                                    }
                                    val current = key.current
                                    ApplicationContent(
                                        user = current.user,
                                        server = current.server,
                                        navigationManager = navigationManager,
                                        preferences = preferences,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    },
                )
                val screenSaverState by screensaverService.state.collectAsState()
                if (screenSaverState.enabled || screenSaverState.enabledTemp) {
                    AnimatedVisibility(
                        visible = screenSaverState.show,
                        enter = ScreensaverService.enterAnimation,
                        exit = ScreensaverService.exitAnimation,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AppScreensaver(userPreferences.appPreferences, Modifier.fillMaxSize())
                    }
                }
                AnimatedVisibility(
                    visible = screenSaverState.showDim || (screenSaverState.dimEnabled && screenSaverState.show),
                    enter = ScreensaverService.enterAnimation,
                    exit = ScreensaverService.exitAnimation,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val alpha =
                        userPreferences.appPreferences.interfacePreferences.screensaverPreference.dimPercent / 100f
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}
