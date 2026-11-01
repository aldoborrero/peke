package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.ui.main.settings.Library
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

/**
 * The libraries the home settings carry are only handed over; they don't make a rebuild another home
 */
class HomePageResolvedSettingsTest {
    private val userId = UUID.randomUUID()
    private val library = Library(UUID.randomUUID(), "Shows", BaseItemKind.COLLECTION_FOLDER, CollectionType.TVSHOWS, false)

    @Test
    fun `a rebuild with the same rows is the same home whatever libraries it carries`() {
        val built = HomePageResolvedSettings(userId, emptyList(), listOf(library))
        val rebuilt = HomePageResolvedSettings(userId, emptyList(), listOf(library.copy(name = "TV")))

        assertEquals(built, rebuilt)
        assertEquals(built.hashCode(), rebuilt.hashCode())
        assertEquals(built, HomePageResolvedSettings(userId, emptyList()))
    }

    @Test
    fun `another user's home is another home`() {
        assertNotEquals(
            HomePageResolvedSettings(userId, emptyList(), listOf(library)),
            HomePageResolvedSettings(UUID.randomUUID(), emptyList(), listOf(library)),
        )
    }
}
