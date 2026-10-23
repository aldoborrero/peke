package dev.aldoborrero.peke.services

import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.work.WorkManager
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestNextUpSchedulerServiceTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = StandardTestDispatcher()
    private val currentUser = MutableStateFlow<CurrentUser?>(null)
    private val mockActivity = mockk<AppCompatActivity>(relaxed = true)
    private val mockServerRepository = mockk<ServerRepository>(relaxed = true)
    private val mockWorkManager = mockk<WorkManager>(relaxed = true)
    private val lifecycleRegistry = LifecycleRegistry(mockk<LifecycleOwner>(relaxed = true))

    @Before
    fun setUp() {
        PekeDispatchers.configure(testDispatcher)
        every { mockActivity.lifecycle } returns lifecycleRegistry
        every { mockServerRepository.current } returns currentUser
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @After
    fun tearDown() = PekeDispatchers.reset()

    private fun createService() =
        LatestNextUpSchedulerService(
            context = mockActivity,
            serverRepository = mockServerRepository,
            workManager = mockWorkManager,
        ).also {
            it.dispatcher = testDispatcher
        }

    @Test
    fun cancels_latestNextUp_work_when_user_null() =
        runTest(testDispatcher) {
            createService()

            currentUser.value = null
            advanceUntilIdle()

            verify { mockWorkManager.cancelUniqueWork(LatestNextUpWorker.WORK_NAME) }
            verify(exactly = 0) { mockWorkManager.cancelUniqueWork(SuggestionsWorker.WORK_NAME) }
        }
}
