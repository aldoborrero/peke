package dev.aldoborrero.peke.kids.ui.rules

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.setup.KidAvatar
import dev.aldoborrero.peke.kids.ui.Chevron
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
import dev.aldoborrero.peke.ui.theme.SafeArea
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.tryRequestFocus
import java.util.UUID

/**
 * Test tags of [KidRulesContent]
 */
object KidRulesTags {
    const val DAILY = "rules-daily"
    const val DAILY_VALUE = "rules-daily-value"
    const val DAILY_SAYS = "rules-daily-says"
    const val SITTING = "rules-sitting"
    const val SITTING_VALUE = "rules-sitting-value"
    const val SITTING_SAYS = "rules-sitting-says"
    const val AUTOPLAY = "rules-autoplay"
    const val AUTOPLAY_VALUE = "rules-autoplay-value"
    const val AUTOPLAY_SAYS = "rules-autoplay-says"
    const val SEARCH = "rules-search"
    const val SEARCH_VALUE = "rules-search-value"
    const val SEARCH_SAYS = "rules-search-says"

    /**
     * The kid switch's chip of the kid at [index] in this TV's order
     */
    fun kid(index: Int) = "rules-kid-$index"
}

/**
 * A kid's picture for the page, [px] board pixels round
 */
typealias RulesAvatar = @Composable (kid: RulesKid, px: Int) -> Unit

/**
 * "Rules" (spec 033): the rules a grown-up changes on the TV, full width, each with its icon, a sentence that says
 * what the value means for the kid, and its stepper; the only focus stops but the kid switch. Nothing read only: what
 * Jellyfin decides and the dashboard stay off the TV (spec 033 D1).
 */
@Composable
fun KidRulesPage(
    modifier: Modifier = Modifier,
    viewModel: KidRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val images = LocalImageUrlService.current
    GrownUpSurface(modifier) {
        KidRulesContent(
            state = state,
            onSave = viewModel::save,
            onSelectKid = viewModel::select,
            avatar = { kid, px ->
                KidAvatar(
                    id = kid.id,
                    name = kid.name,
                    imageUrl = images.getUserImageUrl(kid.id),
                    avatar = kid.avatar,
                    alpha = 1f,
                    initialStyle = boardText(px * 4 / 9, FontWeight.W900),
                    modifier = Modifier.size(px.cpx),
                )
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
 * @param onSave OK on a rule's row with a value other than the saved one
 * @param onSelectKid OK on another kid in the kid switch
 * @param avatar a kid's picture; without it, their initial
 */
@Composable
fun KidRulesContent(
    state: KidRulesState,
    onSave: (RuleChange) -> Unit,
    modifier: Modifier = Modifier,
    onSelectKid: (UUID) -> Unit = {},
    avatar: RulesAvatar? = null,
) {
    val colors = LocalPekeColors.current
    val name = state.kidName.orEmpty()
    val shownKid = state.kids.firstOrNull { it.id == state.kidId } ?: state.kidId?.let { RulesKid(it, state.kidName) }
    val switchFocus = remember(state.kids.size) { List(state.kids.size) { FocusRequester() } }
    val showSwitch = state.kids.size > 1
    val toSwitch: (() -> Unit)? =
        if (showSwitch) {
            {
                val at = state.kids.indexOfFirst { it.id == state.kidId }.coerceAtLeast(0)
                switchFocus[at].tryRequestFocus()
            }
        } else {
            null
        }
    val ruleFocus = remember { mutableStateOf<FocusRequester?>(null) }
    // The row OK was last pressed on, which says "Saved" by its name (board "Normas: guardado")
    var lastSaved by remember(state.kidId) { mutableStateOf<String?>(null) }
    Column(
        verticalArrangement = Arrangement.spacedBy(30.cpx),
        modifier = modifier.fillMaxSize().padding(SafeArea.page),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.cpx), verticalAlignment = Alignment.CenterVertically) {
            if (shownKid != null) {
                Box(Modifier.size(72.cpx).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
                    KidPicture(shownKid, 72, avatar)
                }
            }
            Column(Modifier.weight(1f)) {
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
            if (showSwitch) {
                KidSwitch(
                    kids = state.kids,
                    shownId = state.kidId,
                    focus = switchFocus,
                    onSelect = onSelectKid,
                    onDown = { ruleFocus.value?.tryRequestFocus() },
                    avatar = avatar,
                )
            }
        }
        state.profile?.let { profile ->
            RuleEditors(
                state = state,
                profile = profile,
                savedRow = lastSaved.takeIf { state.saved },
                onSave = { row, change ->
                    lastSaved = row
                    onSave(change)
                },
                onUpFromFirst = toSwitch,
                onFirst = { ruleFocus.value = it },
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            SaveStatus(state)
        }
    }
}

/**
 * The kid switch, variant (a) of the canvas (spec 033 D5): one chip per kid of this TV, the kid shown marked. Left and
 * Right move between them, OK shows that kid's rules, Down returns to the rules. Variant (b), a "Change" button, would
 * replace only this.
 */
@Composable
private fun KidSwitch(
    kids: List<RulesKid>,
    shownId: UUID?,
    focus: List<FocusRequester>,
    onSelect: (UUID) -> Unit,
    onDown: () -> Unit,
    avatar: RulesAvatar?,
) {
    val colors = LocalPekeColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(20.cpx), verticalAlignment = Alignment.CenterVertically) {
        kids.forEachIndexed { index, kid ->
            val shown = kid.id == shownId
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.cpx),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .testTag(KidRulesTags.kid(index))
                        .focusRequester(focus[index])
                        .pekeFocus(CircleShape, grow = false)
                        .height(56.cpx)
                        .background(if (shown) colors.text else colors.surface, CircleShape)
                        .onKeyEvent {
                            when {
                                isEnterKey(it) -> {
                                    if (it.type == KeyEventType.KeyUp) onSelect(kid.id)
                                    true
                                }

                                it.key == Key.DirectionDown -> {
                                    if (it.type == KeyEventType.KeyDown) onDown()
                                    true
                                }

                                // Nothing above the header
                                it.key == Key.DirectionUp -> {
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }.focusable()
                        .padding(start = 8.cpx, end = 22.cpx),
            ) {
                KidPicture(kid, 40, avatar)
                Text(
                    text = kid.name.orEmpty(),
                    style = boardText(20, FontWeight.W900),
                    color = if (shown) colors.background else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun KidPicture(
    kid: RulesKid,
    px: Int,
    avatar: RulesAvatar?,
) {
    if (avatar != null) {
        avatar(kid, px)
    } else {
        val colors = LocalPekeColors.current
        Box(Modifier.size(px.cpx).background(colors.secondary, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                text = kid.name?.take(1).orEmpty(),
                style = boardText(px * 4 / 9, FontWeight.W900),
                color = colors.background,
            )
        }
    }
}

/**
 * The rules changed on the TV, in order: Daily time (with the plugin's allowance), Breaks (with its sitting limit),
 * Next episode by itself and Search. Up and Down move between them; Up on the first goes to the kid switch.
 *
 * @param savedRow the test tag of the row just saved, which says so by its name
 * @param onSave OK on a row: its tag and the change
 */
@Composable
private fun RuleEditors(
    state: KidRulesState,
    profile: KidProfile,
    savedRow: String?,
    onSave: (String, RuleChange) -> Unit,
    onUpFromFirst: (() -> Unit)?,
    onFirst: (FocusRequester) -> Unit,
) {
    val colors = LocalPekeColors.current
    val daily = state.allowanceSupported
    val sitting = state.sessionLimitEditable
    val count = 2 + (if (daily) 1 else 0) + (if (sitting) 1 else 0)
    val focus = remember(count) { List(count) { FocusRequester() } }
    LaunchedEffect(count) {
        onFirst(focus.first())
        focus.first().tryRequestFocus()
    }
    val nav = StepperNav(focus, onUpFromFirst)
    val kid = state.kidId
    var at = 0
    Column(verticalArrangement = Arrangement.spacedBy(18.cpx)) {
        if (daily) {
            StepperRow(
                icon = PekeIcon.TIME_LEFT,
                tint = colors.time,
                label = stringResource(R.string.kids_daily_allowance),
                says = { minutes ->
                    minutes?.let { stringResource(R.string.kids_rules_daily_says, duration(it)) }
                        ?: stringResource(R.string.kids_rules_daily_no_limit)
                },
                saved = profile.dailyAllowanceMinutes,
                kid = kid,
                options = remember(profile.dailyAllowanceMinutes) { KidRulesViewModel.dailyTimeOptions(profile.dailyAllowanceMinutes) },
                text = { minutesLabel(it) },
                tags = Triple(KidRulesTags.DAILY, KidRulesTags.DAILY_VALUE, KidRulesTags.DAILY_SAYS),
                justSaved = savedRow == KidRulesTags.DAILY,
                saving = state.saving,
                nav = nav,
                index = at++,
                onSave = { onSave(KidRulesTags.DAILY, RuleChange.DailyTime(it)) },
            )
        }
        if (sitting) {
            StepperRow(
                icon = PekeIcon.BREAK,
                tint = colors.secondary,
                label = stringResource(R.string.kids_rules_breaks),
                says = { minutes ->
                    when {
                        minutes == null -> stringResource(R.string.kids_rules_breaks_no_limit)
                        state.breakMinutes != null -> stringResource(R.string.kids_rules_breaks_says, minutes, state.breakMinutes)
                        else -> stringResource(R.string.kids_rules_breaks_says_no_break, minutes)
                    }
                },
                saved = profile.sessionLimitMinutes,
                kid = kid,
                options = remember(profile.sessionLimitMinutes) { KidRulesViewModel.sittingOptions(profile.sessionLimitMinutes) },
                text = { minutesLabel(it) },
                tags = Triple(KidRulesTags.SITTING, KidRulesTags.SITTING_VALUE, KidRulesTags.SITTING_SAYS),
                justSaved = savedRow == KidRulesTags.SITTING,
                saving = state.saving,
                nav = nav,
                index = at++,
                onSave = { onSave(KidRulesTags.SITTING, RuleChange.SittingLimit(it)) },
            )
        }
        val autoplay = if (profile.autoplayEnabled) profile.validAutoplayCap else null
        StepperRow(
            icon = PekeIcon.NEXT_EPISODE,
            tint = colors.primary,
            label = stringResource(R.string.kids_rules_autoplay),
            says = { cap ->
                when (cap) {
                    null -> stringResource(R.string.kids_rules_autoplay_off_says)
                    KidProfile.UNLIMITED_AUTOPLAY_CAP -> stringResource(R.string.kids_rules_autoplay_no_limit)
                    else -> pluralStringResource(R.plurals.kids_rules_autoplay_says, cap, cap)
                }
            },
            saved = autoplay,
            kid = kid,
            options = remember(state.unlimitedAutoplay, autoplay) { KidRulesViewModel.autoplayOptions(state.unlimitedAutoplay, autoplay) },
            text = { cap ->
                when (cap) {
                    null -> stringResource(R.string.kids_rules_value_no)
                    KidProfile.UNLIMITED_AUTOPLAY_CAP -> stringResource(R.string.no_limit)
                    else -> pluralStringResource(R.plurals.kids_rules_value_in_a_row, cap, cap)
                }
            },
            tags = Triple(KidRulesTags.AUTOPLAY, KidRulesTags.AUTOPLAY_VALUE, KidRulesTags.AUTOPLAY_SAYS),
            justSaved = savedRow == KidRulesTags.AUTOPLAY,
            saving = state.saving,
            nav = nav,
            index = at++,
            onSave = { onSave(KidRulesTags.AUTOPLAY, RuleChange.Autoplay(it)) },
        )
        StepperRow(
            icon = PekeIcon.SEARCH,
            tint = colors.tertiary,
            label = stringResource(R.string.kids_rules_search),
            says = { stringResource(if (it) R.string.kids_rules_search_says else R.string.kids_rules_search_off_says) },
            saved = profile.searchAllowed,
            kid = kid,
            options = KidRulesViewModel.SEARCH_OPTIONS,
            text = { stringResource(if (it) R.string.kids_rules_value_yes else R.string.kids_rules_value_no) },
            tags = Triple(KidRulesTags.SEARCH, KidRulesTags.SEARCH_VALUE, KidRulesTags.SEARCH_SAYS),
            justSaved = savedRow == KidRulesTags.SEARCH,
            saving = state.saving,
            nav = nav,
            index = at,
            onSave = { onSave(KidRulesTags.SEARCH, RuleChange.Search(it)) },
        )
    }
}

/**
 * Up and Down between the rule rows: [focus] holds one requester per row, in order; Up on the first calls
 * [onUpFromFirst], the kid switch, when there is one
 */
private class StepperNav(
    val focus: List<FocusRequester>,
    val onUpFromFirst: (() -> Unit)?,
) {
    fun move(
        from: Int,
        down: Boolean,
    ) {
        val to = if (down) from + 1 else from - 1
        when {
            to in focus.indices -> focus[to].tryRequestFocus()
            !down -> onUpFromFirst?.invoke()
        }
    }
}

/**
 * A rule as the "Normas" boards' stepper, full width with its icon (spec 033 D2): Left and Right change the value
 * shown, held at the ends with the arrow dimmed; OK saves it, and "Saved" shows by its name. The sentence under the
 * name follows the value shown, and "OK saves · Back leaves it as it was" sits under the row while it isn't saved.
 * Leaving the row, the page or the kid with a value not saved leaves the rule as it was (spec 021 D7, spec 030 D3).
 *
 * @param tags the row's, its value's and its sentence's test tags
 */
@Composable
private fun <T> StepperRow(
    icon: PekeIcon,
    tint: Color,
    label: String,
    says: @Composable (T) -> String,
    saved: T,
    kid: UUID?,
    options: List<T>,
    text: @Composable (T) -> String,
    tags: Triple<String, String, String>,
    justSaved: Boolean,
    saving: Boolean,
    nav: StepperNav,
    index: Int,
    onSave: (T) -> Unit,
) {
    val colors = LocalPekeColors.current
    var shown by remember(saved, kid) { mutableStateOf(saved) }
    var focused by remember { mutableStateOf(false) }
    val at = options.indexOf(shown).coerceAtLeast(0)
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(tags.first)
                    .focusRequester(nav.focus[index])
                    .pekeFocus(GrownUpRowShape, grow = false)
                    .background(colors.surface, GrownUpRowShape)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) shown = saved
                    }.onKeyEvent {
                        when {
                            isEnterKey(it) -> {
                                if (it.type == KeyEventType.KeyUp && !saving && shown != saved) onSave(shown)
                                true
                            }

                            it.key == Key.DirectionLeft -> {
                                if (it.type == KeyEventType.KeyDown) shown = options[(at - 1).coerceAtLeast(0)]
                                true
                            }

                            it.key == Key.DirectionRight -> {
                                if (it.type == KeyEventType.KeyDown) shown = options[(at + 1).coerceAtMost(options.lastIndex)]
                                true
                            }

                            it.key == Key.DirectionDown || it.key == Key.DirectionUp -> {
                                if (it.type == KeyEventType.KeyDown) nav.move(index, down = it.key == Key.DirectionDown)
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    }.focusable()
                    .padding(horizontal = 22.cpx, vertical = 12.cpx),
        ) {
            Box(
                Modifier.size(60.cpx).background(colors.background, RoundedCornerShape(18.cpx)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon.outline), contentDescription = null, tint = tint, modifier = Modifier.size(34.cpx))
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.cpx), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, style = boardText(24, FontWeight.W900), color = colors.text)
                    if (justSaved) {
                        Text(
                            text = stringResource(R.string.kids_saved),
                            style = boardText(16, FontWeight.W900),
                            color = colors.background,
                            modifier =
                                Modifier
                                    .background(colors.secondary, CircleShape)
                                    .padding(horizontal = 14.cpx, vertical = 2.cpx),
                        )
                    }
                }
                Text(
                    text = says(shown),
                    style = boardText(18, FontWeight.W800),
                    color = if (focused) colors.text else colors.textSecondary,
                    modifier = Modifier.testTag(tags.third),
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.widthIn(min = 250.cpx),
            ) {
                Chevron(-90f, color = if (at > 0) colors.textSecondary else colors.textSecondary.copy(alpha = .3f), side = 32.cpx)
                Text(
                    text = text(shown),
                    style = boardText(24, FontWeight.W900),
                    color = colors.time,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.cpx).testTag(tags.second),
                )
                Chevron(
                    90f,
                    color = if (at < options.lastIndex) colors.textSecondary else colors.textSecondary.copy(alpha = .3f),
                    side = 32.cpx,
                )
            }
        }
        if (shown != saved) {
            Text(
                text = stringResource(R.string.kids_rules_unsaved),
                style = boardText(18, FontWeight.W900),
                color = colors.time,
                modifier = Modifier.padding(start = 104.cpx, top = 10.cpx),
            )
        }
    }
}

/**
 * Saving or why a save failed, as an S5 pill; "Saved" shows on its row
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
    }
}

/**
 * A stepper's minutes as the boards write them ("45 min", "1 h", "1 h 30"), or No limit (spec 033 D3)
 */
@Composable
private fun minutesLabel(minutes: Int?): String =
    when {
        minutes == null -> stringResource(R.string.no_limit)
        minutes < 60 -> stringResource(R.string.kids_rules_value_minutes, minutes)
        minutes % 60 == 0 -> stringResource(R.string.kids_rules_value_hours, minutes / 60)
        else -> stringResource(R.string.kids_rules_value_hours_minutes, minutes / 60, minutes % 60)
    }

/**
 * [minutes] said in a sentence: "45 minutes", "1 hour", "1 h 30 min"
 */
@Composable
internal fun duration(minutes: Int): String =
    when {
        minutes < 60 -> pluralStringResource(R.plurals.kids_duration_minutes, minutes, minutes)
        minutes % 60 == 0 -> pluralStringResource(R.plurals.kids_duration_hours, minutes / 60, minutes / 60)
        else -> stringResource(R.string.kids_duration_hours_minutes, minutes / 60, minutes % 60)
    }

private val previewKids = listOf(RulesKid(UUID(0, 1), "Lucy"), RulesKid(UUID(0, 2), "Max"))

private val previewState =
    KidRulesState(
        loading = false,
        kidId = previewKids.first().id,
        kidName = "Lucy",
        kids = previewKids,
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
        sessionLimitEditable = true,
        breakMinutes = 15,
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
private fun KidRulesMandarinaOneKidPreview() {
    PekeTheme(appThemeColors = AppThemeColors.MANDARINA) {
        GrownUpSurface { KidRulesContent(previewState.copy(kids = previewKids.take(1)), {}) }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun KidRulesCarameloPreview() {
    PekeTheme(appThemeColors = AppThemeColors.CARAMELO) {
        GrownUpSurface { KidRulesContent(previewState, {}) }
    }
}
