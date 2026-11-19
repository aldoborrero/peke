package dev.aldoborrero.peke.kids.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.ui.Cards
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.math.sqrt

/**
 * Setup's kid picker (spec 026): Down from a kid lands on Continue when a kid is picked, else on Check again; the check
 * mark sits on the avatar's edge, outside the round surface that clipped it, and grows with the focused avatar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class KidsChoiceTest {
    @get:Rule
    val rule = createComposeRule()

    private val kid = SetupKid(id = UUID.randomUUID(), name = "Manuel", imageUrl = null)

    private val focus = FocusRequester()

    @Composable
    private fun Focused(content: @Composable (FocusRequester) -> Unit) {
        val inputMode = LocalInputModeManager.current
        PekeTheme {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(top = 32.dp),
            ) {
                content(focus)
            }
        }
        LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
    }

    private fun picker(selected: Set<UUID>) {
        rule.setContent {
            Focused { focus ->
                KidsChoice(
                    kids = listOf(kid),
                    selected = selected,
                    loading = false,
                    onToggle = {},
                    onContinue = {},
                    onCheckAgain = {},
                    rowFocus = focus,
                )
            }
        }
        rule.waitForIdle()
        // As setup does once the kids show
        rule.runOnIdle { focus.requestFocus() }
        rule.waitForIdle()
    }

    @Test
    fun `Down from a picked kid lands on Continue, not on Check again nearer the kid`() {
        picker(selected = setOf(kid.id))
        rule.onNodeWithTag(SETUP_CONTINUE_TAG).assertIsNotFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithTag(SETUP_CONTINUE_TAG).assertIsFocused()
        rule.onNodeWithTag(SETUP_CHECK_AGAIN_TAG).assertIsNotFocused()
    }

    @Test
    fun `with no kid picked Continue is off and Down lands on Check again`() {
        picker(selected = emptySet())
        rule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("$SETUP_KID_TAG${kid.id}"))).assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithTag(SETUP_CHECK_AGAIN_TAG).assertIsFocused()
    }

    private fun card(
        selected: Boolean,
        badge: ImageVector? = null,
        focused: Boolean = false,
    ) {
        rule.setContent {
            Focused { focus ->
                KidAvatarCard(
                    id = kid.id,
                    name = kid.name,
                    imageUrl = null,
                    selected = selected,
                    badge = badge,
                    onClick = {},
                    modifier = if (focused) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
        rule.waitForIdle()
        if (focused) rule.runOnIdle { focus.requestFocus() }
        rule.waitForIdle()
    }

    private fun assertOnEdge(scale: Float) {
        // Outside the clickable round surface, which clips its content and draws its border over it
        rule.onNode(hasTestTag(KID_BADGE_TAG) and hasAnyAncestor(hasClickAction())).assertDoesNotExist()
        val avatar = rule.onNode(hasClickAction()).getUnclippedBoundsInRoot()
        val badge = rule.onNodeWithTag(KID_BADGE_TAG).getUnclippedBoundsInRoot()
        val centreX = (avatar.left + avatar.right) / 2
        val centreY = (avatar.top + avatar.bottom) / 2
        // Its centre on the edge, at 45° towards the bottom end; the test reads a scaled node as its scaled corner and
        // unscaled size, so the corner is checked, scaled about the avatar's centre
        val corner = (Cards.serverUserCircle / 2 * cos45 - KidBadgeSize / 2) * scale
        assertDp(corner, badge.left - centreX)
        assertDp(corner, badge.top - centreY)
        assertDp(KidBadgeSize, badge.right - badge.left)
    }

    private val cos45 = sqrt(2f) / 2

    private fun assertDp(
        expected: Dp,
        actual: Dp,
    ) = assertEquals(expected.value, actual.value, 1f)

    @Test
    fun `the check mark of a picked kid sits on the avatar's edge, outside its clip`() {
        card(selected = true)
        assertOnEdge(scale = 1f)
    }

    @Test
    fun `the lock badge sits in the same place`() {
        card(selected = false, badge = Icons.Default.Lock)
        assertOnEdge(scale = 1f)
    }

    @Test
    fun `no badge when the kid isn't picked`() {
        card(selected = false)
        rule.onNodeWithTag(KID_BADGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `the check mark grows with the focused avatar and stays on its edge`() {
        card(selected = true, focused = true)
        rule.onNode(hasClickAction()).assertIsFocused()
        assertOnEdge(scale = PekeFocus.KID_SCALE)
    }
}
