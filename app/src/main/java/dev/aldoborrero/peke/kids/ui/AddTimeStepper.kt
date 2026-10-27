package dev.aldoborrero.peke.kids.ui

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.timer.AddTimePreview
import dev.aldoborrero.peke.kids.timer.AddTimeSteps
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.TodayChange
import dev.aldoborrero.peke.ui.components.CircularProgress
import dev.aldoborrero.peke.ui.components.TextButton
import dev.aldoborrero.peke.ui.playback.isEnterKey
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * The warning colour for a stop the added time runs into
 */
private val Amber = Color(0xFFFFC14D)

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
 * Add time: the amount between two arrows, Left and Right to change it (no wrap-around, the arrow at an end dimmed),
 * OK to add it. Under it, what that means in the grown-up's terms, and while sending, a busy state that ignores OK.
 */
@Composable
fun AddTimeStepper(
    kidName: String?,
    index: Int,
    sending: Boolean,
    error: BonusResult?,
    preview: AddTimePreview,
    onStep: (Int) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val minutes = AddTimeSteps.MINUTES[index]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(24.dp).widthIn(min = 420.dp, max = 560.dp),
    ) {
        Text(
            text = kidName?.let { stringResource(R.string.kids_add_time_for, it) } ?: stringResource(R.string.kids_add_time),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
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
                    .border(
                        width = 3.dp,
                        color = if (focused) MaterialTheme.colorScheme.border else Color.Transparent,
                        shape = RoundedCornerShape(24.dp),
                    ).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            StepArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = index > 0 && !sending)
            Text(
                text = addTimeLabel(minutes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 180.dp),
            )
            StepArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = index < AddTimeSteps.MINUTES.lastIndex && !sending)
        }
        val until = formatClock(context, preview.until)
        Text(
            text =
                preview.hadLeft?.let { stringResource(R.string.kids_add_time_had_left, addTimeLabel(it.inWholeMinutes.toInt()), until) }
                    ?: stringResource(R.string.kids_add_time_until, until),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        preview.bedtimeAt?.let {
            Text(
                text = stringResource(R.string.kids_add_time_bedtime, formatClock(context, it), kidName.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
                color = Amber,
            )
        }
        preview.breakAt?.let {
            Text(
                text = stringResource(R.string.kids_add_time_break, formatClock(context, it)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            sending -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgress(Modifier.size(24.dp))
                    Text(stringResource(R.string.kids_add_time_sending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            error != null -> {
                Text(
                    text =
                        stringResource(
                            if (error == BonusResult.UNREACHABLE) R.string.kids_add_time_unreachable else R.string.kids_add_time_failed,
                        ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            else -> {
                Text(
                    text = stringResource(R.string.kids_add_time_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .25f),
        modifier = Modifier.size(48.dp),
    )
}

/**
 * The time was added: who and until when, then back to the kid. With an undoable bonus, Undo stays for
 * [undoSeconds], at most [UNDO_SECONDS] and never past the server's window; Done, focused first, and Back close it
 * at once.
 */
@Composable
fun AddTimeAdded(
    kidName: String?,
    minutes: Int,
    until: LocalDateTime,
    canUndo: Boolean,
    undoSeconds: Int,
    undoing: Boolean,
    undoError: TodayChange?,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val doneFocus = remember { FocusRequester() }
    var left by remember { mutableIntStateOf(if (canUndo && undoSeconds > 0) undoSeconds else SHOW_SECONDS) }
    LaunchedEffect(Unit) { doneFocus.tryRequestFocus() }
    LaunchedEffect(undoing) {
        if (undoing) return@LaunchedEffect
        while (left > 0) {
            delay(1_000)
            left--
        }
        onDone()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(24.dp).widthIn(min = 420.dp, max = 560.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.kids_time_added, minutes, minutes, kidName.orEmpty(), formatClock(context, until)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        undoError?.let {
            Text(
                text =
                    stringResource(
                        when (it) {
                            TodayChange.TOO_LATE -> R.string.kids_undo_too_late
                            TodayChange.UNREACHABLE -> R.string.kids_cant_reach_server
                            else -> R.string.kids_undo_failed
                        },
                    ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(stringRes = R.string.kids_done, onClick = onDone, modifier = Modifier.focusRequester(doneFocus))
            if (canUndo) {
                TextButton(onClick = onUndo, enabled = !undoing) {
                    Text(stringResource(R.string.kids_undo_countdown, left))
                }
            }
            if (undoing) CircularProgress(Modifier.size(24.dp))
        }
    }
}

const val UNDO_SECONDS = 10
private const val SHOW_SECONDS = 4
