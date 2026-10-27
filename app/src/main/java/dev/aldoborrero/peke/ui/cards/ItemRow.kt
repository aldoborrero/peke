package dev.aldoborrero.peke.ui.cards

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.ui.rememberInt
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.tryRequestFocus

@Composable
fun <T> ItemRow(
    title: String,
    items: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    showViewMore: Boolean = false,
    viewMoreCardContent: @Composable (Modifier) -> Unit = {},
    kidsLayout: Boolean = false,
) {
    val state = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }
    var position by rememberInt()

    val currentOnClickItem by rememberUpdatedState(onClickItem)
    val currentOnLongClickItem by rememberUpdatedState(onLongClickItem)

    Column(
        verticalArrangement = Arrangement.spacedBy(if (kidsLayout) 0.dp else 8.dp),
        modifier =
            modifier.focusProperties {
                onEnter = {
                    focusRequester.tryRequestFocus()
                }
            },
    ) {
        ItemRowTitle(title, startPadding = if (kidsLayout) 0.dp else 8.dp)

        // DESIGN's 20–24 px between cards, and room above and below for the focus ring and scale-up
        val gap = maxOf(horizontalPadding, CardGap)
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(gap),
            // Kids (the canvas boards): the first card lines up with the title and the row reaches past both sides by
            // the gap, so the focus ring isn't cut; 14 px from the title to the cards, widened to 18 dp so the ring and scale-up (about 15 dp) clear the title
            contentPadding = PaddingValues(horizontal = gap, vertical = if (kidsLayout) KidsRowRingRoom else 16.dp),
            modifier =
                Modifier
                    .then(if (kidsLayout) Modifier.bleed(gap) else Modifier)
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer(firstFocus)
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(items) { index, item ->
                val cardModifier =
                    remember(index, position) {
                        if (index == position) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }
                    }

                val onClick =
                    remember(index, item) {
                        {
                            position = index
                            if (item != null) currentOnClickItem(index, item)
                        }
                    }

                val onLongClick =
                    remember(index, item) {
                        {
                            position = index
                            if (item != null) currentOnLongClickItem(index, item)
                        }
                    }

                cardContent.invoke(
                    index,
                    item,
                    cardModifier,
                    onClick,
                    onLongClick,
                )
            }
            if (showViewMore) {
                item {
                    val cardModifier =
                        remember(items.size, position) {
                            if (position == items.size) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            }
                        }
                    viewMoreCardContent.invoke(cardModifier)
                }
            }
        }
    }
}

@Composable
@NonRestartableComposable
fun ItemRowTitle(
    title: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = 8.dp,
) = Text(
    text = title,
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = modifier.padding(start = startPadding),
)

/**
 * The smallest gap between cards in a row: 24 canvas px (design/DESIGN.md, Shape and space)
 */
val CardGap = 18.dp

/**
 * Lays the row out [by] wider on each side than its parent and shifts it left by as much, so its content padding sits
 * outside the parent's edges
 */
private fun Modifier.bleed(by: Dp): Modifier =
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
 * Above and below a kids row's cards: the board's 14 px to the title, widened so a focused card's ring and scale-up
 * (4.5 + 6 dp and 3.5%) don't cover the title
 */
private val KidsRowRingRoom = 18.dp
