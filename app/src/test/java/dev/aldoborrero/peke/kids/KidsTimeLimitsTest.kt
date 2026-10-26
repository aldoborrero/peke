package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsJson
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KidsCountdown
import dev.aldoborrero.peke.kids.timer.KidsFetchClock
import dev.aldoborrero.peke.kids.timer.KidsInterruptedPlayback
import dev.aldoborrero.peke.kids.timer.KidsLimits
import dev.aldoborrero.peke.kids.timer.KidsWarnings
import dev.aldoborrero.peke.kids.timer.ResumePoint
import dev.aldoborrero.peke.kids.timer.isStopped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KidsTimeLimitsTest {
    private fun status(
        remaining: Long?,
        timeUp: Boolean = false,
        date: String = "2026-10-18",
    ) = KidsStatus(
        date = date,
        usedSeconds = 0,
        allowanceSeconds = remaining?.let { 2700 },
        bonusSeconds = 300,
        remainingSeconds = remaining,
        timeUp = timeUp,
    )

    private fun KidsStatus.sitting(
        remaining: Long,
        limit: Long = 1800,
        breakRemaining: Long? = null,
    ) = copy(
        sittingLimitSeconds = limit,
        sittingUsedSeconds = limit - remaining,
        sittingRemainingSeconds = remaining,
        breakRemainingSeconds = breakRemaining,
        breakSeconds = 900,
    )

    @Test
    fun `the status the plugin sends is read`() {
        val json =
            """
            {"Date":"2026-10-19","UsedSeconds":2100,"AllowanceSeconds":3600,"BonusSeconds":0,"RemainingSeconds":1500,
             "TimeUp":false,"ServerTime":"2026-10-19T17:30:00.0000000Z","NextDayStartsAt":"2026-10-19T22:00:00.0000000Z",
             "WarnAtSeconds":[300,60],"Enforced":true,"DeniedReason":"sessionLimit",
             "DeniedUntil":"2026-10-19T17:44:10.0000000Z","SittingLimitSeconds":1800,"SittingUsedSeconds":1802,
             "SittingRemainingSeconds":0,"BreakRemainingSeconds":850,"BreakSeconds":900}
            """.trimIndent()
        val status = KidsJson.decodeFromString<KidsStatus>(json)
        assertTrue(status.enforced)
        assertEquals(KidsDenial.SITTING_OVER, status.denial)
        assertEquals(1800L, status.sittingLimitSeconds)
        assertEquals(0L, status.sittingRemainingSeconds)
        assertEquals(850L, status.breakRemainingSeconds)
        assertEquals("2026-10-19T17:44:10.0000000Z", status.deniedUntil)
        // Older plugins send none of it
        val old = KidsJson.decodeFromString<KidsStatus>("""{"Date":"2026-10-19","UsedSeconds":10}""")
        assertFalse(old.enforced)
        assertNull(old.denial)
        assertNull(old.sittingRemainingSeconds)
    }

    @Test
    fun `the denial follows the server's reason`() {
        val base = status(remaining = 600).copy(enforced = true)
        assertEquals(KidsDenial.TIME_UP, base.copy(deniedReason = "timeUp").denial)
        assertEquals(KidsDenial.BEDTIME, base.copy(deniedReason = "outsideSchedule").denial)
        assertEquals(KidsDenial.SITTING_OVER, base.copy(deniedReason = "sessionLimit", breakRemainingSeconds = 60).denial)
        // A reason this app doesn't know still denies
        assertEquals(KidsDenial.TIME_UP, base.copy(deniedReason = "somethingNew").denial)
        assertNull(base.denial)
    }

    @Test
    fun `without enforcement time's up and the break still deny`() {
        assertEquals(KidsDenial.TIME_UP, status(remaining = 0, timeUp = true).denial)
        assertEquals(KidsDenial.SITTING_OVER, status(remaining = 600).sitting(0, breakRemaining = 120).denial)
        assertNull(status(remaining = 600).sitting(300).denial)
    }

    @Test
    fun `a refusal maps its reason`() {
        assertEquals(KidsDenial.TIME_UP, KidsDenial.of("timeUp"))
        assertEquals(KidsDenial.BEDTIME, KidsDenial.of("OUTSIDESCHEDULE"))
        assertEquals(KidsDenial.SITTING_OVER, KidsDenial.of(" sessionLimit "))
        assertNull(KidsDenial.of(null))
        assertNull(KidsDenial.of(""))
    }

    @Test
    fun `the bar shows whichever ends first, the server's sitting or the allowance`() {
        val allowanceFirst = KidsLimits.of(status(remaining = 300).sitting(600))
        assertEquals(300.seconds, allowanceFirst.remaining)
        assertEquals(3000.seconds, allowanceFirst.total)
        assertFalse(allowanceFirst.expired)

        val sittingFirst = KidsLimits.of(status(remaining = 1200).sitting(600))
        assertEquals(600.seconds, sittingFirst.remaining)
        assertEquals(1800.seconds, sittingFirst.total)
        assertEquals(1200.seconds, sittingFirst.allowanceRemaining)
        assertEquals(600.seconds, sittingFirst.sittingRemaining)

        // An unlimited kid with a sitting limit
        val sittingOnly = KidsLimits.of(status(remaining = null).sitting(900))
        assertEquals(900.seconds, sittingOnly.remaining)
    }

    @Test
    fun `without a sitting limit only the allowance counts`() {
        val limits = KidsLimits.of(status(remaining = 1200))
        assertEquals(1200.seconds, limits.remaining)
        assertNull(limits.sittingRemaining)
    }

    @Test
    fun `unlimited kids show nothing`() {
        val limits = KidsLimits.of(status(remaining = null))
        assertNull(limits.remaining)
        assertFalse(limits.expired)
        assertFalse(KidsLimits.of(null).expired)
    }

    @Test
    fun `the time left counts down between polls but never denies`() {
        val status = status(remaining = 120).sitting(600)
        val counted = KidsLimits.of(status, played = 100.seconds, elapsed = 100.seconds)
        assertEquals(20.seconds, counted.remaining)
        val past = KidsLimits.of(status, played = 10.minutes, elapsed = 10.minutes)
        assertEquals(0.seconds, past.remaining)
        assertFalse(past.expired)
        assertNull(past.denial)
    }

    @Test
    fun `denied kids see the screen for the reason and no bar`() {
        val timesUp = KidsLimits.of(status(remaining = 0, timeUp = true))
        assertEquals(KidsDenial.TIME_UP, timesUp.denial)
        assertTrue(timesUp.expired)
        assertNull(timesUp.remaining)

        val bedtime = KidsLimits.of(status(remaining = 600).copy(enforced = true, deniedReason = "outsideSchedule"))
        assertEquals(KidsDenial.BEDTIME, bedtime.denial)
        assertNull(bedtime.remaining)
        assertNull(bedtime.breakRemaining)
    }

    @Test
    fun `a refused playback denies until a newer status`() {
        val limits = KidsLimits.of(status(remaining = 600), refusal = KidsDenial.BEDTIME)
        assertEquals(KidsDenial.BEDTIME, limits.denial)
        assertNull(KidsLimits.of(status(remaining = 600)).denial)
    }

    @Test
    fun `the sitting over screen shows the break left and lifts when it ends`() {
        val onBreak =
            status(remaining = 1500)
                .sitting(0, breakRemaining = 850)
                .copy(enforced = true, deniedReason = "sessionLimit")
        val limits = KidsLimits.of(onBreak, elapsed = 50.seconds)
        assertEquals(KidsDenial.SITTING_OVER, limits.denial)
        assertEquals(800.seconds, limits.breakRemaining)
        // The countdown stops at zero; only the server lifts it
        val ended = KidsLimits.of(onBreak, elapsed = 900.seconds)
        assertEquals(0.seconds, ended.breakRemaining)
        assertTrue(ended.expired)
        // The server reports the break over
        assertFalse(KidsLimits.of(onBreak.copy(breakRemainingSeconds = 0)).expired)
        // The next status no longer denies
        val after = status(remaining = 1500).sitting(1800)
        assertFalse(KidsLimits.of(after).expired)
        assertEquals(1500.seconds, KidsLimits.of(after).remaining)
    }

    @Test
    fun `polls every 10 seconds near a warning or while denied`() {
        assertEquals(KidsAllowanceService.POLL_INTERVAL, KidsAllowanceService.interval(KidsLimits.of(null)))
        assertEquals(KidsAllowanceService.POLL_INTERVAL, KidsAllowanceService.interval(KidsLimits.of(status(600))))
        assertEquals(KidsAllowanceService.SHORT_POLL_INTERVAL, KidsAllowanceService.interval(KidsLimits.of(status(90))))
        // The sitting near its end
        assertEquals(KidsAllowanceService.SHORT_POLL_INTERVAL, KidsAllowanceService.interval(KidsLimits.of(status(600).sitting(100))))
        assertEquals(KidsAllowanceService.SHORT_POLL_INTERVAL, KidsAllowanceService.interval(KidsLimits.of(status(0, timeUp = true))))
        val bedtime = KidsLimits.of(status(600), refusal = KidsDenial.BEDTIME)
        assertEquals(KidsAllowanceService.SHORT_POLL_INTERVAL, KidsAllowanceService.interval(bedtime))
    }

    @Test
    fun `a break about to end is asked about right after it ends`() {
        val onBreak = status(600).sitting(0, breakRemaining = 4)
        assertEquals(5.seconds, KidsAllowanceService.interval(KidsLimits.of(onBreak)))
        assertEquals(1.seconds, KidsAllowanceService.interval(KidsLimits.of(onBreak, elapsed = 30.seconds)))
        assertEquals(10.seconds, KidsAllowanceService.interval(KidsLimits.of(status(600).sitting(0, breakRemaining = 300))))
    }

    @Test
    fun `warnings fire once per point`() {
        val points = listOf(300.seconds, 60.seconds)
        val warnings = KidsWarnings()
        assertNull(warnings.next(null, 400.seconds, points))
        assertEquals(300.seconds, warnings.next(400.seconds, 290.seconds, points))
        // A newer status puts the time left back up a little: no second warning
        assertNull(warnings.next(290.seconds, 305.seconds, points))
        assertNull(warnings.next(305.seconds, 299.seconds, points))
        // Both crossed: the lower one
        assertEquals(60.seconds, KidsWarnings().next(400.seconds, 50.seconds, points))
        // No warning at zero or when nothing counts down
        assertNull(KidsWarnings().next(30.seconds, 0.seconds, points))
        assertNull(KidsWarnings().next(30.seconds, null, points))
    }

    @Test
    fun `warnings come back after time was added`() {
        val points = listOf(300.seconds, 60.seconds)
        val warnings = KidsWarnings()
        assertEquals(300.seconds, warnings.next(400.seconds, 290.seconds, points))
        // A bonus
        assertNull(warnings.next(290.seconds, 1200.seconds, points))
        assertEquals(300.seconds, warnings.next(310.seconds, 290.seconds, points))
    }

    @Test
    fun `the countdown only counts playing time`() {
        val clock = FakeClock(now = 0)
        val countdown = KidsCountdown(clock)
        countdown.from(0)
        clock.now = 10_000
        assertEquals(0.seconds, countdown.played())
        assertEquals(10.seconds, countdown.elapsed())
        countdown.setPlaying(true)
        clock.now = 25_000
        assertEquals(15.seconds, countdown.played())
        countdown.setPlaying(false)
        clock.now = 40_000
        assertEquals(15.seconds, countdown.played())
        // A new status starts again, also while playing
        countdown.setPlaying(true)
        clock.now = 50_000
        countdown.from(45_000)
        assertEquals(5.seconds, countdown.played())
        assertEquals(5.seconds, countdown.elapsed())
        // The same status again changes nothing
        countdown.from(45_000)
        assertEquals(5.seconds, countdown.played())
    }

    @Test
    fun `adding time resumes what time's up interrupted, once`() {
        val point = ResumePoint(UUID.randomUUID(), 12_345)
        val interrupted = KidsInterruptedPlayback()
        interrupted.interrupt(point)
        interrupted.timeAdded = true
        assertEquals(point, interrupted.take())
        assertNull(interrupted.take())
    }

    @Test
    fun `time coming back on its own does not resume playback`() {
        val interrupted = KidsInterruptedPlayback()
        interrupted.interrupt(ResumePoint(UUID.randomUUID(), 0))
        assertNull(interrupted.take())
        // Time added on an earlier time's up does not carry over
        interrupted.timeAdded = true
        interrupted.interrupt(ResumePoint(UUID.randomUUID(), 0))
        assertNull(interrupted.take())
        interrupted.interrupt(null)
        interrupted.timeAdded = true
        assertNull(interrupted.take())
    }

    @Test
    fun `a poll answered after a bonus doesn't undo it`() {
        val bonus = status(remaining = 900).copy(serverTime = "2026-10-18T09:00:05Z")
        // The poll was computed before the bonus, though it was answered after it
        val poll = status(remaining = 0, timeUp = true).copy(serverTime = "2026-10-18T09:00:03Z")
        assertFalse(KidsAllowanceService.isNewer(bonus, poll, ticket = 1, lastApplied = 2))
        val later = status(remaining = 880).copy(serverTime = "2026-10-18T09:00:25Z")
        assertTrue(KidsAllowanceService.isNewer(bonus, later, ticket = 3, lastApplied = 2))
    }

    @Test
    fun `without server times the later request wins`() {
        val bonus = status(remaining = 900)
        val poll = status(remaining = 0, timeUp = true)
        assertFalse(KidsAllowanceService.isNewer(bonus, poll, ticket = 1, lastApplied = 2))
        assertTrue(KidsAllowanceService.isNewer(bonus, poll, ticket = 3, lastApplied = 2))
    }

    @Test
    fun `a cached status is always replaced by the server's`() {
        val cached = status(remaining = 0, timeUp = true).copy(serverTime = "2026-10-18T09:00:00Z")
        // Even if the server's clock went back
        val fresh = status(remaining = 600).copy(serverTime = "2026-10-18T08:00:00Z")
        assertTrue(KidsAllowanceService.isNewer(cached, fresh, ticket = 1, lastApplied = 0))
        assertTrue(KidsAllowanceService.isNewer(null, fresh, ticket = 1, lastApplied = 5))
    }

    // Statuses the server sent at 18:00
    private val serverTime = "2026-10-23T18:00:00Z"

    @Test
    fun `time's up stops the kid until the next day starts`() {
        val timeUp = status(remaining = 0, timeUp = true).copy(serverTime = serverTime, nextDayStartsAt = "2026-10-24T05:00:00.0000000Z")
        assertTrue(isStopped(timeUp, Duration.ZERO))
        assertTrue(isStopped(timeUp, 11.hours - 1.seconds))
        assertFalse(isStopped(timeUp, 11.hours))
    }

    @Test
    fun `a break stops the kid until it ends, by the server's clock`() {
        val onBreak =
            status(remaining = 600)
                .sitting(remaining = 0, breakRemaining = 300)
                .copy(deniedReason = "sessionLimit", enforced = true, serverTime = serverTime)
        assertTrue(isStopped(onBreak, 299.seconds))
        assertFalse(isStopped(onBreak, 300.seconds))
    }

    @Test
    fun `outside allowed hours stops the kid until they start`() {
        val bedtime =
            status(
                remaining = 600,
            ).copy(deniedReason = "outsideSchedule", enforced = true, serverTime = serverTime, deniedUntil = "2026-10-24T07:00:00+02:00")
        assertTrue(isStopped(bedtime, 10.hours))
        assertFalse(isStopped(bedtime, 11.hours + 1.seconds))
    }

    @Test
    fun `a denial past its end no longer stops the kid`() {
        val yesterday = status(remaining = 0, timeUp = true).copy(serverTime = serverTime, nextDayStartsAt = "2026-10-23T05:00:00Z")
        assertFalse(isStopped(yesterday, Duration.ZERO))
        assertFalse(isStopped(status(remaining = 600), Duration.ZERO))
        assertFalse(isStopped(null, Duration.ZERO))
    }

    @Test
    fun `a denial whose end can't be told stops the kid`() {
        assertTrue(isStopped(KidsStatus(enforced = true, deniedReason = "somethingNew"), 1.days))
        assertTrue(isStopped(status(remaining = 0, timeUp = true).copy(serverTime = serverTime, nextDayStartsAt = "not a date"), 1.days))
        assertTrue(isStopped(status(remaining = 0, timeUp = true).copy(serverTime = serverTime), 1.days))
        assertTrue(isStopped(status(remaining = 600).copy(serverTime = serverTime, deniedReason = "outsideSchedule"), 1.days))
        // Without the server's time the stop left can't be counted
        assertTrue(isStopped(status(remaining = 0, timeUp = true).copy(nextDayStartsAt = "2026-10-24T05:00:00Z"), 1.days))
    }

    @Test
    fun `moving the wall clock forward doesn't end a cached stop`() {
        val timeUp = status(remaining = 0, timeUp = true).copy(serverTime = serverTime, nextDayStartsAt = "2026-10-24T05:00:00Z")
        // Fetched at 1_000_000 on the monotonic clock; the wall clock moved a day forward since, the monotonic one a minute
        val fetched = KidsFetchClock(elapsedMillis = 1_000_000, bootCount = 7)
        assertTrue(isStopped(timeUp, KidsAllowanceService.sinceFetch(fetched, elapsedNow = 1_060_000, bootNow = 7)))
        // The monotonic clock past the stop left
        val later = 1_000_000 + 11.hours.inWholeMilliseconds
        assertFalse(isStopped(timeUp, KidsAllowanceService.sinceFetch(fetched, elapsedNow = later, bootNow = 7)))
    }

    @Test
    fun `the time since a fetch can't be told after a reboot or from an old cache`() {
        val fetched = KidsFetchClock(elapsedMillis = 1_000_000, bootCount = 7)
        assertEquals(1.minutes, KidsAllowanceService.sinceFetch(fetched, elapsedNow = 1_060_000, bootNow = 7))
        // Rebooted: a new boot count, even with the monotonic clock past the fetch again
        assertNull(KidsAllowanceService.sinceFetch(fetched, elapsedNow = 50_000_000, bootNow = 8))
        // Without boot counts, the monotonic clock going back tells a reboot
        val noBoot = KidsFetchClock(elapsedMillis = 1_000_000)
        assertEquals(1.minutes, KidsAllowanceService.sinceFetch(noBoot, elapsedNow = 1_060_000, bootNow = null))
        assertNull(KidsAllowanceService.sinceFetch(noBoot, elapsedNow = 10_000, bootNow = null))
        // A status cached before the fetch time was kept
        assertNull(KidsAllowanceService.sinceFetch(null, elapsedNow = 1_060_000, bootNow = 7))

        val timeUp = status(remaining = 0, timeUp = true).copy(serverTime = serverTime, nextDayStartsAt = "2026-10-24T05:00:00Z")
        assertTrue(isStopped(timeUp, KidsAllowanceService.sinceFetch(fetched, elapsedNow = 50_000_000, bootNow = 8)))
        assertTrue(isStopped(timeUp, KidsAllowanceService.sinceFetch(null, elapsedNow = 50_000_000, bootNow = 7)))
    }

    @Test
    fun `a status cached in the old format still reads`() {
        // The cache keeps the server's JSON as it was; the fetch time sits under its own key
        val json = """{"Date":"2026-10-23","TimeUp":true,"RemainingSeconds":0,"NextDayStartsAt":"2026-10-24T05:00:00Z"}"""
        val cached = KidsJson.decodeFromString<KidsStatus>(json)
        assertEquals(KidsDenial.TIME_UP, cached.denial)
        assertTrue(isStopped(cached, null))
    }
}
