package dev.aldoborrero.peke.ui.nav

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import dev.aldoborrero.peke.ui.tryRequestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back with the sidebar open, wired as [NavDrawer] wires it: on the home it is left to the system (which quits), on any
 * other page it closes the sidebar and gives focus back to where it was on the page; the next Back goes back a page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DrawerBackCloseTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val drawerState = DrawerState(DrawerValue.Closed)
    private val second = FocusRequester()

    /** Back that got past the page: a page back, or the system quitting on the home */
    private var passedBack = 0

    @Composable
    private fun Page(destination: Destination) {
        BackHandler { passedBack++ }
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) {
            inputMode.requestInputMode(InputMode.Keyboard)
            second.requestFocus()
        }
        val selected = remember { FocusRequester() }
        val pageFocus = remember { FocusRequester() }
        BackHandler(enabled = backOpensNavDrawer(drawerState.currentValue, destination)) {
            drawerState.setValue(DrawerValue.Open)
            selected.tryRequestFocus()
        }
        BackHandler(enabled = backClosesNavDrawer(drawerState.currentValue, destination)) {
            pageFocus.tryRequestFocus()
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            initialFocus = selected,
            drawerContent = {
                Column {
                    Box(
                        Modifier
                            .size(48.dp)
                            .testTag("selected")
                            .focusRequester(selected)
                            .focusable(),
                    )
                    Box(Modifier.size(48.dp).focusable())
                }
            },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(pageFocus)
                    .focusRestorer()
                    .focusGroup(),
            ) {
                // Keyed like a page's rows, so the restorer tells them apart
                Column(Modifier.padding(start = 200.dp)) {
                    key("first") { Box(Modifier.size(100.dp).testTag("first").focusable()) }
                    key("second") {
                        Box(
                            Modifier
                                .size(100.dp)
                                .testTag("second")
                                .focusRequester(second)
                                .focusable(),
                        )
                    }
                }
            }
        }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun `on a page Back with the sidebar open closes it and focus goes back where it was`() {
        rule.setContent { Page(Destination.Search()) }
        rule.onNodeWithTag("second").assertIsFocused()
        press(Key.DirectionLeft)
        assertEquals(DrawerValue.Open, drawerState.currentValue)

        back()
        assertEquals(DrawerValue.Closed, drawerState.currentValue)
        rule.onNodeWithTag("second").assertIsFocused()
        assertEquals(0, passedBack)

        back()
        assertEquals(1, passedBack)
    }

    @Test
    fun `on a page Back with the sidebar closed goes back a page`() {
        rule.setContent { Page(Destination.Search()) }
        back()
        assertEquals(1, passedBack)
        assertEquals(DrawerValue.Closed, drawerState.currentValue)
    }

    @Test
    fun `on the home Back opens the sidebar and a second Back is left to the system`() {
        rule.setContent { Page(Destination.Home()) }
        back()
        assertEquals(DrawerValue.Open, drawerState.currentValue)
        rule.onNodeWithTag("selected").assertIsFocused()
        assertEquals(0, passedBack)

        back()
        assertEquals(1, passedBack)
    }
}
