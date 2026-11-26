package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.home.ChannelPicture
import dev.aldoborrero.peke.kids.home.channelImageUrl
import dev.aldoborrero.peke.kids.home.channelPicture
import dev.aldoborrero.peke.ui.main.HomeState
import dev.aldoborrero.peke.ui.main.isRefreshedOnReturn
import dev.aldoborrero.peke.ui.main.liveProgrammes
import dev.aldoborrero.peke.util.HomeRowLoadingState
import dev.aldoborrero.peke.util.LoadingState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRowTest {
    private val now = LocalDateTime.of(2026, 11, 25, 19, 59, 30)
    private val userId = UUID.randomUUID()

    private fun programme(
        start: LocalDateTime,
        end: LocalDateTime?,
    ) = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.PROGRAM,
        startDate = start,
        endDate = end,
    )

    /** What Jellyfin lists for [request] at [at]: airing (started, not ended) and ending at or after the min end */
    private fun GetProgramsDto.lists(
        item: BaseItemDto,
        at: LocalDateTime,
    ): Boolean {
        val start = item.startDate!!
        val end = item.endDate!!
        val airing = !start.isAfter(at) && end.isAfter(at)
        return (isAiring != true || airing) && (minEndDate?.let { !end.isBefore(it) } ?: true)
    }

    @Test
    fun `a programme ending in 30 s is still in the row`() {
        val request = LiveRow.request(userId, 10)
        val ending = programme(now.minusMinutes(29), now.plusSeconds(30))
        assertTrue(request.lists(ending, now))
        assertNull(request.minEndDate)
        assertEquals(true, request.isAiring)
    }

    @Test
    fun `at a boundary with no gap the row shows the next programme, never nothing`() {
        val request = LiveRow.request(userId, 10)
        val boundary = now.plusSeconds(30)
        val first = programme(now.minusMinutes(29), boundary)
        val next = programme(boundary, boundary.plusMinutes(30))
        val programmes = listOf(first, next)

        // Every second from the last minute of the first to the start of the next lists one of them
        for (second in 0L..90L) {
            val at = now.plusSeconds(second)
            assertTrue("empty at +$second s", programmes.any { request.lists(it, at) })
        }
        assertEquals(listOf(first), programmes.filter { request.lists(it, boundary.minusNanos(1)) })
        assertEquals(listOf(next), programmes.filter { request.lists(it, boundary) })
    }

    @Test
    fun `the row is read again just after the first programme shown ends`() {
        val ending = programme(now.minusMinutes(29), now.plusSeconds(30))
        val later = programme(now.minusMinutes(10), now.plusMinutes(20))
        assertEquals(30.seconds + LiveRow.GRACE, LiveRow.nextRefresh(listOf(later, ending), now))
        // One shown has ended already (the TV is ahead of the server): two quick retries, then every minute
        val ended = listOf(later, programme(now.minusMinutes(30), now))
        assertEquals(15.seconds, LiveRow.nextRefresh(ended, now, retries = 0))
        assertEquals(15.seconds, LiveRow.nextRefresh(ended, now, retries = 1))
        assertEquals(LiveRow.RETRY, LiveRow.nextRefresh(ended, now, retries = 2))
        // Nothing to wait for
        assertNull(LiveRow.nextRefresh(emptyList(), now))
        assertNull(LiveRow.nextRefresh(listOf(programme(now, null)), now))
        // After a gap, the next start
        assertEquals(10.minutes + LiveRow.GRACE, LiveRow.untilStart(now.plusMinutes(10), now))
        assertEquals(LiveRow.RETRY, LiveRow.untilStart(now.minusSeconds(5), now))
        assertNull(LiveRow.untilStart(null, now))
    }

    @Test
    fun `the next start asks for one programme from now, earliest first`() {
        val request = LiveRow.nextStartRequest(userId, now)
        assertEquals(now, request.minStartDate)
        assertEquals(1, request.limit)
        assertEquals(listOf(ItemSortBy.START_DATE), request.sortBy)
        assertEquals(listOf(SortOrder.ASCENDING), request.sortOrder)
    }

    /** Runs the timer from [now] on virtual time, answering its reads with [answers] in turn and then stopping */
    private fun timer(
        shown: List<BaseItemDto>,
        nextStart: LocalDateTime? = null,
        vararg answers: LiveRow.Read,
    ): List<String> {
        val log = mutableListOf<String>()
        runTest {
            val clock = { now.plus(currentTime.milliseconds.toJavaDuration()) }
            var reads = 0
            LiveRow.refreshAtEnds(
                shown = shown,
                nextStart = nextStart,
                now = clock,
                read = {
                    log += "read at ${currentTime / 1000}s"
                    answers.getOrNull(reads++)
                },
                show = { log += "show ${it.programmes.size}" },
                hide = { log += "hide, next at $it" },
            )
        }
        return log
    }

    @Test
    fun `the timer reads the row at each end time, and stops when told`() {
        val first = programme(now.minusMinutes(29), now.plusSeconds(30))
        val next = programme(now.plusSeconds(30), now.plusMinutes(30).plusSeconds(30))
        assertEquals(
            listOf("read at 32s", "show 1", "read at ${30 * 60 + 32}s"),
            timer(listOf(first), null, LiveRow.Read.Airing(listOf(next))),
        )
    }

    @Test
    fun `a failed read keeps the row and retries after a minute`() {
        val first = programme(now.minusMinutes(29), now.plusSeconds(30))
        val next = programme(now.plusSeconds(30), now.plusMinutes(30))
        assertEquals(
            listOf("read at 32s", "read at 92s", "show 1", "read at ${30 * 60 + 2}s"),
            timer(listOf(first), null, LiveRow.Read.Failed, LiveRow.Read.Airing(listOf(next))),
        )
    }

    @Test
    fun `the same ended programme from the server is retried at 15 s, 15 s, then a minute`() {
        val first = programme(now.minusMinutes(29), now.plusSeconds(30))
        val stale = LiveRow.Read.Airing(listOf(first))
        assertEquals(
            listOf("read at 32s", "read at 47s", "read at 62s", "read at 122s"),
            timer(listOf(first), null, stale, stale, stale),
        )
    }

    @Test
    fun `a gap keeps the row while a programme on it airs, then hides it until the next start`() {
        val ending = programme(now.minusMinutes(29), now.plusSeconds(30))
        val later = programme(now.minusMinutes(10), now.plusMinutes(5))
        val nextStart = now.plusHours(1)
        assertEquals(
            listOf("read at 32s", "read at ${5 * 60 + 2}s", "hide, next at $nextStart", "read at ${60 * 60 + 2}s"),
            timer(listOf(ending, later), null, LiveRow.Read.Gap(null), LiveRow.Read.Gap(nextStart)),
        )
    }

    @Test
    fun `a hidden row comes back at the next start`() {
        val next = programme(now.plusMinutes(10), now.plusMinutes(40))
        assertEquals(
            listOf("read at ${10 * 60 + 2}s", "show 1", "read at ${40 * 60 + 2}s"),
            timer(emptyList(), now.plusMinutes(10), LiveRow.Read.Airing(listOf(next))),
        )
    }

    @Test
    fun `the live row is read again on a return to the home`() {
        assertTrue(isRefreshedOnReturn(HomeRowConfig.TvPrograms()))
        assertTrue(isRefreshedOnReturn(HomeRowConfig.NextUp()))
        assertFalse(isRefreshedOnReturn(HomeRowConfig.TvChannels()))
    }

    @Test
    fun `the timer watches only the live row's programmes`() {
        val live = programme(now.minusMinutes(5), now.plusMinutes(5))
        val other = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.EPISODE)
        val state =
            HomeState(
                LoadingState.Success,
                LoadingState.Success,
                listOf(
                    HomeRowLoadingState.Success(
                        title = mockk(),
                        items = listOf(BaseItem(other, false)),
                        rowType = HomeRowConfig.NextUp(),
                    ),
                    HomeRowLoadingState.Success(
                        title = mockk(),
                        items = listOf(BaseItem(live, false), null),
                        rowType = HomeRowConfig.TvPrograms(),
                    ),
                ),
                HomePageResolvedSettings.EMPTY,
            )
        assertEquals(listOf(live), liveProgrammes(state))
    }

    @Test
    fun `programmes ask for their channel's logo`() {
        assertTrue(ItemFields.CHANNEL_IMAGE in LiveRow.request(userId, 10).fields.orEmpty())
    }

    @Test
    fun `the card's picture is the channel's logo by its tag, or none`() {
        val api = mockk<ApiClient>()
        every { api.baseUrl } returns "http://localhost"
        every { api.createUrl(any(), any(), any(), any()) } answers { callOriginal() }
        every { api.imageApi } returns ImageApi(api)
        val service = ImageUrlService(api)

        val channel = UUID.randomUUID()
        val programme =
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.PROGRAM,
                channelId = channel,
                channelPrimaryImageTag = "logo",
            )
        val url = service.channelImageUrl(channelPicture(programme)!!)!!
        assertTrue(url, url.contains("/Items/$channel/Images/Primary"))
        assertTrue(url, url.contains("tag=logo"))
        assertNull(service.channelImageUrl(ChannelPicture(channel, null)))
    }
}
