package dev.aldoborrero.peke.kids.home

import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.timer.KidsLimits
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.ui.barMinutes

/**
 * The three cards of the kids home boards (HomeRailPekeTV)
 */
enum class KidsCardKind {
    /**
     * Keep watching: a 16:9 picture with the progress inside its bottom edge
     */
    WIDE,

    /**
     * Watch live: a card with the LIVE badge and the channel's name
     */
    CHANNEL,

    /**
     * Every other row: a 2:3 poster
     */
    POSTER,
}

/**
 * The kind of card a kids home row draws, from its type, so the row looks the same before and after it loads
 */
fun kidsCardKind(row: HomeRowConfig?): KidsCardKind =
    when (row) {
        is HomeRowConfig.ContinueWatching,
        is HomeRowConfig.NextUp,
        is HomeRowConfig.ContinueWatchingCombined,
        -> KidsCardKind.WIDE

        is HomeRowConfig.TvPrograms,
        is HomeRowConfig.TvChannels,
        -> KidsCardKind.CHANNEL

        else -> KidsCardKind.POSTER
    }

/**
 * The time left the kids home shows in its header, where the time-left bar would be
 *
 * @param today the day's allowance is what runs out, so it reads "45 min left today"; a sitting runs out first otherwise
 * @param nearEnd from the plugin's first warning point, as the bar ([KidsLimits.nearEnd])
 */
data class HomeTimeLeft(
    val minutes: Int,
    val today: Boolean,
    val nearEnd: Boolean,
)

/**
 * What the home's header shows of [limits]: nothing without a daily limit, while it isn't known, or when the bar is
 * [KidsTimerBarPosition.HIDDEN]
 */
fun homeTimeLeft(
    limits: KidsLimits,
    position: KidsTimerBarPosition,
): HomeTimeLeft? {
    val remaining = limits.remaining ?: return null
    val total = limits.total ?: return null
    if (!total.isPositive() || position == KidsTimerBarPosition.HIDDEN) return null
    val sitting = limits.sittingRemaining
    return HomeTimeLeft(
        minutes = barMinutes(remaining),
        today = sitting == null || limits.allowanceRemaining?.let { it <= sitting } ?: false,
        nearEnd = limits.nearEnd,
    )
}
