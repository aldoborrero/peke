package dev.aldoborrero.peke

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.datastore.core.DataStore
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.RestoredSession
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.update
import dev.aldoborrero.peke.services.AppUpgradeHandler
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.DeviceProfileService
import dev.aldoborrero.peke.services.IntentResult
import dev.aldoborrero.peke.services.IntentService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.test.currentUser
import dev.aldoborrero.peke.test.server
import dev.aldoborrero.peke.test.user
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.UUID
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestMainActivityViewModel {
    private val testDispatcher = StandardTestDispatcher()

    private val context: Context = mockk()
    private val preferences: DataStore<AppPreferences> = mockk()
    private val serverRepository: ServerRepository = mockk()
    private val setupNavigationManager: SetupNavigationManager = mockk(relaxed = true)
    private val navigationManager: NavigationManager = mockk(relaxed = true)
    private val deviceProfileService: DeviceProfileService = mockk()
    private val backdropService: BackdropService = mockk(relaxed = true)
    private val appUpgradeHandler: AppUpgradeHandler = mockk()
    private val intentService: IntentService = mockk()
    private val kidsModeService: KidsModeService = mockk { coEvery { isLockedNow() } returns false }

    val viewModel =
        MainActivityViewModel(
            context,
            preferences,
            serverRepository,
            setupNavigationManager,
            navigationManager,
            deviceProfileService,
            backdropService,
            appUpgradeHandler,
            intentService,
            kidsModeService,
        )

    private val serverId = UUID.randomUUID()
    private val server = server(serverId)
    private val userId = UUID.randomUUID()
    private val currentUser = currentUser(serverId, userId)

    private val protectedCurrentUser =
        CurrentUser(
            server(serverId),
            user(serverId, userId).copy(pin = "1234"),
        )

    @Before
    fun setUp() {
        PekeDispatchers.configure(testDispatcher)
        every { appUpgradeHandler.needUpgrade() } returns false
        every { appUpgradeHandler.copySubfont(any()) } returns Unit
        mockkStatic(Toast::class)
        val mockToast = mockk<Toast>()
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockToast
        every { mockToast.show() } returns Unit
    }

    @After
    fun tearDown() {
        PekeDispatchers.reset()
        unmockkStatic(Toast::class)
    }

    private fun setupPreferences(block: AppPreferences.Builder.() -> Unit) {
        every { preferences.data } returns
            flow {
                emit(
                    AppPreferences.getDefaultInstance().update(block),
                )
            }
    }

    @Test
    fun `Test auto sign in with unprotected profile`() =
        runTest(testDispatcher) {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow(currentUser)
//            coEvery { serverRepository.tryRestoreSession(serverId, userId) } returns currentUser
//            coEvery { serverRepository.restoreLastSession() } returns
//                RestoredSession.Success(currentUser)

            viewModel.handleAppStart()

            val slot = slot<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(slot)) }
            slot.captured.let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.AppContent,
                )
                destination as SetupDestination.AppContent
                Assert.assertEquals(currentUser, destination.current)
            }
            coVerify(exactly = 0) { serverRepository.restoreLastSession() }
            coVerify(exactly = 0) { serverRepository.getMostRecentServer() }
        }

    @Test
    fun `Test auto sign disabled in with current server`() =
        runTest {
            setupPreferences {
                signInAutomatically = false
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(currentUser)
//            coEvery { serverRepository.restoreSession(serverId, userId) } returns currentUser
            coEvery { serverRepository.getMostRecentServer() } returns
                RestoredSession.ServerOnly(server)

            viewModel.handleAppStart()

            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
            coVerify(exactly = 0) { serverRepository.restoreLastSession() }
            coVerify(exactly = 1) { serverRepository.getMostRecentServer() }
            val args = mutableListOf<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(args)) }
            Assert.assertEquals(1, args.size)
            args[0].let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.UserList,
                )
                destination as SetupDestination.UserList
                Assert.assertEquals(currentUser.server, destination.server)
            }
        }

    @Test
    fun `Test auto sign in, hot load, with protected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns
                MutableStateFlow<CurrentUser?>(
                    protectedCurrentUser,
                )
            coEvery {
                serverRepository.tryChangeUser(serverId, userId)
            } returns null
            coEvery { serverRepository.restoreLastSession() } returns
                RestoredSession.ServerOnly(server)

            viewModel.handleAppStart()

            coVerify(exactly = 0) { serverRepository.tryChangeUser(serverId, userId) }
            val args = mutableListOf<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(args)) }
            Assert.assertEquals(1, args.size)
            args[0].let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.UserList,
                )
                destination as SetupDestination.UserList
                Assert.assertEquals(currentUser.server, destination.server)
            }
        }

    @Test
    fun `Test auto sign in, cold load, with protected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.restoreLastSession() } returns
                RestoredSession.ServerOnly(server)
            coEvery {
                serverRepository.tryChangeUser(serverId, userId)
            } returns null

            viewModel.handleAppStart()

            coVerify(exactly = 1) { serverRepository.restoreLastSession() }
            val slot = slot<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(slot)) }
            slot.captured.let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.UserList,
                )
                destination as SetupDestination.UserList
                Assert.assertEquals(currentUser.server, destination.server)
            }
        }

    @Test
    fun `Test auto sign in, no current user`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.restoreLastSession() } returns RestoredSession.None

            viewModel.handleAppStart()

            coVerify(exactly = 1) { serverRepository.restoreLastSession() }
            val slot = slot<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(slot)) }
            slot.captured.let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.ServerList,
                )
            }
        }

    @Test
    fun `Test intent result is noop, hot reload`() =
        runTest(testDispatcher) {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow(currentUser)
            coEvery { serverRepository.restoreLastSession() } returns
                RestoredSession.Success(currentUser)
            coEvery { intentService.parseIntent(any()) } returns IntentResult.NoOp

            val result = viewModel.handleIntent(Intent())
            assertFalse(result)

//            val slot = slot<SetupDestination>()
//            verify { setupNavigationManager.navigateTo(capture(slot)) }
//            slot.captured.let { destination ->
//                Assert.assertTrue(
//                    "destination is ${destination::class}",
//                    destination is SetupDestination.AppContent,
//                )
//                destination as SetupDestination.AppContent
//                Assert.assertEquals(currentUser, destination.current)
//            }
            coVerify(exactly = 0) { serverRepository.restoreLastSession() }
            coVerify(exactly = 0) { serverRepository.getMostRecentServer() }
        }

    @Test
    fun `Test intent result is error, has server`() =
        runTest(testDispatcher) {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow(currentUser)
            coEvery { intentService.parseIntent(any()) } returns IntentResult.Error("Error")

            val result = viewModel.handleIntent(Intent())
            assertTrue(result)

            val slot = slot<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(slot)) }
            slot.captured.let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.UserList,
                )
                destination as SetupDestination.UserList
                Assert.assertEquals(currentUser.server, destination.server)
            }
        }

    @Test
    fun `Test intent result is error, does not have server`() =
        runTest(testDispatcher) {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow(null)
            coEvery { intentService.parseIntent(any()) } returns IntentResult.Error("Error")

            val result = viewModel.handleIntent(Intent())
            assertTrue(result)

            val slot = slot<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(slot)) }
            slot.captured.let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.ServerList,
                )
            }
        }

    @Test
    fun `Test intent result is target, has server`() =
        runTest(testDispatcher) {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow(currentUser)
//            coEvery { serverRepository.tryRestoreSession(serverId, userId) } returns currentUser
            coEvery { intentService.parseIntent(any()) } returns
                IntentResult.Target(
                    destinations = listOf(Destination.Favorites),
                    addHomeToBackStack = true,
                )
            val backstack = mutableListOf<Destination>()
            every { navigationManager.backStack } returns backstack

            val result = viewModel.handleIntent(Intent())
            assertTrue(result)

            val slot = slot<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(slot)) }
            verify { navigationManager.reloadHome() }
            slot.captured.let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.AppContent,
                )
                destination as SetupDestination.AppContent
                Assert.assertEquals(currentUser, destination.current)
            }
            // Note: navigation manager internally handles Home destination, so it won't be in this list
            Assert.assertEquals(1, backstack.size)
            backstack[0].let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is Destination.Favorites,
                )
            }
        }

    @Test
    fun `Test intent result is target, has server, no home backstack`() =
        runTest(testDispatcher) {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow(currentUser)
//            coEvery { serverRepository.tryRestoreSession(serverId, userId) } returns currentUser
            coEvery { intentService.parseIntent(any()) } returns
                IntentResult.Target(
                    destinations = listOf(Destination.Favorites),
                    addHomeToBackStack = false,
                )
            val backstack = mutableListOf<Destination>()
            every { navigationManager.backStack } returns backstack

            val result = viewModel.handleIntent(Intent())
            assertTrue(result)

            val slot = slot<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(slot)) }
            verify(exactly = 0) { navigationManager.reloadHome() }
            slot.captured.let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.AppContent,
                )
                destination as SetupDestination.AppContent
                Assert.assertEquals(currentUser, destination.current)
            }
            Assert.assertEquals(1, backstack.size)
            backstack[0].let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is Destination.Favorites,
                )
            }
        }

    @Test
    fun `Test intent result is target, has no server`() =
        runTest(testDispatcher) {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow(null)
//            coEvery { serverRepository.tryRestoreSession(serverId, userId) } returns currentUser
            coEvery { intentService.parseIntent(any()) } returns
                IntentResult.Target(
                    destinations = listOf(Destination.Favorites),
                    addHomeToBackStack = true,
                )
            val backstack = mutableListOf<Destination>()
            every { navigationManager.backStack } returns backstack

            val result = viewModel.handleIntent(Intent())
            assertTrue(result)

            val slot = slot<SetupDestination>()
            verify { setupNavigationManager.navigateTo(capture(slot)) }
            verify(exactly = 0) { navigationManager.reloadHome() }
            slot.captured.let { destination ->
                Assert.assertTrue(
                    "destination is ${destination::class}",
                    destination is SetupDestination.ServerList,
                )
            }
        }
}
