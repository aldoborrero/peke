package dev.aldoborrero.peke.kids.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.aldoborrero.peke.data.AppDatabase
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsDataCleaner
import dev.aldoborrero.peke.kids.data.KidsDatabase
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsCaller
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.userJson
import dev.aldoborrero.peke.services.AppVisibility
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * A kid's session the server refuses (spec FR-019), end to end: the 401 goes through the app's own client, the kid's
 * row loses its token, and the launch rule and the picker show them as needing a grown-up
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class RefusedSessionsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val kidsDb = Room.inMemoryDatabaseBuilder(context, KidsDatabase::class.java).allowMainThreadQueries().build()
    private val server = JellyfinServer(UUID.randomUUID(), "home", "http://jf.local", "12.2.0")
    private val http = FakeKidsServer()

    private lateinit var refusedSessions: RefusedSessions

    @Before
    fun setUp() {
        PekeDispatchers.configure(dispatcher)
        // The app's client: every 401 goes to RefusedSessions
        http.interceptors += RefusedTokenInterceptor { refusedSessions.refused(it) }
    }

    @After
    fun tearDown() {
        PekeDispatchers.reset()
        appDb.close()
        kidsDb.close()
    }

    private fun TestScope.tvSetupRepository() =
        TvSetupRepository(kidsDb.tvSetupDao(), appDb.serverDao(), kidsDb.kidProfileDao(), backgroundScope)

    private fun TestScope.serverRepository(tvSetupRepository: TvSetupRepository) =
        ServerRepository(
            context,
            http.jellyfin,
            appDb.serverDao(),
            http.jellyfin.createApi(),
            http.deviceInfo,
            dispatcher,
            tvSetupRepository,
            KidsDataCleaner(appDb.serverDao(), kidsDb.kidProfileDao(), kidsDb.kidsServerDao()),
            mockk(relaxed = true),
            AppVisibility(),
        ).also { refusedSessions = RefusedSessions(it, backgroundScope) }

    private suspend fun kid(name: String): JellyfinUser {
        val kid =
            appDb.serverDao().addOrUpdateUser(
                JellyfinUser(id = UUID.randomUUID(), name = name, serverId = server.id, accessToken = "token-$name"),
            )
        kidsDb.kidProfileDao().upsert(KidProfile(userId = kid.rowId, serverId = server.id))
        return kid
    }

    private suspend fun setUpTv(): Pair<JellyfinUser, JellyfinUser> {
        appDb.serverDao().addOrUpdateServer(server)
        val manuel = kid("manuel")
        val lucia = kid("lucia")
        kidsDb.tvSetupDao().upsert(TvSetupEntity(serverId = server.id, serverUrl = server.url, step = TvSetupStep.DONE))
        return manuel to lucia
    }

    private fun stored(kid: JellyfinUser) = appDb.serverDao().getUser(server.id, kid.id)

    @Test
    fun `a kid whose token the server refuses needs a grown-up, and the picker sends them through the gate`() =
        runTest(dispatcher) {
            val (manuel, lucia) = setUpTv()
            val tvSetupRepository = tvSetupRepository()
            val serverRepository = serverRepository(tvSetupRepository)
            serverRepository.restoreUser(server, manuel)
            // Signed out from the dashboard: Jellyfin no longer knows the token
            http.on("GET", "/Users/Me", 401)

            try {
                serverRepository.apiClient.userApi.getCurrentUser()
            } catch (_: InvalidStatusException) {
            }

            assertNull(stored(manuel)?.accessToken)
            assertEquals("token-lucia", stored(lucia)?.accessToken)
            // Nobody is current: the kids side starts over from the launch rule
            assertNull(serverRepository.current.value)
            val tv = tvSetupRepository.state.first { s -> s is TvState.SetUp && s.kids.any { it.accessToken == null } } as TvState.SetUp
            assertTrue(needsGrownUp(tv.kids.single { it.rowId == manuel.rowId }))

            // The picker shows them so, and picking them goes through the gate, then the administrator
            val setupNavigationManager = SetupNavigationManager(NavigationManager())
            val picker =
                KidsPickerViewModel(
                    tvSetupRepository,
                    mockk<KidsAllowanceService>(relaxed = true),
                    mockk<TvLauncher>(relaxed = true),
                    setupNavigationManager,
                    mockk<ParentLockService>(relaxed = true),
                    http.jellyfin,
                )
            assertTrue(
                picker.state.value.kids
                    .single { it.user.rowId == manuel.rowId }
                    .needsGrownUp,
            )
            val revoked = tv.kids.single { it.rowId == manuel.rowId }
            picker.pick(revoked).join()
            assertEquals(
                PickAction.GATE_THEN_SIGN_IN,
                picker.state
                    .first { it.gate != null }
                    .gate
                    ?.action,
            )
            picker.gatePassed().join()
            advanceUntilIdle()
            assertEquals(SetupDestination.SignInAgain(server, revoked), setupNavigationManager.backStack.single())
        }

    @Test
    fun `a kid at bedtime keeps their token, and only a 401 takes it`() =
        runTest(dispatcher) {
            val (manuel, lucia) = setUpTv()
            val tvSetupRepository = tvSetupRepository()
            val serverRepository = serverRepository(tvSetupRepository)
            serverRepository.restoreUser(server, manuel)
            // Outside the access schedule or in the plugin's bedtime, the server answers the kid's calls with 403
            http.on("GET", "/Users/Me", 403)
            http.on("GET", "/Kids/Users/${manuel.id}/Profile", 403)
            http.on("GET", "/Videos/${manuel.id}/stream", 403)

            // The user config refresh, the profile refresh and a playback call
            try {
                serverRepository.updateUserDto()
            } catch (_: InvalidStatusException) {
            }
            assertEquals(
                KidsResult.Forbidden,
                http.api(dispatcher).profile(server.url, KidsCaller("token-manuel", manuel.id), manuel.id),
            )
            http.client
                .newCall(
                    okhttp3.Request
                        .Builder()
                        .url("${server.url}/Videos/${manuel.id}/stream")
                        .header("Authorization", """MediaBrowser Client="Peke", DeviceId="d", Token="token-manuel"""")
                        .build(),
                ).execute()
                .close()
            advanceUntilIdle()

            assertEquals("token-manuel", stored(manuel)?.accessToken)
            assertEquals(
                manuel.rowId,
                serverRepository.current.value
                    ?.user
                    ?.rowId,
            )
            val tv = tvSetupRepository.state.first { it is TvState.SetUp } as TvState.SetUp
            assertFalse(needsGrownUp(tv.kids.single { it.rowId == manuel.rowId }))
            // The launch rule still opens the kids side, where their stop screen shows
            assertEquals(LaunchDestination.Picker(server, tv.kids), LaunchRule.destination(tv))

            // Signed out from the dashboard: now the token goes
            http.on("GET", "/Users/Me", 401)
            try {
                serverRepository.updateUserDto()
            } catch (_: InvalidStatusException) {
            }
            appDb.serverDao().getUserFlow(server.id, manuel.id).first { it?.accessToken == null }
            assertNull(serverRepository.current.value)
            assertEquals("token-lucia", stored(lucia)?.accessToken)
        }

    @Test
    fun `a refused kids call marks the kid it ran as`() =
        runTest(dispatcher) {
            val (manuel, lucia) = setUpTv()
            serverRepository(tvSetupRepository())
            http.on("GET", "/Kids/Users/${lucia.id}/Status", 401)

            val result = http.api(dispatcher).status(server.url, KidsCaller("token-lucia", lucia.id), lucia.id)

            assertEquals(KidsResult.Forbidden, result)
            appDb.serverDao().getUserFlow(server.id, lucia.id).first { it?.accessToken == null }
            assertEquals("token-manuel", stored(manuel)?.accessToken)
        }

    @Test
    fun `a grant the plugin refuses, or a token nobody here has, changes nothing`() =
        runTest(dispatcher) {
            val (manuel, _) = setUpTv()
            serverRepository(tvSetupRepository())
            // The plugin refuses a missing or expired grant with 403
            http.on("PUT", "/Kids/Users/${manuel.id}/Profile", 403)
            http
                .api(
                    dispatcher,
                ).putProfile(
                    server.url,
                    KidsCaller("token-manuel", manuel.id),
                    "old-grant",
                    manuel.id,
                    kotlinx.serialization.json.JsonObject(emptyMap()),
                )
            advanceUntilIdle()
            assertEquals("token-manuel", stored(manuel)?.accessToken)

            // A token being checked during a sign-in, or an administrator's, isn't any kid's
            http.on("GET", "/Users/Me", 401)
            try {
                http.jellyfin
                    .createApi(baseUrl = server.url, accessToken = "admin-token")
                    .userApi
                    .getCurrentUser()
            } catch (_: InvalidStatusException) {
            }
            assertEquals("token-manuel", stored(manuel)?.accessToken)
            http.on("GET", "/Users/Me", 200, userJson(manuel.id))
            assertNotNull(stored(manuel))
        }

    @Test
    fun `the token is read from every way a request can carry it`() {
        val request =
            okhttp3.Request
                .Builder()
                .url("http://jf.local/Users/Me")
                .header("Authorization", """MediaBrowser Client="Peke", Device="TV", DeviceId="d", Version="1", Token="abc"""")
                .build()
        assertEquals("abc", RefusedTokenInterceptor.tokenOf(request))
        assertEquals(
            "def",
            RefusedTokenInterceptor.tokenOf(
                request
                    .newBuilder()
                    .removeHeader("Authorization")
                    .header("X-Emby-Token", "def")
                    .build(),
            ),
        )
        assertEquals(
            "ghi",
            RefusedTokenInterceptor.tokenOf(
                request
                    .newBuilder()
                    .removeHeader("Authorization")
                    .url("http://jf.local/x?api_key=ghi")
                    .build(),
            ),
        )
        assertNull(RefusedTokenInterceptor.tokenOf(request.newBuilder().removeHeader("Authorization").build()))
    }
}
