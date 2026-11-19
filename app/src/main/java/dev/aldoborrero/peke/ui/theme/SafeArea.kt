package dev.aldoborrero.peke.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * TV-safe margins (design/DESIGN.md, Shape and space).
 *
 * The player keeps Google's Compose overscan area (training/tv/playback/compose/layouts): its clock, title, bar and
 * buttons keep [Horizontal] (48 dp, 5%) from the left and right edges and [Vertical] (27 dp) from the top and bottom.
 *
 * Every other page keeps Android TV's overscan guidance, [EdgeHorizontal] (58 dp) from the sides and [EdgeVertical]
 * (28 dp) from the top and bottom, for content and focus rings alike (spec 028). Rows that scroll sideways may still
 * run to the screen's edge, but their focused card stops inside it.
 */
object SafeArea {
    val Horizontal = 48.dp
    val Vertical = 27.dp
    val padding = PaddingValues(horizontal = Horizontal, vertical = Vertical)

    /**
     * The edge nothing that matters on a page crosses, a focused item's ring included: 116 by 56 px on a 1080p TV
     */
    val EdgeHorizontal = 58.dp
    val EdgeVertical = 28.dp

    /**
     * How far past an item a focus that doesn't scale up draws: the border and the ring
     */
    private val Ring = PekeFocus.outset(0.dp, grow = false)

    /**
     * Where a full-screen page's content starts and ends, so an element at its side keeps its focus ring (border and
     * ring, no scale-up) inside [EdgeHorizontal] and [EdgeVertical]
     */
    val PageHorizontal = EdgeHorizontal + Ring
    val PageVertical = EdgeVertical + Ring

    /**
     * A grown-up page's padding: [PageHorizontal] and [PageVertical], one value for every grown-up page
     */
    val page = PaddingValues(horizontal = PageHorizontal, vertical = PageVertical)
}
