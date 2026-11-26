package dev.aldoborrero.peke.kids

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.aldoborrero.peke.kids.ui.BackToMenu
import dev.aldoborrero.peke.kids.ui.GrownUpMenuItem
import dev.aldoborrero.peke.kids.ui.GrownUpMenuPage
import dev.aldoborrero.peke.kids.ui.GrownUpMenuTags
import dev.aldoborrero.peke.kids.ui.GrownUpWindow
import dev.aldoborrero.peke.kids.ui.MenuAddTime
import dev.aldoborrero.peke.kids.ui.stats.KidStatsContent
import dev.aldoborrero.peke.kids.ui.stats.KidStatsState
import dev.aldoborrero.peke.kids.ui.stats.KidStatsTags
import dev.aldoborrero.peke.kids.ui.stats.StatsKid
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Stats with no server has no focus stop, only the "can't reach the server" card: the remote's Back, sent to the
 * activity as a TV sends it, still shows the menu focused on Stats (spec 025, 033)
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class StatsOfflineBackTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var menuClosed = 0

    private val offline =
        KidStatsState(
            loading = false,
            statsSupported = true,
            offline = true,
            kids = listOf(StatsKid(UUID(0, 1), "Manuel", watching = true, stale = true)),
        )

    @Test
    fun `Back on Stats with no server shows the menu focused on Stats, and Back there leaves`() {
        rule.setContent {
            PekeTheme {
                BackToMenu(
                    item = GrownUpMenuItem.STATS,
                    onMenuClosed = {
                        menuClosed++
                        false
                    },
                    menu = { focus, onDismissRequest ->
                        GrownUpWindow(onBack = onDismissRequest) {
                            GrownUpMenuPage(
                                kidName = "Manuel",
                                todayLeft = null,
                                sittingLeft = null,
                                pluginGone = null,
                                addTime = MenuAddTime.Available,
                                canSwitchKid = false,
                                notice = null,
                                onNoticeShown = {},
                                onAddTime = {},
                                onKeepWatching = {},
                                onSwitchKid = {},
                                onRules = {},
                                onSettings = {},
                                onStats = {},
                                focus = focus,
                            )
                        }
                    },
                ) { KidStatsContent(offline) }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(KidStatsTags.OFFLINE).assertExists()
        rule.onNodeWithTag(KidStatsTags.kid(0)).assertDoesNotExist()
        assertEquals(0, rule.onAllNodes(isFocused()).fetchSemanticsNodes().size)

        rule.runOnUiThread {
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        rule.waitForIdle()

        rule.onNodeWithTag(GrownUpMenuTags.STATS).assertIsFocused()
        rule.onNodeWithTag(GrownUpMenuTags.STATS).performKeyInput { pressKey(Key.Back) }
        rule.waitForIdle()
        assertEquals(1, menuClosed)
    }
}
