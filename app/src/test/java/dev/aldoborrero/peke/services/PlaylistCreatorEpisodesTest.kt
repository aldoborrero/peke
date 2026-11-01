package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.data.model.EpisodesContinuation
import dev.aldoborrero.peke.data.model.Playlist
import dev.aldoborrero.peke.ui.successResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.operations.TvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * An in-order queue of episodes goes on window by window ([PlaylistCreator.continueEpisodes])
 */
class PlaylistCreatorEpisodesTest {
    private val seriesId = UUID.randomUUID()
    private val tvShowsApi = mockk<TvShowsApi>()
    private val api = mockk<ApiClient> { every { tvShowsApi } returns this@PlaylistCreatorEpisodesTest.tvShowsApi }
    private val creator = PlaylistCreator(mockk(relaxed = true), api, mockk(relaxed = true))

    private fun episode(locationType: LocationType = LocationType.FILE_SYSTEM) =
        BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.EPISODE, seriesId = seriesId, locationType = locationType)

    /** The series' episodes, answered as the server does: from startItemId, at most limit */
    private fun serve(episodes: List<BaseItemDto>) {
        coEvery { tvShowsApi.getEpisodes(any<GetEpisodesRequest>()) } answers {
            val request = firstArg<GetEpisodesRequest>()
            val from = episodes.indexOfFirst { it.id == request.startItemId }.coerceAtLeast(0)
            val items = episodes.drop(from).take(request.limit ?: Int.MAX_VALUE)
            successResponse(BaseItemDtoQueryResult(items = items, totalRecordCount = items.size, startIndex = 0))
        }
    }

    @Test
    fun `a window of only missing episodes goes on to the next`() =
        runTest {
            val last = episode()
            val missing = List(Playlist.EPISODES_WINDOW) { episode(LocationType.VIRTUAL) }
            val after = List(3) { episode() }
            serve(listOf(last) + missing + after)

            val window = creator.continueEpisodes(EpisodesContinuation(seriesId, null, last.id))

            assertEquals(after.map { it.id }, window.items.map { it.id })
            assertNull(window.continuation)
        }

    @Test
    fun `the end of the series ends the queue`() =
        runTest {
            val last = episode()
            serve(listOf(last) + List(4) { episode(LocationType.VIRTUAL) })

            val window = creator.continueEpisodes(EpisodesContinuation(seriesId, null, last.id))

            assertEquals(emptyList<UUID>(), window.items.map { it.id })
            assertNull(window.continuation)
        }
}
