package dev.aldoborrero.peke

import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.setup.TvLauncher
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvSetupStep
import dev.aldoborrero.peke.kids.setup.TvState
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
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val serverRepository: ServerRepository = mockk()
    private val setupNavigationManager: SetupNavigationManager = mockk(relaxed = true)
    private val navigationManager: NavigationManager = mockk(relaxed = true)
    private val deviceProfileService: DeviceProfileService = mockk()
    private val backdropService: BackdropService = mockk(relaxed = true)
    private val appUpgradeHandler: AppUpgradeHandler = mockk()
    private val intentService: IntentService = mockk()
    private val tvState = MutableStateFlow<TvState>(TvState.Loading)
    private val tvSetupRepository: TvSetupRepository = mockk { every { state } returns tvState }
    private val tvLauncher =
        TvLauncher(tvSetupRepository, serverRepository, setupNavigationManager, navigationManager, CoroutineScope(testDispatcher))

    val viewModel =
        MainActivityViewModel(
            context,
            serverRepository,
            setupNavigationManager,
            navigationManager,
            deviceProfileService,
            backdropService,
            appUpgradeHandler,
            intentService,
            tvSetupRepository,
            tvLauncher,
        )

    private val serverId = UUID.randomUUID()
    private val server = server(serverId)
    private val userId = UUID.randomUUID()
    private val currentUser = currentUser(serverId, userId)

    private val kid = user(serverId).copy(rowId = 1, name = "kid")
    private val sibling = user(serverId).copy(rowId = 2, name = "sibling")
    private val grownUp = user(serverId).copy(rowId = 3, name = "grown-up")

    @Before
    fun setUp() {
        PekeDispatchers.configure(testDispatcher)
        every { appUpgradeHandler.needUpgrade() } returns false
        every { appUpgradeHandler.copySubfont(any()) } returns Unit
        mockkStatic(Toast::class)
        val mockToast = mockk<Toast>()
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockToast
        every { mockToast.show() } returns Unit
        every { serverRepository.current } returns MutableStateFlow(null)
        coEvery { serverRepository.restoreUser(any(), any()) } answers { CurrentUser(firstArg(), secondArg()) }
        coEvery { serverRepository.updateUserDto() } just Runs
        every { setupNavigationManager.backStack } returns mutableListOf(SetupDestination.Loading)
    }

    @After
    fun tearDown() {
        PekeDispatchers.reset()
        unmockkStatic(Toast::class)
    }

    private fun navigatedTo(): SetupDestination {
        val args = mutableListOf<SetupDestination>()
        verify { setupNavigationManager.navigateTo(capture(args)) }
        Assert.assertEquals(1, args.size)
        return args[0]
    }

    @Test
    fun `a TV with no server starts at server setup`() =
        runTest(testDispatcher) {
            tvState.value = TvState.NotSetUp

            viewModel.handleAppStart()

            Assert.assertEquals(SetupDestination.Setup(TvSetupStep.SERVER), navigatedTo())
        }

    @Test
    fun `a TV in setup resumes setup at its step`() =
        runTest(testDispatcher) {
            tvState.value = TvState.InSetup(TvSetupStep.KIDS)

            viewModel.handleAppStart()

            Assert.assertEquals(SetupDestination.Setup(TvSetupStep.KIDS), navigatedTo())
        }

    @Test
    fun `a setup in progress keeps its screen on a warm start`() =
        runTest(testDispatcher) {
            every { setupNavigationManager.backStack } returns mutableListOf(SetupDestination.Setup(TvSetupStep.SERVER))
            tvState.value = TvState.InSetup(TvSetupStep.KIDS)

            viewModel.handleAppStart()

            verify(exactly = 0) { setupNavigationManager.navigateTo(any()) }
        }

    @Test
    fun `the only kid opens from the stored row, refreshed in the background`() =
        runTest(testDispatcher) {
            tvState.value = TvState.SetUp(server, listOf(kid))

            viewModel.handleAppStart()

            Assert.assertEquals(SetupDestination.AppContent(CurrentUser(server, kid)), navigatedTo())
            // No network before the kid shows: the saved session isn't restored through the server
            coVerify(exactly = 0) { serverRepository.restoreLastSession() }
            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
            coVerify(exactly = 0) { serverRepository.changeUser(any(), any()) }
            advanceUntilIdle()
            coVerify(exactly = 1) { serverRepository.updateUserDto() }
        }

    @Test
    fun `a refresh that fails leaves the kid open`() =
        runTest(testDispatcher) {
            tvState.value = TvState.SetUp(server, listOf(kid))
            coEvery { serverRepository.updateUserDto() } throws IllegalStateException("offline")

            viewModel.handleAppStart()
            advanceUntilIdle()

            Assert.assertEquals(SetupDestination.AppContent(CurrentUser(server, kid)), navigatedTo())
        }

    @Test
    fun `a warm start with the only kid current keeps their session`() =
        runTest(testDispatcher) {
            val current = CurrentUser(server, kid)
            every { serverRepository.current } returns MutableStateFlow(current)
            tvState.value = TvState.SetUp(server, listOf(kid))

            viewModel.handleAppStart()

            Assert.assertEquals(SetupDestination.AppContent(current), navigatedTo())
            coVerify(exactly = 0) { serverRepository.restoreUser(any(), any()) }
        }

    @Test
    fun `a warm start as a grown-up goes back to the only kid`() =
        runTest(testDispatcher) {
            every { serverRepository.current } returns MutableStateFlow(CurrentUser(server, grownUp))
            tvState.value = TvState.SetUp(server, listOf(kid))

            viewModel.handleAppStart()

            Assert.assertEquals(SetupDestination.AppContent(CurrentUser(server, kid)), navigatedTo())
        }

    @Test
    fun `the only kid without a usable session shows on the picker as needing a grown-up`() =
        runTest(testDispatcher) {
            tvState.value = TvState.SetUp(server, listOf(kid.copy(accessToken = null)))

            viewModel.handleAppStart()

            Assert.assertEquals(SetupDestination.KidsPicker, navigatedTo())
            coVerify(exactly = 0) { serverRepository.restoreUser(any(), any()) }
        }

    @Test
    fun `several kids go to the picker, also on a warm start`() =
        runTest(testDispatcher) {
            every { serverRepository.current } returns MutableStateFlow(CurrentUser(server, kid))
            tvState.value = TvState.SetUp(server, listOf(kid, sibling), lastKidRowId = kid.rowId)

            viewModel.handleAppStart()

            Assert.assertEquals(SetupDestination.KidsPicker, navigatedTo())
            coVerify(exactly = 0) { serverRepository.restoreUser(any(), any()) }
        }

    @Test
    fun `a TV with a server and no kids says so`() =
        runTest(testDispatcher) {
            tvState.value = TvState.SetUp(server, emptyList())

            viewModel.handleAppStart()

            Assert.assertEquals(SetupDestination.NoKids, navigatedTo())
        }

    @Test
    fun `a normal launch isn't handled as an intent`() =
        runTest(testDispatcher) {
            every { serverRepository.current } returns MutableStateFlow(currentUser)
            coEvery { intentService.parseIntent(any()) } returns IntentResult.NoOp

            assertFalse(viewModel.handleIntent(Intent()))
            verify(exactly = 0) { setupNavigationManager.navigateTo(any()) }
        }

    @Test
    fun `an intent that fails is dropped and the launch rule runs, never a user or server list`() =
        runTest(testDispatcher) {
            every { serverRepository.current } returns MutableStateFlow(currentUser)
            coEvery { intentService.parseIntent(any()) } returns IntentResult.Error("Error")

            // Not handled, so the app starts as usual
            assertFalse(viewModel.handleIntent(Intent()))
            verify(exactly = 0) { setupNavigationManager.navigateTo(any()) }

            every { serverRepository.current } returns MutableStateFlow(null)
            assertFalse(viewModel.handleIntent(Intent()))
            verify(exactly = 0) { setupNavigationManager.navigateTo(any()) }
        }

    @Test
    fun `an intent opens its pages as the current kid`() =
        runTest(testDispatcher) {
            val current = CurrentUser(server, kid)
            every { serverRepository.current } returns MutableStateFlow(current)
            coEvery { intentService.parseIntent(any()) } returns
                IntentResult.Target(
                    destinations = listOf(Destination.Favorites),
                    addHomeToBackStack = true,
                )
            val backstack = mutableListOf<Destination>()
            every { navigationManager.backStack } returns backstack

            assertTrue(viewModel.handleIntent(Intent()))

            Assert.assertEquals(SetupDestination.AppContent(current), navigatedTo())
            verify { navigationManager.reloadHome() }
            // Note: navigation manager internally handles Home destination, so it won't be in this list
            Assert.assertEquals(listOf(Destination.Favorites), backstack)
        }

    @Test
    fun `an intent without home on the back stack opens only its pages`() =
        runTest(testDispatcher) {
            val current = CurrentUser(server, kid)
            every { serverRepository.current } returns MutableStateFlow(current)
            coEvery { intentService.parseIntent(any()) } returns
                IntentResult.Target(
                    destinations = listOf(Destination.Favorites),
                    addHomeToBackStack = false,
                )
            val backstack = mutableListOf<Destination>()
            every { navigationManager.backStack } returns backstack

            assertTrue(viewModel.handleIntent(Intent()))

            Assert.assertEquals(SetupDestination.AppContent(current), navigatedTo())
            verify(exactly = 0) { navigationManager.reloadHome() }
            Assert.assertEquals(listOf(Destination.Favorites), backstack)
        }

    @Test
    fun `an intent with no current kid is dropped`() =
        runTest(testDispatcher) {
            every { serverRepository.current } returns MutableStateFlow(null)
            coEvery { intentService.parseIntent(any()) } returns
                IntentResult.Target(
                    destinations = listOf(Destination.Favorites),
                    addHomeToBackStack = true,
                )
            val backstack = mutableListOf<Destination>()
            every { navigationManager.backStack } returns backstack

            assertFalse(viewModel.handleIntent(Intent()))

            verify(exactly = 0) { setupNavigationManager.navigateTo(any()) }
            verify(exactly = 0) { navigationManager.reloadHome() }
            assertTrue(backstack.isEmpty())
        }
}
