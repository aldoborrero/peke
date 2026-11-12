package dev.aldoborrero.peke.kids

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.ui.ParentLockHint
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The parent gate says in words which keys make the code, in English and Spanish, not Wholphin's "[0–9]", which read
 * as a placeholder under "Enter the parent code"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ParentLockHintTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(type: ParentLockType) {
        rule.setContent { PekeTheme { ParentLockHint(type) } }
        rule.waitForIdle()
    }

    @Test
    fun `a number code asks for the number keys`() {
        show(ParentLockType.NUMERIC)
        rule.onNodeWithText("Use the number keys").assertExists()
        rule.onNodeWithText("[", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a remote code shows only the arrows`() {
        show(ParentLockType.REMOTE)
        rule.onNodeWithText("or the number keys").assertDoesNotExist()
        rule.onNodeWithText("[", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "es-w960dp-h540dp-xhdpi")
    fun `in Spanish too`() {
        show(ParentLockType.NUMERIC)
        rule.onNodeWithText("Usa las teclas de números").assertExists()
        rule.onNodeWithText("[", substring = true).assertDoesNotExist()
    }
}
