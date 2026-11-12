package dev.aldoborrero.peke.kids

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.aldoborrero.peke.kids.ui.GrownUpButton
import dev.aldoborrero.peke.kids.ui.GrownUpSurface
import dev.aldoborrero.peke.preferences.PekeSettingsSection
import dev.aldoborrero.peke.ui.preferences.PekeSettingsFrame
import dev.aldoborrero.peke.ui.preferences.PekeSettingsTags
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings as board 12 (spec 021 US4): DESIGN's sections on the left, the focused one's rows on the right, Right and
 * OK into the rows, Back up one level; board 11's header without a kid
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PekeSettingsPageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var leftSettings = false

    private fun show(setUp: Pair<String, Boolean>? = null) {
        rule.setContent {
            // What the page sits in, registered first as in the app: Back from the sections leaves it
            BackHandler { leftSettings = true }
            PekeTheme {
                GrownUpSurface {
                    PekeSettingsFrame(
                        sections = PekeSettingsSection.entries.map { it.title },
                        setUpPlugin = setUp,
                        notice = null,
                        onNoticeShown = {},
                    ) { index ->
                        Column {
                            repeat(2) { GrownUpButton("Row $index.$it", onClick = {}, modifier = Modifier.testTag("row-$index-$it")) }
                        }
                    }
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

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun section(index: Int) = PekeSettingsTags.SECTION + index

    @Test
    fun `the sections are DESIGN's, Household focused, its rows shown`() {
        show()
        listOf("Settings", "Household", "This TV", "Playback", "Advanced", "About", "Reset this TV").forEach {
            rule.onNodeWithText(it).assertExists()
        }
        rule.onNodeWithTag(section(0)).assertIsFocused()
        rule.onNodeWithText("Row 0.0").assertExists()
        rule.onNodeWithTag(PekeSettingsTags.SET_UP).assertDoesNotExist()
    }

    @Test
    fun `moving through the sections shows each one's rows`() {
        show()
        press(section(0), listOf(Key.DirectionDown, Key.DirectionDown))
        rule.onNodeWithTag(section(2)).assertIsFocused()
        rule.onNodeWithText("Row 2.0").assertExists()
        rule.onNodeWithText("Row 0.0").assertDoesNotExist()
    }

    @Test
    fun `Right enters the rows, Back returns to the section, Back again leaves`() {
        show()
        press(section(0), listOf(Key.DirectionDown))
        press(section(1), listOf(Key.DirectionRight))
        rule.onNodeWithTag("row-1-0").assertIsFocused()

        back()
        rule.onNodeWithTag(section(1)).assertIsFocused()
        assertFalse(leftSettings)

        back()
        assertTrue(leftSettings)
    }

    @Test
    fun `OK on a section enters its rows`() {
        show()
        press(section(0), listOf(Key.DirectionCenter))
        rule.onNodeWithTag("row-0-0").assertIsFocused()
    }

    @Test
    fun `without a kid, board 11's header is above the columns`() {
        show(setUp = "Kids plugin ready" to false)
        rule.onNodeWithTag(PekeSettingsTags.SET_UP).assertExists()
        rule.onNodeWithText("Set up this TV").assertExists()
        rule.onNodeWithText("Set up this TV for the kids. Grown-ups watch with another Jellyfin app.").assertExists()
        rule.onNodeWithText("Kids plugin ready").assertExists()
        rule.onNodeWithTag(section(0)).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "es-w960dp-h540dp-xhdpi")
    fun `in Spanish too`() {
        show()
        listOf("Ajustes", "Casa", "Esta tele", "Reproducción", "Avanzado", "Acerca de", "Restablecer esta tele").forEach {
            rule.onNodeWithText(it).assertExists()
        }
    }
}
