package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.signInAnswer
import dev.aldoborrero.peke.kids.userJson
import dev.aldoborrero.peke.services.hilt.pekeKidDeviceId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidSignInTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://jf.local", version = null)
    private val adminId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val kidId = UUID.fromString("c1d00000-0000-0000-0000-000000000001")
    private val kidDevice = pekeKidDeviceId(FakeKidsServer.DEVICE_ID, kidId)
    private val http = FakeKidsServer()

    private val serverDao = mockk<JellyfinServerDao> { every { getServer(any()) } returns JellyfinServerUsers(server, emptyList()) }
    private val savedKid = JellyfinUser(rowId = 7, id = kidId, name = "Manuel", serverId = server.id, accessToken = "kid-token")
    private val serverRepository =
        mockk<ServerRepository> {
            coEvery { saveUser(any<String>(), any<AuthenticationResult>(), any()) } returns CurrentUser(server, savedKid)
        }
    private val kidProfileRepository =
        mockk<KidProfileRepository> {
            coEvery { refresh(any(), any()) } returns true
            coEvery { isKid(any()) } returns true
        }

    /**
     * Everything the app logs while a test runs, to check that no secret, token or password reaches the log
     */
    private val logged = mutableListOf<String>()
    private val tree =
        object : Timber.Tree() {
            override fun log(
                priority: Int,
                tag: String?,
                message: String,
                t: Throwable?,
            ) {
                // MockK logs the mocked calls with their arguments through the same tree; only the app's own lines count
                if (!message.startsWith("Answering ")) logged += message + (t?.toString() ?: "")
            }
        }

    @Before
    fun plantTree() = Timber.plant(tree)

    @After
    fun uprootTree() = Timber.uproot(tree)

    private fun TestScope.signIn() =
        KidSignIn(
            http.jellyfin,
            http.deviceInfo,
            serverRepository,
            serverDao,
            kidProfileRepository,
            UnconfinedTestDispatcher(testScheduler),
        )

    private suspend fun TestScope.admin(): AdminSession {
        http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token", adminId))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val opened = AdminSessions(http.jellyfin, http.deviceInfo, http.api(dispatcher), dispatcher).open(server, "admin", "pw")
        http.requests.clear()
        return (opened as AdminSignIn.Opened).session
    }

    private fun quickConnectRequest(
        code: String,
        secret: String,
        authenticated: Boolean = false,
    ) = """{"Authenticated":$authenticated,"Secret":"$secret","Code":"$code","DeviceId":"d","DeviceName":"TV","AppName":"Peke",""" +
        """"AppVersion":"test","DateAdded":"2026-10-23T10:00:00Z"}"""

    private fun quickConnectOn(secret: String = "s3cret-kid") {
        http.on("GET", "/QuickConnect/Enabled", 200, "true")
        http.on("POST", "/QuickConnect/Initiate", 200, quickConnectRequest("111111", secret))
        http.on("POST", "/QuickConnect/Authorize", 200, "true")
        http.on("POST", "/Users/AuthenticateWithQuickConnect", 200, signInAnswer(false, "kid-token", kidId))
        http.on("GET", "/Users/Me", 200, userJson(kidId, name = "Manuel"))
        http.on("POST", "/Sessions/Logout", 204)
    }

    private fun request(path: String) = http.requests.first { it.path == path }

    private fun requests(path: String) = http.requests.filter { it.path == path }

    private fun deviceIdOf(request: FakeKidsServer.Recorded) =
        Regex("DeviceId=\"([^\"]+)\"").find(request.headers["Authorization"]!!)!!.groupValues[1]

    private fun tokenOf(request: FakeKidsServer.Recorded) =
        Regex("Token=\"([^\"]+)\"").find(request.headers["Authorization"]!!)?.groupValues?.get(1)

    @Test
    fun `quick connect signs the kid in on their own device, approved by the administrator`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            val admin = admin()

            val result = signIn().withQuickConnect(admin, kidId)

            assertEquals(KidSignInResult.SignedIn(savedKid), result)
            // Initiate goes out as the kid's own device, with no token
            val initiate = request("/QuickConnect/Initiate")
            assertEquals(kidDevice, deviceIdOf(initiate))
            assertEquals(null, tokenOf(initiate))
            // The administrator approves it for the kid, from the administrator's own device
            val authorize = request("/QuickConnect/Authorize")
            assertEquals("admin-token", tokenOf(authorize))
            assertEquals(admin.deviceId, deviceIdOf(authorize))
            val query = authorize.query!!
            assertTrue(query.contains("code=111111"))
            assertTrue(query.contains("userId=$kidId") || query.contains("userId=${kidId.toString().replace("-", "")}"))
            // The secret is redeemed as the kid's device
            val redeem = request("/Users/AuthenticateWithQuickConnect")
            assertEquals(kidDevice, deviceIdOf(redeem))
            assertTrue(redeem.body!!.contains("s3cret-kid"))
            // The new token is checked before it is saved
            val me = request("/Users/Me")
            assertEquals("kid-token", tokenOf(me))
            assertEquals(kidDevice, deviceIdOf(me))
            coVerify(exactly = 1) { serverRepository.saveUser(server.url, match { it.accessToken == "kid-token" }, null) }
            // Saved, not made current: the pages on screen stay whoever's they were
            coVerify(exactly = 0) { serverRepository.changeUser(any<String>(), any<AuthenticationResult>(), any()) }
            coVerify(exactly = 0) { serverRepository.changeUser(any<JellyfinServer>(), any<JellyfinUser>()) }
            coVerify { kidProfileRepository.refresh(server, savedKid) }
            // The administrator session stays open for the next kid
            assertFalse(admin.isClosed)
            assertTrue(requests("/Sessions/Logout").isEmpty())
        }

    @Test
    fun `a kid already on this TV keeps their row`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            val existing = savedKid.copy(accessToken = "old-token")
            every { serverDao.getServer(server.id) } returns JellyfinServerUsers(server, listOf(existing))

            signIn().withQuickConnect(admin(), kidId)

            coVerify { serverRepository.saveUser(server.url, any(), existing) }
        }

    @Test
    fun `a kid not allowed on this device is told apart`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("POST", "/QuickConnect/Authorize", 403)
            http.on("GET", "/Users/$kidId", 200, userJson(kidId, name = "Manuel", enableAllDevices = false, maxActiveSessions = 2))

            assertEquals(KidSignInResult.DeviceNotAllowed, signIn().withQuickConnect(admin(), kidId))
            assertEquals("admin-token", tokenOf(request("/Users/$kidId")))
            assertTrue(requests("/Users/AuthenticateWithQuickConnect").isEmpty())
            coVerify(exactly = 0) { serverRepository.saveUser(any<String>(), any<AuthenticationResult>(), any()) }
        }

    @Test
    fun `a kid on too many devices is told apart`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("POST", "/QuickConnect/Authorize", 403)
            http.on("GET", "/Users/$kidId", 200, userJson(kidId, enableAllDevices = true, maxActiveSessions = 2))

            assertEquals(KidSignInResult.TooManySessions, signIn().withQuickConnect(admin(), kidId))
            coVerify(exactly = 0) { serverRepository.saveUser(any<String>(), any<AuthenticationResult>(), any()) }
        }

    @Test
    fun `an expired code starts a new request`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("POST", "/QuickConnect/Authorize", 404)
            http.onRequest = { recorded ->
                if (recorded.path == "/QuickConnect/Initiate" && requests("/QuickConnect/Authorize").isNotEmpty()) {
                    // The second request, started after the first code expired, is approved
                    http.on("POST", "/QuickConnect/Authorize", 200, "true")
                    http.on("POST", "/QuickConnect/Initiate", 200, quickConnectRequest("222222", "s3cret-kid-2"))
                }
            }

            val result = signIn().withQuickConnect(admin(), kidId)

            assertEquals(KidSignInResult.SignedIn(savedKid), result)
            assertEquals(2, requests("/QuickConnect/Initiate").size)
            assertTrue(requests("/QuickConnect/Authorize")[1].query!!.contains("code=222222"))
            assertTrue(request("/Users/AuthenticateWithQuickConnect").body!!.contains("s3cret-kid-2"))
        }

    @Test
    fun `a code that keeps expiring gives up`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("POST", "/QuickConnect/Authorize", 404)

            assertEquals(KidSignInResult.CodeExpired, signIn().withQuickConnect(admin(), kidId))
            assertEquals(KidSignIn.QUICK_CONNECT_ATTEMPTS, requests("/QuickConnect/Initiate").size)
        }

    @Test
    fun `an unknown kid is refused`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("POST", "/QuickConnect/Authorize", 400)

            assertEquals(KidSignInResult.UnknownUser, signIn().withQuickConnect(admin(), kidId))
            coVerify(exactly = 0) { serverRepository.saveUser(any<String>(), any<AuthenticationResult>(), any()) }
        }

    @Test
    fun `a token the server refuses is not saved, and its session ends`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("GET", "/Users/Me", 403)

            assertEquals(KidSignInResult.AccountRefused, signIn().withQuickConnect(admin(), kidId))
            coVerify(exactly = 0) { serverRepository.saveUser(any<String>(), any<AuthenticationResult>(), any()) }
            val logout = requests("/Sessions/Logout").single()
            assertEquals("kid-token", tokenOf(logout))
            assertEquals(kidDevice, deviceIdOf(logout))
        }

    @Test
    fun `quick connect off asks for the password`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("GET", "/QuickConnect/Enabled", 200, "false")

            assertEquals(KidSignInResult.QuickConnectOff, signIn().withQuickConnect(admin(), kidId))
            assertTrue(requests("/QuickConnect/Initiate").isEmpty())
        }

    @Test
    fun `the password fallback signs the kid in once, on their own device`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(false, "kid-token", kidId))
            http.on("GET", "/Users/Me", 200, userJson(kidId, name = "Manuel"))

            val result = signIn().withPassword(server, kidId, "Manuel", "kid-password")

            assertEquals(KidSignInResult.SignedIn(savedKid), result)
            // Checked before verifying, since MockK logs the arguments it verifies
            assertTrue("Logged: $logged", logged.none { it.contains("kid-password") || it.contains("kid-token") })
            val signIn = request("/Users/AuthenticateByName")
            assertEquals(kidDevice, deviceIdOf(signIn))
            assertEquals(1, http.requests.count { it.body?.contains("kid-password") == true })
            assertEquals(kidDevice, deviceIdOf(request("/Users/Me")))
            coVerify(exactly = 1) { serverRepository.saveUser(server.url, match { it.accessToken == "kid-token" }, null) }
        }

    @Test
    fun `a wrong password is refused`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("POST", "/Users/AuthenticateByName", 401)

            assertEquals(KidSignInResult.WrongPassword, signIn().withPassword(server, kidId, "Manuel", "nope"))
            coVerify(exactly = 0) { serverRepository.saveUser(any<String>(), any<AuthenticationResult>(), any()) }
        }

    @Test
    fun `a server that can't be reached`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            val admin = admin()
            http.offline = true

            assertEquals(KidSignInResult.Unreachable, signIn().withQuickConnect(admin, kidId))
        }

    @Test
    fun `a kid limited to devices that include this TV is told about their sessions`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("POST", "/QuickConnect/Authorize", 403)
            http.on(
                "GET",
                "/Users/$kidId",
                200,
                userJson(
                    kidId,
                    enableAllDevices = false,
                    maxActiveSessions = 1,
                ).replace("\"Policy\":{", "\"Policy\":{\"EnabledDevices\":[\"$kidDevice\"],"),
            )

            assertEquals(KidSignInResult.TooManySessions, signIn().withQuickConnect(admin(), kidId))
        }

    @Test
    fun `a refusal the kid's policy doesn't explain is a plain failure`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            http.on("POST", "/QuickConnect/Authorize", 403)
            http.on("GET", "/Users/$kidId", 200, userJson(kidId, enableAllDevices = true, maxActiveSessions = 0))
            assertEquals(KidSignInResult.Failed(403), signIn().withQuickConnect(admin(), kidId))

            http.on("GET", "/Users/$kidId", 500)
            assertEquals(KidSignInResult.Failed(403), signIn().withQuickConnect(admin(), kidId))
        }

    @Test
    fun `a 401 with quick connect on means the administrator signs in again`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            // The administrator's session ended on the server, for example after a restart
            http.on("POST", "/QuickConnect/Authorize", 401)

            assertEquals(KidSignInResult.AdminExpired, signIn().withQuickConnect(admin(), kidId))

            // Turned off since the first check: that is what to fix
            http.onRequest = { recorded ->
                if (recorded.path == "/QuickConnect/Authorize") http.on("GET", "/QuickConnect/Enabled", 200, "false")
            }
            assertEquals(KidSignInResult.QuickConnectOff, signIn().withQuickConnect(admin(), kidId))
            coVerify(exactly = 0) { serverRepository.saveUser(any(), any(), any()) }
        }

    @Test
    fun `a kid unmarked while signing in isn't reported as signed in`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn()
            coEvery { kidProfileRepository.isKid(any()) } returns false

            assertEquals(KidSignInResult.UnknownUser, signIn().withQuickConnect(admin(), kidId))
        }

    @Test
    fun `the secret is never logged or stored`() =
        runTest(UnconfinedTestDispatcher()) {
            quickConnectOn(secret = "s3cret-kid")

            val result = signIn().withQuickConnect(admin(), kidId)

            assertTrue(result is KidSignInResult.SignedIn)
            assertTrue(
                "Logged: $logged",
                logged.none { it.contains("s3cret-kid") || it.contains("kid-token") || it.contains("admin-token") },
            )
            // Only the redeem request carries it, and nothing that is saved does
            assertEquals(1, http.requests.count { it.body?.contains("s3cret-kid") == true })
            coVerify { serverRepository.saveUser(any<String>(), match { !it.toString().contains("s3cret-kid") }, any()) }
            assertFalse(result.toString().contains("s3cret-kid"))
        }
}
