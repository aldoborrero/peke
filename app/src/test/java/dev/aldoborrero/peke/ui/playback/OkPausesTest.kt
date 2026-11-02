package dev.aldoborrero.peke.ui.playback

import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import androidx.compose.ui.input.key.KeyEvent
import androidx.media3.common.Player
import dev.aldoborrero.peke.preferences.AppPreferencesSerializer
import dev.aldoborrero.peke.preferences.DpadSeekMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds
import android.view.KeyEvent as NativeKeyEvent

/** TV-PC: with the controls hidden, OK pauses on the first press and shows the controls, and OK again resumes */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OkPausesTest {
    private val controls = ControllerViewState(hideMilliseconds = 5_000, controlsEnabled = true)

    private fun player(playing: Boolean) =
        mockk<Player>(relaxed = true) {
            every { isPlaying } returns playing
            every { playWhenReady } returns playing
            every { playbackState } returns Player.STATE_READY
            every { playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_NONE
            every { isCommandAvailable(any()) } returns true
        }

    private fun handler(player: Player) =
        PlaybackKeyHandler(
            player = player,
            controlsEnabled = true,
            skipWithLeftRight = true,
            seekBack = 10.seconds,
            seekForward = 30.seconds,
            getDurationMs = { 60_000 },
            controllerViewState = controls,
            updateSkipIndicator = {},
            clearSkipIndicator = {},
            skipBackOnResume = null,
            oneClickPause = true,
            onInteraction = {},
            onStop = {},
            onPlaybackDialogTypeClick = {},
            dpadSeekMode = DpadSeekMode.SEEKBAR_MINIMAL,
            onSeekToNext = {},
            onPSeekToPrevious = {},
        )

    private val ok = KeyEvent(NativeKeyEvent(ACTION_UP, KEYCODE_DPAD_CENTER))

    @Test
    fun `OK while playing pauses at once and shows the controls`() {
        val player = player(playing = true)
        assertTrue(handler(player).onKeyEvent(ok))
        verify { player.pause() }
        assertTrue(controls.controlsVisible)
    }

    @Test
    fun `OK while paused resumes and keeps the picture clear`() {
        val player = player(playing = false)
        handler(player).onKeyEvent(ok)
        verify { player.play() }
        assertFalse(controls.controlsVisible)
    }

    @Test
    fun `one-click pause is on by default`() {
        assertTrue(AppPreferencesSerializer().defaultValue.playbackPreferences.oneClickPause)
    }
}
