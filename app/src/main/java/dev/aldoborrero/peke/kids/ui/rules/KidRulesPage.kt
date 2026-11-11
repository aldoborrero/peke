package dev.aldoborrero.peke.kids.ui.rules

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.setup.KidAvatar
import dev.aldoborrero.peke.kids.timer.AllowedHours
import dev.aldoborrero.peke.kids.ui.Chevron
import dev.aldoborrero.peke.kids.ui.GrownUpRow
import dev.aldoborrero.peke.kids.ui.GrownUpRowShape
import dev.aldoborrero.peke.kids.ui.GrownUpSurface
import dev.aldoborrero.peke.kids.ui.KidsParentGate
import dev.aldoborrero.peke.kids.ui.StatusPill
import dev.aldoborrero.peke.kids.ui.boardText
import dev.aldoborrero.peke.preferences.AppThemeColors
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.playback.isEnterKey
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Test tags of [KidRulesContent]
 */
object KidRulesTags {
    const val DAILY = "rules-daily"
    const val DAILY_VALUE = "rules-daily-value"
}

/**
 * "{Kid}'s rules" as on board 10, with spec 006's content (spec 021 D7): the current kid only, with Daily time the one
 * rule to change here and the only focus stop, the rest as text, and where to change it (spec 006 US3, US4)
 */
@Composable
fun KidRulesPage(
    modifier: Modifier = Modifier,
    viewModel: KidRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val imageUrl = state.kidId?.let { LocalImageUrlService.current.getUserImageUrl(it) }
    GrownUpSurface(modifier) {
        KidRulesContent(
            state = state,
            onSaveDaily = viewModel::setDailyTime,
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

    if (state.pending != null) {
        // The grant ran out: the code again, then the value is saved
        KidsParentGate(
            onDismissRequest = { if (viewModel.state.value.pending != null) viewModel.dropPending() },
            onPassed = viewModel::retryPending,
        )
    }
}

/**
 * The page without its view model
 *
 * @param onSaveDaily OK on Daily time with a value other than the saved one
 */
@Composable
fun KidRulesContent(
    state: KidRulesState,
    onSaveDaily: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPekeColors.current
    val name = state.kidName.orEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(24.cpx),
        modifier = modifier.fillMaxSize().padding(horizontal = 64.cpx, vertical = 44.cpx),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.cpx), verticalAlignment = Alignment.CenterVertically) {
            if (avatar != null) {
                Box(Modifier.size(72.cpx).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) { avatar() }
            }
            Column {
                Text(
                    text = if (name.isBlank()) stringResource(R.string.kids_rules) else stringResource(R.string.kids_rules_of, name),
                    style = boardText(40, FontWeight.W900),
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.kids_rules_on_server),
                    style = boardText(18, FontWeight.W800),
                    color = colors.textSecondary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.cpx), modifier = Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            Column(verticalArrangement = Arrangement.spacedBy(20.cpx), modifier = Modifier.weight(1f)) {
                val profile = state.profile
                if (state.allowanceSupported && profile != null) {
                    DailyTimeRow(profile.dailyAllowanceMinutes, saving = state.saving, scroll = scroll, onSave = onSaveDaily)
                }
                // Text only: focus never stops on these (F-K4). Up and Down on Daily time scroll them, so a long list
                // (Spanish, many picks) is still all readable
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.cpx),
                    modifier = Modifier.weight(1f).verticalScroll(scroll, enabled = false),
                ) {
                    ReadOnlyRules(state)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.cpx), modifier = Modifier.width(430.cpx)) {
                JellyfinCard(state, name)
                Spacer(Modifier.weight(1f))
                SaveStatus(state)
            }
        }
    }
}

/**
 * Daily time as board 10's stepper: Left and Right change the value shown, held at the ends; OK saves it. Leaving the
 * page with a value not saved leaves the rule as it was (spec 021 D7).
 */
@Composable
private fun DailyTimeRow(
    saved: Int?,
    saving: Boolean,
    scroll: ScrollState,
    onSave: (Int?) -> Unit,
) {
    val colors = LocalPekeColors.current
    val scope = rememberCoroutineScope()
    val options = remember(saved) { KidRulesViewModel.dailyTimeOptions(saved) }
    var shown by remember(saved) { mutableStateOf(saved) }
    val index = options.indexOf(shown).coerceAtLeast(0)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    GrownUpRow(
        label = stringResource(R.string.kids_daily_allowance),
        line = stringResource(if (shown != saved) R.string.kids_rules_daily_unsaved else R.string.kids_rules_daily_line),
        modifier =
            Modifier
                .testTag(KidRulesTags.DAILY)
                .focusRequester(focus)
                .pekeFocus(GrownUpRowShape, grow = false)
                .onKeyEvent {
                    when {
                        isEnterKey(it) -> {
                            if (it.type == KeyEventType.KeyUp && !saving && shown != saved) onSave(shown)
                            true
                        }

                        it.key == Key.DirectionLeft -> {
                            if (it.type == KeyEventType.KeyDown) shown = options[(index - 1).coerceAtLeast(0)]
                            true
                        }

                        it.key == Key.DirectionRight -> {
                            if (it.type == KeyEventType.KeyDown) shown = options[(index + 1).coerceAtMost(options.lastIndex)]
                            true
                        }

                        it.key == Key.DirectionDown || it.key == Key.DirectionUp -> {
                            val by = if (it.key == Key.DirectionDown) SCROLL_STEP else -SCROLL_STEP
                            if (it.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(by) }
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.focusable(),
    ) {
        Chevron(-90f, color = if (index > 0) colors.textSecondary else colors.textSecondary.copy(alpha = .35f), side = 32.cpx)
        Text(
            text = dailyTimeLabel(shown),
            style = boardText(22, FontWeight.W900),
            color = colors.time,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 120.cpx).testTag(KidRulesTags.DAILY_VALUE),
        )
        Chevron(
            90f,
            color = if (index < options.lastIndex) colors.textSecondary else colors.textSecondary.copy(alpha = .35f),
            side = 32.cpx,
        )
    }
}

private const val SCROLL_STEP = 160f

/**
 * A rule shown as text: its name, and its value at the end in "time"
 */
@Composable
private fun RuleRow(
    label: String,
    value: String,
) {
    val colors = LocalPekeColors.current
    GrownUpRow(label = label) {
        Text(
            text = value,
            style = boardText(20, FontWeight.W900),
            color = colors.time,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 360.cpx),
        )
    }
}

@Composable
private fun ReadOnlyRules(state: KidRulesState) {
    val context = LocalContext.current
    val profile: KidProfile? = state.profile
    val policy = state.policy
    val used = state.usedMinutes
    if (used != null) {
        val today = state.todayMinutes
        RuleRow(
            stringResource(R.string.kids_rules_today),
            if (today != null) {
                stringResource(R.string.kids_rules_today_of, used, today)
            } else {
                pluralStringResource(R.plurals.kids_rules_today_used, used, used)
            },
        )
    }
    if (profile != null && state.sessionLimitSupported) {
        val limit = profile.sessionLimitMinutes
        RuleRow(
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
        RuleRow(
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
        RuleRow(
            stringResource(R.string.kids_rules_search),
            if (profile.searchAllowed) {
                stringResource(if (voice) R.string.kids_rules_search_voice_on else R.string.kids_rules_search_voice_off)
            } else {
                stringResource(R.string.kids_rules_search_off)
            },
        )
        RuleRow(
            stringResource(R.string.kids_rules_autoplay),
            if (profile.autoplayEnabled) {
                pluralStringResource(R.plurals.kids_rules_autoplay_cap, profile.validAutoplayCap, profile.validAutoplayCap)
            } else {
                stringResource(R.string.kids_rules_autoplay_off)
            },
        )
        if (state.liveTvSupported) {
            RuleRow(
                stringResource(R.string.kids_rules_live_tv),
                when {
                    policy != null && !policy.liveTvAccess -> stringResource(R.string.kids_rules_live_tv_off)
                    state.channels.isEmpty() -> stringResource(R.string.kids_live_tv_none)
                    else -> state.channels.joinToString(", ")
                },
            )
        }
        RuleRow(
            stringResource(R.string.kids_rules_picks),
            when {
                profile.pickedItemIds.isEmpty() -> {
                    stringResource(R.string.kids_rules_picks_none)
                }

                state.pickNames.isEmpty() -> {
                    pluralStringResource(R.plurals.kids_rules_picks_count, profile.pickedItemIds.size, profile.pickedItemIds.size)
                }

                else -> {
                    state.pickNames.joinToString(", ")
                }
            },
        )
    }
}

/**
 * Board 10's "Set in the Jellyfin dashboard": what Jellyfin decides, then where the rest is changed
 */
@Composable
private fun ColumnScope.JellyfinCard(
    state: KidRulesState,
    name: String,
) {
    val colors = LocalPekeColors.current
    val policy = state.policy
    Column(
        verticalArrangement = Arrangement.spacedBy(10.cpx),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(26.cpx))
                .padding(horizontal = 22.cpx, vertical = 20.cpx),
    ) {
        if (policy != null) {
            Text(stringResource(R.string.kids_rules_jellyfin), style = boardText(20, FontWeight.W900), color = colors.text)
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
            Text(
                listOf(rating, unrated, libraries).joinToString(" · "),
                style = boardText(17, FontWeight.W800),
                color = colors.textSecondary,
            )
            if (policy.maxParentalRating == null && !policy.blocksUnrated) {
                Text(stringResource(R.string.kids_restrictions_warning), style = boardText(17, FontWeight.W800), color = colors.time)
            }
        }
        Text(stringResource(R.string.kids_rules_where, name), style = boardText(17, FontWeight.W800), color = colors.textSecondary)
    }
    state.dashboardUrl?.let { url ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.cpx),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(26.cpx))
                    .padding(horizontal = 22.cpx, vertical = 20.cpx),
        ) {
            Text(stringResource(R.string.kids_rules_dashboard, name), style = boardText(20, FontWeight.W900), color = colors.text)
            Text(url, style = boardText(17, FontWeight.W800), color = colors.secondary)
            Text(
                stringResource(R.string.kids_rules_dashboard_pick, name),
                style = boardText(17, FontWeight.W800),
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * Saving, saved or why not, as an S5 pill
 */
@Composable
private fun SaveStatus(state: KidRulesState) {
    val colors = LocalPekeColors.current
    when {
        state.error != null -> {
            StatusPill(
                icon = PekeIcon.LIVE,
                tint = colors.primary,
                text = stringResource(state.error),
                textStyle = boardText(19, FontWeight.W800),
            )
        }

        state.saving -> {
            StatusPill(icon = PekeIcon.TIME_LEFT, tint = colors.time, text = stringResource(R.string.kids_saving))
        }

        state.saved -> {
            StatusPill(icon = PekeIcon.WATCHED, tint = colors.secondary, text = stringResource(R.string.kids_saved))
        }
    }
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

private val previewState =
    KidRulesState(
        loading = false,
        kidName = "Lucy",
        profile =
            KidProfile(
                userId = 1,
                searchAllowed = true,
                autoplayEnabled = true,
                autoplayCap = 3,
                dailyAllowanceMinutes = 60,
                sessionLimitMinutes = 30,
            ),
        allowanceSupported = true,
        sessionLimitSupported = true,
        liveTvSupported = true,
        usedMinutes = 15,
        todayMinutes = 60,
        breakMinutes = 15,
        policy =
            PolicySummary(
                maxParentalRating = 7,
                blocksUnrated = true,
                allLibraries = false,
                libraryCount = 2,
                liveTvAccess = true,
                schedules = emptyList(),
            ),
        channels = listOf("PekeTV", "PekeTV Classics"),
        dashboardUrl = "https://jellyfin.home/web/#/configurationpage?name=Kids",
        saved = true,
    )

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun KidRulesPekeTvPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PEKE_TV) {
        GrownUpSurface { KidRulesContent(previewState, {}) }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun KidRulesPapelPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PAPEL) {
        GrownUpSurface { KidRulesContent(previewState.copy(saved = false, error = R.string.kids_changed_elsewhere), {}) }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun KidRulesMandarinaPreview() {
    PekeTheme(appThemeColors = AppThemeColors.MANDARINA) {
        GrownUpSurface { KidRulesContent(previewState, {}) }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun KidRulesCarameloPreview() {
    PekeTheme(appThemeColors = AppThemeColors.CARAMELO) {
        GrownUpSurface { KidRulesContent(previewState, {}) }
    }
}
