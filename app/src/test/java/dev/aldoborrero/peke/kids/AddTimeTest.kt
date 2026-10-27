package dev.aldoborrero.peke.kids

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.timer.AddTimeAmount
import dev.aldoborrero.peke.kids.timer.AddTimeAvailability
import dev.aldoborrero.peke.kids.timer.AddTimePreview
import dev.aldoborrero.peke.kids.timer.AddTimeSteps
import dev.aldoborrero.peke.kids.timer.AllowedHours
import dev.aldoborrero.peke.kids.timer.KidsLimits
import dev.aldoborrero.peke.services.KeyValueService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.AccessSchedule
import org.jellyfin.sdk.model.api.DynamicDayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * The Add time stepper's rules: its steps, the amount kept for this TV, the forecast line and why time can't be added
 * (spec 006 US2)
 */
class AddTimeTest {
    // A Monday at 18:00
    private val now = LocalDateTime.of(2026, 10, 26, 18, 0)

    private fun schedule(
        day: DynamicDayOfWeek,
        start: Double,
        end: Double,
    ) = AccessSchedule(0, UUID.randomUUID(), day, start, end)

    @Test
    fun `steps stop at the ends and unknown amounts open on 15`() {
        assertEquals(listOf(5, 10, 15, 20, 30, 45, 60, 90, 120), AddTimeSteps.MINUTES)
        assertEquals(0, AddTimeSteps.step(0, -1))
        assertEquals(8, AddTimeSteps.step(8, 1))
        assertEquals(3, AddTimeSteps.step(2, 1))
        assertEquals(2, AddTimeSteps.indexOf(null))
        assertEquals(2, AddTimeSteps.indexOf(25))
        assertEquals(6, AddTimeSteps.indexOf(60))
    }

    /**
     * Preferences kept in memory
     */
    private class MemoryPreferences : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also {
                state.value =
                    it
            }
    }

    @Test
    fun `the amount kept for this TV`() =
        runTest {
            val store = MemoryPreferences()
            val amount = AddTimeAmount(KeyValueService(store))
            assertEquals(15, amount.get())
            amount.set(45)
            assertEquals(45, amount.get())
            // Not a step, such as a value from another version, or unreadable
            store.state.value = preferencesOf(stringPreferencesKey(AddTimeAmount.KEY) to "25")
            assertEquals(15, amount.get())
            store.state.value = preferencesOf(stringPreferencesKey(AddTimeAmount.KEY) to "nonsense")
            assertEquals(15, amount.get())
        }

    @Test
    fun `until counts the time left, and none after time's up`() {
        val left = KidsLimits(allowanceRemaining = 10.minutes)
        val preview = AddTimePreview.of(left, 15, now, emptyList())
        assertEquals(now.plusMinutes(25), preview.until)
        assertEquals(10.minutes, preview.hadLeft)
        assertNull(preview.bedtimeAt)
        assertNull(preview.breakAt)

        val timesUp = KidsLimits(denial = KidsDenial.TIME_UP, allowanceRemaining = 3.minutes)
        val after = AddTimePreview.of(timesUp, 15, now, emptyList())
        assertEquals(now.plusMinutes(15), after.until)
        assertNull(after.hadLeft)
    }

    @Test
    fun `under a minute left isn't worth saying`() {
        val preview = AddTimePreview.of(KidsLimits(allowanceRemaining = 30.minutes / 60), 15, now, emptyList())
        assertNull(preview.hadLeft)
    }

    @Test
    fun `bedtime warns only when the time passes the end of today's allowed hours`() {
        val schedules = listOf(schedule(DynamicDayOfWeek.WEEKDAY, 7.0, 18.5))
        assertNull(AddTimePreview.of(KidsLimits(), 20, now, schedules).bedtimeAt)
        assertEquals(now.withMinute(30), AddTimePreview.of(KidsLimits(), 45, now, schedules).bedtimeAt)
        // Weekend hours don't count on a Monday
        assertNull(AddTimePreview.of(KidsLimits(), 45, now, listOf(schedule(DynamicDayOfWeek.WEEKEND, 7.0, 18.5))).bedtimeAt)
        // Windows that touch are one
        val joined = listOf(schedule(DynamicDayOfWeek.MONDAY, 17.0, 18.25), schedule(DynamicDayOfWeek.EVERYDAY, 18.25, 20.0))
        assertEquals(now.withHour(20), AddTimePreview.of(KidsLimits(), 120 + 30, now, joined).bedtimeAt)
        // Until midnight has no bedtime to warn of
        assertNull(AllowedHours.endOfWindow(listOf(schedule(DynamicDayOfWeek.EVERYDAY, 0.0, 24.0)), now))
    }

    @Test
    fun `a break comes first when the sitting ends before the added time`() {
        val limits = KidsLimits(allowanceRemaining = 10.minutes, sittingRemaining = 20.minutes)
        assertEquals(now.plusMinutes(20), AddTimePreview.of(limits, 30, now, emptyList()).breakAt)
        assertNull(AddTimePreview.of(limits, 5, now, emptyList()).breakAt)
    }

    @Test
    fun `why time can't be added, in order`() {
        val status = KidsStatus(allowanceSeconds = 3600)

        fun of(
            limits: KidsLimits,
            s: KidsStatus? = status,
            reachable: Boolean? = true,
            plugin: Boolean = true,
        ) = AddTimeAvailability.of(plugin, s, limits, reachable, canEndBreak = true, now = now)

        assertEquals(AddTimeAvailability.Available, of(KidsLimits()))
        assertEquals(AddTimeAvailability.Available, of(KidsLimits(denial = KidsDenial.TIME_UP)))
        assertEquals(AddTimeAvailability.Available, of(KidsLimits(), reachable = null))
        assertEquals(AddTimeAvailability.Offline, of(KidsLimits(), reachable = false))
        assertEquals(AddTimeAvailability.Offline, of(KidsLimits(), s = null))
        assertEquals(AddTimeAvailability.Offline, of(KidsLimits(), plugin = false))
        assertEquals(
            AddTimeAvailability.OnBreak(now.plusMinutes(12), canEnd = true),
            of(KidsLimits(denial = KidsDenial.SITTING_OVER, breakRemaining = 12.minutes)),
        )
        assertEquals(AddTimeAvailability.Bedtime, of(KidsLimits(denial = KidsDenial.BEDTIME)))
        assertEquals(AddTimeAvailability.Unlimited, of(KidsLimits(), s = KidsStatus()))
        // A kid without a daily limit can still be on a break
        assertEquals(
            AddTimeAvailability.OnBreak(null, canEnd = true),
            of(KidsLimits(denial = KidsDenial.SITTING_OVER), s = KidsStatus()),
        )
    }
}
