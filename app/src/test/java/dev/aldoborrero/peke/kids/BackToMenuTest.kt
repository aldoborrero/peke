package dev.aldoborrero.peke.kids

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
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
import androidx.tv.material3.Text
import dev.aldoborrero.peke.kids.ui.BackToMenu
import dev.aldoborrero.peke.kids.ui.GrownUpMenuItem
import dev.aldoborrero.peke.kids.ui.GrownUpMenuPage
import dev.aldoborrero.peke.kids.ui.GrownUpMenuTags
import dev.aldoborrero.peke.kids.ui.GrownUpWindow
import dev.aldoborrero.peke.kids.ui.MenuAddTime
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back from the rules or Settings opened from the parent menu shows the menu again, focused on that item; Back there
 * leaves to the kid; Back inside the page goes up a level first; a page the menu didn't open keeps its Back (spec 025)
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class BackToMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var dispatcher: OnBackPressedDispatcher
    private var pageLeft = 0
    private var menuClosed = 0
    private var innerLevel by mutableStateOf(false)
    private var innerBacks = 0

    private fun show(item: GrownUpMenuItem?) {
        rule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            PekeTheme {
                // The back stack's own Back, under the page
                BackHandler { pageLeft++ }
                BackToMenu(
                    item = item,
                    onMenuClosed = { menuClosed++ },
                    menu = { focus, onDismissRequest ->
                        GrownUpWindow(onBack = onDismissRequest) {
                            GrownUpMenuPage(
                                kidName = "Lucy",
                                todayLeft = null,
                                sittingLeft = null,
                                pluginGone = null,
                                addTime = MenuAddTime.Available,
                                canSwitchKid = true,
                                notice = null,
                                onNoticeShown = {},
                                onAddTime = {},
                                onKeepWatching = {},
                                onSwitchKid = {},
                                onRules = {},
                                onSettings = {},
                                focus = focus,
                            )
                        }
                    },
                ) {
                    // A Settings section's rows: Back returns to the sections first
                    BackHandler(enabled = innerLevel) {
                        innerBacks++
                        innerLevel = false
                    }
                    Text("the page")
                }
            }
        }
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun `Back from the rules shows the menu focused on the rules, and Back there leaves`() {
        show(GrownUpMenuItem.RULES)

        back()

        rule.onNodeWithTag(GrownUpMenuTags.RULES).assertIsFocused()
        assertEquals(0, pageLeft)
        rule.onNodeWithTag(GrownUpMenuTags.RULES).performKeyInput { pressKey(Key.Back) }
        rule.waitForIdle()
        assertEquals(1, menuClosed)
        rule.onNodeWithTag(GrownUpMenuTags.RULES).assertDoesNotExist()
    }

    @Test
    fun `Back from Settings shows the menu focused on Settings`() {
        show(GrownUpMenuItem.SETTINGS)

        back()

        rule.onNodeWithTag(GrownUpMenuTags.SETTINGS).assertIsFocused()
        assertEquals(0, pageLeft)
    }

    @Test
    fun `Back inside the page goes up a level before the menu`() {
        innerLevel = true
        show(GrownUpMenuItem.SETTINGS)

        back()
        assertEquals(1, innerBacks)
        rule.onNodeWithTag(GrownUpMenuTags.SETTINGS).assertDoesNotExist()

        back()
        rule.onNodeWithTag(GrownUpMenuTags.SETTINGS).assertIsFocused()
    }

    @Test
    fun `a page the menu didn't open keeps its Back`() {
        show(item = null)

        back()

        assertEquals(1, pageLeft)
        rule.onNodeWithText("the page").assertExists()
        rule.onNodeWithTag(GrownUpMenuTags.RULES).assertDoesNotExist()
    }
}
