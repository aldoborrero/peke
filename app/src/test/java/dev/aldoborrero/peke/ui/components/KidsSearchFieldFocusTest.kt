package dev.aldoborrero.peke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekePalette
import dev.aldoborrero.peke.ui.theme.PekeTheme
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * The kids' search field takes Peke's focus like every other control (a border and a ring outside the pill), not the
 * text field's coral underline; and the filter button next to it is named for TalkBack before it shows its title
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidsSearchFieldFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val box = FocusRequester()
    private val other = FocusRequester()
    private lateinit var colors: PekePalette
    private var density = 1f

    @Composable
    private fun Page(focusBox: Boolean) {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) {
            inputMode.requestInputMode(InputMode.Keyboard)
            if (focusBox) box.requestFocus() else other.requestFocus()
        }
        CompositionLocalProvider(
            LocalKidsMode provides KidsModeState.Active(profile = mockk(relaxed = true), user = mockk(relaxed = true)),
        ) {
            PekeTheme {
                colors = LocalPekeColors.current
                density = LocalDensity.current.density
                Column(Modifier.fillMaxSize().background(colors.background).padding(48.dp)) {
                    SearchEditTextBox(
                        value = "bing",
                        onValueChange = {},
                        onSearchClick = {},
                        readOnly = true,
                        modifier = Modifier.width(500.dp).testTag("box").focusRequester(box),
                    )
                    ExpandablePlayButton(
                        title = R.string.view_options,
                        resume = Duration.ZERO,
                        icon = painterResource(PekeIcon.FILTER.outline),
                        onClick = {},
                        modifier = Modifier.padding(top = 48.dp).focusRequester(other),
                    )
                    Column(Modifier.size(10.dp).focusable()) {}
                }
            }
        }
    }

    private fun capture(focusBox: Boolean): PixelMap {
        rule.setContent { Page(focusBox) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        return rule.onRoot().captureToImage().toPixelMap()
    }

    private fun Color.near(other: Color) = abs(red - other.red) < .06f && abs(green - other.green) < .06f && abs(blue - other.blue) < .06f

    private fun px(dp: Float) = (dp * density).roundToInt()

    /** The box's bounds in px: left, top, right, bottom */
    private fun boxBounds() =
        rule.onNodeWithTag("box").fetchSemanticsNode().boundsInRoot.let {
            listOf(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
        }

    /** Halfway into the ring, outside the border, [side] -1 left of the box or +1 right of it */
    private fun ringX(
        left: Int,
        right: Int,
        side: Int,
    ): Int {
        val into = px(PekeFocus.BorderWidth.value + PekeFocus.RingWidth.value / 2)
        return if (side < 0) left - into else right + into
    }

    @Test
    fun `the focused field has the ring outside it and no underline`() {
        val image = capture(focusBox = true)
        val (left, top, right, bottom) = boxBounds()
        val y = (top + bottom) / 2
        assertTrue("ring left of the field", image[ringX(left, right, -1), y].near(colors.primary))
        assertTrue("ring right of the field", image[ringX(left, right, 1), y].near(colors.primary))
        // The underline drew 4 dp of primary along the inside of the bottom edge, in the middle
        val x = (left + right) / 2
        val underline = (bottom - px(4f) until bottom - 1).any { image[x, it].near(colors.primary) }
        assertFalse("no underline", underline)
    }

    @Test
    fun `the field without focus has no ring`() {
        val image = capture(focusBox = false)
        val (left, top, right, bottom) = boxBounds()
        assertFalse(image[ringX(left, right, -1), (top + bottom) / 2].near(colors.primary))
    }

    @Test
    fun `the filter button is named before its title shows`() {
        capture(focusBox = true)
        rule.onNodeWithContentDescription("View options").assert(hasClickAction())
    }
}
