package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsHomeSection
import dev.aldoborrero.peke.kids.data.KidsHomeSectionEntry
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsCaller
import dev.aldoborrero.peke.kids.server.KidsJson
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.SetLockRequest
import dev.aldoborrero.peke.services.hilt.pekeKidDeviceId
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
    fun `a profile without a revision is sent with revision 0 and defaults`() {
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
            val caller = KidsCaller("tok", userId)
            server.on(
                "GET",
                "/Kids/Info",
                200,
                """{"PluginVersion":"0.0.3.0","ApiVersion":1,"Features":["profiles","parentLock"],"New":1}""",
            )
            server.on("PUT", "/Kids/Users/$userId/Profile", 409, profileJson)
            server.on("PUT", "/Kids/Parent/Lock", 429, """{"RetryAfterSeconds":12}""", mapOf("Retry-After" to "12"))
            // ASP.NET Core writes ValidationProblemDetails in camelCase
            server.on("POST", "/Kids/Parent/Verify", 400, """{"status":400,"errors":{"Code":["bad"]}}""")
            server.on("GET", "/Kids/Users/$userId/Status", 500)

            val info = api.info("http://jf.local") as KidsResult.Ok
            assertEquals(1, info.value.apiVersion)
            assertEquals(listOf("profiles", "parentLock"), info.value.features)
            assertNull(server.requests.last().headers["Authorization"])

            val conflict = api.putProfile("http://jf.local", caller, "grant-1", userId, KidsJson.parseToJsonElement("{}").jsonObject)
            assertEquals(JsonPrimitive(3), (conflict as KidsResult.Conflict).current!!["Revision"])
            val put = server.requests.last()
            assertEquals("grant-1", put.headers[KidsApi.GRANT_HEADER])
            assertTrue(put.headers["Authorization"]!!.contains("Token=\"tok\""))

            assertEquals(KidsResult.Throttled(12), api.setLock("http://jf.local", caller, "g", SetLockRequest("NUMERIC", "1234", "0000")))
            assertEquals(KidsResult.Invalid(setOf("Code")), api.verify("http://jf.local", caller, "1"))
            assertEquals(KidsResult.Failed(500), api.status("http://jf.local", caller, userId))
            assertEquals(KidsResult.NotFound, api.profiles("http://jf.local", caller))

            // The plugin's own 404 is told apart from a route that doesn't exist
            server.on("GET", "/Kids/Users/$userId/Profile", 404, """{"title":"This user is not a kid.","status":404}""")
            assertEquals(KidsResult.NotAKid, api.profile("http://jf.local", caller, userId))
            server.on("GET", "/Kids/Users/$userId/Profile", 404, """{"title":"Not Found","status":404}""")
            assertEquals(KidsResult.NotFound, api.profile("http://jf.local", caller, userId))
            server.on("GET", "/Kids/Users/$userId/Profile", 404, "<html>Not found</html>")
            assertEquals(KidsResult.NotFound, api.profile("http://jf.local", caller, userId))

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

    @Test
    fun `calls with a kid's token send that kid's own device id`() =
        runTest {
            val server = FakeKidsServer()
            val api = server.api(StandardTestDispatcher(testScheduler))
            val sibling = UUID.fromString("5f1c0000-0000-0000-0000-000000000009")
            server.on("POST", "/Kids/Parent/Verify", 200, """{"Valid":true,"Grant":"g-1"}""")
            server.on("GET", "/Kids/Parent/Verifier", 200, """{"LockType":"NUMERIC","Verifier":"v"}""")
            server.on("GET", "/Kids/Users/$userId/Status", 200, "{}")

            // The plugin binds a grant to the device of the token it was issued to, so the grant request and its use
            // go out with the same kid's device id
            api.verify("http://jf.local", KidsCaller("tok", userId), "1234")
            api.verifier("http://jf.local", KidsCaller("tok", userId), "g-1")
            api.status("http://jf.local", KidsCaller("tok", userId), userId)
            api.status("http://jf.local", KidsCaller("sib", sibling), userId)

            val kidDevice = "DeviceId=\"${pekeKidDeviceId(FakeKidsServer.DEVICE_ID, userId)}\""
            val siblingDevice = "DeviceId=\"${pekeKidDeviceId(FakeKidsServer.DEVICE_ID, sibling)}\""
            val headers = server.requests.map { it.headers["Authorization"]!! }
            assertTrue(headers[0], headers[0].contains(kidDevice))
            assertTrue(headers[1], headers[1].contains(kidDevice))
            assertTrue(headers[2], headers[2].contains(kidDevice))
            assertTrue(headers[3], headers[3].contains(siblingDevice))
            assertFalse(headers.any { it.contains("DeviceId=\"${FakeKidsServer.DEVICE_ID}\"") })
        }

    @Test
    fun `an answer that can't be read isn't quoted in the log`() =
        runTest {
            val server = FakeKidsServer()
            val api = server.api(StandardTestDispatcher(testScheduler))
            // A verifier the parser chokes on: its error message would quote it
            server.on("GET", "/Kids/Parent/Verifier", 200, """{"LockType":"NUMERIC","Verifier":"v3rifier-secret",""")

            LogCapture().use { log ->
                assertEquals(KidsResult.Failed(200), api.verifier("http://jf.local", KidsCaller("tok", userId), "g-1"))
                assertTrue("Logged: ${log.lines}", log.lines.any { it.contains("Unreadable answer") })
                assertTrue("Logged: ${log.lines}", log.none("v3rifier-secret"))
            }
        }

    @Test
    fun `a quick connect request is asked about without the sdk`() =
        runTest {
            val server = FakeKidsServer()
            val api = server.api(StandardTestDispatcher(testScheduler))
            server.on("GET", "/QuickConnect/Connect", 200, """{"Authenticated":false,"Secret":"s3cret","Code":"123456"}""")

            LogCapture().use { log ->
                assertEquals(KidsResult.Ok(false), api.quickConnectApproved("http://jf.local", "s3cret"))
                server.on("GET", "/QuickConnect/Connect", 200, """{"Authenticated":true,"Secret":"s3cret","Code":"123456"}""")
                assertEquals(KidsResult.Ok(true), api.quickConnectApproved("http://jf.local", "s3cret"))
                server.on("GET", "/QuickConnect/Connect", 401)
                assertEquals(KidsResult.Forbidden, api.quickConnectApproved("http://jf.local", "s3cret"))
                assertTrue("Logged: ${log.lines}", log.none("s3cret"))
            }
            val request = server.requests.first()
            assertEquals("secret=s3cret", request.query)
            // Anonymous: no token goes with it
            assertNull(request.headers["Authorization"])
        }

    @Test
    fun `a bonus names its id, and undo and break end send the grant`() =
        runTest {
            val server = FakeKidsServer()
            val api = server.api(StandardTestDispatcher(testScheduler))
            val caller = KidsCaller("tok", userId)
            server.on(
                "POST",
                "/Kids/Users/$userId/WatchTime/Bonus",
                200,
                """{"RemainingSeconds":900,"BonusSeconds":900,"ServerTime":"2026-10-26T18:02:00Z","BonusId":42,"BonusUndoUntil":"2026-10-26T18:12:00Z"}""",
            )

            val bonus = api.bonus("http://jf.local", caller, "g-1", userId, 15) as KidsResult.Ok
            assertEquals(42L, bonus.value.bonusId)
            // 600 s, on the server's clock
            assertEquals(600L, bonus.value.undoSeconds)
            assertEquals(900L, bonus.value.status.remainingSeconds)
            assertEquals("""{"Minutes":15}""", server.requests.last().body)

            // An older plugin names no bonus
            server.on("POST", "/Kids/Users/$userId/WatchTime/Bonus", 200, """{"RemainingSeconds":900}""")
            assertNull((api.bonus("http://jf.local", caller, "g-1", userId, 15) as KidsResult.Ok).value.bonusId)

            server.on("DELETE", "/Kids/Users/$userId/WatchTime/Bonus/42", 200, """{"TimeUp":true}""")
            val undone = api.undoBonus("http://jf.local", caller, "g-1", userId, 42) as KidsResult.Ok
            assertTrue(undone.value.timeUp)
            assertEquals("g-1", server.requests.last().headers[KidsApi.GRANT_HEADER])
            server.on(
                "DELETE",
                "/Kids/Users/$userId/WatchTime/Bonus/42",
                409,
                """{"title":"This bonus can no longer be undone.","status":409}""",
            )
            assertTrue(api.undoBonus("http://jf.local", caller, "g-1", userId, 42) is KidsResult.Conflict)
            server.on("DELETE", "/Kids/Users/$userId/WatchTime/Bonus/42", 404, """{"title":"No such bonus.","status":404}""")
            assertEquals(KidsResult.NotFound, api.undoBonus("http://jf.local", caller, "g-1", userId, 42))

            server.on("POST", "/Kids/Users/$userId/WatchTime/Break/End", 200, """{"SittingUsedSeconds":0}""")
            assertTrue(api.endBreak("http://jf.local", caller, "g-2", userId) is KidsResult.Ok)
            assertEquals("g-2", server.requests.last().headers[KidsApi.GRANT_HEADER])
            server.on("POST", "/Kids/Users/$userId/WatchTime/Break/End", 403)
            assertEquals(KidsResult.Forbidden, api.endBreak("http://jf.local", caller, null, userId))
        }
}
