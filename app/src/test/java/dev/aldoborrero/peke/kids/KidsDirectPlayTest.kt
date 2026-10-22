package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.playback.KidsDirectPlay
import dev.aldoborrero.peke.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KidsDirectPlayTest {
    private val id = UUID.randomUUID()

    @Test
    fun `videos and series play directly`() {
        listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO, BaseItemKind.SERIES).forEach {
            assertTrue(it.name, KidsDirectPlay.handles(Destination.MediaItem(id, it)))
        }
        assertTrue(KidsDirectPlay.handles(Destination.SeriesOverview(id, BaseItemKind.SERIES)))
    }

    @Test
    fun `collections and other pages open as usual`() {
        listOf(BaseItemKind.BOX_SET, BaseItemKind.PLAYLIST, BaseItemKind.FOLDER).forEach {
            assertFalse(it.name, KidsDirectPlay.handles(Destination.MediaItem(id, it)))
        }
        assertFalse(KidsDirectPlay.handles(Destination.Home()))
        assertFalse(KidsDirectPlay.handles(Destination.Playback(id, 0)))
    }

    @Test
    fun `people, music and photos are refused, not opened`() {
        listOf(BaseItemKind.PERSON, BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.PHOTO, BaseItemKind.PHOTO_ALBUM).forEach {
            assertTrue(it.name, KidsDirectPlay.handles(Destination.MediaItem(id, it)))
        }
    }
}
