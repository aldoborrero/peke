package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.timer.KidsLimits
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Kids plugin's own messages (`DisplayMessage`), sent to every session of a kid when server enforcement is on:
 * warnings before the allowance, the sitting or the allowed hours end, then a message when the kid is denied. Wholphin
 * shows its own warnings and screens for the same things, so a server message that repeats what Wholphin already
 * knows is not shown on top of them. The server's `Playstate Stop` that follows is still obeyed.
 */
object KidsServerMessages {
    // The plugin's headers (English in plugin 0.0.5.0)
    private const val SCREEN_TIME = "Screen time"
    private const val BREAK_TIME = "Break time"
    private const val BEDTIME = "Bedtime"
    private const val TIMES_UP = "Time's up"
    private const val TIME_FOR_A_BREAK = "Time for a break"

    private val HEADERS = setOf(SCREEN_TIME, BREAK_TIME, BEDTIME, TIMES_UP, TIME_FOR_A_BREAK)

    /**
     * How close to a warning point the time left must be for Wholphin's own warning to count as known
     */
    private val MARGIN = 60.seconds

    /**
     * Whether a message with [header] is one of the Kids plugin's
     */
    fun isKids(header: String?): Boolean = header != null && HEADERS.any { it.equals(header.trim(), ignoreCase = true) }

    /**
     * Whether to hide the Kids plugin message with [header], because Wholphin already shows the same, from [limits]
     * read just now. Messages that aren't the plugin's are never hidden.
     *
     * @param warnAt the server's warning points
     */
    fun shouldHide(
        header: String?,
        limits: KidsLimits,
        warnAt: List<Duration>,
    ): Boolean {
        if (!isKids(header)) return false
        // A time's up, bedtime or sitting over screen is up (or about to be): any kids message repeats it
        if (limits.expired) return true
        val window = (warnAt.maxOrNull() ?: Duration.ZERO) + MARGIN
        return when (header!!.trim().lowercase()) {
            SCREEN_TIME.lowercase() -> limits.allowanceRemaining?.let { it <= window } == true

            BREAK_TIME.lowercase() -> limits.sittingRemaining?.let { it <= window } == true

            // Wholphin has no bedtime warning of its own, so the server's is the only one
            BEDTIME.lowercase() -> limits.denial == KidsDenial.BEDTIME

            // A denial Wholphin doesn't know of yet: the next status shows its screen
            else -> false
        }
    }
}
