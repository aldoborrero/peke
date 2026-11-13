package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.ui.GrownUpMenuItem
import dev.aldoborrero.peke.kids.ui.GrownUpMenuReturn
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.acra.ACRA
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Back from a page the parent menu opened returns to the menu (spec 025): only that page, only while it is on top
 */
class GrownUpMenuReturnTest {
    private val navigation = NavigationManager()
    private val menuReturn = GrownUpMenuReturn(navigation)
    private val settings = Destination.Settings(PreferenceScreenOption.PEKE)
    private val kidPage = Destination.MediaItem(UUID.randomUUID(), org.jellyfin.sdk.model.api.BaseItemKind.MOVIE)

    @Before
    fun setUp() {
        mockkStatic(ACRA::class)
        every { ACRA.errorReporter } returns mockk(relaxed = true)
        navigation.navigateTo(kidPage)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `the rules opened from the menu return to it, focused on the rules`() {
        menuReturn.open(Destination.KidRules)

        assertEquals(GrownUpMenuItem.RULES, menuReturn.itemFor(Destination.KidRules))
    }

    @Test
    fun `Settings opened from the menu returns to it, focused on Settings`() {
        menuReturn.open(settings)

        assertEquals(GrownUpMenuItem.SETTINGS, menuReturn.itemFor(settings))
    }

    @Test
    fun `closing the menu shown again removes the page, back to the kid's page`() {
        menuReturn.open(Destination.KidRules)

        menuReturn.close(Destination.KidRules)

        assertEquals(listOf(Destination.Home(), kidPage), navigation.backStack.toList())
        assertNull(menuReturn.itemFor(Destination.KidRules))
    }

    @Test
    fun `a page opened from Settings goes back to Settings, and Settings still to the menu`() {
        menuReturn.open(settings)
        navigation.navigateTo(Destination.KidRules)

        assertNull(menuReturn.itemFor(Destination.KidRules))
        assertNull(menuReturn.itemFor(settings))

        navigation.goBack()
        assertEquals(GrownUpMenuItem.SETTINGS, menuReturn.itemFor(settings))
    }

    @Test
    fun `the rules opened another way keep their Back`() {
        navigation.navigateTo(Destination.KidRules)

        assertNull(menuReturn.itemFor(Destination.KidRules))
    }

    @Test
    fun `a page that went another way is no longer the menu's`() {
        menuReturn.open(Destination.KidRules)
        navigation.goBack()
        navigation.navigateTo(kidPage)
        navigation.navigateTo(Destination.KidRules)

        assertNull(menuReturn.itemFor(Destination.KidRules))
        // Closing then removes nothing
        menuReturn.close(Destination.KidRules)
        assertEquals(Destination.KidRules, navigation.backStack.last())
    }

    @Test
    fun `Settings outside the kid's pages keeps its Back`() {
        menuReturn.open(settings)
        navigation.openApart(settings)

        assertNull(menuReturn.itemFor(settings))
    }
}
