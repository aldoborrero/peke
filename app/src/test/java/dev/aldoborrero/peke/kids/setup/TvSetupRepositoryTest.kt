package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidProfileDao
import dev.aldoborrero.peke.kids.FakeTvSetupDao
import dev.aldoborrero.peke.kids.data.KidProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TvSetupRepositoryTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://jf.local", version = null)
    private val otherServer = JellyfinServer(id = UUID.randomUUID(), name = "other", url = "http://other.local", version = null)

    private fun user(
        rowId: Int,
        on: JellyfinServer = server,
    ) = JellyfinUser(rowId = rowId, id = UUID.randomUUID(), name = "user$rowId", serverId = on.id, accessToken = "t$rowId")

    private val kid1 = user(1)
    private val kid2 = user(2)
    private val grownUp = user(3)

    private val servers = MutableStateFlow<List<JellyfinServerUsers>>(emptyList())
    private val serverDao = mockk<JellyfinServerDao> { every { observeServers() } returns servers }
    private val kidProfileDao = FakeKidProfileDao()
    private val tvSetupDao = FakeTvSetupDao()

    private fun TestScope.repository() = TvSetupRepository(tvSetupDao, serverDao, kidProfileDao, backgroundScope)

    private suspend fun kids(vararg users: JellyfinUser) =
        users.forEach {
            kidProfileDao.upsert(KidProfile(userId = it.rowId, serverId = it.serverId))
        }

    private fun row(
        step: TvSetupStep,
        on: JellyfinServer = server,
    ) = TvSetupEntity(serverId = on.id, serverUrl = on.url, step = step)

    @Test
    fun `not set up only with no server stored`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            advanceUntilIdle()
            assertEquals(TvState.NotSetUp, repository.state.value)

            // Any stored server means set up, even with no row and no kids (FR-018)
            servers.value = listOf(JellyfinServerUsers(server, emptyList()))
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, emptyList()), repository.state.value)
        }

    @Test
    fun `a setup in progress resumes at its step`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = row(TvSetupStep.KIDS)
            servers.value = listOf(JellyfinServerUsers(server, listOf(kid1)))
            kids(kid1)
            val repository = repository()
            advanceUntilIdle()
            assertEquals(TvState.InSetup(TvSetupStep.KIDS), repository.state.value)

            // Before the server step is stored there is still a row, so it's not "not set up"
            tvSetupDao.row.value = row(TvSetupStep.ADMIN)
            servers.value = emptyList()
            advanceUntilIdle()
            assertEquals(TvState.InSetup(TvSetupStep.ADMIN), repository.state.value)
        }

    @Test
    fun `set up with no kids`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = row(TvSetupStep.DONE)
            // A user without a kid profile isn't one of this TV's kids
            servers.value = listOf(JellyfinServerUsers(server, listOf(grownUp)))
            val repository = repository()
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, emptyList()), repository.state.value)
        }

    @Test
    fun `set up with one kid`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = row(TvSetupStep.DONE)
            servers.value = listOf(JellyfinServerUsers(server, listOf(grownUp, kid1)))
            kids(kid1)
            val repository = repository()
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, listOf(kid1)), repository.state.value)
        }

    @Test
    fun `set up with several kids, only those of this TV's server`() =
        runTest(UnconfinedTestDispatcher()) {
            val elsewhere = user(4, otherServer)
            tvSetupDao.row.value = row(TvSetupStep.DONE)
            servers.value =
                listOf(
                    JellyfinServerUsers(server, listOf(kid2, grownUp, kid1)),
                    JellyfinServerUsers(otherServer, listOf(elsewhere)),
                )
            kids(kid1, kid2, elsewhere)
            val repository = repository()
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, listOf(kid1, kid2)), repository.state.value)

            // The kids follow the cache
            kidProfileDao.delete(kid2.rowId)
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, listOf(kid1)), repository.state.value)
        }

    @Test
    fun `a missing row with a stored server is set up, on the server of the most recent kid`() =
        runTest(UnconfinedTestDispatcher()) {
            val elsewhere = user(4, otherServer).copy(lastUsed = ZonedDateTime.now().minusDays(1))
            servers.value =
                listOf(
                    JellyfinServerUsers(otherServer, listOf(elsewhere)),
                    JellyfinServerUsers(server, listOf(kid1.copy(lastUsed = ZonedDateTime.now()), grownUp)),
                )
            kids(kid1, elsewhere)
            val repository = repository()
            advanceUntilIdle()
            val state = repository.state.value as TvState.SetUp
            assertEquals(server, state.server)
            assertEquals(listOf(kid1.rowId), state.kids.map { it.rowId })
        }

    @Test
    fun `an unreadable row is set up with a stored server, else not set up`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = row(TvSetupStep.KIDS)
            tvSetupDao.unreadable = true
            servers.value = listOf(JellyfinServerUsers(server, listOf(kid1)))
            kids(kid1)
            val repository = repository()
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, listOf(kid1)), repository.state.value)
        }

    @Test
    fun `an unreadable row with no server stored is not set up`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = row(TvSetupStep.DONE)
            tvSetupDao.unreadable = true
            val repository = repository()
            advanceUntilIdle()
            assertEquals(TvState.NotSetUp, repository.state.value)
        }

    @Test
    fun `a finished row keeps the TV set up when the app database lost its server`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = row(TvSetupStep.DONE)
            val repository = repository()
            advanceUntilIdle()
            val state = repository.state.value as TvState.SetUp
            assertEquals(server.id, state.server.id)
            assertEquals(server.url, state.server.url)
            assertTrue(state.kids.isEmpty())
        }

    @Test
    fun `a row from the migration takes its url from the server row`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = TvSetupEntity(serverId = server.id, serverUrl = "", step = TvSetupStep.DONE)
            servers.value = listOf(JellyfinServerUsers(server, listOf(kid1)))
            kids(kid1)
            val repository = repository()
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, listOf(kid1)), repository.state.value)
        }

    @Test
    fun `steps are stored, then done`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            repository.markStep(server, TvSetupStep.ADMIN)
            advanceUntilIdle()
            assertEquals(TvState.InSetup(TvSetupStep.ADMIN), repository.state.value)
            assertEquals(server.url, tvSetupDao.row.value?.serverUrl)
            assertNull(tvSetupDao.row.value?.completedAt)

            servers.value = listOf(JellyfinServerUsers(server, listOf(kid1)))
            kids(kid1)
            repository.markDone(server)
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, listOf(kid1)), repository.state.value)
            assertEquals(TvSetupStep.DONE, tvSetupDao.row.value?.step)
            assertNotNull(tvSetupDao.row.value?.completedAt)
        }

    @Test
    fun `the last kid is updated`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = row(TvSetupStep.DONE)
            servers.value = listOf(JellyfinServerUsers(server, listOf(kid1, kid2)))
            kids(kid1, kid2)
            val repository = repository()
            advanceUntilIdle()
            assertNull((repository.state.value as TvState.SetUp).lastKidRowId)

            repository.setLastKid(server, kid2.rowId)
            advanceUntilIdle()
            assertEquals(kid2.rowId, (repository.state.value as TvState.SetUp).lastKidRowId)
            assertEquals(TvSetupStep.DONE, tvSetupDao.row.value?.step)

            repository.setLastKid(server, kid1.rowId)
            advanceUntilIdle()
            assertEquals(kid1.rowId, (repository.state.value as TvState.SetUp).lastKidRowId)
        }

    @Test
    fun `the last kid is kept without a row, as a finished setup`() =
        runTest(UnconfinedTestDispatcher()) {
            servers.value = listOf(JellyfinServerUsers(server, listOf(kid1)))
            kids(kid1)
            val repository = repository()
            repository.setLastKid(server, kid1.rowId)
            advanceUntilIdle()
            assertEquals(TvState.SetUp(server, listOf(kid1), lastKidRowId = kid1.rowId), repository.state.value)
            assertEquals(TvSetupStep.DONE, tvSetupDao.row.value?.step)
        }

    @Test
    fun `erase forgets the setup`() =
        runTest(UnconfinedTestDispatcher()) {
            tvSetupDao.row.value = row(TvSetupStep.DONE)
            val repository = repository()
            repository.erase()
            advanceUntilIdle()
            assertNull(tvSetupDao.row.value)
            assertEquals(TvState.NotSetUp, repository.state.value)
        }
}
