package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.livetv.KidsChannel
import dev.aldoborrero.peke.kids.livetv.KidsLiveTv
import dev.aldoborrero.peke.kids.livetv.KidsLiveTvService
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsInfo
import dev.aldoborrero.peke.kids.server.KidsItemsResult
import dev.aldoborrero.peke.kids.server.KidsJson
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.ui.kidsInterfaceCustomization
import dev.aldoborrero.peke.kids.ui.kidsNavDrawerItems
import dev.aldoborrero.peke.ui.nav.NavDrawerItem
import dev.aldoborrero.peke.ui.util.RestrictedFeature
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Kids live TV (plugin 0.0.6.0): the kid's channels, when live TV shows, the profile field and the kids UI
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidsLiveTvTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "server", url = "http://jf.local", version = null)
    private val kid = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "kid", serverId = server.id, accessToken = "k")
    private val peke = UUID.fromString("7f3c0000-0000-0000-0000-000000000001")
    private val http = FakeKidsServer()
    private val channelsPath = "/Kids/Users/${kid.id}/LiveTv/Channels"

    // As in the contract: a channel with its program on now, plus items kids mode must leave out
    private val channels =
        """
        {"Items":[{"Id":"7f3c0000000000000000000000000001","Name":"PekeTV","ChannelNumber":"1","Type":"TvChannel",
                   "ImageTags":{"Primary":"c0ff"},
                   "CurrentProgram":{"Id":"b1000000000000000000000000000001","Name":"Bluey",
                                     "StartDate":"2026-10-20T17:00:00.0000000Z","EndDate":"2026-10-20T17:30:00.0000000Z",
                                     "ChannelId":"7f3c0000000000000000000000000001"}},
                  {"Id":"b1000000000000000000000000000001","Name":"Bluey","Type":"Program"},
                  {"Name":"No id","Type":"TvChannel"}],
         "TotalRecordCount":1,"StartIndex":0}
        """.trimIndent()

    private val profile = KidProfile(userId = kid.rowId, revision = 3)

    private fun TestScope.service(
        liveTv: Boolean = true,
        state: MutableStateFlow<KidsModeState> = MutableStateFlow(KidsModeState.Active(profile, kid)),
    ): KidsLiveTvService {
        val serverRepository = mockk<ServerRepository>()
        every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(CurrentUser(server, kid))
        every { serverRepository.currentServer } returns server
        val features = KIDS_FEATURES_JSON + if (liveTv) ",\"${KidsFeatures.LIVE_TV}\"" else ""
        http.on("GET", "/Kids/Info", 200, """{"ApiVersion":1,"Features":[$features],"LiveTv":{"Gate":"ok","Missing":[]}}""")
        val api = http.api(UnconfinedTestDispatcher(testScheduler))
        val plugin =
            KidsPluginService(serverRepository, api, FakeKidsServerDao(), FakeKidProfileDao { server.id }, FakeClock(), backgroundScope)
        val kidsModeService = mockk<KidsModeService>()
        every { kidsModeService.state } returns state
        return KidsLiveTvService(api, serverRepository, kidsModeService, plugin, backgroundScope)
    }

    private fun channelRequests() = http.requests.count { it.path == channelsPath }

    @Test
    fun `channels are mapped from the plugin's answer`() {
        val result = KidsJson.decodeFromString(KidsItemsResult.serializer(), channels)
        assertEquals(listOf(KidsChannel(peke, "PekeTV", "1")), KidsLiveTv.channels(result))
    }

    @Test
    fun `the info reports the live TV gate`() {
        val info = KidsJson.decodeFromString(KidsInfo.serializer(), """{"ApiVersion":1,"LiveTv":{"Gate":"degraded","Missing":["A.B"]}}""")
        assertEquals("degraded", info.liveTv?.gate)
        assertEquals(listOf("A.B"), info.liveTv?.missing)
        assertNull(KidsJson.decodeFromString(KidsInfo.serializer(), """{"ApiVersion":1}""").liveTv)
    }

    @Test
    fun `answers that say no live TV and answers that say nothing`() {
        val ok = KidsResult.Ok(KidsJson.decodeFromString(KidsItemsResult.serializer(), channels))
        assertEquals(1, KidsLiveTv.channelsAfter(ok)?.size)
        assertEquals(emptyList<KidsChannel>(), KidsLiveTv.channelsAfter(KidsResult.NotAKid))
        assertEquals(emptyList<KidsChannel>(), KidsLiveTv.channelsAfter(KidsResult.NotFound))
        assertEquals(emptyList<KidsChannel>(), KidsLiveTv.channelsAfter(KidsResult.Forbidden))
        assertNull(KidsLiveTv.channelsAfter(KidsResult.Unreachable))
        assertNull(KidsLiveTv.channelsAfter(KidsResult.Failed(500)))
    }

    @Test
    fun `live TV shows only with the feature and at least one channel`() {
        val one = listOf(KidsChannel(peke, "PekeTV", "1"))
        assertTrue(KidsLiveTv.shown(pluginHasLiveTv = true, channels = one))
        assertFalse(KidsLiveTv.shown(pluginHasLiveTv = true, channels = emptyList()))
        assertFalse(KidsLiveTv.shown(pluginHasLiveTv = false, channels = one))
    }

    @Test
    fun `the current kid's channels are read once per revision with their own token`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("GET", channelsPath, 200, channels)
            val service = service()
            assertTrue(service.available.value)
            assertEquals(listOf(peke), service.channelsFor(kid, profile).map { it.id })
            assertEquals(1, channelRequests())
            assertTrue(
                http.requests
                    .last { it.path == channelsPath }
                    .headers["Authorization"]!!
                    .contains("Token=\"k\""),
            )
        }

    @Test
    fun `a kid without channels has no live TV`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("GET", channelsPath, 200, """{"Items":[],"TotalRecordCount":0}""")
            val service = service()
            assertFalse(service.available.value)
            assertTrue(service.channelsFor(kid, profile).isEmpty())
        }

    @Test
    fun `an older plugin means no live TV and no request`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("GET", channelsPath, 200, channels)
            val service = service(liveTv = false)
            assertFalse(service.available.value)
            assertTrue(service.channelsFor(kid, profile).isEmpty())
            assertEquals(0, channelRequests())
        }

    @Test
    fun `errors keep the channels that were known`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("GET", channelsPath, 200, channels)
            val state = MutableStateFlow<KidsModeState>(KidsModeState.Active(profile, kid))
            val service = service(state = state)
            assertTrue(service.available.value)

            // A new revision whose channels can't be read
            http.on("GET", channelsPath, 500)
            val next = profile.copy(revision = 4)
            state.value = KidsModeState.Active(next, kid)
            assertTrue(service.available.value)
            assertEquals(listOf(peke), service.channelsFor(kid, next).map { it.id })

            // A definite answer that the kid may not have live TV
            http.on("GET", channelsPath, 403)
            val third = profile.copy(revision = 5)
            state.value = KidsModeState.Active(third, kid)
            assertFalse(service.available.value)

            // No longer a kid
            state.value = KidsModeState.Inactive
            assertFalse(service.available.value)
        }

    @Test
    fun `the profile's channels are read and sent back on every save`() {
        val json =
            """{"UserId":"${kid.id}","Revision":5,"SearchAllowed":false,"LiveTvChannelIds":["$peke","not-an-id"]}"""
        val cached = KidProfileMapper.fromServer(kid.rowId, KidsJson.parseToJsonElement(json).jsonObject)
        assertEquals(listOf(peke), KidProfileMapper.liveTvChannelIds(cached))
        val body = KidProfileMapper.toUpdate(cached.copy(searchAllowed = true), cached.revision, sendSessionLimit = true)
        assertEquals("""["$peke","not-an-id"]""", body[KidProfileMapper.LIVE_TV_CHANNEL_IDS].toString())

        // A plugin without live TV never sent the field, so none is sent
        val older = KidProfileMapper.fromServer(kid.rowId, KidsJson.parseToJsonElement("""{"Revision":1}""").jsonObject)
        assertTrue(KidProfileMapper.liveTvChannelIds(older).isEmpty())
        assertNull(KidProfileMapper.toUpdate(older, 1, sendSessionLimit = true)[KidProfileMapper.LIVE_TV_CHANNEL_IDS])
    }

    @Test
    fun `the Live TV entry is only in the drawer when the kid has channels`() {
        assertEquals(listOf(NavDrawerItem.Favorites), kidsNavDrawerItems(liveTv = false))
        assertEquals(listOf(NavDrawerItem.Favorites, NavDrawerItem.LiveTv), kidsNavDrawerItems(liveTv = true))
        val customization = kidsInterfaceCustomization(profile, liveTv = true)
        assertEquals(kidsNavDrawerItems(liveTv = true), customization.navDrawerItems)
        assertFalse(customization.allows(RestrictedFeature.LIVE_TV_RECORDINGS))
        assertEquals(listOf(NavDrawerItem.Favorites), kidsInterfaceCustomization(profile).navDrawerItems)
    }
}
