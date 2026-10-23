package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.preferences.AppPreference
import dev.aldoborrero.peke.ui.detail.favoriteOptions
import dev.aldoborrero.peke.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KidsItemTypesTest {
    private val id = UUID.randomUUID()

    @Test
    fun musicPeopleAndPhotosAreRefused() {
        KidsItemTypes.EXCLUDED.forEach { assertTrue(it.name, KidsItemTypes.refuses(Destination.MediaItem(id, it))) }
        listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.SERIES, BaseItemKind.BOX_SET, BaseItemKind.MUSIC_VIDEO)
            .forEach { assertFalse(it.name, KidsItemTypes.refuses(Destination.MediaItem(id, it))) }
    }

    @Test
    fun favoritesOnlyOfferVideos() {
        assertTrue(favoriteOptions.none { it in KidsItemTypes.EXCLUDED })
        assertFalse(BaseItemKind.TV_CHANNEL in favoriteOptions)
    }

    @Test
    fun debugInfoIsOffByDefault() {
        assertEquals(false, AppPreference.PlaybackDebugInfo.defaultValue)
    }
}
