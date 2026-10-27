package dev.aldoborrero.peke.kids.ui.rules

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.setup.KidAvatar
import dev.aldoborrero.peke.kids.timer.AllowedHours
import dev.aldoborrero.peke.kids.ui.KidsParentGate
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.preferences.ChoicePreference
import dev.aldoborrero.peke.ui.tryRequestFocus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "{Kid}'s rules": the current kid only, with Daily time the one rule to change here and the only focus stop, the rest
 * as text, and where to change it (spec 006 US3, US4)
 */
@Composable
fun KidRulesPage(
    modifier: Modifier = Modifier,
    viewModel: KidRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val name = state.kidName.orEmpty()
    val dailyFocus = remember { FocusRequester() }
    LaunchedEffect(state.loading) { if (!state.loading) dailyFocus.tryRequestFocus() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(.45f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                state.kidId?.let { id ->
                    KidAvatar(
                        id = id,
                        name = state.kidName,
                        imageUrl = LocalImageUrlService.current.getUserImageUrl(id),
                        initialStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.size(72.dp),
                    )
                }
                Text(
                    text = if (name.isBlank()) stringResource(R.string.kids_rules) else stringResource(R.string.kids_rules_of, name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (state.allowanceSupported && state.profile != null) {
                val value = state.profile?.dailyAllowanceMinutes
                val options = KidRulesViewModel.dailyTimeOptions(value)
                ChoicePreference(
                    title = stringResource(R.string.kids_daily_allowance),
                    summary = dailyTimeLabel(value),
                    possibleValues = options,
                    selectedIndex = options.indexOf(value),
                    onValueChange = { viewModel.setDailyTime(options[it]) },
                    valueDisplay = { _, minutes -> Text(dailyTimeLabel(minutes)) },
                    modifier = Modifier.focusRequester(dailyFocus),
                )
                val saveLine =
                    when {
                        state.saving -> stringResource(R.string.kids_saving)
                        state.saved -> stringResource(R.string.kids_saved)
                        else -> null
                    }
                saveLine?.let { RuleNote(it) }
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
            val used = state.usedMinutes
            val today = state.todayMinutes
            if (used != null) {
                RuleLine(
                    stringResource(R.string.kids_rules_today),
                    if (today != null) {
                        stringResource(R.string.kids_rules_today_of, used, today)
                    } else {
                        pluralStringResource(R.plurals.kids_rules_today_used, used, used)
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            state.dashboardUrl?.let { url ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), RoundedCornerShape(24.dp))
                            .padding(24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.kids_rules_dashboard, name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.kids_rules_dashboard_pick, name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Text only: focus never stops here (F-K4)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(.55f),
        ) {
            ReadOnlyRules(state, name)
        }
    }

    if (state.pending != null) {
        // The grant ran out: the code again, then the value is saved
        KidsParentGate(
            onDismissRequest = { if (viewModel.state.value.pending != null) viewModel.dropPending() },
            onPassed = viewModel::retryPending,
        )
    }
}

@Composable
private fun ReadOnlyRules(
    state: KidRulesState,
    name: String,
) {
    val context = LocalContext.current
    val profile = state.profile
    val policy = state.policy
    if (profile != null && state.sessionLimitSupported) {
        val limit = profile.sessionLimitMinutes
        RuleLine(
            stringResource(R.string.kids_rules_in_a_row),
            when {
                limit == null -> stringResource(R.string.no_limit)
                state.breakMinutes != null -> stringResource(R.string.kids_rules_in_a_row_break, limit, state.breakMinutes)
                else -> stringResource(R.string.kids_rules_in_a_row_value, limit)
            },
        )
    }
    if (policy != null) {
        val windows = remember(policy.schedules) { AllowedHours.windows(policy.schedules, LocalDate.now()) }
        RuleLine(
            stringResource(R.string.kids_rules_allowed_hours),
            when {
                policy.schedules.isEmpty() -> stringResource(R.string.kids_rules_any_time)
                windows.isEmpty() -> stringResource(R.string.kids_rules_not_today)
                else -> stringResource(R.string.kids_rules_hours_today, windows.joinToString(", ") { hours(it) })
            },
        )
    }
    if (profile != null) {
        val voice =
            state.voiceSearch &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        RuleLine(
            stringResource(R.string.kids_rules_search),
            if (profile.searchAllowed) {
                stringResource(if (voice) R.string.kids_rules_search_voice_on else R.string.kids_rules_search_voice_off)
            } else {
                stringResource(R.string.kids_rules_search_off)
            },
        )
        RuleLine(
            stringResource(R.string.kids_rules_autoplay),
            if (profile.autoplayEnabled) {
                pluralStringResource(R.plurals.kids_rules_autoplay_cap, profile.validAutoplayCap, profile.validAutoplayCap)
            } else {
                stringResource(R.string.kids_rules_autoplay_off)
            },
        )
        if (state.liveTvSupported) {
            RuleLine(
                stringResource(R.string.kids_rules_live_tv),
                when {
                    policy != null && !policy.liveTvAccess -> stringResource(R.string.kids_rules_live_tv_off)
                    state.channels.isEmpty() -> stringResource(R.string.kids_live_tv_none)
                    else -> state.channels.joinToString(", ")
                },
            )
        }
        RuleLine(
            stringResource(R.string.kids_rules_picks),
            when {
                profile.pickedItemIds.isEmpty() -> {
                    stringResource(R.string.kids_rules_picks_none)
                }

                state.pickNames.isEmpty() -> {
                    pluralStringResource(
                        R.plurals.kids_rules_picks_count,
                        profile.pickedItemIds.size,
                        profile.pickedItemIds.size,
                    )
                }

                else -> {
                    state.pickNames.joinToString(", ")
                }
            },
        )
    }
    if (policy != null) {
        val rating =
            policy.maxParentalRating?.let { stringResource(R.string.kids_restriction_max_rating, it) }
                ?: stringResource(R.string.kids_restriction_no_max_rating)
        val unrated =
            stringResource(
                if (policy.blocksUnrated) R.string.kids_restriction_unrated_blocked else R.string.kids_restriction_unrated_allowed,
            )
        val libraries =
            if (policy.allLibraries) {
                stringResource(R.string.kids_restriction_all_libraries)
            } else {
                pluralStringResource(R.plurals.kids_restriction_some_libraries, policy.libraryCount, policy.libraryCount)
            }
        RuleLine(stringResource(R.string.kids_rules_jellyfin), listOf(rating, unrated, libraries).joinToString(" · "))
        if (policy.maxParentalRating == null && !policy.blocksUnrated) RuleNote(stringResource(R.string.kids_restrictions_warning))
    }
    RuleNote(stringResource(R.string.kids_rules_where, name))
}

/**
 * An allowed window in minutes of the day, on the TV's clock format
 */
private fun hours(window: IntRange): String {
    val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun at(minute: Int) = if (minute >= 24 * 60) LocalTime.MIDNIGHT else LocalTime.of(minute / 60, minute % 60)
    return at(window.first).format(format) + "–" + at(window.last).format(format)
}

@Composable
private fun dailyTimeLabel(minutes: Int?): String =
    minutes?.let { pluralStringResource(R.plurals.kids_minutes_option, it, it) } ?: stringResource(R.string.kids_unlimited)

@Composable
private fun RuleLine(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RuleNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
