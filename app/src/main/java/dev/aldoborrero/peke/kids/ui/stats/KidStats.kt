package dev.aldoborrero.peke.kids.ui.stats

import dev.aldoborrero.peke.kids.server.KidsStats
import dev.aldoborrero.peke.kids.server.KidsStatsDay
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/**
 * A kid's figures for today, in minutes, from the server's status
 *
 * @param leftMinutes what is left today, or null when the kid has no daily limit
 * @param breakLeftMinutes what is left of the break after a sitting, while the kid is on one
 */
data class KidToday(
    val usedMinutes: Int,
    val leftMinutes: Int?,
    val bonusMinutes: Int,
    val breakLeftMinutes: Int?,
    val timeUp: Boolean,
) {
    companion object {
        fun from(status: KidsStatus): KidToday {
            val allowance = status.allowanceSeconds
            return KidToday(
                usedMinutes = (status.usedSeconds / 60).toInt(),
                leftMinutes =
                    allowance?.let {
                        val left = status.remainingSeconds ?: (it + status.bonusSeconds - status.usedSeconds)
                        minutesUp(left.coerceAtLeast(0))
                    },
                bonusMinutes = (status.bonusSeconds / 60).toInt(),
                breakLeftMinutes = status.breakRemainingSeconds?.takeIf { it > 0 }?.let(::minutesUp),
                timeUp = status.timeUp,
            )
        }

        /**
         * Rounded up, so a break or time with seconds left never reads 0
         */
        private fun minutesUp(seconds: Long) = ((seconds + 59) / 60).toInt()
    }
}

/**
 * A day's bar on a kid's card (spec 033 D8)
 *
 * @param height the bar's height, 0 to 1 of the chart
 * @param allowance where that day's allowance mark sits, 0 to 1, or null for an unlimited day
 * @param over watched more than the allowance with that day's extra time
 */
data class StatsBar(
    val day: LocalDate?,
    val usedMinutes: Int,
    val allowanceMinutes: Int? = null,
    val bonusMinutes: Int = 0,
    val height: Float,
    val allowance: Float?,
    val today: Boolean,
    val over: Boolean,
) {
    val dayOfWeek: DayOfWeek? get() = day?.dayOfWeek

    /**
     * How far past the allowance with extra time, when [over]
     */
    val overMinutes: Int get() = (usedMinutes - (allowanceMinutes ?: usedMinutes) - bonusMinutes).coerceAtLeast(0)

    companion object {
        /**
         * The bars of [days], oldest first and today last as the plugin sends them, scaled to the largest of the
         * minutes watched and the allowances so the allowance marks always fit
         */
        fun of(days: List<KidsStatsDay>): List<StatsBar> {
            val top = days.maxOfOrNull { maxOf(it.usedMinutes, it.allowanceMinutes ?: 0) }?.takeIf { it > 0 } ?: 1
            return days.mapIndexed { index, day ->
                val allowance = day.allowanceMinutes
                StatsBar(
                    day = parseDay(day.day),
                    usedMinutes = day.usedMinutes,
                    allowanceMinutes = allowance,
                    bonusMinutes = day.bonusMinutes,
                    height = day.usedMinutes.coerceAtLeast(0).toFloat() / top,
                    allowance = allowance?.let { it.toFloat() / top },
                    today = index == days.lastIndex,
                    over = allowance != null && day.usedMinutes > allowance + day.bonusMinutes,
                )
            }
        }

        private fun parseDay(day: String): LocalDate? =
            try {
                LocalDate.parse(day.take(10))
            } catch (_: Exception) {
                null
            }
    }
}

/**
 * A kid's card on Stats
 *
 * @param watching the kid whose session is open on this TV
 * @param today null when neither the server nor the cache has a status
 * @param stale [today] is the last one cached, not the server's answer just now
 * @param week the plugin's stats, null without them
 */
data class StatsKid(
    val id: UUID,
    val name: String?,
    val avatar: PekeAvatar? = null,
    val watching: Boolean = false,
    val today: KidToday? = null,
    val stale: Boolean = false,
    val week: KidsStats? = null,
) {
    /**
     * The week's bars, worked out once with the card
     */
    val bars: List<StatsBar> = week?.days?.let(StatsBar::of).orEmpty()
}
