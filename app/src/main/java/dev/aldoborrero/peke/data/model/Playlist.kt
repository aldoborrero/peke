package dev.aldoborrero.peke.data.model

import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

/**
 * Tracks playback of multiple items. Points to the current media with function to advance or go to previous ones.
 *
 * This is not the same thing as a Jellyfin server playlist
 */
data class Playlist(
    val items: List<PlaylistItem>,
    /** Where an in-order queue of episodes goes on, when there are more than it holds */
    val continuation: EpisodesContinuation? = null,
) {
    companion object {
        const val MAX_SIZE = 100

        /** Episodes read at a time for an in-order queue of episodes */
        const val EPISODES_WINDOW = 10

        fun fromMedia(list: List<BaseItem>) = Playlist(list.map { PlaylistItem.Media(it) })
    }
}

/**
 * The episodes of [seriesId] (of [seasonId] only, if set) after [afterEpisodeId], in order
 */
data class EpisodesContinuation(
    val seriesId: UUID,
    val seasonId: UUID?,
    val afterEpisodeId: UUID,
)

data class PlaylistInfo(
    val id: UUID,
    val name: String,
    val count: Int,
    val mediaType: MediaType,
)

sealed interface PlaylistItem {
    val id: UUID
    val item: BaseItem

    data class Media(
        override val item: BaseItem,
    ) : PlaylistItem {
        override val id: UUID
            get() = item.id
    }

    data class Intro(
        override val item: BaseItem,
    ) : PlaylistItem {
        override val id: UUID
            get() = item.id
    }
}
