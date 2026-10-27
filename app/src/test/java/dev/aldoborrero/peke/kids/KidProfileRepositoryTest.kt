package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinServerUsers
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.data.SaveResult
import dev.aldoborrero.peke.kids.gate.KidsAuth
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.setup.KidSignOut
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidProfileRepositoryTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "server", url = "http://jf.local", version = null)
    private val adult = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "adult", serverId = server.id, accessToken = "a")
    private val kid = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "kid", serverId = server.id, accessToken = "k")
    private val http = FakeKidsServer()
    private val profileDao = FakeKidProfileDao { server.id }
    private val lock = mockk<ParentLockService>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)

    private fun profile(revision: Int) =
        """{"UserId":"${kid.id}","Revision":$revision,"SearchAllowed":false,"AutoplayEnabled":false,"AutoplayCap":3,""" +
            """"Sections":[{"Section":"Picked","Visible":true}],"PickedItemIds":[],"Extra":"kept"}"""

    private suspend fun TestScope.repository(
        available: Boolean = true,
        reachable: Boolean = true,
    ): KidProfileRepository {
        every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(CurrentUser(server, adult))
        val jellyfinServerDao = mockk<JellyfinServerDao>()
        every { jellyfinServerDao.getServer(server.id) } returns JellyfinServerUsers(server, listOf(adult, kid))
        every { jellyfinServerDao.getServers() } returns listOf(JellyfinServerUsers(server, listOf(adult, kid)))
        every { lock.auth() } returns KidsAuth(server, adult, "a", "g-1")
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val api = http.api(dispatcher)
        http.offline = !reachable
        val plugin = KidsPluginService(serverRepository, api, FakeKidsServerDao(), profileDao, FakeClock(), backgroundScope)
        if (available) http.on("GET", "/Kids/Info", 200, """{"ApiVersion":1,"Features":[$KIDS_FEATURES_JSON]}""")
        plugin.check(server)
        http.offline = false
        http.on("POST", "/Sessions/Logout", 204)
        return KidProfileRepository(
            api,
            profileDao,
            jellyfinServerDao,
            plugin,
            lock,
            serverRepository,
            KidSignOut(http.jellyfin, http.deviceInfo, dispatcher),
        )
    }

    @Test
    fun `refresh caches a kid, removes a non-kid, keeps the cache on errors`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 200, profile(3))
            repository.refresh(server, kid)
            assertEquals(3L, profileDao.get(kid.rowId)?.revision)
            assertTrue(
                http.requests
                    .last()
                    .headers["Authorization"]!!
                    .contains("Token=\"k\""),
            )

            http.on("GET", "/Kids/Users/${kid.id}/Profile", 500)
            repository.refresh(server, kid)
            assertEquals(3L, profileDao.get(kid.rowId)?.revision)

            http.offline = true
            repository.refresh(server, kid)
            assertEquals(3L, profileDao.get(kid.rowId)?.revision)

            coVerify(exactly = 0) { serverRepository.removeUser(any()) }

            // Only the plugin's own answer that the user is not a kid removes the profile, and the user with it: signed
            // out and forgotten on this TV
            http.offline = false
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 404, NOT_A_KID)
            repository.refresh(server, kid)
            assertNull(profileDao.get(kid.rowId))
            coVerify { serverRepository.removeUser(kid) }
            assertTrue(
                http.requests
                    .single { it.path == "/Sessions/Logout" }
                    .headers["Authorization"]!!
                    .contains("Token=\"k\""),
            )
        }

    @Test
    fun `refresh says whether the server answered, and remembers who isn't a kid`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 200, profile(3))
            assertTrue(repository.refresh(server, kid))

            http.on("GET", "/Kids/Users/${adult.id}/Profile", 500)
            assertFalse(repository.refresh(server, adult))
            http.offline = true
            assertFalse(repository.refresh(server, adult))
            assertEquals(emptySet<Int>(), repository.notKids.value)

            http.offline = false
            http.on("GET", "/Kids/Users/${adult.id}/Profile", 404, NOT_A_KID)
            assertTrue(repository.refresh(server, adult))
            assertEquals(setOf(adult.rowId), repository.notKids.value)
        }

    @Test
    fun `nobody is answered for while the plugin's state is unknown`() =
        runTest(UnconfinedTestDispatcher()) {
            // /Kids/Info couldn't be read: no answer
            val unknown = repository(available = false, reachable = false)
            assertFalse(unknown.refresh(server, adult))
            assertEquals(emptySet<Int>(), unknown.notKids.value)
        }

    @Test
    fun `a server that said it has no plugin answers that nobody is a kid`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository(available = false)
            assertTrue(repository.refresh(server, adult))
            assertEquals(setOf(adult.rowId), repository.notKids.value)
            assertTrue(http.requests.none { it.path.startsWith("/Kids/Users") })
        }

    @Test
    fun `a 404 from a plugin that went away keeps the kid`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 200, profile(3))
            repository.refresh(server, kid)

            // The plugin was removed: its routes answer a bare 404, and so does /Kids/Info
            http.on("GET", "/Kids/Info", 404)
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 404)
            repository.refresh(server, kid)
            assertEquals(3L, profileDao.get(kid.rowId)?.revision)
            // /Kids/Info was asked again
            assertEquals("/Kids/Info", http.requests.last().path)
        }

    @Test
    fun `a bare 404 keeps the kid even while the plugin answers`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            profileDao.upsert(KidProfile(userId = kid.rowId, revision = 3))
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 404)
            repository.refresh(server, kid)
            assertEquals(3L, profileDao.get(kid.rowId)?.revision)
        }

    @Test
    fun `not a kid while the plugin can't be reached keeps the kid`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            profileDao.upsert(KidProfile(userId = kid.rowId, revision = 3))
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 404, NOT_A_KID)
            http.on("GET", "/Kids/Info", 503)
            repository.refresh(server, kid)
            assertEquals(3L, profileDao.get(kid.rowId)?.revision)
        }

    @Test
    fun `a save answered with a 404 only removes the kid when the plugin says not a kid`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            profileDao.upsert(KidProfile(userId = kid.rowId, revision = 3))
            http.on("PUT", "/Kids/Users/${kid.id}/Profile", 404)
            assertEquals(SaveResult.FAILED, repository.save(kid, KidProfile(userId = kid.rowId, revision = 3, searchAllowed = true)))
            assertEquals(3L, profileDao.get(kid.rowId)?.revision)

            http.on("PUT", "/Kids/Users/${kid.id}/Profile", 404, NOT_A_KID)
            assertEquals(SaveResult.CONFLICT, repository.save(kid, KidProfile(userId = kid.rowId, revision = 3, searchAllowed = true)))
            assertNull(profileDao.get(kid.rowId))
        }

    @Test
    fun `nothing is read without the plugin`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository(available = false)
            profileDao.upsert(KidProfile(userId = kid.rowId, revision = 1))
            repository.refreshServer(server)
            assertEquals(1L, profileDao.get(kid.rowId)?.revision)
            assertTrue(http.requests.none { it.path.startsWith("/Kids/Users") || it.path == "/Kids/Profiles" })
        }

    @Test
    fun `server refresh follows the kids list`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            profileDao.upsert(KidProfile(userId = adult.rowId, revision = 1))
            http.on("GET", "/Kids/Profiles", 200, """[{"UserId":"${kid.id.toString().replace("-", "")}","Revision":4}]""")
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 200, profile(4))
            http.on("GET", "/Kids/Users/${adult.id}/Profile", 404, NOT_A_KID)
            repository.refreshServer(server)
            assertNull(profileDao.get(adult.rowId))
            // Left out of the list and the plugin said the adult isn't a kid, so they are signed out and forgotten
            assertEquals(setOf(adult.rowId), repository.notKids.value)
            coVerify { serverRepository.removeUser(adult) }
            assertTrue(
                http.requests
                    .single { it.path == "/Sessions/Logout" }
                    .headers["Authorization"]!!
                    .contains("Token=\"a\""),
            )
            coVerify(exactly = 0) { serverRepository.removeUser(kid) }
            assertEquals(4L, profileDao.get(kid.rowId)?.revision)

            // A failed list changes nothing
            http.on("GET", "/Kids/Profiles", 500)
            repository.refreshServer(server)
            assertEquals(4L, profileDao.get(kid.rowId)?.revision)
        }

    @Test
    fun `server refresh asks only with a kid's token`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            http.on("GET", "/Kids/Users/${adult.id}/Profile", 404, NOT_A_KID)
            http.on("GET", "/Kids/Profiles", 200, """[{"UserId":"${kid.id}","Revision":4}]""")
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 200, profile(4))

            // No kid on this TV yet: the adult's token isn't used
            repository.refreshServer(server, preferred = adult)
            assertTrue(http.requests.none { it.path == "/Kids/Profiles" })

            profileDao.upsert(KidProfile(userId = kid.rowId, revision = 1))
            repository.refreshServer(server, preferred = adult)
            val list = http.requests.single { it.path == "/Kids/Profiles" }
            assertTrue(list.headers["Authorization"]!!.contains("Token=\"k\""))
            // The adult, no kid, goes
            coVerify { serverRepository.removeUser(adult) }
        }

    @Test
    fun `an empty kids list removes nobody without the plugin's own answer`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            profileDao.upsert(KidProfile(userId = kid.rowId, revision = 4))
            http.on("GET", "/Kids/Profiles", 200, "[]")
            // The kid's own profile still answers, or fails
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 200, profile(4))
            repository.refreshServer(server)
            assertEquals(4L, profileDao.get(kid.rowId)?.revision)
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 500)
            repository.refreshServer(server)
            assertEquals(4L, profileDao.get(kid.rowId)?.revision)
            coVerify(exactly = 0) { serverRepository.removeUser(kid) }
            assertTrue(http.requests.none { it.path == "/Sessions/Logout" && it.headers["Authorization"]!!.contains("Token=\"k\"") })
        }

    @Test
    fun `a kid left out of the list goes only when the plugin says not a kid`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            profileDao.upsert(KidProfile(userId = kid.rowId, revision = 4))
            http.on("GET", "/Kids/Profiles", 200, "[]")
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 404, NOT_A_KID)
            repository.refreshServer(server)
            assertNull(profileDao.get(kid.rowId))
            coVerify { serverRepository.removeUser(kid) }
        }

    @Test
    fun `saving sends the cached revision and keeps unknown fields`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 200, profile(3))
            repository.refresh(server, kid)
            http.on("PUT", "/Kids/Users/${kid.id}/Profile", 200, profile(4))
            val cached = profileDao.get(kid.rowId)!!
            assertEquals(SaveResult.SAVED, repository.save(kid, cached.copy(searchAllowed = true)))
            val put = http.requests.last()
            assertTrue(put.body!!.contains("\"Revision\":3"))
            assertTrue(put.body.contains("\"Extra\":\"kept\""))
            assertTrue(put.body.contains("\"SearchAllowed\":true"))
            assertEquals("g-1", put.headers["X-Kids-Grant"])
            assertEquals(4L, profileDao.get(kid.rowId)?.revision)
        }

    @Test
    fun `saving sends the kid's live TV channels back`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            val channel = "7f3c0000-0000-0000-0000-000000000001"
            val withChannels = profile(3).replace("\"Extra\":\"kept\"", "\"LiveTvChannelIds\":[\"$channel\"]")
            http.on("GET", "/Kids/Users/${kid.id}/Profile", 200, withChannels)
            repository.refresh(server, kid)
            http.on("PUT", "/Kids/Users/${kid.id}/Profile", 200, withChannels.replace("\"Revision\":3", "\"Revision\":4"))
            val cached = profileDao.get(kid.rowId)!!
            assertEquals(SaveResult.SAVED, repository.save(kid, cached.copy(autoplayEnabled = true)))
            // The PUT is a full replacement: leaving the channels out would take live TV away
            assertTrue(
                http.requests
                    .last()
                    .body!!
                    .contains("\"LiveTvChannelIds\":[\"$channel\"]"),
            )
        }

    @Test
    fun `a conflict shows the server's values instead of overwriting them`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            http.on("PUT", "/Kids/Users/${kid.id}/Profile", 409, profile(9))
            assertEquals(SaveResult.CONFLICT, repository.save(kid, KidProfile(userId = kid.rowId, revision = 3, searchAllowed = true)))
            val cached = profileDao.get(kid.rowId)!!
            assertEquals(9L, cached.revision)
            assertEquals(false, cached.searchAllowed)
        }

    @Test
    fun `a refused grant asks for the code again and an admin-less TV can't save`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            http.on("PUT", "/Kids/Users/${kid.id}/Profile", 403)
            val profile = KidProfile(userId = kid.rowId, revision = 1)
            assertEquals(SaveResult.NEEDS_GRANT, repository.save(kid, profile))
            verify { lock.dropGrant() }

            every { lock.auth() } returns null
            assertEquals(SaveResult.NEEDS_GRANT, repository.save(kid, profile))

            every { lock.auth() } returns KidsAuth(server, adult, "a", "g-1")
            http.offline = true
            assertEquals(SaveResult.UNREACHABLE, repository.save(kid, profile))
        }

    companion object {
        // ASP.NET Core writes ProblemDetails in camelCase
        private const val NOT_A_KID = """{"title":"This user is not a kid.","status":404}"""
    }
}
