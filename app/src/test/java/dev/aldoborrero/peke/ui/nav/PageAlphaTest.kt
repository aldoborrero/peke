package dev.aldoborrero.peke.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page fades to 35% behind the open sidebar and back, without recomposing the page as the drawer opens and closes
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageAlphaTest {
    @get:Rule
    val rule = createComposeRule()

    private var compositions = 0
    private lateinit var alpha: State<Float>

    private fun setContent(drawerState: DrawerState) {
        rule.setContent {
            compositions++
            alpha = rememberPageAlpha(drawerState)
            Box(Modifier.fillMaxSize().dimmedTo(Color.Black) { alpha.value })
        }
        rule.waitForIdle()
    }

    @Test
    fun `opening and closing the drawer fades the page without recomposing it`() {
        val drawerState = DrawerState(DrawerValue.Closed)
        setContent(drawerState)
        assertEquals(1f, alpha.value)

        rule.runOnIdle { drawerState.setValue(DrawerValue.Open) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertEquals(.35f, alpha.value, .01f)

        rule.runOnIdle { drawerState.setValue(DrawerValue.Closed) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertEquals(1f, alpha.value, .01f)

        assertEquals("compositions of the page", 1, compositions)
    }

    @Test
    fun `a page made with the drawer open starts faded`() {
        setContent(DrawerState(DrawerValue.Open))
        assertEquals(.35f, alpha.value)
    }
}
