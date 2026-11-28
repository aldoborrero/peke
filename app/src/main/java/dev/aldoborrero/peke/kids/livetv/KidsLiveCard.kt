package dev.aldoborrero.peke.kids.livetv

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.ui.KidsCardSurface
import dev.aldoborrero.peke.kids.ui.KidsCardTitle
import dev.aldoborrero.peke.kids.ui.KidsUnderCard
import dev.aldoborrero.peke.kids.ui.kidsTitleGap
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.util.LocalClock
import org.jellyfin.sdk.model.api.ImageType

/**
 * [liveStatus] of [programme] on the TV's clock. The clock ticks every 2 s, but the status only changes once a
 * minute, and only then does the caller recompose.
 */
@Composable
fun rememberLiveStatus(programme: LiveProgramme?): LiveStatus? {
    val clock = LocalClock.current
    val status by remember(clock, programme) { derivedStateOf { programme?.let { liveStatus(it, clock.now.value) } } }
    return status
}

/**
 * "12 min left", "1 min left", "ending now", "1 h 20 min left"
 */
@Composable
fun timeLeftText(timeLeft: TimeLeft): String = LocalContext.current.resources.liveTimeLeft(timeLeft)

/** [timeLeftText] outside composition */
fun Resources.liveTimeLeft(timeLeft: TimeLeft): String =
    when (timeLeft) {
        TimeLeft.EndingNow -> {
            getString(R.string.kids_live_ending)
        }

        is TimeLeft.Minutes -> {
            getQuantityString(R.plurals.kids_live_minutes_left, timeLeft.minutes, timeLeft.minutes)
        }

        is TimeLeft.Hours -> {
            if (timeLeft.minutes == 0) {
                getQuantityString(R.plurals.kids_live_hour_left, timeLeft.hours, timeLeft.hours)
            } else {
                getString(R.string.kids_live_hours_left, timeLeft.hours, timeLeft.minutes)
            }
        }
    }

/**
 * The time left in full words, for TalkBack: "12 minutes left", "1 hour and 20 minutes left"
 */
@Composable
fun timeLeftSpoken(timeLeft: TimeLeft): String = LocalContext.current.resources.liveTimeLeftSpoken(timeLeft)

/** [timeLeftSpoken] outside composition */
fun Resources.liveTimeLeftSpoken(timeLeft: TimeLeft): String =
    when (timeLeft) {
        TimeLeft.EndingNow -> {
            getString(R.string.kids_live_ending_spoken)
        }

        is TimeLeft.Minutes -> {
            getQuantityString(R.plurals.kids_live_minutes_left_spoken, timeLeft.minutes, timeLeft.minutes)
        }

        is TimeLeft.Hours -> {
            if (timeLeft.minutes == 0) {
                getQuantityString(R.plurals.kids_live_hour_left_spoken, timeLeft.hours, timeLeft.hours)
            } else {
                getString(
                    R.string.kids_live_hours_minutes_spoken,
                    getQuantityString(R.plurals.kids_live_hours_spoken, timeLeft.hours, timeLeft.hours),
                    getQuantityString(R.plurals.kids_live_minutes_spoken, timeLeft.minutes, timeLeft.minutes),
                )
            }
        }
    }

/**
 * What TalkBack reads for a live card: "PekeTV: Hey Duggee, 12 minutes left", or the channel alone without a guide
 */
@Composable
fun liveCardSpoken(
    card: LiveCard,
    status: LiveStatus?,
): String = LocalContext.current.resources.liveCardSpoken(card, status)

/** [liveCardSpoken] outside composition */
fun Resources.liveCardSpoken(
    card: LiveCard,
    status: LiveStatus?,
): String {
    val programme = card.programme
    return if (programme == null || status == null) {
        card.channelName.orEmpty()
    } else {
        getString(R.string.kids_live_card_spoken, card.channelName.orEmpty(), programme.name.orEmpty(), liveTimeLeftSpoken(status.timeLeft))
    }
}

/**
 * "PekeTV · 12 min left": the channel in "text, secondary" and the time in the "time" colour
 */
@Composable
fun LiveChannelLine(
    channelName: String?,
    status: LiveStatus,
    modifier: Modifier = Modifier,
    size: Int = 19,
) {
    val colors = LocalPekeColors.current
    val time = timeLeftText(status.timeLeft)
    val text =
        remember(channelName, time, colors) {
            buildAnnotatedString {
                if (!channelName.isNullOrBlank()) {
                    withStyle(SpanStyle(color = colors.textSecondary)) { append(channelName) }
                    withStyle(SpanStyle(color = colors.textSecondary)) { append(" · ") }
                }
                withStyle(SpanStyle(color = colors.time)) { append(time) }
            }
        }
    Text(
        text = text,
        fontSize = size.csp,
        fontWeight = FontWeight.W800,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The address of a live [picture] for a card [heightPx] tall; null for Peke's TV, which is drawn, not loaded
 */
fun ImageUrlService.livePictureUrl(
    picture: LivePicture,
    heightPx: Int,
): String? =
    when (picture) {
        is LivePicture.Still -> {
            getItemImageUrl(
                itemId = picture.programmeId,
                imageType = ImageType.THUMB,
                fillHeight = heightPx,
                tag = picture.tag,
            )
        }

        is LivePicture.Poster -> {
            getItemImageUrl(
                itemId = picture.programmeId,
                imageType = ImageType.PRIMARY,
                maxHeight = heightPx,
                tag = picture.tag,
            )
        }

        is LivePicture.Logo -> {
            channelLogoUrl(picture)
        }

        LivePicture.PekeTv -> {
            null
        }
    }

private fun ImageUrlService.channelLogoUrl(logo: LivePicture.Logo): String? =
    getItemImageUrl(
        itemId = logo.channelId,
        imageType = ImageType.PRIMARY,
        maxHeight = ImageUrlService.channelImageMaxHeight,
        tag = logo.tag,
    )

/**
 * A live card's picture, in [livePictures]' order: each one that can't load gives way to the next. Over the
 * programme's own picture the channel's logo sits in a pill at the top left, when it has one.
 *
 * @param pill whether to draw the logo pill (the Live page's "later" picture has none)
 * @param backdrop around a poster: a colour that stands out from what is around the card (surface on the home,
 * the page colour on the Live page's surface rows)
 */
@Composable
fun BoxScope.LivePictureBox(
    card: LiveCard,
    height: Dp,
    pill: Boolean = true,
    backdrop: Color = LocalPekeColors.current.surface,
) {
    val colors = LocalPekeColors.current
    val imageUrlService = LocalImageUrlService.current
    val heightPx = with(LocalDensity.current) { height.roundToPx() }
    val pictures = remember(card) { livePictures(card) }
    var index by remember(pictures) { mutableIntStateOf(0) }
    val picture = pictures[index.coerceAtMost(pictures.lastIndex)]
    val url = remember(picture, heightPx) { imageUrlService.livePictureUrl(picture, heightPx) }
    val next = { if (index < pictures.lastIndex) index++ }
    when (picture) {
        is LivePicture.Still -> {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { next() },
                modifier = Modifier.fillMaxSize().background(colors.surface),
            )
        }

        is LivePicture.Poster -> {
            // A 2:3 poster in the middle of a dark card
            Box(Modifier.fillMaxSize().background(backdrop), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { next() },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }

        is LivePicture.Logo -> {
            // The channel's logo in a navy pill on the channel's teal tile
            Box(Modifier.fillMaxSize().background(colors.secondary), contentAlignment = Alignment.Center) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .background(colors.background, CircleShape)
                            .padding(horizontal = height * .12f, vertical = height * .08f),
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onError = { next() },
                        modifier = Modifier.height(height * .24f).widthIn(max = height * 1.1f),
                    )
                }
            }
        }

        LivePicture.PekeTv -> {
            Box(Modifier.fillMaxSize().background(colors.tertiary), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(PekeIcon.LIVE.outline),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(height * .45f),
                )
            }
        }
    }
    if (pill && showsLogoPill(card, picture)) {
        val logoTag = card.logoTag
        if (logoTag != null) {
            LogoPill(
                url = remember(card.channelId, logoTag) { imageUrlService.channelLogoUrl(LivePicture.Logo(card.channelId, logoTag)) },
                modifier = Modifier.align(Alignment.TopStart).padding(14.cpx),
            )
        }
    }
}

/**
 * The channel's logo on a 40 px navy pill; nothing when the logo can't load
 */
@Composable
private fun LogoPill(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    var failed by remember(url) { mutableStateOf(false) }
    if (url == null || failed) return
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(40.cpx).background(colors.background, CircleShape).padding(horizontal = 14.cpx),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = Modifier.height(26.cpx).widthIn(max = 140.cpx),
        )
    }
}

/**
 * How much of the programme has aired, inside the picture's bottom edge: 10 px, yellow on see-through navy
 */
@Composable
fun LiveProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    Box(modifier.fillMaxWidth().height(10.cpx).background(colors.background.copy(alpha = .55f), CircleShape)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(10.cpx).background(colors.time, CircleShape))
    }
}

/**
 * A Watch live card on the kids home (P031LiveB2): the picture of what is on, 320×180 like keep watching, with the
 * channel's logo and how far it has gone; under it the series, and the channel with the time left. A channel without a
 * guide shows its logo and its name alone. The time left moves once a minute; at a programme's end the home's timer
 * brings the next one (spec 034).
 */
@Composable
fun KidsLiveCard(
    card: LiveCard?,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = rememberLiveStatus(card?.programme)
    var hasFocus by remember { mutableStateOf(false) }
    val spoken = card?.let { liveCardSpoken(it, status) }.orEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(kidsTitleGap(height)),
        modifier = modifier.width(width),
    ) {
        KidsCardSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = RoundedCornerShape(24.cpx),
            containerColor = Color.Transparent,
            modifier =
                Modifier
                    .size(width, height)
                    .onFocusChanged { hasFocus = it.isFocused }
                    .semantics { contentDescription = spoken },
        ) {
            if (card != null) {
                LivePictureBox(card, height)
                status?.let { LiveProgress(it.progress, Modifier.align(Alignment.BottomCenter).padding(14.cpx)) }
            }
        }
        KidsUnderCard(width = width, focused = hasFocus) {
            val programme = card?.programme
            KidsCardTitle(title = programme?.name ?: card?.channelName, focused = hasFocus)
            if (programme != null && status != null) LiveChannelLine(card.channelName, status)
        }
    }
}
