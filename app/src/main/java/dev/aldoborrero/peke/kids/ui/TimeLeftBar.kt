package dev.aldoborrero.peke.kids.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import kotlin.time.Duration

/**
 * Whole minutes left as the bar shows them: rounded up, and never 0 while time is left
 */
fun barMinutes(remaining: Duration): Int = ((remaining.inWholeSeconds + 59) / 60).toInt().coerceAtLeast(1)

/**
 * The time left a kid sees (design/DESIGN.md: time left in the "time" colour): a bar along the top or bottom edge
 * and, next to it, the time-left icon with the minutes. From the plugin's first warning point ([nearEnd]) the bar grows
 * and the label gets bigger, together with the warning, and stays so until the end. Nothing flashes.
 *
 * @param position top or bottom; the caller shows nothing for [KidsTimerBarPosition.HIDDEN]
 */
@Composable
fun TimeLeftBar(
    remaining: Duration,
    total: Duration,
    nearEnd: Boolean,
    position: KidsTimerBarPosition,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    val fraction = (remaining / total).toFloat().coerceIn(0f, 1f)
    val height by animateDpAsState(if (nearEnd) 12.dp else 6.dp, label = "time left bar")
    val minutes = barMinutes(remaining)
    val description = pluralStringResource(R.plurals.kids_minutes_left, minutes, minutes)
    val bar: @Composable () -> Unit = {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(colors.textSecondary.copy(alpha = .25f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(height)
                        .background(colors.time),
            )
        }
    }
    val label: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(vertical = 8.dp)
                    .background(colors.time, CircleShape)
                    .padding(horizontal = if (nearEnd) 20.dp else 14.dp, vertical = if (nearEnd) 8.dp else 4.dp)
                    .semantics { contentDescription = description },
        ) {
            Icon(
                painter = painterResource(PekeIcon.TIME_LEFT.outline),
                contentDescription = null,
                // Text on "time" uses the background colour: 10.78:1 in PekeTV
                tint = colors.background,
                modifier = Modifier.size(if (nearEnd) 28.dp else 20.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.kids_time_bar_minutes, minutes, minutes),
                style = if (nearEnd) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                color = colors.background,
            )
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (position == KidsTimerBarPosition.BOTTOM) {
            label()
            bar()
        } else {
            bar()
            label()
        }
    }
}
