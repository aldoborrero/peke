package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.FakeKidsServer
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KidsPickerTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val serverId = UUID.randomUUID()
    private val server = JellyfinServer(id = serverId, name = "home", url = "http://jf.local", version = null)
    private val manuel = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Manuel", serverId = serverId, accessToken = "t1")
    private val lucia = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "Lucía", serverId = serverId, accessToken = "t2")
    private val now = Instant.parse("2026-10-23T18:00:00Z")

    private val tvState = MutableStateFlow<TvState>(TvState.SetUp(server, listOf(manuel, lucia), lastKidRowId = manuel.rowId))
    private val tvSetupRepository = mockk<TvSetupRepository> { every { state } returns tvState }
    private val allowanceService = mockk<KidsAllowanceService> { coEvery { cachedStatus(any()) } returns null }
    private val launcher = mockk<TvLauncher>(relaxed = true)
    private val setupNavigationManager = mockk<SetupNavigationManager>(relaxed = true)
    private val parentLockService = mockk<ParentLockService>(relaxed = true)

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
        ) { now }

    private fun timeUpUntil(end: String?) = KidsStatus(timeUp = true, remainingSeconds = 0, allowanceSeconds = 3600, nextDayStartsAt = end)

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
    fun `picking the last kid opens their home, also when they are stopped`() =
        runTest(dispatcher) {
            coEvery { allowanceService.cachedStatus(manuel.rowId) } returns timeUpUntil("2026-10-24T05:00:00Z")
            val vm = viewModel()

            vm.pick(manuel).join()

            // Their home shows their time's-up screen by itself
            coVerify { launcher.openKid(server, manuel) }
            assertNull(vm.state.value.gate)
        }

    @Test
    fun `picking a sibling while the last kid is stopped asks for the parent code first`() =
        runTest(dispatcher) {
            coEvery { allowanceService.cachedStatus(manuel.rowId) } returns timeUpUntil("2026-10-24T05:00:00Z")
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
            coEvery { allowanceService.cachedStatus(manuel.rowId) } returns timeUpUntil("2026-10-24T05:00:00Z")
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
            coEvery { allowanceService.cachedStatus(manuel.rowId) } returns timeUpUntil("2026-10-23T05:00:00Z")
            vm.pick(lucia).join()
            coVerify(exactly = 2) { launcher.openKid(server, lucia) }
            assertNull(vm.state.value.gate)
        }

    @Test
    fun `a kid who needs a grown-up goes through the gate and then the administrator`() =
        runTest(dispatcher) {
            val revoked = lucia.copy(accessToken = null)
            tvState.value = TvState.SetUp(server, listOf(manuel, revoked), lastKidRowId = manuel.rowId)
            val vm = viewModel()
            assertTrue(
                vm.state.value.kids
                    .single { it.user.rowId == revoked.rowId }
                    .needsGrownUp,
            )

            vm.pick(revoked).join()
            assertEquals(PickerGate(revoked, PickAction.GATE_THEN_SIGN_IN), vm.state.value.gate)

            vm.gatePassed().join()
            verify { setupNavigationManager.navigateTo(SetupDestination.SignInAgain(server, revoked)) }
            coVerify(exactly = 0) { launcher.openKid(any(), any()) }
        }

    @Test
    fun `a denial that can't be read counts as stopped`() =
        runTest(dispatcher) {
            coEvery { allowanceService.cachedStatus(manuel.rowId) } returns KidsStatus(enforced = true, deniedReason = "somethingNew")
            val vm = viewModel()

            vm.pick(lucia).join()

            assertEquals(
                PickAction.GATE_THEN_OPEN,
                vm.state.value.gate
                    ?.action,
            )

            coEvery { allowanceService.cachedStatus(manuel.rowId) } returns timeUpUntil("not a date")
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
            coEvery { allowanceService.cachedStatus(manuel.rowId) } returns timeUpUntil("2026-10-24T05:00:00Z")
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
    fun `a kid signed in again after the gate starts without the grant`() =
        runTest(dispatcher) {
            val revoked = lucia.copy(accessToken = null)
            tvState.value = TvState.SetUp(server, listOf(manuel, revoked), lastKidRowId = manuel.rowId)
            val vm = viewModel()
            vm.pick(revoked).join()
            verify(exactly = 0) { parentLockService.forgetGrants() }

            vm.gatePassed().join()

            // The administrator signs them in; the gate's grant isn't needed for that
            verifyOrder {
                parentLockService.forgetGrants()
                setupNavigationManager.navigateTo(SetupDestination.SignInAgain(server, revoked))
            }
        }

    @Test
    fun `closing the gate keeps nothing to forget`() =
        runTest(dispatcher) {
            coEvery { allowanceService.cachedStatus(manuel.rowId) } returns timeUpUntil("2026-10-24T05:00:00Z")
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
        assertEquals(PickAction.GATE_THEN_SIGN_IN, pickAction(lucia.copy(accessToken = null), manuel.rowId, false))
        assertEquals(PickAction.GATE_THEN_SIGN_IN, pickAction(manuel.copy(accessToken = null), manuel.rowId, false))
        assertFalse(needsGrownUp(manuel))
    }
}
