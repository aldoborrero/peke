package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.gate.GrownUpSessions
import dev.aldoborrero.peke.kids.gate.LockChange
import dev.aldoborrero.peke.kids.gate.LockState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.VerifyOutcome
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.setup.AdminSessions
import dev.aldoborrero.peke.kids.setup.KidsPickerViewModel
import dev.aldoborrero.peke.kids.setup.TvLauncher
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KidsInterruptedPlayback
import dev.aldoborrero.peke.kids.timer.KidsLimits
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.kids.timer.KnownKidsStatus
import dev.aldoborrero.peke.kids.ui.GateStage
import dev.aldoborrero.peke.kids.ui.KidsParentGateViewModel
import dev.aldoborrero.peke.services.MusicService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

/**
 * A grant from the gate lasts a few minutes ([ParentLockService.hasLiveGrant]); leaving the parent menu ends it, so the
 * kid behind the menu can't open grown-up pages with it
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidsParentGateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val parentLockService =
        mockk<ParentLockService>(relaxed = true) {
            coEvery { lockState() } returns LockState.Code(ParentLockType.NUMERIC)
            coEvery { verify(any()) } returns VerifyOutcome.Valid
        }
    private val bonus = CompletableDeferred<BonusResult>()
    private val allowanceService =
        mockk<KidsAllowanceService>(relaxed = true) {
            coEvery { addBonus(any()) } coAnswers { bonus.await() }
        }
    private val timeLimits =
        mockk<KidsTimeLimits> {
            every { state } returns MutableStateFlow(KidsLimits())
            every { interrupted } returns KidsInterruptedPlayback()
            every { allowanceService } returns this@KidsParentGateViewModelTest.allowanceService
        }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val http = FakeKidsServer()
    private val adminSessions = AdminSessions(http.jellyfin, http.deviceInfo, http.api(dispatcher), dispatcher)
    private val grownUpSessions = GrownUpSessions(parentLockService, adminSessions, CoroutineScope(dispatcher))

    private val serverId = UUID.randomUUID()
    private val server = JellyfinServer(serverId, "home", "http://jf.local", null)
    private val manuel = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Manuel", serverId = serverId, accessToken = "t1")
    private val lucia = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "Lucía", serverId = serverId, accessToken = "t2")
    private val tvState = MutableStateFlow<TvState>(TvState.SetUp(server, listOf(manuel, lucia), lastKidRowId = manuel.rowId))
    private val tvSetupRepository = mockk<TvSetupRepository> { every { state } returns tvState }
    private val serverRepository =
        mockk<ServerRepository> { every { current } returns MutableStateFlow(CurrentUser(server, manuel)) }
    private val navigationManager = mockk<NavigationManager>(relaxed = true)
    private val setupNavigationManager = mockk<SetupNavigationManager>(relaxed = true)

    private fun viewModel() =
        KidsParentGateViewModel(
            timeLimits,
            mockk<KidsPluginService>(relaxed = true),
            parentLockService,
            navigationManager,
            setupNavigationManager,
            adminSessions,
            grownUpSessions,
            tvSetupRepository,
            serverRepository,
            mockk<MusicService>(relaxed = true),
            CoroutineScope(dispatcher),
        )

    private fun KidsParentGateViewModel.openMenu() {
        submit("1234", onDone = {}) { }
        assertEquals(GateStage.Menu, stage.value)
    }

    @Test
    fun `leaving the parent menu ends the grant`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.openMenu()
            verify(exactly = 0) { parentLockService.forgetGrants() }

            vm.leaveMenu()

            verify(exactly = 1) { parentLockService.forgetGrants() }
        }

    @Test
    fun `a page opened from the menu keeps the grant it needs`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.openMenu()

            vm.openSettings()
            vm.openKidsOnTv()
            vm.openThisTv()
            vm.switchKid()
            vm.openReset()

            verify(exactly = 0) { parentLockService.forgetGrants() }
        }

    @Test
    fun `adding time ends the grant once the server has the minutes`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.openMenu()
            vm.chooseMinutes()
            var result: BonusResult? = null

            vm.addTime(15) { result = it }
            // The bonus is sent with the grant
            verify(exactly = 0) { parentLockService.forgetGrants() }

            bonus.complete(BonusResult.ADDED)
            assertEquals(BonusResult.ADDED, result)
            coVerifyOrder {
                allowanceService.addBonus(15)
                parentLockService.forgetGrants()
            }
        }

    @Test
    fun `going to the background with the menu open starts the gate over`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.openMenu()
            val epoch = vm.epoch.value

            grownUpSessions.end().join()

            // The gate closes on it, and asks for the code again if it opens
            assertEquals(epoch + 1, vm.epoch.value)
            assertEquals(GateStage.Passcode(ParentLockType.NUMERIC), vm.stage.value)
            verify { parentLockService.forgetGrants() }
        }

    @Test
    fun `going to the background during a recovery ends the administrator session`() =
        runTest(dispatcher) {
            coEvery { parentLockService.gateServer() } returns JellyfinServer(UUID.randomUUID(), "home", "http://jf.local", null)
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token"))
            http.on("POST", "/Sessions/Logout", 204)
            val vm = viewModel()
            vm.forgotCode()
            vm.adminSignIn("admin", "pw")
            // The sign-in answers from OkHttp's thread
            assertEquals(GateStage.RecoveryCode(ParentLockType.NUMERIC), vm.stage.first { it !is GateStage.AdminSignIn })
            assertTrue(adminSessions.anyOpen)

            grownUpSessions.end().join()

            assertFalse(adminSessions.anyOpen)
            assertEquals(1, http.requests.count { it.path == "/Sessions/Logout" })
            assertEquals(GateStage.Passcode(ParentLockType.NUMERIC), vm.stage.value)
            // Nothing is left to set a code with
            vm.setRecoveredCode("4321") { }
            coVerify(exactly = 0) { parentLockService.recover(any(), any(), any()) }
        }

    @Test
    fun `a recovered code opens the parent menu`() =
        runTest(dispatcher) {
            coEvery { parentLockService.gateServer() } returns JellyfinServer(UUID.randomUUID(), "home", "http://jf.local", null)
            coEvery { parentLockService.recover(any(), any(), any()) } returns LockChange.Done
            http.on("POST", "/Users/AuthenticateByName", 200, signInAnswer(true, "admin-token"))
            http.on("POST", "/Sessions/Logout", 204)
            val vm = viewModel()
            vm.forgotCode()
            vm.adminSignIn("admin", "pw")
            vm.stage.first { it is GateStage.RecoveryCode }

            vm.setRecoveredCode("4321") { }

            // With Kids on this TV and Reset this TV
            assertEquals(GateStage.Menu, vm.stage.first { it !is GateStage.RecoveryCode })
        }

    @Test
    fun `Switch kid shows only with two or more kids on this TV`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertTrue(vm.canSwitchKid.value)

            tvState.value = TvState.SetUp(server, listOf(manuel), lastKidRowId = manuel.rowId)
            assertFalse(vm.canSwitchKid.value)

            tvState.value = TvState.SetUp(server, listOf(manuel, lucia), lastKidRowId = manuel.rowId)
            assertTrue(vm.canSwitchKid.value)
            tvState.value = TvState.Loading
            assertFalse(vm.canSwitchKid.value)
        }

    @Test
    fun `Switch kid goes to the picker without a second gate, and the grant ends with the pick`() =
        runTest(dispatcher) {
            // The grant and the pick grant, as ParentLockService keeps them
            var live = false
            var pick = false
            every { parentLockService.hasLiveGrant() } answers { live }
            every { parentLockService.grantPick() } answers { pick = true }
            every { parentLockService.consumePickGrant() } answers { pick.also { pick = false } }
            every { parentLockService.forgetGrants() } answers {
                live = false
                pick = false
            }
            val vm = viewModel()
            vm.openMenu()
            live = true

            vm.switchKid()

            verify { setupNavigationManager.navigateTo(SetupDestination.KidsPicker) }
            assertEquals(GateStage.Menu, vm.stage.value)
            verify(exactly = 0) { parentLockService.forgetGrants() }

            // Manuel, the last kid, is stopped; a grown-up picks his sister anyway
            coEvery { allowanceService.lastKnownStatus(server, manuel, any()) } returns
                KnownKidsStatus(KidsStatus(timeUp = true), fresh = true, sinceFetch = Duration.ZERO)
            val launcher = mockk<TvLauncher>(relaxed = true)
            val picker =
                KidsPickerViewModel(tvSetupRepository, allowanceService, launcher, setupNavigationManager, parentLockService, http.jellyfin)

            picker.pick(lucia).join()

            assertNull(picker.state.value.gate)
            coVerifyOrder {
                parentLockService.forgetGrants()
                launcher.openKid(server, lucia)
            }
            assertFalse(parentLockService.hasLiveGrant())

            // The next picker asks again: the pick grant was for that one picker only
            val next =
                KidsPickerViewModel(tvSetupRepository, allowanceService, launcher, setupNavigationManager, parentLockService, http.jellyfin)
            next.pick(lucia).join()
            assertTrue(next.state.value.gate != null)
        }

    @Test
    fun `Settings opens in the kid's pages, or outside them from the picker`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.openMenu()

            vm.openThisTv()
            verify { navigationManager.navigateTo(Destination.Settings(PreferenceScreenOption.PEKE)) }

            vm.withKid = false
            vm.openThisTv()
            verify { setupNavigationManager.navigateTo(SetupDestination.ThisTv) }
        }
}
