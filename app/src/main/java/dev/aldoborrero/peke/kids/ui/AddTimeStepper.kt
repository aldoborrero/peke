package dev.aldoborrero.peke.kids.ui

import android.content.Context
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.timer.AddTimePreview
import dev.aldoborrero.peke.kids.timer.AddTimeSteps
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.TodayChange
import dev.aldoborrero.peke.preferences.AppThemeColors
import dev.aldoborrero.peke.ui.components.CircularProgress
import dev.aldoborrero.peke.ui.playback.isEnterKey
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * Test tags of [AddTimePage] and [AddTimeAddedPage]
 */
object AddTimeTags {
    const val STEPPER = "add-time-stepper"
    const val LESS = "add-time-less"
    const val MORE = "add-time-more"
    const val ADDED = "add-time-added"
    const val DONE = "add-time-done"
    const val UNDO = "add-time-undo"
}

/**
 * [time] on the TV's clock, in its 12 or 24 hour format
 */
fun formatClock(
    context: Context,
    time: LocalDateTime,
): String =
    android.text.format.DateFormat
        .getTimeFormat(context)
        .format(Date.from(time.atZone(ZoneId.systemDefault()).toInstant()))

/**
 * An amount of the stepper: "15 min", "1 h", "1 h 30"
 */
@Composable
fun addTimeLabel(minutes: Int): String =
    when {
        minutes < 60 -> stringResource(R.string.kids_add_time_min, minutes)
        minutes % 60 == 0 -> stringResource(R.string.kids_add_time_hours, minutes / 60)
        else -> stringResource(R.string.kids_add_time_hours_minutes, minutes / 60, minutes % 60)
    }

/**
 * The top of the Add time pages (board 9): where we are, the title and a line on how extra time counts
 */
@Composable
private fun AddTimeHeader(kidName: String?) {
    val colors = LocalPekeColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.cpx)) {
        Text(
            text = kidName?.let { stringResource(R.string.kids_menu_of, it) } ?: stringResource(R.string.kids_menu_title),
            style = boardText(18, FontWeight.W800),
            color = colors.textSecondary,
        )
        Text(
            text = kidName?.let { stringResource(R.string.kids_add_time_for, it) } ?: stringResource(R.string.kids_add_time),
            style = boardText(48, FontWeight.W900),
            color = colors.text,
        )
        Text(
            text = stringResource(R.string.kids_add_time_server),
            style = boardText(22, FontWeight.W800),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun AddTimeFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(32.cpx),
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.cpx, vertical = 56.cpx),
        content = content,
    )
}

/**
 * Add time as on board 9, with spec 006's stepper (spec 021 D5): the amount between two arrows, Left and Right to
 * change it (no wrap-around, the arrow at an end dimmed), OK to add it. Under it, what that means in the grown-up's
 * terms, and while sending, a busy state that ignores OK. Back, which the pill names, returns to the menu.
 */
@Composable
fun AddTimePage(
    kidName: String?,
    index: Int,
    sending: Boolean,
    error: BonusResult?,
    preview: AddTimePreview,
    onStep: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalPekeColors.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val minutes = AddTimeSteps.MINUTES[index]
    val shape = RoundedCornerShape(30.cpx)
    AddTimeFrame {
        AddTimeHeader(kidName)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.cpx)
                    .testTag(AddTimeTags.STEPPER)
                    .focusRequester(focusRequester)
                    .pekeFocus(shape, grow = false)
                    .onKeyEvent {
                        when {
                            isEnterKey(it) -> {
                                if (it.type == KeyEventType.KeyUp && !sending) onConfirm()
                                true
                            }

                            it.key == Key.DirectionLeft -> {
                                if (it.type == KeyEventType.KeyDown) onStep(-1)
                                true
                            }

                            it.key == Key.DirectionRight -> {
                                if (it.type == KeyEventType.KeyDown) onStep(1)
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    }.focusable()
                    .background(colors.surface, shape)
                    .padding(horizontal = 36.cpx),
        ) {
            StepArrow(-90f, enabled = index > 0 && !sending, tag = AddTimeTags.LESS)
            Text(
                text = addTimeLabel(minutes),
                style = boardText(56, FontWeight.W900),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            StepArrow(90f, enabled = index < AddTimeSteps.MINUTES.lastIndex && !sending, tag = AddTimeTags.MORE)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.cpx),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(28.cpx))
                    .padding(horizontal = 28.cpx, vertical = 22.cpx),
        ) {
            val until = formatClock(context, preview.until)
            ForecastLine(
                icon = PekeIcon.TIME_LEFT,
                tint = colors.time,
                text =
                    preview.hadLeft?.let { stringResource(R.string.kids_add_time_had_left, addTimeLabel(it.inWholeMinutes.toInt()), until) }
                        ?: stringResource(R.string.kids_add_time_until, until),
                color = colors.time,
                px = 24,
            )
            preview.bedtimeAt?.let {
                ForecastLine(
                    icon = PekeIcon.BEDTIME,
                    tint = colors.time,
                    text = stringResource(R.string.kids_add_time_bedtime, formatClock(context, it), kidName.orEmpty()),
                    color = colors.time,
                )
            }
            preview.breakAt?.let {
                ForecastLine(
                    icon = PekeIcon.BREAK,
                    tint = colors.secondary,
                    text = stringResource(R.string.kids_add_time_break, formatClock(context, it)),
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.weight(1f))
            when {
                sending -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.cpx)) {
                        CircularProgress(Modifier.size(32.cpx))
                        Text(
                            stringResource(R.string.kids_add_time_sending),
                            style = boardText(20, FontWeight.W800),
                            color = colors.textSecondary,
                        )
                    }
                }

                error != null -> {
                    StatusPill(
                        icon = PekeIcon.LIVE,
                        tint = colors.primary,
                        text =
                            stringResource(
                                if (error == BonusResult.UNREACHABLE) R.string.kids_add_time_unreachable else R.string.kids_add_time_failed,
                            ),
                        textStyle = boardText(19, FontWeight.W800),
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.kids_add_time_hint),
                        style = boardText(18, FontWeight.W800),
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastLine(
    icon: PekeIcon,
    tint: Color,
    text: String,
    color: Color,
    px: Int = 20,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.cpx), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon.outline),
            contentDescription = null,
            tint = tint,
            modifier =
                Modifier.size(
                    if (px >
                        20
                    ) {
                        44.cpx
                    } else {
                        32.cpx
                    },
                ),
        )
        Text(text = text, style = boardText(px, FontWeight.W800), color = color)
    }
}

@Composable
private fun StepArrow(
    degrees: Float,
    enabled: Boolean,
    tag: String,
) {
    val colors = LocalPekeColors.current
    Chevron(
        degrees = degrees,
        color = if (enabled) colors.text else colors.textSecondary.copy(alpha = .35f),
        side = 64.cpx,
        modifier = Modifier.testTag(if (enabled) tag else "$tag-dimmed"),
    )
}

/**
 * The time was added (board S5's pill, spec 021 D6): who and until when, then back to the kid. With an undoable bonus,
 * Undo stays for [undoSeconds], at most [UNDO_SECONDS] and never past the server's window; Done, focused first, and
 * Back close it at once. Once undone, the page says so for a moment, then closes.
 *
 * @param undone the undo's result, once it went through
 */
@Composable
fun AddTimeAddedPage(
    kidName: String?,
    minutes: Int,
    until: LocalDateTime,
    canUndo: Boolean,
    undoSeconds: Int,
    undoing: Boolean,
    undoError: TodayChange?,
    undone: TodayChange?,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalPekeColors.current
    val doneFocus = remember { FocusRequester() }
    var left by remember { mutableIntStateOf(if (canUndo && undoSeconds > 0) undoSeconds else SHOW_SECONDS) }
    LaunchedEffect(Unit) { doneFocus.tryRequestFocus() }
    LaunchedEffect(undoing, undone) {
        if (undone != null) {
            delay(SHOW_SECONDS * 1_000L)
            onDone()
            return@LaunchedEffect
        }
        if (undoing) return@LaunchedEffect
        while (left > 0) {
            delay(1_000)
            left--
        }
        onDone()
    }
    AddTimeFrame {
        AddTimeHeader(kidName)
        if (undone == null) {
            StatusPill(
                icon = PekeIcon.WATCHED,
                tint = colors.secondary,
                text = pluralStringResource(R.plurals.kids_time_added, minutes, minutes, kidName.orEmpty(), formatClock(context, until)),
                modifier = Modifier.testTag(AddTimeTags.ADDED),
            )
        } else {
            StatusPill(
                icon = PekeIcon.WATCHED,
                tint = colors.secondary,
                text = stringResource(if (undone == TodayChange.ALREADY_UNDONE) R.string.kids_undo_already else R.string.kids_undo_done),
                modifier = Modifier.testTag(AddTimeTags.ADDED),
            )
        }
        undoError?.let {
            StatusPill(
                icon = PekeIcon.LIVE,
                tint = colors.primary,
                text =
                    stringResource(
                        when (it) {
                            TodayChange.TOO_LATE -> R.string.kids_undo_too_late
                            TodayChange.UNREACHABLE -> R.string.kids_cant_reach_server
                            else -> R.string.kids_undo_failed
                        },
                    ),
                textStyle = boardText(19, FontWeight.W800),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.cpx), verticalAlignment = Alignment.CenterVertically) {
            GrownUpButton(
                text = stringResource(R.string.kids_done),
                onClick = onDone,
                primary = true,
                modifier = Modifier.focusRequester(doneFocus).testTag(AddTimeTags.DONE),
            )
            if (canUndo && undone == null) {
                GrownUpButton(
                    text = stringResource(R.string.kids_undo_countdown, left),
                    onClick = onUndo,
                    enabled = !undoing,
                    modifier = Modifier.testTag(AddTimeTags.UNDO),
                )
            }
            if (undoing) CircularProgress(Modifier.size(32.cpx))
        }
    }
}

const val UNDO_SECONDS = 10
private const val SHOW_SECONDS = 4

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun AddTimePekeTvPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PEKE_TV) {
        GrownUpSurface {
            AddTimePage(
                kidName = "Lucy",
                index = 2,
                sending = false,
                error = null,
                preview =
                    AddTimePreview(
                        until = LocalDateTime.of(2026, 11, 11, 19, 15),
                        hadLeft = null,
                        bedtimeAt = LocalDateTime.of(2026, 11, 11, 20, 0),
                        breakAt = LocalDateTime.of(2026, 11, 11, 18, 40),
                    ),
                onStep = {},
                onConfirm = {},
            )
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun AddTimeMandarinaErrorPreview() {
    PekeTheme(appThemeColors = AppThemeColors.MANDARINA) {
        GrownUpSurface {
            AddTimePage(
                kidName = "Lucy",
                index = 0,
                sending = false,
                error = BonusResult.UNREACHABLE,
                preview = AddTimePreview(LocalDateTime.of(2026, 11, 11, 19, 15), null, null, null),
                onStep = {},
                onConfirm = {},
            )
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun AddTimeAddedCarameloPreview() {
    PekeTheme(appThemeColors = AppThemeColors.CARAMELO) {
        GrownUpSurface {
            AddTimeAddedPage("Lucy", 15, LocalDateTime.of(2026, 11, 11, 19, 15), true, 10, false, null, null, {}, {})
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun AddTimeAddedPapelPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PAPEL) {
        GrownUpSurface {
            AddTimeAddedPage("Lucy", 15, LocalDateTime.of(2026, 11, 11, 19, 15), true, 10, false, TodayChange.TOO_LATE, null, {}, {})
        }
    }
}
