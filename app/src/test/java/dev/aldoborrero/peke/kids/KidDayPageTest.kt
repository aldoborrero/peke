package dev.aldoborrero.peke.kids

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KidsSnapshot
import dev.aldoborrero.peke.kids.ui.GrownUpSurface
import dev.aldoborrero.peke.kids.ui.day.KidDay
import dev.aldoborrero.peke.kids.ui.day.KidDayContent
import dev.aldoborrero.peke.kids.ui.day.KidDayState
import dev.aldoborrero.peke.kids.ui.day.KidDayTags
import dev.aldoborrero.peke.kids.ui.day.KidDayViewModel
import dev.aldoborrero.peke.ui.theme.PekeTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * "{Kid}'s day" (spec 030 US3): today's use, apart from the rules, as text with no focus stop of its own
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidDayPageTest {
    @get:Rule
    val rule = createComposeRule()

    private val day =
        KidDay(
            usedMinutes = 35,
            allowanceMinutes = 75,
            bonusMinutes = 15,
            leftMinutes = 40,
            sittingUsedMinutes = 20,
            sittingLimitMinutes = 30,
            breakLeftMinutes = 8,
        )
    private var state by mutableStateOf(KidDayState(kidName = "Lucy", day = day))

    private fun show() {
        rule.setContent {
            PekeTheme {
                GrownUpSurface { KidDayContent(state) }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the kid's figures for today, and the page the only focus stop`() {
        show()
        rule.onNodeWithText("Lucy's day").assertExists()
        rule.onNodeWithText("Watched today").assertExists()
        rule.onNodeWithText("35 of 75 minutes").assertExists()
        rule.onNodeWithText("Left today").assertExists()
        rule.onNodeWithText("40 minutes").assertExists()
        rule.onNodeWithText("Time added today").assertExists()
        rule.onNodeWithText("15 min").assertExists()
        rule.onNodeWithText("Watched in a row").assertExists()
        rule.onNodeWithText("20 of 30 minutes").assertExists()
        rule.onNodeWithText("8 min to go").assertExists()
        rule.onNodeWithTag(KidDayTags.PAGE).assertIsFocused()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).assertCountEquals(1)
        // The rules stay on the rules page
        rule.onNodeWithText("Autoplay").assertDoesNotExist()
    }

    @Test
    fun `no daily limit, no time added, no sitting limit`() {
        state = state.copy(day = KidDay(12, null, 0, null, null, null, null))
        show()
        rule.onNodeWithText("12 minutes").assertExists()
        rule.onNodeWithText("Unlimited").assertExists()
        rule.onNodeWithText("Time added today").assertDoesNotExist()
        rule.onNodeWithText("Watched in a row").assertDoesNotExist()
        rule.onNodeWithText("On a break").assertDoesNotExist()
    }

    @Test
    fun `before the server answers, it says so`() {
        state = KidDayState(kidName = "Lucy", reachable = false)
        show()
        rule.onNodeWithText("Can't reach the server. Try again in a moment.").assertExists()
    }

    @Test
    fun `the status maps to minutes, with time left and breaks rounded up`() {
        val status =
            KidsStatus(
                usedSeconds = 35 * 60 + 30,
                allowanceSeconds = 60 * 60,
                bonusSeconds = 15 * 60,
                remainingSeconds = 39 * 60 + 1,
                sittingLimitSeconds = 30 * 60,
                sittingUsedSeconds = 20 * 60,
                breakRemainingSeconds = 61,
            )
        assertEquals(KidDay(35, 75, 15, 40, 20, 30, 2), KidDay.from(status))
        val unlimited = KidDay.from(KidsStatus(usedSeconds = 600))
        assertNull(unlimited.allowanceMinutes)
        assertNull(unlimited.leftMinutes)
        assertNull(unlimited.sittingLimitMinutes)
        assertNull(unlimited.breakLeftMinutes)
    }

    @Test
    fun `the view model asks for a fresh status and shows the current kid`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val serverId = UUID.randomUUID()
            val kid = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Leo", serverId = serverId, accessToken = "t")
            val allowance =
                mockk<KidsAllowanceService>(relaxed = true) {
                    every { snapshot } returns MutableStateFlow(KidsSnapshot(KidsStatus(usedSeconds = 300), 0))
                    every { reachable } returns MutableStateFlow(true)
                }
            val vm =
                KidDayViewModel(
                    mockk<ServerRepository> {
                        every { current } returns MutableStateFlow(CurrentUser(JellyfinServer(serverId, "home", "https://jf/", null), kid))
                    },
                    allowance,
                )
            verify { allowance.refresh() }
            assertEquals("Leo", vm.state.value.kidName)
            assertEquals(
                5,
                vm.state.value.day
                    ?.usedMinutes,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
