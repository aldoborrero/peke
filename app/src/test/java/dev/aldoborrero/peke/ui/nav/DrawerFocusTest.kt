package dev.aldoborrero.peke.ui.nav

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Each nav entry has its own drawer, all sharing one [DrawerState]. When the drawer opens, focus goes to the selected
 * item of the entry on screen, never to the first item (the kid's avatar, which leads to the parent gate), and a drawer
 * on its way out never takes focus (constitution V, spec 005 FR-013).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrawerFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val drawerState = DrawerState(DrawerValue.Closed)

    @Composable
    private fun Drawer(
        name: String,
        active: Boolean,
    ) {
        val home = remember { FocusRequester() }
        ModalNavigationDrawer(
            drawerState = drawerState,
            activeEntry = active,
            initialFocus = home,
            drawerContent = {
                Column {
                    // First in the drawer, as the profile icon is
                    Row(Modifier.size(10.dp).testTag("$name-avatar").focusable()) {}
                    Row(
                        Modifier
                            .size(10.dp)
                            .testTag("$name-home")
                            .focusRequester(home)
                            .focusable(),
                    ) {}
                }
            },
        ) {
            Row(Modifier.size(10.dp).testTag("$name-content").focusable()) {}
        }
    }

    @Composable
    private fun Keyboard() {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
    }

    @Test
    fun `opening the drawer focuses the selected item, not the avatar`() {
        rule.setContent {
            Keyboard()
            Drawer("one", active = true)
        }
        rule.runOnIdle { drawerState.setValue(DrawerValue.Open) }

        rule.onNodeWithTag("one-home").assertIsFocused()
        rule.onNodeWithTag("one-avatar").assertIsNotFocused()
    }

    @Test
    fun `a new entry composed while the drawer is open focuses its selected item`() {
        var second by mutableStateOf(false)
        rule.setContent {
            Keyboard()
            Drawer("old", active = !second)
            if (second) Drawer("new", active = true)
        }
        rule.runOnIdle { drawerState.setValue(DrawerValue.Open) }
        // A home reload: the new entry comes in while the old one is still leaving
        rule.runOnIdle { second = true }

        rule.onNodeWithTag("new-home").assertIsFocused()
        rule.onNodeWithTag("new-avatar").assertIsNotFocused()
        rule.onNodeWithTag("old-avatar").assertIsNotFocused()
    }

    @Test
    fun `a leaving entry never takes focus when the drawer opens`() {
        rule.setContent {
            Keyboard()
            Column {
                Drawer("old", active = false)
                Drawer("new", active = true)
            }
        }
        rule.runOnIdle { drawerState.setValue(DrawerValue.Open) }

        rule.onNodeWithTag("new-home").assertIsFocused()
        rule.onNodeWithTag("old-avatar").assertIsNotFocused()
        rule.onNodeWithTag("old-home").assertIsNotFocused()
    }
}
