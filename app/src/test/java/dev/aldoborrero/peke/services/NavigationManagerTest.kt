package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.acra.ACRA
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class NavigationManagerTest {
    private val loading = Destination.KidsResume(Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE))
    private val player = Destination.Playback(UUID.randomUUID(), 0)

    @Before
    fun setUp() {
        mockkStatic(ACRA::class)
        every { ACRA.errorReporter } returns io.mockk.mockk(relaxed = true)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `replaces its own page when it is on top`() {
        val manager = NavigationManager()
        manager.navigateTo(loading)
        assertTrue(manager.replaceTop(loading, player))
        assertEquals(listOf(Destination.Home(), player), manager.backStack.toList())
    }

    @Test
    fun `with nothing to show it only goes back`() {
        val manager = NavigationManager()
        manager.navigateTo(loading)
        assertTrue(manager.replaceTop(loading, null))
        assertEquals(listOf<Destination>(Destination.Home()), manager.backStack.toList())
    }

    @Test
    fun `changes nothing after the kid went back`() {
        val manager = NavigationManager()
        manager.navigateTo(loading)
        manager.goBack()
        assertFalse(manager.replaceTop(loading, player))
        assertEquals(listOf<Destination>(Destination.Home()), manager.backStack.toList())
    }

    @Test
    fun `changes nothing when time's up came on top`() {
        val manager = NavigationManager()
        manager.navigateTo(loading)
        manager.navigateTo(Destination.KidsTimesUp)
        assertFalse(manager.replaceTop(loading, player))
        assertEquals(listOf(Destination.Home(), loading, Destination.KidsTimesUp), manager.backStack.toList())
    }

    @Test
    fun `pages shown apart leave the kid's pages as they were`() {
        val manager = NavigationManager()
        manager.navigateTo(loading)
        val kid = listOf(Destination.Home(), loading)
        val settings = Destination.Settings(PreferenceScreenOption.PEKE)

        val apart = manager.openApart(settings)
        apart.add(Destination.License)

        assertTrue(manager.isApart)
        assertEquals(listOf(settings, Destination.License), manager.backStack.toList())
        // What the activity saves is still the kid's
        assertEquals(kid, manager.kidBackStack.toList())

        manager.closeApart()
        assertFalse(manager.isApart)
        assertEquals(kid, manager.backStack.toList())
        // Closing twice changes nothing
        manager.closeApart()
        assertEquals(kid, manager.backStack.toList())
    }

    @Test
    fun `back from a settings sub-screen returns to Settings, in a kid's pages and apart`() {
        val settings = Destination.Settings(PreferenceScreenOption.PEKE)
        listOf(PreferenceScreenOption.PEKE_PLAYBACK, PreferenceScreenOption.PEKE_ADVANCED).forEach { screen ->
            val sub = Destination.Settings(screen)

            val manager = NavigationManager()
            manager.navigateTo(settings)
            manager.navigateTo(sub)
            manager.goBack()
            assertEquals(listOf(Destination.Home(), settings), manager.backStack.toList())

            // From the kids picker, the sub-screen opens among the pages shown apart, and the kid's pages stay
            manager.goBack()
            manager.openApart(settings)
            manager.navigateTo(sub)
            assertEquals(listOf(settings, sub), manager.backStack.toList())
            assertEquals(listOf<Destination>(Destination.Home()), manager.kidBackStack.toList())
            manager.goBack()
            assertEquals(listOf<Destination>(settings), manager.backStack.toList())
            manager.closeApart()
        }
    }
}
