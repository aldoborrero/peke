package dev.aldoborrero.peke.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import dev.aldoborrero.peke.kids.ui.KidsCardRow
import dev.aldoborrero.peke.kids.ui.KidsEpisodeCardSize
import dev.aldoborrero.peke.kids.ui.KidsMinCardGap
import dev.aldoborrero.peke.kids.ui.KidsPictureCard
import dev.aldoborrero.peke.kids.ui.KidsPosterCardSize
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.theme.FocusRoom
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekePalette
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import io.mockk.mockk
import org.jellyfin.sdk.model.api.ImageType
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
 * A kids details row (DetailsPekeTV): its cards line up with the section title while unfocused, and a focused card's
 * scale-up, border and ring are neither cut by the row nor reach the next card or the titles
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidsRowFocusGeometryTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var colors: PekePalette
    private var density = 1f
    private val requesters = List(3) { FocusRequester() }
    private var composed = false
    private val card = KidsEpisodeCardSize

    @Composable
    private fun Page() {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
        CompositionLocalProvider(LocalImageUrlService provides mockk<ImageUrlService>(relaxed = true)) { Content() }
    }

    @Composable
    private fun Content() {
        PekeTheme {
            colors = LocalPekeColors.current
            density = LocalDensity.current.density
            Box(Modifier.fillMaxSize().background(colors.background).padding(start = PAGE_START, top = PAGE_TOP)) {
                KidsCardRow(title = "Episodes", room = FocusRoom.of(card, KidsMinCardGap), modifier = Modifier.testTag("row")) {
                    itemsIndexed(requesters) { index, requester ->
                        KidsPictureCard(
                            item = null,
                            title = "Episode ${index + 1}",
                            imageType = ImageType.PRIMARY,
                            width = card.width,
                            height = card.height,
                            corner = 22.cpx,
                            onClick = {},
                            onLongClick = {},
                            modifier = Modifier.focusRequester(requester).testTag("card$index"),
                        )
                    }
                }
            }
        }
    }

    private fun capture(focus: Int): PixelMap {
        if (!composed) rule.setContent { Page() }
        composed = true
        rule.runOnIdle { requesters[focus].requestFocus() }
        // Past the focus animation
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        return rule.onRoot().captureToImage().toPixelMap()
    }

    private fun px(dp: Float) = (dp * density).roundToInt()

    private fun Color.near(other: Color) = abs(red - other.red) < .04f && abs(green - other.green) < .04f && abs(blue - other.blue) < .04f

    /** The card's picture, in px: its left, top, right and bottom while unfocused */
    private fun cardBounds(index: Int): List<Int> {
        val b = rule.onNodeWithTag("card$index").getUnclippedBoundsInRoot()
        return listOf(px(b.left.value), px(b.top.value), px(b.left.value + card.width.value), px(b.top.value + card.height.value))
    }

    /** How far a focused card grows past each side, in px */
    private fun growth() = px(card.width.value * (PekeFocus.KID_SCALE - 1) / 2)

    @Test
    fun `unfocused cards line up with the section title`() {
        capture(focus = 1)
        val (left) = cardBounds(0)
        assertEquals(px(PAGE_START.value).toFloat(), left.toFloat(), 1f)
    }

    @Test
    fun `the first card's border and ring aren't cut on the left`() {
        val image = capture(focus = 0)
        val (left, top, _, bottom) = cardBounds(0)
        val y = (top + bottom) / 2
        val edge = left - growth()
        // Halfway through the ring, then past it on the page
        val ring = edge - px((PekeFocus.BorderWidth.value + PekeFocus.RingWidth.value / 2) * PekeFocus.KID_SCALE)
        assertTrue("ring at $ring,$y is ${image[ring, y]}", image[ring, y].near(colors.primary))
        val outside = edge - px((PekeFocus.BorderWidth.value + PekeFocus.RingWidth.value) * PekeFocus.KID_SCALE) - 2
        assertTrue("page at $outside,$y", image[outside, y].near(colors.background))
    }

    @Test
    fun `the first card's ring isn't cut above and clears the section title`() {
        val image = capture(focus = 0)
        val (left, top, right) = cardBounds(0)
        val x = (left + right) / 2
        val edge = top - px(card.height.value * (PekeFocus.KID_SCALE - 1) / 2)
        val ring = edge - px((PekeFocus.BorderWidth.value + PekeFocus.RingWidth.value / 2) * PekeFocus.KID_SCALE)
        assertTrue("ring at $x,$ring", image[x, ring].near(colors.primary))
        // From the ring's outer edge up to the title, only the page
        val title =
            px(
                rule
                    .onNodeWithText("Episodes")
                    .getUnclippedBoundsInRoot()
                    .bottom.value,
            )
        val outer = edge - px((PekeFocus.BorderWidth.value + PekeFocus.RingWidth.value) * PekeFocus.KID_SCALE) - 1
        assertTrue("room between the title at $title and the ring at $outer", outer - title >= px(PekeFocus.Spare.value) - 2)
    }

    @Test
    fun `the focused card's ring clears the next card with room to spare`() {
        val image = capture(focus = 0)
        val (_, top, right, bottom) = cardBounds(0)
        val (nextLeft) = cardBounds(1)
        val y = (top + bottom) / 2
        // Out of the grown card, its border and its ring, to the first pixel of the page
        val outer = (right + growth() until nextLeft).first { image[it, y].near(colors.background) }
        val page = (outer until nextLeft).count { image[it, y].near(colors.background) }
        assertTrue("$page px of page between the ring and the next card", page >= px(PekeFocus.Spare.value) - 1)
        assertTrue("next card's edge at $nextLeft untouched", !image[nextLeft - 1, y].near(colors.primary))
    }

    @Test
    fun `the focused card's title starts at its left edge`() {
        val image = capture(focus = 0)
        val (left, _, right, bottom) = cardBounds(0)
        val titleTop = bottom + px(PekeFocus.clearance(card.height).value)
        val band = titleTop until titleTop + px(14f)
        val ink = (left - growth() - px(8f) until right).first { x -> band.any { y -> !image[x, y].near(colors.background) } }
        assertEquals("title's first ink", (left - growth()).toFloat(), ink.toFloat(), px(2.5f).toFloat())
    }

    @Test
    fun `every kids row size leaves room for the focus at its ends, between cards and above and below`() {
        for (size in listOf(KidsEpisodeCardSize, KidsPosterCardSize, DpSize(140.cpx, 200.cpx))) {
            val room = FocusRoom.of(size, KidsMinCardGap)
            val across = size.width.value * (PekeFocus.KID_SCALE - 1) / 2 + 10.5f * PekeFocus.KID_SCALE + 4
            val along = size.height.value * (PekeFocus.KID_SCALE - 1) / 2 + 10.5f * PekeFocus.KID_SCALE + 4
            assertTrue("$size gap", room.gap.value >= across - .01f && room.gap >= KidsMinCardGap)
            assertTrue("$size side", room.side.value >= across - .01f)
            assertTrue("$size top", room.top.value >= along - .01f)
            assertTrue("$size bottom", room.bottom.value >= along - .01f)
        }
    }

    private companion object {
        val PAGE_START = 48.cpx
        val PAGE_TOP = 48.cpx
    }
}
