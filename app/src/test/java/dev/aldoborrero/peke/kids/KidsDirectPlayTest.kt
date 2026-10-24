package dev.aldoborrero.peke.kids

import android.content.Context
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.playback.KidsDirectPlay
import dev.aldoborrero.peke.kids.playback.KidsDirectPlayViewModel
import dev.aldoborrero.peke.kids.playback.KidsSuggestionsViewModel
import dev.aldoborrero.peke.kids.ui.kidsInterfaceCustomization
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.PlaylistCreator
import dev.aldoborrero.peke.test.item
import dev.aldoborrero.peke.ui.detail.series.SeasonEpisodeIds
import dev.aldoborrero.peke.ui.main.clickDestination
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.ui.successResponse
import dev.aldoborrero.peke.ui.util.ResStringProvider
import dev.aldoborrero.peke.ui.util.RestrictedFeature
import dev.aldoborrero.peke.util.HomeRowLoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ExtraType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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
    fun `keep watching items that already go to the player keep their destination`() {
        val extra = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.VIDEO, extraType = ExtraType.BEHIND_THE_SCENES))
        val overridden = BaseItem(item(BaseItemKind.MOVIE), destinationOverride = Destination.Playback(id, 5))
        listOf(extra, overridden).forEach {
            val destination = row(HomeRowConfig.ContinueWatchingCombined()).clickDestination(it, clickToPlay = false, kidsMode = true)
            assertTrue(destination is Destination.Playback)
            assertEquals(it.destination(), destination)
            assertFalse(KidsDirectPlay.handles(destination))
        }
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
    fun `details pages hide item menus, delete and people from kids`() {
        val customization = kidsInterfaceCustomization(KidProfile(userId = 1))
        listOf(RestrictedFeature.ITEM_MENUS, RestrictedFeature.DELETE, RestrictedFeature.PEOPLE).forEach {
            assertFalse(it.name, customization.allows(it))
        }
    }
}

/**
 * Spec 005 FR-003, FR-004: the loading page becomes the player only while it is still on top (C14)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidsDirectPlayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val movie = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE)
    private val destination = Destination.KidsResume(Destination.MediaItem(movie.id, BaseItemKind.MOVIE))
    private val navigationManager = mockk<NavigationManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val userLibraryApi = mockk<UserLibraryApi>()
    private val api = mockk<ApiClient>(relaxed = true)

    @Before
    fun setUp() {
        PekeDispatchers.configure(dispatcher)
        mockkStatic("dev.aldoborrero.peke.ui.ExtensionsKt")
        coEvery { showToast(any(), any<CharSequence>()) } just Runs
        every { api.userLibraryApi } returns userLibraryApi
        every { context.getString(any()) } returns "nothing"
    }

    @After
    fun tearDown() {
        PekeDispatchers.reset()
        unmockkAll()
    }

    private fun viewModel() =
        KidsDirectPlayViewModel(
            context,
            api,
            mockk<ServerRepository>(relaxed = true),
            navigationManager,
            mockk<PlaylistCreator>(relaxed = true),
        )

    @Test
    fun `the player replaces the loading page`() =
        runTest(dispatcher) {
            coEvery { userLibraryApi.getItem(itemId = movie.id, userId = any()) } returns successResponse(movie)
            every { navigationManager.replaceTop(any(), any()) } returns true
            viewModel().play(destination)
            advanceUntilIdle()
            verify { navigationManager.replaceTop(destination, match { it is Destination.Playback && it.itemId == movie.id }) }
            verify(exactly = 0) { navigationManager.goBack() }
            verify(exactly = 0) { navigationManager.navigateTo(any()) }
            coVerify(exactly = 0) { showToast(any(), any<CharSequence>()) }
        }

    @Test
    fun `nothing happens after the kid went back`() =
        runTest(dispatcher) {
            coEvery { userLibraryApi.getItem(itemId = movie.id, userId = any()) } returns successResponse(movie)
            every { navigationManager.replaceTop(any(), any()) } returns false
            viewModel().play(destination)
            advanceUntilIdle()
            verify(exactly = 0) { navigationManager.goBack() }
            verify(exactly = 0) { navigationManager.navigateTo(any()) }
        }

    @Test
    fun `nothing to play is said only on the loading page`() =
        runTest(dispatcher) {
            coEvery { userLibraryApi.getItem(itemId = movie.id, userId = any()) } throws IllegalStateException("gone")
            every { navigationManager.replaceTop(any(), any()) } returns true
            viewModel().play(destination)
            advanceUntilIdle()
            verify { navigationManager.replaceTop(destination, null) }
            coVerify(exactly = 1) { showToast(any(), any<CharSequence>()) }
        }

    @Test
    fun `nothing to play is not said after the kid went back`() =
        runTest(dispatcher) {
            coEvery { userLibraryApi.getItem(itemId = movie.id, userId = any()) } throws IllegalStateException("gone")
            every { navigationManager.replaceTop(any(), any()) } returns false
            viewModel().play(destination)
            advanceUntilIdle()
            coVerify(exactly = 0) { showToast(any(), any<CharSequence>()) }
        }
}
