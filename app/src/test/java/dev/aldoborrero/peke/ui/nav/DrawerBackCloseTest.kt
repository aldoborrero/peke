package dev.aldoborrero.peke.ui.nav

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
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
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import dev.aldoborrero.peke.ui.tryRequestFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back through [DrawerBackHandler], wired as [NavDrawer] wires it: with the sidebar open it closes it and gives focus back
 * to where it was on the page, on any page; with it closed the next Back goes back a page, or on the home asks "Leave
 * Peke?" (spec 024), whose "Leave" hands the Back on to the system.
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
        DrawerBackHandler(drawerState, destination, enabled = true, onClose = { pageFocus.tryRequestFocus() })
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

    private fun leaveDialogShown(): Boolean = rule.onAllNodesWithText(LEAVE_TITLE).fetchSemanticsNodes().isNotEmpty()

    private fun showLeaveDialog() {
        rule.setContent { Page(Destination.Home()) }
        rule.onNodeWithTag("second").assertIsFocused()
        back()
        assertTrue(leaveDialogShown())
        rule.onNodeWithText(KEEP_WATCHING).assertIsFocused()
        assertEquals(DrawerValue.Closed, drawerState.currentValue)
        assertEquals(0, passedBack)
    }

    @Test
    fun `on the home Back with the sidebar open closes it and focus goes back where it was`() {
        rule.setContent { Page(Destination.Home()) }
        rule.onNodeWithTag("second").assertIsFocused()
        press(Key.DirectionLeft)
        assertEquals(DrawerValue.Open, drawerState.currentValue)

        back()
        assertEquals(DrawerValue.Closed, drawerState.currentValue)
        rule.onNodeWithTag("second").assertIsFocused()
        assertFalse(leaveDialogShown())
        assertEquals(0, passedBack)
    }

    @Test
    fun `on the home Back with the sidebar closed asks before leaving and never opens the sidebar`() {
        showLeaveDialog()
    }

    @Test
    fun `Keep watching dismisses the question and stays`() {
        showLeaveDialog()
        rule.onNodeWithText(KEEP_WATCHING).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertFalse(leaveDialogShown())
        assertEquals(0, passedBack)
        rule.onNodeWithTag("second").assertIsFocused()
    }

    @Test
    fun `Back dismisses the question and stays`() {
        showLeaveDialog()
        // Back in the dialog's own window, as the remote's Back reaches it
        val dialogView = (rule.onNodeWithText(LEAVE_TITLE).fetchSemanticsNode().root as ViewRootForTest).view
        rule.runOnUiThread { dialogView.findViewTreeOnBackPressedDispatcherOwner()!!.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertFalse(leaveDialogShown())
        assertEquals(0, passedBack)
    }

    @Test
    fun `Leave hands Back on to the system, which quits`() {
        showLeaveDialog()
        rule.onNodeWithText(LEAVE).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertFalse(leaveDialogShown())
        assertEquals(1, passedBack)

        // Back on the home asks again afterwards, as after coming back to the app
        back()
        assertTrue(leaveDialogShown())
        assertEquals(1, passedBack)
    }

    private companion object {
        const val LEAVE_TITLE = "Leave Peke?"
        const val KEEP_WATCHING = "Keep watching"
        const val LEAVE = "Leave"
    }
}
