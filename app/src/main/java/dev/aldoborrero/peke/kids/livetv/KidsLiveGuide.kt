package dev.aldoborrero.peke.kids.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.ui.KidsCardSurface
import dev.aldoborrero.peke.kids.ui.KidsEmptyMessage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.formatTime
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.theme.FocusRoom
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.SafeArea
import dev.aldoborrero.peke.ui.theme.bleedHorizontally
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.tryRequestFocus
import dev.aldoborrero.peke.ui.util.LocalClock

/**
 * The kid's live TV (P031LiveB2Page): "Watch live" and a full-width row per channel, with what is on now big (the
 * still, the series on up to two lines, the episode, the channel with the time left and how far it has gone) and what
 * comes later small ("Later · 19:55", its picture and series). Clicking a row plays the channel; the channels are the
 * kid's own (004). The guide is three hours from the plugin, read again only when it runs out ([KidsLiveViewModel]).
 */
@Composable
fun KidsLiveGuide(
    modifier: Modifier = Modifier,
    viewModel: KidsLiveViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(Unit) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    val state by viewModel.state.collectAsState()
    val guide = state.guide
    if (guide == null) {
        LoadingPage(modifier)
        return
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.channels.isNotEmpty()) { firstFocus.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(28.cpx),
        // The rows' rings keep inside the TV-safe edge on the right (spec 028)
        modifier = modifier.fillMaxSize().padding(top = 40.cpx, start = 48.cpx, end = SafeArea.PageHorizontal),
    ) {
        Text(
            text = stringResource(R.string.watch_live),
            style = MaterialTheme.typography.displaySmall,
            fontSize = 44.csp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (state.channels.isEmpty()) {
            KidsEmptyMessage(R.string.kids_empty)
        } else {
            // The rows line up with the title, and the list leaves room for a focused row's border and ring
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(maxOf(28.cpx, ChannelRoom.top)),
                contentPadding = ChannelRoom.padding,
                modifier = Modifier.bleedHorizontally(ChannelRoom.side),
            ) {
                itemsIndexed(state.channels, key = { _, channel -> channel.id }) { index, channel ->
                    KidsChannelRow(
                        channel = channel,
                        guide = guide,
                        onClick = {
                            viewModel.navigationManager.navigateTo(Destination.Playback(itemId = channel.id, positionMs = 0L))
                        },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * A channel's row: now on the left and in the middle, later on the right. Now and later only change at a programme's
 * boundary and the time left once a minute, so the row doesn't recompose on the clock's ticks.
 */
@Composable
private fun KidsChannelRow(
    channel: KidsChannel,
    guide: List<LiveProgramme>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    val clock = LocalClock.current
    val nowNext by remember(guide, channel.id, clock) { derivedStateOf { nowNext(guide, channel.id, clock.now.value) } }
    val now = remember(channel, nowNext.now) { LiveCard(channel.id, channel.name, channel.imageTag, nowNext.now) }
    val status = rememberLiveStatus(nowNext.now)
    val nowSpoken = liveCardSpoken(now, status)
    val later = nowNext.next
    val laterSpoken =
        later?.let { stringResource(R.string.kids_live_later_spoken, formatTime(LocalContext.current, it.start), it.name.orEmpty()) }
    KidsCardSurface(
        onClick = onClick,
        onLongClick = {},
        shape = RoundedCornerShape(32.cpx),
        // As wide as the page, so focus doesn't grow it past the page's sides
        grow = false,
        modifier =
            modifier
                .fillMaxWidth()
                .height(ChannelRowHeight)
                .semantics { contentDescription = listOfNotNull(nowSpoken, laterSpoken).joinToString(". ") },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(20.cpx),
        ) {
            Box(Modifier.size(NowPicture).clip(RoundedCornerShape(24.cpx))) {
                LivePictureBox(now, NowPicture.height)
                status?.let { LiveProgress(it.progress, Modifier.align(Alignment.BottomCenter).padding(14.cpx)) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.cpx), modifier = Modifier.weight(1f)) {
                val programme = nowNext.now
                Text(
                    text = programme?.name ?: channel.name.orEmpty(),
                    fontSize = 32.csp,
                    lineHeight = 38.csp,
                    fontWeight = FontWeight.W900,
                    color = colors.text,
                    // Owner's decision: a long series name wraps to a second line here, the home card cuts it
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                programme?.let { episodeLine(it) }?.let {
                    Text(
                        text = it,
                        fontSize = 20.csp,
                        fontWeight = FontWeight.W800,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                status?.let { LiveChannelLine(channel.name, it) }
            }
            if (later != null) {
                KidsLaterColumn(channel, later)
            }
        }
    }
}

/**
 * "Later · 19:55", the later programme's picture at 200×113 and its series
 */
@Composable
private fun KidsLaterColumn(
    channel: KidsChannel,
    later: LiveProgramme,
) {
    val colors = LocalPekeColors.current
    val card = remember(channel, later) { LiveCard(channel.id, channel.name, channel.imageTag, later) }
    Column(verticalArrangement = Arrangement.spacedBy(10.cpx), modifier = Modifier.width(LaterPicture.width)) {
        Text(
            text = stringResource(R.string.kids_live_later, formatTime(LocalContext.current, later.start)),
            fontSize = 18.csp,
            fontWeight = FontWeight.W900,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Box(Modifier.size(LaterPicture).clip(RoundedCornerShape(16.cpx))) {
            LivePictureBox(card, LaterPicture.height, pill = false)
        }
        Text(
            text = later.name.orEmpty(),
            fontSize = 19.csp,
            fontWeight = FontWeight.W800,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "Ep. 12 · The cake badge", or what of it the guide has; null with neither
 */
@Composable
private fun episodeLine(programme: LiveProgramme): String? {
    val number = programme.episodeNumber?.let { stringResource(R.string.kids_episode_short, it) }
    val title = programme.episodeTitle?.takeIf { it.isNotBlank() && it != programme.name }
    return listOfNotNull(number, title).joinToString(" · ").ifEmpty { null }
}

/** A channel's row: 220 canvas px tall, as wide as the page */
private val ChannelRowHeight = 220.cpx

/** Now's still, as big as a home card */
private val NowPicture = DpSize(320.cpx, 180.cpx)

/** Later's picture */
private val LaterPicture = DpSize(200.cpx, 113.cpx)

/**
 * The room the guide leaves for a focused channel row's border and ring, which doesn't grow
 */
private val ChannelRoom = FocusRoom.of(DpSize(0.dp, ChannelRowHeight), grow = false)
