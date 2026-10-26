package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.data.model.ServerUserConfig
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.ui.isGrownUp
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Whose pages the kids side shows after a launch, a resume or opening a kid: the kid's own, never another kid's, never
 * a grown-up page without the gate (spec FR-008, C12)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class TvLauncherTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://jf.local", version = null)
    private val manuel = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Manuel", serverId = server.id, accessToken = "t1")
    private val lucia = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "Lucía", serverId = server.id, accessToken = "t2")

    private val rows = MutableStateFlow(listOf(manuel, lucia))
    private val serverDao = mockk<JellyfinServerDao> { every { getServer(server.id) } answers { JellyfinServerUsers(server, rows.value) } }
    private val tvState = MutableStateFlow<TvState>(TvState.SetUp(server, listOf(manuel)))
    private val tvSetupRepository = mockk<TvSetupRepository> { every { state } returns tvState }
    private val current = MutableStateFlow<CurrentUser?>(null)
    private val userDto = MutableStateFlow<ServerUserConfig?>(null)
    private val serverRepository =
        mockk<ServerRepository>(relaxed = true) {
            every { this@mockk.current } returns this@TvLauncherTest.current
            every { currentUserDto } answers { userDto.value }
            coEvery { restoreUser(any(), any()) } answers
                { CurrentUser(firstArg(), secondArg()).also { this@TvLauncherTest.current.value = it } }
            coEvery { clearCurrent() } answers { this@TvLauncherTest.current.value = null }
        }
    private val kidProfileRepository = mockk<KidProfileRepository>(relaxed = true)
    private val navigationManager = NavigationManager()
    private val setupNavigationManager = SetupNavigationManager(navigationManager)

    @Before
    fun setUp() = PekeDispatchers.configure(dispatcher)

    @After
    fun tearDown() = PekeDispatchers.reset()

    private fun TestScope.launcher() =
        TvLauncher(
            tvSetupRepository,
            serverRepository,
            serverDao,
            kidProfileRepository,
            setupNavigationManager,
            navigationManager,
            backgroundScope,
        )

    private fun screen() = setupNavigationManager.backStack.single()

    private fun pages() = navigationManager.backStack.toList()

    @Test
    fun `a warm start with a grown-up page on top shows the kid's own pages without it`() =
        runTest(dispatcher) {
            val launcher = launcher()
            launcher.launch()
            val details = Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE)
            // The kid's details page, then This TV and the kids settings from the parent menu
            navigationManager.navigateTo(details)
            navigationManager.navigateTo(Destination.Settings(PreferenceScreenOption.PEKE))
            navigationManager.navigateTo(Destination.KidsSettings)

            // Home or standby, then back
            launcher.launch()

            assertEquals(SetupDestination.AppContent(CurrentUser(server, manuel)), screen())
            assertTrue(pages().none { it.isGrownUp() })
            assertEquals(details, pages().last())
        }

    @Test
    fun `a warm start with only grown-up pages starts at the kid's home`() =
        runTest(dispatcher) {
            val launcher = launcher()
            launcher.launch()
            navigationManager.backStack.clear()
            navigationManager.backStack.add(Destination.License)

            launcher.launch()

            assertTrue(pages().single() is Destination.Home)
        }

    @Test
    fun `the parent menu's pages outside the kid's go back to the launch rule`() =
        runTest(dispatcher) {
            val launcher = launcher()
            setupNavigationManager.navigateTo(SetupDestination.ResetTv(fromKid = true))

            launcher.launch()

            assertEquals(SetupDestination.AppContent(CurrentUser(server, manuel)), screen())
        }

    @Test
    fun `adding a kid from a kid's menu returns to that kid's own pages`() =
        runTest(dispatcher) {
            tvState.value = TvState.SetUp(server, listOf(manuel, lucia), lastKidRowId = manuel.rowId)
            val launcher = launcher()
            launcher.openKid(server, manuel)
            val details = Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE)
            navigationManager.navigateTo(details)
            // Grown-ups → Kids on this TV → Add: the kid signed in is saved, not made current
            setupNavigationManager.navigateTo(SetupDestination.AddKids(server, fromKid = true))

            launcher.resume()

            assertEquals(SetupDestination.AppContent(CurrentUser(server, manuel)), screen())
            assertEquals(details, pages().last())
            assertEquals(manuel, current.value?.user)
        }

    @Test
    fun `another kid never gets the pages of the one before`() =
        runTest(dispatcher) {
            tvState.value = TvState.SetUp(server, listOf(manuel, lucia), lastKidRowId = manuel.rowId)
            val launcher = launcher()
            launcher.openKid(server, manuel)
            navigationManager.navigateTo(Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE))

            // Switched through the gate and the picker
            launcher.launch()
            assertEquals(SetupDestination.KidsPicker, screen())
            assertNull("Nobody is current on the picker", current.value)
            launcher.openKid(server, lucia)

            assertEquals(SetupDestination.AppContent(CurrentUser(server, lucia)), screen())
            assertTrue(pages().single() is Destination.Home)
        }

    @Test
    fun `a back stack restored without its owner, or for another kid, is dropped`() =
        runTest(dispatcher) {
            val launcher = launcher()
            // Restored after the process died, holding the pages of the sibling
            navigationManager.backStack.add(Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE))
            launcher.pagesOf = lucia.rowId

            launcher.launch()
            assertTrue(pages().single() is Destination.Home)
            assertEquals(manuel.rowId, launcher.pagesOf)

            // Restored with no owner known
            navigationManager.backStack.add(Destination.Favorites)
            launcher.pagesOf = null
            current.value = null
            launcher.launch()
            assertTrue(pages().single() is Destination.Home)
        }

    @Test
    fun `the kid's own restored pages stay`() =
        runTest(dispatcher) {
            val launcher = launcher()
            navigationManager.backStack.add(Destination.Favorites)
            launcher.pagesOf = manuel.rowId

            launcher.launch()

            assertEquals(Destination.Favorites, pages().last())
        }

    @Test
    fun `a kid whose session was refused or who was removed meanwhile is not opened`() =
        runTest(dispatcher) {
            val launcher = launcher()
            // The state read a moment ago still has the token; the row doesn't any more
            rows.value = listOf(manuel.copy(accessToken = null), lucia)

            launcher.launch()

            assertEquals(SetupDestination.KidsPicker, screen())
            coVerify(exactly = 0) { serverRepository.restoreUser(any(), any()) }

            rows.value = listOf(lucia)
            launcher.launch()
            assertEquals(SetupDestination.KidsPicker, screen())
            coVerify(exactly = 0) { serverRepository.restoreUser(any(), any()) }
        }

    @Test
    fun `a kid with a cached configuration opens at once, refreshed in the background`() =
        runTest(dispatcher) {
            userDto.value = ServerUserConfig(id = manuel.id)
            coEvery { serverRepository.updateUserDto() } coAnswers { awaitCancellation() }

            launcher().launch()

            assertEquals(SetupDestination.AppContent(CurrentUser(server, manuel)), screen())
            assertEquals(0L, currentTime)
        }

    @Test
    fun `a kid without a cached configuration waits a moment for the server's`() =
        runTest(dispatcher) {
            coEvery { serverRepository.updateUserDto() } coAnswers {
                delay(500)
                userDto.value = ServerUserConfig(id = manuel.id)
            }

            val launcher = launcher()
            launcher.launch()

            assertEquals(SetupDestination.AppContent(CurrentUser(server, manuel)), screen())
            assertEquals(500L, currentTime)
            assertEquals(manuel.id, userDto.value?.id)

            // Offline: not for long
            current.value = null
            userDto.value = null
            coEvery { serverRepository.updateUserDto() } coAnswers { awaitCancellation() }
            val start = currentTime
            launcher.launch()
            advanceUntilIdle()
            assertTrue(currentTime - start <= 3_000L)
            assertEquals(SetupDestination.AppContent(CurrentUser(server, manuel)), screen())
        }

    @Test
    fun `a launch asks the plugin which users are still kids`() =
        runTest(dispatcher) {
            launcher().launch()

            coVerify { kidProfileRepository.refreshServer(server, null) }
        }
}
