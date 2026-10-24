package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.ui.nav.Destination
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
}
