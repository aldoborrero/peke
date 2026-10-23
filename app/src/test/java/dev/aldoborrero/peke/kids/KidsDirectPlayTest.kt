package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.playback.KidsDirectPlay
import dev.aldoborrero.peke.kids.playback.KidsSuggestionsViewModel
import dev.aldoborrero.peke.kids.ui.kidsInterfaceCustomization
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.test.item
import dev.aldoborrero.peke.ui.detail.series.SeasonEpisodeIds
import dev.aldoborrero.peke.ui.main.clickDestination
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.util.ResStringProvider
import dev.aldoborrero.peke.ui.util.RestrictedFeature
import dev.aldoborrero.peke.util.HomeRowLoadingState
import io.mockk.mockk
import io.mockk.verifyOrder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KidsDirectPlayTest {
    private val id = UUID.randomUUID()
    private val movie = BaseItem(item(BaseItemKind.MOVIE))
    private val series = BaseItem(item(BaseItemKind.SERIES))
    private val episode =
        BaseItem(
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                seriesId = UUID.randomUUID(),
                seasonId = UUID.randomUUID(),
                parentIndexNumber = 1,
                indexNumber = 2,
            ),
        )

    private fun row(config: HomeRowConfig) = HomeRowLoadingState.Success(ResStringProvider(0), emptyList(), rowType = config)

    @Test
    fun `keep watching plays directly`() {
        listOf(movie, series, episode).forEach {
            val destination = row(HomeRowConfig.ContinueWatchingCombined()).clickDestination(it, clickToPlay = false, kidsMode = true)
            assertEquals(Destination.KidsResume(it.destination()), destination)
            assertTrue(it.type.name, KidsDirectPlay.handles(destination))
        }
        // An episode still resumes itself, not its series' next up
        assertEquals(
            episode.id,
            ((KidsDirectPlay.resume(episode) as Destination.KidsResume).target as Destination.SeriesOverview).seasonEpisode?.episodeId,
        )
    }

    @Test
    fun `the same items from any other row open their page`() {
        val rows =
            listOf(
                row(HomeRowConfig.ByParent(parentId = id)),
                row(HomeRowConfig.RecentlyAdded(parentId = id)),
                row(HomeRowConfig.Favorite(kind = BaseItemKind.SERIES)),
                row(HomeRowConfig.TvPrograms()),
                null,
            )
        rows.forEach { row ->
            listOf(movie, series, episode).forEach {
                val destination = row.clickDestination(it, clickToPlay = true, kidsMode = true)
                assertEquals(it.destination(), destination)
                assertFalse("${row?.rowType} ${it.type}", KidsDirectPlay.handles(destination))
            }
        }
    }

    @Test
    fun `videos and series opened from anywhere else get their page`() {
        listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO, BaseItemKind.MUSIC_VIDEO, BaseItemKind.SERIES).forEach {
            assertFalse(it.name, KidsDirectPlay.handles(Destination.MediaItem(id, it)))
        }
        assertFalse(KidsDirectPlay.handles(Destination.SeriesOverview(id, BaseItemKind.SERIES)))
        assertFalse(
            KidsDirectPlay.handles(Destination.SeriesOverview(id, BaseItemKind.SERIES, SeasonEpisodeIds(id, 1, id, 1))),
        )
    }

    @Test
    fun `grown-ups keep the home's click to play setting`() {
        val keepWatching = row(HomeRowConfig.ContinueWatchingCombined())
        assertEquals(Destination.Playback(movie), keepWatching.clickDestination(movie, clickToPlay = true, kidsMode = false))
        assertEquals(movie.destination(), keepWatching.clickDestination(movie, clickToPlay = false, kidsMode = false))
    }

    @Test
    fun `collections and other pages open as usual`() {
        listOf(BaseItemKind.BOX_SET, BaseItemKind.PLAYLIST, BaseItemKind.FOLDER).forEach {
            assertFalse(it.name, KidsDirectPlay.handles(Destination.MediaItem(id, it)))
        }
        assertFalse(KidsDirectPlay.handles(Destination.Home()))
        assertFalse(KidsDirectPlay.handles(Destination.Playback(id, 0)))
    }

    @Test
    fun `people, music and photos are refused from anywhere`() {
        listOf(BaseItemKind.PERSON, BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.PHOTO, BaseItemKind.PHOTO_ALBUM).forEach {
            assertTrue(it.name, KidsDirectPlay.handles(Destination.MediaItem(id, it)))
            assertTrue(it.name, KidsDirectPlay.handles(Destination.KidsResume(Destination.MediaItem(id, it))))
        }
    }

    @Test
    fun `a suggestion in the player opens its page instead of playing`() {
        val navigationManager = mockk<NavigationManager>(relaxed = true)
        val viewModel = KidsSuggestionsViewModel(mockk(relaxed = true), mockk(relaxed = true), navigationManager)
        listOf(movie, series, episode).forEach {
            viewModel.open(it)
            verifyOrder {
                navigationManager.goBack()
                navigationManager.navigateTo(it.destination())
            }
            assertFalse(it.type.name, KidsDirectPlay.handles(it.destination()))
        }
    }

    @Test
    fun `details pages hide item menus and links to other apps from kids`() {
        val customization = kidsInterfaceCustomization(KidProfile(userId = 1))
        listOf(RestrictedFeature.ITEM_MENUS, RestrictedFeature.DELETE, RestrictedFeature.EXTERNAL_APPS, RestrictedFeature.PEOPLE).forEach {
            assertFalse(it.name, customization.allows(it))
        }
    }
}
