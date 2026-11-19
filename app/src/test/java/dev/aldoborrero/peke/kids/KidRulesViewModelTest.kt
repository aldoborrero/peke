package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.data.SaveResult
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsJson
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.server.KidsServerState
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.ui.rules.KidRulesViewModel
import dev.aldoborrero.peke.kids.ui.rules.RuleChange
import dev.aldoborrero.peke.kids.ui.rules.RulesKid
import dev.aldoborrero.peke.ui.theme.PekeAvatar
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * "{Kid}'s rules": daily time, time in a row, autoplay and search are saved from the TV (spec 030), daily time
 * opening on the current value with Unlimited last, and a change the grant ran out for waits for the code (spec 006 US3)
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
    private val lucy = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "Lucy", serverId = serverId, accessToken = "t2")
    private var lucyProfile = KidProfile(userId = lucy.rowId, serverId = serverId, revision = 5, dailyAllowanceMinutes = 30)
    private val dao =
        mockk<KidProfileDao> {
            coEvery { get(leo.rowId) } answers { profile }
            coEvery { get(lucy.rowId) } answers { lucyProfile }
        }
    private val repository = mockk<KidProfileRepository>()
    private val allowance =
        mockk<KidsAllowanceService>(relaxed = true) {
            every { snapshot } returns MutableStateFlow(null)
        }

    private fun viewModel(
        features: String = "profiles,parentLock,enforcement,sitting,allowance,liveTv",
        kids: List<JellyfinUser> = listOf(leo),
    ) = KidRulesViewModel(
        mockk<ServerRepository> {
            every { current } returns MutableStateFlow(CurrentUser(server, leo))
        },
        dao,
        repository,
        mockk<KidsPluginService> {
            coEvery { state(serverId) } returns
                KidsServerState(
                    KidsServer(serverId, 1, features),
                    KidsServerStatus.AVAILABLE,
                )
        },
        allowance,
        mockk<TvSetupRepository> { every { state } returns MutableStateFlow(TvState.SetUp(server, kids)) },
        mockk<KidAvatars> { every { observe(serverId) } returns flowOf(mapOf(lucy.rowId to PekeAvatar.entries.first())) },
    )

    @Test
    fun `the choices open on the current value, with unlimited last`() {
        assertEquals(listOf(5, 10, 15, 30, 45, 60, 90, 120, 180, 240, null), KidRulesViewModel.dailyTimeOptions(60))
        assertEquals(listOf(5, 10, 15, 25, 30, 45, 60, 90, 120, 180, 240, null), KidRulesViewModel.dailyTimeOptions(25))
        assertEquals(null, KidRulesViewModel.dailyTimeOptions(null).last())
        assertEquals(11, KidRulesViewModel.dailyTimeOptions(null).size)
    }

    @Test
    fun `the page shows the current kid and the features, and nothing read only`() =
        runTest(dispatcher) {
            val state = viewModel().state.value
            assertFalse(state.loading)
            assertEquals("Leo", state.kidName)
            assertEquals(leo.id, state.kidId)
            assertTrue(state.allowanceSupported)
            assertEquals(listOf(RulesKid(leo.id, "Leo")), state.kids)
        }

    @Test
    fun `a saved daily time says so and asks for a fresh status`() =
        runTest(dispatcher) {
            coEvery { repository.save(leo, any()) } answers {
                profile = secondArg()
                SaveResult.SAVED
            }
            val vm = viewModel()

            vm.save(RuleChange.DailyTime(90))

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
            vm.save(RuleChange.DailyTime(60))
            coVerify(exactly = 0) { repository.save(any(), any()) }
        }

    @Test
    fun `a value the grant ran out for waits for the code, then is saved`() =
        runTest(dispatcher) {
            coEvery { repository.save(leo, any()) } returnsMany listOf(SaveResult.NEEDS_GRANT, SaveResult.SAVED)
            val vm = viewModel()

            vm.save(RuleChange.DailyTime(null))
            assertEquals(RuleChange.DailyTime(null), vm.state.value.pending)
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
            vm.save(RuleChange.DailyTime(30))

            vm.dropPending()

            assertNull(vm.state.value.pending)
            assertEquals(dev.aldoborrero.peke.R.string.kids_not_saved, vm.state.value.error)
        }

    private val serverProfile =
        """
        {"UserId":"${leo.id}","Revision":2,"SearchAllowed":false,"AutoplayEnabled":true,"AutoplayCap":3,
         "Sections":[{"Section":"New","Visible":false}],"PickedItemIds":["${UUID.randomUUID()}"],
         "DailyAllowanceMinutes":60,"SessionLimitMinutes":30,"LiveTvChannelIds":["${UUID.randomUUID()}"],
         "FutureField":{"x":1}}
        """.trimIndent()

    /**
     * Save [change] from the server's profile and return the keys of the PUT body that differ from an unchanged one
     */
    private fun changedKeys(
        change: RuleChange,
        features: String = SITTING,
    ): Map<String, JsonElement?> {
        profile = KidProfileMapper.fromServer(leo.rowId, serverId, KidsJson.parseToJsonElement(serverProfile).jsonObject)
        val before = profile
        val sent = mutableListOf<KidProfile>()
        coEvery { repository.save(leo, capture(sent)) } answers {
            profile = secondArg()
            SaveResult.SAVED
        }
        val vm = viewModel(features)
        vm.save(change)
        assertTrue(vm.state.value.saved)
        val old = KidProfileMapper.toUpdate(before, before.revision, sendSessionLimit = true)
        val new = KidProfileMapper.toUpdate(sent.single(), before.revision, sendSessionLimit = true)
        return (old.keys + new.keys).filter { old[it] != new[it] }.associateWith { new[it] }
    }

    @Test
    fun `autoplay up to N episodes is sent alone, and the rest is kept`() =
        runTest(dispatcher) {
            assertEquals(mapOf("AutoplayCap" to JsonPrimitive(7)), changedKeys(RuleChange.Autoplay(7)))
        }

    @Test
    fun `autoplay off keeps the cap for later`() =
        runTest(dispatcher) {
            assertEquals(mapOf("AutoplayEnabled" to JsonPrimitive(false)), changedKeys(RuleChange.Autoplay(null)))
            assertEquals(3, profile.autoplayCap)
        }

    @Test
    fun `search is sent alone`() =
        runTest(dispatcher) {
            assertEquals(mapOf("SearchAllowed" to JsonPrimitive(true)), changedKeys(RuleChange.Search(true)))
        }

    @Test
    fun `time in a row is sent alone, and no limit as null`() =
        runTest(dispatcher) {
            assertEquals(mapOf("SessionLimitMinutes" to JsonPrimitive(45)), changedKeys(RuleChange.SittingLimit(45)))
            assertEquals(mapOf("SessionLimitMinutes" to JsonNull), changedKeys(RuleChange.SittingLimit(null)))
        }

    @Test
    fun `daily time is sent alone`() =
        runTest(dispatcher) {
            assertEquals(mapOf("DailyAllowanceMinutes" to JsonPrimitive(90)), changedKeys(RuleChange.DailyTime(90)))
        }

    @Test
    fun `the autoplay cap is held to 1 to 10, or 0 for no limit`() {
        val base = KidProfile(userId = 1)
        assertEquals(10, RuleChange.Autoplay(42).apply(base).autoplayCap)
        assertEquals(1, RuleChange.Autoplay(-3).apply(base).autoplayCap)
        assertEquals(0, RuleChange.Autoplay(KidProfile.UNLIMITED_AUTOPLAY_CAP).apply(base).autoplayCap)
        assertEquals(listOf(null) + (1..10).toList(), KidRulesViewModel.AUTOPLAY_OPTIONS)
    }

    @Test
    fun `time in a row opens on the current value, with no limit last`() {
        assertEquals(listOf(10, 15, 20, 25, 30, 45, 60, 90, 120, null), KidRulesViewModel.sittingOptions(25))
        assertEquals(null, KidRulesViewModel.sittingOptions(null).last())
    }

    @Test
    fun `time in a row is changed only when the plugin advertises the sitting limit`() =
        runTest(dispatcher) {
            profile = profile.copy(sessionLimitMinutes = 30)
            val without = viewModel()
            assertFalse(without.state.value.sessionLimitEditable)
            without.save(RuleChange.SittingLimit(45))
            coVerify(exactly = 0) { repository.save(any(), any()) }

            coEvery { repository.save(leo, any()) } returns SaveResult.SAVED
            val with = viewModel(SITTING)
            assertTrue(with.state.value.sessionLimitEditable)
            with.save(RuleChange.SittingLimit(45))
            coVerify { repository.save(leo, match { it.sessionLimitMinutes == 45 }) }
        }

    @Test
    fun `an autoplay change the grant ran out for waits for the code, then is saved`() =
        runTest(dispatcher) {
            coEvery { repository.save(leo, any()) } returnsMany listOf(SaveResult.NEEDS_GRANT, SaveResult.SAVED)
            val vm = viewModel()

            vm.save(RuleChange.Autoplay(5))
            assertEquals(RuleChange.Autoplay(5), vm.state.value.pending)
            assertFalse(vm.state.value.saved)

            vm.retryPending()

            coVerify(exactly = 2) { repository.save(leo, match { it.autoplayEnabled && it.autoplayCap == 5 }) }
            assertNull(vm.state.value.pending)
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun `a search change the grant ran out for is kept for the code`() =
        runTest(dispatcher) {
            coEvery { repository.save(leo, any()) } returnsMany listOf(SaveResult.NEEDS_GRANT, SaveResult.SAVED)
            val vm = viewModel()
            vm.save(RuleChange.Search(true))
            assertEquals(RuleChange.Search(true), vm.state.value.pending)
            vm.retryPending()
            coVerify(exactly = 2) { repository.save(leo, match { it.searchAllowed }) }
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun `no limit is offered last only with the plugin's unlimited autoplay`() {
        assertEquals(KidRulesViewModel.AUTOPLAY_OPTIONS, KidRulesViewModel.autoplayOptions(unlimited = false, current = 3))
        assertEquals(KidRulesViewModel.AUTOPLAY_OPTIONS + 0, KidRulesViewModel.autoplayOptions(unlimited = true, current = 3))
        assertEquals(0, KidRulesViewModel.autoplayOptions(unlimited = true, current = null).last())
    }

    @Test
    fun `no limit is sent as cap 0 with the plugin's unlimited autoplay`() =
        runTest(dispatcher) {
            assertEquals(
                mapOf("AutoplayCap" to JsonPrimitive(0)),
                changedKeys(RuleChange.Autoplay(KidProfile.UNLIMITED_AUTOPLAY_CAP), "$SITTING,unlimitedAutoplay"),
            )
            assertTrue(profile.autoplayEnabled)
            assertEquals(0, profile.autoplayCap)
        }

    @Test
    fun `without unlimited autoplay no limit is never sent`() =
        runTest(dispatcher) {
            val vm = viewModel(SITTING)
            assertFalse(vm.state.value.unlimitedAutoplay)
            vm.save(RuleChange.Autoplay(KidProfile.UNLIMITED_AUTOPLAY_CAP))
            coVerify(exactly = 0) { repository.save(any(), any()) }
            assertTrue(viewModel("$SITTING,unlimitedAutoplay").state.value.unlimitedAutoplay)
        }

    @Test
    fun `the switch shows another kid's rules, then refreshes them from the server`() =
        runTest(dispatcher) {
            coEvery { repository.refresh(server, lucy) } answers {
                lucyProfile = lucyProfile.copy(dailyAllowanceMinutes = 45)
                true
            }
            val vm = viewModel(kids = listOf(leo, lucy))
            assertEquals(listOf(RulesKid(leo.id, "Leo"), RulesKid(lucy.id, "Lucy", PekeAvatar.entries.first())), vm.state.value.kids)

            vm.select(lucy.id)

            assertEquals(lucy.id, vm.state.value.kidId)
            assertEquals("Lucy", vm.state.value.kidName)
            assertEquals(
                45,
                vm.state.value.profile
                    ?.dailyAllowanceMinutes,
            )
            coVerify { repository.refresh(server, lucy) }
        }

    @Test
    fun `a change on the kid switched to is saved for them, not for the kid watching`() =
        runTest(dispatcher) {
            coEvery { repository.refresh(server, lucy) } returns true
            coEvery { repository.save(lucy, any()) } returns SaveResult.SAVED
            val vm = viewModel(kids = listOf(leo, lucy))
            vm.select(lucy.id)

            vm.save(RuleChange.DailyTime(90))

            coVerify { repository.save(lucy, match { it.dailyAllowanceMinutes == 90 && it.revision == 5L && it.userId == lucy.rowId }) }
            coVerify(exactly = 0) { repository.save(leo, any()) }
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun `a change the grant ran out for is saved after the code for the kid it was made for`() =
        runTest(dispatcher) {
            coEvery { repository.refresh(server, leo) } returns true
            coEvery { repository.refresh(server, lucy) } returns true
            coEvery { repository.save(lucy, any()) } returnsMany listOf(SaveResult.NEEDS_GRANT, SaveResult.SAVED)
            val vm = viewModel(kids = listOf(leo, lucy))
            vm.select(lucy.id)
            vm.save(RuleChange.Search(true))
            assertEquals(RuleChange.Search(true), vm.state.value.pending)
            vm.select(leo.id)

            vm.retryPending()

            coVerify(exactly = 2) { repository.save(lucy, match { it.searchAllowed }) }
            coVerify(exactly = 0) { repository.save(leo, any()) }
        }

    @Test
    fun `with one kid on this TV there is nobody to switch to`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.select(lucy.id)
            assertEquals(leo.id, vm.state.value.kidId)
        }

    private companion object {
        const val SITTING = "profiles,parentLock,enforcement,sitting,allowance,liveTv,sessionLimit"
    }
}
