package dev.aldoborrero.peke.ui.nav

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Left from a kid page opens the sidebar on the page's own entry, as Back on the home does, even when what had focus
 * sits level with the kid's avatar at the top, as the search box does; the avatar leads to the parent gate. Inside the
 * sidebar the avatar stays reachable with Up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DrawerEnterFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val drawerState = DrawerState(DrawerValue.Closed)
    private val box = FocusRequester()

    @Composable
    private fun Page() {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) {
            inputMode.requestInputMode(InputMode.Keyboard)
            box.requestFocus()
        }
        val search = remember { FocusRequester() }
        ModalNavigationDrawer(
            drawerState = drawerState,
            initialFocus = search,
            drawerContent = {
                Column {
                    // The kid's avatar first, then the pages, Search selected
                    Box(Modifier.size(48.dp).testTag("avatar").focusable())
                    Spacer(Modifier.height(40.dp))
                    Box(
                        Modifier
                            .size(48.dp)
                            .testTag("search")
                            .focusRequester(search)
                            .focusable(),
                    )
                    Box(Modifier.size(48.dp).testTag("home").focusable())
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                // The search box, level with the avatar
                Box(
                    Modifier
                        .padding(start = 200.dp, top = 30.dp)
                        .size(400.dp, 54.dp)
                        .testTag("box")
                        .focusRequester(box)
                        .focusable(),
                )
            }
        }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `Left from the page opens the sidebar on the page's entry, not the avatar`() {
        rule.setContent { Page() }
        rule.onNodeWithTag("box").assertIsFocused()
        press(Key.DirectionLeft)
        rule.onNodeWithTag("search").assertIsFocused()
        rule.onNodeWithTag("avatar").assertIsNotFocused()
        assertEquals(DrawerValue.Open, drawerState.currentValue)
    }

    @Test
    fun `the avatar is still reachable with Up inside the sidebar`() {
        rule.setContent { Page() }
        press(Key.DirectionLeft)
        press(Key.DirectionUp)
        rule.onNodeWithTag("avatar").assertIsFocused()
    }

    @Test
    fun `Right leaves the sidebar for the page again`() {
        rule.setContent { Page() }
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        rule.onNodeWithTag("box").assertIsFocused()
    }
}
