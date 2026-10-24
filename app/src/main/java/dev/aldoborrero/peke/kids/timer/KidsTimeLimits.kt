package dev.aldoborrero.peke.kids.timer

import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The time limits that apply to the current kid, all from the server's status: the daily allowance, the sitting
 * (counted by the server across the kid's devices) and the break after it, and why the kid may not watch now
 *
 * The times count down smoothly between polls, from the last status, but only a status or a refused playback denies.
 *
 * @param remaining time left of whichever limit ends first, null when no limit is counting down or the kid is denied
 * @param total the full length of that limit, for the remaining-time bar
 * @param denial why the kid may not watch now, which shows the time's up, bedtime or sitting over screen
 * @param breakRemaining the break left before the kid may watch again, on the sitting over screen
 */
data class KidsLimits(
    val remaining: Duration? = null,
    val total: Duration? = null,
    val denial: KidsDenial? = null,
    val breakRemaining: Duration? = null,
    val allowanceRemaining: Duration? = null,
    val sittingRemaining: Duration? = null,
) {
    val expired: Boolean get() = denial != null

    companion object {
        /**
         * @param refusal a refused playback newer than [status]
         * @param played playing time on this device since [status] was received
         * @param elapsed time since [status] was received
         */
        fun of(
            status: KidsStatus?,
            refusal: KidsDenial? = null,
            played: Duration = Duration.ZERO,
            elapsed: Duration = Duration.ZERO,
        ): KidsLimits {
            val denial = refusal ?: status?.denial
            val allowance =
                status?.remainingSeconds?.takeIf { !status.timeUp }?.let { remaining ->
                    val total = (status.allowanceSeconds ?: 0) + status.bonusSeconds
                    countdown(remaining, total, played)
                }
            val sitting =
                status?.sittingRemainingSeconds?.let { remaining ->
                    countdown(remaining, status.sittingLimitSeconds ?: 0, played)
                }
            val first = listOfNotNull(allowance, sitting).minByOrNull { it.first }?.takeIf { denial == null }
            val breakRemaining =
                status?.breakRemainingSeconds?.takeIf { denial == KidsDenial.SITTING_OVER }?.let {
                    (it.seconds - elapsed).coerceAtLeast(Duration.ZERO)
                }
            return KidsLimits(
                remaining = first?.first,
                total = first?.second,
                denial = denial,
                breakRemaining = breakRemaining,
                allowanceRemaining = allowance?.first,
                sittingRemaining = sitting?.first,
            )
        }

        private fun countdown(
            remainingSeconds: Long,
            totalSeconds: Long,
            played: Duration,
        ): Pair<Duration, Duration> {
            val remaining = remainingSeconds.seconds
            return (remaining - played).coerceAtLeast(Duration.ZERO) to totalSeconds.coerceAtLeast(remainingSeconds).seconds
        }
    }
}

/**
 * Playing time on this device since the last status, to count the allowance and the sitting down between polls
 */
class KidsCountdown(
    private val clock: MonotonicClock,
) {
    private var since = clock.elapsedMillis()
    private var playedMs = 0L
    private var playingSince: Long? = null

    /**
     * Count from a status received at [receivedAt], in [MonotonicClock] millis. Counting from the same status again
     * changes nothing.
     */
    @Synchronized
    fun from(receivedAt: Long) {
        if (receivedAt == since) return
        since = receivedAt
        playedMs = 0
        playingSince?.let { playingSince = maxOf(it, receivedAt) }
    }

    @Synchronized
    fun setPlaying(playing: Boolean) {
        val now = clock.elapsedMillis()
        val start = playingSince
        if (playing && start == null) {
            playingSince = now
        } else if (!playing && start != null) {
            playedMs += now - start
            playingSince = null
        }
    }

    @Synchronized
    fun played(): Duration {
        val now = clock.elapsedMillis()
        return (playedMs + (playingSince?.let { now - it } ?: 0)).coerceAtLeast(0).milliseconds
    }

    @Synchronized
    fun elapsed(): Duration = (clock.elapsedMillis() - since).coerceAtLeast(0).milliseconds
}

/**
 * Decides when the time left crosses one of the warning points, once per point, even when a newer status moves the
 * time left back up a little
 */
class KidsWarnings {
    private var warned: Duration? = null

    /**
     * The warning to show going from [previous] to [current] time left: the lowest point crossed, if any
     */
    @Synchronized
    fun next(
        previous: Duration?,
        current: Duration?,
        points: List<Duration>,
    ): Duration? {
        if (current == null) {
            warned = null
            return null
        }
        // Time was added, or a new day or sitting started
        warned?.let { if (current > it + RESET_MARGIN) warned = null }
        if (previous == null || current <= Duration.ZERO) return null
        val crossed =
            points
                .filter { previous > it && current <= it && warned.let { w -> w == null || it < w } }
                .minOrNull() ?: return null
        warned = crossed
        return crossed
    }

    companion object {
        val RESET_MARGIN = 60.seconds
    }
}

/**
 * The current kid's [KidsLimits] for the bar, the warnings and the time's up screens, from [KidsAllowanceService]
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class KidsTimeLimits
    @Inject
    constructor(
        val allowanceService: KidsAllowanceService,
        clock: MonotonicClock,
        @param:DefaultCoroutineScope scope: CoroutineScope,
    ) {
        private val countdown = KidsCountdown(clock)
        private val warningTracker = KidsWarnings()
        private val _warnings = MutableSharedFlow<Duration>(extraBufferCapacity = 4)

        /**
         * Emits the time left when it crosses one of the server's warning points
         */
        val warnings: SharedFlow<Duration> = _warnings

        private var previous: KidsLimits? = null

        val state: StateFlow<KidsLimits> =
            combine(allowanceService.snapshot, allowanceService.refusal, allowanceService.foreground) { snapshot, refusal, fg ->
                Triple(snapshot, refusal, fg)
            }.flatMapLatest { (snapshot, refusal, fg) ->
                if (snapshot == null) {
                    flowOf(KidsLimits.of(null, refusal))
                } else {
                    countdown.from(snapshot.receivedAt)
                    flow {
                        while (true) {
                            emit(KidsLimits.of(snapshot.status, refusal, countdown.played(), countdown.elapsed()))
                            // In the background nothing is on screen, so a single value is enough
                            if (!fg) break
                            delay(TICK)
                        }
                    }
                }
            }.onEach { limits ->
                val points =
                    allowanceService.snapshot.value
                        ?.status
                        ?.warnAtSeconds
                        .orEmpty()
                        .map { it.seconds }
                warningTracker.next(previous?.remaining, limits.remaining, points)?.let { _warnings.tryEmit(it) }
                previous = limits
            }.stateIn(scope, SharingStarted.Eagerly, KidsLimits())

        /**
         * Whether something is playing on this device, which counts down the allowance and the sitting between polls
         */
        fun setPlaying(playing: Boolean) = countdown.setPlaying(playing)

        val interrupted = KidsInterruptedPlayback()

        companion object {
            private val TICK = 1.seconds
        }
    }

/**
 * Where playback was when time ran out
 */
data class ResumePoint(
    val itemId: UUID,
    val positionMs: Long,
)

/**
 * What was playing when time ran out, so that a grown-up adding time from the time's up screen resumes it. Time that
 * comes back on its own, such as the next day, does not. Only kept in memory.
 */
class KidsInterruptedPlayback {
    @Volatile
    private var point: ResumePoint? = null

    /**
     * Set when a grown-up adds time while time is up
     */
    @Volatile
    var timeAdded = false

    /**
     * Time ran out while [point] was playing, or nothing was if null
     */
    fun interrupt(point: ResumePoint?) {
        this.point = point
        timeAdded = false
    }

    /**
     * What to resume on leaving the time's up screen, if a grown-up added time. Forgotten either way.
     */
    fun take(): ResumePoint? =
        point.takeIf { timeAdded }.also {
            point = null
            timeAdded = false
        }
}

/**
 * Whether [status], the last one cached for a kid, still stops them at [now]: time's up until the next day starts,
 * a break until it ends and outside allowed hours until they start, by the wall clock (research R6). A denial whose
 * end can't be told counts as stopped (fail closed); no status, or one without a denial, doesn't stop.
 */
fun isStopped(
    status: KidsStatus?,
    now: Instant,
): Boolean {
    val denial = status?.denial ?: return false
    val end =
        when (denial) {
            KidsDenial.TIME_UP -> {
                instantOrNull(status.deniedUntil) ?: instantOrNull(status.nextDayStartsAt)
            }

            KidsDenial.BEDTIME -> {
                instantOrNull(status.deniedUntil)
            }

            KidsDenial.SITTING_OVER -> {
                val serverTime = instantOrNull(status.serverTime)
                val breakLeft = status.breakRemainingSeconds
                if (serverTime != null && breakLeft != null) serverTime.plusSeconds(breakLeft) else instantOrNull(status.deniedUntil)
            }
        } ?: return true
    return now.isBefore(end)
}

private fun instantOrNull(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
