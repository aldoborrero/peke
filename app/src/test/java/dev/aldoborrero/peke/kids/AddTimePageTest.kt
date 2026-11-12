package dev.aldoborrero.peke.kids

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.aldoborrero.peke.kids.timer.AddTimePreview
import dev.aldoborrero.peke.kids.timer.AddTimeSteps
import dev.aldoborrero.peke.kids.timer.TodayChange
import dev.aldoborrero.peke.kids.ui.AddTimeAddedPage
import dev.aldoborrero.peke.kids.ui.AddTimePage
import dev.aldoborrero.peke.kids.ui.AddTimeTags
import dev.aldoborrero.peke.kids.ui.GrownUpWindow
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Add time as board 9 with spec 006's stepper, and its confirmation as an S5 pill (spec 021 US2)
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class AddTimePageTest {
    @get:Rule
    val rule = createComposeRule()

    private var index by mutableIntStateOf(0)
    private var confirms = 0
    private var backs = 0
    private val until = LocalDateTime.of(2026, 11, 11, 19, 15)

    private fun showStepper(start: Int) {
        index = start
        rule.setContent {
            PekeTheme {
                GrownUpWindow(onBack = { backs++ }) {
                    AddTimePage(
                        kidName = "Lucy",
                        index = index,
                        sending = false,
                        error = null,
                        preview = AddTimePreview(until, null, null, null),
                        onStep = { index = AddTimeSteps.step(index, it) },
                        onConfirm = { confirms++ },
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
    fun `the page shows where it is, the stepper focused, the forecast and Back`() {
        showStepper(2)
        rule.onNodeWithText("Grown-up menu · Lucy").assertExists()
        rule.onNodeWithText("Add time for Lucy").assertExists()
        rule.onNodeWithText("15 min").assertExists()
        rule.onNodeWithText("Back").assertExists()
        rule.onNodeWithTag(AddTimeTags.STEPPER).assertIsFocused()
    }

    @Test
    fun `the arrows step without wrapping, dimmed at the ends, and OK adds`() {
        showStepper(0)
        rule.onNodeWithTag(AddTimeTags.LESS + "-dimmed").assertExists()
        press(AddTimeTags.STEPPER, listOf(Key.DirectionLeft))
        assertEquals(0, index)
        press(AddTimeTags.STEPPER, listOf(Key.DirectionRight, Key.DirectionRight))
        rule.onNodeWithText("15 min").assertExists()
        rule.onNodeWithTag(AddTimeTags.LESS).assertExists()
        press(AddTimeTags.STEPPER, listOf(Key.DirectionCenter))
        assertEquals(1, confirms)
    }

    @Test
    fun `at 2 h the right arrow is dimmed`() {
        showStepper(AddTimeSteps.MINUTES.lastIndex)
        rule.onNodeWithText("2 h").assertExists()
        rule.onNodeWithTag(AddTimeTags.MORE + "-dimmed").assertExists()
    }

    @Test
    fun `Back returns, without adding`() {
        showStepper(2)
        press(AddTimeTags.STEPPER, listOf(Key.Back))
        assertEquals(1, backs)
        assertEquals(0, confirms)
    }

    @Test
    fun `added is a pill with Done focused and Undo, and once undone it says so`() {
        var undone by mutableStateOf<TodayChange?>(null)
        var undos = 0
        rule.setContent {
            PekeTheme {
                GrownUpWindow(onBack = {}) {
                    AddTimeAddedPage(
                        kidName = "Lucy",
                        minutes = 15,
                        until = until,
                        canUndo = true,
                        undoSeconds = 10,
                        undoing = false,
                        undoError = null,
                        undone = undone,
                        onUndo = { undos++ },
                        onDone = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(AddTimeTags.ADDED).assertExists()
        rule.onNodeWithTag(AddTimeTags.DONE).assertIsFocused()
        press(AddTimeTags.DONE, listOf(Key.DirectionRight, Key.DirectionCenter))
        assertEquals(1, undos)

        undone = TodayChange.DONE
        rule.waitForIdle()
        rule.onNodeWithText("The time was taken back").assertExists()
        rule.onNodeWithTag(AddTimeTags.UNDO).assertDoesNotExist()
    }
}
