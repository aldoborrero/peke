package dev.aldoborrero.peke.kids.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.ui.cards.SeasonCard
import dev.aldoborrero.peke.ui.components.ExpandablePlayButton
import dev.aldoborrero.peke.ui.ifElse
import dev.aldoborrero.peke.ui.theme.BuddyMood
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlin.time.Duration

/**
 * Shown in kids mode when playback ends with nothing next: watch again, go home or pick something similar
 */
@Composable
fun KidsEndScreen(
    suggestions: List<BaseItem>,
    onWatchAgain: () -> Unit,
    onHome: () -> Unit,
    onClickSuggestion: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val suggestionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // The buddy cheers the kid on at the end of what they watched (design/DESIGN.md, The PekeTV buddy)
        Image(
            painter = painterResource(BuddyMood.CHEERING.drawable),
            contentDescription = null,
            modifier = Modifier.height(120.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .focusGroup()
                    .focusRestorer(focus),
        ) {
            ExpandablePlayButton(
                title = R.string.kids_watch_again,
                resume = Duration.ZERO,
                icon = Icons.Default.Refresh,
                onClick = { onWatchAgain() },
                modifier = Modifier.focusRequester(focus),
            )
            ExpandablePlayButton(
                title = R.string.home,
                resume = Duration.ZERO,
                icon = Icons.Default.Home,
                onClick = { onHome() },
            )
        }
        if (suggestions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.more_like_this),
                style = MaterialTheme.typography.titleLarge,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRestorer(suggestionFocus),
            ) {
                itemsIndexed(suggestions, key = { _, item -> item.id }) { index, item ->
                    SeasonCard(
                        item = item,
                        onClick = { onClickSuggestion(item) },
                        onLongClick = {},
                        imageHeight = 140.dp,
                        modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(suggestionFocus)),
                    )
                }
            }
        }
    }
}
