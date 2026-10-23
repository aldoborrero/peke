package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidsPluginServiceTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "server", url = "http://jf.local", version = null)
    private val http = FakeKidsServer()
    private val serverDao = FakeKidsServerDao()
    private val profileDao = FakeKidProfileDao { if (it == KID_ROW) server.id else null }

    private fun TestScope.service(): KidsPluginService {
        val repository = mockk<ServerRepository>()
        every { repository.current } returns MutableStateFlow<CurrentUser?>(null)
        every { repository.currentServer } returns server
        return KidsPluginService(
            repository,
            http.api(UnconfinedTestDispatcher(testScheduler)),
            serverDao,
            profileDao,
            FakeClock(),
            backgroundScope,
        )
    }

    private fun info(
        version: Int = 1,
        features: String = KIDS_FEATURES_JSON,
    ) = http.on("GET", "/Kids/Info", 200, """{"PluginVersion":"0.0.2.0","ApiVersion":$version,"Features":[$features]}""")

    @Test
    fun `available with the required features in version 1`() =
        runTest(UnconfinedTestDispatcher()) {
            info()
            val service = service()
            service.check(server)
            assertEquals(KidsServerStatus.AVAILABLE, service.state(server.id).status)
        }

    @Test
    fun `absent without the plugin, an unknown version, or only profiles`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            service.check(server)
            assertEquals(KidsServerStatus.ABSENT, service.state(server.id).status)
            // Nothing is stored for a server that never had the plugin
            assertNull(serverDao.get(server.id))

            info(version = 2)
            service.check(server)
            assertEquals(KidsServerStatus.ABSENT, service.state(server.id).status)

            info(features = "\"profiles\"")
            service.check(server)
            assertEquals(KidsServerStatus.ABSENT, service.state(server.id).status)
        }

    @Test
    fun `a plugin without enforcement and the server sitting is outdated`() =
        runTest(UnconfinedTestDispatcher()) {
            // Plugin 0.0.4: the app no longer counts time itself, so it can't offer kids mode
            info(features = "\"profiles\",\"parentLock\",\"allowance\",\"sessionLimit\"")
            val service = service()
            service.check(server)
            val state = service.state(server.id)
            assertEquals(KidsServerStatus.ABSENT, state.status)
            assertTrue(state.outdated)
            assertFalse(state.has(KidsFeatures.ALLOWANCE))

            info()
            service.check(server)
            assertEquals(KidsServerStatus.AVAILABLE, service.state(server.id).status)
            assertFalse(service.state(server.id).outdated)
        }

    @Test
    fun `errors keep the last answer`() =
        runTest(UnconfinedTestDispatcher()) {
            info()
            val service = service()
            service.check(server)
            http.on("GET", "/Kids/Info", 503)
            service.check(server)
            assertEquals(KidsServerStatus.AVAILABLE, service.state(server.id).status)
            http.offline = true
            service.check(server)
            assertEquals(KidsServerStatus.AVAILABLE, service.state(server.id).status)
        }

    @Test
    fun `fails closed when the plugin goes away with cached kids`() =
        runTest(UnconfinedTestDispatcher()) {
            info()
            val service = service()
            service.check(server)
            profileDao.upsert(KidProfile(userId = KID_ROW, revision = 1))
            http.on("GET", "/Kids/Info", 404)
            service.check(server)
            assertEquals(KidsServerStatus.GONE, service.state(server.id).status)

            service.forget(server.id)
            advanceUntilIdle()
            assertEquals(KidsServerStatus.ABSENT, service.state(server.id).status)
            assertEquals(emptyList<Int>(), profileDao.allUserIds())
        }

    @Test
    fun `a server that had the plugin keeps its row when the plugin goes away`() =
        runTest(UnconfinedTestDispatcher()) {
            info()
            val service = service()
            service.check(server)
            http.on("GET", "/Kids/Info", 404)
            service.check(server)
            assertNull(serverDao.get(server.id)!!.apiVersion)
        }

    @Test
    fun `a check doesn't overwrite a lock cached while it was on its way`() =
        runTest(UnconfinedTestDispatcher()) {
            info()
            val service = service()
            service.check(server)
            // The parent gate caches the verifier while the next /Kids/Info is answered
            http.onRequest = {
                if (it.path == "/Kids/Info") {
                    runBlocking { service.cacheLock(server.id, "NUMERIC", "verifier-1", "2026-10-18T09:12:00Z") }
                }
            }
            info(features = "$KIDS_FEATURES_JSON,\"allowance\"")
            service.check(server)
            val cached = serverDao.get(server.id)!!
            assertEquals("verifier-1", cached.verifier)
            assertEquals("2026-10-18T09:12:00Z", cached.lockChangedAt)
            assertEquals("$KIDS_FEATURES,allowance", cached.features)
        }

    @Test
    fun `caching the lock keeps the plugin's answer and the offline throttle`() =
        runTest(UnconfinedTestDispatcher()) {
            info()
            val service = service()
            service.check(server)
            serverDao.updateOfflineThrottle(server.id, 3, null)
            service.cacheLock(server.id, "REMOTE", "verifier-2", "2026-10-18T10:00:00Z")
            val cached = serverDao.get(server.id)!!
            assertEquals(1, cached.apiVersion)
            assertEquals(3, cached.offlineFailures)
            assertEquals("REMOTE", cached.lockType)
        }

    companion object {
        private const val KID_ROW = 2
    }
}
