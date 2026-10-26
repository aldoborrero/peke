package dev.aldoborrero.peke.services

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.RestoredSession
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.UserConfigCache
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.data.model.ServerUserConfig
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvSetupStep
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.AppPreferencesSerializer
import dev.aldoborrero.peke.services.hilt.pekeKidDeviceId
import dev.aldoborrero.peke.test.nonBlankString
import dev.aldoborrero.peke.ui.successResponse
import dev.aldoborrero.peke.ui.toServerString
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val temporaryFolder: TemporaryFolder =
        TemporaryFolder
            .builder()
            .assureDeletion()
            .build()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockJellyfin = mockk<Jellyfin>()
    private val mockJellyfinServerDao = mockk<JellyfinServerDao>()
    private val mockApiClient = mockk<ApiClient>()
    private val dataStore =
        DataStoreFactory.create(
            serializer = AppPreferencesSerializer(),
            produceFile = { temporaryFolder.newFile("test_datastore.pb") },
            scope = CoroutineScope(testDispatcher),
            corruptionHandler =
                ReplaceFileCorruptionHandler(
                    produceNewData = { AppPreferences.getDefaultInstance() },
                ),
        )

    private val mockUserApi = mockk<UserApi>()
    private val mockSystemApi = mockk<SystemApi>()
    private val tvState = MutableStateFlow<TvState>(TvState.NotSetUp)
    private val tvSetupRepository = mockk<TvSetupRepository> { every { state } returns tvState }
    private val installDevice = DeviceInfo("install-id", "Living room TV")

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        PekeDispatchers.configure(testDispatcher)

        every { mockApiClient.userApi } returns mockUserApi
        every { mockApiClient.systemApi } returns mockSystemApi
        coEvery { mockUserApi.getCurrentUser() } returns successResponse(userDto)
        coEvery { mockSystemApi.getPublicSystemInfo() } returns
            Response(
                PublicSystemInfo(
                    id = serverId.toServerString(),
                    serverName = "test server",
                    version = "10.11.11",
                ),
                200,
                emptyMap(),
            )
        coEvery { mockJellyfinServerDao.addOrUpdateUser(user) } returns user
        coEvery { mockJellyfinServerDao.getServer(serverId) } returns
            JellyfinServerUsers(server, listOf(user))
        coEvery { mockJellyfinServerDao.addOrUpdateServer(server) } just Runs
        every { mockApiClient.clientInfo } returns ClientInfo("Peke test", "0.0.1")
        every { mockApiClient.deviceInfo } returns DeviceInfo("Peke test ID", "Peke test device")
        every { mockApiClient.update(any(), any(), any(), any()) } just Runs
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        PekeDispatchers.reset()
    }

    private val userConfigCache = mockk<UserConfigCache>(relaxed = true).also { coEvery { it.get(any()) } returns null }
    private val appVisibility = AppVisibility()

    private fun create() =
        ServerRepository(
            mockContext,
            mockJellyfin,
            mockJellyfinServerDao,
            mockApiClient,
            installDevice,
            testDispatcher,
            tvSetupRepository,
            mockk(relaxed = true),
            userConfigCache,
            appVisibility,
        )

    private val serverId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val server = JellyfinServer(serverId, "test server", "http://localhost:8096", "10.11.11")
    private val user =
        JellyfinUser(
            rowId = 1,
            id = userId,
            serverId = serverId,
            name = "test-user",
            accessToken = "token",
            pin = "1234",
        )
    private val userDto =
        UserDto(
            id = userId,
            name = "test-user",
            serverName = "test server",
            hasPassword = true,
            hasConfiguredPassword = true,
            hasConfiguredEasyPassword = false,
        )
    private val serverUserConfig = ServerUserConfig(userDto)

    private fun setUpCurrentUser(
        serverRepository: ServerRepository,
        currentUser: JellyfinUser = user,
    ) {
        every { mockApiClient.baseUrl } returns server.url
        every { mockApiClient.accessToken } returns user.accessToken
        Assert.assertNull(serverRepository.currentUser)
        (serverRepository.current as MutableStateFlow).update {
            CurrentUser(server, currentUser)
        }
        Assert.assertNotNull(serverRepository.currentUser)
    }

    @Test
    fun `Test overriding state`() {
        val serverRepository = create()
        Assert.assertNull(serverRepository.currentServer)
        Assert.assertNull(serverRepository.currentUser)
        setUpCurrentUser(serverRepository, currentUser = user)
        Assert.assertEquals(serverId, serverRepository.currentServer?.id)
        Assert.assertEquals(userId, serverRepository.currentUser?.id)
    }

    @Test
    fun `Test changeUser`() =
        runTest {
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(user) } returns user

            val serverRepository = create()
            Assert.assertNull(serverRepository.currentUser)

            serverRepository.changeUser(server, user)
            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateServer(server) }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(user) }

            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)
            Assert.assertEquals(serverUserConfig, serverRepository.currentUserDto)

            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(user) }
        }

    @Test
    fun `Test restoreUser via changeUser`() =
        runTest {
            val user = user.copy(pin = null)
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(user) } returns user
            every { mockJellyfinServerDao.getServer(serverId) } returns JellyfinServerUsers(server, listOf(user))

            val serverRepository = create()
            Assert.assertNull(serverRepository.currentUser)
            val result = serverRepository.tryChangeUser(server.id, user.id)
            assertTrue(result != null)
            assertEquals(serverId, result?.server?.id)
            assertEquals(user.id, result?.user?.id)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)
            Assert.assertEquals(serverUserConfig, serverRepository.currentUserDto)
        }

    @Test
    fun `Test remove user`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteUser(any(), any()) } just Runs

            val serverRepository = create()
            Assert.assertNull(serverRepository.currentUser)

            serverRepository.removeUser(user)
            verify(exactly = 1) { mockJellyfinServerDao.deleteUser(serverId, userId) }
        }

    @Test
    fun `Test remove different user than current`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteUser(any(), any()) } just Runs

            val otherUser =
                JellyfinUser(
                    rowId = 2,
                    id = UUID.randomUUID(),
                    name = "other",
                    serverId = serverId,
                    accessToken = "other token",
                )
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = otherUser)
            Assert.assertEquals(otherUser, serverRepository.currentUser)

            serverRepository.removeUser(user)
            verify(exactly = 0) { mockApiClient.update(accessToken = null) }
            verify(exactly = 1) { mockJellyfinServerDao.deleteUser(serverId, userId) }
            Assert.assertEquals(otherUser, serverRepository.currentUser)
        }

    @Test
    fun `Test remove current user`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteUser(any(), any()) } just Runs
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = user)

            serverRepository.removeUser(user)

            Assert.assertNull(serverRepository.current.value)
            verify {
                mockApiClient.update(
                    accessToken = null,
                    baseUrl = nonBlankString(),
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.deleteUser(serverId, userId) }
        }

    @Test
    fun `Test remove different server than current`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteServer(any()) } just Runs
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = user)

            val toRemove = JellyfinServer(UUID.randomUUID(), null, "http://jellyfin.local", null)
            serverRepository.removeServer(toRemove)

            Assert.assertEquals(server, serverRepository.currentServer)
            verify(exactly = 0) {
                mockApiClient.update(
                    accessToken = any(),
                    baseUrl = any(),
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.deleteServer(toRemove.id) }
        }

    @Test
    fun `Test remove current server`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteServer(any()) } just Runs
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = user)

            serverRepository.removeServer(server)

            Assert.assertNull(serverRepository.current.value)
            verify {
                mockApiClient.update(
                    accessToken = null,
                    baseUrl = null,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.deleteServer(serverId) }
        }

    @Test
    fun `Test changeUser via auth`() =
        runTest {
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(any()) } returns user

            val authResult =
                AuthenticationResult(
                    user = userDto,
                    serverId = serverId.toServerString(),
                    accessToken = user.accessToken,
                )
            val serverRepository = create()
            serverRepository.changeUser(server.url, authResult, null)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateServer(server) }
            val savedUser = slot<JellyfinUser>()
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(capture(savedUser)) }
            // Newly created user, so rowId should be 0
            Assert.assertEquals(0, savedUser.captured.rowId)
            Assert.assertEquals(user.id, savedUser.captured.id)

            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)
            Assert.assertEquals(serverUserConfig, serverRepository.currentUserDto)
        }

    @Test
    fun `Test changeUser via auth for existing`() =
        runTest {
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(any()) } returns user

            val authResult =
                AuthenticationResult(
                    user = userDto,
                    serverId = serverId.toServerString(),
                    accessToken = user.accessToken,
                )
            val serverRepository = create()
            serverRepository.changeUser(server.url, authResult, user)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateServer(server) }
            val savedUser = slot<JellyfinUser>()
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(capture(savedUser)) }
            // Existing user, so rowId should not change
            Assert.assertEquals(user.rowId, savedUser.captured.rowId)
            Assert.assertEquals(user.id, savedUser.captured.id)
            // PIN should be removed when re-authing existing user
            Assert.assertNull(savedUser.captured.pin)

            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)
            Assert.assertEquals(serverUserConfig, serverRepository.currentUserDto)
        }

    @Test
    fun `Test changeUser via invalid auth`() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val authResult =
                    AuthenticationResult(
                        user = userDto,
                        serverId = serverId.toServerString(),
                        accessToken = null,
                    )
                create().changeUser(server.url, authResult, user)
            }
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val authResult =
                    AuthenticationResult(
                        user = userDto,
                        serverId = null,
                        accessToken = user.accessToken,
                    )
                create().changeUser(server.url, authResult, user)
            }
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val authResult =
                    AuthenticationResult(
                        user = userDto,
                        serverId = "invalid-uuid",
                        accessToken = user.accessToken,
                    )
                create().changeUser(server.url, authResult, user)
            }
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val authResult =
                    AuthenticationResult(
                        user = null,
                        serverId = "invalid-uuid",
                        accessToken = user.accessToken,
                    )
                create().changeUser(server.url, authResult, user)
            }
        }
    }

    @Test
    fun `restoreLastSession restores this TV's last kid`() =
        runTest {
            val user = user.copy(pin = null)
            val sibling = user.copy(rowId = user.rowId + 1, id = UUID.randomUUID(), name = "sibling", accessToken = "s")
            coEvery { mockJellyfinServerDao.addOrUpdateUser(user) } returns user
            coEvery { mockJellyfinServerDao.getServer(serverId) } returns JellyfinServerUsers(server, listOf(user, sibling))
            tvState.value = TvState.SetUp(server, listOf(sibling, user), lastKidRowId = user.rowId)

            val result = create().restoreLastSession()
            assertTrue(result is RestoredSession.Success)
            val current = (result as RestoredSession.Success).currentUser
            assertEquals(server, current.server)
            assertEquals(user, current.user)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
        }

    @Test
    fun `restoreLastSession restores the only kid when no kid was last`() =
        runTest {
            val user = user.copy(pin = null)
            coEvery { mockJellyfinServerDao.addOrUpdateUser(user) } returns user
            coEvery { mockJellyfinServerDao.getServer(serverId) } returns JellyfinServerUsers(server, listOf(user))
            tvState.value = TvState.SetUp(server, listOf(user))

            val result = create().restoreLastSession()
            assertEquals(CurrentUser(server, user), (result as RestoredSession.Success).currentUser)
        }

    @Test
    fun `restoreLastSession picks no kid among several without a last one`() =
        runTest {
            val sibling = user.copy(rowId = user.rowId + 1, id = UUID.randomUUID(), name = "sibling", accessToken = "s")
            tvState.value = TvState.SetUp(server, listOf(user, sibling))

            val result = create().restoreLastSession()
            assertEquals(RestoredSession.ServerOnly(server), result)
            coVerify(exactly = 0) { mockUserApi.getCurrentUser() }
        }

    @Test
    fun `restoreLastSession restores no kid who needs a grown-up`() =
        runTest {
            tvState.value = TvState.SetUp(server, listOf(user.copy(accessToken = null)), lastKidRowId = user.rowId)

            assertEquals(RestoredSession.ServerOnly(server), create().restoreLastSession())
            coVerify(exactly = 0) { mockUserApi.getCurrentUser() }
        }

    @Test
    fun `restoreLastSession restores nobody on a TV that isn't set up`() =
        runTest {
            tvState.value = TvState.NotSetUp
            assertTrue(create().restoreLastSession() is RestoredSession.None)
            tvState.value = TvState.InSetup(TvSetupStep.KIDS)
            assertTrue(create().restoreLastSession() is RestoredSession.None)
            coVerify(exactly = 0) { mockUserApi.getCurrentUser() }
        }

    private fun kidDevice(id: UUID = userId) = DeviceInfo(pekeKidDeviceId(installDevice.id, id), installDevice.name)

    @Test
    fun `changeUser sends the user's own device id`() =
        runTest {
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(user) } returns user

            create().changeUser(server, user)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = kidDevice(),
                )
            }
        }

    @Test
    fun `restoreUser opens a saved user without the network`() =
        runTest {
            val serverRepository = create()

            val current = serverRepository.restoreUser(server, user)

            assertEquals(CurrentUser(server, user), current)
            assertEquals(current, serverRepository.current.value)
            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = kidDevice(),
                )
            }
            coVerify(exactly = 0) { mockUserApi.getCurrentUser() }
            coVerify(exactly = 0) { mockSystemApi.getPublicSystemInfo() }
            verify(exactly = 0) { mockJellyfinServerDao.addOrUpdateUser(any()) }
            // Not known until the background refresh
            Assert.assertNull(serverRepository.currentUserDto)
        }

    @Test
    fun `restoreUser refuses a user of another server`() =
        runTest {
            val serverRepository = create()
            val other = JellyfinServer(UUID.randomUUID(), null, "http://other.local", null)
            val refused =
                try {
                    serverRepository.restoreUser(other, user)
                    false
                } catch (_: IllegalStateException) {
                    true
                }
            assertTrue(refused)
            Assert.assertNull(serverRepository.current.value)
        }

    @Test
    fun `removing the current user goes back to the install device id`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteUser(any(), any()) } just Runs
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = user)

            serverRepository.removeUser(user)

            verify {
                mockApiClient.update(
                    baseUrl = any(),
                    accessToken = null,
                    clientInfo = any(),
                    deviceInfo = installDevice,
                )
            }
        }

    @Test
    fun `adding a server talks to it with the install device id`() =
        runTest {
            coEvery { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            create().addAndChangeServer(server)

            verify {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = null,
                    clientInfo = any(),
                    deviceInfo = installDevice,
                )
            }
        }

    private fun authenticationResult(token: String) =
        AuthenticationResult(user = userDto, accessToken = token, serverId = serverId.toServerString())

    @Test
    fun `saving a signed-in kid doesn't make them current`() =
        runTest(testDispatcher) {
            val other = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "other", serverId = serverId, accessToken = "other")
            every { mockJellyfinServerDao.addOrUpdateUser(any()) } answers { firstArg<JellyfinUser>().copy(rowId = 1) }
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = other)

            val saved = serverRepository.saveUser(server.url, authenticationResult("new-token"), null)

            assertEquals("new-token", saved.user.accessToken)
            assertEquals(server, saved.server)
            // The kid on screen stays current, with their own token on the app's client
            assertEquals(other, serverRepository.currentUser)
            verify(exactly = 0) { mockApiClient.update(any(), any(), any(), any()) }
            coVerify { userConfigCache.save(userDto) }
            // The stored server keeps its name and version
            verify(exactly = 0) { mockJellyfinServerDao.addOrUpdateServer(any()) }
        }

    @Test
    fun `saving the current kid signed in again moves their session to the new token`() =
        runTest(testDispatcher) {
            every { mockJellyfinServerDao.addOrUpdateUser(any()) } answers { firstArg() }
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = user)

            serverRepository.saveUser(server.url, authenticationResult("new-token"), user)

            assertEquals("new-token", serverRepository.currentUser?.accessToken)
            // An old PIN is dropped with the new sign-in
            assertEquals(null, serverRepository.currentUser?.pin)
            verify { mockApiClient.update(baseUrl = server.url, accessToken = "new-token", clientInfo = any(), deviceInfo = any()) }
        }

    @Test
    fun `a kid opened from the stored row starts with their cached configuration`() =
        runTest(testDispatcher) {
            coEvery { userConfigCache.get(userId) } returns userDto
            val serverRepository = create()

            serverRepository.restoreUser(server, user)

            assertEquals(serverUserConfig, serverRepository.currentUserDto)
            // Nothing asked the server
            coVerify(exactly = 0) { mockUserApi.getCurrentUser() }
        }

    @Test
    fun `a refreshed configuration is cached for the next start`() =
        runTest(testDispatcher) {
            val serverRepository = create()
            setUpCurrentUser(serverRepository)

            serverRepository.updateUserDto()

            assertEquals(serverUserConfig, serverRepository.currentUserDto)
            coVerify { userConfigCache.save(userDto) }
        }

    @Test
    fun `background work doesn't switch user while the app's pages are on screen`() =
        runTest(testDispatcher) {
            tvState.value = TvState.SetUp(server, listOf(user), lastKidRowId = user.rowId)
            appVisibility.started = true
            val serverRepository = create()

            assertEquals(RestoredSession.None, serverRepository.restoreLastSession())
            Assert.assertNull(serverRepository.currentUser)
            coVerify(exactly = 0) { mockUserApi.getCurrentUser() }
        }

    @Test
    fun `a refused token leaves its row without a token, and nobody current`() =
        runTest(testDispatcher) {
            every { mockJellyfinServerDao.getServers() } returns listOf(JellyfinServerUsers(server, listOf(user)))
            every { mockJellyfinServerDao.addOrUpdateUser(any()) } answers { firstArg() }
            val serverRepository = create()
            setUpCurrentUser(serverRepository)

            assertTrue(serverRepository.forgetToken("token"))

            verify { mockJellyfinServerDao.addOrUpdateUser(user.copy(accessToken = null)) }
            Assert.assertNull(serverRepository.current.value)
            Assert.assertNull(serverRepository.currentUserDto)
            // Some other token: nothing to forget
            assertEquals(false, serverRepository.forgetToken("someone-else"))
        }
}
