package dev.aldoborrero.peke.kids.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.tv.material3.MaterialTheme
import dev.aldoborrero.peke.ui.Cards
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The picked kid's check mark is drawn over the focused avatar's ring, not under it (spec 026): where the ring crosses
 * the badge's background, around its icon, the pixels are the badge's background, not the ring.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidBadgeRenderTest {
    @get:Rule
    val rule = createComposeRule()

    private fun Color.near(other: Color) = abs(red - other.red) < .04f && abs(green - other.green) < .04f && abs(blue - other.blue) < .04f

    @Test
    fun `the check mark covers the focus ring where they cross`() {
        val focus = FocusRequester()
        var badgeBackground = Color.Unspecified
        var ring = Color.Unspecified
        rule.setContent {
            val inputMode = LocalInputModeManager.current
            PekeTheme {
                badgeBackground = MaterialTheme.colorScheme.surface
                ring = MaterialTheme.colorScheme.primary
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    KidAvatarCard(
                        id = UUID.randomUUID(),
                        name = "Manuel",
                        imageUrl = null,
                        selected = true,
                        onClick = {},
                        modifier = Modifier.focusRequester(focus),
                    )
                }
            }
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
        }
        rule.waitForIdle()
        rule.runOnIdle { focus.requestFocus() }
        rule.waitForIdle()
        rule.onNode(hasClickAction()).assertIsFocused()

        val avatar = rule.onNode(hasClickAction()).fetchSemanticsNode().boundsInRoot
        val px = rule.density.density * PekeFocus.KID_SCALE
        // The focus ring's middle, and the badge's centre on the avatar's edge, from the avatar's centre
        val ringRadius = (Cards.serverUserCircle.value / 2 + PekeFocus.BorderWidth.value + PekeFocus.RingWidth.value / 2) * px
        val badgeCentre = Cards.serverUserCircle.value / 2 * px
        // Between the icon's circle (10 of 24) and the badge's background circle: the background's colour
        val fromBadge = KidBadgeSize.value * (10f / 24 + .5f) / 2 * px
        // Where the ring passes at that distance from the badge's centre, a little past 45°
        val angle =
            PI / 4 + acos((ringRadius * ringRadius + badgeCentre * badgeCentre - fromBadge * fromBadge) / (2 * ringRadius * badgeCentre))
        val x = (avatar.center.x + ringRadius * cos(angle)).roundToInt()
        val y = (avatar.center.y + ringRadius * sin(angle)).roundToInt()
        val pixel = rule.onRoot().captureToImage().toPixelMap()[x, y]
        assertTrue("at $x,$y: $pixel, ring $ring", pixel.near(badgeBackground))
    }
}
