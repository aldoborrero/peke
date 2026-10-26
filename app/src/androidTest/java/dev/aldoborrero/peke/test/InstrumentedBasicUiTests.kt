package dev.aldoborrero.peke.test

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.navigation3.runtime.NavBackStack
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.aldoborrero.peke.MainContent
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ScreensaverService
import dev.aldoborrero.peke.services.ScreensaverState
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.theme.PekeTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class InstrumentedBasicUiTests {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    lateinit var screensaverService: ScreensaverService

    @Before
    fun setup() {
        screensaverService = mockk(relaxed = true)
        every { screensaverService.state } returns MutableStateFlow(ScreensaverState(false, false, false, false))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() {
        val navigationManager = NavigationManager()
        navigationManager.backStack = NavBackStack(Destination.Home())
        // Start the app
        composeTestRule.setContent {
            PekeTheme {
                MainContent(
                    backStack = mutableListOf(SetupDestination.Setup()),
                    navigationManager = navigationManager,
                    userPreferences = mockk(relaxed = true),
                    backdropService = mockk(relaxed = true),
                    screensaverService = screensaverService,
                    modifier = Modifier,
                )
            }
        }

        // A TV that isn't set up starts at the setup's server step
        composeTestRule.onNodeWithText("Add your Jellyfin server").assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.performClickEnter() =
    performKeyInput {
        pressKey(Key.DirectionCenter)
    }
