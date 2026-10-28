package dev.aldoborrero.peke.kids.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.ui.cards.ItemRowTitle
import dev.aldoborrero.peke.ui.theme.FocusRoom
import dev.aldoborrero.peke.ui.theme.bleedHorizontally
import dev.aldoborrero.peke.ui.theme.cpx

/**
 * A kids row of cards under its [title] (DetailsPekeTV): the cards line up with the title while unfocused, and [room]
 * keeps a focused card's scale-up, border and ring clear of the row's edges, the title and the next card
 *
 * @param rowModifier goes on the row of cards, for the caller's focus restorer
 */
@Composable
fun KidsCardRow(
    title: String,
    room: FocusRoom,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = modifier) {
        ItemRowTitle(title, startPadding = 0.dp)
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(room.gap),
            contentPadding = room.padding,
            modifier = Modifier.bleedHorizontally(room.side).fillMaxWidth().then(rowModifier),
            content = content,
        )
    }
}

/**
 * An episode card on a kid's season page (DetailsPekeTV): 260 by 146 canvas px
 */
val KidsEpisodeCardSize = DpSize(260.cpx, 146.cpx)

/**
 * A poster in a kids row, as the seasons and "more like this" (KSeasonsPekeTV): 180 by 264 canvas px
 */
val KidsPosterCardSize = DpSize(180.cpx, 264.cpx)

/**
 * The board's 22 px between cards, the least a row leaves
 */
val KidsMinCardGap = 22.cpx
