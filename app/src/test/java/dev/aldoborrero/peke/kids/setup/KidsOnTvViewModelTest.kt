package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.KIDS_FEATURES_JSON
import dev.aldoborrero.peke.kids.gate.GrownUpSessions
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.signInAnswer
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.services.hilt.pekeKidDeviceId
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidsOnTvViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://jf.local", version = "12.2.0")
    private val adminId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val manuel =
        JellyfinUser(
            rowId = 1,
            id = UUID.fromString("c1d00000-0000-0000-0000-000000000001"),
            name = "Manuel",
            serverId = server.id,
            accessToken = "t1",
        )
    private val lucia =
        JellyfinUser(
            rowId = 2,
            id = UUID.fromString("c1d00000-0000-0000-0000-000000000002"),
            name = "Lucía",
            serverId = server.id,
            accessToken = "t2",
        )
    private val http = FakeKidsServer()

    private val tvState = MutableStateFlow<TvState>(TvState.SetUp(server, listOf(manuel, lucia)))
    private val tvSetupRepository = mockk<TvSetupRepository>(relaxed = true) { every { state } returns tvState }
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val allowanceService = mockk<KidsAllowanceService>(relaxed = true)
    private val launcher = mockk<TvLauncher>(relaxed = true)
    private val setupNavigationManager = mockk<SetupNavigationManager>(relaxed = true)

    @Before
    fun setUp() {
        PekeDispatchers.configure(dispatcher)
        http.on("POST", "/Sessions/Logout", 204)
    }

    @After
    fun tearDown() = PekeDispatchers.reset()

    private fun viewModel() =
        KidsOnTvViewModel(
            tvSetupRepository,
            serverRepository,
            KidSignOut(http.jellyfin, http.deviceInfo, dispatcher),
            allowanceService,
            launcher,
            setupNavigationManager,
            http.jellyfin,
        )

    private fun logouts() = http.requests.filter { it.path == "/Sessions/Logout" }

    private fun deviceIdOf(request: FakeKidsServer.Recorded) =
        Regex("DeviceId=\"([^\"]+)\"").find(request.headers["Authorization"]!!)!!.groupValues[1]

    @Test
    fun `lists this TV's kids`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(
                listOf(manuel, lucia),
                vm.state.value.kids
                    .map { it.user },
            )
        }

    @Test
    fun `removing a kid signs them out and deletes only their row`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.remove(manuel).join()

            // Signed out with their own token and device id, so a sibling's session stays
            val logout = logouts().single()
            assertTrue(logout.headers["Authorization"]!!.contains("Token=\"t1\""))
            assertEquals(pekeKidDeviceId(FakeKidsServer.DEVICE_ID, manuel.id), deviceIdOf(logout))
            coVerify(exactly = 1) { serverRepository.removeUser(manuel) }
            coVerify(exactly = 0) { serverRepository.removeUser(lucia) }
            coVerify { allowanceService.forget(listOf(manuel.rowId)) }
            // Back to the first screen the launch rule picks
            coVerify { launcher.launch(any()) }
        }

    @Test
    fun `removing a kid offline still forgets them here`() =
        runTest(dispatcher) {
            http.offline = true
            val vm = viewModel()
            vm.remove(lucia).join()

            coVerify(exactly = 1) { serverRepository.removeUser(lucia) }
            coVerify(exactly = 0) { serverRepository.removeUser(manuel) }
            coVerify { launcher.launch(any()) }
        }

    @Test
    fun `adding opens the setup's kid steps for this server`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.add()
            verify { setupNavigationManager.navigateTo(SetupDestination.AddKids(server)) }
        }

    private fun setupFlow(kidSignIn: KidSignIn): SetupFlowViewModel {
        val adminSessions = AdminSessions(http.jellyfin, http.deviceInfo, http.api(dispatcher), dispatcher)
        val parentLockService = mockk<ParentLockService>(relaxed = true)
        return SetupFlowViewModel(
            tvSetupRepository,
            serverRepository,
            mockk(),
            mockk<KidsPluginService>(relaxed = true),
            http.api(dispatcher),
            adminSessions,
            kidSignIn,
            launcher,
            http.jellyfin,
            parentLockService,
            GrownUpSessions(parentLockService, adminSessions, CoroutineScope(dispatcher)),
            mockk<VoiceSearchSetting>(relaxed = true),
            CoroutineScope(dispatcher),
        )
    }

    @Test
    fun `adding signs the administrator in, lists only kids not on this TV and closes the session`() =
        runTest(dispatcher) {
            http.on("GET", "/Kids/Info", 200, """{"PluginVersion":"0.0.7.0","ApiVersion":1,"Features":[$KIDS_FEATURES_JSON]}""")
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token", adminId))
            http.on("GET", "/Kids/Parent/Lock", 200, """{"LockType":"Numeric","ChangedAt":"2026-10-01T00:00:00Z"}""")
            http.on("GET", "/Kids/Profiles", 200, """[{"UserId":"${manuel.id}","Revision":1},{"UserId":"${lucia.id}","Revision":1}]""")
            http.on(
                "GET",
                "/Users",
                200,
                """[{"Id":"${manuel.id}","Name":"Manuel"},{"Id":"${lucia.id}","Name":"Lucía"},{"Id":"$adminId","Name":"Admin"}]""",
            )
            tvState.value = TvState.SetUp(server, listOf(manuel))
            val kidSignIn = mockk<KidSignIn> { coEvery { withQuickConnect(any(), lucia.id) } returns KidSignInResult.SignedIn(lucia) }
            val setup = setupFlow(kidSignIn)

            setup.addKids(server).join()
            assertEquals(SetupScreen.Admin(server), setup.screen.value)
            setup.signIn("admin", "pw").join()

            // Only the household's kids that aren't on this TV yet
            val kids = setup.screen.value as SetupScreen.Kids
            assertEquals(listOf(lucia.id), kids.kids.map { it.id })
            setup.toggle(lucia.id)
            setup.confirmKids().join()
            coVerify(exactly = 1) { kidSignIn.withQuickConnect(any(), lucia.id) }
            assertTrue(logouts().isEmpty())

            setup.finish().join()
            // The TV is already set up: no setup step is written, and the administrator is signed out
            coVerify(exactly = 0) { tvSetupRepository.markStep(any(), any()) }
            coVerify(exactly = 0) { tvSetupRepository.markDone(any()) }
            assertEquals(1, logouts().size)
            assertTrue(logouts().single().headers["Authorization"]!!.contains("Token=\"admin-token\""))
            coVerify { launcher.launch(any()) }
        }

    @Test
    fun `leaving the add steps closes the administrator session`() =
        runTest(dispatcher) {
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token", adminId))
            http.on("GET", "/Kids/Profiles", 200, "[]")
            http.on("GET", "/Users", 200, "[]")
            tvState.value = TvState.SetUp(server, listOf(manuel))
            val setup = setupFlow(mockk())
            setup.addKids(server).join()
            setup.signIn("admin", "pw").join()
            assertTrue((setup.screen.value as SetupScreen.Kids).adding)

            setup.abandon().join()
            assertEquals(1, logouts().size)
        }

    @Test
    fun `leaving returns to the kid whose menu opened it, or else to the launch rule`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.close(fromKid = true).join()
            coVerify(exactly = 1) { launcher.resume() }
            coVerify(exactly = 0) { launcher.launch(any()) }
            vm.close(fromKid = false).join()
            coVerify(exactly = 1) { launcher.launch(any()) }
        }

    @Test
    fun `resuming opens the current kid's pages only while they are on this TV`() =
        runTest(dispatcher) {
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, manuel))
            every { serverRepository.current } returns current
            val serverDao = mockk<JellyfinServerDao> { every { getServer(server.id) } returns JellyfinServerUsers(server, listOf(manuel)) }
            val tvLauncher =
                TvLauncher(
                    tvSetupRepository,
                    serverRepository,
                    serverDao,
                    mockk(relaxed = true),
                    setupNavigationManager,
                    mockk(relaxed = true),
                    backgroundScope,
                )

            tvLauncher.resume()
            verify(exactly = 1) { setupNavigationManager.navigateTo(SetupDestination.AppContent(CurrentUser(server, manuel))) }

            // Removed meanwhile: the launch rule picks, here the picker of the kids left
            tvState.value = TvState.SetUp(server, listOf(lucia, manuel.copy(rowId = 3)))
            every { setupNavigationManager.backStack } returns mutableListOf()
            tvLauncher.resume()
            verify(exactly = 1) { setupNavigationManager.navigateTo(SetupDestination.KidsPicker) }
        }
}
