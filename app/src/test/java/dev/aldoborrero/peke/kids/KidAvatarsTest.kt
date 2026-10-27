package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsJson
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.setup.KidPicture
import dev.aldoborrero.peke.kids.setup.kidPicture
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * Kid avatars from the Jellyfin Kids plugin (Kids API 1.1, feature `avatar`; contracts/kids-api-avatar.md in spec 007)
 */
class KidAvatarsTest {
    private val server = UUID.randomUUID()
    private val other = UUID.randomUUID()

    private fun cached(
        rowId: Int,
        avatar: String?,
        serverId: UUID = server,
    ): KidProfile {
        val avatarField = avatar?.let { ""","Avatar":"$it"""" }.orEmpty()
        val json = """{"UserId":"${UUID.randomUUID()}","Revision":2$avatarField}"""
        return KidProfileMapper.fromServer(rowId, serverId, KidsJson.parseToJsonElement(json).jsonObject)
    }

    private fun plugin(vararg extra: String) =
        KidsServer(serverId = server, apiVersion = 1, features = (KidsFeatures.REQUIRED + extra).joinToString(","))

    @Test
    fun `the avatar is read from the profile the server sent`() {
        assertEquals("lucy", KidProfileMapper.avatarId(cached(1, "lucy")))
        // Omitted when the kid has none
        assertNull(KidProfileMapper.avatarId(cached(1, null)))
        assertNull(KidProfileMapper.avatarId(KidProfile(userId = 1)))
    }

    @Test
    fun `this TV's kids get their avatars when the plugin offers them`() =
        runTest {
            val profiles = FakeKidProfileDao()
            val servers = FakeKidsServerDao()
            listOf(cached(1, "lucy"), cached(2, null), cached(3, "bob"), cached(4, "Max"), cached(5, "leo", other))
                .forEach { profiles.upsert(it) }
            val avatars = KidAvatars(profiles, servers)

            // No plugin row yet, or a plugin without the feature: nobody has an avatar
            assertEquals(emptyMap<Int, PekeAvatar>(), avatars.observe(server).first())
            servers.upsert(plugin())
            assertEquals(emptyMap<Int, PekeAvatar>(), avatars.observe(server).first())

            servers.upsert(plugin(KidsFeatures.AVATAR))
            // Only the six ids the plugin sends, lowercase; another server's kid isn't this server's
            assertEquals(mapOf(1 to PekeAvatar.LUCY), avatars.observe(server).first())
        }

    @Test
    fun `a kid shows their avatar, else their picture, else their initial`() {
        assertEquals(KidPicture.Avatar(PekeAvatar.MIA), kidPicture(PekeAvatar.MIA, "http://jf/Users/1/Images/Primary", false))
        assertEquals(KidPicture.Image("http://jf/Users/1/Images/Primary"), kidPicture(null, "http://jf/Users/1/Images/Primary", false))
        // The picture couldn't load, or there is none
        assertEquals(KidPicture.Initial, kidPicture(null, "http://jf/Users/1/Images/Primary", true))
        assertEquals(KidPicture.Initial, kidPicture(null, null, false))
        assertEquals(KidPicture.Initial, kidPicture(null, " ", false))
    }
}
