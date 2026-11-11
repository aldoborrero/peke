package dev.aldoborrero.peke.ui.preferences

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.ui.GrownUpButton
import dev.aldoborrero.peke.kids.ui.GrownUpNotice
import dev.aldoborrero.peke.kids.ui.GrownUpNoticePill
import dev.aldoborrero.peke.kids.ui.GrownUpSurface
import dev.aldoborrero.peke.kids.ui.StatusPill
import dev.aldoborrero.peke.kids.ui.boardText
import dev.aldoborrero.peke.kids.ui.pluginGoneTitle
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.AppThemeColors
import dev.aldoborrero.peke.preferences.PekeSettingsSection
import dev.aldoborrero.peke.ui.theme.BuddyMood
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.tryRequestFocus

/**
 * True when Settings opened without a kid, from the picker or "no kids": the page then carries board 11's header
 * (spec 021 D9)
 */
val LocalSetUpThisTv = compositionLocalOf { false }

/**
 * Test tags of [PekeSettingsFrame]
 */
object PekeSettingsTags {
    const val SECTION = "settings-section-"
    const val ROWS = "settings-rows"
    const val SET_UP = "settings-set-up"
}

/**
 * Peke's Settings as board 12: the sections DESIGN lists on the left, the chosen one's rows on the right, drawn by
 * Wholphin's preference code (spec 021 D8). Without a kid, board 11's header is above them (D9).
 */
@Composable
fun PekeSettingsPage(
    initialPreferences: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val notice by viewModel.notices.current.collectAsState()
    val plugin by viewModel.plugin.collectAsState()
    val setUp = LocalSetUpThisTv.current
    GrownUpSurface(modifier) {
        PekeSettingsFrame(
            sections = PekeSettingsSection.entries.map { it.title },
            setUpPlugin =
                if (!setUp) {
                    null
                } else if (plugin.status == KidsServerStatus.AVAILABLE) {
                    stringResource(R.string.peke_set_up_plugin_ready) to false
                } else {
                    stringResource(pluginGoneTitle(plugin.outdated)) to true
                },
            notice = notice,
            onNoticeShown = viewModel.notices::shown,
        ) { index ->
            PreferencesContent(
                initialPreferences = initialPreferences,
                preferenceScreenOption = PreferenceScreenOption.PEKE,
                section = PekeSettingsSection.entries[index],
                takeFocus = false,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The page without its view model: [rows] draws the rows of the section at an index
 *
 * @param setUpPlugin without a kid, the plugin's state for board 11's header and whether it's a problem; null with a kid
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PekeSettingsFrame(
    sections: List<Int>,
    setUpPlugin: Pair<String, Boolean>?,
    notice: GrownUpNotice?,
    onNoticeShown: (GrownUpNotice) -> Unit,
    modifier: Modifier = Modifier,
    rows: @Composable (Int) -> Unit,
) {
    val colors = LocalPekeColors.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val sectionFocus = remember(sections.size) { List(sections.size) { FocusRequester() } }
    var inRows by remember { mutableStateOf(false) }
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { sectionFocus[selected].tryRequestFocus() }
    // Back goes up one level: from the rows to their section; from the sections it leaves Settings (D8)
    BackHandler(enabled = inRows) { sectionFocus[selected].tryRequestFocus() }
    Box(modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.cpx),
            modifier = Modifier.fillMaxSize().padding(horizontal = 64.cpx, vertical = 44.cpx),
        ) {
            setUpPlugin?.let { (plugin, problem) -> SetUpHeader(plugin, problem) }
            Row(horizontalArrangement = Arrangement.spacedBy(32.cpx), modifier = Modifier.weight(1f)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.cpx),
                    modifier =
                        Modifier
                            .width(280.cpx)
                            .focusRestorer(sectionFocus[selected]),
                ) {
                    Text(
                        text = stringResource(R.string.peke_menu_settings),
                        style = boardText(36, FontWeight.W900),
                        color = colors.text,
                        modifier = Modifier.padding(bottom = 10.cpx),
                    )
                    sections.forEachIndexed { index, title ->
                        SectionPill(
                            title = stringResource(title),
                            selected = index == selected,
                            // OK on a section enters its rows, as Right does
                            onClick = { rowsFocus.tryRequestFocus() },
                            modifier =
                                Modifier
                                    .focusRequester(sectionFocus[index])
                                    // Into the rows from the top, not the row level with the section
                                    .focusProperties { right = rowsFocus }
                                    .onFocusChanged { if (it.isFocused) selected = index }
                                    .testTag(PekeSettingsTags.SECTION + index),
                        )
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag(PekeSettingsTags.ROWS)
                            .focusRequester(rowsFocus)
                            .onFocusChanged { inRows = it.hasFocus }
                            .focusRestorer(),
                ) {
                    key(selected) { rows(selected) }
                }
            }
        }
        GrownUpNoticePill(
            notice = notice,
            onShown = onNoticeShown,
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 64.cpx, vertical = 36.cpx),
        )
    }
}

/**
 * A section on the left of board 12: the one shown in primary, the others on surface
 */
@Composable
private fun SectionPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    val shape = RoundedCornerShape(20.cpx)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) colors.primary else colors.surface,
                contentColor = if (selected) colors.onPrimary else colors.text,
                focusedContainerColor = colors.primary,
                focusedContentColor = colors.onPrimary,
                pressedContainerColor = colors.primary,
                pressedContentColor = colors.onPrimary,
            ),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.cpx).pekeFocus(shape, grow = false),
    ) {
        Text(
            text = title,
            style = boardText(21, if (selected) FontWeight.W900 else FontWeight.W800),
            modifier = Modifier.padding(horizontal = 20.cpx, vertical = 14.cpx),
        )
    }
}

/**
 * Board 11's header, for Settings without a kid: the buddy, "Set up this TV", what it's for, and the Kids plugin
 */
@Composable
private fun SetUpHeader(
    plugin: String,
    problem: Boolean,
) {
    val colors = LocalPekeColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(22.cpx),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag(PekeSettingsTags.SET_UP),
    ) {
        Image(
            painterResource(BuddyMood.HAPPY.drawable),
            contentDescription = null,
            modifier = Modifier.size(width = 96.cpx, height = 82.cpx),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.cpx), modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.peke_set_up_title), style = boardText(40, FontWeight.W900), color = colors.text)
            Text(stringResource(R.string.peke_set_up_line), style = boardText(20, FontWeight.W800), color = colors.textSecondary)
        }
        StatusPill(
            icon = if (problem) PekeIcon.LIVE else PekeIcon.WATCHED,
            tint = if (problem) colors.time else colors.secondary,
            text = plugin,
            border = if (problem) colors.time else null,
            textStyle = boardText(17, FontWeight.W800),
        )
    }
}

@Composable
private fun PreviewRows(index: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.cpx), modifier = Modifier.padding(14.cpx)) {
        repeat(3) { GrownUpButton(text = "Row ${index + 1}.${it + 1}", onClick = {}) }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun PekeSettingsPekeTvPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PEKE_TV) {
        GrownUpSurface {
            PekeSettingsFrame(PekeSettingsSection.entries.map { it.title }, null, null, {}) { PreviewRows(it) }
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun PekeSettingsSetUpPapelPreview() {
    PekeTheme(appThemeColors = AppThemeColors.PAPEL) {
        GrownUpSurface {
            PekeSettingsFrame(PekeSettingsSection.entries.map { it.title }, "Plugin Kids listo" to false, null, {}) { PreviewRows(it) }
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION)
@Composable
private fun PekeSettingsSetUpMandarinaPreview() {
    PekeTheme(appThemeColors = AppThemeColors.MANDARINA) {
        GrownUpSurface {
            PekeSettingsFrame(
                PekeSettingsSection.entries.map { it.title },
                "This server no longer has the Kids plugin" to true,
                GrownUpNotice(R.string.kids_gate_code_changed),
                {},
            ) { PreviewRows(it) }
        }
    }
}

@Preview(device = "spec:width=960dp,height=540dp", uiMode = UI_MODE_TYPE_TELEVISION, locale = "es")
@Composable
private fun PekeSettingsCarameloPreview() {
    PekeTheme(appThemeColors = AppThemeColors.CARAMELO) {
        GrownUpSurface {
            PekeSettingsFrame(PekeSettingsSection.entries.map { it.title }, null, null, {}) { PreviewRows(it) }
        }
    }
}
