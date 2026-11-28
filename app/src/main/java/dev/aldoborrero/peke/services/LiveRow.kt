package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.ui.ProgramItemFields
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * The home's "Watch live" row (spec 034): what is airing now, read again when a programme on it ends
 */
object LiveRow {
    /** After a programme's end, how long to wait before reading the row again, so the server has moved on too */
    val GRACE = 2.seconds

    /**
     * How long to wait to read again the first [QUICK_RETRIES] times the server still sends a programme that has ended
     * on the TV's clock (the TV is a little ahead of the server)
     */
    val QUICK_RETRY = 15.seconds
    const val QUICK_RETRIES = 2

    /** How long to wait to read again after a failed read, or once the quick retries are spent */
    val RETRY = 1.minutes

    /**
     * What is airing now: a programme stays until its very end, and at a boundary the next one, which has started,
     * takes its place. No minimum end date, which would leave the row empty in a programme's last minute.
     * The episode's still (Thumb) and the series' poster (Primary) are the live card's picture (spec 036).
     */
    fun request(
        userId: UUID,
        limit: Int,
    ) = GetProgramsDto(
        userId = userId,
        fields = ProgramItemFields,
        limit = limit,
        enableUserData = true,
        enableImages = true,
        enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.LOGO),
        imageTypeLimit = 1,
        isAiring = true,
    )

    /** The next programme to start from [now] on, on any channel, to know when a gap in the guide ends */
    fun nextStartRequest(
        userId: UUID,
        now: LocalDateTime,
    ) = GetProgramsDto(
        userId = userId,
        minStartDate = now,
        limit = 1,
        sortBy = listOf(ItemSortBy.START_DATE),
        sortOrder = listOf(SortOrder.ASCENDING),
        enableImages = false,
        enableUserData = false,
    )

    /** What the timer's read of the row brought */
    sealed interface Read {
        /** Programmes airing */
        data class Airing(
            val programmes: List<BaseItemDto>,
        ) : Read

        /** Nothing airing on any channel, and when the next programme starts if the guide has one */
        data class Gap(
            val nextStart: LocalDateTime?,
        ) : Read

        /** The read failed: the network, the server, any error */
        data object Failed : Read
    }

    /**
     * How long until the row should be read again: [GRACE] past the first end of the programmes [shown] after [now];
     * when one of them has already ended, the [retries]-th retry ([retryAfter]); null with nothing to wait for (no
     * programmes, or none with an end)
     */
    fun nextRefresh(
        shown: List<BaseItemDto>,
        now: LocalDateTime,
        retries: Int = 0,
    ): Duration? {
        val ends = shown.mapNotNull { it.endDate }
        if (ends.isEmpty()) return null
        if (ends.any { !it.isAfter(now) }) return retryAfter(retries)
        return between(now, ends.min()) + GRACE
    }

    /** The wait before a retry: [QUICK_RETRY] the first [QUICK_RETRIES] times, [RETRY] after */
    fun retryAfter(retries: Int) = if (retries < QUICK_RETRIES) QUICK_RETRY else RETRY

    /**
     * How long until [nextStart] (and [GRACE]); [RETRY] when it has already passed on the TV's clock, so a server
     * behind never makes a busy loop; null when there is none
     */
    fun untilStart(
        nextStart: LocalDateTime?,
        now: LocalDateTime,
    ): Duration? =
        when {
            nextStart == null -> null
            nextStart.isAfter(now) -> between(now, nextStart) + GRACE
            else -> RETRY
        }

    /**
     * Reads the row again each time a programme [shown] ends, or at [nextStart] when it shows nothing:
     * - [read] returns what it brought, or null to stop (the home was left, its return reads the row).
     * - New programmes go on screen with [show]; the same ones that have ended on the TV's clock are retried soon.
     * - A failed read keeps the row and retries after [RETRY]: the timer never puts an error in front of a kid.
     * - A gap keeps the row while a programme on it is still airing on the TV's clock, then [hide]s it, with the next
     *   start to come back at.
     *
     * Runs until there is nothing left to wait for.
     */
    suspend fun refreshAtEnds(
        shown: List<BaseItemDto>,
        nextStart: LocalDateTime?,
        now: () -> LocalDateTime,
        read: suspend () -> Read?,
        show: (Read.Airing) -> Unit,
        hide: (nextStart: LocalDateTime?) -> Unit,
    ) {
        var current = shown
        var retries = 0
        var wait = if (current.isEmpty()) untilStart(nextStart, now()) else nextRefresh(current, now())
        while (true) {
            delay(wait ?: return)
            val result = read() ?: return
            val at = now()
            wait =
                when (result) {
                    Read.Failed -> {
                        RETRY
                    }

                    is Read.Airing -> {
                        if (result.programmes != current) {
                            current = result.programmes
                            show(result)
                        }
                        if (current.any { it.endDate?.isAfter(at) == false }) {
                            nextRefresh(current, at, retries++)
                        } else {
                            retries = 0
                            nextRefresh(current, at)
                        }
                    }

                    is Read.Gap -> {
                        val airing = current.filter { it.endDate?.isAfter(at) == true }
                        if (airing.isNotEmpty()) {
                            nextRefresh(airing, at)
                        } else {
                            current = emptyList()
                            hide(result.nextStart)
                            untilStart(result.nextStart, at)
                        }
                    }
                }
        }
    }

    private fun between(
        from: LocalDateTime,
        to: LocalDateTime,
    ) = java.time.Duration
        .between(from, to)
        .toKotlinDuration()
}
