package dev.aldoborrero.peke.util

import androidx.media3.common.Player
import dev.aldoborrero.peke.ui.successResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Progress reports while paused: once a minute from the timer, at once on a seek, and buffering isn't a pause
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class TrackActivityPlaybackListenerTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val playStateApi = mockk<PlayStateApi>(relaxed = true)
    private val api = mockk<ApiClient> { every { playStateApi } returns this@TrackActivityPlaybackListenerTest.playStateApi }
    private var playing = true
    private var toPlay = true
    private val player =
        mockk<Player>(relaxed = true) {
            every { isPlaying } answers { playing }
            every { playWhenReady } answers { toPlay }
            every { currentPosition } returns 60_000L
        }
    private lateinit var listener: TrackActivityPlaybackListener

    @Before
    fun setUp() {
        PekeDispatchers.configure(dispatcher)
        coEvery { playStateApi.reportPlaybackProgress(any()) } returns successResponse(Unit)
        listener = TrackActivityPlaybackListener(api, player) { PlaybackItemState(UUID.randomUUID(), PlayMethod.DIRECT_PLAY) }
        listener.init()
    }

    @After
    fun tearDown() {
        listener.release()
        PekeDispatchers.reset()
    }

    private fun progressReports(count: Int) = coVerify(exactly = count) { playStateApi.reportPlaybackProgress(any()) }

    @Test
    fun `the timer reports while paused only once a minute`() =
        runTest(dispatcher) {
            playing = false
            toPlay = false
            listener.onIsPlayingChanged(false)
            progressReports(1)

            listener.reportFromTimer()
            listener.reportFromTimer()

            progressReports(1)
        }

    @Test
    fun `a seek while paused is reported at once`() =
        runTest(dispatcher) {
            playing = false
            toPlay = false
            listener.onIsPlayingChanged(false)

            listener.onPositionDiscontinuity(mockk(), mockk(), Player.DISCONTINUITY_REASON_SEEK)

            progressReports(2)
        }

    @Test
    fun `buffering goes on reporting`() =
        runTest(dispatcher) {
            playing = false
            toPlay = true
            listener.onIsPlayingChanged(false)

            listener.reportFromTimer()
            listener.reportFromTimer()

            progressReports(3)
        }
}
