package dev.aldoborrero.peke.ui.nav

import androidx.tv.material3.DrawerValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavDrawerBackTest {
    @Test
    fun `Back on the home with the drawer closed asks before leaving and never opens the drawer or the gate`() {
        assertTrue(backAsksToLeave(DrawerValue.Closed, Destination.Home()))
        assertFalse(backClosesNavDrawer(DrawerValue.Closed))
    }

    @Test
    fun `Back on the home with the drawer open closes it`() {
        assertTrue(backClosesNavDrawer(DrawerValue.Open))
        assertFalse(backAsksToLeave(DrawerValue.Open, Destination.Home()))
    }

    @Test
    fun `Back with the drawer open on other pages closes it`() {
        assertTrue(backClosesNavDrawer(DrawerValue.Open))
        assertFalse(backAsksToLeave(DrawerValue.Open, Destination.Search()))
        assertFalse(backAsksToLeave(DrawerValue.Open, Destination.License))
    }

    @Test
    fun `Back with the drawer closed on other pages goes back step by step`() {
        assertFalse(backAsksToLeave(DrawerValue.Closed, Destination.Search()))
        assertFalse(backAsksToLeave(DrawerValue.Closed, Destination.License))
        assertFalse(backClosesNavDrawer(DrawerValue.Closed))
    }
}
