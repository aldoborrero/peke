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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.kids.ui.KidsMinCardGap
import dev.aldoborrero.peke.kids.ui.KidsPosterCardSize
import dev.aldoborrero.peke.ui.rememberInt
import dev.aldoborrero.peke.ui.theme.FocusRoom
import dev.aldoborrero.peke.ui.theme.bleedHorizontally
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
    kidsRoom: FocusRoom = FocusRoom.of(KidsPosterCardSize, KidsMinCardGap),
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

        // DESIGN's 20–24 px between cards, and room above and below for the focus ring and scale-up. Kids (the canvas
        // boards): [kidsRoom], with the first card lined up with the title and the row reaching past both sides into the
        // room its first and last cards' rings need
        val gap = if (kidsLayout) kidsRoom.gap else maxOf(horizontalPadding, CardGap)
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = if (kidsLayout) kidsRoom.padding else PaddingValues(horizontal = gap, vertical = 16.dp),
            modifier =
                Modifier
                    .then(if (kidsLayout) Modifier.bleedHorizontally(kidsRoom.side) else Modifier)
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer(firstFocus)
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(items, contentType = { _, _ -> "card" }) { index, item ->
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
                item(contentType = "viewMore") {
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
