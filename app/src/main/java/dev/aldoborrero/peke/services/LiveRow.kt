package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.ui.ProgramItemFields
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
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

    /** How long to wait to read again when every programme shown has already ended (the server's clock is behind) */
    val RETRY = 1.minutes

    /**
     * What is airing now: a programme stays until its very end, and at a boundary the next one, which has started,
     * takes its place. No minimum end date, which would leave the row empty in a programme's last minute.
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
        enableImageTypes = listOf(ImageType.PRIMARY, ImageType.LOGO),
        imageTypeLimit = 1,
        isAiring = true,
    )

    /**
     * How long until the row should be read again: [GRACE] past the first end of the programmes [shown] after [now],
     * [RETRY] when they have all ended, or null with nothing to wait for (no programmes, or none with an end)
     */
    fun nextRefresh(
        shown: List<BaseItemDto>,
        now: LocalDateTime,
    ): Duration? {
        val ends = shown.mapNotNull { it.endDate }
        if (ends.isEmpty()) return null
        val next = ends.filter { it.isAfter(now) }.minOrNull() ?: return RETRY
        return java.time.Duration
            .between(now, next)
            .toKotlinDuration() + GRACE
    }

    /**
     * Reads the row again each time a programme [shown] ends: [refresh] reads it and returns what it now shows, or
     * null to stop (the home was left, its return reads the row). Runs until there is nothing left to wait for.
     */
    suspend fun refreshAtEnds(
        shown: List<BaseItemDto>,
        now: () -> LocalDateTime,
        refresh: suspend () -> List<BaseItemDto>?,
    ) {
        var current = shown
        while (true) {
            delay(nextRefresh(current, now()) ?: return)
            current = refresh() ?: return
        }
    }
}
