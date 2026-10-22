package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * The item types a kid never gets in Peke: music, people and photos. Kids get videos and their collections.
 */
object KidsItemTypes {
    val EXCLUDED =
        listOf(
            BaseItemKind.AUDIO,
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.MUSIC_ALBUM,
            BaseItemKind.MUSIC_ARTIST,
            BaseItemKind.PERSON,
            BaseItemKind.PHOTO,
            BaseItemKind.PHOTO_ALBUM,
        )

    /**
     * Whether [destination] leads to something kids don't get, so it is refused instead of opening a page
     */
    fun refuses(destination: Destination): Boolean =
        when (destination) {
            is Destination.MediaItem -> destination.type in EXCLUDED
            is Destination.Slideshow -> true
            else -> false
        }
}
