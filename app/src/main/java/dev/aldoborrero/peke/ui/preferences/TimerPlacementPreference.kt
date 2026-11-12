package dev.aldoborrero.peke.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.timer.KidsTimerLabelPlacement

/**
 * Settings › This TV: where the time left sits along the timer bar, left, in the middle or right (spec 022)
 */
@Composable
fun TimerPlacementPreference(
    placement: KidsTimerLabelPlacement,
    onChange: (KidsTimerLabelPlacement) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val values = stringArrayResource(R.array.kids_timer_placements).toList()
    ChoicePreference(
        title = stringResource(R.string.kids_timer_placement),
        summary = values[placement.ordinal],
        possibleValues = values,
        selectedIndex = placement.ordinal,
        onValueChange = { onChange(KidsTimerLabelPlacement.entries[it]) },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}
