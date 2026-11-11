package dev.aldoborrero.peke.kids.ui

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.gate.ParentLockKeys
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.preferences.AppThemeColors
import dev.aldoborrero.peke.ui.playback.isBackKey
import dev.aldoborrero.peke.ui.playback.isEnterKey
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.tryRequestFocus

/**
 * Test tags of [ParentCodePage]
 */
object ParentCodePageTags {
    const val ENTRY = "parent-code-entry"
    const val DOT_FILLED = "parent-code-dot-filled"
    const val DOT_EMPTY = "parent-code-dot-empty"
    const val DOT_WRONG = "parent-code-dot-wrong"
    const val BACK = GrownUpTags.BACK
    const val CROSS = "parent-code-cross"
    const val PAD = "parent-code-pad"
}

private const val MIN_DOTS = 4
private const val MAX_DOTS = 8

/**
 * The parent code entry as a page over the whole screen (canvas board 7, errors from board S5; spec 019). It opens in
 * its own window over the screen that asked for it, so every caller keeps its result.
 *
 * The entry is the page's only focus target: with the remote type the arrows type the code, so nothing else could be
 * reached. Back always leaves, and the "Back to {Kid}" pill only says so (spec 019 D4).
 *
 * @param title the line at the top: "Grown-ups only" in the gate
 * @param attempt change it to clear the entry, after a refusal
 * @param refusal why the last code was refused; shown until a key is typed
 * @param backLabel what the Back pill says: "Back to {Kid}", or "Back"
 * @param forgotHint show "Forgot it? Press OK with no code" (F-G1)
 * @param onConfirm OK, with what was typed; empty when nothing was
 */
@Composable
fun ParentCodePage(
    @StringRes title: Int,
    type: ParentLockType,
    attempt: Int,
    refusal: GateRefusal?,
    backLabel: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    forgotHint: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        key(type, attempt) {
            ParentCodeContent(
                title = title,
                type = type,
                refusal = refusal,
                backLabel = backLabel,
                onConfirm = onConfirm,
                onBack = onDismissRequest,
                forgotHint = forgotHint,
            )
        }
    }
}

@Composable
private fun ParentCodeContent(
    @StringRes title: Int,
    type: ParentLockType,
    refusal: GateRefusal?,
    backLabel: String,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit,
    forgotHint: Boolean,
) {
    val colors = LocalPekeColors.current
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    // A refusal shows until the next key (spec 019 FR-003, FR-004)
    val shown = refusal?.takeIf { input.isEmpty() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .testTag(ParentCodePageTags.ENTRY)
                .focusRequester(focusRequester)
                .onKeyEvent {
                    when {
                        isBackKey(it) || it.key == Key.Escape -> {
                            // Back leaves with any code type, typed keys or not (D4); once per press
                            if (it.type == KeyEventType.KeyUp) onBack()
                            true
                        }

                        isEnterKey(it) -> {
                            if (it.type == KeyEventType.KeyUp) onConfirm(input)
                            true
                        }

                        else -> {
                            val symbol = ParentLockKeys.symbol(it.key.nativeKeyCode, type)
                            if (symbol != null && it.type == KeyEventType.KeyUp) input += symbol
                            // The arrows never move focus: there is nothing else to reach
                            symbol != null || it.key.isArrow()
                        }
                    }
                }.focusable(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(80.cpx, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            // The board's 64 px all round, less above and below: Spanish and an error pill need the height
            modifier = Modifier.padding(horizontal = 64.cpx, vertical = 40.cpx),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.cpx),
                modifier = Modifier.widthIn(max = 480.cpx),
            ) {
                Icon(
                    painter = painterResource(PekeIcon.GROWN_UPS.outline),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(64.cpx),
                )
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.text,
                )
                Text(
                    text =
                        stringResource(
                            if (type == ParentLockType.REMOTE) R.string.kids_gate_hint_remote else R.string.kids_gate_hint_numeric,
                        ),
                    style = boardText(24, FontWeight.W800),
                    color = colors.textSecondary,
                )
                CodeDots(count = input.length, wrong = shown == GateRefusal.Wrong)
                when (shown) {
                    null -> {}

                    GateRefusal.Wrong -> {
                        Text(
                            text = stringResource(R.string.kids_gate_wrong_passcode),
                            style = boardText(22, FontWeight.W900),
                            color = colors.primary,
                        )
                    }

                    is GateRefusal.Wait -> {
                        GatePill(PekeIcon.TIME_LEFT, colors.time) {
                            Text(
                                text = pluralStringResource(R.plurals.kids_gate_wait, shown.seconds, shown.seconds),
                                style = boardText(21, FontWeight.W900),
                                color = colors.text,
                            )
                        }
                    }

                    GateRefusal.CannotCheck -> {
                        GatePill(PekeIcon.LIVE, colors.secondary) {
                            Text(
                                text = stringResource(R.string.kids_gate_no_code_offline),
                                style = boardText(19, FontWeight.W800),
                                color = colors.text,
                            )
                        }
                    }
                }
                if (forgotHint) {
                    Text(
                        text = stringResource(R.string.kids_gate_forgot_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                BackKeyPill(backLabel)
            }
            when (type) {
                ParentLockType.REMOTE -> DpadCross()
                ParentLockType.NUMERIC -> NumberPad()
            }
        }
    }
}

private fun Key.isArrow() = this == Key.DirectionUp || this == Key.DirectionDown || this == Key.DirectionLeft || this == Key.DirectionRight

/**
 * Four dots or more, up to eight: typed ones filled in "time", the rest outlined; all in primary after a wrong code
 */
@Composable
private fun CodeDots(
    count: Int,
    wrong: Boolean,
) {
    val colors = LocalPekeColors.current
    val dots = count.coerceIn(MIN_DOTS, MAX_DOTS)
    Row(horizontalArrangement = Arrangement.spacedBy(18.cpx)) {
        repeat(dots) { i ->
            val base = Modifier.size(40.cpx)
            when {
                wrong -> Box(base.background(colors.primary, CircleShape).testTag(ParentCodePageTags.DOT_WRONG))
                i < count -> Box(base.background(colors.time, CircleShape).testTag(ParentCodePageTags.DOT_FILLED))
                else -> Box(base.border(4.cpx, colors.textSecondary, CircleShape).testTag(ParentCodePageTags.DOT_EMPTY))
            }
        }
    }
}

/**
 * An error as on board S5: an icon and a line on a surface pill
 */
@Composable
private fun GatePill(
    icon: PekeIcon,
    tint: Color,
    content: @Composable () -> Unit,
) {
    val colors = LocalPekeColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.cpx),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .background(colors.surface, RoundedCornerShape(26.cpx))
                .padding(horizontal = 24.cpx, vertical = 20.cpx),
    ) {
        Icon(painterResource(icon.outline), contentDescription = null, tint = tint, modifier = Modifier.size(48.cpx))
        content()
    }
}

/**
 * Board 7's D-pad cross with OK in the middle, for codes typed with the arrows: the only keys such a code has (D11)
 */
@Composable
private fun DpadCross() {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.cpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(380.cpx)
                .testTag(ParentCodePageTags.CROSS),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.cpx)) {
            GapCell()
            KeyTile(height = 110) { Chevron(0f) }
            GapCell()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.cpx)) {
            KeyTile(height = 110) { Chevron(-90f) }
            OkKey(height = 110)
            KeyTile(height = 110) { Chevron(90f) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.cpx)) {
            GapCell()
            KeyTile(height = 110) { Chevron(180f) }
            GapCell()
        }
    }
}

/**
 * The number keys, drawn as the cross is, for codes typed with the number buttons (spec 019 D3)
 */
@Composable
private fun NumberPad() {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(
        verticalArrangement = Arrangement.spacedBy(18.cpx),
        modifier =
            Modifier
                .width(380.cpx)
                .testTag(ParentCodePageTags.PAD),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.cpx)) {
                row.forEach { KeyTile(height = 80) { Digit(it) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.cpx)) {
            GapCell()
            KeyTile(height = 80) { Digit("0") }
            OkKey(height = 80)
        }
    }
}

@Composable
private fun RowScope.GapCell() {
    Box(Modifier.weight(1f))
}

@Composable
private fun RowScope.KeyTile(
    height: Int,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .weight(1f)
                .height(height.cpx)
                .background(LocalPekeColors.current.surface, RoundedCornerShape(28.cpx)),
    ) { content() }
}

@Composable
private fun RowScope.OkKey(height: Int) {
    val colors = LocalPekeColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .weight(1f)
                .height(height.cpx)
                .background(colors.primary, CircleShape),
    ) {
        Text(
            text = "OK",
            style = boardText(26, FontWeight.W900),
            color = colors.onPrimary,
        )
    }
}

@Composable
private fun Digit(text: String) {
    Text(
        text = text,
        style = boardText(32, FontWeight.W900),
        color = LocalPekeColors.current.text,
    )
}

/**
 * Board 7's chevron, "M6 15l6-6 6 6" on a 24 grid with a stroke of 3, pointing up, turned by [degrees]; also the
 * steppers' arrows (spec 021)
 */
@Composable
fun Chevron(
    degrees: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalPekeColors.current.text,
    side: Dp = 44.cpx,
) {
    Canvas(
        modifier
            .size(side)
            .rotate(degrees),
    ) {
        val u = size.width / 24f
        val path =
            Path().apply {
                moveTo(6 * u, 15 * u)
                lineTo(12 * u, 9 * u)
                lineTo(18 * u, 15 * u)
            }
        drawPath(path, color, style = Stroke(width = 3 * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun ParentCodePekeTvPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PEKE_TV) {
        ParentCodeContent(R.string.kids_gate_title, ParentLockType.REMOTE, null, "Back to Lucy", {}, {}, forgotHint = true)
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun ParentCodeCarameloWrongPreview() {
    PekeTheme(appThemeColors = AppThemeColors.CARAMELO) {
        ParentCodeContent(R.string.kids_gate_title, ParentLockType.NUMERIC, GateRefusal.Wrong, "Back to Lucy", {}, {}, forgotHint = true)
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun ParentCodeMandarinaWaitPreview() {
    PekeTheme(appThemeColors = AppThemeColors.MANDARINA) {
        ParentCodeContent(R.string.kids_gate_title, ParentLockType.REMOTE, GateRefusal.Wait(30), "Back", {}, {}, forgotHint = true)
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun ParentCodePapelOfflinePreview() {
    PekeTheme(appThemeColors = AppThemeColors.PAPEL) {
        ParentCodeContent(
            R.string.kids_gate_title,
            ParentLockType.NUMERIC,
            GateRefusal.CannotCheck,
            "Back to Lucy",
            {},
            {},
            forgotHint = true,
        )
    }
}
