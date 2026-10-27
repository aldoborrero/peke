package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidProfileDao
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.FakeKidsServerDao
import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsJson
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KnownKidsStatus
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class KidsPickerTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val serverId = UUID.randomUUID()
    private val server = JellyfinServer(id = serverId, name = "home", url = "http://jf.local", version = null)
    private val manuel = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Manuel", serverId = serverId, accessToken = "t1")
    private val lucia = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "Lucía", serverId = serverId, accessToken = "t2")

    private val tvState = MutableStateFlow<TvState>(TvState.SetUp(server, listOf(manuel, lucia), lastKidRowId = manuel.rowId))
    private val tvSetupRepository = mockk<TvSetupRepository> { every { state } returns tvState }

    // By default the server says the last kid may watch
    private val allowanceService =
        mockk<KidsAllowanceService> {
            coEvery { lastKnownStatus(any(), any(), any()) } returns
                KnownKidsStatus(KidsStatus(), fresh = true, sinceFetch = Duration.ZERO)
        }
    private val launcher = mockk<TvLauncher>(relaxed = true)
    private val setupNavigationManager = mockk<SetupNavigationManager>(relaxed = true)
    private val parentLockService = mockk<ParentLockService>(relaxed = true)
    private val profiles = FakeKidProfileDao()
    private val kidsServers = FakeKidsServerDao()

    @Before
    fun setUp() = PekeDispatchers.configure(dispatcher)

    @After
    fun tearDown() = PekeDispatchers.reset()

    private fun viewModel() =
        KidsPickerViewModel(
            tvSetupRepository,
            allowanceService,
            launcher,
            setupNavigationManager,
            parentLockService,
            FakeKidsServer().jellyfin,
            KidAvatars(profiles, kidsServers),
        )

    /**
     * The last kid's status is only the cached one: the server can't be reached
     */
    private fun cached(
        status: KidsStatus?,
        sinceFetch: Duration? = 1.minutes,
    ) {
        coEvery { allowanceService.lastKnownStatus(server, manuel, any()) } returns
            status?.let { KnownKidsStatus(it, fresh = false, sinceFetch = sinceFetch) }
    }

    /**
     * Time's up, sent by the server at 18:00 until [end]
     */
    private fun timeUpUntil(end: String?) =
        KidsStatus(
            timeUp = true,
            remainingSeconds = 0,
            allowanceSeconds = 3600,
            serverTime = "2026-10-23T18:00:00Z",
            nextDayStartsAt = end,
        )

    @Test
    fun `only this TV's kids are listed`() =
        runTest(dispatcher) {
            val vm = viewModel()

            assertEquals(
                listOf(manuel, lucia),
                vm.state.value.kids
                    .map { it.user },
            )
            assertEquals(server, vm.state.value.server)
            assertTrue(
                vm.state.value.kids
                    .none { it.needsGrownUp },
            )
        }

    @Test
    fun `each kid shows the avatar a grown-up picked in the plugin`() =
        runTest(dispatcher) {
            kidsServers.upsert(
                KidsServer(serverId, apiVersion = 1, features = (KidsFeatures.REQUIRED + KidsFeatures.AVATAR).joinToString(",")),
            )
            val json = """{"UserId":"${lucia.id}","Revision":1,"Avatar":"mia"}"""
            profiles.upsert(KidProfileMapper.fromServer(lucia.rowId, serverId, KidsJson.parseToJsonElement(json).jsonObject))
            val vm = viewModel()

            assertEquals(
                listOf(null, PekeAvatar.MIA),
                vm.state.value.kids
                    .map { it.avatar },
            )
        }

    @Test
    fun `picking the last kid opens their home, also when they are stopped`() =
        runTest(dispatcher) {
            cached(timeUpUntil("2026-10-24T05:00:00Z"))
            val vm = viewModel()

            vm.pick(manuel).join()

            // Their home shows their time's-up screen by itself
            coVerify { launcher.openKid(server, manuel) }
            assertNull(vm.state.value.gate)
        }

    @Test
    fun `picking a sibling while the last kid is stopped asks for the parent code first`() =
        runTest(dispatcher) {
            cached(timeUpUntil("2026-10-24T05:00:00Z"))
            val vm = viewModel()

            vm.pick(lucia).join()

            assertEquals(PickerGate(lucia, PickAction.GATE_THEN_OPEN), vm.state.value.gate)
            coVerify(exactly = 0) { launcher.openKid(any(), any()) }

            vm.gatePassed().join()
            coVerify { launcher.openKid(server, lucia) }
            assertNull(vm.state.value.gate)
        }

    @Test
    fun `closing the gate opens nobody`() =
        runTest(dispatcher) {
            cached(timeUpUntil("2026-10-24T05:00:00Z"))
            val vm = viewModel()
            vm.pick(lucia).join()

            vm.gateDismissed()

            assertNull(vm.state.value.gate)
            coVerify(exactly = 0) { launcher.openKid(any(), any()) }
        }

    @Test
    fun `a sibling is free otherwise`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.pick(lucia).join()
            coVerify { launcher.openKid(server, lucia) }

            // A time's up from yesterday is over
            cached(timeUpUntil("2026-10-23T05:00:00Z"))
            vm.pick(lucia).join()
            coVerify(exactly = 2) { launcher.openKid(server, lucia) }
            assertNull(vm.state.value.gate)
        }

    @Test
    fun `a denial that can't be read counts as stopped`() =
        runTest(dispatcher) {
            cached(KidsStatus(enforced = true, deniedReason = "somethingNew"))
            val vm = viewModel()

            vm.pick(lucia).join()

            assertEquals(
                PickAction.GATE_THEN_OPEN,
                vm.state.value.gate
                    ?.action,
            )

            cached(timeUpUntil("not a date"))
            vm.gateDismissed()
            vm.pick(lucia).join()
            assertEquals(
                PickAction.GATE_THEN_OPEN,
                vm.state.value.gate
                    ?.action,
            )
        }

    @Test
    fun `a gate passed to switch kids ends once the kid is chosen`() =
        runTest(dispatcher) {
            cached(timeUpUntil("2026-10-24T05:00:00Z"))
            val vm = viewModel()

            vm.pick(lucia).join()
            // Waiting for the code: nothing to forget yet
            verify(exactly = 0) { parentLockService.forgetGrants() }

            vm.gatePassed().join()
            // The new kid can't use the grown-up's grant to open grown-up pages
            coVerifyOrder {
                parentLockService.forgetGrants()
                launcher.openKid(server, lucia)
            }
            verify(exactly = 1) { parentLockService.forgetGrants() }
        }

    @Test
    fun `a kid picked after the sidebar's gate doesn't keep the grown-up's grant`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.pick(lucia).join()

            coVerifyOrder {
                parentLockService.forgetGrants()
                launcher.openKid(server, lucia)
            }
        }

    @Test
    fun `closing the gate keeps nothing to forget`() =
        runTest(dispatcher) {
            cached(timeUpUntil("2026-10-24T05:00:00Z"))
            val vm = viewModel()
            vm.pick(lucia).join()

            vm.gateDismissed()

            verify(exactly = 0) { parentLockService.forgetGrants() }
        }

    @Test
    fun `the rule for each pick`() {
        assertEquals(PickAction.OPEN, pickAction(manuel, lastKidRowId = manuel.rowId, lastKidStopped = true))
        assertEquals(PickAction.OPEN, pickAction(lucia, lastKidRowId = manuel.rowId, lastKidStopped = false))
        assertEquals(PickAction.GATE_THEN_OPEN, pickAction(lucia, lastKidRowId = manuel.rowId, lastKidStopped = true))
        assertEquals(PickAction.OPEN, pickAction(lucia, lastKidRowId = null, lastKidStopped = false))
        assertFalse(needsGrownUp(manuel))
    }

    @Test
    fun `a last kid whose status is unknown or unreadable counts as stopped`() =
        runTest(dispatcher) {
            // Neither the server nor the cache can tell: missing, or unreadable (the read gives nothing)
            cached(null)
            val vm = viewModel()

            vm.pick(lucia).join()

            assertEquals(PickerGate(lucia, PickAction.GATE_THEN_OPEN), vm.state.value.gate)
            coVerify(exactly = 0) { launcher.openKid(any(), any()) }
        }

    @Test
    fun `with no last kid, as on a TV just set up, nobody is stopped`() =
        runTest(dispatcher) {
            tvState.value = TvState.SetUp(server, listOf(manuel, lucia), lastKidRowId = null)
            cached(null)
            val vm = viewModel()

            vm.pick(lucia).join()

            coVerify { launcher.openKid(server, lucia) }
            coVerify(exactly = 0) { allowanceService.lastKnownStatus(any(), any(), any()) }
        }

    @Test
    fun `the server's fresh word decides over a stale cache`() =
        runTest(dispatcher) {
            // The cache still has yesterday's allowance; the server says time is up now
            coEvery { allowanceService.lastKnownStatus(server, manuel, any()) } returns
                KnownKidsStatus(KidsStatus(timeUp = true), fresh = true, sinceFetch = Duration.ZERO)
            val vm = viewModel()
            vm.pick(lucia).join()
            assertEquals(
                PickAction.GATE_THEN_OPEN,
                vm.state.value.gate
                    ?.action,
            )

            // And the other way round: a time's up the server no longer has
            vm.gateDismissed()
            coEvery { allowanceService.lastKnownStatus(server, manuel, any()) } returns
                KnownKidsStatus(KidsStatus(), fresh = true, sinceFetch = Duration.ZERO)
            vm.pick(lucia).join()
            coVerify { launcher.openKid(server, lucia) }
        }

    @Test
    fun `a cached stop counts down by the monotonic clock, not the wall clock`() =
        runTest(dispatcher) {
            val vm = viewModel()
            // Offline, a minute after the fetch by the monotonic clock, whatever the wall clock says: still stopped
            cached(timeUpUntil("2026-10-24T05:00:00Z"), sinceFetch = 1.minutes)
            vm.pick(lucia).join()
            assertEquals(
                PickAction.GATE_THEN_OPEN,
                vm.state.value.gate
                    ?.action,
            )

            // After a reboot, or from a cache written before the fetch time was kept, the time since can't be told
            vm.gateDismissed()
            cached(timeUpUntil("2026-10-24T05:00:00Z"), sinceFetch = null)
            vm.pick(lucia).join()
            assertEquals(
                PickAction.GATE_THEN_OPEN,
                vm.state.value.gate
                    ?.action,
            )

            // Past the stop the server gave, by the monotonic clock
            vm.gateDismissed()
            cached(timeUpUntil("2026-10-24T05:00:00Z"), sinceFetch = 11.hours + 1.seconds)
            vm.pick(lucia).join()
            coVerify { launcher.openKid(server, lucia) }
            assertNull(vm.state.value.gate)
        }

    @Test
    fun `the stopped rule`() {
        val timeUp = timeUpUntil("2026-10-24T05:00:00Z")

        fun cached(sinceFetch: Duration?) = KnownKidsStatus(timeUp, fresh = false, sinceFetch = sinceFetch)
        assertFalse(lastKidStopped(lastKid = false, status = null))
        assertTrue(lastKidStopped(lastKid = true, status = null))
        // 11 hours left at the fetch
        assertTrue(lastKidStopped(lastKid = true, status = cached(Duration.ZERO)))
        assertTrue(lastKidStopped(lastKid = true, status = cached(11.hours - 1.seconds)))
        assertFalse(lastKidStopped(lastKid = true, status = cached(11.hours)))
        // A reboot or an old cache: the time since the fetch is unknown (fail closed)
        assertTrue(lastKidStopped(lastKid = true, status = cached(null)))
        // Without the server's time, the stop left is unknown (fail closed)
        assertTrue(
            lastKidStopped(lastKid = true, status = KnownKidsStatus(timeUp.copy(serverTime = null), fresh = false, sinceFetch = 12.hours)),
        )
        // A fresh status is the server's word, whatever the time since
        assertTrue(lastKidStopped(lastKid = true, status = KnownKidsStatus(timeUp, fresh = true, sinceFetch = 12.hours)))
        assertFalse(lastKidStopped(lastKid = true, status = KnownKidsStatus(KidsStatus(), fresh = true, sinceFetch = Duration.ZERO)))
        // A denial this app doesn't know stops (fail closed)
        assertTrue(
            lastKidStopped(
                lastKid = true,
                status = KnownKidsStatus(KidsStatus(deniedReason = "somethingNew"), fresh = true, sinceFetch = Duration.ZERO),
            ),
        )
    }

    @Test
    fun `after Switch kid a grown-up picks any kid while the grant is live`() =
        runTest(dispatcher) {
            cached(timeUpUntil("2026-10-24T05:00:00Z"))
            every { parentLockService.consumePickGrant() } returns true
            every { parentLockService.hasLiveGrant() } returns true
            val vm = viewModel()

            vm.pick(lucia).join()

            assertNull(vm.state.value.gate)
            coVerifyOrder {
                parentLockService.forgetGrants()
                launcher.openKid(server, lucia)
            }
        }

    @Test
    fun `after Switch kid a grant that ran out asks for the code again`() =
        runTest(dispatcher) {
            cached(timeUpUntil("2026-10-24T05:00:00Z"))
            every { parentLockService.consumePickGrant() } returns true
            every { parentLockService.hasLiveGrant() } returns false
            val vm = viewModel()

            vm.pick(lucia).join()

            assertEquals(PickerGate(lucia, PickAction.GATE_THEN_OPEN), vm.state.value.gate)
            coVerify(exactly = 0) { launcher.openKid(any(), any()) }
        }
}
