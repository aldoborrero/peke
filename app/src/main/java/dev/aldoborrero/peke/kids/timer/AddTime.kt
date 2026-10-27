package dev.aldoborrero.peke.kids.timer

import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.services.KeyValueService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.model.api.AccessSchedule
import org.jellyfin.sdk.model.api.DynamicDayOfWeek
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * The amounts a grown-up can add from the parent menu, in minutes: no wrap-around, 2 hours at most
 */
object AddTimeSteps {
    val MINUTES = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
    const val DEFAULT_MINUTES = 15

    /**
     * The step of [minutes], or the default's when it isn't one of them
     */
    fun indexOf(minutes: Int?): Int = MINUTES.indexOf(minutes).takeIf { it >= 0 } ?: MINUTES.indexOf(DEFAULT_MINUTES)

    /**
     * The step [delta] away from [index], held at the ends
     */
    fun step(
        index: Int,
        delta: Int,
    ): Int = (index + delta).coerceIn(0, MINUTES.lastIndex)
}

/**
 * The last amount a grown-up added on this TV, where the next Add time starts. Kept for this TV only.
 */
@Singleton
class AddTimeAmount
    @Inject
    constructor(
        private val keyValueService: KeyValueService,
    ) {
        /**
         * The stored amount, or the default when there is none, it isn't a step or it can't be read
         */
        suspend fun get(): Int =
            try {
                keyValueService.get<Int>(KEY).first()?.takeIf { it in AddTimeSteps.MINUTES } ?: AddTimeSteps.DEFAULT_MINUTES
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not read the add-time amount: %s", ex.javaClass.simpleName)
                AddTimeSteps.DEFAULT_MINUTES
            }

        suspend fun set(minutes: Int) = keyValueService.save(KEY, minutes)

        companion object {
            const val KEY = "kids_add_time_minutes"
        }
    }

/**
 * Jellyfin's allowed hours (a user's access schedule), in the server's local time, read with the TV's clock
 */
object AllowedHours {
    private fun applies(
        day: DynamicDayOfWeek,
        date: DayOfWeek,
    ): Boolean =
        when (day) {
            DynamicDayOfWeek.EVERYDAY -> true
            DynamicDayOfWeek.WEEKDAY -> date != DayOfWeek.SATURDAY && date != DayOfWeek.SUNDAY
            DynamicDayOfWeek.WEEKEND -> date == DayOfWeek.SATURDAY || date == DayOfWeek.SUNDAY
            DynamicDayOfWeek.SUNDAY -> date == DayOfWeek.SUNDAY
            DynamicDayOfWeek.MONDAY -> date == DayOfWeek.MONDAY
            DynamicDayOfWeek.TUESDAY -> date == DayOfWeek.TUESDAY
            DynamicDayOfWeek.WEDNESDAY -> date == DayOfWeek.WEDNESDAY
            DynamicDayOfWeek.THURSDAY -> date == DayOfWeek.THURSDAY
            DynamicDayOfWeek.FRIDAY -> date == DayOfWeek.FRIDAY
            DynamicDayOfWeek.SATURDAY -> date == DayOfWeek.SATURDAY
        }

    private fun minutesOf(hours: Double): Int = (hours * 60).roundToInt().coerceIn(0, DAY_MINUTES)

    /**
     * The allowed windows on [date] in minutes of the day, start to end, sorted and joined where they touch
     */
    fun windows(
        schedules: List<AccessSchedule>,
        date: LocalDate,
    ): List<IntRange> {
        val sorted =
            schedules
                .filter { applies(it.dayOfWeek, date.dayOfWeek) }
                .map { minutesOf(it.startHour)..minutesOf(it.endHour) }
                .filter { it.last > it.first }
                .sortedBy { it.first }
        return sorted.fold(mutableListOf()) { joined, window ->
            val last = joined.lastOrNull()
            if (last != null && window.first <= last.last) {
                joined[joined.lastIndex] = last.first..maxOf(last.last, window.last)
            } else {
                joined += window
            }
            joined
        }
    }

    /**
     * When the allowed window that holds [now] ends today, or null with no schedule, outside every window or at
     * midnight
     */
    fun endOfWindow(
        schedules: List<AccessSchedule>,
        now: LocalDateTime,
    ): LocalDateTime? {
        val minute = now.hour * 60 + now.minute
        val window = windows(schedules, now.toLocalDate()).firstOrNull { minute in it } ?: return null
        if (window.last >= DAY_MINUTES) return null
        return now.toLocalDate().atTime(LocalTime.of(window.last / 60, window.last % 60))
    }

    private const val DAY_MINUTES = 24 * 60
}

/**
 * What adding an amount would mean, in the grown-up's terms, forecast from the kid's last status. The server still
 * decides: this is only what the line under the stepper says.
 *
 * @param until when the kid could watch until
 * @param hadLeft the time the kid had left before, if any
 * @param bedtimeAt when allowed hours end, if that comes before [until]: the kid stops then
 * @param breakAt when the sitting ends, if that comes before [until]: a break starts then
 */
data class AddTimePreview(
    val until: LocalDateTime,
    val hadLeft: Duration?,
    val bedtimeAt: LocalDateTime?,
    val breakAt: LocalDateTime?,
) {
    companion object {
        fun of(
            limits: KidsLimits,
            minutes: Int,
            now: LocalDateTime,
            schedules: List<AccessSchedule>,
        ): AddTimePreview {
            val left = if (limits.denial == KidsDenial.TIME_UP) Duration.ZERO else limits.allowanceRemaining ?: Duration.ZERO
            val until = now + left + minutes.minutes
            val bedtime = AllowedHours.endOfWindow(schedules, now)?.takeIf { it < until }
            val sittingEnd =
                limits.sittingRemaining
                    ?.takeIf { limits.denial == null }
                    ?.let { now + it }
                    ?.takeIf { it < until && (bedtime == null || it < bedtime) }
            return AddTimePreview(
                until = until,
                hadLeft = left.takeIf { it >= 1.minutes },
                bedtimeAt = bedtime,
                breakAt = sittingEnd,
            )
        }

        private operator fun LocalDateTime.plus(duration: Duration): LocalDateTime = plus(duration.toJavaDuration())
    }
}

/**
 * Whether a grown-up can add time now, and if not, why: the menu says so in Add time's place
 */
sealed interface AddTimeAvailability {
    data object Available : AddTimeAvailability

    /**
     * On a break after the sitting, which ends at [endsAt] if known; with [canEnd] the plugin can end it now
     */
    data class OnBreak(
        val endsAt: LocalDateTime?,
        val canEnd: Boolean,
    ) : AddTimeAvailability

    /**
     * Outside allowed hours, which are Jellyfin's
     */
    data object Bedtime : AddTimeAvailability

    /**
     * The kid has no daily limit
     */
    data object Unlimited : AddTimeAvailability

    /**
     * The server or its plugin can't be reached, or the kid's status isn't known
     */
    data object Offline : AddTimeAvailability

    companion object {
        /**
         * @param pluginAvailable the server has a usable Kids plugin
         * @param reachable whether the last status request had an answer, null before the first
         * @param canEndBreak the plugin can end a break
         */
        fun of(
            pluginAvailable: Boolean,
            status: KidsStatus?,
            limits: KidsLimits,
            reachable: Boolean?,
            canEndBreak: Boolean,
            now: LocalDateTime,
        ): AddTimeAvailability =
            when {
                !pluginAvailable || status == null || reachable == false -> {
                    Offline
                }

                limits.denial == KidsDenial.SITTING_OVER -> {
                    OnBreak(
                        limits.breakRemaining?.let { now.plus(it.toJavaDuration()) },
                        canEndBreak,
                    )
                }

                limits.denial == KidsDenial.BEDTIME -> {
                    Bedtime
                }

                status.unlimited -> {
                    Unlimited
                }

                else -> {
                    Available
                }
            }
    }
}
