package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.kids.gate.GrownUpSessions
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.setup.AdminSessions
import dev.aldoborrero.peke.kids.setup.AdminSignIn
import dev.aldoborrero.peke.kids.setup.TvLauncher
import dev.aldoborrero.peke.kids.ui.GrownUpGuardViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Home or standby leaves the TV to a kid (F1): every grant and administrator session ends, and a grown-up page asks
 * for the code again when it shows
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrownUpSessionsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val server = JellyfinServer(UUID.randomUUID(), "home", "http://jf.local", null)
    private val http = FakeKidsServer()
    private var live = true
    private val parentLockService =
        mockk<ParentLockService>(relaxed = true) {
            every { hasLiveGrant() } answers { live }
            every { forgetGrants() } answers { live = false }
        }
    private val adminSessions = AdminSessions(http.jellyfin, http.deviceInfo, http.api(dispatcher), dispatcher)
    private val sessions = GrownUpSessions(parentLockService, adminSessions, CoroutineScope(dispatcher))

    @Test
    fun `going to the background ends every grant and administrator session`() =
        runTest(dispatcher) {
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token"))
            http.on("POST", "/Sessions/Logout", 204)
            val setup = (adminSessions.open(server, "admin", "pw") as AdminSignIn.Opened).session
            val recovery = (adminSessions.open(server, "admin", "pw") as AdminSignIn.Opened).session

            sessions.end().join()

            verify { parentLockService.forgetGrants() }
            assertTrue(setup.isClosed && recovery.isClosed)
            assertEquals(2, http.requests.count { it.path == "/Sessions/Logout" })
            assertEquals(1, sessions.epoch.value)
        }

    @Test
    fun `a grown-up page left open asks for the code again when it shows`() =
        runTest(dispatcher) {
            val guard = GrownUpGuardViewModel(parentLockService, mockk(relaxed = true), mockk<TvLauncher>(relaxed = true))
            assertTrue(guard.allowed)

            sessions.end().join()
            // The page resumes when the app comes back
            guard.recheck()

            assertFalse(guard.allowed)
            guard.passed()
            assertTrue(guard.allowed)
        }
}
