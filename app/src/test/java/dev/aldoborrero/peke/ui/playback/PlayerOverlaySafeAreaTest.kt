package dev.aldoborrero.peke.ui.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.media3.common.Player
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.playback.overlay.PlaybackOverlay
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.SafeArea
import dev.aldoborrero.peke.ui.util.Clock
import dev.aldoborrero.peke.ui.util.LocalClock
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * The player overlay, kids' and grown-ups', keeps its buttons and the clock inside Google's overscan-safe area (48 dp
 * at the sides, 27 dp top and bottom; TV-OV)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PlayerOverlaySafeAreaTest {
    @get:Rule
    val rule = createComposeRule()

    private var density = 1f

    private val player =
        mockk<Player>(relaxed = true) {
            every { duration } returns 600_000
            every { currentPosition } returns 60_000
            every { isCommandAvailable(any()) } returns true
        }

    @Composable
    private fun Overlay(
        kids: Boolean,
        showPlay: Boolean = false,
    ) {
        val controls = ControllerViewState(hideMilliseconds = Long.MAX_VALUE, controlsEnabled = true).apply { showControls() }
        CompositionLocalProvider(
            LocalImageUrlService provides mockk<ImageUrlService>(relaxed = true),
            LocalClock provides Clock(timeString = mutableStateOf("4:24 PM")),
            LocalKidsMode provides
                if (kids) KidsModeState.Active(profile = mockk(relaxed = true), user = mockk(relaxed = true)) else KidsModeState.Inactive,
        ) {
            PekeTheme {
                density = LocalDensity.current.density
                PlaybackOverlay(
                    item = null,
                    chapters = emptyList(),
                    player = player,
                    controllerViewState = controls,
                    showPlay = showPlay,
                    showClock = true,
                    previousEnabled = true,
                    nextEnabled = false,
                    seekEnabled = true,
                    seekBack = 10.seconds,
                    skipBackOnResume = null,
                    seekForward = 30.seconds,
                    onPlaybackActionClick = {},
                    onClickPlaybackDialogType = {},
                    onSeekBarChange = {},
                    showDebugInfo = false,
                    currentPlayback = null,
                    currentSegment = null,
                    analyticsState = AnalyticsState(),
                    queue = emptyList(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
    }

    private fun px(dp: Float) = dp * density

    /** The safe area in px: 960×540 dp less 48 dp at the sides and 27 dp top and bottom */
    private val safe get() =
        Rect(
            px(SafeArea.Horizontal.value),
            px(SafeArea.Vertical.value),
            px(960 - SafeArea.Horizontal.value),
            px(540 - SafeArea.Vertical.value),
        )

    private fun assertInside(
        what: String,
        bounds: Rect,
    ) {
        // A pixel of rounding allowed
        val area = safe.inflate(1f)
        assertTrue("$what at $bounds, outside $area", area.contains(bounds.topLeft) && area.contains(bounds.bottomRight - Offset(1f, 1f)))
    }

    private fun buttons(): List<SemanticsNode> = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    private fun assertControlsInSafeArea() {
        val buttons = buttons()
        assertTrue("buttons shown", buttons.size >= 5)
        buttons.forEach {
            val name = it.config.getOrElse(SemanticsProperties.ContentDescription) { listOf("a button") }.joinToString()
            assertInside(name, it.boundsInRoot)
        }
        val clock = rule.onNodeWithText("4:24 PM").getUnclippedBoundsInRoot()
        assertInside("clock", Rect(px(clock.left.value), px(clock.top.value), px(clock.right.value), px(clock.bottom.value)))
    }

    @Test
    fun `the grown-up player's buttons and clock keep to the safe area`() {
        rule.setContent { Overlay(kids = false) }
        settle()
        assertControlsInSafeArea()
    }

    @Test
    fun `the kids player's buttons and clock keep to the safe area`() {
        rule.setContent { Overlay(kids = true) }
        settle()
        assertControlsInSafeArea()
    }
}
