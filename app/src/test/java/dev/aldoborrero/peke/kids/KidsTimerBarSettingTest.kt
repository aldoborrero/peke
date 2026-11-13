package dev.aldoborrero.peke.kids

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.timer.KidsTimerBarSetting
import dev.aldoborrero.peke.kids.timer.KidsTimerLabelPlacement
import dev.aldoborrero.peke.services.KeyValueService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The timer bar's position keeps its stored key and values; where its time sits is a second setting (spec 022 D4)
 */
class KidsTimerBarSettingTest {
    private class MemoryPreferences : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val store = MemoryPreferences()
    private val setting = KidsTimerBarSetting(KeyValueService(store))

    @Test
    fun `a new TV has the bar at the top and its time in the middle, as before`() =
        runTest {
            assertEquals(KidsTimerBarPosition.TOP, setting.position.first())
            assertEquals(KidsTimerLabelPlacement.CENTER, setting.placement.first())
        }

    @Test
    fun `a position stored before spec 022 still reads the same, with the time in the middle`() =
        runTest {
            // As the earlier versions stored it: the enum's name as JSON, under kids_timer_bar
            mapOf(
                "\"TOP\"" to KidsTimerBarPosition.TOP,
                "\"BOTTOM\"" to KidsTimerBarPosition.BOTTOM,
                "\"HIDDEN\"" to KidsTimerBarPosition.HIDDEN,
            ).forEach { (stored, position) ->
                store.state.value = preferencesOf(stringPreferencesKey("kids_timer_bar") to stored)
                assertEquals(position, setting.position.first())
                assertEquals(KidsTimerLabelPlacement.CENTER, setting.placement.first())
            }
        }

    @Test
    fun `each placement is kept apart from the position`() =
        runTest {
            setting.set(KidsTimerBarPosition.BOTTOM)
            KidsTimerLabelPlacement.entries.forEach {
                setting.setPlacement(it)
                assertEquals(it, setting.placement.first())
                assertEquals(KidsTimerBarPosition.BOTTOM, setting.position.first())
            }
            assertEquals("\"BOTTOM\"", store.state.value[stringPreferencesKey("kids_timer_bar")])
            assertEquals("\"END\"", store.state.value[stringPreferencesKey("kids_timer_label")])
        }
}
