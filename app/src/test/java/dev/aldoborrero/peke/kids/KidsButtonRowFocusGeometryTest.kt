package dev.aldoborrero.peke.kids

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.playback.KidsNextUpPanel
import dev.aldoborrero.peke.kids.ui.KidsDetailsHeader
import dev.aldoborrero.peke.kids.ui.KidsPlayPill
import dev.aldoborrero.peke.kids.ui.KidsRoundButton
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekePalette
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.focusGapBetween
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
 * Kid button rows (DetailsPekeTV's Play pill and round buttons, KNextUpPekeTV's panel): a focused button's scale-up,
 * border and ring clear the buttons next to it and the text above, and the unfocused buttons line up with the title
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidsButtonRowFocusGeometryTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var colors: PekePalette
    private var density = 1f
    private val requesters = List(3) { FocusRequester() }
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Composable
    private fun Page(content: @Composable () -> Unit) {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
        CompositionLocalProvider(LocalImageUrlService provides mockk<ImageUrlService>(relaxed = true)) {
            PekeTheme {
                colors = LocalPekeColors.current
                density = LocalDensity.current.density
                Box(Modifier.fillMaxSize().background(colors.background).padding(48.cpx)) { content() }
            }
        }
    }

    private fun details(focus: Int): PixelMap {
        rule.setContent {
            Page {
                KidsDetailsHeader(
                    poster = null,
                    title = "Bing",
                    overview = "Bing is a curious bunny",
                    buttons = {
                        KidsPlayPill(text = "Play episode 1", onClick = {}, modifier = Modifier.focusRequester(requesters[0]).testTag("b0"))
                        KidsRoundButton(
                            description = R.string.shuffle,
                            glyph = R.string.fa_shuffle,
                            onClick = {},
                            modifier = Modifier.focusRequester(requesters[1]).testTag("b1"),
                        )
                        KidsRoundButton(
                            description = R.string.mark_watched,
                            icon = painterResource(PekeIcon.WATCHED.outline),
                            onClick = {},
                            modifier = Modifier.focusRequester(requesters[2]).testTag("b2"),
                        )
                    },
                )
            }
        }
        rule.runOnIdle { requesters[focus].requestFocus() }
        return settle()
    }

    private fun settle(): PixelMap {
        // Past the focus animation
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        return rule.onRoot().captureToImage().toPixelMap()
    }

    private fun px(dp: Float) = (dp * density).roundToInt()

    private fun px(dp: Dp) = px(dp.value)

    private fun Color.near(other: Color) = abs(red - other.red) < .04f && abs(green - other.green) < .04f && abs(blue - other.blue) < .04f

    private fun DpRect.px() = listOf(px(left), px(top), px(right), px(bottom))

    /** The title [text], not the placeholder of the picture left of it, which shows it too */
    private fun titleBounds(text: String) =
        rule
            .onAllNodesWithText(text)
            .fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.left }
            .let { b -> with(b.boundsInRoot) { listOf(left, top, right, bottom).map { it.roundToInt() } } }

    private fun button(index: Int) = rule.onNodeWithTag("b$index").getUnclippedBoundsInRoot().px()

    /** How far a focused button [length] px long grows past each end, its border and ring included */
    private fun outset(length: Int) = px(PekeFocus.outset((length / density).dp, grow = true))

    /** The run of [page]-coloured pixels on row [y], going [step] from [from] past the focused button's ring */
    private fun PixelMap.room(
        y: Int,
        from: Int,
        step: Int,
        page: Color,
    ): Int {
        var x = from
        while (!this[x, y].near(page)) x += step
        var count = 0
        while (this[x, y].near(page)) {
            count++
            x += step
        }
        return count
    }

    @Test
    fun `unfocused buttons line up with the title`() {
        details(focus = 1)
        val (titleLeft) = titleBounds("Bing")
        val (left) = button(0)
        assertEquals(titleLeft.toFloat(), left.toFloat(), 1f)
    }

    @Test
    fun `the focused Play pill's ring clears the round button next to it`() {
        val image = details(focus = 0)
        val (left, top, right, bottom) = button(0)
        val (nextLeft) = button(1)
        val y = (top + bottom) / 2
        val room = image.room(y, right, 1, colors.background)
        assertTrue("$room px between Play's ring and the next button", room >= px(PekeFocus.Spare) - 1)
        assertTrue("Play's ring ends before the next button", right + outset(right - left) < nextLeft)
        assertTrue("next button's edge untouched", !image[nextLeft, y].near(colors.primary))
    }

    @Test
    fun `a focused round button's ring clears Play and the button after it`() {
        val image = details(focus = 1)
        val (left, top, right, bottom) = button(1)
        val y = (top + bottom) / 2
        val before = image.room(y, left, -1, colors.background)
        val after = image.room(y, right, 1, colors.background)
        assertTrue("$before px between the ring and Play", before >= px(PekeFocus.Spare) - 1)
        assertTrue("$after px between the ring and the next button", after >= px(PekeFocus.Spare) - 1)
    }

    @Test
    fun `the focused Play pill's ring isn't cut and clears the overview above`() {
        val image = details(focus = 0)
        val (left, top, right, bottom) = button(0)
        val x = (left + right) / 2
        // The ring's outer edge, then halfway into the ring
        val outer = top - outset(bottom - top)
        val ring = outer + px(PekeFocus.RingWidth * PekeFocus.KID_SCALE) / 2
        assertTrue("ring above Play at $x,$ring", image[x, ring].near(colors.primary))
        val (_, _, _, overview) = rule.onNodeWithText("Bing is a curious bunny").getUnclippedBoundsInRoot().px()
        assertTrue("room between the overview at $overview and the ring at $outer", outer - overview >= px(PekeFocus.Spare) - 2)
    }

    @Test
    fun `next-up panel - Play now's ring clears Stop and the title above`() {
        val title = "Bing: Carrot"
        rule.setContent {
            Page {
                KidsNextUpPanel(
                    itemId = null,
                    title = title,
                    imageUrl = null,
                    secondsLeft = null,
                    countdownSeconds = 10,
                    onPlayNow = {},
                    onStop = {},
                )
            }
        }
        val image = settle()
        val (left, top, right, bottom) = rule.onNodeWithText(context.getString(R.string.kids_play_now)).getUnclippedBoundsInRoot().px()
        val (stopLeft) = rule.onNodeWithText(context.getString(R.string.kids_stop)).getUnclippedBoundsInRoot().px()
        val y = (top + bottom) / 2
        // The panel's colour, in its padding left of the picture
        val panel = image[px(48.cpx) + px(8f), y]
        val room = image.room(y, right, 1, panel)
        assertTrue("$room px between Play now's ring and Stop", room >= px(PekeFocus.Spare) - 1)
        assertTrue("Play now's ring ends before Stop", right + outset(right - left) < stopLeft)
        val (_, _, _, titleBottom) = titleBounds(title)
        assertTrue("room above Play now's ring", top - outset(bottom - top) - titleBottom >= px(PekeFocus.Spare) - 2)
    }

    @Test
    fun `buttons are spaced for the wider of two neighbours`() {
        val pill = 300.dp
        val round = 54.dp
        val gap = focusGapBetween(pill, round, 15.dp)
        assertTrue(gap >= PekeFocus.clearance(pill) && gap >= PekeFocus.clearance(round) && gap >= 15.dp)
        assertEquals(40.dp, focusGapBetween(0.dp, 0.dp, 40.dp))
    }
}
