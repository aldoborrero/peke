package dev.aldoborrero.peke.services

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber

/**
 * Who may drive the player's media session (spec 005 FR-017, review P1): Peke itself and controllers the system
 * trusts (its media controls, the remote's media keys, Google TV's remote), and only with transport commands. Any
 * other app, on the TV or a paired device, is refused, and nothing may set, add or replace what is queued.
 *
 * On Android TV before API 28, platform controllers can arrive untrusted; check the media keys there before relying on
 * this (research R9).
 */
@OptIn(UnstableApi::class)
class KidsSessionCallback(
    private val packageName: String,
) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        if (controller.packageName != packageName && !controller.isTrusted) {
            Timber.i("Refusing media controller %s", controller.packageName)
            return MediaSession.ConnectionResult.reject()
        }
        return MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(PLAYER_COMMANDS)
            .build()
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFailedFuture(UnsupportedOperationException("Not for kids"))

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        Futures.immediateFailedFuture(UnsupportedOperationException("Not for kids"))

    companion object {
        /**
         * Play, pause, seek and skip within what Peke queued, plus reading the state. Nothing that changes the queue,
         * the speed, the tracks or the volume.
         */
        val PLAYER_COMMANDS: Player.Commands =
            Player.Commands
                .Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                    Player.COMMAND_GET_VOLUME,
                    Player.COMMAND_GET_DEVICE_VOLUME,
                    Player.COMMAND_GET_TRACKS,
                ).build()
    }
}
