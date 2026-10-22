package dev.aldoborrero.peke.kids

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import dev.aldoborrero.peke.kids.playback.KidsPlaybackRefusal
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.timer.KidsLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Server enforcement: the plugin's refusals and its own messages
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class KidsEnforcementTest {
    private val warnAt = listOf(300.seconds, 60.seconds)

    private fun status(
        remaining: Long = 1200,
        sittingRemaining: Long? = null,
    ) = KidsStatus(
        date = "2026-10-19",
        allowanceSeconds = 3600,
        remainingSeconds = remaining,
        sittingLimitSeconds = sittingRemaining?.let { 1800 },
        sittingRemainingSeconds = sittingRemaining,
        enforced = true,
    )

    private fun httpError(
        code: Int,
        headers: Map<String, List<String>>,
    ): PlaybackException {
        val http =
            HttpDataSource.InvalidResponseCodeException(
                code,
                null,
                IOException(),
                headers,
                DataSpec(Uri.parse("http://localhost/Videos/1/stream")),
                ByteArray(0),
            )
        return PlaybackException("Source error", http, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
    }

    @Test
    fun `a refused stream gives the plugin's reason`() {
        val error =
            httpError(
                403,
                mapOf(
                    "x-application-error-code" to listOf("PlaybackNotAllowed"),
                    "X-Kids-Reason" to listOf("sessionLimit"),
                ),
            )
        val refusal = KidsPlaybackRefusal.of(error)
        assertEquals("sessionLimit", refusal?.reason)
        assertEquals(KidsDenial.SITTING_OVER, KidsDenial.of(refusal?.reason))
    }

    @Test
    fun `other stream errors are not refusals`() {
        // Not the plugin's 403
        assertNull(KidsPlaybackRefusal.of(httpError(403, mapOf("X-Application-Error-Code" to listOf("ParentalControl")))))
        assertNull(KidsPlaybackRefusal.of(httpError(403, emptyMap())))
        assertNull(KidsPlaybackRefusal.of(httpError(500, mapOf("X-Application-Error-Code" to listOf("PlaybackNotAllowed")))))
        assertNull(KidsPlaybackRefusal.of(PlaybackException("decoder", null, PlaybackException.ERROR_CODE_DECODING_FAILED)))
        // The plugin's refusal without a reason
        val noReason = KidsPlaybackRefusal.of(httpError(403, mapOf("X-Application-Error-Code" to listOf("PlaybackNotAllowed"))))
        assertNull(noReason!!.reason)
    }

    @Test
    fun `a refused live TV stream is not a time refusal`() {
        val channel =
            httpError(
                403,
                mapOf("X-Application-Error-Code" to listOf("PlaybackNotAllowed"), "X-Kids-Reason" to listOf("liveTvChannel")),
            )
        val reason = KidsPlaybackRefusal.of(channel)?.reason
        assertEquals(KidsPlaybackRefusal.Kind.LIVE_TV_CHANNEL, KidsPlaybackRefusal.kindOf(reason))
        assertEquals(KidsPlaybackRefusal.Kind.LIVE_TV_RECORDING, KidsPlaybackRefusal.kindOf("liveTvRecording"))
        assertEquals(KidsPlaybackRefusal.Kind.LIVE_TV_CHANNEL, KidsPlaybackRefusal.kindOf(" LiveTvChannel "))
        // Time refusals, and refusals without a reason, still go to the time limits and their screens
        assertEquals(KidsPlaybackRefusal.Kind.TIME, KidsPlaybackRefusal.kindOf("timeUp"))
        assertEquals(KidsPlaybackRefusal.Kind.TIME, KidsPlaybackRefusal.kindOf("outsideSchedule"))
        assertEquals(KidsPlaybackRefusal.Kind.TIME, KidsPlaybackRefusal.kindOf(null))
    }

    @Test
    fun `the reason header is found whatever its case`() {
        val headers = mapOf("x-kids-reason" to listOf("outsideSchedule"))
        assertEquals("outsideSchedule", KidsPlaybackRefusal.header(headers, KidsPlaybackRefusal.REASON_HEADER))
        assertNull(KidsPlaybackRefusal.header(emptyMap(), KidsPlaybackRefusal.REASON_HEADER))
    }

    @Test
    fun `only the plugin's messages are hidden`() {
        val denied = KidsLimits.of(status(), refusal = KidsDenial.TIME_UP)
        assertTrue(KidsServerMessages.isKids("Time's up"))
        assertFalse(KidsServerMessages.isKids("Server restarting"))
        assertFalse(KidsServerMessages.shouldHide("Server restarting", denied, warnAt))
        assertFalse(KidsServerMessages.shouldHide(null, denied, warnAt))
    }

    @Test
    fun `denial messages are hidden behind the kids screens`() {
        val denied = KidsLimits.of(status(), refusal = KidsDenial.SITTING_OVER)
        assertTrue(KidsServerMessages.shouldHide("Time for a break", denied, warnAt))
        assertTrue(KidsServerMessages.shouldHide("Time's up", denied, warnAt))
        assertTrue(KidsServerMessages.shouldHide("Bedtime", denied, warnAt))
        // A denial the app doesn't know of yet is shown
        assertFalse(KidsServerMessages.shouldHide("Time's up", KidsLimits.of(status()), warnAt))
    }

    @Test
    fun `warnings are hidden when the app warns too`() {
        val allowanceNear = KidsLimits.of(status(remaining = 290))
        assertTrue(KidsServerMessages.shouldHide("Screen time", allowanceNear, warnAt))
        assertFalse(KidsServerMessages.shouldHide("Break time", allowanceNear, warnAt))
        val sittingNear = KidsLimits.of(status(remaining = 1200, sittingRemaining = 55))
        assertTrue(KidsServerMessages.shouldHide("Break time", sittingNear, warnAt))
        assertFalse(KidsServerMessages.shouldHide("Screen time", sittingNear, warnAt))
        // The app's status says there is more time: the server's warning is news
        assertFalse(KidsServerMessages.shouldHide("Screen time", KidsLimits.of(status(remaining = 1200)), warnAt))
        // The app has no bedtime warning of its own
        assertFalse(KidsServerMessages.shouldHide("Bedtime", allowanceNear, warnAt))
    }
}
