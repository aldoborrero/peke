package dev.aldoborrero.peke.kids

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.aldoborrero.peke.data.AppDatabase
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsDataCleaner
import dev.aldoborrero.peke.kids.data.KidsDatabase
import dev.aldoborrero.peke.kids.server.KidsServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class KidsDataCleanerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val kidsDb = Room.inMemoryDatabaseBuilder(context, KidsDatabase::class.java).allowMainThreadQueries().build()
    private val cleaner = KidsDataCleaner(appDb.serverDao(), kidsDb.kidProfileDao(), kidsDb.kidsServerDao())

    private val server = JellyfinServer(UUID.randomUUID(), "home", "http://localhost:8096", "10.11.0")
    private val other = JellyfinServer(UUID.randomUUID(), "other", "http://other:8096", "10.11.0")

    private fun addUser(
        server: JellyfinServer,
        name: String,
    ): JellyfinUser =
        appDb.serverDao().addOrUpdateUser(
            JellyfinUser(id = UUID.randomUUID(), name = name, serverId = server.id, accessToken = "token"),
        )

    @After
    fun tearDown() {
        appDb.close()
        kidsDb.close()
    }

    @Test
    fun removingAUserRemovesOnlyItsKidProfile() =
        runTest {
            appDb.serverDao().addOrUpdateServer(server)
            val kid = addUser(server, "kid")
            val sibling = addUser(server, "sibling")
            kidsDb.kidProfileDao().upsert(KidProfile(userId = kid.rowId, serverId = server.id))
            kidsDb.kidProfileDao().upsert(KidProfile(userId = sibling.rowId, serverId = server.id))

            cleaner.onUserRemoved(kid.rowId)

            assertNull(kidsDb.kidProfileDao().get(kid.rowId))
            assertNotNull(kidsDb.kidProfileDao().get(sibling.rowId))
        }

    @Test
    fun removingAServerRemovesItsKidsAndPluginState() =
        runTest {
            appDb.serverDao().addOrUpdateServer(server)
            appDb.serverDao().addOrUpdateServer(other)
            val kid = addUser(server, "kid")
            val elsewhere = addUser(other, "kid")
            kidsDb.kidProfileDao().upsert(KidProfile(userId = kid.rowId, serverId = server.id))
            kidsDb.kidProfileDao().upsert(KidProfile(userId = elsewhere.rowId, serverId = other.id))
            kidsDb.kidsServerDao().upsert(KidsServer(server.id))
            kidsDb.kidsServerDao().upsert(KidsServer(other.id))

            cleaner.onServerRemoved(server.id)

            assertNull(kidsDb.kidProfileDao().get(kid.rowId))
            assertNull(kidsDb.kidsServerDao().get(server.id))
            assertNotNull(kidsDb.kidProfileDao().get(elsewhere.rowId))
            assertNotNull(kidsDb.kidsServerDao().get(other.id))
        }

    @Test
    fun sweepRemovesRowsWithoutAUserOrServer() =
        runTest {
            appDb.serverDao().addOrUpdateServer(server)
            val kid = addUser(server, "kid")
            val goneServer = UUID.randomUUID()
            kidsDb.kidProfileDao().upsert(KidProfile(userId = kid.rowId, serverId = server.id))
            kidsDb.kidProfileDao().upsert(KidProfile(userId = kid.rowId + 100, serverId = goneServer))
            kidsDb.kidsServerDao().upsert(KidsServer(server.id))
            kidsDb.kidsServerDao().upsert(KidsServer(goneServer))

            cleaner.sweep()

            assertEquals(listOf(kid.rowId), kidsDb.kidProfileDao().allUserIds())
            assertEquals(listOf(server.id), kidsDb.kidsServerDao().allServerIds())
        }
}
