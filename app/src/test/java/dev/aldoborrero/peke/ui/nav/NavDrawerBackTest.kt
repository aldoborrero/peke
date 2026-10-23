package dev.aldoborrero.peke.ui.nav

import androidx.tv.material3.DrawerValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavDrawerBackTest {
    @Test
    fun `the first Back on the home page opens the nav drawer`() {
        assertTrue(backOpensNavDrawer(DrawerValue.Closed, Destination.Home()))
    }

    @Test
    fun `a second Back with the drawer open is left to the system, which quits, and never opens the gate`() {
        // Nothing in the nav drawer handles it any more, so the parent gate only opens from its drawer entry
        assertFalse(backOpensNavDrawer(DrawerValue.Open, Destination.Home()))
    }

    @Test
    fun `Back on other pages goes back step by step`() {
        assertFalse(backOpensNavDrawer(DrawerValue.Closed, Destination.Search()))
        assertFalse(backOpensNavDrawer(DrawerValue.Open, Destination.Search()))
        assertFalse(backOpensNavDrawer(DrawerValue.Closed, Destination.License))
    }
}
