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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.aldoborrero.peke.kids.server.KidsStats
import dev.aldoborrero.peke.kids.server.KidsStatsDay
import dev.aldoborrero.peke.kids.server.KidsTopSeries
import dev.aldoborrero.peke.kids.ui.GrownUpSurface
import dev.aldoborrero.peke.kids.ui.stats.KidStatsContent
import dev.aldoborrero.peke.kids.ui.stats.KidStatsState
import dev.aldoborrero.peke.kids.ui.stats.KidStatsTags
import dev.aldoborrero.peke.kids.ui.stats.KidToday
import dev.aldoborrero.peke.kids.ui.stats.StatsBar
import dev.aldoborrero.peke.kids.ui.stats.StatsKid
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.util.UUID

/**
 * Stats (spec 033): one read-only card per kid, focus walking them; today's figures and status, the week's bars with
 * the allowance line and today and over-limit days marked, the most watched with advanced stats, and the page's line
 * for each state
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidStatsPageTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * Friday 13 to Thursday 19 November 2026: 40, 75 (over 60), 30, 70 with 15 extra (not over), 0, 55, and 29 today
     */
    private fun week(advanced: Boolean) =
        KidsStats(
            advancedStats = advanced,
            days =
                listOf(40, 75, 30, 70, 0, 55, 29).mapIndexed { i, used ->
                    KidsStatsDay("2026-11-${13 + i}", used, if (i == 3) 15 else 0, 60)
                },
            topSeries =
                if (advanced) {
                    listOf(
                        KidsTopSeries(name = "Bluey", minutes = 80),
                        KidsTopSeries(name = "Hey Duggee", minutes = 45),
                        KidsTopSeries(name = "PekeTV", minutes = 120, isLiveTv = true),
                        KidsTopSeries(name = "Fourth", minutes = 10),
                    )
                } else {
                    emptyList()
                },
        )

    private val manuel =
        StatsKid(UUID(0, 1), "Manuel", watching = true, today = KidToday(29, 31, 10, null, false), week = week(true))
    private val lucia = StatsKid(UUID(0, 2), "Lucía", today = KidToday(52, 8, 0, 8, false), week = week(true))

    private var state by mutableStateOf(
        KidStatsState(loading = false, statsSupported = true, advanced = true, kids = listOf(manuel, lucia)),
    )

    private fun show() {
        rule.setContent {
            PekeTheme {
                GrownUpSurface { KidStatsContent(state) }
            }
        }
        rule.waitForIdle()
    }

    private val focusStops get() = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused))

    @Test
    fun `stats with advanced stats on show today, the week and the most watched`() {
        show()
        rule.onNodeWithText("Stats").assertExists()
        rule.onNodeWithText("Today and the last 7 days").assertExists()
        rule.onNodeWithText("Watching now").assertExists()
        rule.onNodeWithText("On a break · 8 min").assertExists()
        rule.onNodeWithText("29 min").assertExists()
        rule.onNodeWithText("31 min").assertExists()
        rule.onNodeWithText("+10 min").assertExists()
        rule.onAllNodesWithTag(KidStatsTags.top(0)).assertCountEquals(1)
        rule.onAllNodesWithText("MOST WATCHED THIS WEEK").assertCountEquals(2)
        rule.onAllNodesWithText("Bluey").assertCountEquals(2)
        rule.onAllNodesWithText("1 h 20").assertCountEquals(2)
        rule.onAllNodesWithText("Hey Duggee").assertCountEquals(2)
        // Two, as the boards' cards hold
        rule.onNodeWithText("PekeTV, live").assertDoesNotExist()
        // The legend, and the over-limit day says by how much
        rule.onNodeWithText("Over the limit").assertExists()
        rule.onAllNodesWithText("+15").assertCountEquals(2)
        rule.onAllNodesWithText("1 h").assertCountEquals(2)
        rule.onNodeWithText("More detail on the Kids plugin's page of the dashboard, from a phone or a computer.").assertExists()
    }

    @Test
    fun `the bars mark today and the days over the limit, with the allowance line on every day`() {
        show()
        rule.onAllNodesWithTag(KidStatsTags.BAR_TODAY).assertCountEquals(2)
        rule.onAllNodesWithTag(KidStatsTags.BAR_OVER).assertCountEquals(2)
        rule.onAllNodesWithTag(KidStatsTags.BAR).assertCountEquals(10)
        rule.onAllNodesWithTag(KidStatsTags.ALLOWANCE).assertCountEquals(14)
    }

    @Test
    fun `bars from Days, scaled to the week's top, today last and over only past allowance plus extra time`() {
        val bars = StatsBar.of(week(false).days)
        assertEquals(7, bars.size)
        assertEquals(listOf(false, false, false, false, false, false, true), bars.map { it.today })
        assertEquals(listOf(false, true, false, false, false, false, false), bars.map { it.over })
        assertEquals(1f, bars[1].height)
        assertEquals(60f / 75, bars[0].allowance!!, 0.001f)
        assertEquals(0f, bars[4].height)
        assertEquals(DayOfWeek.FRIDAY, bars[0].dayOfWeek)
        assertEquals(DayOfWeek.THURSDAY, bars[6].dayOfWeek)
        // An unlimited day has no allowance mark
        assertNull(StatsBar.of(listOf(KidsStatsDay("2026-11-19", 30, 0, null))).single().allowance)
        assertEquals(emptyList<StatsBar>(), StatsBar.of(emptyList()))
    }

    @Test
    fun `with advanced stats off there is no most watched, and one line to turn it on`() {
        state =
            state.copy(advanced = false, kids = state.kids.map { it.copy(week = week(false)) })
        show()
        rule.onNodeWithText("MOST WATCHED THIS WEEK").assertDoesNotExist()
        rule.onAllNodesWithTag(KidStatsTags.BAR_TODAY).assertCountEquals(2)
        rule
            .onNodeWithText("To see what each kid watched, turn on \"Advanced stats\" on the Kids plugin's dashboard page.")
            .assertExists()
    }

    @Test
    fun `without the plugin's stats only today shows, no bars`() {
        state = KidStatsState(loading = false, statsSupported = false, kids = state.kids.map { it.copy(week = null) })
        show()
        rule.onNodeWithText("Today").assertExists()
        rule.onNodeWithText("29 min").assertExists()
        rule.onAllNodesWithTag(KidStatsTags.BAR_TODAY).assertCountEquals(0)
        rule.onAllNodesWithTag(KidStatsTags.ALLOWANCE).assertCountEquals(0)
        rule.onNodeWithText("The last 7 days come with a newer Kids plugin.").assertExists()
    }

    @Test
    fun `when the week couldn't be loaded the page says so, today only and no bars`() {
        state = state.copy(advanced = null, weekFailed = true, kids = state.kids.map { it.copy(week = null) })
        show()
        rule.onNodeWithText("Today").assertExists()
        rule.onNodeWithText("29 min").assertExists()
        rule.onAllNodesWithTag(KidStatsTags.BAR_TODAY).assertCountEquals(0)
        rule.onNodeWithText("The last 7 days couldn't be loaded.").assertExists()
        rule.onNodeWithTag(KidStatsTags.OFFLINE).assertDoesNotExist()
    }

    @Test
    fun `with the plugin gone the cards show the cached figures with Offline and the menu's notice`() {
        state =
            state.copy(
                statsSupported = false,
                advanced = null,
                pluginGone = true,
                kids = state.kids.map { it.copy(stale = true, week = null) },
            )
        show()
        rule.onNodeWithTag(KidStatsTags.OFFLINE).assertDoesNotExist()
        rule.onNodeWithText("29 min").assertExists()
        rule.onAllNodesWithText("Offline").assertCountEquals(2)
        rule.onNodeWithText("This server no longer has the Kids plugin").assertExists()
        rule.onNodeWithText("Can't reach the server at home").assertDoesNotExist()
    }

    @Test
    fun `with the plugin too old the line says to update it`() {
        state =
            state.copy(
                statsSupported = false,
                advanced = null,
                pluginGone = true,
                pluginOutdated = true,
                kids = state.kids.map { it.copy(stale = true, week = null) },
            )
        show()
        rule.onNodeWithText("This server's Kids plugin is too old").assertExists()
    }

    @Test
    fun `with no kid's status from the server the page says it can't reach it, without cards`() {
        state = state.copy(offline = true, kids = state.kids.map { it.copy(stale = true, week = null) })
        show()
        rule.onNodeWithTag(KidStatsTags.OFFLINE).assertExists()
        rule.onNodeWithText("Can't reach the server at home").assertExists()
        rule.onNodeWithText("The stats come back as soon as it can be reached.").assertExists()
        rule.onNodeWithTag(KidStatsTags.kid(0)).assertDoesNotExist()
    }

    @Test
    fun `a kid the server didn't answer for shows the cached figures with Offline, or no figures yet`() {
        state =
            state.copy(
                kids = listOf(manuel, lucia.copy(stale = true), lucia.copy(id = UUID(0, 3), today = null, stale = true)),
            )
        show()
        rule.onNodeWithTag(KidStatsTags.OFFLINE).assertDoesNotExist()
        rule.onNodeWithText("29 min").assertExists()
        rule.onNodeWithText("Offline").assertExists()
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithText("No figures yet").assertExists()
    }

    @Test
    fun `one kid is one card and the only focus stop`() {
        state = state.copy(kids = listOf(manuel))
        show()
        rule.onNodeWithTag(KidStatsTags.kid(0)).assertIsFocused()
        rule.onNodeWithTag(KidStatsTags.kid(1)).assertDoesNotExist()
        focusStops.assertCountEquals(1)
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(KidStatsTags.kid(0)).assertIsFocused()
    }

    @Test
    fun `focus walks the cards, two then four kids, the row scrolling to the last`() {
        val four =
            listOf(manuel, lucia) + (3..4).map { StatsKid(UUID(0, it.toLong()), "Kid $it", today = KidToday(it, null, 0, null, false)) }
        state = state.copy(kids = four)
        show()
        rule.onNodeWithTag(KidStatsTags.kid(0)).assertIsFocused()
        repeat(3) { i ->
            rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
            rule.waitForIdle()
            rule.onNodeWithTag(KidStatsTags.kid(i + 1)).assertIsFocused()
        }
        rule.onNode(hasTestTag(KidStatsTags.kid(3))).assertExists()
        rule.onAllNodesWithText("No limit").assertCountEquals(2)
    }

    @Test
    fun `time's up shows on the card`() {
        state = state.copy(kids = listOf(manuel.copy(watching = false, today = KidToday(60, 0, 0, null, true))))
        show()
        rule.onNodeWithText("Time's up for today").assertExists()
        rule.onNodeWithText("Watching now").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "es-w960dp-h540dp-xhdpi")
    fun `in Spanish too`() {
        show()
        rule.onNodeWithText("Estadísticas").assertExists()
        rule.onNodeWithText("Hoy y los últimos 7 días").assertExists()
        rule.onNodeWithText("Viendo ahora").assertExists()
        rule.onNodeWithText("Descansando · 8 min").assertExists()
        rule.onAllNodesWithText("LO MÁS VISTO ESTA SEMANA").assertCountEquals(2)
        rule.onAllNodesWithText("visto hoy").assertCountEquals(2)
        rule.onAllNodesWithText("X").assertCountEquals(2)
    }
}
