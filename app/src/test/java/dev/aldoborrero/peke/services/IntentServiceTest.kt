package dev.aldoborrero.peke.services

import android.app.SearchManager
import android.content.Intent
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.test.assertIs
import dev.aldoborrero.peke.test.movie
import dev.aldoborrero.peke.test.server
import dev.aldoborrero.peke.test.user
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.successResponse
import dev.aldoborrero.peke.ui.toServerString
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class IntentServiceTest {
    private val api: ApiClient = mockk()
    private val serverRepository: ServerRepository = mockk()
    private val userLibraryApi: UserLibraryApi = mockk()

    lateinit var intentService: IntentService

    private val serverId = UUID.randomUUID()
    private val server = server(serverId)
    private val kid = user(serverId).copy(rowId = 1, name = "kid")
    private val sibling = user(serverId).copy(rowId = 2, name = "sibling")
    private val currentUser = CurrentUser(server, kid)
    private val itemId = UUID.randomUUID()

    private val current = MutableStateFlow<CurrentUser?>(currentUser)
    private val tvState = MutableStateFlow<TvState>(TvState.SetUp(server, listOf(kid, sibling), lastKidRowId = kid.rowId))
    private val tvSetupRepository = mockk<TvSetupRepository> { every { state } returns tvState }
    private var profile = KidProfile(userId = kid.rowId, serverId = serverId, searchAllowed = true)
    private val kidsModeService = mockk<KidsModeService> { coEvery { profileFor(any()) } answers { profile } }

    @Before
    fun setup() {
        intentService = IntentService(api, serverRepository, tvSetupRepository, kidsModeService)
        every { api.userLibraryApi } returns userLibraryApi
        every { serverRepository.current } returns current
        coEvery { userLibraryApi.getItem(itemId) } returns successResponse(movie(itemId))
    }

    @Test
    fun `an intent runs as the current kid`() =
        runTest {
            assertNull(intentService.prepare())
            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
            coVerify(exactly = 0) { serverRepository.restoreLastSession() }
        }

    @Test
    fun `with no current user an intent is dropped`() =
        runTest {
            current.value = null

            assertIs<IntentResult.Error>(intentService.prepare())
            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
            coVerify(exactly = 0) { serverRepository.restoreLastSession() }
        }

    @Test
    fun `a current user who isn't one of this TV's kids drops the intent`() =
        runTest {
            current.value = CurrentUser(server, user(serverId).copy(rowId = 9, name = "grown-up"))
            assertIs<IntentResult.Error>(intentService.prepare())

            // Nor a kid whose stored session changed, or a TV that isn't set up
            current.value = CurrentUser(server, kid.copy(accessToken = "old"))
            assertIs<IntentResult.Error>(intentService.prepare())
            current.value = currentUser
            tvState.value = TvState.Loading
            assertIs<IntentResult.Error>(intentService.prepare())
        }

    @Test
    fun `user ids in an intent are ignored`() =
        runTest {
            val intent =
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    putExtra("serverId", serverId.toServerString())
                    putExtra("userId", sibling.id.toServerString())
                    putExtra("itemId", itemId.toString())
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)
            assertEquals(1, result.destinations.size)
            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
            coVerify(exactly = 0) { serverRepository.changeUser(any(), any()) }
            assertEquals(currentUser, current.value)

            // Also without a current kid: dropped, never a switch to the user it names
            current.value = null
            assertIs<IntentResult.Error>(intentService.parseIntent(intent))
            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
        }

    @Test
    fun `search is dropped for a kid without search`() =
        runTest {
            profile = profile.copy(searchAllowed = false)
            val intent = Intent().apply { action = Intent.ACTION_SEARCH }

            assertIs<IntentResult.Error>(intentService.parseIntent(intent))
        }

    @Test
    fun `Test parseIntent with view item`() =
        runTest {
            val intent =
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    putExtra("itemId", itemId.toString())
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(1, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            assertIs<Destination.MediaItem>(first)

            assertEquals(itemId, first.itemId)

            coVerify { userLibraryApi.getItem(itemId) }
        }

    @Test
    fun `Test parseIntent with view and no itemId`() =
        runTest {
            val intent =
                Intent().apply {
                    action = Intent.ACTION_VIEW
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(0, destinations.size)
            assertTrue(result.addHomeToBackStack)

            coVerify(exactly = 0) { userLibraryApi.getItem(itemId) }
        }

    @Test
    fun `Test parseIntent with search`() =
        runTest {
            val intent =
                Intent().apply {
                    action = Intent.ACTION_SEARCH
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(1, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            assertIs<Destination.Search>(first)

            assertEquals("", first.query)
        }

    @Test
    fun `Test parseIntent with search query`() =
        runTest {
            val query = "query123"
            val intent =
                Intent().apply {
                    action = Intent.ACTION_SEARCH
                    putExtra(SearchManager.QUERY, query)
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(1, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            assertIs<Destination.Search>(first)

            assertEquals(query, first.query)
        }

    @Test
    fun `Test parseIntent with play but no itemId`() =
        runTest {
            val intent =
                Intent().apply {
                    action = IntentService.ACTION_PLAYBACK
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Error>(result)
        }

    @Test
    fun `Test parseIntent with play itemId`() =
        runTest {
            val intent =
                Intent().apply {
                    action = IntentService.ACTION_PLAYBACK
                    putExtra("itemId", itemId.toString())
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(2, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            val second = destinations[1]
            assertIs<Destination.MediaItem>(first)
            assertIs<Destination.Playback>(second)

            assertEquals(itemId, first.itemId)
            assertEquals(itemId, second.itemId)

            coVerify { userLibraryApi.getItem(itemId) }
        }

    @Test
    fun `Test parseIntent with play itemId and params`() =
        runTest {
            val position = 4_000L
            val shuffle = true
            val intent =
                Intent().apply {
                    action = IntentService.ACTION_PLAYBACK
                    putExtra("itemId", itemId.toString())
                    putExtra("position", position)
                    putExtra("shuffle", shuffle)
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(2, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            val second = destinations[1]
            assertIs<Destination.MediaItem>(first)
            assertIs<Destination.Playback>(second)

            assertEquals(itemId, first.itemId)
            assertEquals(itemId, second.itemId)
            assertEquals(shuffle, second.shuffle)
            assertEquals(position, second.positionMs)

            coVerify { userLibraryApi.getItem(itemId) }
        }
}
