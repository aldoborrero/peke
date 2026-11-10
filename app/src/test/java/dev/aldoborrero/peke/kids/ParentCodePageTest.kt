package dev.aldoborrero.peke.kids

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.ui.GateRefusal
import dev.aldoborrero.peke.kids.ui.ParentCodePage
import dev.aldoborrero.peke.kids.ui.ParentCodePageTags
import dev.aldoborrero.peke.kids.ui.gateBackLabel
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The parent gate's page (spec 019): board 7's parts, the refusals on the page instead of toasts, and Back, which
 * leaves with either code type while the Back pill only names the key
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ParentCodePageTest {
    @get:Rule
    val rule = createComposeRule()

    private val confirmed = mutableListOf<String>()
    private var dismissed = 0
    private var refusal by mutableStateOf<GateRefusal?>(null)
    private var attempt by mutableStateOf(0)

    private fun show(
        type: ParentLockType,
        kid: String? = "Lucy",
    ) {
        rule.setContent {
            PekeTheme {
                ParentCodePage(
                    title = R.string.kids_gate_title,
                    type = type,
                    attempt = attempt,
                    refusal = refusal,
                    backLabel = gateBackLabel(kid),
                    forgotHint = true,
                    onConfirm = { confirmed += it },
                    onDismissRequest = { dismissed++ },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun press(keys: List<Key>) {
        rule.onNodeWithTag(ParentCodePageTags.ENTRY).performKeyInput { keys.forEach { pressKey(it) } }
        rule.waitForIdle()
    }

    private fun dots(tag: String) = rule.onAllNodesWithTag(tag)

    @Test
    fun `the arrows type shows the cross, the hint, empty dots and Back to the kid`() {
        show(ParentLockType.REMOTE)
        rule.onNodeWithText("Grown-ups only").assertExists()
        rule.onNodeWithText("Enter the parent code with the remote's arrows or number buttons.").assertExists()
        rule.onNodeWithText("Forgot it? Press OK with no code").assertExists()
        rule.onNodeWithText("Back to Lucy").assertExists()
        rule.onNodeWithTag(ParentCodePageTags.CROSS).assertExists()
        rule.onNodeWithTag(ParentCodePageTags.PAD).assertDoesNotExist()
        dots(ParentCodePageTags.DOT_EMPTY).assertCountEquals(4)
    }

    @Test
    fun `the number type shows the pad and its own hint`() {
        show(ParentLockType.NUMERIC)
        rule.onNodeWithText("Enter the parent code with the remote's number buttons.").assertExists()
        rule.onNodeWithTag(ParentCodePageTags.PAD).assertExists()
        rule.onNodeWithTag(ParentCodePageTags.CROSS).assertDoesNotExist()
    }

    @Test
    fun `typed keys fill the dots, arrows included with the arrows type, and OK sends them`() {
        show(ParentLockType.REMOTE)
        press(listOf(Key.DirectionUp, Key.Two))
        dots(ParentCodePageTags.DOT_FILLED).assertCountEquals(2)
        dots(ParentCodePageTags.DOT_EMPTY).assertCountEquals(2)
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf("U2"), confirmed)
    }

    @Test
    fun `OK with nothing typed is sent empty, for the recovery`() {
        show(ParentLockType.NUMERIC)
        press(listOf(Key.Enter))
        assertEquals(listOf(""), confirmed)
    }

    @Test
    fun `a wrong code clears the entry, says so on the page and goes at the next key`() {
        show(ParentLockType.NUMERIC)
        press(listOf(Key.One, Key.Two, Key.Three, Key.Four))
        refusal = GateRefusal.Wrong
        attempt = 1
        rule.waitForIdle()
        rule.onNodeWithText("Wrong code").assertExists()
        dots(ParentCodePageTags.DOT_WRONG).assertCountEquals(4)
        dots(ParentCodePageTags.DOT_FILLED).assertCountEquals(0)

        press(listOf(Key.Five))

        rule.onNodeWithText("Wrong code").assertDoesNotExist()
        dots(ParentCodePageTags.DOT_FILLED).assertCountEquals(1)
        dots(ParentCodePageTags.DOT_EMPTY).assertCountEquals(3)
    }

    @Test
    fun `too many tries and an unreachable server are pills on the page`() {
        refusal = GateRefusal.Wait(30)
        show(ParentLockType.NUMERIC)
        rule.onNodeWithText("Too many tries. Wait 30 seconds").assertExists()
        refusal = GateRefusal.CannotCheck
        rule.waitForIdle()
        rule.onNodeWithText("Can't reach the server to check the parent code. Try again in a moment.").assertExists()
    }

    private fun backLeaves(type: ParentLockType) {
        show(type)
        rule.onNodeWithTag(ParentCodePageTags.BACK).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        press(listOf(Key.Two, Key.DirectionLeft))

        press(listOf(Key.Back))

        assertEquals(1, dismissed)
        assertEquals(emptyList<String>(), confirmed)
    }

    @Test
    fun `Back leaves with keys typed, and the pill takes no focus, with the arrows type`() = backLeaves(ParentLockType.REMOTE)

    @Test
    fun `Back leaves with keys typed with the number type`() = backLeaves(ParentLockType.NUMERIC)

    @Test
    fun `without a kid the pill just says Back`() {
        show(ParentLockType.REMOTE, kid = null)
        rule.onNodeWithText("Back").assertExists()
    }

    @Test
    @Config(qualifiers = "es-w960dp-h540dp-xhdpi")
    fun `in Spanish too`() {
        refusal = GateRefusal.Wrong
        show(ParentLockType.NUMERIC)
        rule.onNodeWithText("Solo para adultos").assertExists()
        rule.onNodeWithText("Escribe el código de padres con las teclas de números del mando.").assertExists()
        rule.onNodeWithText("Volver con Lucy").assertExists()
        rule.onNodeWithText("Código incorrecto").assertExists()
    }
}
