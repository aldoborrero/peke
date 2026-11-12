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
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A kid's rules as board 10 (spec 021 US3): Daily time is the only focus stop and a stepper that saves on OK; the rest
 * is text
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidRulesPageTest {
    @get:Rule
    val rule = createComposeRule()

    private val saved = mutableListOf<Int?>()
    private var state by mutableStateOf(
        KidRulesState(
            loading = false,
            kidName = "Lucy",
            profile = KidProfile(userId = 1, searchAllowed = true, dailyAllowanceMinutes = 60, sessionLimitMinutes = 30),
            allowanceSupported = true,
            sessionLimitSupported = true,
            usedMinutes = 15,
            todayMinutes = 60,
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
                GrownUpSurface { KidRulesContent(state, onSaveDaily = { saved += it }) }
            }
        }
        rule.waitForIdle()
    }

    private fun press(keys: List<Key>) {
        rule.onNodeWithTag(KidRulesTags.DAILY).performKeyInput { keys.forEach { pressKey(it) } }
        rule.waitForIdle()
    }

    @Test
    fun `the title, the server line and daily time focused, the only focus stop`() {
        show()
        rule.onNodeWithText("Lucy's rules").assertExists()
        rule.onNodeWithText("Saved on the server, so every TV follows them").assertExists()
        rule.onNodeWithTag(KidRulesTags.DAILY).assertIsFocused()
        rule.onNodeWithTag(KidRulesTags.DAILY_VALUE).assertTextEquals("60 minutes")
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).assertCountEquals(1)
        rule.onNodeWithText("https://jellyfin.home/web/#/configurationpage?name=Kids").assertExists()
    }

    @Test
    fun `Right then OK saves the next value`() {
        show()
        press(listOf(Key.DirectionRight))
        rule.onNodeWithText("OK saves · Back leaves it as it was").assertExists()
        assertEquals(emptyList<Int?>(), saved)
        press(listOf(Key.DirectionCenter))
        assertEquals(1, saved.size)
        assert(saved.single()!! > 60)
    }

    @Test
    fun `Up and Down scroll the rules and keep focus on daily time`() {
        show()
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionUp))
        rule.onNodeWithTag(KidRulesTags.DAILY).assertIsFocused()
        assertEquals(emptyList<Int?>(), saved)
    }

    @Test
    fun `OK on the saved value saves nothing, and Left stops at the first value`() {
        show()
        press(listOf(Key.DirectionCenter))
        assertEquals(emptyList<Int?>(), saved)
        repeat(20) { press(listOf(Key.DirectionLeft)) }
        press(listOf(Key.DirectionCenter))
        assertEquals(1, saved.size)
        assert(saved.single()!! < 60)
    }
}
