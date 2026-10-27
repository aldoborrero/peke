package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.gate.AdminCheck
import dev.aldoborrero.peke.kids.gate.GrownUpSessions
import dev.aldoborrero.peke.kids.gate.LockChange
import dev.aldoborrero.peke.kids.gate.LockState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.VerifyOutcome
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.setup.AdminSession
import dev.aldoborrero.peke.kids.setup.AdminSessions
import dev.aldoborrero.peke.kids.setup.AdminSignIn
import dev.aldoborrero.peke.kids.setup.KidsPickerViewModel
import dev.aldoborrero.peke.kids.setup.TvLauncher
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.kids.timer.AddTimeAmount
import dev.aldoborrero.peke.kids.timer.BonusOutcome
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KidsInterruptedPlayback
import dev.aldoborrero.peke.kids.timer.KidsLimits
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.kids.timer.KnownKidsStatus
import dev.aldoborrero.peke.kids.timer.TodayChange
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
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration

/**
 * A grant from the gate lasts a few minutes ([ParentLockService.hasLiveGrant]); leaving the parent menu ends it, so the
 * kid behind the menu can't open grown-up pages with it
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidsParentGateViewModelTest {
    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 26, 18, 0)
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val parentLockService =
        mockk<ParentLockService>(relaxed = true) {
            coEvery { lockState() } returns LockState.Code(ParentLockType.NUMERIC)
            coEvery { verify(any()) } returns VerifyOutcome.Valid
        }
    private val bonus = CompletableDeferred<BonusOutcome>()
    private val allowanceService =
        mockk<KidsAllowanceService>(relaxed = true) {
            coEvery { addBonus(any()) } coAnswers { bonus.await() }
        }
    private val holdStop = MutableStateFlow(false)
    private var storedAmount: Int? = null
    private val addTimeAmount =
        mockk<AddTimeAmount> {
            coEvery { get() } answers { storedAmount ?: 15 }
            coEvery { set(any()) } answers { storedAmount = firstArg() }
        }
    private val timeLimits =
        mockk<KidsTimeLimits> {
            every { state } returns MutableStateFlow(KidsLimits())
            every { interrupted } returns KidsInterruptedPlayback()
            every { holdStop } returns this@KidsParentGateViewModelTest.holdStop
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
        mockk<ServerRepository> {
            every { current } returns MutableStateFlow(CurrentUser(server, manuel))
            every { currentUserDto } returns null
        }
    private val navigationManager = mockk<NavigationManager>(relaxed = true)
    private val setupNavigationManager = mockk<SetupNavigationManager>(relaxed = true)

    private fun viewModel(sessions: AdminSessions = adminSessions) =
        KidsParentGateViewModel(
            timeLimits,
            mockk<KidsPluginService>(relaxed = true),
            parentLockService,
            navigationManager,
            setupNavigationManager,
            sessions,
            grownUpSessions,
            tvSetupRepository,
            serverRepository,
            mockk<MusicService>(relaxed = true),
            addTimeAmount,
            CoroutineScope(dispatcher),
        ).also { it.now = { NOW } }

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

            vm.openRules()
            vm.openThisTv()
            vm.switchKid()
            vm.openReset()

            verify(exactly = 0) { parentLockService.forgetGrants() }
        }

    private suspend fun KidsParentGateViewModel.openStepper(): GateStage.AddTime {
        openMenu()
        openAddTime()
        return stage.first { it is GateStage.AddTime } as GateStage.AddTime
    }

    @Test
    fun `Add time opens on this TV's last amount, 15 the first time, and never wraps`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(15, vm.openStepper().minutes)
            assertTrue(holdStop.value)

            repeat(10) { vm.stepAddTime(-1) }
            assertEquals(5, (vm.stage.value as GateStage.AddTime).minutes)
            repeat(20) { vm.stepAddTime(1) }
            assertEquals(120, (vm.stage.value as GateStage.AddTime).minutes)

            storedAmount = 45
            vm.backToMenu()
            assertEquals(45, vm.openStepper().minutes)
        }

    @Test
    fun `Back on the stepper returns to the menu with the grant`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.openStepper()

            vm.backToMenu()

            assertEquals(GateStage.Menu, vm.stage.value)
            assertFalse(holdStop.value)
            verify(exactly = 0) { parentLockService.forgetGrants() }
        }

    @Test
    fun `OK sends once, stores the amount and keeps the grant until the confirmation closes`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.openStepper()
            vm.stepAddTime(1)

            vm.sendAddTime()
            vm.sendAddTime()
            assertTrue((vm.stage.value as GateStage.AddTime).sending)
            coVerify(exactly = 1) { allowanceService.addBonus(20) }

            bonus.complete(BonusOutcome(BonusResult.ADDED, bonusId = 42))
            val added = vm.stage.value as GateStage.Added
            assertEquals(20, added.minutes)
            assertEquals(42L, added.bonusId)
            assertEquals(NOW.plusMinutes(20), added.until)
            assertEquals(20, storedAmount)
            assertTrue(holdStop.value)
            verify(exactly = 0) { parentLockService.forgetGrants() }

            vm.closeAdded()

            verify(exactly = 1) { parentLockService.forgetGrants() }
            assertFalse(holdStop.value)
        }

    @Test
    fun `a failure shows inline and OK retries`() =
        runTest(dispatcher) {
            coEvery { allowanceService.addBonus(any()) } returns BonusOutcome(BonusResult.UNREACHABLE)
            val vm = viewModel()
            vm.openStepper()

            vm.sendAddTime()

            assertEquals(GateStage.AddTime(2, sending = false, error = BonusResult.UNREACHABLE), vm.stage.value)
            assertEquals(null, storedAmount)
            vm.sendAddTime()
            coVerify(exactly = 2) { allowanceService.addBonus(15) }
        }

    @Test
    fun `a grant that ran out asks for the code, then sends the same amount`() =
        runTest(dispatcher) {
            coEvery { allowanceService.addBonus(any()) } returnsMany
                listOf(BonusOutcome(BonusResult.NEEDS_GRANT), BonusOutcome(BonusResult.ADDED))
            val vm = viewModel()
            vm.openStepper()
            vm.stepAddTime(2)

            vm.sendAddTime()
            assertEquals(GateStage.Passcode(ParentLockType.NUMERIC), vm.stage.value)

            vm.submit("1234", onDone = {}) { }

            coVerify(exactly = 2) { allowanceService.addBonus(30) }
            assertTrue(vm.stage.value is GateStage.Added)
        }

    @Test
    fun `Undo takes the time back and closes the gate`() =
        runTest(dispatcher) {
            coEvery { allowanceService.addBonus(any()) } returns BonusOutcome(BonusResult.ADDED, bonusId = 7)
            coEvery { allowanceService.undoBonus(7) } returns TodayChange.DONE
            val vm = viewModel()
            vm.openStepper()
            vm.sendAddTime()
            var closed = false

            vm.undoAddTime { closed = true }

            assertTrue(closed)
            assertFalse(timeLimits.interrupted.timeAdded)
            verify(exactly = 1) { parentLockService.forgetGrants() }
            assertFalse(holdStop.value)
        }

    @Test
    fun `Undo is offered for 10 seconds at most, and never past the server's window`() =
        runTest(dispatcher) {
            val vm = viewModel()
            coEvery { allowanceService.addBonus(any()) } returns BonusOutcome(BonusResult.ADDED, bonusId = 7, undoSeconds = 600)
            vm.openStepper()
            vm.sendAddTime()
            assertEquals(10, (vm.stage.value as GateStage.Added).undoSeconds)

            // The kid's day ends in 3 seconds
            coEvery { allowanceService.addBonus(any()) } returns BonusOutcome(BonusResult.ADDED, bonusId = 8, undoSeconds = 3)
            vm.closeAdded()
            vm.reset()
            vm.openStepper()
            vm.sendAddTime()
            assertEquals(3, (vm.stage.value as GateStage.Added).undoSeconds)

            coEvery { allowanceService.addBonus(any()) } returns BonusOutcome(BonusResult.ADDED, bonusId = 9, undoSeconds = 0)
            vm.reset()
            vm.openStepper()
            vm.sendAddTime()
            assertNull((vm.stage.value as GateStage.Added).bonusId)
        }

    @Test
    fun `an Undo of a bonus already gone closes the gate and says so`() =
        runTest(dispatcher) {
            coEvery { allowanceService.addBonus(any()) } returns BonusOutcome(BonusResult.ADDED, bonusId = 7)
            coEvery { allowanceService.undoBonus(7) } returns TodayChange.ALREADY_UNDONE
            val vm = viewModel()
            vm.openStepper()
            vm.sendAddTime()
            var result: TodayChange? = null

            vm.undoAddTime { result = it }

            assertEquals(TodayChange.ALREADY_UNDONE, result)
            assertFalse(timeLimits.interrupted.timeAdded)
            assertFalse(holdStop.value)
        }

    @Test
    fun `an Undo that comes too late says so and is no longer offered`() =
        runTest(dispatcher) {
            coEvery { allowanceService.addBonus(any()) } returns BonusOutcome(BonusResult.ADDED, bonusId = 7)
            coEvery { allowanceService.undoBonus(7) } returns TodayChange.TOO_LATE
            val vm = viewModel()
            vm.openStepper()
            vm.sendAddTime()

            vm.undoAddTime { }

            val added = vm.stage.value as GateStage.Added
            assertEquals(TodayChange.TOO_LATE, added.undoError)
            assertNull(added.bonusId)
        }

    @Test
    fun `without a kid, the code opens Settings`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.withKid = false
            vm.openMenu()
            // The status isn't asked for: there is no kid
            coVerify(exactly = 0) { allowanceService.check() }
        }

    @Test
    fun `OK with no code opens Forgot the code without asking the server, and Back returns`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.stage.first { it is GateStage.Passcode && it.type != null }

            vm.submit("", onDone = {}) { }

            assertEquals(GateStage.AdminSignIn(recovery = true), vm.stage.value)
            coVerify(exactly = 0) { parentLockService.verify(any()) }
            assertTrue(vm.backFromSignIn())
            assertEquals(GateStage.Passcode(ParentLockType.NUMERIC), vm.stage.value)
        }

    @Test
    fun `Forgot the code with Quick Connect shows the code, then asks for the new code`() =
        runTest(dispatcher) {
            coEvery { parentLockService.gateServer() } returns server
            val approved = CompletableDeferred<Unit>()
            val sessions =
                mockk<AdminSessions>(relaxed = true) {
                    coEvery { openWithQuickConnect(server, any()) } coAnswers {
                        secondArg<suspend (String) -> Unit>().invoke("123456")
                        approved.await()
                        AdminSignIn.Opened(mockk<AdminSession>(relaxed = true))
                    }
                }
            val vm = viewModel(sessions)
            vm.forgotCode()

            vm.startQuickConnect()
            assertEquals(
                GateStage.AdminSignIn(checking = true, recovery = true, quickConnect = true, quickConnectCode = "123456"),
                vm.stage.value,
            )

            approved.complete(Unit)
            assertEquals(GateStage.RecoveryCode(ParentLockType.NUMERIC), vm.stage.value)
        }

    @Test
    fun `Quick Connect turned off says so`() =
        runTest(dispatcher) {
            coEvery { parentLockService.gateServer() } returns server
            val sessions =
                mockk<AdminSessions>(relaxed = true) {
                    coEvery { openWithQuickConnect(server, any()) } returns
                        AdminSignIn.QuickConnectOff
                }
            val vm = viewModel(sessions)
            vm.forgotCode()

            vm.startQuickConnect()

            assertEquals(GateStage.AdminSignIn(error = AdminCheck.QUICK_CONNECT_OFF, recovery = true, quickConnect = true), vm.stage.value)
            vm.usePassword()
            assertEquals(GateStage.AdminSignIn(recovery = true), vm.stage.value)
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
                KidsPickerViewModel(
                    tvSetupRepository,
                    allowanceService,
                    launcher,
                    setupNavigationManager,
                    parentLockService,
                    http.jellyfin,
                    KidAvatars(FakeKidProfileDao(), FakeKidsServerDao()),
                )

            picker.pick(lucia).join()

            assertNull(picker.state.value.gate)
            coVerifyOrder {
                parentLockService.forgetGrants()
                launcher.openKid(server, lucia)
            }
            assertFalse(parentLockService.hasLiveGrant())

            // The next picker asks again: the pick grant was for that one picker only
            val next =
                KidsPickerViewModel(
                    tvSetupRepository,
                    allowanceService,
                    launcher,
                    setupNavigationManager,
                    parentLockService,
                    http.jellyfin,
                    KidAvatars(FakeKidProfileDao(), FakeKidsServerDao()),
                )
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
