package dev.aldoborrero.peke.kids.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.server.KidsServerDao
import dev.aldoborrero.peke.kids.signInAnswer
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.services.KeyValueService
import dev.aldoborrero.peke.services.hilt.pekeKidDeviceId
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
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

    private fun TestScope.sessions() = AdminSessions(http.jellyfin, http.deviceInfo, UnconfinedTestDispatcher(testScheduler))

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

    /**
     * Everything on the device that could keep a sign-in. [AdminSessions] and [AdminSession] can't reach any of them,
     * which [cannot reach storage] checks, so these strict mocks record no call however a session is used.
     */
    private val serverRepository = mockk<ServerRepository>()
    private val serverDao = mockk<JellyfinServerDao>()
    private val tvSetupDao = mockk<TvSetupDao>()
    private val kidProfileDao = mockk<KidProfileDao>()
    private val kidsServerDao = mockk<KidsServerDao>()
    private val preferences = mockk<DataStore<AppPreferences>>()
    private val keyValueService = mockk<KeyValueService>()
    private val sharedPreferences = mockk<SharedPreferences>()
    private val appApiClient = mockk<ApiClient>()

    private val storage =
        listOf(
            ServerRepository::class.java,
            JellyfinServerDao::class.java,
            TvSetupDao::class.java,
            KidProfileDao::class.java,
            KidsServerDao::class.java,
            DataStore::class.java,
            KeyValueService::class.java,
            SharedPreferences::class.java,
            Context::class.java,
        )

    @Test
    fun `cannot reach storage`() {
        val types =
            listOf(AdminSessions::class.java, AdminSession::class.java).flatMap { type ->
                type.declaredConstructors.flatMap { it.parameterTypes.toList() } + type.declaredFields.map { it.type }
            }
        val reached = types.filter { t -> storage.any { s -> s.isAssignableFrom(t) || (t != Any::class.java && t.isAssignableFrom(s)) } }
        assertTrue("An administrator session reaches $reached", reached.isEmpty())
    }

    @Test
    fun `the token is never passed to storage or the app's own client`() =
        runTest(UnconfinedTestDispatcher()) {
            signsIn()
            val session = sessions().openAdmin()
            assertEquals(adminId, session.userId)
            assertFalse("The token must not show in logs", session.toString().contains("admin-token"))
            session.close()

            confirmVerified(
                serverRepository,
                serverDao,
                tvSetupDao,
                kidProfileDao,
                kidsServerDao,
                preferences,
                keyValueService,
                sharedPreferences,
                appApiClient,
            )
            // The password goes only into the sign-in itself
            assertEquals(1, http.requests.count { it.body?.contains("secret") == true })
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
        }
}
