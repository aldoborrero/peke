package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidProfileDao
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.FakeKidsServerDao
import dev.aldoborrero.peke.kids.FakeTvSetupDao
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsDataCleaner
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.services.NavigationManager
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ResetTvTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://jf.local", version = null)
    private val manuel = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Manuel", serverId = server.id, accessToken = "t1")
    private val lucia = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "Lucía", serverId = server.id, accessToken = "t2")
    private val http = FakeKidsServer()

    private val servers = MutableStateFlow(listOf(JellyfinServerUsers(server, listOf(manuel, lucia))))
    private val serverDao =
        mockk<JellyfinServerDao> {
            every { observeServers() } returns servers
            every { getServers() } answers { servers.value }
        }
    private val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, manuel))
    private val serverRepository =
        mockk<ServerRepository> {
            every { this@mockk.current } returns this@ResetTvTest.current
            // As the app database does: the server's users go with it
            coEvery { removeServer(any()) } answers {
                val removed = firstArg<JellyfinServer>()
                servers.value = servers.value.filter { it.server.id != removed.id }
                if (this@ResetTvTest
                        .current.value
                        ?.server
                        ?.id == removed.id
                ) {
                    this@ResetTvTest.current.value = null
                }
            }
        }
    private val kidProfileDao = FakeKidProfileDao()
    private val kidsServerDao = FakeKidsServerDao()
    private val tvSetupDao =
        FakeTvSetupDao(TvSetupEntity(serverId = server.id, serverUrl = server.url, step = TvSetupStep.DONE, lastKidRowId = 1))
    private val allowanceService = mockk<KidsAllowanceService>(relaxed = true)
    private val parentLockService = mockk<ParentLockService>(relaxed = true)
    private val navigationManager = mockk<NavigationManager>(relaxed = true)

    @Before
    fun setUp() {
        PekeDispatchers.configure(dispatcher)
        http.on("POST", "/Sessions/Logout", 204)
    }

    @After
    fun tearDown() = PekeDispatchers.reset()

    private suspend fun cacheKidsData() {
        kidProfileDao.upsert(KidProfile(userId = manuel.rowId, serverId = server.id))
        kidProfileDao.upsert(KidProfile(userId = lucia.rowId, serverId = server.id))
        kidsServerDao.upsert(KidsServer(server.id, lockType = "NUMERIC", verifier = "v"))
    }

    private fun TestScope.repository() = TvSetupRepository(tvSetupDao, serverDao, kidProfileDao, backgroundScope)

    private fun resetTv(repository: TvSetupRepository) =
        ResetTv(
            serverDao,
            serverRepository,
            repository,
            KidsDataCleaner(serverDao, kidProfileDao, kidsServerDao),
            allowanceService,
            parentLockService,
            KidSignOut(http.jellyfin, http.deviceInfo, dispatcher),
            navigationManager,
        )

    private fun logouts() = http.requests.filter { it.path == "/Sessions/Logout" }

    private fun deviceIdOf(request: FakeKidsServer.Recorded) =
        Regex("DeviceId=\"([^\"]+)\"").find(request.headers["Authorization"]!!)!!.groupValues[1]

    @Test
    fun `every kid is signed out with their own device id`() =
        runTest(dispatcher) {
            cacheKidsData()
            assertEquals(ResetOutcome.DONE, resetTv(repository()).reset())

            val byToken = logouts().associateBy { Regex("Token=\"([^\"]+)\"").find(it.headers["Authorization"]!!)!!.groupValues[1] }
            assertEquals(setOf("t1", "t2"), byToken.keys)
            assertEquals(pekeKidDeviceId(FakeKidsServer.DEVICE_ID, manuel.id), deviceIdOf(byToken.getValue("t1")))
            assertEquals(pekeKidDeviceId(FakeKidsServer.DEVICE_ID, lucia.id), deviceIdOf(byToken.getValue("t2")))
        }

    @Test
    fun `setup, users, servers and kids data are erased`() =
        runTest(dispatcher) {
            cacheKidsData()
            resetTv(repository()).reset()

            assertNull(tvSetupDao.row.value)
            coVerify(exactly = 1) { serverRepository.removeServer(server) }
            assertTrue(servers.value.isEmpty())
            assertTrue(kidProfileDao.allUserIds().isEmpty())
            // The cached verifier goes with the kids servers, and the cached statuses with the kids
            assertNull(kidsServerDao.get(server.id))
            coVerify { allowanceService.forget(match { it.toSet() == setOf(manuel.rowId, lucia.rowId) }) }
            verify { parentLockService.forgetGrants() }
            // The kids' pages don't carry over to the next setup
            verify { navigationManager.reloadHome() }
        }

    @Test
    fun `offline everything is still erased, and the dashboard is named`() =
        runTest(dispatcher) {
            cacheKidsData()
            http.offline = true
            assertEquals(ResetOutcome.OFFLINE, resetTv(repository()).reset())

            assertNull(tvSetupDao.row.value)
            assertTrue(servers.value.isEmpty())
            assertTrue(kidProfileDao.allUserIds().isEmpty())
            assertNull(kidsServerDao.get(server.id))
        }

    @Test
    fun `a warm resume after a reset shows setup`() =
        runTest(dispatcher) {
            cacheKidsData()
            val repository = repository()
            advanceUntilIdle()
            assertTrue(repository.state.value is TvState.SetUp)
            resetTv(repository).reset()
            // The state follows the databases
            assertEquals(TvState.NotSetUp, repository.state.first { it == TvState.NotSetUp })

            // Peke comes back from the background still showing the kid's pages
            val setupNavigationManager =
                mockk<SetupNavigationManager>(relaxed = true) {
                    every { backStack } returns mutableListOf<SetupDestination>(SetupDestination.AppContent(CurrentUser(server, manuel)))
                }
            TvLauncher(repository, serverRepository, setupNavigationManager, navigationManager, backgroundScope).launch()
            verify { setupNavigationManager.navigateTo(SetupDestination.Setup(TvSetupStep.SERVER)) }
        }
}
