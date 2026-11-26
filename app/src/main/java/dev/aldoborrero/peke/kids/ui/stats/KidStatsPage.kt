package dev.aldoborrero.peke.kids.ui.stats

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.server.KidsStats
import dev.aldoborrero.peke.kids.server.KidsStatsDay
import dev.aldoborrero.peke.kids.server.KidsTopSeries
import dev.aldoborrero.peke.kids.setup.KidAvatar
import dev.aldoborrero.peke.kids.ui.GrownUpSurface
import dev.aldoborrero.peke.kids.ui.KidsParentGate
import dev.aldoborrero.peke.kids.ui.boardText
import dev.aldoborrero.peke.kids.ui.pluginGoneTitle
import dev.aldoborrero.peke.preferences.AppThemeColors
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.SafeArea
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.tryRequestFocus
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.UUID

/**
 * Test tags of [KidStatsContent]
 */
object KidStatsTags {
    const val BAR = "stats-bar"
    const val BAR_TODAY = "stats-bar-today"
    const val BAR_OVER = "stats-bar-over"
    const val ALLOWANCE = "stats-allowance"
    const val OFFLINE = "stats-offline"

    /**
     * The card of the kid at [index] in this TV's order
     */
    fun kid(index: Int) = "stats-kid-$index"

    /**
     * "Most watched" on the card of the kid at [index]
     */
    fun top(index: Int) = "stats-top-$index"
}

/**
 * A kid's picture on a card, [px] board pixels round
 */
typealias StatsAvatar = @Composable (kid: StatsKid, px: Int) -> Unit

/**
 * "Stats" (spec 033): one card per kid of this TV with today, the last 7 days and the most watched, read only. Opened
 * from the parent menu, Back returns to it (spec 025); a grant that ran out asks for the code first
 * ([dev.aldoborrero.peke.kids.ui.GrownUpGuard]), and so does a 403 on the week.
 */
@Composable
fun KidStatsPage(
    modifier: Modifier = Modifier,
    viewModel: KidStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val images = LocalImageUrlService.current
    GrownUpSurface(modifier) {
        KidStatsContent(
            state = state,
            avatar = { kid, px ->
                KidAvatar(
                    id = kid.id,
                    name = kid.name,
                    imageUrl = images.getUserImageUrl(kid.id),
                    avatar = kid.avatar,
                    alpha = 1f,
                    initialStyle = boardText(px * 4 / 9, FontWeight.W900),
                    modifier = Modifier.size(px.cpx),
                )
            },
        )
    }
    if (state.needsGrant) {
        KidsParentGate(
            onDismissRequest = { if (viewModel.state.value.needsGrant) viewModel.dropWeek() },
            onPassed = viewModel::retryWeek,
        )
    }
}

/**
 * The page without its view model, as the "Estadísticas" boards
 *
 * @param avatar a kid's picture; without it, their initial
 */
@Composable
fun KidStatsContent(
    state: KidStatsState,
    modifier: Modifier = Modifier,
    avatar: StatsAvatar? = null,
) {
    val colors = LocalPekeColors.current
    val first = remember { FocusRequester() }
    // Once, when the first card is laid out: the row composes its cards only then
    var focusGiven by remember { mutableStateOf(false) }
    val anyBars = state.kids.any { it.bars.isNotEmpty() }
    Column(
        verticalArrangement = Arrangement.spacedBy(24.cpx),
        modifier = modifier.fillMaxSize().padding(SafeArea.page),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.cpx)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.kids_stats), style = boardText(40, FontWeight.W900), color = colors.text)
                Text(
                    stringResource(
                        if (state.statsSupported && !state.weekRefused && !state.weekFailed) {
                            R.string.kids_stats_line
                        } else {
                            R.string.kids_stats_line_today
                        },
                    ),
                    style = boardText(18, FontWeight.W800),
                    color = colors.textSecondary,
                )
            }
            if (anyBars && !state.offline) Legend()
        }
        if (state.offline) {
            Offline()
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 32.cpx
            // Two cards in view, a third and more scroll sideways (D9); one kid gets a wider card
            val cardWidth = if (state.kids.size == 1) ONE_CARD.cpx else (maxWidth - gap) / 2
            LazyRow(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(state.kids, key = { _, kid -> kid.id }) { index, kid ->
                    if (index == 0 && !focusGiven) {
                        LaunchedEffect(Unit) {
                            focusGiven = first.tryRequestFocus()
                        }
                    }
                    StatsCard(
                        kid = kid,
                        index = index,
                        advanced = state.advanced == true,
                        avatar = avatar,
                        modifier = Modifier.width(cardWidth).let { if (index == 0) it.focusRequester(first) else it },
                    )
                }
            }
        }
        footer(state)?.let {
            Text(stringResource(it), style = boardText(18, FontWeight.W800), color = colors.textSecondary)
        }
    }
}

private const val ONE_CARD = 640

/**
 * What the bars' colours and the dashed line mean
 */
@Composable
private fun Legend() {
    val colors = LocalPekeColors.current
    val style = boardText(16, FontWeight.W800)
    Row(horizontalArrangement = Arrangement.spacedBy(20.cpx), verticalAlignment = Alignment.CenterVertically) {
        listOf(colors.time to R.string.kids_stats_legend_today, colors.primary to R.string.kids_stats_legend_over).forEach { (tint, text) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.cpx), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.cpx).background(tint, RoundedCornerShape(4.cpx)))
                Text(stringResource(text), style = style, color = colors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.cpx), verticalAlignment = Alignment.CenterVertically) {
            val line = colors.time
            Canvas(Modifier.width(22.cpx).height(3.cpx)) { dashed(line, size.height / 2) }
            Text(stringResource(R.string.kids_stats_legend_allowance), style = style, color = colors.textSecondary)
        }
    }
}

/**
 * No kid's status came from the server: one card that says so, instead of the kids' cards (board "Estadísticas sin
 * servidor")
 */
@Composable
private fun Offline() {
    val colors = LocalPekeColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .testTag(KidStatsTags.OFFLINE)
                    .width(760.cpx)
                    .background(colors.surface, RoundedCornerShape(36.cpx))
                    .padding(horizontal = 44.cpx, vertical = 40.cpx),
        ) {
            Icon(
                painterResource(PekeIcon.LIVE.outline),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(96.cpx),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.cpx)) {
                Text(stringResource(R.string.kids_stats_offline_title), style = boardText(28, FontWeight.W900), color = colors.text)
                Text(stringResource(R.string.kids_stats_offline_line), style = boardText(20, FontWeight.W800), color = colors.textSecondary)
            }
        }
    }
}

/**
 * The line at the end of the page (D9, D10)
 */
private fun footer(state: KidStatsState): Int? =
    when {
        state.loading -> null
        state.pluginGone -> pluginGoneTitle(state.pluginOutdated)
        !state.statsSupported -> R.string.kids_stats_no_week
        state.weekRefused -> R.string.kids_stats_week_needs_code
        state.weekFailed -> R.string.kids_stats_week_failed
        state.advanced == true -> R.string.kids_stats_more
        state.advanced == false -> R.string.kids_stats_advanced_off
        else -> null
    }

/**
 * A kid's card: who, today's three figures, the week's bars and the most watched. A focus stop that does nothing:
 * focus walks the cards (D11).
 */
@Composable
private fun StatsCard(
    kid: StatsKid,
    index: Int,
    advanced: Boolean,
    avatar: StatsAvatar?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    val shape = RoundedCornerShape(32.cpx)
    Column(
        verticalArrangement = Arrangement.spacedBy(16.cpx),
        modifier =
            modifier
                .testTag(KidStatsTags.kid(index))
                .pekeFocus(shape, grow = false)
                .background(colors.surface, shape)
                .focusable()
                .padding(horizontal = 26.cpx, vertical = 22.cpx),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.cpx), verticalAlignment = Alignment.CenterVertically) {
            KidPicture(kid, 52, avatar)
            Text(
                kid.name.orEmpty(),
                style = boardText(28, FontWeight.W900),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KidStatus(kid)
        }
        val today = kid.today
        if (today == null) {
            Text(stringResource(R.string.kids_stats_none), style = boardText(20, FontWeight.W800), color = colors.textSecondary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(36.cpx)) {
                Figure(stringResource(R.string.kids_stats_minutes, today.usedMinutes), stringResource(R.string.kids_stats_watched))
                Figure(
                    today.leftMinutes?.let { stringResource(R.string.kids_stats_minutes, it) } ?: stringResource(R.string.no_limit),
                    stringResource(R.string.kids_stats_left),
                )
                Figure(
                    if (today.bonusMinutes > 0) {
                        stringResource(R.string.kids_stats_extra_value, today.bonusMinutes)
                    } else {
                        stringResource(R.string.kids_stats_minutes, 0)
                    },
                    stringResource(R.string.kids_stats_extra),
                )
            }
        }
        val bars = kid.bars
        if (bars.isNotEmpty()) {
            WeekBars(
                bars,
                kid.week
                    ?.days
                    ?.lastOrNull()
                    ?.allowanceMinutes,
            )
        }
        val week = kid.week
        if (advanced && week != null && week.advancedStats) MostWatched(week.topSeries, index)
    }
}

@Composable
private fun KidStatus(kid: StatsKid) {
    val colors = LocalPekeColors.current
    val today = kid.today
    val (text, background, color) =
        when {
            kid.stale -> {
                Triple(stringResource(R.string.kids_stats_offline_pill), colors.background, colors.textSecondary)
            }

            today?.breakLeftMinutes != null -> {
                Triple(
                    stringResource(R.string.kids_stats_on_break, today.breakLeftMinutes),
                    colors.time,
                    colors.background,
                )
            }

            today?.timeUp == true -> {
                Triple(stringResource(R.string.kids_stats_time_up), colors.primary, colors.background)
            }

            kid.watching -> {
                Triple(stringResource(R.string.kids_stats_watching), colors.secondary, colors.background)
            }

            else -> {
                return
            }
        }
    Text(
        text,
        style = boardText(17, FontWeight.W900),
        color = color,
        maxLines = 1,
        modifier = Modifier.background(background, CircleShape).padding(horizontal = 16.cpx, vertical = 4.cpx),
    )
}

@Composable
private fun Figure(
    value: String,
    label: String,
) {
    val colors = LocalPekeColors.current
    Column {
        Text(value, style = boardText(36, FontWeight.W900).copy(lineHeight = 40.csp), color = colors.time, maxLines = 1)
        Text(label, style = boardText(16, FontWeight.W800), color = colors.textSecondary, maxLines = 1)
    }
}

/**
 * The last 7 days as bars (D8): today in "time", a day over its limit in "primary" with how much over above it, the
 * rest in "secondary"; each day's allowance as a dashed mark across its column, today's labelled at the end; the day's
 * letter under it, and "Today" under today's
 *
 * @param allowance today's allowance, for the line's label
 */
@Composable
private fun WeekBars(
    bars: List<StatsBar>,
    allowance: Int?,
) {
    val colors = LocalPekeColors.current
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Box(Modifier.fillMaxWidth().height(CHART.cpx)) {
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(12.cpx)) {
                bars.forEach { bar ->
                    val tint =
                        when {
                            bar.today -> colors.time
                            bar.over -> colors.primary
                            else -> colors.secondary
                        }
                    val tag =
                        when {
                            bar.today -> KidStatsTags.BAR_TODAY
                            bar.over -> KidStatsTags.BAR_OVER
                            else -> KidStatsTags.BAR
                        }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.cpx, Alignment.Bottom),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        if (bar.over && !bar.today) {
                            Text(
                                stringResource(R.string.kids_stats_over, bar.overMinutes),
                                style = boardText(13, FontWeight.W900),
                                color = colors.primary,
                                maxLines = 1,
                            )
                        }
                        Box(
                            Modifier
                                .testTag(tag)
                                .fillMaxWidth()
                                .height(maxOf(4f, bar.height.coerceIn(0f, 1f) * PLOT).cpx)
                                .background(
                                    tint,
                                    RoundedCornerShape(topStart = 10.cpx, topEnd = 10.cpx, bottomStart = 4.cpx, bottomEnd = 4.cpx),
                                ),
                        )
                    }
                }
            }
            // The allowance marks, over the bars and the gaps between them
            Row(Modifier.matchParentSize()) {
                bars.forEach { bar ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        bar.allowance?.let { mark ->
                            val line = colors.time.copy(alpha = .7f)
                            Canvas(Modifier.matchParentSize().testTag(KidStatsTags.ALLOWANCE)) {
                                dashed(line, size.height - (mark.coerceIn(0f, 1f) * PLOT).cpx.toPx())
                            }
                        }
                    }
                }
            }
            allowance?.let { minutes ->
                val mark = bars.lastOrNull()?.allowance ?: return@let
                Text(
                    watchTime(minutes),
                    style = boardText(14, FontWeight.W900),
                    color = colors.time,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = (mark.coerceIn(0f, 1f) * PLOT + 4).cpx),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.cpx), horizontalArrangement = Arrangement.spacedBy(12.cpx)) {
            bars.forEach { bar ->
                Text(
                    if (bar.today) {
                        stringResource(R.string.kids_stats_today_short)
                    } else {
                        bar.dayOfWeek?.getDisplayName(TextStyle.NARROW, locale).orEmpty()
                    },
                    style = boardText(15, if (bar.today) FontWeight.W900 else FontWeight.W800),
                    color = if (bar.today) colors.text else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The chart's height above the day letters, and the tallest bar in it, leaving room for "+15" above
 */
private const val CHART = 126
private const val PLOT = 104f

private fun DrawScope.dashed(
    color: Color,
    y: Float,
) = drawLine(
    color = color,
    start = Offset(0f, y),
    end = Offset(size.width, y),
    strokeWidth = 3.cpx.toPx(),
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.cpx.toPx(), 5.cpx.toPx())),
)

@Composable
private fun MostWatched(
    top: List<KidsTopSeries>,
    index: Int,
) {
    val colors = LocalPekeColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.cpx),
        modifier =
            Modifier
                .testTag(KidStatsTags.top(index))
                .fillMaxWidth()
                .drawBehind { drawLine(colors.background, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2.cpx.toPx()) }
                .padding(top = 12.cpx),
    ) {
        Text(
            stringResource(R.string.kids_stats_top).uppercase(LocalConfiguration.current.locales[0]),
            style = boardText(14, FontWeight.W900).copy(letterSpacing = .06.em),
            color = colors.tertiary,
        )
        if (top.isEmpty()) {
            Text(stringResource(R.string.kids_stats_top_none), style = boardText(19, FontWeight.W800), color = colors.textSecondary)
        }
        top.take(TOP_SHOWN).forEach { series ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val name = series.name.orEmpty()
                Text(
                    if (series.isLiveTv) stringResource(R.string.kids_stats_live, name) else name,
                    style = boardText(19, FontWeight.W800),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(watchTime(series.minutes), style = boardText(19, FontWeight.W800), color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/**
 * As many as the boards' cards hold
 */
private const val TOP_SHOWN = 2

/**
 * "45 min", "2 h", "1 h 20"
 */
@Composable
private fun watchTime(minutes: Int): String =
    when {
        minutes < 60 -> stringResource(R.string.kids_stats_minutes, minutes)
        minutes % 60 == 0 -> stringResource(R.string.kids_stats_hours, minutes / 60)
        else -> stringResource(R.string.kids_rules_value_hours_minutes, minutes / 60, minutes % 60)
    }

@Composable
private fun KidPicture(
    kid: StatsKid,
    px: Int,
    avatar: StatsAvatar?,
) {
    if (avatar != null) {
        avatar(kid, px)
    } else {
        val colors = LocalPekeColors.current
        Box(Modifier.size(px.cpx).background(colors.secondary, CircleShape), contentAlignment = Alignment.Center) {
            Text(kid.name?.take(1).orEmpty(), style = boardText(px * 4 / 9, FontWeight.W900), color = colors.background)
        }
    }
}

private fun previewWeek(
    used: List<Int>,
    advanced: Boolean,
): KidsStats {
    val today = LocalDate.of(2026, 11, 19)
    return KidsStats(
        advancedStats = advanced,
        days =
            used.mapIndexed { i, minutes ->
                KidsStatsDay(today.minusDays((used.size - 1 - i).toLong()).toString(), minutes, 0, 60)
            },
        topSeries =
            if (advanced) {
                listOf(
                    KidsTopSeries(name = "Bluey", minutes = 80),
                    KidsTopSeries(name = "PekeTV", minutes = 40, isLiveTv = true),
                )
            } else {
                emptyList()
            },
    )
}

private val previewState =
    KidStatsState(
        loading = false,
        statsSupported = true,
        advanced = true,
        kids =
            listOf(
                StatsKid(
                    UUID(0, 1),
                    "Manuel",
                    watching = true,
                    today = KidToday(29, 31, 10, null, false),
                    week = previewWeek(listOf(40, 62, 55, 30, 75, 45, 29), true),
                ),
                StatsKid(
                    UUID(0, 2),
                    "Lucía",
                    today = KidToday(52, 8, 0, 8, false),
                    week = previewWeek(listOf(60, 58, 70, 20, 45, 61, 52), true),
                ),
            ),
    )

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun KidStatsPekeTvPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PEKE_TV) {
        GrownUpSurface { KidStatsContent(previewState) }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun KidStatsPapelTodayOnlyPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PAPEL) {
        GrownUpSurface {
            KidStatsContent(
                previewState.copy(
                    statsSupported = false,
                    advanced = null,
                    kids = previewState.kids.map { it.copy(week = null) },
                ),
            )
        }
    }
}
