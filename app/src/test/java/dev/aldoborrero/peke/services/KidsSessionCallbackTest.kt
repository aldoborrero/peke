package dev.aldoborrero.peke.services

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutionException

/**
 * Spec 005 FR-017: only Peke and trusted controllers drive a kid's player, with transport commands only (P1)
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class KidsSessionCallbackTest {
    private val session = mockk<MediaSession>(relaxed = true)
    private val callback = KidsSessionCallback(PACKAGE)

    private fun controller(
        packageName: String,
        trusted: Boolean,
    ) = mockk<MediaSession.ControllerInfo> {
        every { this@mockk.packageName } returns packageName
        every { isTrusted } returns trusted
    }

    @Test
    fun `peke and trusted controllers are accepted with transport commands only`() {
        listOf(controller(PACKAGE, trusted = false), controller("com.android.systemui", trusted = true)).forEach {
            val result = callback.onConnect(session, it)
            assertTrue(it.packageName, result.isAccepted)
            assertEquals(KidsSessionCallback.PLAYER_COMMANDS, result.availablePlayerCommands)
        }
    }

    @Test
    fun `other apps are refused`() {
        val result = callback.onConnect(session, controller("com.example.remote", trusted = false))
        assertFalse(result.isAccepted)
    }

    @Test
    fun `nothing can change what is queued`() {
        listOf(
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_SET_PLAYLIST_METADATA,
        ).forEach { assertFalse("command $it", KidsSessionCallback.PLAYER_COMMANDS.contains(it)) }
        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
        ).forEach { assertTrue("command $it", KidsSessionCallback.PLAYER_COMMANDS.contains(it)) }
    }

    @Test
    fun `adding or setting items fails`() {
        val controller = controller(PACKAGE, trusted = true)
        val items = mutableListOf(MediaItem.fromUri("https://example.org/video.mp4"))
        assertFailed { callback.onAddMediaItems(session, controller, items).get() }
        assertFailed { callback.onSetMediaItems(session, controller, items, 0, 0L).get() }
    }

    private fun assertFailed(block: () -> Unit) {
        try {
            block()
        } catch (_: ExecutionException) {
            return
        }
        throw AssertionError("Expected a failed future")
    }

    private companion object {
        const val PACKAGE = "dev.aldoborrero.peke.debug"
    }
}
