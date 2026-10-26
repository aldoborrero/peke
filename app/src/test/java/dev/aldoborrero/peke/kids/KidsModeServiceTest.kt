package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsServerState
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidsModeServiceTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "server", url = "http://localhost", version = null)
    private val adult = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "adult", serverId = server.id, accessToken = "a")
    private val kid = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "kid", serverId = server.id, accessToken = "k")
    private val notKids = MutableStateFlow<Set<Int>>(emptySet())
    private val repository =
        mockk<KidProfileRepository>(relaxed = true) {
            every { notKids } returns this@KidsModeServiceTest.notKids
        }
    private val pluginService = mockk<KidsPluginService>(relaxed = true)
    private val tvSetupRepository = mockk<TvSetupRepository>(relaxed = true)

    /**
     * The server answers that [user] isn't a kid
     */
    private fun answersNotAKid(user: JellyfinUser) {
        coEvery { repository.refresh(any(), user) } coAnswers {
            notKids.value += user.rowId
            true
        }
    }

    private fun TestScope.createService(
        current: MutableStateFlow<CurrentUser?>,
        dao: FakeKidProfileDao,
        plugin: MutableStateFlow<KidsServerState> = MutableStateFlow(KidsServerState.ABSENT),
    ): KidsModeService {
        val serverRepository = mockk<ServerRepository>()
        every { serverRepository.current } returns current
        every { pluginService.current } returns plugin
        coEvery { pluginService.forget(any()) } coAnswers { dao.deleteForServer(firstArg()) }
        return KidsModeService(
            serverRepository,
            dao,
            repository,
            pluginService,
            FakeClock(),
            tvSetupRepository,
            backgroundScope,
        )
    }

    @Test
    fun `each kid who becomes current is remembered as the last kid`() =
        runTest(UnconfinedTestDispatcher()) {
            val sibling = kid.copy(rowId = 3, id = UUID.randomUUID(), name = "sibling")
            val dao = FakeKidProfileDao { server.id }
            dao.upsert(KidProfile(userId = kid.rowId))
            dao.upsert(KidProfile(userId = sibling.rowId))
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, adult))
            createService(current, dao)

            current.value = CurrentUser(server, kid)
            advanceUntilIdle()
            coVerify(exactly = 1) { tvSetupRepository.setLastKid(server, kid.rowId) }
            coVerify(exactly = 0) { tvSetupRepository.setLastKid(any(), adult.rowId) }

            current.value = CurrentUser(server, sibling)
            advanceUntilIdle()
            coVerify(exactly = 1) { tvSetupRepository.setLastKid(server, sibling.rowId) }
        }

    @Test
    fun `inactive for users the server says aren't kids`() =
        runTest(UnconfinedTestDispatcher()) {
            answersNotAKid(adult)
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, adult))
            val service = createService(current, FakeKidProfileDao())
            advanceUntilIdle()
            assertEquals(KidsModeState.Inactive, service.state.value)
        }

    @Test
    fun `active once a kid profile is current`() =
        runTest(UnconfinedTestDispatcher()) {
            val dao = FakeKidProfileDao { server.id }
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, adult))
            val service = createService(current, dao)
            dao.upsert(KidProfile(userId = kid.rowId))
            current.value = CurrentUser(server, kid)
            advanceUntilIdle()
            assertTrue(service.isActive)
            assertEquals(
                kid.rowId,
                service.state.value.profile
                    ?.userId,
            )
        }

    @Test
    fun `a kid removed from the cache leaves kids mode`() =
        runTest(UnconfinedTestDispatcher()) {
            val dao = FakeKidProfileDao { server.id }
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
            val service = createService(current, dao)
            dao.upsert(KidProfile(userId = kid.rowId))
            advanceUntilIdle()
            assertTrue(service.isActive)
            dao.delete(kid.rowId)
            advanceUntilIdle()
            assertFalse(service.isActive)
        }

    @Test
    fun `the current user's profile is read once, after asking for the plugin if the server hasn't said`() =
        runTest(UnconfinedTestDispatcher()) {
            every { pluginService.answered(server.id) } returns false
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
            createService(current, FakeKidProfileDao { server.id })
            advanceUntilIdle()
            coVerifyOrder {
                pluginService.check(server)
                repository.refresh(server, kid)
            }
            coVerify(exactly = 1) { repository.refresh(any(), any()) }
        }

    @Test
    fun `a user without a cached profile stays loading until the server says they aren't a kid`() =
        runTest(UnconfinedTestDispatcher()) {
            // No connection: no answer
            coEvery { repository.refresh(any(), adult) } returns false
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, adult))
            val service = createService(current, FakeKidProfileDao { server.id })
            advanceUntilIdle()
            assertEquals(KidsModeState.Loading, service.state.value)
            assertTrue(service.unanswered.value)

            answersNotAKid(adult)
            service.onResume(mockk())
            advanceUntilIdle()
            assertEquals(KidsModeState.Inactive, service.state.value)
            assertFalse(service.unanswered.value)
        }

    @Test
    fun `a cached kid is a kid without an answer from the server`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { repository.refresh(any(), kid) } returns false
            val dao = FakeKidProfileDao { server.id }
            dao.upsert(KidProfile(userId = kid.rowId))
            val service = createService(MutableStateFlow(CurrentUser(server, kid)), dao)
            advanceUntilIdle()
            assertTrue(service.isActive)
            assertFalse(service.unanswered.value)
        }

    @Test
    fun `coming back to the app shares the read that started when the user became current`() =
        runTest(UnconfinedTestDispatcher()) {
            val dao = FakeKidProfileDao { server.id }
            val answer = CompletableDeferred<Unit>()
            coEvery { repository.refresh(server, kid) } coAnswers {
                answer.await()
                dao.upsert(KidProfile(userId = kid.rowId))
                true
            }
            val current = MutableStateFlow<CurrentUser?>(null)
            val service = createService(current, dao)
            current.value = CurrentUser(server, kid)
            assertEquals(KidsModeState.Loading, service.state.value)
            // Still reading: no second read
            service.onResume(mockk())
            answer.complete(Unit)
            advanceUntilIdle()
            assertTrue(service.isActive)
            coVerify(exactly = 1) { repository.refresh(any(), any()) }
            // Coming back soon after reads nothing again
            service.onResume(mockk())
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.refresh(any(), any()) }
        }

    @Test
    fun `the state starts unknown`() =
        runTest(UnconfinedTestDispatcher()) {
            val serverRepository = mockk<ServerRepository>()
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
            every { pluginService.current } returns MutableStateFlow(KidsServerState.ABSENT)
            val dao = FakeKidProfileDao { server.id }
            // A scope that hasn't run anything yet, as right after the app starts
            val notStarted = TestScope(StandardTestDispatcher(testScheduler))
            val service =
                KidsModeService(
                    serverRepository,
                    dao,
                    repository,
                    pluginService,
                    FakeClock(),
                    tvSetupRepository,
                    notStarted.backgroundScope,
                )
            assertEquals(KidsModeState.Loading, service.state.value)
        }
}
