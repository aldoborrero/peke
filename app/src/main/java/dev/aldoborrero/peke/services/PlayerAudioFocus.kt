@file:OptIn(markerClass = [UnstableApi::class])

package dev.aldoborrero.peke.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioFocusManager
import androidx.media3.common.util.UnstableApi
import java.io.Closeable

/** What video plays as: a film's soundtrack, which takes the audio focus while it plays */
val VideoAudioAttributes: AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

/**
 * Audio focus for a [player] that doesn't handle it itself, as ExoPlayer does with `handleAudioFocus` (MPV): requests
 * focus when playback starts, pauses when another app takes it, and resumes when a short loss, as the Assistant
 * speaking, ends; ducks for a notification where the player can change its volume. Uses media3's own focus manager, so
 * both backends behave the same. [close] gives the focus back.
 */
class PlayerAudioFocus(
    context: Context,
    private val player: Player,
) : Player.Listener,
    AudioFocusManager.PlayerControl,
    Closeable {
    private val manager =
        AudioFocusManager(context, player.applicationLooper, this).apply { setAudioAttributes(VideoAudioAttributes) }

    /** Paused by a short loss of focus, so playback resumes when focus comes back */
    private var pausedForFocus = false

    init {
        player.addListener(this)
        update()
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) = update()

    override fun onPlaybackStateChanged(playbackState: Int) = update()

    private fun update() {
        val command = manager.updateAudioFocus(player.playWhenReady, player.playbackState)
        // A pause, ours or the kid's, needs nothing more; playing again is the kid's choice and asks for focus
        if (player.playWhenReady) {
            pausedForFocus = false
            follow(command)
        }
    }

    override fun executePlayerCommand(command: Int) = follow(command)

    private fun follow(command: Int) {
        when (command) {
            AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY -> {
                if (pausedForFocus) {
                    pausedForFocus = false
                    player.play()
                }
            }

            AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK -> {
                if (player.playWhenReady) {
                    pausedForFocus = true
                    player.pause()
                }
            }

            else -> {
                pausedForFocus = false
                if (player.playWhenReady) player.pause()
            }
        }
    }

    override fun setVolumeMultiplier(volumeMultiplier: Float) {
        if (player.isCommandAvailable(Player.COMMAND_SET_VOLUME)) player.volume = volumeMultiplier
    }

    override fun close() {
        player.removeListener(this)
        manager.release()
    }
}
