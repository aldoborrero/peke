package dev.aldoborrero.peke.kids.livetv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.ui.KidsCardSurface
import dev.aldoborrero.peke.kids.ui.KidsEmptyMessage
import dev.aldoborrero.peke.kids.ui.KidsLiveBadge
import dev.aldoborrero.peke.kids.ui.tileColor
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.data.RowColumn
import dev.aldoborrero.peke.ui.detail.livetv.LiveTvViewModel
import dev.aldoborrero.peke.ui.detail.livetv.TvChannel
import dev.aldoborrero.peke.ui.detail.livetv.TvProgram
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.theme.BuddyMood
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.tryRequestFocus
import dev.aldoborrero.peke.ui.util.LocalClock
import dev.aldoborrero.peke.util.LoadingState
import java.time.Duration
import java.time.LocalDateTime

/**
 * What a channel card shows (LiveGuidePekeTV): the programme airing [now], the [next] one and how far [now] has gone
 */
data class ChannelNowNext(
    val now: TvProgram?,
    val next: TvProgram?,
    val progress: Float?,
)

/**
 * The programme airing at [at] (start ≤ at < end), the first one starting at or after its end (or after [at] in a gap),
 * and the elapsed part of the one airing. Channels without a guide only have Wholphin's placeholder programmes, which
 * count as no guide.
 */
fun channelNowNext(
    programs: List<TvProgram>,
    at: LocalDateTime,
): ChannelNowNext {
    val guide = programs.filterNot { it.isFake }.sortedBy { it.start }
    val now = guide.firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
    val after = now?.end ?: at
    val next = guide.firstOrNull { it != now && !it.start.isBefore(after) }
    val progress =
        now?.let {
            val length = Duration.between(it.start, it.end).seconds.toFloat()
            if (length > 0) (Duration.between(it.start, at).seconds / length).coerceIn(0f, 1f) else null
        }
    return ChannelNowNext(now, next, progress)
}

/**
 * The kid's live TV (LiveGuidePekeTV): "Watch live" and a full-width card per channel, with the channel's picture on a
 * coloured tile (the buddy when it has none), LIVE and its name, the programme on now, how far it has gone and what
 * comes next. Clicking a card plays the channel, as the guide's channel cell did; the channels are the kid's own
 * (004, [LiveTvViewModel]).
 */
@Composable
fun KidsLiveGuide(
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Pending -> {
            LoadingPage(modifier)
        }

        LoadingState.Loading,
        LoadingState.Success,
        -> {
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(state.channels.isNotEmpty()) { firstFocus.tryRequestFocus() }
            val now by LocalClock.current.now
            Column(
                verticalArrangement = Arrangement.spacedBy(28.cpx),
                modifier = modifier.fillMaxSize().padding(top = 48.cpx, start = 48.cpx, end = 48.cpx),
            ) {
                Text(
                    text = stringResource(R.string.watch_live),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (state.channels.isEmpty()) {
                    KidsEmptyMessage(R.string.kids_empty)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(28.cpx),
                        // Room for the focus ring
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                    ) {
                        itemsIndexed(state.channels, key = { _, channel -> channel.id }) { index, channel ->
                            KidsChannelRow(
                                channel = channel,
                                nowNext =
                                    remember(state.programs, channel.id, now) {
                                        channelNowNext(state.programs.programsByChannel[channel.id].orEmpty(), now)
                                    },
                                onClick = {
                                    viewModel.navigationManager.navigateTo(Destination.Playback(itemId = channel.id, positionMs = 0L))
                                },
                                modifier =
                                    Modifier
                                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                        .onFocusChanged { if (it.hasFocus) viewModel.onFocusChannel(RowColumn(index, 0)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsChannelRow(
    channel: TvChannel,
    nowNext: ChannelNowNext,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    KidsCardSurface(
        onClick = onClick,
        onLongClick = {},
        shape = RoundedCornerShape(32.cpx),
        modifier = modifier.fillMaxWidth().height(208.cpx),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(24.cpx),
        ) {
            ChannelTile(channel)
            Column(verticalArrangement = Arrangement.spacedBy(10.cpx), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.cpx), verticalAlignment = Alignment.CenterVertically) {
                    KidsLiveBadge(size = 16)
                    Text(
                        text = channel.name.orEmpty(),
                        fontSize = 22.csp,
                        fontWeight = FontWeight.W800,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = nowNext.now?.name ?: channel.name.orEmpty(),
                    fontSize = 34.csp,
                    fontWeight = FontWeight.W900,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                nowNext.progress?.let { progress ->
                    Box(Modifier.fillMaxWidth().height(12.cpx).background(colors.text.copy(alpha = .18f), CircleShape)) {
                        Box(Modifier.fillMaxWidth(progress).height(12.cpx).background(colors.time, CircleShape))
                    }
                }
                nowNext.next?.name?.let {
                    Text(
                        text = stringResource(R.string.kids_next_programme, it),
                        fontSize = 20.csp,
                        fontWeight = FontWeight.W800,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The channel's picture on a 160 px tile in one of the theme's colours, or the buddy when it has none or it can't load
 */
@Composable
private fun ChannelTile(channel: TvChannel) {
    val colors = LocalPekeColors.current
    var imageError by remember(channel.imageUrl) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.cpx).clip(RoundedCornerShape(30.cpx)).background(tileColor(colors, channel.id)),
    ) {
        if (channel.imageUrl != null && !imageError) {
            AsyncImage(
                model = channel.imageUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                onError = { imageError = true },
                modifier = Modifier.size(120.cpx),
            )
        } else {
            Image(
                painter = painterResource(BuddyMood.HAPPY.drawable),
                contentDescription = null,
                modifier = Modifier.size(120.cpx, 100.cpx),
            )
        }
    }
}
