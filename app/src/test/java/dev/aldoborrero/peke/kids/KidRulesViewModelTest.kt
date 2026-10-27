package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.data.SaveResult
import dev.aldoborrero.peke.kids.livetv.KidsChannel
import dev.aldoborrero.peke.kids.livetv.KidsLiveTvService
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.server.KidsServerState
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.setup.VoiceSearchSetting
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.ui.rules.KidRulesViewModel
import dev.aldoborrero.peke.kids.ui.rules.PendingDailyTime
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

/**
 * "{Kid}'s rules": Daily time is the one rule saved from the TV, opening on the current value with Unlimited last,
 * and a change the grant ran out for waits for the code (spec 006 US3)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidRulesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val serverId = UUID.randomUUID()
    private val server = JellyfinServer(serverId, "home", "https://jf.example.org/", null)
    private val leo = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Leo", serverId = serverId, accessToken = "t1")
    private var profile = KidProfile(userId = leo.rowId, serverId = serverId, revision = 2, dailyAllowanceMinutes = 60)
    private val dao =
        mockk<KidProfileDao> {
            coEvery { get(leo.rowId) } answers { profile }
        }
    private val repository = mockk<KidProfileRepository>()
    private val allowance =
        mockk<KidsAllowanceService>(relaxed = true) {
            every { snapshot } returns MutableStateFlow(null)
        }
    private val liveTv =
        mockk<KidsLiveTvService> {
            coEvery { channelsFor(leo, any()) } returns
                listOf(KidsChannel(UUID.randomUUID(), "Clan", "1"), KidsChannel(UUID.randomUUID(), null, "2"))
        }

    private fun viewModel() =
        KidRulesViewModel(
            mockk<ServerRepository> {
                every { current } returns MutableStateFlow(CurrentUser(server, leo))
                every { currentUserDto } returns null
            },
            dao,
            repository,
            mockk<KidsPluginService> {
                coEvery { state(serverId) } returns
                    KidsServerState(
                        KidsServer(serverId, 1, "profiles,parentLock,enforcement,sitting,allowance,liveTv"),
                        KidsServerStatus.AVAILABLE,
                    )
            },
            allowance,
            liveTv,
            mockk<VoiceSearchSetting> { every { on } returns flowOf(true) },
            mockk(relaxed = true),
        )

    @Test
    fun `the choices open on the current value, with unlimited last`() {
        assertEquals(listOf(5, 10, 15, 30, 45, 60, 90, 120, 180, 240, null), KidRulesViewModel.dailyTimeOptions(60))
        assertEquals(listOf(5, 10, 15, 25, 30, 45, 60, 90, 120, 180, 240, null), KidRulesViewModel.dailyTimeOptions(25))
        assertEquals(null, KidRulesViewModel.dailyTimeOptions(null).last())
        assertEquals(11, KidRulesViewModel.dailyTimeOptions(null).size)
    }

    @Test
    fun `the page shows the current kid, their channel names and the dashboard`() =
        runTest(dispatcher) {
            val state = viewModel().state.value
            assertFalse(state.loading)
            assertEquals("Leo", state.kidName)
            assertTrue(state.allowanceSupported)
            assertTrue(state.liveTvSupported)
            assertEquals(listOf("Clan"), state.channels)
            assertTrue(state.voiceSearch)
            assertEquals("https://jf.example.org/web/#/configurationpage?name=Kids", state.dashboardUrl)
        }

    @Test
    fun `a saved daily time says so and asks for a fresh status`() =
        runTest(dispatcher) {
            coEvery { repository.save(leo, any()) } answers {
                profile = secondArg()
                SaveResult.SAVED
            }
            val vm = viewModel()

            vm.setDailyTime(90)

            coVerify { repository.save(leo, match { it.dailyAllowanceMinutes == 90 && it.revision == 2L }) }
            assertTrue(vm.state.value.saved)
            assertEquals(
                90,
                vm.state.value.profile
                    ?.dailyAllowanceMinutes,
            )
            verify { allowance.refresh() }
        }

    @Test
    fun `the same value is not sent again`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.setDailyTime(60)
            coVerify(exactly = 0) { repository.save(any(), any()) }
        }

    @Test
    fun `a value the grant ran out for waits for the code, then is saved`() =
        runTest(dispatcher) {
            coEvery { repository.save(leo, any()) } returnsMany listOf(SaveResult.NEEDS_GRANT, SaveResult.SAVED)
            val vm = viewModel()

            vm.setDailyTime(null)
            assertEquals(PendingDailyTime(null), vm.state.value.pending)
            assertFalse(vm.state.value.saved)

            vm.retryPending()

            coVerify(exactly = 2) { repository.save(leo, match { it.dailyAllowanceMinutes == null }) }
            assertNull(vm.state.value.pending)
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun `leaving the code drops the value and says it wasn't saved`() =
        runTest(dispatcher) {
            coEvery { repository.save(leo, any()) } returns SaveResult.NEEDS_GRANT
            val vm = viewModel()
            vm.setDailyTime(30)

            vm.dropPending()

            assertNull(vm.state.value.pending)
            assertEquals(dev.aldoborrero.peke.R.string.kids_not_saved, vm.state.value.error)
        }
}
