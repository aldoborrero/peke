package dev.aldoborrero.peke.ui.nav

import androidx.tv.material3.DrawerValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavDrawerBackTest {
    @Test
    fun `the first Back on the home page opens the nav drawer`() {
        assertTrue(backOpensNavDrawer(DrawerValue.Closed, Destination.Home()))
        assertFalse(backClosesNavDrawer(DrawerValue.Closed, Destination.Home()))
    }

    @Test
    fun `a second Back with the drawer open on the home is left to the system, which quits, and never opens the gate`() {
        // Nothing in the nav drawer handles it, so leaving stays ungated and the parent gate only opens from its entry
        assertFalse(backOpensNavDrawer(DrawerValue.Open, Destination.Home()))
        assertFalse(backClosesNavDrawer(DrawerValue.Open, Destination.Home()))
    }

    @Test
    fun `Back with the drawer open on other pages closes it`() {
        assertTrue(backClosesNavDrawer(DrawerValue.Open, Destination.Search()))
        assertTrue(backClosesNavDrawer(DrawerValue.Open, Destination.License))
        assertFalse(backOpensNavDrawer(DrawerValue.Open, Destination.Search()))
    }

    @Test
    fun `Back with the drawer closed on other pages goes back step by step`() {
        assertFalse(backOpensNavDrawer(DrawerValue.Closed, Destination.Search()))
        assertFalse(backClosesNavDrawer(DrawerValue.Closed, Destination.Search()))
        assertFalse(backOpensNavDrawer(DrawerValue.Closed, Destination.License))
        assertFalse(backClosesNavDrawer(DrawerValue.Closed, Destination.License))
    }
}
