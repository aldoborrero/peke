package dev.aldoborrero.peke.kids.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aldoborrero.peke.ui.playback.isBackKey
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.LocalPekeFocusScale
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.theme.pekeFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/*
 * The parts the grown-up pages share (canvas boards 8–12, S5; spec 021): the page window, the cards and rows on
 * surface and the S5 pills. Grown-up pages draw Peke's focus without the kids' scale-up.
 */

/**
 * Test tags of the grown-up pages
 */
object GrownUpTags {
    const val NOTICE = "grown-up-notice"
}

/**
 * A board size in Nunito, with the theme's line height of 1.25 (`ui/theme/Type.kt`)
 */
@Composable
fun boardText(
    px: Int,
    weight: FontWeight,
): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = px.csp, lineHeight = (px * 1.25f).cpx.value.sp, fontWeight = weight)

/**
 * A grown-up page over the whole screen in its own window, as the parent gate's (spec 019 D1, spec 021 D1): every
 * caller keeps its result, and Back calls [onBack]
 */
@Composable
fun GrownUpWindow(
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GrownUpSurface(
            // Back goes up one level from anywhere on the page, once per press (spec 021 D4)
            modifier =
                Modifier.onKeyEvent {
                    if (isBackKey(it)) {
                        if (it.type == KeyEventType.KeyUp) onBack()
                        true
                    } else {
                        false
                    }
                },
            content = content,
        )
    }
}

/**
 * The page's background, with grown-up focus: border and ring, no scale-up (design/DESIGN.md, Focus)
 */
@Composable
fun GrownUpSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalPekeFocusScale provides false, LocalGrownUpPage provides true) {
        Box(
            modifier = modifier.fillMaxSize().background(LocalPekeColors.current.background),
            content = content,
        )
    }
}

/**
 * A message as on board S5: an icon and a line, or a line and a smaller second one, on a surface pill
 *
 * @param border a coloured edge, for a warning such as the plugin gone
 */
@Composable
fun StatusPill(
    icon: PekeIcon,
    tint: Color,
    text: String,
    modifier: Modifier = Modifier,
    line: String? = null,
    border: Color? = null,
    textStyle: TextStyle = boardText(22, FontWeight.W900),
) {
    val colors = LocalPekeColors.current
    val shape = RoundedCornerShape(30.cpx)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.cpx),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .background(colors.surface, shape)
                .let { if (border != null) it.border(3.cpx, border, shape) else it }
                .padding(start = 16.cpx, end = 26.cpx, top = 14.cpx, bottom = 14.cpx),
    ) {
        Icon(painterResource(icon.outline), contentDescription = null, tint = tint, modifier = Modifier.size(40.cpx))
        Column {
            Text(text = text, style = textStyle, color = colors.text)
            line?.let { Text(text = it, style = boardText(16, FontWeight.W800), color = colors.textSecondary) }
        }
    }
}

/**
 * A big card of the parent menu (board 8): an icon at the top, a title and a line at the bottom. Without [onClick] it
 * is text that focus skips, its icon dimmed (spec 021 D3).
 */
@Composable
fun GrownUpCard(
    icon: PekeIcon,
    tint: Color,
    title: String,
    line: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    height: Dp = 230.cpx,
) {
    val colors = LocalPekeColors.current
    val shape = RoundedCornerShape(32.cpx)
    val content: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(28.cpx),
        ) {
            Icon(
                painter = painterResource(icon.outline),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(64.cpx).alpha(if (onClick == null) .5f else 1f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.cpx)) {
                Text(
                    text = title,
                    style = boardText(30, FontWeight.W900),
                    color = if (onClick == null) colors.textSecondary else colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                line?.let {
                    Text(
                        text = it,
                        style = boardText(18, FontWeight.W800),
                        color = colors.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (onClick == null) {
        Box(modifier.height(height).background(colors.surface, shape)) { content() }
    } else {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = PekeFocus.surfaceScale,
            border = PekeFocus.surfaceBorder(),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = colors.surface,
                    contentColor = colors.text,
                    focusedContainerColor = colors.surface,
                    focusedContentColor = colors.text,
                    pressedContainerColor = colors.surface,
                    pressedContentColor = colors.text,
                ),
            modifier = modifier.height(height).pekeFocus(shape, grow = false).semantics { role = Role.Button },
        ) { content() }
    }
}

/**
 * A row on surface (boards 10 and 12): a label, a smaller line under it, and what goes at the end
 */
@Composable
fun GrownUpRow(
    label: String,
    modifier: Modifier = Modifier,
    line: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalPekeColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.cpx),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surface, GrownUpRowShape)
                .padding(horizontal = 20.cpx, vertical = 14.cpx),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = boardText(22, FontWeight.W900), color = colors.text)
            line?.let { Text(text = it, style = boardText(16, FontWeight.W800), color = colors.textSecondary) }
        }
        trailing()
    }
}

/**
 * Board 10 and 12's rows: 22 px corners
 */
val GrownUpRowShape = RoundedCornerShape(22.cpx)

/**
 * A pill button of the grown-up pages: primary when it's the main action, surface otherwise
 */
@Composable
fun GrownUpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalPekeColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) colors.primary else colors.surface,
                contentColor = if (primary) colors.onPrimary else colors.text,
                focusedContainerColor = colors.primary,
                focusedContentColor = colors.onPrimary,
                pressedContainerColor = colors.primary,
                pressedContentColor = colors.onPrimary,
                disabledContainerColor = colors.surface,
                disabledContentColor = colors.textSecondary,
            ),
        modifier = modifier.height(60.cpx).widthIn(min = 160.cpx).pekeFocus(CircleShape, grow = false),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 28.cpx), contentAlignment = Alignment.Center) {
            Text(text = text, style = boardText(22, FontWeight.W900))
        }
    }
}

/**
 * A message for the grown-up, shown as an S5 pill on the grown-up page that is showing, instead of a toast
 * (spec 021 D11)
 *
 * @param message a string, unless [plural] is set
 * @param plural a plural, with [quantity]
 * @param problem a problem rather than something done: drawn with a different icon
 */
@Immutable
data class GrownUpNotice(
    @param:StringRes val message: Int = 0,
    @param:PluralsRes val plural: Int = 0,
    val quantity: Int = 0,
    val args: List<Any> = emptyList(),
    val problem: Boolean = false,
    val id: Long = ids.incrementAndGet(),
) {
    private companion object {
        val ids = AtomicLong()
    }
}

/**
 * The grown-up's messages, across the gate's window and the pages behind it: a notice posted as the gate closes, such
 * as a recovered code saved, shows on Settings
 */
@Singleton
class GrownUpNotices
    @Inject
    constructor() {
        private val _current = MutableStateFlow<GrownUpNotice?>(null)
        val current: StateFlow<GrownUpNotice?> = _current

        fun post(notice: GrownUpNotice) {
            _current.value = notice
        }

        /**
         * [notice] was shown long enough; a newer one stays
         */
        fun shown(notice: GrownUpNotice) {
            _current.update { if (it?.id == notice.id) null else it }
        }
    }

/**
 * Shows [notice] as an S5 pill for [NOTICE_MILLIS], then calls [onShown]
 */
@Composable
fun GrownUpNoticePill(
    notice: GrownUpNotice?,
    onShown: (GrownUpNotice) -> Unit,
    modifier: Modifier = Modifier,
) {
    notice ?: return
    LaunchedEffect(notice.id) {
        delay(NOTICE_MILLIS)
        onShown(notice)
    }
    val colors = LocalPekeColors.current
    val text =
        if (notice.plural != 0) {
            pluralStringResource(notice.plural, notice.quantity, *(notice.args.ifEmpty { listOf(notice.quantity) }).toTypedArray())
        } else {
            stringResource(notice.message, *notice.args.toTypedArray())
        }
    StatusPill(
        icon = if (notice.problem) PekeIcon.LIVE else PekeIcon.WATCHED,
        tint = if (notice.problem) colors.primary else colors.secondary,
        text = text,
        modifier = modifier.testTag(GrownUpTags.NOTICE),
    )
}

const val NOTICE_MILLIS = 4_000L
