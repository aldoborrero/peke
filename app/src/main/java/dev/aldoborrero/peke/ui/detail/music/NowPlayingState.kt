package dev.aldoborrero.peke.ui.detail.music

import androidx.compose.runtime.Stable
import dev.aldoborrero.peke.data.model.AudioItem
import dev.aldoborrero.peke.services.BackdropResult
import dev.aldoborrero.peke.services.MusicServiceState
import org.jellyfin.sdk.model.api.LyricDto

@Stable
data class NowPlayingState(
    val musicServiceState: MusicServiceState,
    val currentMediaItem: AudioItem? = null,
    val lyrics: LyricDto? = null,
    val currentLyricIndex: Int? = null,
    val visualizerPermissions: Boolean = false,
    val backdropResult: BackdropResult = BackdropResult.NONE,
) {
    val hasLyrics: Boolean get() = lyrics != null && lyrics.lyrics.isNotEmpty()
}
