package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.KIDS_FEATURES_JSON
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.signInAnswer
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SetupFlowViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://jf.local", version = "12.2.0")
    private val adminId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val kidId = UUID.fromString("c1d00000-0000-0000-0000-000000000001")
    private val siblingId = UUID.fromString("c1d00000-0000-0000-0000-000000000002")
    private val http = FakeKidsServer()

    private val tvSetupRepository = mockk<TvSetupRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val serverDao = mockk<JellyfinServerDao> { every { getServer(any()) } returns null }
    private val serverLookup = mockk<SetupServerLookup> { coEvery { find(any()) } returns ServerLookup.Found(server) }
    private val pluginService = mockk<KidsPluginService>(relaxed = true)
    private val kidSignIn = mockk<KidSignIn>()
    private val launcher = mockk<TvLauncher>(relaxed = true)

    private val manuel = JellyfinUser(rowId = 1, id = kidId, name = "Manuel", serverId = server.id, accessToken = "kid-token")

    @Before
    fun setUp() {
        PekeDispatchers.configure(dispatcher)
        coEvery { kidSignIn.withQuickConnect(any(), any()) } returns KidSignInResult.SignedIn(manuel)
        http.on("GET", "/Kids/Info", 200, info("0.0.7.0"))
        http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token", adminId))
        http.on("POST", "/Sessions/Logout", 204)
        http.on("GET", "/Kids/Parent/Lock", 200, """{"LockType":"Numeric","ChangedAt":"2026-10-01T00:00:00Z"}""")
        http.on("PUT", "/Kids/Parent/Lock", 200, """{"LockType":"Numeric","ChangedAt":"2026-10-23T00:00:00Z"}""")
        household(kidId to "Manuel", siblingId to "Lucía")
    }

    @After
    fun tearDown() = PekeDispatchers.reset()

    private fun info(version: String) = """{"PluginVersion":"$version","ApiVersion":1,"Features":[$KIDS_FEATURES_JSON]}"""

    private fun household(vararg kids: Pair<UUID, String>) {
        http.on("GET", "/Kids/Profiles", 200, kids.joinToString(",", "[", "]") { """{"UserId":"${it.first}","Revision":1}""" })
        val users =
            kids.map { """{"Id":"${it.first}","Name":"${it.second}","PrimaryImageTag":"tag"}""" } + """{"Id":"$adminId","Name":"Admin"}"""
        http.on("GET", "/Users", 200, users.joinToString(",", "[", "]"))
    }

    private fun logouts() = http.requests.filter { it.path == "/Sessions/Logout" }

    private fun TestScope.viewModel() =
        SetupFlowViewModel(
            tvSetupRepository,
            serverRepository,
            serverDao,
            serverLookup,
            pluginService,
            http.api(dispatcher),
            AdminSessions(http.jellyfin, http.deviceInfo, dispatcher),
            kidSignIn,
            launcher,
            http.jellyfin,
            CoroutineScope(dispatcher),
        )

    private fun SetupFlowViewModel.screenAs() = screen.value

    /**
     * Server, administrator and, with a code on the server, straight to the kids
     */
    private suspend fun SetupFlowViewModel.toKids() {
        start(TvSetupStep.SERVER).join()
        addServer("jf.local").join()
        signIn("admin", "pw").join()
    }

    @Test
    fun `the steps go in order, from server to signing the kids in`() =
        runTest(dispatcher) {
            http.on("GET", "/Kids/Parent/Lock", 200, "{}")
            val vm = viewModel()

            vm.start(TvSetupStep.SERVER).join()
            assertTrue(vm.screenAs() is SetupScreen.Server)

            vm.addServer("jf.local").join()
            assertEquals(SetupScreen.Admin(server), vm.screenAs())
            // The setup row comes first, so a server stored without it never counts as a set-up TV
            coVerifyOrder {
                tvSetupRepository.markStep(server, TvSetupStep.ADMIN)
                serverRepository.addAndChangeServer(server)
            }
            coVerify { pluginService.check(server) }

            vm.signIn("admin", "pw").join()
            assertTrue(vm.screenAs() is SetupScreen.Code)
            coVerify { tvSetupRepository.markStep(server, TvSetupStep.CODE) }

            vm.setCode(ParentLockType.NUMERIC, "1234").join()
            val setLock = http.requests.single { it.method == "PUT" && it.path == "/Kids/Parent/Lock" }
            assertTrue(setLock.headers["Authorization"]!!.contains("Token=\"admin-token\""))
            val kids = vm.screenAs() as SetupScreen.Kids
            coVerify { tvSetupRepository.markStep(server, TvSetupStep.KIDS) }
            // Only the household's kids, never the administrator
            assertEquals(listOf("Lucía", "Manuel"), kids.kids.map { it.name })

            vm.toggle(kidId)
            vm.confirmKids().join()
            coVerify { tvSetupRepository.markStep(server, TvSetupStep.SIGN_IN) }
            coVerify(exactly = 1) { kidSignIn.withQuickConnect(any(), kidId) }
            coVerify(exactly = 0) { kidSignIn.withQuickConnect(any(), siblingId) }
            val signIn = vm.screenAs() as SetupScreen.SignIn
            assertEquals(KidRowState.Done, signIn.rows.single().state)
            assertTrue(logouts().isEmpty())

            vm.finish().join()
            coVerifyOrder {
                tvSetupRepository.markDone(server)
                launcher.launch(any())
            }
            assertEquals(1, logouts().size)
        }

    @Test
    fun `the code step is skipped when the household has a code`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toKids()

            assertTrue(vm.screenAs() is SetupScreen.Kids)
            coVerify(exactly = 0) { tvSetupRepository.markStep(any(), TvSetupStep.CODE) }
            coVerify { tvSetupRepository.markStep(server, TvSetupStep.KIDS) }
        }

    @Test
    fun `an account that isn't an administrator is refused`() =
        runTest(dispatcher) {
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(false, "kid-token", kidId))
            val vm = viewModel()
            vm.toKids()

            assertEquals(SetupScreen.Admin(server, error = SetupError.NOT_ADMIN), vm.screenAs())
            coVerify(exactly = 0) { tvSetupRepository.markStep(any(), TvSetupStep.KIDS) }
            coVerify(exactly = 0) { tvSetupRepository.markStep(any(), TvSetupStep.CODE) }
            // Its session was ended right away
            assertEquals(1, logouts().size)
        }

    @Test
    fun `a server without the plugin 0_0_7_0 is refused`() =
        runTest(dispatcher) {
            http.on("GET", "/Kids/Info", 200, info("0.0.6.0"))
            val vm = viewModel()
            vm.start(TvSetupStep.SERVER).join()
            vm.addServer("jf.local").join()

            assertEquals(SetupError.PLUGIN_NEEDED, (vm.screenAs() as SetupScreen.Server).error)
            coVerify(exactly = 0) { tvSetupRepository.markStep(any(), any()) }
            coVerify(exactly = 0) { serverRepository.addAndChangeServer(any()) }

            http.on("GET", "/Kids/Info", 404)
            vm.addServer("jf.local").join()
            assertEquals(SetupError.PLUGIN_NEEDED, (vm.screenAs() as SetupScreen.Server).error)
            coVerify(exactly = 0) { serverRepository.addAndChangeServer(any()) }
        }

    @Test
    fun `no kids yet, then check again`() =
        runTest(dispatcher) {
            household()
            val vm = viewModel()
            vm.toKids()
            assertEquals(emptyList<SetupKid>(), (vm.screenAs() as SetupScreen.Kids).kids)
            vm.confirmKids().join()
            assertTrue("Can't go on without a kid", vm.screenAs() is SetupScreen.Kids)

            household(kidId to "Manuel")
            vm.checkAgain().join()
            assertEquals(listOf(kidId), (vm.screenAs() as SetupScreen.Kids).kids.map { it.id })
        }

    @Test
    fun `setup finishes only with a kid signed in`() =
        runTest(dispatcher) {
            coEvery { kidSignIn.withQuickConnect(any(), kidId) } returns KidSignInResult.QuickConnectOff
            val vm = viewModel()
            vm.toKids()
            vm.toggle(kidId)
            vm.confirmKids().join()

            val row = (vm.screenAs() as SetupScreen.SignIn).rows.single()
            assertEquals(KidRowState.Problem(KidSignInResult.QuickConnectOff), row.state)
            vm.finish().join()
            coVerify(exactly = 0) { tvSetupRepository.markDone(any()) }
            assertTrue(logouts().isEmpty())

            // The password, typed once, signs them in
            coEvery { kidSignIn.withPassword(server, kidId, "Manuel", "kid-pw") } returns KidSignInResult.SignedIn(manuel)
            vm.signInWithPassword(kidId, "kid-pw").join()
            assertEquals(KidRowState.Done, (vm.screenAs() as SetupScreen.SignIn).rows.single().state)
            vm.finish().join()
            coVerify(exactly = 1) { tvSetupRepository.markDone(server) }
        }

    @Test
    fun `a kid already on this TV isn't signed in again`() =
        runTest(dispatcher) {
            every { serverDao.getServer(server.id) } returns JellyfinServerUsers(server, listOf(manuel))
            val vm = viewModel()
            vm.toKids()
            val kids = vm.screenAs() as SetupScreen.Kids
            assertTrue(kids.kids.single { it.id == kidId }.onTv)
            assertEquals(setOf(kidId), kids.selected)

            vm.confirmKids().join()
            coVerify(exactly = 0) { kidSignIn.withQuickConnect(any(), any()) }
            vm.finish().join()
            coVerify { tvSetupRepository.markDone(server) }
        }

    @Test
    fun `the administrator session closes on finish`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toKids()
            vm.toggle(kidId)
            vm.confirmKids().join()
            vm.finish().join()
            vm.abandon().join()

            assertEquals(1, logouts().size)
            assertTrue(logouts().single().headers["Authorization"]!!.contains("Token=\"admin-token\""))
        }

    @Test
    fun `the administrator session closes when setup is abandoned`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toKids()
            assertTrue(logouts().isEmpty())

            vm.abandon().join()

            assertEquals(1, logouts().size)
            coVerify(exactly = 0) { tvSetupRepository.markDone(any()) }
        }

    @Test
    fun `going back from the kids closes the session and signs in again`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toKids()

            assertTrue(vm.back())
            assertEquals(SetupScreen.Admin(server), vm.screenAs())
            vm.abandon().join()
            assertEquals(1, logouts().size)
            assertTrue(vm.back())
            assertTrue(vm.screenAs() is SetupScreen.Server)
            // Back on the server step quits
            assertFalse(vm.back())
        }

    @Test
    fun `a setup in progress resumes with a fresh administrator sign-in`() =
        runTest(dispatcher) {
            coEvery { tvSetupRepository.server() } returns server
            val vm = viewModel()
            vm.start(TvSetupStep.KIDS).join()

            assertEquals(SetupScreen.Admin(server), vm.screenAs())
            vm.signIn("admin", "pw").join()
            assertTrue(vm.screenAs() is SetupScreen.Kids)
        }

    @Test
    fun `signing a kid in again goes from the administrator straight to that kid`() =
        runTest(dispatcher) {
            coEvery { tvSetupRepository.server() } returns server
            val vm = viewModel()
            vm.again(server, manuel).join()

            assertEquals(SetupScreen.Admin(server), vm.screenAs())
            vm.signIn("admin", "pw").join()
            coVerify(exactly = 1) { kidSignIn.withQuickConnect(any(), kidId) }
            vm.finish().join()
            // The TV is already set up: no setup step is written
            coVerify(exactly = 0) { tvSetupRepository.markStep(any(), any()) }
            coVerify(exactly = 0) { tvSetupRepository.markDone(any()) }
            coVerify { launcher.launch(any()) }
            assertEquals(1, logouts().size)
        }
}
