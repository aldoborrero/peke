package dev.aldoborrero.peke.kids

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.ui.GrownUpMenuPage
import dev.aldoborrero.peke.kids.ui.GrownUpMenuTags
import dev.aldoborrero.peke.kids.ui.GrownUpNotice
import dev.aldoborrero.peke.kids.ui.GrownUpTags
import dev.aldoborrero.peke.kids.ui.GrownUpWindow
import dev.aldoborrero.peke.kids.ui.MenuAddTime
import dev.aldoborrero.peke.kids.ui.NOTICE_MILLIS
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The parent menu as board 8 (spec 021 US1): spec 006's items as cards in their order, the first usable one focused,
 * Add time that can't be used in its place without focus, the Back pill as a label, Back leaving, and the S5 pill
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GrownUpMenuPageTest {
    @get:Rule
    val rule = createComposeRule()

    private val clicks = mutableListOf<String>()
    private var backs = 0
    private var notice by mutableStateOf<GrownUpNotice?>(null)

    private fun show(
        addTime: MenuAddTime = MenuAddTime.Available,
        canSwitchKid: Boolean = false,
        kid: String? = "Lucy",
    ) {
        rule.setContent {
            PekeTheme {
                GrownUpWindow(onBack = { backs++ }) {
                    GrownUpMenuPage(
                        kidName = kid,
                        todayLeft = "45 minutes left today",
                        sittingLeft = "20 minutes left in this sitting",
                        pluginGone = null,
                        addTime = addTime,
                        canSwitchKid = canSwitchKid,
                        notice = notice,
                        onNoticeShown = { if (notice?.id == it.id) notice = null },
                        onAddTime = { clicks += "add" },
                        onKeepWatching = { clicks += "keep" },
                        onSwitchKid = { clicks += "switch" },
                        onRules = { clicks += "rules" },
                        onStats = { clicks += "stats" },
                        onSettings = { clicks += "settings" },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun press(
        tag: String,
        keys: List<Key>,
    ) {
        rule.onNodeWithTag(tag).performKeyInput { keys.forEach { pressKey(it) } }
        rule.waitForIdle()
    }

    @Test
    fun `the header and the cards follow board 8, with Add time focused`() {
        show()
        rule.onNodeWithText("Grown-up menu").assertExists()
        rule.onNodeWithText("Lucy").assertExists()
        rule.onNodeWithText("45 minutes left today").assertExists()
        rule.onNodeWithText("20 minutes left in this sitting").assertExists()
        rule.onNodeWithText("Rules").assertExists()
        rule.onNodeWithText("Stats").assertExists()
        rule.onNodeWithText("Settings").assertExists()
        // One kid: no Switch kid, and no card of the kid's day any more (spec 033)
        rule.onNodeWithTag(GrownUpMenuTags.SWITCH_KID).assertDoesNotExist()
        rule.onNodeWithText("Lucy's day").assertDoesNotExist()
        rule.onNodeWithText("How Lucy is doing today and this week").assertExists()
        rule.onNodeWithText("More minutes for Lucy today").assertExists()
        rule.onNodeWithTag(GrownUpMenuTags.ADD_TIME).assertIsFocused()
        // No Leave kids mode (spec 006)
        rule.onNodeWithText("Leave kids mode").assertDoesNotExist()
    }

    @Test
    fun `with two kids the cards are Add time, Rules, Stats, Switch kid and Settings, and OK opens the focused one`() {
        show(canSwitchKid = true)
        press(GrownUpMenuTags.ADD_TIME, listOf(Key.DirectionRight))
        rule.onNodeWithTag(GrownUpMenuTags.RULES).assertIsFocused()
        press(GrownUpMenuTags.RULES, listOf(Key.DirectionRight))
        rule.onNodeWithTag(GrownUpMenuTags.STATS).assertIsFocused()
        press(GrownUpMenuTags.STATS, listOf(Key.DirectionCenter))
        press(GrownUpMenuTags.STATS, listOf(Key.DirectionRight))
        rule.onNodeWithTag(GrownUpMenuTags.SWITCH_KID).assertIsFocused()
        press(GrownUpMenuTags.SWITCH_KID, listOf(Key.DirectionRight))
        rule.onNodeWithTag(GrownUpMenuTags.SETTINGS).assertIsFocused()
        press(GrownUpMenuTags.SETTINGS, listOf(Key.DirectionCenter))
        assertEquals(listOf("stats", "settings"), clicks)
        rule.onNodeWithText("How each kid is doing today and this week").assertExists()
    }

    @Test
    fun `with one kid Stats is followed by Settings`() {
        show()
        press(GrownUpMenuTags.ADD_TIME, listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionRight))
        rule.onNodeWithTag(GrownUpMenuTags.SETTINGS).assertIsFocused()
    }

    @Test
    fun `add time that can't be used says why, takes no focus, and focus starts on the next card`() {
        show(addTime = MenuAddTime.Unavailable("Lucy has no daily limit"))
        rule.onNodeWithText("Lucy has no daily limit").assertExists()
        rule.onNodeWithTag(GrownUpMenuTags.ADD_TIME_OFF).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        rule.onNodeWithTag(GrownUpMenuTags.RULES).assertIsFocused()
    }

    @Test
    fun `a break the plugin can end offers Keep watching now first`() {
        show(addTime = MenuAddTime.KeepWatching("Break: ends at 18:20"))
        rule.onNodeWithText("Keep watching now").assertExists()
        rule.onNodeWithText("Break: ends at 18:20").assertExists()
        rule.onNodeWithTag(GrownUpMenuTags.KEEP_WATCHING).assertIsFocused()
        press(GrownUpMenuTags.KEEP_WATCHING, listOf(Key.DirectionCenter))
        assertEquals(listOf("keep"), clicks)
    }

    @Test
    fun `no pill names the Back key, and Back leaves`() {
        show()
        rule.onNodeWithText("Back to Lucy").assertDoesNotExist()
        press(GrownUpMenuTags.ADD_TIME, listOf(Key.Back))
        assertEquals(1, backs)
        assertEquals(emptyList<String>(), clicks)
    }

    @Test
    fun `a notice shows as a pill, then goes`() {
        rule.mainClock.autoAdvance = false
        notice = GrownUpNotice(R.string.kids_cant_reach_server, problem = true)
        show()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(GrownUpTags.NOTICE).assertExists()
        rule.mainClock.advanceTimeBy(NOTICE_MILLIS + 100)
        rule.waitForIdle()
        rule.onNodeWithTag(GrownUpTags.NOTICE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "es-w960dp-h540dp-xhdpi")
    fun `in Spanish too`() {
        show(canSwitchKid = true)
        rule.onNodeWithText("Menú de adultos").assertExists()
        rule.onNodeWithText("Dar más tiempo").assertExists()
        rule.onNodeWithText("Cambiar de niño").assertExists()
        rule.onNodeWithText("Normas").assertExists()
        rule.onNodeWithText("Estadísticas").assertExists()
        rule.onNodeWithText("Ajustes").assertExists()
        rule.onNodeWithText("Volver con Lucy").assertDoesNotExist()
    }
}
