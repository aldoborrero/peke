package dev.aldoborrero.peke.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The search box on the D-pad (KSearchPekeTV): Down leaves it for what is below, Up comes back, and OK starts typing.
 * Key events from the test, like adb's and some remotes', don't come from a D-pad device, which the text field alone
 * took as cursor moves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SearchBoxKeysTest {
    @get:Rule
    val rule = createComposeRule()

    private val box = FocusRequester()
    private var started = 0

    @Composable
    private fun Page() {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) {
            inputMode.requestInputMode(InputMode.Keyboard)
            box.requestFocus()
        }
        PekeTheme {
            var editing by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            Column {
                SearchEditTextBox(
                    value = "bing",
                    onValueChange = {},
                    onSearchClick = {},
                    readOnly = !editing,
                    modifier =
                        Modifier
                            .testTag("box")
                            .focusRequester(box)
                            .searchBoxKeys(editing = editing, focusManager = focusManager) {
                                started++
                                editing = true
                            },
                )
                Column(Modifier.testTag("result").size(100.dp).focusable()) {}
            }
        }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `Down leaves the box for the results and Up comes back`() {
        rule.setContent { Page() }
        rule.onNodeWithTag("box").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithTag("result").assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithTag("box").assertIsFocused()
    }

    @Test
    fun `OK on the box starts typing, Left then moves the cursor, and Down still leaves`() {
        rule.setContent { Page() }
        press(Key.DirectionCenter)
        assertEquals(1, started)
        press(Key.DirectionLeft)
        rule.onNodeWithTag("box").assertIsFocused()
        // The TV keyboard closes on Back without the page knowing, so typing may still look on
        press(Key.DirectionDown)
        rule.onNodeWithTag("result").assertIsFocused()
    }
}
