package dev.aldoborrero.peke.kids

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.kids.ui.KidsDetailsHeader
import dev.aldoborrero.peke.kids.ui.KidsPlayPill
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A kids details page opens with its title and poster on screen (TV-OV), and they come back when focus returns to the
 * buttons from the rows below. A TV scrolls a focused button to a pivot 30% down the screen, which scrolled the title
 * off the top; the test gives the list that pivot, as Compose does on a device with the leanback feature.
 */
@OptIn(ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidsDetailsHeaderInViewTest {
    @get:Rule
    val rule = createComposeRule()

    private val play = FocusRequester()
    private var density = 1f
    private var page = Color.Unspecified
    private var text = Color.Unspecified

    /** Compose's TV default: the focused item's leading edge at 30% of the list, unless that leaves it cut */
    private val tvPivot =
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val pivot = .3f * containerSize
                val lead = if (size <= containerSize && containerSize - pivot < size) containerSize - size else pivot
                return offset - lead
            }
        }

    @Composable
    private fun DetailsPage() {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) {
            inputMode.requestInputMode(InputMode.Keyboard)
        }
        CompositionLocalProvider(
            LocalImageUrlService provides mockk<ImageUrlService>(relaxed = true),
            LocalBringIntoViewSpec provides tvPivot,
        ) {
            PekeTheme {
                density = LocalDensity.current.density
                page = LocalPekeColors.current.background
                text = androidx.tv.material3.MaterialTheme.colorScheme.onBackground
                LazyColumn(Modifier.fillMaxSize().background(page)) {
                    item {
                        KidsDetailsHeader(
                            poster = null,
                            title = "Bing",
                            overview = "Bing is a curious bunny who loves to play with his friends",
                            buttons = {
                                KidsPlayPill(
                                    text = "Play",
                                    onClick = {},
                                    modifier = Modifier.focusRequester(play).testTag("play"),
                                )
                            },
                            modifier = Modifier.padding(top = 48.cpx, start = 48.cpx, end = 48.cpx, bottom = 16.dp),
                        )
                    }
                    items(3) { row ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .padding(16.dp)
                                .testTag("row$row")
                                .focusable(),
                        )
                    }
                }
            }
        }
    }

    private fun settle(): PixelMap {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        return rule.onRoot().captureToImage().toPixelMap()
    }

    private fun Color.near(other: Color) = abs(red - other.red) < .06f && abs(green - other.green) < .06f && abs(blue - other.blue) < .06f

    /** The title, not the placeholder of the picture left of it, which shows it too */
    private fun titleBounds() =
        rule
            .onAllNodesWithText("Bing")
            .fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.left }
            .boundsInRoot

    private fun assertHeaderOnScreen(image: PixelMap) {
        val title = titleBounds()
        val top = (48 * .75f * density).roundToInt()
        assertTrue("title at ${title.top} px, below the page's top padding of $top px", title.top >= top - 2)
        // Its letters are drawn: the title's text colour on the rows of its top half
        val rows = title.top.roundToInt() until (title.top + title.height / 2).roundToInt()
        val inked = rows.any { y -> (title.left.roundToInt() until title.right.roundToInt()).any { x -> image[x, y].near(text) } }
        assertTrue("title drawn", inked)
    }

    /** Presses [key] until [tag] has focus, a few times at most */
    private fun pressUntilFocused(
        key: Key,
        tag: String,
    ) {
        repeat(6) {
            if (rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            rule.onRoot().performKeyInput { pressKey(key) }
            settle()
        }
        rule.onNodeWithTag(tag).assertIsFocused()
    }

    /** Opens the page and focuses Play once it is laid out, as the page does when its item has loaded */
    private fun open() {
        rule.setContent { DetailsPage() }
        settle()
        rule.runOnIdle { play.requestFocus() }
    }

    @Test
    fun `the page opens with the title and poster on screen`() {
        open()
        assertHeaderOnScreen(settle())
    }

    @Test
    fun `coming back up from the rows brings the header back`() {
        open()
        settle()
        pressUntilFocused(Key.DirectionDown, "row1")
        assertTrue("scrolled down", rule.onAllNodesWithText("Bing").fetchSemanticsNodes().none { it.boundsInRoot.bottom > 0 })
        pressUntilFocused(Key.DirectionUp, "play")
        assertHeaderOnScreen(settle())
    }
}
