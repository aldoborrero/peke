package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.data.model.ServerUserConfig
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.gate.AdminCheck
import dev.aldoborrero.peke.kids.gate.LockChange
import dev.aldoborrero.peke.kids.gate.LockState
import dev.aldoborrero.peke.kids.gate.OfflineThrottle
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.PasscodeHasher
import dev.aldoborrero.peke.kids.gate.VerifyOutcome
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.setup.AdminSession
import dev.aldoborrero.peke.kids.setup.AdminSessions
import dev.aldoborrero.peke.kids.setup.AdminSignIn
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.services.hilt.pekeKidDeviceId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.UserPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ParentLockServiceTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "server", url = "http://jf.local", version = null)
    private val kid = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "kid", serverId = server.id, accessToken = "k")
    private val http = FakeKidsServer()
    private val clock = FakeClock()
    private val serverDao = FakeKidsServerDao()
    private val profileDao = FakeKidProfileDao { if (it == kid.rowId) server.id else null }
    private val current = MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
    private var admin = false
    private val verifier = PasscodeHasher.hash("1234")

    /**
     * This TV's users, as stored
     */
    private var users = listOf(kid)

    /**
     * @param pluginChecked whether the plugin answered in this run of the app before the gate opens
     */
    private suspend fun TestScope.service(pluginChecked: Boolean = true): ParentLockService {
        // The kid is one of this TV's kids: only their token is used
        profileDao.upsert(KidProfile(userId = kid.rowId))
        val repository = mockk<ServerRepository>()
        every { repository.current } returns current
        every { repository.currentServer } answers { current.value?.server }
        every { repository.currentUserDto } answers {
            ServerUserConfig(id = kid.id, policy = mockk<UserPolicy> { every { isAdministrator } returns admin })
        }
        val jellyfinServerDao = mockk<JellyfinServerDao>()
        every { jellyfinServerDao.getServer(server.id) } answers { JellyfinServerUsers(server, users) }
        every { jellyfinServerDao.getServers() } answers { listOf(JellyfinServerUsers(server, users)) }
        val tvSetupRepository = mockk<TvSetupRepository> { coEvery { server() } returns server }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = http.api(dispatcher)
        http.on("GET", "/Kids/Info", 200, """{"ApiVersion":1,"Features":[$KIDS_FEATURES_JSON]}""")
        val plugin = KidsPluginService(repository, api, serverDao, profileDao, clock, backgroundScope)
        if (pluginChecked) plugin.check(server)
        return ParentLockService(
            repository,
            jellyfinServerDao,
            profileDao,
            serverDao,
            tvSetupRepository,
            api,
            plugin,
            AdminSessions(http.jellyfin, http.deviceInfo, api, dispatcher),
            clock,
            dispatcher,
        )
    }

    private fun serverLock() {
        http.on("GET", "/Kids/Parent/Lock", 200, """{"LockType":"NUMERIC","ChangedAt":"2026-10-18T09:12:00Z"}""")
        http.on("GET", "/Kids/Parent/Verifier", 200, """{"LockType":"NUMERIC","Verifier":"$verifier"}""")
    }

    private fun verifyAnswer(body: String) = http.on("POST", "/Kids/Parent/Verify", 200, body)

    @Test
    fun `a right code gives a grant and caches the verifier`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            assertEquals(LockState.Code(ParentLockType.NUMERIC), service.lockState())
            assertNull(service.auth())

            assertEquals(VerifyOutcome.Valid, service.verify("1234"))
            assertEquals("g-1", service.auth()?.grant)
            assertEquals(verifier, serverDao.get(server.id)?.verifier)
            assertEquals("g-1", http.requests.last { it.path == "/Kids/Parent/Verifier" }.headers["X-Kids-Grant"])
        }

    @Test
    fun `wrong and throttled codes give no grant`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            val service = service()
            verifyAnswer("""{"Valid":false}""")
            assertEquals(VerifyOutcome.Wrong, service.verify("0000"))
            verifyAnswer("""{"Valid":false,"RetryAfterSeconds":30}""")
            assertEquals(VerifyOutcome.Throttled(30), service.verify("1234"))
            assertFalse(service.hasGrant())
        }

    @Test
    fun `grants expire before the server's five minutes`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            service.verify("1234")
            clock.now += 4 * 60 * 1000L
            assertTrue(service.hasGrant())
            clock.now += 50 * 1000L
            assertFalse(service.hasGrant())
        }

    @Test
    fun `offline the cached verifier opens the gate without a grant`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            service.verify("1234")
            service.dropGrant()
            http.offline = true
            assertEquals(VerifyOutcome.ValidOffline, service.verify("1234"))
            assertEquals(VerifyOutcome.Wrong, service.verify("9999"))
            assertFalse(service.hasGrant())
            assertEquals(LockState.Code(ParentLockType.NUMERIC), service.lockState())
        }

    @Test
    fun `offline wrong codes are limited like on the server, also after a restart`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            service.verify("1234")
            http.offline = true
            repeat(OfflineThrottle.THRESHOLD) { assertEquals(VerifyOutcome.Wrong, service.verify("0000")) }
            // Refused unchecked, even the right code
            assertEquals(VerifyOutcome.Throttled(30), service.verify("1234"))

            // Restarting the app doesn't end the wait: it is kept with the cached lock
            val restarted = service()
            clock.now += 10_000
            assertEquals(VerifyOutcome.Throttled(20), restarted.verify("1234"))
            clock.now += 20_000
            // Each wrong code after a wait doubles it
            assertEquals(VerifyOutcome.Wrong, restarted.verify("0000"))
            assertEquals(VerifyOutcome.Throttled(60), restarted.verify("1234"))
            clock.now += 60_000
            assertEquals(VerifyOutcome.ValidOffline, restarted.verify("1234"))
            // A right code clears it
            assertEquals(0, serverDao.get(server.id)!!.offlineFailures)
            assertEquals(VerifyOutcome.Wrong, restarted.verify("0000"))
            assertEquals(VerifyOutcome.ValidOffline, restarted.verify("1234"))
        }

    @Test
    fun `a right code checked by the server clears the offline limit`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            service.verify("1234")
            http.offline = true
            repeat(3) { service.verify("0000") }
            assertEquals(3, serverDao.get(server.id)!!.offlineFailures)
            http.offline = false
            assertEquals(VerifyOutcome.Valid, service.verify("1234"))
            assertEquals(0, serverDao.get(server.id)!!.offlineFailures)
        }

    @Test
    fun `the offline wait doubles up to 15 minutes and survives a TV restart`() {
        assertEquals(0L, OfflineThrottle.waitMillis(4))
        assertEquals(30_000L, OfflineThrottle.waitMillis(5))
        assertEquals(60_000L, OfflineThrottle.waitMillis(6))
        assertEquals(15 * 60_000L, OfflineThrottle.waitMillis(10))
        assertEquals(15 * 60_000L, OfflineThrottle.waitMillis(100))
        assertEquals(20_000L, OfflineThrottle.remainingMillis(5, blockedAt = 100_000, now = 110_000))
        assertEquals(0L, OfflineThrottle.remainingMillis(5, blockedAt = 100_000, now = 130_000))
        // After a TV restart the monotonic clock starts again at 0: only the time since then counts
        assertEquals(25_000L, OfflineThrottle.remainingMillis(5, blockedAt = 100_000, now = 5_000))
        assertEquals(0L, OfflineThrottle.remainingMillis(4, blockedAt = 100_000, now = 100_000))
    }

    @Test
    fun `after the server asks to wait no code is sent until the wait is over`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            val service = service()
            verifyAnswer("""{"Valid":false,"RetryAfterSeconds":30}""")
            assertEquals(VerifyOutcome.Throttled(30), service.verify("1234"))
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val sent = http.requests.count { it.path == "/Kids/Parent/Verify" }
            clock.now += 10_000
            assertEquals(VerifyOutcome.Throttled(20), service.verify("1234"))
            assertEquals(sent, http.requests.count { it.path == "/Kids/Parent/Verify" })
            clock.now += 20_000
            assertEquals(VerifyOutcome.Valid, service.verify("1234"))

            // The same for a 429 with Retry-After
            http.on("POST", "/Kids/Parent/Verify", 429, """{"RetryAfterSeconds":12}""", mapOf("Retry-After" to "12"))
            assertEquals(VerifyOutcome.Throttled(12), service.verify("0000"))
            verifyAnswer("""{"Valid":true,"Grant":"g-2"}""")
            assertEquals(VerifyOutcome.Throttled(12), service.verify("1234"))
            clock.now += 12_000
            assertEquals(VerifyOutcome.Valid, service.verify("1234"))
        }

    @Test
    fun `a changed code drops the cached verifier`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            service.verify("1234")
            assertEquals("2026-10-18T09:12:00Z", serverDao.get(server.id)?.lockChangedAt)
            // Unchanged: kept
            service.lockState()
            assertEquals(verifier, serverDao.get(server.id)?.verifier)

            // Changed on another TV, with the same type
            http.on("GET", "/Kids/Parent/Lock", 200, """{"LockType":"NUMERIC","ChangedAt":"2026-10-18T11:00:00Z"}""")
            assertEquals(LockState.Code(ParentLockType.NUMERIC), service.lockState())
            assertNull(serverDao.get(server.id)?.verifier)
            http.offline = true
            // The old code no longer opens the gate offline
            assertEquals(VerifyOutcome.CannotCheck, service.verify("1234"))
        }

    @Test
    fun `a changed lock type drops the cached verifier`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            service.verify("1234")
            http.on("GET", "/Kids/Parent/Lock", 200, """{"LockType":"REMOTE","ChangedAt":"2026-10-18T09:12:00Z"}""")
            assertEquals(LockState.Code(ParentLockType.REMOTE), service.lockState())
            assertNull(serverDao.get(server.id)?.verifier)
        }

    @Test
    fun `grants are timed from receipt, shortened only by a sane ExpiresAt`() {
        val now = Instant.parse("2026-10-18T09:00:00Z").toEpochMilli()
        val margin = 15_000L
        // No ExpiresAt: the plugin's 5 minutes
        assertEquals(5 * 60_000L - margin, ParentLockService.grantMillis(null, now))
        // A TV clock behind the server can't stretch it
        assertEquals(5 * 60_000L - margin, ParentLockService.grantMillis("2026-10-18T10:05:00Z", now))
        // A TV clock ahead of the server can't end it
        assertEquals(5 * 60_000L - margin, ParentLockService.grantMillis("2026-10-18T08:05:00Z", now))
        // A sane ExpiresAt sooner than 5 minutes shortens it
        assertEquals(2 * 60_000L - margin, ParentLockService.grantMillis("2026-10-18T09:02:00Z", now))
        assertEquals(5 * 60_000L - margin, ParentLockService.grantMillis("not a date", now))
    }

    @Test
    fun `offline without a cached verifier the code can't be checked`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            http.offline = true
            assertEquals(VerifyOutcome.CannotCheck, service.verify("1234"))
            assertEquals(LockState.Unknown, service.lockState())
        }

    @Test
    fun `with no current user the most recent kid's server is used`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            profileDao.upsert(KidProfile(userId = kid.rowId))
            val service = service()
            current.value = null
            assertEquals(VerifyOutcome.Valid, service.verify("1234"))
            assertTrue(
                http.requests
                    .last { it.path == "/Kids/Parent/Verify" }
                    .headers["Authorization"]!!
                    .contains("Token=\"k\""),
            )
        }

    @Test
    fun `changing the code needs the current one and checks the new one`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            http.on("PUT", "/Kids/Parent/Lock", 200, """{"LockType":"REMOTE"}""")
            val service = service()
            assertEquals(LockChange.NeedsGrant, service.setCode(ParentLockType.REMOTE, "UDLR", "1234"))
            service.verify("1234")
            assertEquals(LockChange.Done, service.setCode(ParentLockType.REMOTE, "UDLR", "1234"))
            val put = http.requests.first { it.method == "PUT" }
            assertTrue(put.body!!.contains("\"Current\":\"1234\""))
            // The change ends every grant, so the new code is checked right away for a fresh one
            assertTrue(http.requests.indexOfLast { it.path == "/Kids/Parent/Verify" } > http.requests.indexOf(put))
            assertNotNull(service.auth())

            http.on("PUT", "/Kids/Parent/Lock", 400, """{"status":400,"errors":{"Current":["wrong"]}}""")
            assertEquals(LockChange.WrongCurrent, service.setCode(ParentLockType.REMOTE, "UDLR", "0000"))
        }

    @Test
    fun `the code can be changed from Settings with no kid current`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            http.on("PUT", "/Kids/Parent/Lock", 200, """{"LockType":"NUMERIC"}""")
            // The picker: no kid is current, and the gate checks the code with this TV's kid
            current.value = null
            val service = service()
            service.verify("1234")
            // A change to one kid still needs that kid current
            assertNull(service.auth())

            assertEquals(LockChange.Done, service.setCode(ParentLockType.NUMERIC, "4321", "1234"))
            val put = http.requests.first { it.method == "PUT" }
            assertEquals("g-1", put.headers[KidsApi.GRANT_HEADER])
            assertTrue(put.headers["Authorization"]!!.contains("Token=\"k\""))
        }

    @Test
    fun `an administrator's token is no longer a grant`() =
        runTest(UnconfinedTestDispatcher()) {
            admin = true
            serverLock()
            http.on("PUT", "/Kids/Parent/Lock", 200, """{"LockType":"NUMERIC"}""")
            val service = service()
            assertNull(service.auth())
            assertEquals(LockChange.NeedsGrant, service.setCode(ParentLockType.NUMERIC, "4321", null))
            assertTrue(http.requests.none { it.method == "PUT" })
            assertFalse(service.hasLiveGrant())
        }

    @Test
    fun `with no kid token the gate uses the offline verifier`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            service.verify("1234")
            service.dropGrant()
            // No kid is current, and the TV's only kid lost their token
            current.value = null
            users = listOf(kid.copy(accessToken = null))
            val sent = http.requests.size

            assertEquals(LockState.Code(ParentLockType.NUMERIC), service.lockState())
            assertEquals(VerifyOutcome.Wrong, service.verify("0000"))
            assertEquals(VerifyOutcome.ValidOffline, service.verify("1234"))
            // Nothing went to the server without a kid's token
            assertEquals(sent, http.requests.size)
            assertTrue(service.hasLiveGrant())
        }

    @Test
    fun `a refused kid token falls back to the offline verifier`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            profileDao.upsert(KidProfile(userId = kid.rowId))
            val service = service()
            service.verify("1234")
            service.dropGrant()
            current.value = null
            // Signed out from the dashboard: the server refuses the kid's token
            http.on("GET", "/Kids/Parent/Lock", 401)
            http.on("POST", "/Kids/Parent/Verify", 401)

            assertEquals(LockState.Code(ParentLockType.NUMERIC), service.lockState())
            assertEquals(VerifyOutcome.ValidOffline, service.verify("1234"))
        }

    @Test
    fun `with no kid token and no verifier only recovery is offered`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            val service = service()
            current.value = null
            users = emptyList()

            assertEquals(LockState.RecoveryOnly, service.lockState())
            assertEquals(VerifyOutcome.CannotCheck, service.verify("1234"))
            assertTrue(http.requests.none { it.path.startsWith("/Kids/Parent") })
        }

    @Test
    fun `plugin gone with no cached verifier needs an administrator`() =
        runTest(UnconfinedTestDispatcher()) {
            profileDao.upsert(KidProfile(userId = kid.rowId))
            val service = service()
            // The plugin was uninstalled before this TV ever cached the code
            http.on("GET", "/Kids/Info", 404)
            http.on("GET", "/Kids/Parent/Lock", 404)
            assertEquals(LockState.PluginMissing, service.lockState())
            assertNull(serverDao.get(server.id)?.verifier)
        }

    @Test
    fun `plugin gone with a cached verifier still asks for the code`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            profileDao.upsert(KidProfile(userId = kid.rowId))
            val service = service()
            service.verify("1234")
            http.on("GET", "/Kids/Info", 404)
            http.on("GET", "/Kids/Parent/Lock", 404)
            assertEquals(LockState.Code(ParentLockType.NUMERIC), service.lockState())
            assertEquals(VerifyOutcome.ValidOffline, service.verify("1234"))
        }

    @Test
    fun `an unreachable server with the plugin is not reported as plugin missing`() =
        runTest(UnconfinedTestDispatcher()) {
            profileDao.upsert(KidProfile(userId = kid.rowId))
            val service = service()
            http.offline = true
            assertEquals(LockState.Unknown, service.lockState())
        }

    @Test
    fun `an administrator sign-in is checked once and signed out`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token"))
            http.on("POST", "/Sessions/Logout", 204)
            assertEquals(AdminCheck.ADMIN, service.checkAdmin("admin", "secret"))
            val signIn = http.requests.single { it.path == "/Users/AuthenticateByName" }
            assertTrue(signIn.body!!.contains("\"Pw\":\"secret\""))
            // A one-off device, so this TV's own sessions are left alone
            val deviceId = Regex("DeviceId=\"([^\"]+)\"").find(signIn.headers["Authorization"]!!)!!.groupValues[1]
            assertNotEquals("device-1", deviceId)
            val logout = http.requests.single { it.path == "/Sessions/Logout" }
            assertTrue(logout.headers["Authorization"]!!.contains("Token=\"admin-token\""))
            assertTrue(logout.headers["Authorization"]!!.contains("DeviceId=\"$deviceId\""))
            // Nothing about the administrator is kept
            assertNull(serverDao.get(server.id)?.verifier)
            assertFalse(service.hasGrant())
        }

    @Test
    fun `administrator sign-in refusals`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(false, "t"))
            http.on("POST", "/Sessions/Logout", 204)
            assertEquals(AdminCheck.NOT_ADMIN, service.checkAdmin("kid", "pw"))
            assertEquals(1, http.requests.count { it.path == "/Sessions/Logout" })
            http.on("POST", "/Users/AuthenticateByName", 401)
            assertEquals(AdminCheck.WRONG, service.checkAdmin("admin", "nope"))
            http.on("POST", "/Users/AuthenticateByName", 500)
            assertEquals(AdminCheck.FAILED, service.checkAdmin("admin", "pw"))
            http.offline = true
            assertEquals(AdminCheck.UNREACHABLE, service.checkAdmin("admin", "pw"))
        }

    @Test
    fun `ending the administrator session is retried once`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token"))
            http.on("POST", "/Sessions/Logout", 500)
            assertEquals(AdminCheck.ADMIN, service.checkAdmin("admin", "secret"))
            assertEquals(2, http.requests.count { it.path == "/Sessions/Logout" })
        }

    private fun TestScope.adminSessions(): AdminSessions {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return AdminSessions(http.jellyfin, http.deviceInfo, http.api(dispatcher), dispatcher)
    }

    private suspend fun TestScope.adminSession(): AdminSession {
        http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token"))
        http.on("POST", "/Sessions/Logout", 204)
        return (adminSessions().open(server, "admin", "pw") as AdminSignIn.Opened).session
    }

    @Test
    fun `recovery sets a new code with the administrator, without the current one`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            val service = service()
            service.verify("1234")
            assertNotNull(serverDao.get(server.id)?.verifier)
            http.on("PUT", "/Kids/Parent/Lock", 200, """{"LockType":"NUMERIC","ChangedAt":"2026-10-23T10:00:00Z"}""")
            val newVerifier = PasscodeHasher.hash("4321")
            http.on("GET", "/Kids/Parent/Verifier", 200, """{"LockType":"NUMERIC","Verifier":"$newVerifier"}""")
            val session = adminSession()

            assertEquals(LockChange.Done, service.recover(session, ParentLockType.NUMERIC, "4321"))
            val put = http.requests.single { it.method == "PUT" && it.path == "/Kids/Parent/Lock" }
            assertFalse(put.body!!.contains("Current"))
            assertTrue(put.body.contains("\"Code\":\"4321\""))
            assertTrue(put.headers["Authorization"]!!.contains("Token=\"admin-token\""))
            assertTrue(put.headers["Authorization"]!!.contains("DeviceId=\"${session.deviceId}\""))
            assertNull(put.headers["X-Kids-Grant"])
            // The old code no longer opens the gate offline, the new one does, and the grants the old one gave are gone
            assertEquals(newVerifier, serverDao.get(server.id)?.verifier)
            val verifierRead = http.requests.last { it.path == "/Kids/Parent/Verifier" }
            assertTrue(verifierRead.headers["Authorization"]!!.contains("Token=\"admin-token\""))
            assertFalse(service.hasGrant())
            // The administrator passed the gate: the parent menu, with Reset this TV, opens
            assertTrue(service.hasLiveGrant())
            // The administrator session ended right after
            assertTrue(session.isClosed)
            val logout = http.requests.single { it.path == "/Sessions/Logout" }
            assertTrue(logout.headers["Authorization"]!!.contains("Token=\"admin-token\""))
        }

    @Test
    fun `recovery refuses an account that isn't an administrator`() =
        runTest(UnconfinedTestDispatcher()) {
            service()
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(false, "kid-token"))
            http.on("POST", "/Sessions/Logout", 204)
            assertEquals(AdminSignIn.NotAdmin, adminSessions().open(server, "kid", "pw"))
            assertTrue(http.requests.none { it.method == "PUT" })
            // Its session was ended right away
            assertEquals(1, http.requests.count { it.path == "/Sessions/Logout" })
        }

    @Test
    fun `the recovery session closes when the change fails`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            http.on("PUT", "/Kids/Parent/Lock", 500)
            val session = adminSession()
            assertNotEquals(LockChange.Done, service.recover(session, ParentLockType.NUMERIC, "4321"))
            assertTrue(session.isClosed)
            assertEquals(1, http.requests.count { it.path == "/Sessions/Logout" })
        }

    @Test
    fun `recovery refuses a code that can't be used, and still closes the session`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            val session = adminSession()
            assertEquals(LockChange.Invalid, service.recover(session, ParentLockType.NUMERIC, "12"))
            assertTrue(http.requests.none { it.method == "PUT" })
            assertTrue(session.isClosed)
        }

    @Test
    fun `with no current kid and no plugin status known, the gate asks the plugin`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            // The kids data was lost: nothing cached about the server, and the picker has no kid current, so nothing
            // asked the plugin yet
            current.value = null
            val service = service(pluginChecked = false)
            assertTrue(http.requests.none { it.path == "/Kids/Info" })

            assertEquals(LockState.Code(ParentLockType.NUMERIC), service.lockState())
            assertTrue(http.requests.any { it.path == "/Kids/Info" })
            val lockRead = http.requests.last { it.path == "/Kids/Parent/Lock" }
            // Asked as one of this TV's kids, with that kid's own device id
            assertTrue(lockRead.headers["Authorization"]!!.contains("Token=\"k\""))
            assertTrue(lockRead.headers["Authorization"]!!.contains(pekeKidDeviceId(FakeKidsServer.DEVICE_ID, kid.id)))
            assertEquals(VerifyOutcome.Valid, service.verify("1234"))
        }

    @Test
    fun `a user who isn't one of this TV's kids is never asked with`() =
        runTest(UnconfinedTestDispatcher()) {
            serverLock()
            verifyAnswer("""{"Valid":true,"Grant":"g-1"}""")
            // An administrator's row an older version kept, with a token
            val grownUp = JellyfinUser(rowId = 9, id = UUID.randomUUID(), name = "admin", serverId = server.id, accessToken = "admin-token")
            users = listOf(grownUp, kid.copy(accessToken = null))
            val service = service()
            current.value = null

            assertEquals(LockState.RecoveryOnly, service.lockState())
            assertEquals(VerifyOutcome.CannotCheck, service.verify("1234"))
            assertTrue(http.requests.none { it.headers["Authorization"]?.contains("admin-token") == true })
        }

    @Test
    fun `after a recovery with no kid token the gate still checks the new code`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            current.value = null
            users = listOf(kid.copy(accessToken = null))
            assertEquals(LockState.RecoveryOnly, service.lockState())
            http.on("PUT", "/Kids/Parent/Lock", 200, """{"LockType":"NUMERIC","ChangedAt":"2026-10-23T10:00:00Z"}""")
            http.on("GET", "/Kids/Parent/Verifier", 200, """{"LockType":"NUMERIC","Verifier":"${PasscodeHasher.hash("4321")}"}""")

            assertEquals(LockChange.Done, service.recover(adminSession(), ParentLockType.NUMERIC, "4321"))
            assertTrue(service.hasLiveGrant())
            service.forgetGrants()

            assertEquals(LockState.Code(ParentLockType.NUMERIC), service.lockState())
            assertEquals(VerifyOutcome.Wrong, service.verify("1234"))
            assertEquals(VerifyOutcome.ValidOffline, service.verify("4321"))
        }
}
