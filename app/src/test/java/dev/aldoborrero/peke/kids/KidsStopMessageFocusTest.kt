package dev.aldoborrero.peke.kids

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.aldoborrero.peke.kids.ui.KIDS_STOP_MESSAGE_TAG
import dev.aldoborrero.peke.kids.ui.KidsGrownUpsMessage
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.util.Clock
import dev.aldoborrero.peke.ui.util.LocalClock
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The stop screens (time's up, bedtime, break) start with focus on the message (spec 005 FR-013), which shows nothing
 * for it, so it doesn't look like a button, and does nothing on OK; Down reaches Grown-ups, which shows Peke's focus
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidsStopMessageFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private var grownUps = 0

    @Composable
    private fun Stop() {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
        CompositionLocalProvider(
            LocalKidsMode provides KidsModeState.Active(profile = mockk(relaxed = true), user = mockk(relaxed = true)),
            LocalClock provides Clock(timeString = mutableStateOf("2:34 PM")),
        ) {
            PekeTheme {
                KidsGrownUpsMessage(
                    title = "Time's up!",
                    subtitle = "See you next time, Mia",
                    onClickGrownUps = { grownUps++ },
                )
            }
        }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    private fun start() {
        rule.setContent { Stop() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    private fun message() = rule.onNodeWithTag(KIDS_STOP_MESSAGE_TAG)

    private fun grownUpsButton() = rule.onNode(hasClickAction())

    /** The screen's pixels in [area] grown by 24 px, where a border, a ring or a scale-up would draw */
    private fun pixels(area: Rect): List<Int> {
        val image: ImageBitmap = rule.onRoot().captureToImage()
        val map = image.toPixelMap()
        val grown = area.inflate(24f)
        val left = grown.left.roundToInt().coerceAtLeast(0)
        val top = grown.top.roundToInt().coerceAtLeast(0)
        val right = grown.right.roundToInt().coerceAtMost(image.width)
        val bottom = grown.bottom.roundToInt().coerceAtMost(image.height)
        return (top until bottom step 2).flatMap { y -> (left until right step 2).map { x -> map[x, y].hashCode() } }
    }

    @Test
    fun `the message starts with focus and looks the same with or without it`() {
        start()
        message().assertIsFocused()
        val bounds = message().fetchSemanticsNode().boundsInRoot
        val focused = pixels(bounds)
        press(Key.DirectionDown)
        message().assertIsNotFocused()
        assertEquals("no scale-up", bounds, message().fetchSemanticsNode().boundsInRoot)
        assertTrue("no border or ring while focused", focused == pixels(bounds))
    }

    @Test
    fun `OK on the message does nothing`() {
        start()
        press(Key.DirectionCenter)
        press(Key.Enter)
        assertEquals(0, grownUps)
        message().assertIsFocused()
    }

    @Test
    fun `Down reaches Grown-ups, which shows its focus`() {
        start()
        val bounds = grownUpsButton().fetchSemanticsNode().boundsInRoot
        val unfocused = pixels(bounds)
        press(Key.DirectionDown)
        grownUpsButton().assertIsFocused()
        assertFalse("Grown-ups draws its focus", unfocused == pixels(bounds))
        press(Key.DirectionCenter)
        assertEquals(1, grownUps)
    }
}
