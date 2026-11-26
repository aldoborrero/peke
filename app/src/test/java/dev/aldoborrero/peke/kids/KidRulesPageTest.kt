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
import dev.aldoborrero.peke.kids.ui.rules.RuleChange
import dev.aldoborrero.peke.kids.ui.rules.RulesKid
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Rules (spec 021 US3, spec 030, spec 033): Daily time, Breaks, Next episode by itself and Search are the focus stops, in
 * that order, each a stepper that saves on OK, with a sentence that follows the value; nothing read only, and a kid
 * switch with two kids or more
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
            sessionLimitEditable = true,
            breakMinutes = 15,
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
        rule.onNodeWithText("They apply on every TV at home").assertExists()
        rule.onNodeWithTag(KidRulesTags.DAILY).assertIsFocused()
        rule.onNodeWithTag(KidRulesTags.DAILY_VALUE).assertTextEquals("1 h")
        rule.onNodeWithTag(KidRulesTags.SITTING_VALUE).assertTextEquals("30 min")
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("No")
        rule.onNodeWithTag(KidRulesTags.SEARCH_VALUE).assertTextEquals("No")
        focusStops.assertCountEquals(4)
        // One name per rule, and a sentence that says what it means for the kid
        rule.onNodeWithText("Daily time").assertExists()
        rule.onNodeWithText("Breaks").assertExists()
        rule.onNodeWithText("Next episode by itself").assertExists()
        rule.onNodeWithText("Search").assertExists()
        rule.onNodeWithTag(KidRulesTags.DAILY_SAYS).assertTextEquals("Can watch 1 hour each day")
        rule.onNodeWithTag(KidRulesTags.SITTING_SAYS).assertTextEquals("Rests 15 min every 30 min in a row")
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_SAYS).assertTextEquals("Each episode waits for them to pick the next")
        rule.onNodeWithTag(KidRulesTags.SEARCH_SAYS).assertTextEquals("Only what's on their home")
    }

    @Test
    fun `no read-only rows, no Jellyfin column and no dashboard`() {
        show()
        listOf(
            "Today",
            "Watched today",
            "Allowed hours (set in Jellyfin)",
            "Live TV",
            "Picks",
            "What Jellyfin decides",
            "Time in a row, then a break",
            "Then a break",
            "Then a 15 min break",
        ).forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        rule.onNodeWithText("configurationpage", substring = true).assertDoesNotExist()
        rule.onNodeWithText("dashboard", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the sentence follows the value shown before it is saved`() {
        show()
        press(listOf(Key.DirectionRight))
        rule.onNodeWithTag(KidRulesTags.DAILY_SAYS).assertTextEquals("Can watch 1 h 30 min each day")
        repeat(10) { press(listOf(Key.DirectionRight)) }
        rule.onNodeWithTag(KidRulesTags.DAILY_VALUE).assertTextEquals("No limit")
        rule.onNodeWithTag(KidRulesTags.DAILY_SAYS).assertTextEquals("Can watch as much as they like")
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionRight, Key.DirectionRight, Key.DirectionRight))
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_SAYS).assertTextEquals("Stops after 3 episodes in a row")
        press(listOf(Key.DirectionDown, Key.DirectionRight))
        rule.onNodeWithTag(KidRulesTags.SEARCH_SAYS).assertTextEquals("Can search for series and films")
        assertEquals(emptyList<RuleChange>(), saved)
    }

    @Test
    fun `with two kids Up reaches the switch, Right and OK pick the other kid, Down goes back to the rules`() {
        val leo = RulesKid(UUID(0, 1), "Leo")
        val lucy = RulesKid(UUID(0, 2), "Lucy")
        state = state.copy(kidId = lucy.id, kids = listOf(leo, lucy))
        val picked = mutableListOf<UUID>()
        rule.setContent {
            PekeTheme {
                GrownUpSurface { KidRulesContent(state, onSave = { saved += it }, onSelectKid = { picked += it }) }
            }
        }
        rule.waitForIdle()
        press(listOf(Key.DirectionUp))
        // The kid shown is where the switch is reached
        rule.onNodeWithTag(KidRulesTags.kid(1)).assertIsFocused()
        press(listOf(Key.DirectionLeft))
        rule.onNodeWithTag(KidRulesTags.kid(0)).assertIsFocused()
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf(leo.id), picked)
        press(listOf(Key.DirectionDown))
        rule.onNodeWithTag(KidRulesTags.DAILY).assertIsFocused()
        focusStops.assertCountEquals(6)
    }

    @Test
    fun `with one kid there is no switch and Up stays on the first rule`() {
        state = state.copy(kids = listOf(RulesKid(UUID(0, 2), "Lucy")))
        show()
        rule.onNodeWithTag(KidRulesTags.kid(0)).assertDoesNotExist()
        press(listOf(Key.DirectionUp))
        rule.onNodeWithTag(KidRulesTags.DAILY).assertIsFocused()
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
        rule.onNodeWithTag(KidRulesTags.DAILY_VALUE).assertTextEquals("1 h")
        assertEquals(emptyList<RuleChange>(), saved)
    }

    @Test
    fun `breaks step and save, with no limit last`() {
        show()
        press(listOf(Key.DirectionDown, Key.DirectionRight))
        rule.onNodeWithTag(KidRulesTags.SITTING_VALUE).assertTextEquals("45 min")
        repeat(10) { press(listOf(Key.DirectionRight)) }
        rule.onNodeWithTag(KidRulesTags.SITTING_VALUE).assertTextEquals("No limit")
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf<RuleChange>(RuleChange.SittingLimit(null)), saved)
    }

    @Test
    fun `autoplay goes from off to 1 and stops at 10 episodes`() {
        show()
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionLeft))
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("No")
        press(listOf(Key.DirectionRight))
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("1 in a row")
        repeat(15) { press(listOf(Key.DirectionRight)) }
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("10 in a row")
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
        rule.onNodeWithTag(KidRulesTags.SEARCH_VALUE).assertTextEquals("Yes")
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf<RuleChange>(RuleChange.Search(true)), saved)
    }

    @Test
    fun `without the plugin's sitting limit there is no Breaks row`() {
        state = state.copy(sessionLimitEditable = false)
        show()
        rule.onNodeWithTag(KidRulesTags.SITTING).assertDoesNotExist()
        rule.onNodeWithText("Breaks").assertDoesNotExist()
        focusStops.assertCountEquals(3)
        press(listOf(Key.DirectionDown))
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY).assertIsFocused()
    }

    @Test
    fun `a save says Saved by the name of its rule`() {
        show()
        rule.onNodeWithText("Saved").assertDoesNotExist()
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionRight, Key.DirectionCenter))
        state = state.copy(saved = true, profile = state.profile?.copy(autoplayEnabled = true, autoplayCap = 1))
        rule.waitForIdle()
        rule.onNodeWithText("Saved").assertExists()
        rule.onNodeWithTag(KidRulesTags.AUTOPLAY_VALUE).assertTextEquals("1 in a row")
    }
}
