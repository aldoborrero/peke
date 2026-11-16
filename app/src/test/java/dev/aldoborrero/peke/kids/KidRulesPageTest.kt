package dev.aldoborrero.peke.kids

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.ui.GrownUpSurface
import dev.aldoborrero.peke.kids.ui.rules.KidRulesContent
import dev.aldoborrero.peke.kids.ui.rules.KidRulesState
import dev.aldoborrero.peke.kids.ui.rules.KidRulesTags
import dev.aldoborrero.peke.kids.ui.rules.PolicySummary
import dev.aldoborrero.peke.kids.ui.rules.RuleChange
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A kid's rules as board 10 (spec 021 US3, spec 030): Daily time, Time in a row, Autoplay and Search are the focus
 * stops, in that order, each a stepper that saves on OK; the rest is text
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidRulesPageTest {
    @get:Rule
    val rule = createComposeRule()

    private val saved = mutableListOf<RuleChange>()
    private var state by mutableStateOf(
        KidRulesState(
            loading = false,
            kidName = "Lucy",
            profile =
                KidProfile(
                    userId = 1,
                    searchAllowed = false,
                    autoplayEnabled = false,
                    autoplayCap = 3,
                    dailyAllowanceMinutes = 60,
                    sessionLimitMinutes = 30,
                ),
            allowanceSupported = true,
            sessionLimitSupported = true,
            sessionLimitEditable = true,
            policy =
                PolicySummary(
                    7,
                    blocksUnrated = true,
                    allLibraries = true,
                    libraryCount = 0,
                    liveTvAccess = true,
                    schedules = emptyList(),
                ),
            dashboardUrl = "https://jellyfin.home/web/#/configurationpage?name=Kids",
        ),
    )

    private fun show() {
        rule.setContent {
            PekeTheme {
                GrownUpSurface { KidRulesContent(state, onSave = { saved += it }) }
            }
        }
        rule.waitForIdle()
    }

    private fun press(keys: List<Key>) {
        keys.forEach { key ->
            rule.onNode(isFocused()).performKeyInput { pressKey(key) }
            rule.waitForIdle()
        }
    }

    private val focusStops get() = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused))

    @Test
    fun `the title, the server line and daily time focused, with the four rules the only focus stops`() {
        show()
        rule.onNodeWithText("Lucy's rules").assertExists()
        rule.onNodeWithText("Saved on the server, so every TV follows them").assertExists()
        rule.onNodeWithTag(KidRulesTags.DAILY).assertIsFocused()
        rule.onNodeWithTag(KidRulesTags.DAILY_VALUE).assertTextEquals("60 minutes")
        rule.onNodeWithTag(KidRulesTags.SITTING_VALUE).assertTextEquals("30 minutes")
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("Off")
        rule.onNodeWithTag(KidRulesTags.SEARCH_VALUE).assertTextEquals("Not allowed")
        focusStops.assertCountEquals(4)
        // The read-only text and the dashboard stay text; today's use is on the kid's day
        rule.onNodeWithText("Allowed hours (set in Jellyfin)").assertExists()
        rule.onNodeWithText("Today").assertDoesNotExist()
        rule.onNodeWithText("Watched today").assertDoesNotExist()
        rule.onNodeWithText("https://jellyfin.home/web/#/configurationpage?name=Kids").assertExists()
        rule.onNodeWithText("Change Lucy's picks and home on the dashboard").assertExists()
    }

    @Test
    fun `Down and Up move through the rules in order, and past the ends keep focus`() {
        show()
        press(listOf(Key.DirectionDown))
        rule.onNodeWithTag(KidRulesTags.SITTING).assertIsFocused()
        press(listOf(Key.DirectionDown))
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY).assertIsFocused()
        press(listOf(Key.DirectionDown))
        rule.onNodeWithTag(KidRulesTags.SEARCH).assertIsFocused()
        press(listOf(Key.DirectionDown, Key.DirectionDown))
        rule.onNodeWithTag(KidRulesTags.SEARCH).assertIsFocused()
        press(listOf(Key.DirectionUp, Key.DirectionUp, Key.DirectionUp, Key.DirectionUp))
        rule.onNodeWithTag(KidRulesTags.DAILY).assertIsFocused()
        assertEquals(emptyList<RuleChange>(), saved)
    }

    @Test
    fun `Right then OK saves the next daily time`() {
        show()
        press(listOf(Key.DirectionRight))
        rule.onNodeWithText("OK saves · Back leaves it as it was").assertExists()
        assertEquals(emptyList<RuleChange>(), saved)
        press(listOf(Key.DirectionCenter))
        val change = saved.single() as RuleChange.DailyTime
        assertTrue(change.minutes!! > 60)
    }

    @Test
    fun `OK on the saved value saves nothing, and Left stops at the first value`() {
        show()
        press(listOf(Key.DirectionCenter))
        assertEquals(emptyList<RuleChange>(), saved)
        repeat(20) { press(listOf(Key.DirectionLeft)) }
        press(listOf(Key.DirectionCenter))
        assertTrue((saved.single() as RuleChange.DailyTime).minutes!! < 60)
    }

    @Test
    fun `a value not saved goes back when focus leaves its row`() {
        show()
        press(listOf(Key.DirectionRight, Key.DirectionDown, Key.DirectionUp))
        rule.onNodeWithTag(KidRulesTags.DAILY_VALUE).assertTextEquals("60 minutes")
        assertEquals(emptyList<RuleChange>(), saved)
    }

    @Test
    fun `time in a row steps and saves, with no limit last`() {
        show()
        press(listOf(Key.DirectionDown, Key.DirectionRight))
        rule.onNodeWithTag(KidRulesTags.SITTING_VALUE).assertTextEquals("45 minutes")
        repeat(10) { press(listOf(Key.DirectionRight)) }
        rule.onNodeWithTag(KidRulesTags.SITTING_VALUE).assertTextEquals("No limit")
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf<RuleChange>(RuleChange.SittingLimit(null)), saved)
    }

    @Test
    fun `autoplay goes from off to 1 and stops at 10 episodes`() {
        show()
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionLeft))
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("Off")
        press(listOf(Key.DirectionRight))
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("1 episode")
        repeat(15) { press(listOf(Key.DirectionRight)) }
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("10 episodes")
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf<RuleChange>(RuleChange.Autoplay(10)), saved)
    }

    @Test
    fun `with the plugin's unlimited autoplay, no limit comes after 10 and saves cap 0`() {
        state = state.copy(unlimitedAutoplay = true)
        show()
        press(listOf(Key.DirectionDown, Key.DirectionDown))
        repeat(15) { press(listOf(Key.DirectionRight)) }
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("No limit")
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf<RuleChange>(RuleChange.Autoplay(0)), saved)
    }

    @Test
    fun `search is allowed with Right and OK`() {
        show()
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionDown, Key.DirectionRight))
        rule.onNodeWithTag(KidRulesTags.SEARCH_VALUE).assertTextEquals("Allowed")
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf<RuleChange>(RuleChange.Search(true)), saved)
    }

    @Test
    fun `without the plugin's sitting limit time in a row is text, not a focus stop`() {
        state = state.copy(sessionLimitEditable = false)
        show()
        rule.onNodeWithTag(KidRulesTags.SITTING).assertDoesNotExist()
        rule.onNodeWithText("Time in a row, then a break").assertExists()
        rule.onNodeWithText("30 min").assertExists()
        focusStops.assertCountEquals(3)
        press(listOf(Key.DirectionDown))
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY).assertIsFocused()
    }

    @Test
    fun `a save says Saved`() {
        state = state.copy(saved = true)
        show()
        rule.onNodeWithText("Saved").assertExists()
    }
}
