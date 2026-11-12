package dev.aldoborrero.peke.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ProvideTextStyle
import dev.aldoborrero.peke.kids.ui.GrownUpRowShape
import dev.aldoborrero.peke.preferences.AppSliderPreference
import dev.aldoborrero.peke.ui.components.SliderBar
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.cpx

@Composable
fun SliderPreference(
    preference: AppSliderPreference<*>,
    title: String,
    summary: String?,
    value: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    summaryBelow: Boolean = false,
    additionalSummary: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val focused = interactionSource.collectIsFocusedAsState().value
    // Board 12's row, with the grown-up focus border and ring while the slider has focus (spec 021 D10)
    val colors = LocalPekeColors.current
    val contentColor = colors.text

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier =
            modifier
                .defaultMinSize(minHeight = 72.dp)
                .fillMaxWidth()
                .background(colors.surface, GrownUpRowShape)
                .let {
                    if (focused) {
                        it
                            .border(PekeFocus.BorderWidth + PekeFocus.RingWidth, colors.primary, GrownUpRowShape)
                            .border(PekeFocus.BorderWidth, colors.focusBorder, GrownUpRowShape)
                    } else {
                        it
                    }
                }.padding(PaddingValues(horizontal = 20.cpx, vertical = 14.cpx)),
    ) {
        PreferenceTitle(title, color = contentColor, modifier = Modifier.padding(bottom = 8.dp))

        ProvideTextStyle(PreferenceSummaryStyle.copy(color = contentColor)) {
            additionalSummary?.invoke(this)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            SliderBar(
                value = value,
                min = preference.min,
                max = preference.max,
                interval = preference.interval,
                onChange = onChange,
                enableWrapAround = false,
                interactionSource = interactionSource,
                modifier = Modifier.weight(1f),
            )

            if (!summaryBelow) {
                PreferenceSummary(summary, color = contentColor)
            }
        }
        if (summaryBelow) {
            PreferenceSummary(summary, color = contentColor)
        }
    }
}
