package dev.aldoborrero.peke.ui.setup

import android.content.Context
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.util.LoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.JellyfinOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.api.operations.SessionApi
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchUserViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val server = JellyfinServer(id = UUID.randomUUID(), name = "server", url = "http://localhost", version = null)
    private val kid = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Manuel", serverId = server.id, accessToken = "k")
    private val grownUp = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "Parent", serverId = server.id, accessToken = "p")

    private val savedUsers = MutableStateFlow<List<JellyfinUser>>(emptyList())
    private val kidRows = MutableStateFlow<Set<Int>>(emptySet())
    private val locked = MutableStateFlow<Boolean?>(false)

    private val userApi = mockk<UserApi>()
    private val sessionApi = mockk<SessionApi>(relaxed = true)
    private val api =
        mockk<ApiClient>(relaxed = true) {
            every { userApi } returns this@SwitchUserViewModelTest.userApi
            every { sessionApi } returns this@SwitchUserViewModelTest.sessionApi
            every { imageApi } returns mockk<ImageApi>(relaxed = true)
            every { systemApi } returns
                mockk<SystemApi> {
                    coEvery { getPublicSystemInfo() } returns
                        Response(mockk<PublicSystemInfo> { every { version } returns "10.11.0" }, 200, emptyMap())
                }
        }
    private val jellyfin =
        mockk<Jellyfin>(relaxed = true) {
            every { createApi(any(), any(), any(), any(), any()) } returns api
            // createApi's defaults read the options field directly, which a mock leaves empty
            Jellyfin::class.java
                .getDeclaredField("options")
                .apply { isAccessible = true }
                .set(this, mockk<JellyfinOptions>(relaxed = true))
        }
    private val serverDao =
        mockk<JellyfinServerDao> {
            every { getServer(server.id) } answers { JellyfinServerUsers(server, savedUsers.value) }
            every { getUser(any(), any()) } answers { savedUsers.value.firstOrNull { it.serverId == firstArg() && it.id == secondArg() } }
        }
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val kidsModeService =
        mockk<KidsModeService>(relaxed = true) {
            every { locked } returns this@SwitchUserViewModelTest.locked
            every { kidRowIds } returns kidRows
            coEvery { isLockedNow() } answers { this@SwitchUserViewModelTest.locked.value == true }
            coEvery { kidUserRowIds() } answers { kidRows.value }
            coEvery { profileFor(any()) } answers
                { firstArg<JellyfinUser?>()?.takeIf { it.rowId in kidRows.value }?.let { KidProfile(it.rowId) } }
        }
    private val context =
        mockk<Context> {
            every { getString(R.string.kids_sign_in_locked) } returns "locked"
            every { getString(R.string.login_sign_in_refused) } returns "refused"
        }

    private fun viewModel() =
        SwitchUserViewModel(
            context,
            jellyfin,
            serverRepository,
            serverDao,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(),
            kidsModeService,
            server,
        )

    private fun signsInAs(user: JellyfinUser) {
        coEvery { userApi.authenticateUserByName(any<AuthenticateUserByName>()) } returns
            Response(
                AuthenticationResult(
                    user = mockk<UserDto> { every { id } returns user.id },
                    accessToken = "new",
                    serverId = server.id.toString(),
                ),
                200,
                emptyMap(),
            )
    }

    @Before
    fun setUp() {
        PekeDispatchers.configure(testDispatcher)
        coEvery { userApi.getPublicUsers() } returns Response(emptyList(), 200, emptyMap())
        coEvery { serverRepository.changeUser(any<String>(), any(), any()) } returns CurrentUser(server, kid)
    }

    @After
    fun tearDown() {
        PekeDispatchers.reset()
    }

    @Test
    fun `the user list is loaded again when a kid signs in and locks the TV`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            viewModel.init()
            advanceUntilIdle()
            assertEquals(
                emptyList<JellyfinUser>(),
                viewModel.state.value.users
                    .map { it.user },
            )

            // Manuel signed in through Add User: saved, found to be a kid, and the TV locked
            savedUsers.value = listOf(kid, grownUp)
            kidRows.value = setOf(kid.rowId)
            locked.value = true
            advanceUntilIdle()
            assertEquals(
                listOf(kid),
                viewModel.state.value.users
                    .map { it.user },
            )
            assertTrue(viewModel.state.value.kidsLocked)
        }

    @Test
    fun `when the TV locks the list shown narrows at once, without the network`() =
        runTest(testDispatcher) {
            savedUsers.value = listOf(kid, grownUp)
            kidRows.value = setOf(kid.rowId)
            val viewModel = viewModel()
            viewModel.init()
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.users.size)

            // The server can't be reached any more, which doesn't matter: nothing new has to appear
            coEvery { userApi.getPublicUsers() } throws InvalidStatusException(503)
            locked.value = true
            advanceUntilIdle()
            assertEquals(
                listOf(kid),
                viewModel.state.value.users
                    .map { it.user },
            )
            assertTrue(viewModel.state.value.kidsLocked)
            coVerify(exactly = 1) { userApi.getPublicUsers() }
        }

    @Test
    fun `a failed reload still narrows the list to kids`() =
        runTest(testDispatcher) {
            savedUsers.value = listOf(grownUp)
            val viewModel = viewModel()
            viewModel.init()
            advanceUntilIdle()
            assertEquals(
                listOf(grownUp),
                viewModel.state.value.users
                    .map { it.user },
            )

            // A kid new on this TV has to appear, but the server doesn't answer
            coEvery { userApi.getPublicUsers() } throws InvalidStatusException(503)
            savedUsers.value = listOf(kid, grownUp)
            kidRows.value = setOf(kid.rowId)
            locked.value = true
            advanceUntilIdle()
            assertEquals(
                emptyList<JellyfinUser>(),
                viewModel.state.value.users
                    .map { it.user },
            )
            assertTrue(viewModel.state.value.kidsLocked)
            assertEquals(LoadingState.Success, viewModel.state.value.loading)
        }

    @Test
    fun `while locked a grown-up's saved tile is refused`() =
        runTest(testDispatcher) {
            savedUsers.value = listOf(kid, grownUp)
            kidRows.value = setOf(kid.rowId)
            locked.value = true
            val viewModel = viewModel()
            val result = viewModel.trySwitchUser(grownUp)
            advanceUntilIdle()
            assertEquals(SwitchUserResult.Error("locked", false), result.await())
            coVerify(exactly = 0) { serverRepository.changeUser(any<JellyfinServer>(), any()) }
        }

    @Test
    fun `opening the user list keeps the most recent session`() =
        runTest(testDispatcher) {
            savedUsers.value = listOf(kid, grownUp)
            val viewModel = viewModel()
            viewModel.init()
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.users.size)
            // Only picking, signing in or removing a user changes the saved session, and none happened
            verify { serverRepository wasNot Called }
        }

    @Test
    fun `while locked a saved kid signing in through Add User renews their row`() =
        runTest(testDispatcher) {
            savedUsers.value = listOf(kid, grownUp)
            kidRows.value = setOf(kid.rowId)
            locked.value = true
            signsInAs(kid)
            val viewModel = viewModel()
            viewModel.login(server, existingUser = null, username = "Manuel", password = "x")
            advanceUntilIdle()
            coVerify(exactly = 1) { serverRepository.changeUser(server.url, any(), kid) }
            coVerify(exactly = 1) { kidsModeService.lockIfKidAfterSignIn(any()) }
            coVerify(exactly = 0) { sessionApi.reportSessionEnded() }
        }

    @Test
    fun `while locked another account is refused and its session ended`() =
        runTest(testDispatcher) {
            savedUsers.value = listOf(kid, grownUp)
            kidRows.value = setOf(kid.rowId)
            locked.value = true
            signsInAs(grownUp)
            val viewModel = viewModel()
            viewModel.login(server, existingUser = null, username = "Parent", password = "x")
            advanceUntilIdle()
            coVerify(exactly = 0) { serverRepository.changeUser(any<String>(), any(), any()) }
            coVerify(exactly = 1) { sessionApi.reportSessionEnded() }
            assertEquals(LoadingState.Error("locked"), viewModel.state.value.switchUserState)
        }

    @Test
    fun `a 403 at sign-in gets a readable message`() =
        runTest(testDispatcher) {
            coEvery { userApi.authenticateUserByName(any<AuthenticateUserByName>()) } throws InvalidStatusException(403)
            val viewModel = viewModel()
            viewModel.login(server, existingUser = null, username = "Manuel", password = "x")
            advanceUntilIdle()
            assertEquals(LoadingState.Error("refused"), viewModel.state.value.switchUserState)
        }
}
