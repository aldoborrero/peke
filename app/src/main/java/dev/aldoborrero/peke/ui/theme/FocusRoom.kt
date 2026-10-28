package dev.aldoborrero.peke.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The room a row, list or grid of kids' cards leaves for a focused card's scale-up, border and ring, plus
 * [PekeFocus.Spare]: [side] before the first card and after the last, [gap] between cards, and [top] and [bottom]
 * against the title above and what comes below.
 *
 * The row gets [padding] as its content padding and [bleedHorizontally] by [side], so its cards line up with the title
 * above it while unfocused and the first and last cards' rings grow into the padding instead of being cut.
 */
@Immutable
data class FocusRoom(
    val gap: Dp,
    val side: Dp,
    val top: Dp,
    val bottom: Dp,
) {
    val padding: PaddingValues get() = PaddingValues(start = side, end = side, top = top, bottom = bottom)

    companion object {
        /**
         * For cards up to [card] in size, at least [minGap] apart; [grow] false for rows that don't scale up when
         * focused
         */
        fun of(
            card: DpSize,
            minGap: Dp = 0.dp,
            grow: Boolean = true,
        ): FocusRoom {
            val across = PekeFocus.clearance(card.width, grow)
            val along = PekeFocus.clearance(card.height, grow)
            return FocusRoom(gap = maxOf(minGap, across), side = across, top = along, bottom = along)
        }
    }
}

/**
 * Lays this out [by] wider on each side than its parent allows and shifts it left by as much, so its first and last
 * [by] sit past the parent's edges: a row's content padding, which its cards' focus rings grow into
 */
fun Modifier.bleedHorizontally(by: Dp): Modifier =
    layout { measurable, constraints ->
        val px = by.roundToPx()
        val wider =
            if (constraints.hasBoundedWidth) {
                constraints.copy(maxWidth = constraints.maxWidth + 2 * px, minWidth = constraints.minWidth + 2 * px)
            } else {
                constraints
            }
        val placeable = measurable.measure(wider)
        layout((placeable.width - 2 * px).coerceAtLeast(0), placeable.height) { placeable.place(-px, 0) }
    }
