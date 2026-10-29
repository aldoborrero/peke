package dev.aldoborrero.peke.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Google's overscan-safe area for TV (training/tv/playback/compose/layouts): what must stay on screen, as the clock and
 * the player's title, bar and buttons, keeps 48 dp (5%) from the left and right edges and 27 dp from the top and
 * bottom. On the canvas that is 64 px and 36 px. Rows that scroll sideways may still run to the edge.
 */
object SafeArea {
    val Horizontal = 48.dp
    val Vertical = 27.dp
    val padding = PaddingValues(horizontal = Horizontal, vertical = Vertical)
}
