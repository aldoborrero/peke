package dev.aldoborrero.peke.kids.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.timer.KidsTimerBarSetting
import dev.aldoborrero.peke.kids.ui.KidsCardSurface
import dev.aldoborrero.peke.kids.ui.KidsCardTitle
import dev.aldoborrero.peke.kids.ui.KidsLiveBadge
import dev.aldoborrero.peke.kids.ui.KidsPictureCard
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.util.LocalClock
import org.jellyfin.sdk.model.api.ImageType
import javax.inject.Inject

@HiltViewModel
class KidsTimeLeftViewModel
    @Inject
    constructor(
        val timeLimits: KidsTimeLimits,
        val timerBarSetting: KidsTimerBarSetting,
    ) : ViewModel()

/**
 * What the kid's header shows of their time: [homeTimeLeft] of the current limits and bar setting
 */
@Composable
fun rememberHomeTimeLeft(viewModel: KidsTimeLeftViewModel = hiltViewModel()): HomeTimeLeft? {
    val limits by viewModel.timeLimits.state.collectAsState()
    val position by viewModel.timerBarSetting.position.collectAsState(KidsTimerBarPosition.TOP)
    return homeTimeLeft(limits, position)
}

/**
 * "{N} min left today", or "{N} min left" when a sitting ends first
 */
@Composable
fun timeLeftText(timeLeft: HomeTimeLeft): String =
    pluralStringResource(
        if (timeLeft.today) R.plurals.kids_home_time_left_today else R.plurals.kids_home_time_left,
        timeLeft.minutes,
        timeLeft.minutes,
    )

/**
 * The kids home's header (HomeRailPekeTV): "Hi, Lucy!" and "What do you want to watch?", and on the right the time
 * left, where the time-left bar would be, and the clock when it is on
 */
@Composable
fun KidsHomeHeader(
    showClock: Boolean,
    modifier: Modifier = Modifier,
) {
    val name = (LocalKidsMode.current as? KidsModeState.Active)?.user?.name
    val timeLeft = rememberHomeTimeLeft()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.cpx),
        modifier = modifier,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name?.let { stringResource(R.string.kids_home_greeting, it) }.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 28.csp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.kids_home_question),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        timeLeft?.let { KidsTimeLeftPill(it) }
        if (showClock) {
            val time by LocalClock.current.timeString
            Text(
                text = time,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 20.csp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The time left on a surface pill, in the "time" colour with its icon; from the first warning point bigger, as the
 * bar grows
 */
@Composable
fun KidsTimeLeftPill(
    timeLeft: HomeTimeLeft,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.cpx),
        modifier =
            modifier
                .background(colors.surface, CircleShape)
                .padding(horizontal = 20.cpx, vertical = 10.cpx),
    ) {
        Icon(
            painter = painterResource(PekeIcon.TIME_LEFT.outline),
            contentDescription = null,
            tint = colors.time,
            modifier = Modifier.size(if (timeLeft.nearEnd) 32.cpx else 24.cpx),
        )
        Text(
            text = timeLeftText(timeLeft),
            fontSize = if (timeLeft.nearEnd) 24.csp else 20.csp,
            fontWeight = if (timeLeft.nearEnd) FontWeight.W900 else FontWeight.W800,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.time,
        )
    }
}

/**
 * A kids home card of [kind] (HomeRailPekeTV): wide 320×180 for keep watching, a 240×112 channel card for live,
 * a 140×200 poster otherwise
 */
@Composable
fun KidsHomeCard(
    kind: KidsCardKind,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        KidsCardKind.WIDE -> {
            KidsPictureCard(
                item = item,
                title = item?.title,
                imageType = ImageType.THUMB,
                width = 320.cpx,
                height = 180.cpx,
                corner = 24.cpx,
                onClick = onClick,
                onLongClick = onLongClick,
                playPercent = item?.data?.userData?.playedPercentage ?: 0.0,
                modifier = modifier,
            )
        }

        KidsCardKind.CHANNEL -> {
            KidsChannelCard(
                name = item?.data?.channelName ?: item?.name,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier,
            )
        }

        KidsCardKind.POSTER -> {
            KidsPictureCard(
                item = item,
                title = item?.title,
                imageType = ImageType.PRIMARY,
                width = 140.cpx,
                height = 200.cpx,
                corner = 22.cpx,
                onClick = onClick,
                onLongClick = onLongClick,
                titleSize = 17,
                modifier = modifier,
            )
        }
    }
}

/**
 * A live channel on the home: a surface card with the LIVE badge and the channel's name
 */
@Composable
fun KidsChannelCard(
    name: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KidsCardSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = RoundedCornerShape(24.cpx),
        modifier = modifier.size(240.cpx, 112.cpx),
    ) {
        KidsLiveBadge(Modifier.align(Alignment.TopStart).padding(horizontal = 16.cpx, vertical = 14.cpx))
        // The channel name is the card's own text, always in the text colour
        KidsCardTitle(
            title = name,
            focused = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.cpx, vertical = 14.cpx),
        )
    }
}
