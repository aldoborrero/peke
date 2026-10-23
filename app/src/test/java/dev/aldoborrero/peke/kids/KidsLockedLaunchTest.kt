package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.RestoredSession
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.ZonedDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidsLockedLaunchTest {
    private val testDispatcher = StandardTestDispatcher()

    private val home = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://home", version = null)
    private val other = JellyfinServer(id = UUID.randomUUID(), name = "other", url = "http://other", version = null)
    private val now = ZonedDateTime.now()
    private val manuel = kid(1, home, now)
    private val lucia = kid(2, other, now.minusDays(1))
    private val parent = JellyfinUser(rowId = 3, id = UUID.randomUUID(), name = "Parent", serverId = other.id, accessToken = "p")

    private var lockedNow = true
    private var kidRows = setOf(manuel.rowId)
    private var servers = listOf(JellyfinServerUsers(home, listOf(manuel)), JellyfinServerUsers(other, listOf(parent)))

    private val kidsModeService =
        mockk<KidsModeService> {
            coEvery { isLockedNow() } answers { lockedNow }
            coEvery { kidUserRowIds() } answers { kidRows }
        }
    private val serverRepository = mockk<ServerRepository>()
    private val serverDao = mockk<JellyfinServerDao> { every { getServers() } answers { servers } }
    private val launch = KidsLockedLaunch(kidsModeService, serverRepository, serverDao)

    private fun kid(
        rowId: Int,
        server: JellyfinServer,
        lastUsed: ZonedDateTime,
    ) = JellyfinUser(
        rowId = rowId,
        id = UUID.randomUUID(),
        name = "kid$rowId",
        serverId = server.id,
        accessToken = "k",
        lastUsed = lastUsed,
    )

    @Before
    fun setUp() {
        PekeDispatchers.configure(testDispatcher)
    }

    @After
    fun tearDown() {
        PekeDispatchers.reset()
    }

    @Test
    fun `a locked launch with no saved session signs the only kid in again`() =
        runTest(testDispatcher) {
            val current = CurrentUser(home, manuel)
            coEvery { serverRepository.tryChangeUser(home.id, manuel.id) } returns current
            assertEquals(SetupDestination.AppContent(current), launch.destination(RestoredSession.None, signInAutomatically = true))
        }

    @Test
    fun `a locked launch whose restore failed signs the only kid in again`() =
        runTest(testDispatcher) {
            val current = CurrentUser(home, manuel)
            coEvery { serverRepository.tryChangeUser(home.id, manuel.id) } returns current
            assertEquals(SetupDestination.AppContent(current), launch.destination(null, signInAutomatically = true))
        }

    @Test
    fun `when the only kid's token no longer works the launch goes to the kids list`() =
        runTest(testDispatcher) {
            coEvery { serverRepository.tryChangeUser(home.id, manuel.id) } throws InvalidStatusException(401)
            assertEquals(SetupDestination.UserList(home), launch.destination(RestoredSession.None, signInAutomatically = true))
        }

    @Test
    fun `an only kid without a token or who must sign in goes to the kids list without trying`() =
        runTest(testDispatcher) {
            listOf(manuel.copy(accessToken = null), manuel.copy(requireLogin = true)).forEach { kid ->
                servers = listOf(JellyfinServerUsers(home, listOf(kid)), JellyfinServerUsers(other, listOf(parent)))
                assertEquals(SetupDestination.UserList(home), launch.destination(RestoredSession.None, signInAutomatically = true))
            }
            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
        }

    @Test
    fun `with several kids the launch goes to the kids list of the most recent kid server`() =
        runTest(testDispatcher) {
            kidRows = setOf(manuel.rowId, lucia.rowId)
            servers = listOf(JellyfinServerUsers(other, listOf(parent, lucia)), JellyfinServerUsers(home, listOf(manuel)))
            // The saved session only had a server, here the one without the most recent kid
            assertEquals(SetupDestination.UserList(home), launch.destination(RestoredSession.ServerOnly(other), signInAutomatically = true))
            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
        }

    @Test
    fun `without automatic sign-in the launch goes to the kids list`() =
        runTest(testDispatcher) {
            assertEquals(SetupDestination.UserList(home), launch.destination(RestoredSession.None, signInAutomatically = false))
            coVerify(exactly = 0) { serverRepository.tryChangeUser(any(), any()) }
        }

    @Test
    fun `a restored session or an unlocked TV keeps the usual destination`() =
        runTest(testDispatcher) {
            assertNull(launch.destination(RestoredSession.Success(CurrentUser(home, manuel)), signInAutomatically = true))
            lockedNow = false
            assertNull(launch.destination(RestoredSession.None, signInAutomatically = true))
        }
}
