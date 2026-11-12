package dev.aldoborrero.peke.kids

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dev.aldoborrero.peke.kids.timer.KidsTimerLabelPlacement
import dev.aldoborrero.peke.ui.preferences.TimerPlacementPreference
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › This TV's "Timer on the bar" row: it shows the current placement and changes it (spec 022 FR-005)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TimerPlacementPreferenceTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var placement by mutableStateOf(KidsTimerLabelPlacement.CENTER)

    private fun show() {
        rule.setContent {
            PekeTheme {
                TimerPlacementPreference(
                    placement = placement,
                    onChange = { placement = it },
                    modifier = Modifier.testTag("row"),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the row shows where the time sits`() {
        show()
        rule.onNodeWithText("Timer on the bar").assertExists()
        rule.onNodeWithText("In the middle").assertExists()
    }

    /** Focus the row, OK opens the choice on the current placement, [moves] D-pad presses, then OK picks */
    @OptIn(ExperimentalTestApi::class)
    private fun pick(moves: List<Key>) {
        rule.onNodeWithTag("row").requestFocus()
        rule.onNodeWithTag("row").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        // The choice opens on the current placement
        rule.onNode(isFocused() and hasAnyAncestor(isDialog())).performKeyInput {
            moves.forEach { pressKey(it) }
            pressKey(Key.DirectionCenter)
        }
        rule.waitForIdle()
    }

    @Test
    fun `picking a placement with the D-pad changes it`() {
        show()
        pick(listOf(Key.DirectionDown))
        assertEquals(KidsTimerLabelPlacement.END, placement)
        rule.onNodeWithText("On the right").assertExists()

        pick(listOf(Key.DirectionUp, Key.DirectionUp))
        assertEquals(KidsTimerLabelPlacement.START, placement)
        rule.onNodeWithText("On the left").assertExists()
    }

    @Test
    @Config(qualifiers = "es-w960dp-h540dp-xhdpi")
    fun `in Spanish too`() {
        show()
        rule.onNodeWithText("Tiempo en la barra").assertExists()
        rule.onNodeWithText("En el centro").assertExists()
    }
}
