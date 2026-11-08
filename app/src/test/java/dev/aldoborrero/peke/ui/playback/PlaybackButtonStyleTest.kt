package dev.aldoborrero.peke.ui.playback

import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.ui.playback.overlay.PlaybackButtonStyle
import dev.aldoborrero.peke.ui.playback.overlay.playbackButtonStyle
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The kids player's play/pause icon is 36 px in its 76 px circle, proportionally bigger than the 26 px icons of the
 * 62 px buttons beside it (PlayerPekeTV)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PlaybackButtonStyleTest {
    @get:Rule
    val rule = createComposeRule()

    private fun PlaybackButtonStyle.icon(): Dp = size - padding.calculateStartPadding(LayoutDirection.Ltr) * 2

    @Test
    fun `play and pause get a 36 px icon, bigger in proportion than the small buttons' 26 px`() {
        lateinit var large: PlaybackButtonStyle
        lateinit var small: PlaybackButtonStyle
        rule.setContent {
            PekeTheme {
                CompositionLocalProvider(
                    LocalKidsMode provides KidsModeState.Active(profile = mockk(relaxed = true), user = mockk(relaxed = true)),
                ) {
                    large = playbackButtonStyle(large = true)
                    small = playbackButtonStyle(large = false)
                }
            }
        }
        rule.waitForIdle()
        assertEquals(76.cpx, large.size)
        assertEquals(36.cpx, large.icon())
        assertEquals(62.cpx, small.size)
        assertEquals(26.cpx, small.icon())
        assertTrue(large.icon() / large.size > small.icon() / small.size)
    }
}
