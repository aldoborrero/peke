package dev.aldoborrero.peke.kids.ui.day

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.setup.KidAvatar
import dev.aldoborrero.peke.kids.ui.GrownUpSurface
import dev.aldoborrero.peke.kids.ui.boardText
import dev.aldoborrero.peke.kids.ui.rules.RuleRow
import dev.aldoborrero.peke.preferences.AppThemeColors
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.tryRequestFocus

/**
 * Test tags of [KidDayContent]
 */
object KidDayTags {
    const val PAGE = "day-page"
}

/**
 * "{Kid}'s day" (spec 030 US3): what the current kid watched today and the time left, as text on the grown-up page
 * kit. Opened from the parent menu, Back returns to it (spec 025); a grant that ran out asks for the code first
 * ([dev.aldoborrero.peke.kids.ui.GrownUpGuard]).
 */
@Composable
fun KidDayPage(
    modifier: Modifier = Modifier,
    viewModel: KidDayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val imageUrl = state.kidId?.let { LocalImageUrlService.current.getUserImageUrl(it) }
    GrownUpSurface(modifier) {
        KidDayContent(
            state = state,
            avatar =
                state.kidId?.let { id ->
                    {
                        KidAvatar(
                            id = id,
                            name = state.kidName,
                            imageUrl = imageUrl,
                            alpha = 1f,
                            initialStyle = boardText(32, FontWeight.W900),
                            modifier = Modifier.size(72.cpx),
                        )
                    }
                },
        )
    }
}

/**
 * The page without its view model. Text only: the page itself holds focus, so the remote's keys stay here and Back
 * leaves.
 */
@Composable
fun KidDayContent(
    state: KidDayState,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPekeColors.current
    val name = state.kidName.orEmpty()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(24.cpx),
        modifier =
            modifier
                .fillMaxSize()
                .testTag(KidDayTags.PAGE)
                .focusRequester(focus)
                .focusable()
                .padding(horizontal = 64.cpx, vertical = 44.cpx),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.cpx), verticalAlignment = Alignment.CenterVertically) {
            if (avatar != null) {
                Box(Modifier.size(72.cpx).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) { avatar() }
            }
            Column {
                Text(
                    text = if (name.isBlank()) stringResource(R.string.kids_day) else stringResource(R.string.kids_day_of, name),
                    style = boardText(40, FontWeight.W900),
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.kids_day_line),
                    style = boardText(18, FontWeight.W800),
                    color = colors.textSecondary,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.cpx), modifier = Modifier.widthIn(max = 1100.cpx)) {
            val day = state.day
            if (day == null) {
                Text(
                    text = stringResource(if (state.reachable == false) R.string.kids_cant_reach_server else R.string.kids_day_none),
                    style = boardText(20, FontWeight.W800),
                    color = colors.textSecondary,
                )
            } else {
                DayRows(day)
            }
        }
    }
}

@Composable
private fun DayRows(day: KidDay) {
    val allowance = day.allowanceMinutes
    RuleRow(
        stringResource(R.string.kids_day_watched),
        if (allowance != null) {
            stringResource(R.string.kids_rules_today_of, day.usedMinutes, allowance)
        } else {
            pluralStringResource(R.plurals.kids_rules_today_used, day.usedMinutes, day.usedMinutes)
        },
    )
    RuleRow(
        stringResource(R.string.kids_day_left),
        day.leftMinutes?.let { pluralStringResource(R.plurals.kids_minutes_option, it, it) } ?: stringResource(R.string.kids_unlimited),
    )
    if (day.bonusMinutes > 0) {
        RuleRow(stringResource(R.string.kids_day_bonus), stringResource(R.string.kids_day_bonus_value, day.bonusMinutes))
    }
    val limit = day.sittingLimitMinutes
    if (limit != null) {
        RuleRow(
            stringResource(R.string.kids_day_sitting),
            stringResource(R.string.kids_rules_today_of, day.sittingUsedMinutes ?: 0, limit),
        )
    }
    day.breakLeftMinutes?.let {
        RuleRow(stringResource(R.string.kids_day_break), stringResource(R.string.kids_day_break_left, it))
    }
}

private val previewState =
    KidDayState(
        kidName = "Lucy",
        day =
            KidDay(
                usedMinutes = 35,
                allowanceMinutes = 75,
                bonusMinutes = 15,
                leftMinutes = 40,
                sittingUsedMinutes = 20,
                sittingLimitMinutes = 30,
                breakLeftMinutes = null,
            ),
    )

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun KidDayPekeTvPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PEKE_TV) {
        GrownUpSurface { KidDayContent(previewState) }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun KidDayPapelPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PAPEL) {
        GrownUpSurface { KidDayContent(previewState.copy(day = previewState.day?.copy(breakLeftMinutes = 8))) }
    }
}
