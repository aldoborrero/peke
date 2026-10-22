package dev.aldoborrero.peke.ui.playback

import androidx.compose.ui.text.intl.Locale
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.ItemPlayback
import dev.aldoborrero.peke.data.model.Playlist
import dev.aldoborrero.peke.data.model.PlaylistItem
import dev.aldoborrero.peke.preferences.PlayerBackend
import dev.aldoborrero.peke.ui.formatBitrate
import dev.aldoborrero.peke.util.LoadingState
import io.github.peerless2012.ass.media.AssHandler
import org.jellyfin.sdk.model.api.MediaSegmentDto

data class PlaybackState(
    val loading: LoadingState = LoadingState.Loading,
    val currentMediaInfo: CurrentMediaInfo = CurrentMediaInfo.EMPTY,
    val currentPlayback: CurrentPlayback? = null,
    val currentItemPlayback: ItemPlayback? = null,
    val currentSegment: MediaSegmentState? = null,
    val analyticsState: AnalyticsState = AnalyticsState(),
    val subtitleCues: List<Cue> = emptyList(),
    val nextUp: BaseItem? = null,
    // Kids mode: show "watch again" / "home" instead of continuing
    val kidsEndScreen: Boolean = false,
    val playlistIndex: Int = 0,
    val playlist: Playlist = Playlist(emptyList()),
) {
    val hasNext: Boolean get() = (playlistIndex + 1) < playlist.items.size
    val hasPrevious: Boolean get() = playlistIndex > 0
    val upcomingItems: List<PlaylistItem>
        get() =
            playlist.items.subList(
                (playlistIndex + 1).coerceAtMost(playlist.items.size),
                playlist.items.size,
            )

    fun nextItem(): PlaylistItem? = playlist.items.getOrNull(playlistIndex + 1)
}

data class PlayerInstance(
    val player: Player,
    val backend: PlayerBackend,
    val assHandler: AssHandler?,
)

data class MediaSegmentState(
    val segment: MediaSegmentDto,
    val interacted: Boolean,
)

data class AnalyticsState(
    val bitrate: String = formatBitrate(0),
    val bitrateEstimate: String = formatBitrate(0),
    val droppedFrames: Int = 0,
)

data class SubtitleSearchState(
    val status: SubtitleSearchStatus = SubtitleSearchStatus.Inactive,
    val language: String = Locale.current.language,
)
