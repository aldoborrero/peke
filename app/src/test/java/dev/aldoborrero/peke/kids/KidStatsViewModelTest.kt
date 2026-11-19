package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.gate.KidsAuth
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.server.KidsServerState
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.server.KidsStats
import dev.aldoborrero.peke.kids.server.KidsStatsDay
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.server.KidsTopSeries
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KnownKidsStatus
import dev.aldoborrero.peke.kids.ui.stats.KidStatsViewModel
import dev.aldoborrero.peke.kids.ui.stats.KidToday
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Stats (spec 033 D7–D10): today from each kid's status with their own token or the cache, the week from the plugin's
 * stats with the grown-up's grant, and what the page says without the feature, offline or on a 403
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidStatsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val serverId = UUID.randomUUID()
    private val server = JellyfinServer(serverId, "home", "https://jf.example.org/", null)
    private val leo = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Leo", serverId = serverId, accessToken = "t1")
    private val lucy = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "Lucy", serverId = serverId, accessToken = "t2")
    private val auth = KidsAuth(server, leo, "t1", "grant")

    private val allowance = mockk<KidsAllowanceService>()
    private val api = mockk<KidsApi>()
    private val lock =
        mockk<ParentLockService>(relaxed = true) {
            every { auth() } returns auth
        }

    private fun status(
        used: Long,
        allowance: Long? = 3600,
        bonus: Long = 0,
        breakLeft: Long? = null,
    ) = KidsStatus(usedSeconds = used, allowanceSeconds = allowance, bonusSeconds = bonus, breakRemainingSeconds = breakLeft)

    private fun week(advanced: Boolean) =
        KidsStats(
            advancedStats = advanced,
            days = (0..6).map { KidsStatsDay("2026-11-${13 + it}", usedMinutes = 10 * it, allowanceMinutes = 60) },
            topSeries = if (advanced) listOf(KidsTopSeries(name = "Bluey", minutes = 80)) else emptyList(),
        )

    private fun viewModel(
        features: String = "profiles,parentLock,enforcement,sitting,allowance,stats",
        kids: List<JellyfinUser> = listOf(leo, lucy),
    ) = KidStatsViewModel(
        mockk<ServerRepository> { every { current } returns MutableStateFlow(CurrentUser(server, leo)) },
        mockk<TvSetupRepository> { every { state } returns MutableStateFlow(TvState.SetUp(server, kids)) },
        mockk<KidsPluginService> {
            coEvery { state(serverId) } returns KidsServerState(KidsServer(serverId, 1, features), KidsServerStatus.AVAILABLE)
        },
        allowance,
        lock,
        api,
        mockk<KidAvatars> { every { observe(serverId) } returns flowOf(emptyMap()) },
    )

    @Test
    fun `every kid of this TV gets today and the week, with advanced stats on`() =
        runTest(dispatcher) {
            coEvery { allowance.lastKnownStatus(server, leo) } returns KnownKidsStatus(status(29 * 60, bonus = 600), true, Duration.ZERO)
            coEvery { allowance.lastKnownStatus(server, lucy) } returns
                KnownKidsStatus(status(52 * 60, breakLeft = 450), true, Duration.ZERO)
            coEvery { api.stats(server.url, any(), "grant", any(), 7) } returns KidsResult.Ok(week(advanced = true))

            val state = viewModel().state.value

            assertFalse(state.loading)
            assertTrue(state.statsSupported)
            assertEquals(true, state.advanced)
            assertTrue(state.reachable)
            assertFalse(state.offline)
            assertEquals(listOf("Leo", "Lucy"), state.kids.map { it.name })
            assertEquals(listOf(true, false), state.kids.map { it.watching })
            assertEquals(KidToday(29, 41, 10, null, false), state.kids[0].today)
            assertEquals(8, state.kids[1].today?.breakLeftMinutes)
            assertEquals(7, state.kids[0].bars.size)
            assertEquals(
                "Bluey",
                state.kids[1]
                    .week
                    ?.topSeries
                    ?.single()
                    ?.name,
            )
            coVerify { api.stats(server.url, match { it.token == "t1" }, "grant", leo.id, 7) }
            coVerify { api.stats(server.url, match { it.token == "t1" }, "grant", lucy.id, 7) }
        }

    @Test
    fun `with advanced stats off the week comes without the most watched`() =
        runTest(dispatcher) {
            coEvery { allowance.lastKnownStatus(server, any()) } returns KnownKidsStatus(status(600), true, Duration.ZERO)
            coEvery { api.stats(any(), any(), any(), any(), any()) } returns KidsResult.Ok(week(advanced = false))

            val state = viewModel().state.value

            assertEquals(false, state.advanced)
            assertEquals(7, state.kids[0].bars.size)
            assertTrue(state.kids.all { it.week?.topSeries.isNullOrEmpty() })
        }

    @Test
    fun `without the plugin's stats only today is shown, and the stats are never asked for`() =
        runTest(dispatcher) {
            coEvery { allowance.lastKnownStatus(server, any()) } returns KnownKidsStatus(status(600), true, Duration.ZERO)

            val state = viewModel(features = "profiles,parentLock,enforcement,sitting,allowance").state.value

            assertFalse(state.statsSupported)
            assertNull(state.advanced)
            assertTrue(state.kids.all { it.today != null && it.bars.isEmpty() })
            coVerify(exactly = 0) { api.stats(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `offline the cards show the cached status, or nothing, and the page says so`() =
        runTest(dispatcher) {
            coEvery { allowance.lastKnownStatus(server, leo) } returns KnownKidsStatus(status(1200), false, 30.minutes)
            coEvery { allowance.lastKnownStatus(server, lucy) } returns null
            coEvery { api.stats(any(), any(), any(), any(), any()) } returns KidsResult.Unreachable

            val state = viewModel().state.value

            assertFalse(state.reachable)
            assertTrue(state.offline)
            assertTrue(state.kids.all { it.stale })
            assertEquals(20, state.kids[0].today?.usedMinutes)
            assertNull(state.kids[1].today)
            assertTrue(state.kids.all { it.week == null })
        }

    @Test
    fun `a 403 on the week asks for the code, then loads it again`() =
        runTest(dispatcher) {
            coEvery { allowance.lastKnownStatus(server, any()) } returns KnownKidsStatus(status(600), true, Duration.ZERO)
            coEvery { api.stats(any(), any(), any(), any(), any()) } returnsMany
                listOf(KidsResult.Forbidden, KidsResult.Forbidden, KidsResult.Ok(week(true)), KidsResult.Ok(week(true)))
            val vm = viewModel()
            assertTrue(vm.state.value.needsGrant)
            verify { lock.dropGrant() }

            vm.retryWeek()

            assertFalse(vm.state.value.needsGrant)
            assertTrue(
                vm.state.value.kids
                    .all { it.week != null },
            )
        }

    @Test
    fun `a code that gives no grant, such as one checked offline, isn't asked for again`() =
        runTest(dispatcher) {
            every { lock.auth() } returns null
            coEvery { allowance.lastKnownStatus(server, any()) } returns KnownKidsStatus(status(600), true, Duration.ZERO)
            val vm = viewModel()
            assertTrue(vm.state.value.needsGrant)

            vm.retryWeek()

            assertFalse(vm.state.value.needsGrant)
            assertTrue(vm.state.value.weekRefused)
        }

    @Test
    fun `offline without a grant the code isn't asked for at all`() =
        runTest(dispatcher) {
            every { lock.auth() } returns null
            coEvery { allowance.lastKnownStatus(server, any()) } returns KnownKidsStatus(status(600), false, 5.minutes)
            val state = viewModel().state.value
            assertFalse(state.needsGrant)
            assertTrue(state.offline)
        }

    @Test
    fun `leaving the code keeps today only, and says the week needs it`() =
        runTest(dispatcher) {
            every { lock.auth() } returns null
            coEvery { allowance.lastKnownStatus(server, any()) } returns KnownKidsStatus(status(600), true, Duration.ZERO)
            val vm = viewModel()
            assertTrue(vm.state.value.needsGrant)

            vm.dropWeek()

            assertFalse(vm.state.value.needsGrant)
            assertTrue(vm.state.value.weekRefused)
            assertTrue(
                vm.state.value.kids
                    .all { it.week == null && it.today != null },
            )
        }

    @Test
    fun `one kid, and four kids in this TV's order`() =
        runTest(dispatcher) {
            val more =
                (3..4).map {
                    JellyfinUser(
                        rowId = it,
                        id = UUID.randomUUID(),
                        name = "Kid $it",
                        serverId = serverId,
                        accessToken = "t$it",
                    )
                }
            coEvery { allowance.lastKnownStatus(server, any()) } returns KnownKidsStatus(status(600), true, Duration.ZERO)
            coEvery { api.stats(any(), any(), any(), any(), any()) } returns KidsResult.Ok(week(true))

            assertEquals(
                listOf("Leo"),
                viewModel(kids = listOf(leo))
                    .state.value.kids
                    .map { it.name },
            )
            assertEquals(
                listOf("Leo", "Lucy", "Kid 3", "Kid 4"),
                viewModel(kids = listOf(leo, lucy) + more)
                    .state.value.kids
                    .map { it.name },
            )
        }
}
