package dev.aldoborrero.peke.ui.nav

import androidx.tv.material3.DrawerValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Back's rules on the pages with the sidebar, as in Wholphin (spec 035) */
class NavDrawerBackTest {
    @Test
    fun `Back on the home with the drawer closed opens it`() {
        assertTrue(backOpensNavDrawer(DrawerValue.Closed, Destination.Home()))
        assertFalse(backClosesNavDrawerOnTheWayBack(DrawerValue.Closed, Destination.Home()))
    }

    @Test
    fun `Back on the home with the drawer open is left to the system, which quits`() {
        assertFalse(backOpensNavDrawer(DrawerValue.Open, Destination.Home()))
        assertFalse(backClosesNavDrawerOnTheWayBack(DrawerValue.Open, Destination.Home()))
    }

    @Test
    fun `Back with the drawer closed on other pages is left to the page, which goes back`() {
        assertFalse(backOpensNavDrawer(DrawerValue.Closed, Destination.Search()))
        assertFalse(backOpensNavDrawer(DrawerValue.Closed, Destination.License))
        assertFalse(backClosesNavDrawerOnTheWayBack(DrawerValue.Closed, Destination.Search()))
        assertFalse(backClosesNavDrawerOnTheWayBack(DrawerValue.Closed, Destination.License))
    }

    @Test
    fun `Back with the drawer open on other pages closes it on the way back`() {
        assertTrue(backClosesNavDrawerOnTheWayBack(DrawerValue.Open, Destination.Search()))
        assertTrue(backClosesNavDrawerOnTheWayBack(DrawerValue.Open, Destination.License))
        assertFalse(backOpensNavDrawer(DrawerValue.Open, Destination.Search()))
    }
}
