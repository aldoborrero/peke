package dev.aldoborrero.peke.kids

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.timer.KidsTimerLabelPlacement
import dev.aldoborrero.peke.kids.ui.TimeLeftBar
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The time left sits at the left, in the middle or at the right of the bar, at the top and the bottom (spec 022 US2),
 * and at either end keeps DESIGN's 64 px (48 dp) overscan margin
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-mdpi")
class TimeLeftBarPlacementTest {
    @get:Rule
    val rule = createComposeRule()

    private fun label(
        position: KidsTimerBarPosition,
        placement: KidsTimerLabelPlacement,
    ): Rect {
        rule.setContent {
            PekeTheme {
                Box(Modifier.fillMaxSize()) {
                    TimeLeftBar(
                        fraction = { .5f },
                        minutes = 20,
                        nearEnd = false,
                        position = position,
                        placement = placement,
                        modifier =
                            Modifier.align(
                                if (position ==
                                    KidsTimerBarPosition.BOTTOM
                                ) {
                                    Alignment.BottomCenter
                                } else {
                                    Alignment.TopCenter
                                },
                            ),
                    )
                }
            }
        }
        rule.waitForIdle()
        return rule.onNodeWithContentDescription("minutes left", substring = true).fetchSemanticsNode().boundsInRoot
    }

    // mdpi: 1 px = 1 dp, so the screen is 960 wide and the margin 48
    private val width = 960f
    private val margin = 48f

    private fun check(
        position: KidsTimerBarPosition,
        placement: KidsTimerLabelPlacement,
    ) {
        val label = label(position, placement)
        when (placement) {
            KidsTimerLabelPlacement.START -> assertEquals(margin, label.left, 1f)
            KidsTimerLabelPlacement.CENTER -> assertEquals(width / 2, label.center.x, 1f)
            KidsTimerLabelPlacement.END -> assertEquals(width - margin, label.right, 1f)
        }
        if (position == KidsTimerBarPosition.TOP) {
            assertTrue("at the top: $label", label.bottom < 540f / 2)
        } else {
            assertTrue("at the bottom: $label", label.top > 540f / 2)
        }
    }

    @Test
    fun `top left`() = check(KidsTimerBarPosition.TOP, KidsTimerLabelPlacement.START)

    @Test
    fun `top centre`() = check(KidsTimerBarPosition.TOP, KidsTimerLabelPlacement.CENTER)

    @Test
    fun `top right`() = check(KidsTimerBarPosition.TOP, KidsTimerLabelPlacement.END)

    @Test
    fun `bottom left`() = check(KidsTimerBarPosition.BOTTOM, KidsTimerLabelPlacement.START)

    @Test
    fun `bottom centre`() = check(KidsTimerBarPosition.BOTTOM, KidsTimerLabelPlacement.CENTER)

    @Test
    fun `bottom right`() = check(KidsTimerBarPosition.BOTTOM, KidsTimerLabelPlacement.END)
}
