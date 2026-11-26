package dev.aldoborrero.peke.kids

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.aldoborrero.peke.kids.ui.BackToMenu
import dev.aldoborrero.peke.kids.ui.GrownUpMenuItem
import dev.aldoborrero.peke.kids.ui.GrownUpMenuReturn
import dev.aldoborrero.peke.kids.ui.GrownUpWindow
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.nav.KID_PAGES_FADE_MILLIS
import dev.aldoborrero.peke.ui.nav.KidPages
import dev.aldoborrero.peke.ui.nav.KidPagesRoute
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import dev.aldoborrero.peke.ui.theme.PekeTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.acra.ACRA
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back on the menu shown again over the rules or Settings leaves to the kid's page without the page showing alone in
 * between: the menu covers until the page has gone (spec 027)
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class MenuBackFlashTest {
    @get:Rule
    val rule = createComposeRule()

    private val navigation = NavigationManager()
    private val menuReturn = GrownUpMenuReturn(navigation)
    private val settings = Destination.Settings(PreferenceScreenOption.PEKE)
    private lateinit var dispatcher: OnBackPressedDispatcher
    private var menuLeft = 0
    private var reopened = 0

    @Before
    fun setUp() {
        mockkStatic(ACRA::class)
        every { ACRA.errorReporter } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun show(page: Destination) {
        menuReturn.open(page)
        rule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            PekeTheme {
                KidPages(
                    backStack = navigation.backStack,
                    onBack = navigation::goBack,
                    route = KidPagesRoute.SHOW,
                    contentKey = { it.toString() },
                    onLaunchRule = {},
                ) { destination ->
                    if (destination is Destination.Home) {
                        Text("kid page")
                    } else {
                        // As GrownUpGuard wraps the page
                        BackToMenu(
                            item = menuReturn.itemFor(destination),
                            onMenuClosed = { menuReturn.close(destination) },
                            menu = { _, onDismissRequest -> Menu(page, onDismissRequest) },
                        ) { Text("the page", Modifier.testTag(PAGE_TEXT)) }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun Menu(
        page: Destination,
        onDismissRequest: () -> Unit,
    ) {
        val focus = remember { FocusRequester() }
        // Back on the menu ends the grant and closes it, as KidsParentGate's leaveMenu
        GrownUpWindow(onBack = {
            menuLeft++
            onDismissRequest()
        }) {
            Column {
                Text("menu", Modifier.testTag(MENU))
                // Choosing the same page again, as KidsParentGate's leaveFor
                Button(
                    onClick = {
                        reopened++
                        onDismissRequest()
                        menuReturn.open(page)
                    },
                    modifier = Modifier.testTag(REOPEN).focusRequester(focus),
                ) { Text("again") }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }

    private fun backToMenu() {
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(MENU).assertExists()
    }

    private fun backOnMenu() {
        rule.onNodeWithTag(REOPEN).performKeyInput { pressKey(Key.Back) }
    }

    private fun menuShown() = rule.onAllNodesWithTag(MENU).fetchSemanticsNodes().isNotEmpty()

    private fun pageShown() = rule.onAllNodesWithTag(PAGE_TEXT, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Back on the menu, then frame by frame until the page has gone: the page is never there without the menu
     */
    private fun assertLeavesWithoutFlash() {
        rule.mainClock.autoAdvance = false
        backOnMenu()

        assertEquals(listOf<Destination>(Destination.Home()), navigation.backStack.toList())
        assertEquals(1, menuLeft)
        var frames = 0
        while (pageShown()) {
            assertTrue("the page showed without the menu ${frames * 16} ms after Back", menuShown())
            rule.mainClock.advanceTimeByFrame()
            frames++
            assertTrue("the page never left", frames < 100)
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag(MENU).assertDoesNotExist()
        rule.onNodeWithText("kid page").assertExists()
    }

    @Test
    fun `Back on the menu over the rules leaves to the kid's page without the rules showing alone`() {
        show(Destination.KidRules)
        backToMenu()

        assertLeavesWithoutFlash()
    }

    @Test
    fun `Back on the menu over the stats leaves to the kid's page without the stats showing alone`() {
        show(Destination.KidStats)
        backToMenu()

        assertLeavesWithoutFlash()
    }

    @Test
    fun `Back on the menu over Settings leaves to the kid's page without Settings showing alone`() {
        show(settings)
        backToMenu()

        assertLeavesWithoutFlash()
    }

    @Test
    fun `the menu ignores keys while it covers the page leaving`() {
        show(Destination.KidRules)
        backToMenu()
        rule.mainClock.autoAdvance = false
        backOnMenu()
        rule.mainClock.advanceTimeByFrame()
        assertTrue(menuShown())

        rule.onNodeWithTag(REOPEN).performKeyInput { pressKey(Key.DirectionCenter) }
        backOnMenu()

        assertEquals(0, reopened)
        assertEquals(1, menuLeft)
        rule.mainClock.advanceTimeBy(KID_PAGES_FADE_MILLIS * 4L)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(listOf<Destination>(Destination.Home()), navigation.backStack.toList())
    }

    @Test
    fun `choosing the same page again closes the menu over it, and Back shows the menu again`() {
        show(Destination.KidRules)
        backToMenu()

        rule.onNodeWithTag(REOPEN).assertIsFocused()
        rule.onNodeWithTag(REOPEN).performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        assertEquals(1, reopened)
        rule.onNodeWithTag(MENU).assertDoesNotExist()
        rule.onNodeWithText("the page").assertExists()
        assertEquals(listOf(Destination.Home(), Destination.KidRules), navigation.backStack.toList())
        backToMenu()
    }

    private companion object {
        const val MENU = "menu"
        const val REOPEN = "reopen"
        const val PAGE_TEXT = "page text"
    }
}
