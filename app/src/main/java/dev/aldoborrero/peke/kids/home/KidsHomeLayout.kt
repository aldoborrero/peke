package dev.aldoborrero.peke.kids.home

import androidx.compose.ui.unit.DpSize
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.timer.KidsLimits
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.ui.KidsMinCardGap
import dev.aldoborrero.peke.kids.ui.barMinutes
import dev.aldoborrero.peke.ui.theme.FocusRoom
import dev.aldoborrero.peke.ui.theme.cpx
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/**
 * The three cards of the kids home boards (HomeRailPekeTV)
 */
enum class KidsCardKind {
    /**
     * Keep watching: a 16:9 picture with the progress inside its bottom edge
     */
    WIDE,

    /**
     * Watch live: a card with the channel's picture, the LIVE badge and its name
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

/**
 * A kids home card's size (HomeRailPekeTV): 320×180 px for keep watching, 300×112 for a channel, 140×200 for a poster
 */
val KidsCardKind.cardSize: DpSize
    get() =
        when (this) {
            KidsCardKind.WIDE -> DpSize(320.cpx, 180.cpx)
            KidsCardKind.CHANNEL -> DpSize(300.cpx, 112.cpx)
            KidsCardKind.POSTER -> DpSize(140.cpx, 200.cpx)
        }

/**
 * The room a row of these cards leaves: DESIGN's 22 px between them, widened so a focused card's scale-up, border and
 * ring clear the next card, the row's ends and the titles above and below
 */
val KidsCardKind.focusRoom: FocusRoom
    get() = FocusRoom.of(cardSize, KidsMinCardGap)

/**
 * The channel whose picture a Watch live card shows, and the picture's tag when Jellyfin has one
 */
data class ChannelPicture(
    val channelId: UUID,
    val tag: String?,
)

/**
 * The picture of a Watch live card's channel: a programme's channel, or the channel itself; null for a programme with
 * no channel
 */
fun channelPicture(item: BaseItemDto): ChannelPicture? =
    if (item.type == BaseItemKind.TV_CHANNEL) {
        ChannelPicture(item.id, item.imageTags?.get(ImageType.PRIMARY))
    } else {
        item.channelId?.let { ChannelPicture(it, item.channelPrimaryImageTag) }
    }
