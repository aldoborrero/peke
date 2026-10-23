package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsHomeSection
import dev.aldoborrero.peke.kids.data.KidsHomeSectionEntry
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsJson
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.SetLockRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidsApiTest {
    private val userId = UUID.fromString("5f1c0000-0000-0000-0000-000000000001")
    private val pick = UUID.fromString("a1b20000-0000-0000-0000-000000000002")

    private val profileJson =
        """
        {"UserId":"$userId","Revision":3,"SearchAllowed":true,"AutoplayEnabled":true,"AutoplayCap":2,
         "Sections":[{"Section":"New","Visible":false},{"Section":"Picked","Visible":true},{"Section":"Shorts","Visible":true}],
         "PickedItemIds":["$pick"],"DailyAllowanceMinutes":45,"FutureField":{"x":1}}
        """.trimIndent()

    @Test
    fun `maps a server profile to the cache`() {
        val profile = KidProfileMapper.fromServer(7, null, KidsJson.parseToJsonElement(profileJson).jsonObject)
        assertEquals(7, profile.userId)
        assertEquals(3L, profile.revision)
        assertTrue(profile.searchAllowed)
        assertTrue(profile.autoplayEnabled)
        assertEquals(2, profile.autoplayCap)
        assertEquals(listOf(pick), profile.pickedItemIds)
        assertEquals(45, profile.dailyAllowanceMinutes)
        assertNull(profile.sessionLimitMinutes)
        // Unknown section names are dropped and missing ones appended, as in v1
        assertEquals(KidsHomeSectionEntry(KidsHomeSection.NEW, false), profile.sections[0])
        assertEquals(KidsHomeSection.PICKED, profile.sections[1].section)
        assertEquals(KidsHomeSection.entries.size, profile.sections.size)
        assertFalse(KidProfileMapper.hasSessionLimitField(profile))
    }

    @Test
    fun `an update keeps unknown fields and sends every known one`() {
        val cached = KidProfileMapper.fromServer(7, null, KidsJson.parseToJsonElement(profileJson).jsonObject)
        val body = KidProfileMapper.toUpdate(cached.copy(searchAllowed = false, dailyAllowanceMinutes = null), 3, sendSessionLimit = false)
        assertEquals(JsonPrimitive(3L), body["Revision"])
        assertEquals(JsonPrimitive(false), body["SearchAllowed"])
        assertEquals(JsonNull, body["DailyAllowanceMinutes"])
        assertEquals("""{"x":1}""", body["FutureField"].toString())
        assertNull(body["UserId"])
        assertNull(body["SessionLimitMinutes"])
        assertEquals(
            "\"New\"",
            body["Sections"]!!
                .jsonArray[0]
                .jsonObject["Section"]
                .toString(),
        )
    }

    @Test
    fun `a new kid is sent with revision 0 and defaults`() {
        val body = KidProfileMapper.toUpdate(KidProfile(userId = 1, sessionLimitMinutes = 30), 0, sendSessionLimit = true)
        assertEquals(JsonPrimitive(0L), body["Revision"])
        assertEquals(JsonPrimitive(KidProfile.DEFAULT_AUTOPLAY_CAP), body["AutoplayCap"])
        assertEquals(JsonPrimitive(30), body["SessionLimitMinutes"])
        assertEquals(KidsHomeSection.entries.size, body["Sections"]!!.jsonArray.size)
    }

    @Test
    fun `answers map to results`() =
        runTest {
            val server = FakeKidsServer()
            val api = server.api(StandardTestDispatcher(testScheduler))
            server.on(
                "GET",
                "/Kids/Info",
                200,
                """{"PluginVersion":"0.0.3.0","ApiVersion":1,"Features":["profiles","parentLock"],"New":1}""",
            )
            server.on("PUT", "/Kids/Users/$userId/Profile", 409, profileJson)
            server.on("DELETE", "/Kids/Users/$userId/Profile", 403)
            server.on("PUT", "/Kids/Parent/Lock", 429, """{"RetryAfterSeconds":12}""", mapOf("Retry-After" to "12"))
            // ASP.NET Core writes ValidationProblemDetails in camelCase
            server.on("POST", "/Kids/Parent/Verify", 400, """{"status":400,"errors":{"Code":["bad"]}}""")
            server.on("GET", "/Kids/Users/$userId/Status", 500)

            val info = api.info("http://jf.local") as KidsResult.Ok
            assertEquals(1, info.value.apiVersion)
            assertEquals(listOf("profiles", "parentLock"), info.value.features)
            assertNull(server.requests.last().headers["Authorization"])

            val conflict = api.putProfile("http://jf.local", "tok", "grant-1", userId, KidsJson.parseToJsonElement("{}").jsonObject)
            assertEquals(JsonPrimitive(3), (conflict as KidsResult.Conflict).current!!["Revision"])
            val put = server.requests.last()
            assertEquals("grant-1", put.headers[KidsApi.GRANT_HEADER])
            assertTrue(put.headers["Authorization"]!!.contains("Token=\"tok\""))

            assertEquals(KidsResult.Forbidden, api.deleteProfile("http://jf.local", "tok", null, userId))
            assertEquals(KidsResult.Throttled(12), api.setLock("http://jf.local", "tok", "g", SetLockRequest("NUMERIC", "1234", "0000")))
            assertEquals(KidsResult.Invalid(setOf("Code")), api.verify("http://jf.local", "tok", "1"))
            assertEquals(KidsResult.Failed(500), api.status("http://jf.local", "tok", userId))
            assertEquals(KidsResult.NotFound, api.profiles("http://jf.local", "tok"))

            // The plugin's own 404 is told apart from a route that doesn't exist
            server.on("GET", "/Kids/Users/$userId/Profile", 404, """{"title":"This user is not a kid.","status":404}""")
            assertEquals(KidsResult.NotAKid, api.profile("http://jf.local", "tok", userId))
            server.on("GET", "/Kids/Users/$userId/Profile", 404, """{"title":"Not Found","status":404}""")
            assertEquals(KidsResult.NotFound, api.profile("http://jf.local", "tok", userId))
            server.on("GET", "/Kids/Users/$userId/Profile", 404, "<html>Not found</html>")
            assertEquals(KidsResult.NotFound, api.profile("http://jf.local", "tok", userId))

            server.offline = true
            assertEquals(KidsResult.Unreachable, api.info("http://jf.local"))
        }

    @Test
    fun `base urls with a path keep it`() =
        runTest {
            val server = FakeKidsServer()
            val api = server.api(StandardTestDispatcher(testScheduler))
            server.on("GET", "/jellyfin/Kids/Info", 200, """{"ApiVersion":1}""")
            assertTrue(api.info("http://jf.local/jellyfin") is KidsResult.Ok)
        }
}
