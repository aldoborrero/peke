package dev.aldoborrero.peke.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SwitchColors
import androidx.tv.material3.SwitchDefaults
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.cpx

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    summaryOn: String?,
    summaryOff: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = SwitchPreference(
    title = title,
    value = value,
    onClick = onClick,
    modifier = modifier,
    summary = if (value) summaryOn else summaryOff,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
)

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
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
        modifier = modifier.semantics { toggleableState = ToggleableState(value) },
    ) {
        PekeSwitch(value)
    }
}

/**
 * Board 12's switch, 64 by 36 px: on, a knob at the end of a pill in "secondary"; off, at the start of an outlined one
 */
@Composable
fun PekeSwitch(
    on: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    val shape = RoundedCornerShape(18.cpx)
    Box(
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        modifier =
            modifier
                .size(width = 64.cpx, height = 36.cpx)
                .background(if (on) colors.secondary else colors.background, shape)
                .let { if (on) it else it.border(3.cpx, colors.textSecondary, shape) }
                .padding(if (on) 4.cpx else 5.cpx),
    ) {
        Box(
            Modifier
                .size(if (on) 28.cpx else 22.cpx)
                .background(if (on) colors.onPrimary else colors.textSecondary, CircleShape),
        )
    }
}

@Composable
fun SwitchColors(): SwitchColors =
    // A switch that is on is drawn in the secondary colour (design/DESIGN.md)
    SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
        checkedTrackColor = MaterialTheme.colorScheme.secondary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.background,
    )
