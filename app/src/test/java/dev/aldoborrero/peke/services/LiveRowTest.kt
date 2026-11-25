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
        // Ended already (the server is behind): try again a little later, not in a busy loop
        assertEquals(LiveRow.RETRY, LiveRow.nextRefresh(listOf(programme(now.minusMinutes(30), now)), now))
        // Nothing to wait for
        assertNull(LiveRow.nextRefresh(emptyList(), now))
        assertNull(LiveRow.nextRefresh(listOf(programme(now, null)), now))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the timer reads the row at each end time, and stops when told`() =
        runTest {
            val clock = { now.plus(currentTime.milliseconds.toJavaDuration()) }
            val first = programme(now.minusMinutes(29), now.plusSeconds(30))
            val next = programme(now.plusSeconds(30), now.plusMinutes(30).plusSeconds(30))
            val reads = mutableListOf<Long>()
            LiveRow.refreshAtEnds(listOf(first), clock) {
                reads += currentTime
                when (reads.size) {
                    1 -> listOf(next)
                    else -> null // the home was left
                }
            }
            assertEquals(
                listOf(
                    (30.seconds + LiveRow.GRACE).inWholeMilliseconds,
                    (30.seconds + 30.minutes + LiveRow.GRACE).inWholeMilliseconds,
                ),
                reads,
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
