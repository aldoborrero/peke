package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.livetv.channelNowNext
import dev.aldoborrero.peke.ui.detail.livetv.ProgramCategory
import dev.aldoborrero.peke.ui.detail.livetv.TvProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

// TvProgram needs Android classes to load
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class KidsLiveGuideTest {
    private val channel = UUID.randomUUID()
    private val t0 = LocalDateTime.of(2026, 10, 27, 18, 0)

    private fun program(
        name: String,
        start: Int,
        end: Int,
        category: ProgramCategory? = null,
    ) = TvProgram(
        id = UUID.randomUUID(),
        channelId = channel,
        start = t0.plusMinutes(start.toLong()),
        end = t0.plusMinutes(end.toLong()),
        startHours = 0f,
        endHours = 0f,
        duration = (end - start).minutes,
        name = name,
        subtitle = null,
        seasonEpisode = null,
        isRecording = false,
        isSeriesRecording = false,
        isRepeat = false,
        category = category,
    )

    @Test
    fun `now is the programme airing, next the first after it, and progress how far it has gone`() {
        val programs = listOf(program("Ocean Friends", 0, 30), program("Little Forest", 30, 60), program("Later", 60, 90))
        val result = channelNowNext(programs, t0.plusMinutes(10))
        assertEquals("Ocean Friends", result.now?.name)
        assertEquals("Little Forest", result.next?.name)
        assertEquals(1f / 3, result.progress!!, 0.001f)
    }

    @Test
    fun `at a programme's end the next one is airing`() {
        val programs = listOf(program("Ocean Friends", 0, 30), program("Little Forest", 30, 60))
        val result = channelNowNext(programs, t0.plusMinutes(30))
        assertEquals("Little Forest", result.now?.name)
        assertNull(result.next)
    }

    @Test
    fun `a channel without a guide has no programme, progress or next`() {
        val result = channelNowNext(listOf(program("PekeTV", 0, 60, ProgramCategory.FAKE)), t0.plusMinutes(10))
        assertNull(result.now)
        assertNull(result.next)
        assertNull(result.progress)
        assertNull(channelNowNext(emptyList(), t0).now)
    }

    @Test
    fun `a gap in the guide has no programme now but still the next one`() {
        val programs = listOf(program("Ocean Friends", 0, 30), program("Little Forest", 40, 60))
        val result = channelNowNext(programs, t0.plusMinutes(35))
        assertNull(result.now)
        assertEquals("Little Forest", result.next?.name)
    }
}
