package dev.aldoborrero.peke.ui.main

import androidx.lifecycle.viewModelScope
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.data.model.ServerUserConfig
import dev.aldoborrero.peke.services.HomePageResolvedSettings
import dev.aldoborrero.peke.services.HomeRowConfigDisplay
import dev.aldoborrero.peke.services.HomeSettingsService
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.ui.util.ResStringProvider
import dev.aldoborrero.peke.util.HomeRowLoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID

/**
 * The home's live row timer (spec 034): it follows the row, stops while the home is left, the return reads the row,
 * a failed read keeps the row and retries, and a gap keeps the row while it airs, then hides it until the next start
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class HomeViewModelLiveRowTest {
    private val dispatcher = StandardTestDispatcher()
    private val base = LocalDateTime.of(2026, 11, 26, 18, 0, 0)
    private val user = ServerUserConfig(id = UUID.randomUUID())
    private val title = ResStringProvider(R.string.watch_live)
    private val live = HomeRowConfig.TvPrograms()
    private val settings =
        HomePageResolvedSettings(
            user.id,
            listOf(
                HomeRowConfigDisplay(0, title, HomeRowConfig.NextUp()),
                HomeRowConfigDisplay(1, title, live),
            ),
            libraries = emptyList(),
        )

    /** What the server answers for the live row on each read, in turn: programmes, or an exception to throw */
    private val answers = ArrayDeque<Any>()

    /** When the live row was read, in seconds from [base] */
    private val reads = mutableListOf<Long>()
    private var nextStart: LocalDateTime? = null
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() = PekeDispatchers.configure(dispatcher)

    @After
    fun tearDown() = PekeDispatchers.reset()

    /** Runs [body] on virtual time, and ends the view model in it: its timer would otherwise keep the clock running */
    private fun liveTest(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    private fun programme(
        start: Long,
        end: Long,
    ) = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.PROGRAM,
        startDate = base.plusSeconds(start),
        endDate = base.plusSeconds(end),
    )

    private fun now() = dispatcher.scheduler.currentTime / 1000

    private fun liveRow(programmes: List<BaseItemDto>) =
        HomeRowLoadingState.Success(title, programmes.map { BaseItem(it, false) }, rowType = live)

    private fun TestScope.start(first: List<BaseItemDto>) {
        answers.addFirst(first)
        val serverRepository = mockk<ServerRepository>(relaxed = true)
        every { serverRepository.currentUserDtoFlow } returns MutableStateFlow(user)
        every { serverRepository.currentUserDto } returns user
        val homeSettingsService = mockk<HomeSettingsService>(relaxed = true)
        every { homeSettingsService.currentSettings } returns MutableStateFlow(settings)
        coEvery { homeSettingsService.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            if (firstArg<HomeRowConfig>() is HomeRowConfig.TvPrograms) {
                reads += now()
                when (val answer = answers.removeFirstOrNull() ?: emptyList<BaseItemDto>()) {
                    is Exception -> {
                        throw answer
                    }

                    is HomeRowLoadingState -> {
                        answer
                    }

                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        liveRow(answer as List<BaseItemDto>)
                    }
                }
            } else {
                HomeRowLoadingState.Success(title, emptyList(), rowType = HomeRowConfig.NextUp())
            }
        }
        coEvery { homeSettingsService.nextLiveStart(any(), any()) } answers { nextStart }
        val userPreferencesService = mockk<UserPreferencesService>()
        coEvery { userPreferencesService.getCurrent() } returns mockk(relaxed = true)
        viewModel =
            HomeViewModel(
                context = mockk(relaxed = true),
                navigationManager = mockk(relaxed = true),
                serverRepository = serverRepository,
                serverReportService = mockk(relaxed = true),
                navDrawerService = mockk(relaxed = true),
                homeSettingsService = homeSettingsService,
                favoriteWatchManager = mockk(relaxed = true),
                backdropService = mockk(relaxed = true),
                userPreferencesService = userPreferencesService,
                mediaManagementService = mockk(relaxed = true),
                latestNextUpService = mockk(relaxed = true),
                datePlayedService = mockk(relaxed = true),
            )
        viewModel.clock = { base.plusSeconds(now()) }
        runCurrent()
    }

    private fun TestScope.at(second: Long) {
        advanceTimeBy(second * 1000 - dispatcher.scheduler.currentTime)
        runCurrent()
    }

    private fun shown() = viewModel.state.value.homeRows[1]

    @Test
    fun `the timer reads the row when its programme ends, and follows a new row`() =
        liveTest {
            val a = programme(-60, 30)
            start(listOf(a))
            assertEquals(liveRow(listOf(a)), shown())

            // A return at 5 s brings a programme ending sooner: the timer now waits for that one
            at(5)
            viewModel.onLeft()
            val b = programme(-60, 10)
            answers.addLast(listOf(b))
            viewModel.refreshWatching()
            runCurrent()
            assertEquals(liveRow(listOf(b)), shown())

            val c = programme(10, 600)
            answers.addLast(listOf(c))
            at(40)
            assertEquals(listOf(0L, 5L, 12L), reads)
            assertEquals(liveRow(listOf(c)), shown())
        }

    @Test
    fun `the timer stops while the home is left, and the return reads the row`() =
        liveTest {
            start(listOf(programme(-60, 30)))
            at(5)
            viewModel.onLeft()
            at(200)
            assertEquals(listOf(0L), reads)

            val next = programme(30, 1800)
            answers.addLast(listOf(next))
            viewModel.refreshWatching()
            at(201)
            assertEquals(listOf(0L, 200L), reads)
            assertEquals(liveRow(listOf(next)), shown())
        }

    @Test
    fun `a failed read keeps the row on screen and retries after a minute`() =
        liveTest {
            val a = programme(-60, 30)
            start(listOf(a))
            answers.addLast(IOException("Unable to resolve host"))
            at(33)
            assertEquals(liveRow(listOf(a)), shown())

            answers.addLast(HomeRowLoadingState.Error(title, "server error"))
            at(93)
            assertEquals(liveRow(listOf(a)), shown())

            val b = programme(30, 1800)
            answers.addLast(listOf(b))
            at(153)
            assertEquals(listOf(0L, 32L, 92L, 152L), reads)
            assertEquals(liveRow(listOf(b)), shown())
        }

    @Test
    fun `a gap keeps the row while a programme on it airs, then hides it until the next start`() =
        liveTest {
            val ending = programme(-60, 30)
            val later = programme(-60, 300)
            start(listOf(ending, later))

            answers.addLast(emptyList<BaseItemDto>())
            at(33)
            assertEquals(liveRow(listOf(ending, later)), shown())

            nextStart = base.plusHours(1)
            answers.addLast(emptyList<BaseItemDto>())
            at(303)
            assertEquals(liveRow(emptyList()), shown())

            val morning = programme(3600, 5400)
            answers.addLast(listOf(morning))
            at(3603)
            assertEquals(listOf(0L, 32L, 302L, 3602L), reads)
            assertEquals(liveRow(listOf(morning)), shown())
        }
}
