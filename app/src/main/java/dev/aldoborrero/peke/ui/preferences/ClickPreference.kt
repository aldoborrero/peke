package dev.aldoborrero.peke.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aldoborrero.peke.kids.ui.GrownUpRowShape
import dev.aldoborrero.peke.kids.ui.boardText
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.pekeFocus

@Composable
fun ClickPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    PreferenceRow(
        title = title,
        summary = summary,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

/**
 * A settings row as on board 12 (spec 021 D10): surface, 22 px corners, the title and a smaller line under it, and
 * what goes at the end, with the grown-up focus (border and ring, no scale-up). Settings are grown-up only.
 */
@Composable
fun PreferenceRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = LocalPekeColors.current
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(GrownUpRowShape),
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
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().pekeFocus(GrownUpRowShape, grow = false),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.cpx, vertical = 14.cpx),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = boardText(22, FontWeight.W900), color = colors.text)
                summary?.let { Text(text = it, style = boardText(16, FontWeight.W800), color = colors.textSecondary) }
            }
            trailing?.invoke(this)
        }
    }
}
