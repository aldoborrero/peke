package dev.aldoborrero.peke.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The kid's pages follow what is known about the user after they were first drawn: right after setup the pages can
 * open before the kid's profile is read (spec 012)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KidPagesTest {
    @get:Rule
    val rule = createComposeRule()

    private var route by mutableStateOf(KidPagesRoute.WAIT)
    private var launchRules = 0

    private fun setContent() {
        rule.setContent {
            KidPages(
                backStack = listOf(Destination.Home()),
                onBack = {},
                route = route,
                contentKey = { it.toString() },
                onLaunchRule = { launchRules++ },
            ) { Text("home") }
        }
    }

    @Test
    fun `pages first drawn while waiting show once the user is known to be a kid`() {
        setContent()
        rule.onNodeWithText("home").assertDoesNotExist()

        route = KidPagesRoute.SHOW
        rule.onNodeWithText("home").assertExists()
    }

    @Test
    fun `pages first drawn while waiting go to the launch rule once the user is known not to be a kid`() {
        setContent()
        rule.waitForIdle()
        assertEquals(0, launchRules)

        route = KidPagesRoute.LAUNCH_RULE
        rule.waitForIdle()
        assertEquals(1, launchRules)
        rule.onNodeWithText("home").assertDoesNotExist()
    }
}
