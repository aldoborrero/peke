package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.LogCapture
import dev.aldoborrero.peke.kids.signInAnswer
import dev.aldoborrero.peke.services.hilt.pekeKidDeviceId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AdminSessionTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://jf.local", version = null)
    private val adminId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val http = FakeKidsServer()

    private fun TestScope.sessions(): AdminSessions {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return AdminSessions(http.jellyfin, http.deviceInfo, http.api(dispatcher), dispatcher)
    }

    private fun signsIn(
        isAdministrator: Boolean = true,
        token: String = "admin-token",
    ) {
        http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(isAdministrator, token, adminId))
        http.on("POST", "/Sessions/Logout", 204)
    }

    private fun logouts() = http.requests.filter { it.path == "/Sessions/Logout" }

    private fun deviceIdOf(request: FakeKidsServer.Recorded) =
        Regex("DeviceId=\"([^\"]+)\"").find(request.headers["Authorization"]!!)!!.groupValues[1]

    private suspend fun AdminSessions.openAdmin(): AdminSession = (open(server, "admin", "secret") as AdminSignIn.Opened).session

    @Test
    fun `the token and the password stay out of the session's text and the log`() =
        runTest(UnconfinedTestDispatcher()) {
            signsIn()
            LogCapture().use { log ->
                val session = sessions().openAdmin()
                assertEquals(adminId, session.userId)
                assertFalse("The token must not show in logs", session.toString().contains("admin-token"))
                session.close()
                assertTrue("Logged: ${log.lines}", log.none("admin-token") && log.none("secret"))
            }
            // The password goes only into the sign-in itself
            assertEquals(1, http.requests.count { it.body?.contains("secret") == true })
        }

    @Test
    fun `the open sessions all close when the app goes to the background`() =
        runTest(UnconfinedTestDispatcher()) {
            signsIn()
            val sessions = sessions()
            val first = sessions.openAdmin()
            val second = sessions.openAdmin()
            first.close()
            assertTrue(sessions.anyOpen)

            sessions.closeAll()

            assertTrue(second.isClosed)
            assertFalse(sessions.anyOpen)
            assertEquals(2, logouts().size)
        }

    @Test
    fun `close ends the session on the server once`() =
        runTest(UnconfinedTestDispatcher()) {
            signsIn()
            val session = sessions().openAdmin()
            session.close()
            session.close()

            val logout = logouts().single()
            assertTrue(logout.headers["Authorization"]!!.contains("Token=\"admin-token\""))
            assertEquals(deviceIdOf(http.requests.first { it.path == "/Users/AuthenticateByName" }), deviceIdOf(logout))
        }

    @Test
    fun `close runs when the screen that opened it is cancelled`() =
        runTest(UnconfinedTestDispatcher()) {
            signsIn()
            val sessions = sessions()
            val opened = CompletableDeferred<Unit>()
            val job =
                launch {
                    sessions.openAdmin().use {
                        opened.complete(Unit)
                        awaitCancellation()
                    }
                }
            opened.await()
            assertTrue(logouts().isEmpty())
            job.cancel()
            job.join()
            assertEquals(1, logouts().size)
        }

    @Test
    fun `use ends the session when its action finishes or fails`() =
        runTest(UnconfinedTestDispatcher()) {
            signsIn()
            val sessions = sessions()
            assertEquals(42, sessions.openAdmin().use { 42 })
            assertEquals(1, logouts().size)
            try {
                sessions.openAdmin().use { throw IllegalStateException("failed") }
            } catch (_: IllegalStateException) {
            }
            assertEquals(2, logouts().size)
        }

    @Test
    fun `the device id is random for each session`() =
        runTest(UnconfinedTestDispatcher()) {
            signsIn()
            val sessions = sessions()
            val first = sessions.openAdmin()
            val second = sessions.openAdmin()

            assertNotEquals(first.deviceId, second.deviceId)
            for (id in listOf(first.deviceId, second.deviceId)) {
                // Never the TV's own id, nor the one a kid's or the administrator's own sessions use
                assertNotEquals(FakeKidsServer.DEVICE_ID, id)
                assertNotEquals(pekeKidDeviceId(FakeKidsServer.DEVICE_ID, adminId), id)
            }
            val signIns = http.requests.filter { it.path == "/Users/AuthenticateByName" }
            assertEquals(listOf(first.deviceId, second.deviceId), signIns.map { deviceIdOf(it) })
        }

    @Test
    fun `an account that isn't an administrator is refused and signed out`() =
        runTest(UnconfinedTestDispatcher()) {
            signsIn(isAdministrator = false, token = "kid-token")
            assertEquals(AdminSignIn.NotAdmin, sessions().open(server, "kid", "pw"))
            assertTrue(logouts().single().headers["Authorization"]!!.contains("Token=\"kid-token\""))
        }

    @Test
    fun `sign-in failures`() =
        runTest(UnconfinedTestDispatcher()) {
            val sessions = sessions()
            http.on("POST", "/Users/AuthenticateByName", 401)
            assertEquals(AdminSignIn.Wrong, sessions.open(server, "admin", "nope"))
            http.on("POST", "/Users/AuthenticateByName", 500)
            assertEquals(AdminSignIn.Failed(500), sessions.open(server, "admin", "pw"))
            http.offline = true
            assertEquals(AdminSignIn.Unreachable, sessions.open(server, "admin", "pw"))
            assertTrue(logouts().isEmpty())
        }

    @Test
    fun `quick connect signs the administrator in on the session's own device`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on(
                "POST",
                "/QuickConnect/Initiate",
                200,
                """{"Authenticated":false,"Secret":"s3cret","Code":"123456","DeviceId":"d","DeviceName":"TV","AppName":"Peke",""" +
                    """"AppVersion":"test","DateAdded":"2026-10-23T10:00:00Z"}""",
            )
            http.on("POST", "/Users/AuthenticateWithQuickConnect", 200, signInAnswer(true, "admin-token", adminId))
            http.on("POST", "/Sessions/Logout", 204)
            val codes = mutableListOf<String>()
            val result =
                sessions().openWithQuickConnect(server) { code ->
                    codes += code
                    // The administrator approves the code elsewhere, after the first check
                    assertTrue(http.requests.none { it.path == "/QuickConnect/Connect" })
                    http.on(
                        "GET",
                        "/QuickConnect/Connect",
                        200,
                        """{"Authenticated":true,"Secret":"s3cret","Code":"123456","DeviceId":"d","DeviceName":"TV","AppName":"Peke",""" +
                            """"AppVersion":"test","DateAdded":"2026-10-23T10:00:00Z"}""",
                    )
                }
            assertEquals(listOf("123456"), codes)

            val session = (result as AdminSignIn.Opened).session
            assertEquals(1, http.requests.count { it.path == "/QuickConnect/Connect" })
            val initiate = http.requests.first { it.path == "/QuickConnect/Initiate" }
            val redeem = http.requests.first { it.path == "/Users/AuthenticateWithQuickConnect" }
            assertEquals(session.deviceId, deviceIdOf(initiate))
            assertEquals(session.deviceId, deviceIdOf(redeem))
            session.close()
            assertEquals(1, logouts().size)
        }

    @Test
    fun `quick connect turned off is told apart`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("POST", "/QuickConnect/Initiate", 401)
            assertEquals(AdminSignIn.QuickConnectOff, sessions().openWithQuickConnect(server) { })

            // Turned off while the code waits
            http.on("POST", "/QuickConnect/Initiate", 200, quickConnectRequest(authenticated = false))
            http.on("GET", "/QuickConnect/Connect", 401)
            assertEquals(AdminSignIn.QuickConnectOff, sessions().openWithQuickConnect(server) { })
        }

    private fun quickConnectRequest(authenticated: Boolean) =
        """{"Authenticated":$authenticated,"Secret":"s3cret-admin","Code":"123456","DeviceId":"d","DeviceName":"TV",""" +
            """"AppName":"Peke","AppVersion":"test","DateAdded":"2026-10-23T10:00:00Z"}"""

    @Test
    fun `the administrator's quick connect secret never reaches the log`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("POST", "/QuickConnect/Initiate", 200, quickConnectRequest(authenticated = false))
            http.on("POST", "/Users/AuthenticateWithQuickConnect", 200, signInAnswer(true, "admin-token", adminId))
            http.on("POST", "/Sessions/Logout", 204)
            var polls = 0
            http.onRequest = { recorded ->
                // Approved on a phone after two checks
                if (recorded.path == "/QuickConnect/Connect" && ++polls == 2) {
                    http.on("GET", "/QuickConnect/Connect", 200, quickConnectRequest(authenticated = true))
                }
            }
            http.on("GET", "/QuickConnect/Connect", 200, quickConnectRequest(authenticated = false))

            LogCapture().use { log ->
                val result = sessions().openWithQuickConnect(server) { }
                (result as AdminSignIn.Opened).session.close()

                // The SDK's own request lines are in the capture, so a secret in a URL would be too
                assertTrue("Logged: ${log.lines}", log.lines.any { it.contains("QuickConnect/Initiate") })
                assertTrue("Logged: ${log.lines}", log.none("s3cret-admin"))
                assertTrue("Logged: ${log.lines}", log.none("admin-token"))
            }
            assertEquals(2, http.requests.count { it.path == "/QuickConnect/Connect" })
            // The secret goes in the checks' query and the redeem's body only
            assertTrue(http.requests.filter { it.path == "/QuickConnect/Connect" }.all { it.query == "secret=s3cret-admin" })
        }
}
