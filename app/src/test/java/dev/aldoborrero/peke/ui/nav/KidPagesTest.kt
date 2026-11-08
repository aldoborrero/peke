package dev.aldoborrero.peke.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `a new page replaces the last one with a short fade, not Nav3's long one`() {
        val backStack = mutableStateListOf<Destination>(Destination.Home())
        val series = Destination.MediaItem(itemId = java.util.UUID.randomUUID(), type = BaseItemKind.SERIES)
        route = KidPagesRoute.SHOW
        rule.setContent {
            KidPages(
                backStack = backStack,
                onBack = { backStack.removeAt(backStack.lastIndex) },
                route = route,
                contentKey = { it.toString() },
                onLaunchRule = {},
            ) { Text(if (it is Destination.Home) "home" else "series") }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { backStack.add(series) }
        val pushed = framesUntilGone("home")
        assertTrue("the last page left after $pushed ms", pushed <= FADE_BOUND)
        rule.onNodeWithText("series").assertExists()

        rule.runOnIdle { backStack.removeAt(backStack.lastIndex) }
        val popped = framesUntilGone("series")
        assertTrue("the page left after $popped ms", popped <= FADE_BOUND)
        rule.onNodeWithText("home").assertExists()
    }

    /** Milliseconds of frames until no node shows [text], up to a second */
    private fun framesUntilGone(text: String): Long {
        var elapsed = 0L
        while (elapsed < 1_000 && rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) {
            rule.mainClock.advanceTimeByFrame()
            elapsed += 16
        }
        return elapsed
    }

    private companion object {
        /** The short fade and a few frames of slack, well short of Nav3's 700 ms */
        const val FADE_BOUND = KID_PAGES_FADE_MILLIS + 100L
    }
}
