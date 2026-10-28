package dev.aldoborrero.peke.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
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

/**
 * The gap that keeps whichever of two neighbours [a] and [b] long is focused, its scale-up, border and ring clear of the
 * other by [PekeFocus.Spare]; at least [minGap]
 */
fun focusGapBetween(
    a: Dp,
    b: Dp,
    minGap: Dp = 0.dp,
): Dp = maxOf(minGap, PekeFocus.clearance(a), PekeFocus.clearance(b))

/**
 * Buttons side by side, centred on each other and spaced by [focusGapBetween] their widths, so a focused button's
 * scale-up, border and ring clear its neighbours; a wide pill grows more and gets more room. The first button lines up
 * with what is above the row, so the row's parent leaves [PekeFocus.clearance] of it before and after the row.
 */
@Composable
fun FocusRow(
    modifier: Modifier = Modifier,
    minGap: Dp = 0.dp,
    content: @Composable () -> Unit,
) = Layout(content, modifier, remember(minGap) { FocusSpacing(minGap, horizontal = true) })

/**
 * Buttons stacked, as [FocusRow] side by side: spaced by [focusGapBetween] their heights, lined up at the start
 */
@Composable
fun FocusColumn(
    modifier: Modifier = Modifier,
    minGap: Dp = 0.dp,
    content: @Composable () -> Unit,
) = Layout(content, modifier, remember(minGap) { FocusSpacing(minGap, horizontal = false) })

private class FocusSpacing(
    val minGap: Dp,
    val horizontal: Boolean,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val lengths = placeables.map { if (horizontal) it.width else it.height }
        val gaps = lengths.zipWithNext { a, b -> focusGapBetween(a.toDp(), b.toDp(), minGap).roundToPx() }
        val length = lengths.sum() + gaps.sum()
        val across = placeables.maxOfOrNull { if (horizontal) it.height else it.width } ?: 0
        val width = (if (horizontal) length else across).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (if (horizontal) across else length).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            var at = 0
            placeables.forEachIndexed { i, placeable ->
                if (horizontal) placeable.placeRelative(at, (height - placeable.height) / 2) else placeable.placeRelative(0, at)
                at += lengths[i] + gaps.getOrElse(i) { 0 }
            }
        }
    }
}
