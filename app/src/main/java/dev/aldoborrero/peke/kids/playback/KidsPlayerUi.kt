package dev.aldoborrero.peke.kids.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.ui.KidsCardPicture
import dev.aldoborrero.peke.ui.theme.BuddyMood
import dev.aldoborrero.peke.ui.theme.FocusRow
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.tryRequestFocus
import java.util.UUID

/**
 * "Episode [n] of [cap] in a row" on next-up (KNextUpPekeTV)
 */
data class InARow(
    val n: Int,
    val cap: Int,
)

/**
 * The in-a-row chip for an episode that is ending: shown only when the kid's autoplay has a [cap]; it counts the
 * episodes played in a row, the one ending included, and never goes past the cap
 *
 * @param playedInARow episodes autoplayed since the kid last pressed a button
 */
fun inARow(
    playedInARow: Int,
    cap: Int?,
): InARow? = cap?.takeIf { it > 0 }?.let { InARow((playedInARow + 1).coerceAtMost(it), it) }

/**
 * The player's title for a kid (PlayerPekeTV): the show and the episode on one line, "Space Pals · Episode 4"
 */
fun kidsPlayerTitle(
    title: String?,
    episode: String?,
): String? = listOfNotNull(title, episode).joinToString(" · ").ifEmpty { null }

/**
 * Next up for a kid (KNextUpPekeTV): a panel with the next episode's picture, "Next Up", its number and name, Play now
 * with the countdown drawn round its icon, and Stop. Play now and Stop do what the card and Back did.
 *
 * @param secondsLeft of the countdown, null when it isn't running
 */
@Composable
fun KidsNextUpPanel(
    itemId: UUID?,
    title: String,
    imageUrl: String?,
    secondsLeft: Long?,
    countdownSeconds: Long,
    onPlayNow: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(22.cpx),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .width(560.cpx)
                .background(colors.outline.copy(alpha = .95f), RoundedCornerShape(36.cpx))
                .padding(26.cpx),
    ) {
        Box(Modifier.size(200.cpx, 112.cpx).clip(RoundedCornerShape(22.cpx))) {
            KidsCardPicture(imageUrl = imageUrl, title = title, id = itemId)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.cpx)) {
            Text(
                text = stringResource(R.string.next_up),
                fontSize = 18.csp,
                fontWeight = FontWeight.W800,
                color = colors.textSecondary,
            )
            Text(
                text = title,
                fontSize = 24.csp,
                fontWeight = FontWeight.W900,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Room above for a focused button's ring, clear of the title
            FocusRow(minGap = 12.cpx, modifier = Modifier.padding(top = PekeFocus.clearance(PanelButtonHeight) - 8.cpx)) {
                KidsPanelButton(
                    text = stringResource(R.string.kids_play_now),
                    primary = true,
                    onClick = onPlayNow,
                    modifier = Modifier.focusRequester(focusRequester),
                ) {
                    CountdownIcon(
                        fraction = secondsLeft?.let { (it.toFloat() / countdownSeconds).coerceIn(0f, 1f) },
                    )
                }
                KidsPanelButton(text = stringResource(R.string.kids_stop), primary = false, onClick = onStop)
            }
        }
    }
}

/**
 * Peke's play icon in a ring that empties as the countdown runs; just the icon without a countdown
 */
@Composable
private fun CountdownIcon(fraction: Float?) {
    val color = LocalContentColor.current
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.cpx)) {
        if (fraction != null) {
            Canvas(Modifier.size(30.cpx)) {
                val stroke = 4.cpx.toPx()
                val inset = stroke / 2
                drawArc(
                    color = color.copy(alpha = .3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Icon(painter = painterResource(PekeIcon.PLAY.outline), contentDescription = null, modifier = Modifier.size(14.cpx))
        } else {
            Icon(painter = painterResource(PekeIcon.PLAY.outline), contentDescription = null, modifier = Modifier.size(30.cpx))
        }
    }
}

private val PanelButtonHeight = 52.cpx

/**
 * A 52 px pill on the next-up panel: primary for Play now, surface for Stop
 */
@Composable
private fun KidsPanelButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {},
) {
    val colors = LocalPekeColors.current
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) colors.primary else colors.surface,
                contentColor = if (primary) colors.onPrimary else colors.text,
                focusedContainerColor = colors.primary,
                focusedContentColor = colors.onPrimary,
            ),
        modifier = modifier.heightIn(min = PanelButtonHeight).pekeFocus(CircleShape),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = PanelButtonHeight).padding(horizontal = 22.cpx),
        ) {
            icon()
            Text(text = text, fontSize = 20.csp, fontWeight = if (primary) FontWeight.W900 else FontWeight.W800)
        }
    }
}

/**
 * "Episode 2 of 3 in a row" in the "time" colour on a dark pill, at the bottom left while next-up shows
 */
@Composable
fun KidsInARowChip(
    inARow: InARow,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    Text(
        text = stringResource(R.string.kids_in_a_row, inARow.n, inARow.cap),
        fontSize = 18.csp,
        fontWeight = FontWeight.W800,
        color = colors.time,
        modifier =
            modifier
                .background(colors.outline.copy(alpha = .9f), RoundedCornerShape(26.cpx))
                .padding(horizontal = 20.cpx, vertical = 10.cpx),
    )
}

/**
 * The time warning on the player (PlayerPekeTV): the buddy and "5 minutes left" in the "time" colour on a dark pill
 */
@Composable
fun KidsPlayerWarning(
    minutes: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.cpx),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .background(colors.outline.copy(alpha = .92f), RoundedCornerShape(40.cpx))
                .padding(start = 12.cpx, end = 26.cpx, top = 12.cpx, bottom = 12.cpx),
    ) {
        Image(
            painter = painterResource(BuddyMood.HAPPY.drawable),
            contentDescription = null,
            modifier = Modifier.size(64.cpx, 56.cpx),
        )
        Text(
            text = pluralStringResource(R.plurals.kids_minutes_left, minutes, minutes),
            fontSize = 26.csp,
            fontWeight = FontWeight.W900,
            color = colors.time,
        )
    }
}
