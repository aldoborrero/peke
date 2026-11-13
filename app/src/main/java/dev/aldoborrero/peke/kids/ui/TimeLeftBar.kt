package dev.aldoborrero.peke.kids.ui

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.timer.KidsTimerLabelPlacement
import dev.aldoborrero.peke.preferences.AppThemeColors
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import kotlin.time.Duration

/**
 * Whole minutes left as the bar shows them: rounded up, and never 0 while time is left
 */
fun barMinutes(remaining: Duration): Int = ((remaining.inWholeSeconds + 59) / 60).toInt().coerceAtLeast(1)

/**
 * The room the time-left bar and its label take along the top or bottom edge, 0 where they don't show. On the player
 * the time warning sits below it, so it never covers the label, wherever the label is along the bar.
 */
@Stable
class TimeLeftBand {
    var top by mutableStateOf(0.dp)
    var bottom by mutableStateOf(0.dp)
}

/**
 * The time left a kid sees (design/DESIGN.md: time left in the "time" colour): a bar along the top or bottom edge
 * and, next to it, the time-left icon with the minutes. From the plugin's first warning point ([nearEnd]) the bar grows
 * and the label gets bigger, together with the warning, and stays so until the end. Nothing flashes.
 *
 * @param fraction the part of the time left, read when the bar is drawn: it changes every second, the rest of the bar
 * only every minute ([barMinutes])
 * @param position top or bottom; the caller shows nothing for [KidsTimerBarPosition.HIDDEN]
 * @param placement where the label sits along the bar; at the start or end it keeps DESIGN's overscan margin
 * @param band told the room the bar and label take, at the top or the bottom as [position], and 0 once they're gone
 */
@Composable
fun TimeLeftBar(
    fraction: () -> Float,
    minutes: Int,
    nearEnd: Boolean,
    position: KidsTimerBarPosition,
    modifier: Modifier = Modifier,
    placement: KidsTimerLabelPlacement = KidsTimerLabelPlacement.CENTER,
    band: TimeLeftBand? = null,
) {
    val density = LocalDensity.current
    var measured by remember { mutableStateOf(0.dp) }
    // Read here, not in the effect, so a new size recomposes and reaches the band
    val room = measured
    SideEffect {
        band?.top = if (position == KidsTimerBarPosition.TOP) room else 0.dp
        band?.bottom = if (position == KidsTimerBarPosition.BOTTOM) room else 0.dp
    }
    DisposableEffect(band) {
        onDispose {
            band?.top = 0.dp
            band?.bottom = 0.dp
        }
    }
    val colors = LocalPekeColors.current
    val height by animateDpAsState(if (nearEnd) 12.dp else 6.dp, label = "time left bar")
    val description = pluralStringResource(R.plurals.kids_minutes_left, minutes, minutes)
    val bar: @Composable () -> Unit = {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(colors.textSecondary.copy(alpha = .25f))
                    .drawBehind {
                        // The filled part from the start edge
                        val filled = size.width * fraction().coerceIn(0f, 1f)
                        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - filled else 0f
                        drawRect(colors.time, topLeft = Offset(left, 0f), size = Size(filled, size.height))
                    },
        )
    }
    val label: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(vertical = 8.dp)
                    // The bar runs edge to edge; at either end the label stays where every TV shows it
                    .padding(horizontal = if (placement == KidsTimerLabelPlacement.CENTER) 0.dp else 64.cpx)
                    .semantics { contentDescription = description }
                    .background(colors.time, CircleShape)
                    .padding(horizontal = if (nearEnd) 20.dp else 14.dp, vertical = if (nearEnd) 8.dp else 4.dp),
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
        horizontalAlignment =
            when (placement) {
                KidsTimerLabelPlacement.START -> Alignment.Start
                KidsTimerLabelPlacement.CENTER -> Alignment.CenterHorizontally
                KidsTimerLabelPlacement.END -> Alignment.End
            },
        modifier = modifier.fillMaxWidth().onSizeChanged { measured = with(density) { it.height.toDp() } },
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

/**
 * The bar in its three placements, at the top and the bottom of a small frame each, on the theme's background
 */
@Composable
private fun TimeLeftBarPlacements(appThemeColors: AppThemeColors) {
    PekeTheme(appThemeColors = appThemeColors) {
        Column(Modifier.fillMaxSize().background(LocalPekeColors.current.background)) {
            listOf(KidsTimerBarPosition.TOP, KidsTimerBarPosition.BOTTOM).forEach { position ->
                KidsTimerLabelPlacement.entries.forEach { placement ->
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        TimeLeftBar(
                            fraction = { .6f },
                            minutes = 24,
                            nearEnd = placement == KidsTimerLabelPlacement.END,
                            position = position,
                            placement = placement,
                            modifier =
                                Modifier.align(
                                    if (position ==
                                        KidsTimerBarPosition.BOTTOM
                                    ) {
                                        Alignment.BottomCenter
                                    } else {
                                        Alignment.TopCenter
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun TimeLeftBarPekeTvPreview() = TimeLeftBarPlacements(AppThemeColors.PEKE_TV)

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun TimeLeftBarMandarinaPreview() = TimeLeftBarPlacements(AppThemeColors.MANDARINA)

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun TimeLeftBarCarameloPreview() = TimeLeftBarPlacements(AppThemeColors.CARAMELO)

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun TimeLeftBarPapelPreview() = TimeLeftBarPlacements(AppThemeColors.PAPEL)
