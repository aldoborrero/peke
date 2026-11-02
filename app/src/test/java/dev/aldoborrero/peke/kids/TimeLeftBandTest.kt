package dev.aldoborrero.peke.kids

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.ui.TimeLeftBand
import dev.aldoborrero.peke.kids.ui.TimeLeftBar
import dev.aldoborrero.peke.ui.theme.PekeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * The time-left label never covers a page: the bar and label say how much room they take, and the pages in the sidebar
 * keep out of it as NavDrawer does, so a title or the search box scrolled up stops below the label (or above it at the
 * bottom), for the bar's normal and bigger near-the-end sizes
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TimeLeftBandTest {
    @get:Rule
    val rule = createComposeRule()

    private val band = TimeLeftBand()
    private val list = LazyListState()
    private var shown by mutableStateOf(true)

    @Composable
    private fun Screen(
        position: KidsTimerBarPosition,
        nearEnd: Boolean,
    ) {
        PekeTheme {
            Box(Modifier.fillMaxSize()) {
                // The page, padded as NavDrawer pads it, with a header of title rows and more below
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().padding(top = band.top, bottom = band.bottom),
                ) {
                    items(30) { Box(Modifier.fillMaxWidth().height(60.dp).testTag("row")) }
                }
                if (shown) {
                    TimeLeftBar(
                        remaining = 89.minutes,
                        total = 120.minutes,
                        nearEnd = nearEnd,
                        position = position,
                        band = band,
                        modifier =
                            Modifier.align(
                                if (position ==
                                    KidsTimerBarPosition.BOTTOM
                                ) {
                                    Alignment.BottomCenter
                                } else {
                                    Alignment.TopCenter
                                },
                            ),
                    )
                }
            }
        }
    }

    private fun label(): Rect = rule.onNodeWithContentDescription("minutes left", substring = true).fetchSemanticsNode().boundsInRoot

    /** Every row on screen, scrolled to [index], is clear of the label */
    private fun assertClearAt(index: Int) {
        rule.waitForIdle()
        rule.runOnIdle { runBlocking { list.scrollToItem(index, 25) } }
        rule.waitForIdle()
        val label = label()
        val rows =
            rule
                .onAllNodesWithTag("row")
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .filter { it.height > 0f }
        assertTrue("rows on screen", rows.isNotEmpty())
        rows.forEach { assertTrue("a row at $it under the label at $label", !it.overlaps(label)) }
    }

    @Test
    fun `at the top, a scrolled page stops below the label`() {
        rule.setContent { Screen(KidsTimerBarPosition.TOP, nearEnd = false) }
        assertClearAt(0)
        assertClearAt(10)
        assertEquals(0f, band.bottom.value)
    }

    @Test
    fun `near the end the bigger label still keeps clear`() {
        rule.setContent { Screen(KidsTimerBarPosition.TOP, nearEnd = true) }
        rule.mainClock.advanceTimeBy(1_000)
        assertClearAt(10)
    }

    @Test
    fun `at the bottom, the page ends above the label`() {
        rule.setContent { Screen(KidsTimerBarPosition.BOTTOM, nearEnd = false) }
        assertClearAt(10)
        assertEquals(0f, band.top.value)
    }

    @Test
    fun `without the bar the page takes the whole height`() {
        rule.setContent { Screen(KidsTimerBarPosition.TOP, nearEnd = false) }
        rule.waitForIdle()
        assertTrue(band.top.value > 0f)
        shown = false
        rule.waitForIdle()
        assertEquals(0f, band.top.value)
        assertEquals(0f, band.bottom.value)
    }
}
