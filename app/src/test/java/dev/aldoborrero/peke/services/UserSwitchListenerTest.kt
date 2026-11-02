package dev.aldoborrero.peke.services

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.test.item
import dev.aldoborrero.peke.test.user
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.configure
import dev.aldoborrero.peke.util.reset
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class UserSwitchListenerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val serverId = UUID.randomUUID()
    private val currentUser = MutableStateFlow<JellyfinUser?>(user(serverId))
    private val activity = mockk<AppCompatActivity>(relaxed = true)
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(mockk<LifecycleOwner>(relaxed = true))
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val homeSettingsService = mockk<HomeSettingsService>(relaxed = true)
    private val profilePolicy = mockk<ProfilePolicy>(relaxed = true)
    private val itemHandOver = ItemHandOver()

    @Before
    fun setUp() {
        PekeDispatchers.configure(testDispatcher)
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.setApplicationLocales(any()) } returns Unit
        every { activity.lifecycle } returns lifecycleRegistry
        every { serverRepository.currentUserFlow } returns currentUser
        every { serverRepository.currentUser } answers { currentUser.value }
        every { homeSettingsService.currentSettings } returns MutableStateFlow(HomePageResolvedSettings.EMPTY)
        every { profilePolicy.homeChanges } returns emptyFlow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @After
    fun tearDown() {
        unmockkStatic(AppCompatDelegate::class)
        PekeDispatchers.reset()
    }

    private fun createListener() = UserSwitchListener(activity, serverRepository, homeSettingsService, profilePolicy, itemHandOver)

    @Test
    fun `another user doesn't get the item handed over to the one before`() =
        runTest(testDispatcher) {
            createListener()
            advanceUntilIdle()
            val series = BaseItem(item(BaseItemKind.SERIES), false)
            itemHandOver.put(series)

            currentUser.value = user(serverId)
            advanceUntilIdle()

            assertNull(itemHandOver.take(series.id))
        }

    @Test
    fun `the same user keeps the item handed over`() =
        runTest(testDispatcher) {
            createListener()
            advanceUntilIdle()
            val series = BaseItem(item(BaseItemKind.SERIES), false)
            itemHandOver.put(series)

            currentUser.value = currentUser.value?.copy()
            advanceUntilIdle()

            assertEquals(series, itemHandOver.take(series.id))
        }
}
