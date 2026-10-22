package com.github.damontecres.wholphin.kids

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.kids.data.KidProfile
import com.github.damontecres.wholphin.kids.data.KidProfileRepository
import com.github.damontecres.wholphin.kids.server.KidsPluginService
import com.github.damontecres.wholphin.kids.server.KidsServer
import com.github.damontecres.wholphin.kids.server.KidsServerState
import com.github.damontecres.wholphin.services.KeyValueService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidsModeServiceTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val server = JellyfinServer(id = UUID.randomUUID(), name = "server", url = "http://localhost", version = null)
    private val adult = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "adult", serverId = server.id, accessToken = "a")
    private val kid = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "kid", serverId = server.id, accessToken = "k")
    private val repository = mockk<KidProfileRepository>(relaxed = true)

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temporaryFolder.newFile("kids_${UUID.randomUUID()}.preferences_pb")
        }

    private suspend fun TestScope.createService(
        current: MutableStateFlow<CurrentUser?>,
        dao: FakeKidProfileDao,
        plugin: MutableStateFlow<KidsServerState> = MutableStateFlow(KidsServerState.ABSENT),
    ): KidsModeService {
        val serverRepository = mockk<ServerRepository>()
        every { serverRepository.current } returns current
        val pluginService = mockk<KidsPluginService>(relaxed = true)
        every { pluginService.current } returns plugin
        coEvery { pluginService.forget(any()) } coAnswers { dao.deleteForServer(firstArg()) }
        return KidsModeService(
            serverRepository,
            dao,
            KeyValueService(store()),
            repository,
            pluginService,
            FakeClock(),
            backgroundScope,
        )
    }

    @Test
    fun `inactive for users without a kid profile`() =
        runTest(UnconfinedTestDispatcher()) {
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, adult))
            val service = createService(current, FakeKidProfileDao())
            advanceUntilIdle()
            assertEquals(KidsModeState.Inactive, service.state.value)
            assertEquals(false, service.locked.value)
        }

    @Test
    fun `active and locked once a kid profile is current`() =
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
            assertEquals(true, service.locked.value)
        }

    @Test
    fun `kids lock fails closed and only unlock clears it`() =
        runTest(UnconfinedTestDispatcher()) {
            val dao = FakeKidProfileDao { server.id }
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, adult))
            val service = createService(current, dao)
            // A kid profile exists but was never active: no stored flag, still locked
            dao.upsert(KidProfile(userId = kid.rowId))
            advanceUntilIdle()
            assertEquals(true, service.locked.value)

            service.unlock()
            advanceUntilIdle()
            assertEquals(false, service.locked.value)

            current.value = CurrentUser(server, kid)
            advanceUntilIdle()
            assertEquals(true, service.locked.value)
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
            assertEquals(emptySet<Int>(), service.kidUserRowIds())
        }

    @Test
    fun `the current user's profile is read once the plugin is available`() =
        runTest(UnconfinedTestDispatcher()) {
            val plugin = MutableStateFlow(KidsServerState.ABSENT)
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
            createService(current, FakeKidProfileDao { server.id }, plugin)
            advanceUntilIdle()
            coVerify(exactly = 0) { repository.refresh(any(), any()) }
            plugin.value = KidsServerState.of(KidsServer(server.id, apiVersion = 1, features = KIDS_FEATURES), 0)
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.refresh(server, kid) }
        }

    @Test
    fun `leaving kids mode stays unlocked when the kid's profile is refreshed`() =
        runTest(UnconfinedTestDispatcher()) {
            val dao = FakeKidProfileDao { server.id }
            dao.upsert(KidProfile(userId = kid.rowId, revision = 1))
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
            val service = createService(current, dao)
            advanceUntilIdle()
            assertEquals(true, service.locked.value)

            service.unlock()
            // The user list refreshes who is a kid, which stores the kid's newer profile
            dao.upsert(KidProfile(userId = kid.rowId, revision = 2, searchAllowed = true))
            advanceUntilIdle()
            assertTrue(service.isActive)
            assertEquals(false, service.locked.value)

            // Picking the same kid again from the user list locks
            service.lockIfKid(kid)
            advanceUntilIdle()
            assertEquals(true, service.locked.value)

            // So does switching to a kid from another profile
            service.unlock()
            current.value = CurrentUser(server, adult)
            advanceUntilIdle()
            assertEquals(false, service.locked.value)
            service.lockIfKid(adult)
            advanceUntilIdle()
            assertEquals(false, service.locked.value)
            current.value = CurrentUser(server, kid)
            advanceUntilIdle()
            assertEquals(true, service.locked.value)
        }

    @Test
    fun `the state and the lock start unknown`() =
        runTest(UnconfinedTestDispatcher()) {
            val serverRepository = mockk<ServerRepository>()
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
            val pluginService = mockk<KidsPluginService>(relaxed = true)
            every { pluginService.current } returns MutableStateFlow(KidsServerState.ABSENT)
            val dao = FakeKidProfileDao { server.id }
            // A scope that hasn't run anything yet, as right after the app starts
            val notStarted = TestScope(StandardTestDispatcher(testScheduler))
            val service =
                KidsModeService(
                    serverRepository,
                    dao,
                    KeyValueService(store()),
                    repository,
                    pluginService,
                    FakeClock(),
                    notStarted.backgroundScope,
                )
            assertEquals(KidsModeState.Loading, service.state.value)
            assertNull(service.locked.value)
        }

    @Test
    fun `turning kids mode off for a server removes its kids`() =
        runTest(UnconfinedTestDispatcher()) {
            val dao = FakeKidProfileDao { server.id }
            val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
            val service = createService(current, dao)
            dao.upsert(KidProfile(userId = kid.rowId))
            advanceUntilIdle()
            assertTrue(service.isActive)
            service.turnOffForServer(server.id)
            advanceUntilIdle()
            assertFalse(service.isActive)
            assertEquals(false, service.locked.value)
        }
}
