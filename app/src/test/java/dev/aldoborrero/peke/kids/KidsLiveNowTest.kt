package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.livetv.KidsChannel
import dev.aldoborrero.peke.kids.livetv.LiveCard
import dev.aldoborrero.peke.kids.livetv.LiveGuideWindow
import dev.aldoborrero.peke.kids.livetv.LivePicture
import dev.aldoborrero.peke.kids.livetv.LiveProgramme
import dev.aldoborrero.peke.kids.livetv.TimeLeft
import dev.aldoborrero.peke.kids.livetv.guideRefreshAt
import dev.aldoborrero.peke.kids.livetv.liveCard
import dev.aldoborrero.peke.kids.livetv.livePictures
import dev.aldoborrero.peke.kids.livetv.liveProgramme
import dev.aldoborrero.peke.kids.livetv.liveStatus
import dev.aldoborrero.peke.kids.livetv.nowNext
import dev.aldoborrero.peke.kids.livetv.showsLogoPill
import dev.aldoborrero.peke.kids.livetv.timeLeft
import dev.aldoborrero.peke.kids.livetv.withKidsChannels
import dev.aldoborrero.peke.kids.livetv.withKidsLiveChannels
import dev.aldoborrero.peke.kids.server.KidsItemsResult
import dev.aldoborrero.peke.kids.server.KidsJson
import dev.aldoborrero.peke.ui.util.StringStringProvider
import dev.aldoborrero.peke.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * The live card and the Live page's data (spec 036): pictures, time left, now and next, the home row's channels
 */
class KidsLiveNowTest {
    private val peke = UUID.fromString("7f3c0000-0000-0000-0000-000000000001")
    private val classics = UUID.fromString("7f3c0000-0000-0000-0000-000000000002")
    private val t0 = LocalDateTime.of(2026, 11, 28, 19, 0)

    private fun programme(
        name: String,
        start: Int,
        end: Int,
        channel: UUID = peke,
        thumb: String? = "still",
        primary: String? = "poster",
    ) = LiveProgramme(
        id = UUID.nameUUIDFromBytes("$name$start".toByteArray()),
        channelId = channel,
        name = name,
        episodeTitle = null,
        episodeNumber = null,
        start = t0.plusMinutes(start.toLong()),
        end = t0.plusMinutes(end.toLong()),
        thumbTag = thumb,
        primaryTag = primary,
    )

    private fun card(
        programme: LiveProgramme?,
        logo: String? = "logo",
    ) = LiveCard(peke, "PekeTV", logo, programme)

    @Test
    fun `pictures go still, poster, channel logo, Peke's TV`() {
        val duggee = programme("Hey Duggee", 0, 20)
        assertEquals(
            listOf(
                LivePicture.Still(duggee.id, "still"),
                LivePicture.Poster(duggee.id, "poster"),
                LivePicture.Logo(peke, "logo"),
                LivePicture.PekeTv,
            ),
            livePictures(card(duggee)),
        )
        // No still: the poster first
        assertEquals(LivePicture.Poster(duggee.id, "poster"), livePictures(card(duggee.copy(thumbTag = null))).first())
        // No programme picture: the channel's logo
        assertEquals(LivePicture.Logo(peke, "logo"), livePictures(card(duggee.copy(thumbTag = null, primaryTag = null))).first())
        // Nothing at all: Peke's TV
        assertEquals(listOf(LivePicture.PekeTv), livePictures(card(duggee.copy(thumbTag = null, primaryTag = null), logo = null)))
        // No guide: the logo, then Peke's TV
        assertEquals(listOf(LivePicture.Logo(peke, "logo"), LivePicture.PekeTv), livePictures(card(null)))
    }

    @Test
    fun `the logo pill sits only on the programme's own picture, and only with a logo`() {
        val duggee = programme("Hey Duggee", 0, 20)
        assertTrue(showsLogoPill(card(duggee), LivePicture.Still(duggee.id, "still")))
        assertTrue(showsLogoPill(card(duggee), LivePicture.Poster(duggee.id, "poster")))
        assertFalse(showsLogoPill(card(duggee), LivePicture.Logo(peke, "logo")))
        assertFalse(showsLogoPill(card(duggee), LivePicture.PekeTv))
        // A channel without a logo gets no pill: its name is in line 2
        assertFalse(showsLogoPill(card(duggee, logo = null), LivePicture.Still(duggee.id, "still")))
    }

    @Test
    fun `time left in whole minutes, the last minute ending now, hours from an hour`() {
        val end = t0.plusHours(2)
        assertEquals(TimeLeft.Hours(1, 1), timeLeft(end.minusMinutes(61), end))
        assertEquals(TimeLeft.Hours(1, 0), timeLeft(end.minusMinutes(60), end))
        assertEquals(TimeLeft.Minutes(59), timeLeft(end.minusMinutes(59), end))
        assertEquals(TimeLeft.Hours(1, 20), timeLeft(end.minusMinutes(80), end))
        assertEquals(TimeLeft.Minutes(12), timeLeft(end.minusMinutes(12).minusSeconds(30), end))
        assertEquals(TimeLeft.Minutes(1), timeLeft(end.minusSeconds(119), end))
        assertEquals(TimeLeft.Minutes(1), timeLeft(end.minusSeconds(60), end))
        assertEquals(TimeLeft.EndingNow, timeLeft(end.minusSeconds(59), end))
        assertEquals(TimeLeft.EndingNow, timeLeft(end, end))
        // Past the end, before the timer brings the next programme
        assertEquals(TimeLeft.EndingNow, timeLeft(end.plusSeconds(1), end))
    }

    @Test
    fun `the status moves once a minute`() {
        val duggee = programme("Hey Duggee", 0, 20)
        // 12 min 30 s left
        val at = t0.plusMinutes(7).plusSeconds(30)
        val status = liveStatus(duggee, at)
        assertEquals(TimeLeft.Minutes(12), status.timeLeft)
        assertEquals(.4f, status.progress, .001f)
        // Ticks of the clock within the minute give the same status, so nothing recomposes
        assertEquals(status, liveStatus(duggee, at.plusSeconds(2)))
        assertEquals(status, liveStatus(duggee, at.plusSeconds(30)))
        assertEquals(TimeLeft.Minutes(11), liveStatus(duggee, at.plusSeconds(31)).timeLeft)
        assertEquals(1f, liveStatus(duggee, t0.plusMinutes(25)).progress)
        assertEquals(0f, liveStatus(duggee, t0).progress)
    }

    @Test
    fun `now is the programme airing, next the one after it`() {
        val guide =
            listOf(
                programme("Hey Duggee", 0, 20),
                programme("Bluey", 20, 30),
                programme("Later", 30, 40),
                programme("D'Artacan y los tres mosqueperros", 0, 25, classics),
                programme("La Pantera Rosa", 25, 35, classics),
            ).shuffled()
        val onPeke = nowNext(guide, peke, t0.plusMinutes(8))
        assertEquals("Hey Duggee", onPeke.now?.name)
        assertEquals("Bluey", onPeke.next?.name)
        val onClassics = nowNext(guide, classics, t0.plusMinutes(8))
        assertEquals("D'Artacan y los tres mosqueperros", onClassics.now?.name)
        assertEquals("La Pantera Rosa", onClassics.next?.name)
        // At the boundary the next one is airing
        assertEquals("Bluey", nowNext(guide, peke, t0.plusMinutes(20)).now?.name)
    }

    @Test
    fun `a channel without a guide has no now or next, a gap still has a next`() {
        assertEquals(null, nowNext(emptyList(), peke, t0).now)
        val gap = nowNext(listOf(programme("Hey Duggee", 0, 20), programme("Bluey", 30, 40)), peke, t0.plusMinutes(25))
        assertNull(gap.now)
        assertEquals("Bluey", gap.next?.name)
    }

    @Test
    fun `the page reads its guide again when it runs out of a later`() {
        val guide =
            listOf(
                programme("Hey Duggee", 0, 20),
                programme("Bluey", 20, 30),
                programme("Old", 0, 15, classics),
                programme("New", 15, 60, classics),
            )
        // PekeTV's last known programme starts at 20, Peke Clásicos' at 15
        assertEquals(t0.plusMinutes(15), guideRefreshAt(guide, t0.plusMinutes(5)))
        // Once the last one airs on a channel, at its end
        assertEquals(t0.plusMinutes(30), guideRefreshAt(guide, t0.plusMinutes(22)))
        assertNull(guideRefreshAt(emptyList(), t0))
    }

    @Test
    fun `the guide window is three hours from now, not 48`() {
        val now = Instant.parse("2026-11-28T18:41:07.123Z")
        val (start, end) = LiveGuideWindow.from(now)
        assertEquals(Instant.parse("2026-11-28T18:41:07Z"), start)
        assertEquals(Duration.ofHours(3), Duration.between(start, end))
    }

    @Test
    fun `programmes are read from the plugin's guide on the TV's clock`() {
        val json =
            """
            {"Items":[{"Id":"b1000000000000000000000000000001","Name":"Hey Duggee","EpisodeTitle":"La insignia del pastel",
                       "IndexNumber":12,"Type":"Program","ChannelId":"7f3c0000000000000000000000000001",
                       "StartDate":"2026-11-28T18:30:00.0000000Z","EndDate":"2026-11-28T18:52:00.0000000Z",
                       "ImageTags":{"Primary":"p","Thumb":"t"}},
                      {"Id":"b1000000000000000000000000000002","Name":"No dates","ChannelId":"7f3c0000000000000000000000000001"}]}
            """.trimIndent()
        val items = KidsJson.decodeFromString(KidsItemsResult.serializer(), json).items
        val duggee = liveProgramme(items[0], ZoneOffset.ofHours(1))!!
        assertEquals("Hey Duggee", duggee.name)
        assertEquals("La insignia del pastel", duggee.episodeTitle)
        assertEquals(12, duggee.episodeNumber)
        assertEquals(peke, duggee.channelId)
        assertEquals(LocalDateTime.of(2026, 11, 28, 19, 30), duggee.start)
        assertEquals(LocalDateTime.of(2026, 11, 28, 19, 52), duggee.end)
        assertEquals("t", duggee.thumbTag)
        assertEquals("p", duggee.primaryTag)
        assertNull(liveProgramme(items[1]))
    }

    @Test
    fun `a home row programme becomes a live card with its still, poster and channel logo`() {
        val dto =
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.PROGRAM,
                name = "Hey Duggee",
                channelId = peke,
                channelName = "PekeTV",
                channelPrimaryImageTag = "logo",
                startDate = t0,
                endDate = t0.plusMinutes(20),
                imageTags = mapOf(ImageType.THUMB to "still", ImageType.PRIMARY to "poster"),
            )
        val card = liveCard(dto)!!
        assertEquals("PekeTV", card.channelName)
        assertEquals("logo", card.logoTag)
        assertEquals("Hey Duggee", card.programme?.name)
        assertEquals("still", card.programme?.thumbTag)
        assertEquals("poster", card.programme?.primaryTag)
        assertNull(liveCard(dto.copy(channelId = null)))

        // A channel with nothing airing: no programme, its own logo
        val channel =
            liveCard(
                BaseItemDto(
                    id = peke,
                    type = BaseItemKind.TV_CHANNEL,
                    name = "PekeTV",
                    imageTags =
                        mapOf(ImageType.PRIMARY to "own"),
                ),
            )!!
        assertNull(channel.programme)
        assertEquals("own", channel.logoTag)
    }

    @Test
    fun `the home row has every channel of the kid, in their order, with nothing airing as a card of its own`() {
        val pekeTv = KidsChannel(peke, "PekeTV", "1", "logo")
        val clasicos = KidsChannel(classics, "Peke Clásicos", "2", null)
        val onClassics = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.PROGRAM, channelId = classics), false)
        val elsewhere = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.PROGRAM, channelId = UUID.randomUUID()), false)

        val row = withKidsChannels(listOf(onClassics, elsewhere), listOf(pekeTv, clasicos))
        assertEquals(3, row.size)
        // PekeTV has no guide: its own card, which plays the channel
        assertEquals(BaseItemKind.TV_CHANNEL, row[0]?.type)
        assertEquals(peke, row[0]?.id)
        assertEquals("logo", row[0]?.data?.imageTags?.get(ImageType.PRIMARY))
        assertEquals(onClassics, row[1])
        assertEquals(elsewhere, row[2])

        // Channels not read yet: the row as the server sent it
        assertEquals(listOf(onClassics), withKidsChannels(listOf(onClassics), emptyList()))

        // A row the server answered empty (a gap, no guide) still shows the channels; other rows are left alone
        val live = HomeRowLoadingState.Success(StringStringProvider("Watch live"), emptyList(), rowType = HomeRowConfig.TvPrograms())
        val other = HomeRowLoadingState.Success(StringStringProvider("Other"), emptyList(), rowType = HomeRowConfig.TvChannels())
        val rows = withKidsLiveChannels(listOf(live, other), listOf(pekeTv))
        assertEquals(listOf(peke), (rows[0] as HomeRowLoadingState.Success).items.map { it?.id })
        assertEquals(other, rows[1])
    }
}
