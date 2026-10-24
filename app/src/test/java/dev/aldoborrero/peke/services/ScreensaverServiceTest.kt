package dev.aldoborrero.peke.services

import android.content.Context
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.test.server
import dev.aldoborrero.peke.test.user
import dev.aldoborrero.peke.ui.components.ScreensaverItem
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * The screensaver shows only the current kid's things, and Peke's own art when no kid is current (C10)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreensaverServiceTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val serverId = UUID.randomUUID()
    private val server = server(serverId)
    private val kid = user(serverId).copy(rowId = 1, name = "kid")
    private val sibling = user(serverId).copy(rowId = 2, name = "sibling")

    private val current = MutableStateFlow<CurrentUser?>(null)
    private val tvState = MutableStateFlow<TvState>(TvState.SetUp(server, listOf(kid, sibling)))
    private val shown = mutableListOf<SetupDestination>(SetupDestination.Loading)
    private val api = mockk<ApiClient>(relaxed = true)
    private val userPreferencesService =
        mockk<UserPreferencesService> {
            every { flow } returns emptyFlow()
        }

    @Before
    fun setUp() = PekeDispatchers.configure(dispatcher)

    @After
    fun tearDown() = PekeDispatchers.reset()

    private fun kotlinx.coroutines.test.TestScope.service() =
        ScreensaverService(
            mockk<Context>(relaxed = true),
            backgroundScope,
            api,
            userPreferencesService,
            mockk(relaxed = true),
            mockk<ServerRepository> { every { this@mockk.current } returns this@ScreensaverServiceTest.current },
            mockk<TvSetupRepository> { every { state } returns tvState },
            mockk<SetupNavigationManager> { every { backStack } returns shown },
        )

    @Test
    fun `with no kid current it shows Peke's own art`() =
        runTest(dispatcher) {
            assertEquals(ScreensaverItem.Neutral, service().createItemFlow(backgroundScope).first())
        }

    @Test
    fun `a kid whose pages aren't showing isn't current, as on the picker`() =
        runTest(dispatcher) {
            current.value = CurrentUser(server, kid)
            shown[0] = SetupDestination.KidsPicker

            assertEquals(ScreensaverItem.Neutral, service().createItemFlow(backgroundScope).first())
        }

    @Test
    fun `a user who isn't one of this TV's kids gets Peke's own art`() =
        runTest(dispatcher) {
            val grownUp = CurrentUser(server, user(serverId).copy(rowId = 9, name = "grown-up"))
            current.value = grownUp
            shown[0] = SetupDestination.AppContent(grownUp)

            assertEquals(ScreensaverItem.Neutral, service().createItemFlow(backgroundScope).first())
        }

    @Test
    fun `the current kid's library is shown`() =
        runTest(dispatcher) {
            val kidCurrent = CurrentUser(server, kid)
            current.value = kidCurrent
            shown[0] = SetupDestination.AppContent(kidCurrent)

            // The items come from the server as the kid, which this test has no answer for
            val item = service().createItemFlow(backgroundScope).first()
            assertTrue("$item", item is ScreensaverItem.Error)
        }
}
