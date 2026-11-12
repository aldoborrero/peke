package dev.aldoborrero.peke.kids

import androidx.media3.common.Player
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.ui.pausedFlow
import dev.aldoborrero.peke.kids.ui.timeLeftBarShown
import dev.aldoborrero.peke.ui.nav.Destination
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The time-left bar shows only on the playback page while the video plays (spec 022 FR-001, D1)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeLeftBarShownTest {
    private val id = UUID.randomUUID()

    @Test
    fun `the bar is drawn on the playback page, at the top or the bottom`() {
        listOf(Destination.Playback(id, 0L), Destination.PlaybackList(id)).forEach { page ->
            assertTrue("$page", timeLeftBarShown(page, KidsTimerBarPosition.TOP, paused = false))
            assertTrue("$page", timeLeftBarShown(page, KidsTimerBarPosition.BOTTOM, paused = false))
        }
    }

    @Test
    fun `no bar on any other page`() {
        listOf(
            null,
            Destination.Home(),
            Destination.Search(),
            Destination.Favorites,
            Destination.KidsLiveTv,
            Destination.MediaItem(id, BaseItemKind.MOVIE),
            Destination.SeriesOverview(id, BaseItemKind.SERIES),
            Destination.KidsResume(Destination.Playback(id, 0L)),
            Destination.KidRules,
            Destination.HomeSettings,
            Destination.KidsTimesUp,
            Destination.KidsChannelRefused,
        ).forEach { page ->
            assertFalse("$page", timeLeftBarShown(page, KidsTimerBarPosition.TOP, paused = false))
            assertFalse("$page", timeLeftBarShown(page, KidsTimerBarPosition.BOTTOM, paused = false))
        }
    }

    @Test
    fun `hidden is never drawn, not even while playing`() {
        assertFalse(timeLeftBarShown(Destination.Playback(id, 0L), KidsTimerBarPosition.HIDDEN, paused = false))
        assertFalse(timeLeftBarShown(Destination.PlaybackList(id), KidsTimerBarPosition.HIDDEN, paused = false))
    }

    @Test
    fun `no bar while the video is paused, on either edge`() {
        listOf(Destination.Playback(id, 0L), Destination.PlaybackList(id)).forEach { page ->
            assertFalse("$page", timeLeftBarShown(page, KidsTimerBarPosition.TOP, paused = true))
            assertFalse("$page", timeLeftBarShown(page, KidsTimerBarPosition.BOTTOM, paused = true))
        }
    }

    @Test
    fun `pausing hides the bar and playing again shows it`() =
        runTest {
            val listeners = mutableListOf<Player.Listener>()
            val player = mockk<Player>(relaxed = true)
            every { player.playWhenReady } returns true
            every { player.addListener(capture(listeners)) } just runs
            every { player.removeListener(any()) } answers { listeners.remove(firstArg()) }

            val seen = mutableListOf<Boolean>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { player.pausedFlow().collect { seen += it } }
            listeners.single().onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            listeners.single().onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            job.cancel()

            assertEquals(listOf(false, true, false), seen)
            val page = Destination.Playback(id, 0L)
            assertEquals(listOf(true, false, true), seen.map { timeLeftBarShown(page, KidsTimerBarPosition.TOP, it) })
            assertTrue("the listener goes with the player", listeners.isEmpty())
        }
}
