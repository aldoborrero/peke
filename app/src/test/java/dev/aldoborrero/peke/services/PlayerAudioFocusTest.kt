package dev.aldoborrero.peke.services

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.PlayerBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.media.AudioAttributes as PlatformAudioAttributes

/**
 * Video takes the audio focus (the media session guide's "must"): MPV through [PlayerAudioFocus], which pauses for
 * another app and resumes after a short loss, and ExoPlayer through its own focus handling with the same attributes
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerAudioFocusTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    private val player = FakePlayer()
    private val focus = PlayerAudioFocus(context, player)

    @After
    fun tearDown() = focus.close()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun focusChange(change: Int) {
        audioManager.lastAudioFocusRequest.listener.onAudioFocusChange(change)
        idle()
    }

    private fun play() {
        player.play()
        idle()
    }

    @Test
    fun `playing asks for the audio focus as a film`() {
        assertEquals("nothing asked before playing", null, audioManager.lastAudioFocusRequest)
        play()
        val request = audioManager.lastAudioFocusRequest
        assertNotNull(request)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, request.durationHint)
        assertEquals(PlatformAudioAttributes.USAGE_MEDIA, request.audioFocusRequest.audioAttributes.usage)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `a short loss pauses and playback resumes when the focus comes back`() {
        play()
        focusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse("paused while the Assistant speaks", player.playWhenReady)
        focusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertTrue("playing again", player.playWhenReady)
    }

    @Test
    fun `a kid's pause during a short loss stays paused`() {
        play()
        focusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        player.play()
        player.pause()
        idle()
        focusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `losing the focus to another app pauses until the kid plays again`() {
        play()
        focusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertFalse(player.playWhenReady)
        play()
        assertTrue("asks again and plays", player.playWhenReady)
    }

    @Test
    fun `closing gives the focus back`() {
        play()
        focus.close()
        idle()
        assertNotNull(audioManager.lastAbandonedAudioFocusRequest)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the ExoPlayer for video plays as a film`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exo =
                PlayerFactory(
                    context,
                    OkHttpClient(),
                ).createVideoPlayer(PlayerBackend.EXO_PLAYER, AppPreferences.getDefaultInstance()).player
            try {
                assertEquals(VideoAudioAttributes, exo.audioAttributes)
            } finally {
                exo.release()
                Dispatchers.resetMain()
            }
        }

    /** A player that only plays and pauses */
    private class FakePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var current =
            State
                .Builder()
                .setAvailableCommands(
                    Player.Commands
                        .Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .build(),
                ).setPlaylist(listOf(MediaItemData.Builder("episode").build()))
                .setPlaybackState(Player.STATE_READY)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()

        override fun getState(): State = current

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            current = current.buildUpon().setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }
    }
}
