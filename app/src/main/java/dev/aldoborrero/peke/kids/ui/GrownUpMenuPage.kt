package dev.aldoborrero.peke.kids.ui

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.preferences.AppThemeColors
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.tryRequestFocus

/**
 * Test tags of [GrownUpMenuPage]
 */
object GrownUpMenuTags {
    const val ADD_TIME = "menu-add-time"
    const val KEEP_WATCHING = "menu-keep-watching"
    const val ADD_TIME_OFF = "menu-add-time-off"
    const val SWITCH_KID = "menu-switch-kid"
    const val RULES = "menu-rules"
    const val SETTINGS = "menu-settings"
}

/**
 * What the first card of the menu does (spec 006 F-M4, spec 021 D3)
 */
@Immutable
sealed interface MenuAddTime {
    data object Available : MenuAddTime

    /**
     * Add time can't be used: the card says why and takes no focus
     */
    data class Unavailable(
        val reason: String,
    ) : MenuAddTime

    /**
     * A break the plugin can end: the card is Keep watching now, with the reason as its line
     */
    data class KeepWatching(
        val reason: String,
    ) : MenuAddTime
}

/**
 * The parent menu as on board 8, with spec 006's items in its fixed order: the kid and their time, Add time (or why
 * not), Switch kid with two kids or more, the kid's rules and Settings (spec 021 D2). No mascot. The Back pill only
 * names the key: Back leaves the menu, which ends the grant (D4).
 *
 * @param avatar the kid's picture, 112 px round
 */
@Composable
fun GrownUpMenuPage(
    kidName: String?,
    todayLeft: String?,
    sittingLeft: String?,
    pluginGone: Pair<String, String>?,
    addTime: MenuAddTime,
    canSwitchKid: Boolean,
    notice: GrownUpNotice?,
    onNoticeShown: (GrownUpNotice) -> Unit,
    onAddTime: () -> Unit,
    onKeepWatching: () -> Unit,
    onSwitchKid: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPekeColors.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(36.cpx),
        modifier = modifier.fillMaxSize().padding(horizontal = 72.cpx, vertical = 56.cpx),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.cpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatar != null && kidName != null) {
                Box(Modifier.size(112.cpx).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) { avatar() }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.cpx), modifier = Modifier.weight(1f)) {
                if (kidName != null) {
                    Text(
                        text = stringResource(R.string.kids_menu_title),
                        style = boardText(18, FontWeight.W800),
                        color = colors.textSecondary,
                    )
                }
                Text(
                    text = kidName ?: stringResource(R.string.kids_grown_ups),
                    style = boardText(40, FontWeight.W900),
                    color = colors.text,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.cpx), horizontalAlignment = Alignment.End) {
                todayLeft?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.cpx),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .background(colors.surface, CircleShape)
                                .padding(horizontal = 18.cpx, vertical = 8.cpx),
                    ) {
                        Icon(
                            painterResource(PekeIcon.TIME_LEFT.outline),
                            contentDescription = null,
                            tint = colors.time,
                            modifier = Modifier.size(28.cpx),
                        )
                        Text(text = it, style = boardText(20, FontWeight.W800), color = colors.time)
                    }
                }
                sittingLeft?.let {
                    Text(text = it, style = boardText(18, FontWeight.W800), color = colors.textSecondary, textAlign = TextAlign.End)
                }
            }
        }
        pluginGone?.let { (title, line) ->
            StatusPill(
                icon = PekeIcon.LIVE,
                tint = colors.time,
                text = title,
                line = line,
                border = colors.time,
                textStyle = boardText(19, FontWeight.W900),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.cpx)) {
            // The first card that takes focus gets it first
            var focusGiven = false

            fun Modifier.first(): Modifier =
                if (!focusGiven) {
                    focusGiven = true
                    focusRequester(firstFocus)
                } else {
                    this
                }
            val card = Modifier.weight(1f)
            when (addTime) {
                MenuAddTime.Available -> {
                    GrownUpCard(
                        icon = PekeIcon.TIME_LEFT,
                        tint = colors.time,
                        title = stringResource(R.string.kids_add_time),
                        line = stringResource(R.string.kids_menu_add_time_line),
                        onClick = onAddTime,
                        modifier = card.first().testTag(GrownUpMenuTags.ADD_TIME),
                    )
                }

                is MenuAddTime.KeepWatching -> {
                    GrownUpCard(
                        icon = PekeIcon.PLAY,
                        tint = colors.secondary,
                        title = stringResource(R.string.kids_keep_watching),
                        line = addTime.reason,
                        onClick = onKeepWatching,
                        modifier = card.first().testTag(GrownUpMenuTags.KEEP_WATCHING),
                    )
                }

                is MenuAddTime.Unavailable -> {
                    GrownUpCard(
                        icon = PekeIcon.TIME_LEFT,
                        tint = colors.textSecondary,
                        title = stringResource(R.string.kids_add_time),
                        line = addTime.reason,
                        onClick = null,
                        modifier = card.testTag(GrownUpMenuTags.ADD_TIME_OFF),
                    )
                }
            }
            if (canSwitchKid) {
                GrownUpCard(
                    icon = PekeIcon.SWITCH_KID,
                    tint = colors.primary,
                    title = stringResource(R.string.peke_menu_switch_kid),
                    line = stringResource(R.string.kids_menu_switch_kid_line),
                    onClick = onSwitchKid,
                    modifier = card.first().testTag(GrownUpMenuTags.SWITCH_KID),
                )
            }
            GrownUpCard(
                icon = PekeIcon.SETTINGS,
                tint = colors.tertiary,
                title = kidName?.let { stringResource(R.string.kids_rules_of, it) } ?: stringResource(R.string.kids_rules),
                line = stringResource(R.string.kids_menu_rules_line),
                onClick = onRules,
                modifier = card.first().testTag(GrownUpMenuTags.RULES),
            )
            GrownUpCard(
                icon = PekeIcon.LIVE,
                tint = colors.secondary,
                title = stringResource(R.string.peke_menu_settings),
                line = stringResource(R.string.kids_menu_settings_line),
                onClick = onSettings,
                modifier = card.testTag(GrownUpMenuTags.SETTINGS),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.weight(1f))
            GrownUpNoticePill(notice, onNoticeShown)
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun GrownUpMenuPekeTvPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PEKE_TV) {
        GrownUpSurface {
            GrownUpMenuPage(
                kidName = "Lucy",
                todayLeft = "45 minutes left today",
                sittingLeft = "20 minutes left in this sitting",
                pluginGone = null,
                addTime = MenuAddTime.Available,
                canSwitchKid = true,
                notice = null,
                onNoticeShown = {},
                onAddTime = {},
                onKeepWatching = {},
                onSwitchKid = {},
                onRules = {},
                onSettings = {},
                avatar = { Box(Modifier.fillMaxSize().background(LocalPekeColors.current.secondary, CircleShape)) },
            )
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun GrownUpMenuPapelBreakPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PAPEL) {
        GrownUpSurface {
            GrownUpMenuPage(
                kidName = "Lucy",
                todayLeft = "Le quedan 45 minutos hoy",
                sittingLeft = null,
                pluginGone = "Este servidor ya no tiene el plugin Kids" to "El modo niños sigue en esta tele con lo último que supo",
                addTime = MenuAddTime.KeepWatching("Descanso: acaba a las 18:20"),
                canSwitchKid = false,
                notice = GrownUpNotice(R.string.kids_cant_reach_server, problem = true),
                onNoticeShown = {},
                onAddTime = {},
                onKeepWatching = {},
                onSwitchKid = {},
                onRules = {},
                onSettings = {},
            )
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun GrownUpMenuMandarinaUnlimitedPreview() {
    PekeTheme(appThemeColors = AppThemeColors.MANDARINA) {
        GrownUpSurface {
            GrownUpMenuPage(
                kidName = "Max",
                todayLeft = null,
                sittingLeft = null,
                pluginGone = null,
                addTime = MenuAddTime.Unavailable("Max has no daily limit"),
                canSwitchKid = true,
                notice = null,
                onNoticeShown = {},
                onAddTime = {},
                onKeepWatching = {},
                onSwitchKid = {},
                onRules = {},
                onSettings = {},
            )
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun GrownUpMenuCarameloPreview() {
    PekeTheme(appThemeColors = AppThemeColors.CARAMELO) {
        GrownUpSurface {
            GrownUpMenuPage(
                kidName = "Mia",
                todayLeft = "12 minutes left today",
                sittingLeft = null,
                pluginGone = null,
                addTime = MenuAddTime.Available,
                canSwitchKid = false,
                notice = GrownUpNotice(R.string.kids_gate_code_changed),
                onNoticeShown = {},
                onAddTime = {},
                onKeepWatching = {},
                onSwitchKid = {},
                onRules = {},
                onSettings = {},
            )
        }
    }
}
