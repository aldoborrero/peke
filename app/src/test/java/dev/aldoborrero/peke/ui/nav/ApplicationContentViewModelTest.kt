package dev.aldoborrero.peke.ui.nav

import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.data.KidProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationContentViewModelTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "server", url = "http://localhost", version = null)
    private val user = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Manuel", serverId = server.id, accessToken = "k")
    private val profile = CompletableDeferred<KidProfile?>()
    private val kidsModeService =
        mockk<KidsModeService> {
            every { lockEngaged } returns MutableStateFlow(true)
            coEvery { refreshedProfileFor(server, user) } coAnswers { profile.await() }
        }
    private val viewModel = ApplicationContentViewModel(mockk(), kidsModeService, mockk(relaxed = true))

    @Test
    fun `no bounce while the user's kid status is still being read`() =
        runTest(UnconfinedTestDispatcher()) {
            launch { viewModel.checkNotAKid(server, user) }
            assertNull(viewModel.notAKid.value)

            // The server says Manuel is a kid: the kids state takes over, never the user list
            profile.complete(KidProfile(userId = user.rowId))
            advanceUntilIdle()
            assertNull(viewModel.notAKid.value)
        }

    @Test
    fun `a user the server confirms isn't a kid is a grown-up`() =
        runTest(UnconfinedTestDispatcher()) {
            profile.complete(null)
            viewModel.checkNotAKid(server, user)
            assertEquals(user.rowId, viewModel.notAKid.value)
        }
}
