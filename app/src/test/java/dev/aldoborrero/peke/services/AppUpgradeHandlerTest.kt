package dev.aldoborrero.peke.services

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.aldoborrero.peke.data.AppDatabase
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsDatabase
import dev.aldoborrero.peke.kids.setup.KidSignOut
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The upgrade to spec 004 keeps only this TV's kids (SC-002): a TV set up by an older version kept the administrator
 * signed in
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class AppUpgradeHandlerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val kidsDb = Room.inMemoryDatabaseBuilder(context, KidsDatabase::class.java).allowMainThreadQueries().build()
    private val server = JellyfinServer(UUID.randomUUID(), "home", "http://jf.local", "12.2.0")
    private val http = FakeKidsServer()
    private val keyValueService = mockk<KeyValueService>(relaxed = true)
    private val serverRepository =
        mockk<ServerRepository> {
            // As the real one does, without the current user, who is nobody at an upgrade
            coEvery { removeUser(any()) } answers {
                val user = firstArg<JellyfinUser>()
                appDb.serverDao().deleteUser(user.serverId, user.id)
            }
        }

    @After
    fun tearDown() {
        appDb.close()
        kidsDb.close()
    }

    private fun user(
        name: String,
        pin: String? = null,
    ) = appDb.serverDao().addOrUpdateUser(
        JellyfinUser(
            id = UUID.randomUUID(),
            name = name,
            serverId = server.id,
            accessToken = "token-$name",
            pin = pin,
            requireLogin =
                pin != null,
        ),
    )

    @Test
    fun `users who are no kids are signed out and deleted, and the old lock key and PINs go`() =
        runTest(UnconfinedTestDispatcher()) {
            http.on("POST", "/Sessions/Logout", 204)
            appDb.serverDao().addOrUpdateServer(server)
            val admin = user("admin")
            val kid = user("kid", pin = "1234")
            kidsDb.kidProfileDao().upsert(KidProfile(userId = kid.rowId, serverId = server.id))
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val handler =
                AppUpgradeHandler(
                    context,
                    appDb.serverDao(),
                    kidsDb.kidProfileDao(),
                    serverRepository,
                    KidSignOut(http.jellyfin, http.deviceInfo, dispatcher),
                    keyValueService,
                    backgroundScope,
                )

            handler.keepOnlyKids()
            advanceUntilIdle()

            val users = appDb.serverDao().getServer(server.id)!!.users
            assertEquals(listOf(kid.rowId), users.map { it.rowId })
            assertNull(appDb.serverDao().getUser(server.id, admin.id))
            // No token of the administrator is left, and their session ended on the server
            assertTrue(users.none { it.accessToken == "token-admin" })
            val logout = http.requests.single { it.path == "/Sessions/Logout" }
            assertTrue(logout.headers["Authorization"]!!.contains("Token=\"token-admin\""))
            // The kid keeps their session, without the PIN kept in plaintext
            val stored = users.single()
            assertEquals("token-kid", stored.accessToken)
            assertNull(stored.pin)
            assertFalse(stored.requireLogin)
            coVerify { keyValueService.remove(listOf("kids_lock")) }
        }
}
