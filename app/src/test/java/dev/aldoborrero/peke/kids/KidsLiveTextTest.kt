package dev.aldoborrero.peke.kids

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.aldoborrero.peke.kids.livetv.LiveCard
import dev.aldoborrero.peke.kids.livetv.LiveProgramme
import dev.aldoborrero.peke.kids.livetv.LiveStatus
import dev.aldoborrero.peke.kids.livetv.TimeLeft
import dev.aldoborrero.peke.kids.livetv.liveCardSpoken
import dev.aldoborrero.peke.kids.livetv.liveTimeLeft
import dev.aldoborrero.peke.kids.livetv.liveTimeLeftSpoken
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.UUID

/**
 * The live card's time texts, in the owner's words (spec 036), and what TalkBack reads
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class KidsLiveTextTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    private val duggee =
        LiveCard(
            channelId = UUID.randomUUID(),
            channelName = "PekeTV",
            logoTag = null,
            programme =
                LiveProgramme(
                    id = UUID.randomUUID(),
                    channelId = UUID.randomUUID(),
                    name = "Hey Duggee",
                    episodeTitle = null,
                    episodeNumber = null,
                    start = LocalDateTime.MIN,
                    end = LocalDateTime.MAX,
                    thumbTag = null,
                    primaryTag = null,
                ),
        )

    @Test
    @Config(qualifiers = "es")
    fun `Spanish time texts`() {
        assertEquals("quedan 12 min", resources.liveTimeLeft(TimeLeft.Minutes(12)))
        assertEquals("quedan 59 min", resources.liveTimeLeft(TimeLeft.Minutes(59)))
        assertEquals("queda 1 min", resources.liveTimeLeft(TimeLeft.Minutes(1)))
        assertEquals("acaba ya", resources.liveTimeLeft(TimeLeft.EndingNow))
        assertEquals("queda 1 h", resources.liveTimeLeft(TimeLeft.Hours(1, 0)))
        assertEquals("quedan 2 h", resources.liveTimeLeft(TimeLeft.Hours(2, 0)))
        assertEquals("quedan 1 h 1 min", resources.liveTimeLeft(TimeLeft.Hours(1, 1)))
        assertEquals("quedan 1 h 20 min", resources.liveTimeLeft(TimeLeft.Hours(1, 20)))
    }

    @Test
    @Config(qualifiers = "es")
    fun `TalkBack reads the channel, the series and the time left in full words`() {
        assertEquals("PekeTV: Hey Duggee, quedan 12 minutos", resources.liveCardSpoken(duggee, LiveStatus(TimeLeft.Minutes(12), .4f)))
        assertEquals("queda 1 minuto", resources.liveTimeLeftSpoken(TimeLeft.Minutes(1)))
        assertEquals("quedan 1 hora y 20 minutos", resources.liveTimeLeftSpoken(TimeLeft.Hours(1, 20)))
        assertEquals("queda 1 hora", resources.liveTimeLeftSpoken(TimeLeft.Hours(1, 0)))
        // No guide: the channel alone
        assertEquals("PekeTV", resources.liveCardSpoken(duggee.copy(programme = null), null))
    }

    @Test
    fun `English time texts`() {
        assertEquals("12 min left", resources.liveTimeLeft(TimeLeft.Minutes(12)))
        assertEquals("1 min left", resources.liveTimeLeft(TimeLeft.Minutes(1)))
        assertEquals("ending now", resources.liveTimeLeft(TimeLeft.EndingNow))
        assertEquals("1 h left", resources.liveTimeLeft(TimeLeft.Hours(1, 0)))
        assertEquals("1 h 20 min left", resources.liveTimeLeft(TimeLeft.Hours(1, 20)))
        assertEquals("PekeTV: Hey Duggee, 12 minutes left", resources.liveCardSpoken(duggee, LiveStatus(TimeLeft.Minutes(12), .4f)))
        assertEquals("1 hour and 20 minutes left", resources.liveTimeLeftSpoken(TimeLeft.Hours(1, 20)))
    }
}
