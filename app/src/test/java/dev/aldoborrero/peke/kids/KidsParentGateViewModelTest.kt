package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.gate.LockState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.VerifyOutcome
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.setup.AdminSessions
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KidsInterruptedPlayback
import dev.aldoborrero.peke.kids.timer.KidsLimits
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.kids.ui.GateStage
import dev.aldoborrero.peke.kids.ui.KidsParentGateViewModel
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupNavigationManager
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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

    private fun viewModel() =
        KidsParentGateViewModel(
            timeLimits,
            mockk<KidsPluginService>(relaxed = true),
            parentLockService,
            mockk<NavigationManager>(relaxed = true),
            mockk<SetupNavigationManager>(relaxed = true),
            mockk<AdminSessions>(relaxed = true),
            mockk(relaxed = true),
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
            vm.openLicense()
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
}
