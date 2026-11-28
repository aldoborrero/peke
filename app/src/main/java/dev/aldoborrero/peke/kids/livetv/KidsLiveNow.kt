package dev.aldoborrero.peke.kids.livetv

import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.server.KidsLiveTvItem
import dev.aldoborrero.peke.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * A programme on a kid's channel, as the live card and the Live page show it (spec 036)
 *
 * @param name the series, or the programme's title
 * @param episodeTitle the episode's own title, when the guide has one
 * @param thumbTag the episode's 16:9 still (ErsatzTV's `still`, Jellyfin's Thumb)
 * @param primaryTag the series' poster (ErsatzTV's `icon`, Jellyfin's Primary)
 */
data class LiveProgramme(
    val id: UUID,
    val channelId: UUID,
    val name: String?,
    val episodeTitle: String?,
    val episodeNumber: Int?,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val thumbTag: String?,
    val primaryTag: String?,
)

/**
 * What a live card shows of a channel: its name and logo, and the programme airing; no programme when the channel has
 * no guide (or a gap in it)
 */
data class LiveCard(
    val channelId: UUID,
    val channelName: String?,
    val logoTag: String?,
    val programme: LiveProgramme?,
)

/**
 * The picture a live card draws, in the owner's order (spec 036): the episode's still, the series' poster, the channel's
 * logo on the channel's tile, then Peke's TV
 */
sealed interface LivePicture {
    data class Still(
        val programmeId: UUID,
        val tag: String,
    ) : LivePicture

    data class Poster(
        val programmeId: UUID,
        val tag: String,
    ) : LivePicture

    data class Logo(
        val channelId: UUID,
        val tag: String,
    ) : LivePicture

    data object PekeTv : LivePicture
}

/**
 * The pictures to try for [card], best first; the card moves to the next one when a picture can't load. Peke's TV is
 * always last.
 */
fun livePictures(card: LiveCard): List<LivePicture> =
    listOfNotNull(
        card.programme?.let { p -> p.thumbTag?.let { LivePicture.Still(p.id, it) } },
        card.programme?.let { p -> p.primaryTag?.let { LivePicture.Poster(p.id, it) } },
        card.logoTag?.let { LivePicture.Logo(card.channelId, it) },
        LivePicture.PekeTv,
    )

/**
 * Whether a card drawing [picture] carries the channel's logo in a pill: only over the programme's own picture, and
 * only when the channel has a logo (a channel without one is named in the line under the card)
 */
fun showsLogoPill(
    card: LiveCard,
    picture: LivePicture,
): Boolean = card.logoTag != null && (picture is LivePicture.Still || picture is LivePicture.Poster)

/**
 * How long a programme has left, in the words the card uses
 */
sealed interface TimeLeft {
    /** Its last minute: "ending now" */
    data object EndingNow : TimeLeft

    /** Under an hour: "12 min left", "1 min left" */
    data class Minutes(
        val minutes: Int,
    ) : TimeLeft

    /** From an hour: "1 h 20 min left", or "1 h left" on the hour */
    data class Hours(
        val hours: Int,
        val minutes: Int,
    ) : TimeLeft
}

/**
 * The time left from [now] to [end], in whole minutes rounded down: so it changes once a minute, and the last minute
 * (and past the end, before the next programme comes in) is [TimeLeft.EndingNow]
 */
fun timeLeft(
    now: LocalDateTime,
    end: LocalDateTime,
): TimeLeft {
    val minutes = Duration.between(now, end).seconds / 60
    return when {
        minutes < 1 -> TimeLeft.EndingNow
        minutes < 60 -> TimeLeft.Minutes(minutes.toInt())
        else -> TimeLeft.Hours((minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

/**
 * What the card shows of a programme's time at [now]: the time left and how far it has gone, both moving once a
 * minute, so the card recomposes once a minute and not on every tick of the clock
 */
data class LiveStatus(
    val timeLeft: TimeLeft,
    val progress: Float,
)

fun liveStatus(
    programme: LiveProgramme,
    now: LocalDateTime,
): LiveStatus {
    val length = Duration.between(programme.start, programme.end).seconds
    val leftMinutes = (Duration.between(now, programme.end).seconds / 60).coerceAtLeast(0)
    val progress = if (length > 0) ((length - leftMinutes * 60).toFloat() / length).coerceIn(0f, 1f) else 1f
    return LiveStatus(timeLeft(now, programme.end), progress)
}

/**
 * What a channel's row on the Live page shows: the programme airing and the one after it
 */
data class NowNext(
    val now: LiveProgramme?,
    val next: LiveProgramme?,
)

/**
 * The programme airing on [channelId] at [at] (start ≤ at < end), and the first one starting at or after its end (or
 * after [at] in a gap)
 */
fun nowNext(
    programmes: List<LiveProgramme>,
    channelId: UUID,
    at: LocalDateTime,
): NowNext {
    val guide = programmes.filter { it.channelId == channelId }.sortedBy { it.start }
    val now = guide.firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
    val after = now?.end ?: at
    val next = guide.firstOrNull { it != now && !it.start.isBefore(after) }
    return NowNext(now, next)
}

/**
 * When the Live page reads its guide again after [now]: when the last programme it knows on a channel starts, as from
 * then it has no "later" for it; or, when that one is already airing (a long programme filling the window), at its end.
 * Null when no channel has a guide; reopening the page reads it then.
 */
fun guideRefreshAt(
    programmes: List<LiveProgramme>,
    now: LocalDateTime,
): LocalDateTime? =
    programmes
        .groupBy { it.channelId }
        .values
        .mapNotNull { guide ->
            val last = guide.maxByOrNull { it.start } ?: return@mapNotNull null
            if (last.start.isAfter(now)) last.start else last.end.takeIf { it.isAfter(now) }
        }.minOrNull()

/**
 * The window the Live page asks the plugin for: from now, three hours, enough for now and next on every channel
 * (the old guide asked for 48 hours)
 */
object LiveGuideWindow {
    val LENGTH: Duration = Duration.ofHours(3)

    fun from(now: Instant): Pair<Instant, Instant> {
        val start = now.truncatedTo(ChronoUnit.SECONDS)
        return start to start.plus(LENGTH)
    }
}

/**
 * A programme from the plugin's guide (`GET /Kids/Users/{id}/LiveTv/Programs`); null without an id, a channel or
 * dates. The plugin's dates are UTC; the card works on the TV's clock in [zone].
 */
fun liveProgramme(
    item: KidsLiveTvItem,
    zone: ZoneId = ZoneId.systemDefault(),
): LiveProgramme? {
    val id = item.id?.toUUIDOrNull() ?: return null
    val channelId = item.channelId?.toUUIDOrNull() ?: return null
    val start = item.startDate?.let { parseUtc(it, zone) } ?: return null
    val end = item.endDate?.let { parseUtc(it, zone) } ?: return null
    return LiveProgramme(
        id = id,
        channelId = channelId,
        name = item.name,
        episodeTitle = item.episodeTitle,
        episodeNumber = item.indexNumber,
        start = start,
        end = end,
        thumbTag = item.imageTags[THUMB],
        primaryTag = item.imageTags[PRIMARY],
    )
}

private const val THUMB = "Thumb"
private const val PRIMARY = "Primary"

private fun parseUtc(
    text: String,
    zone: ZoneId,
): LocalDateTime? =
    try {
        // Jellyfin writes seven fractional digits and a Z; older servers may leave the Z out
        val instant = if (text.endsWith("Z") || text.contains('+')) Instant.parse(text) else Instant.parse("${text}Z")
        LocalDateTime.ofInstant(instant, zone)
    } catch (_: Exception) {
        null
    }

/**
 * A home row's item as a live card: a programme with its channel, or a channel (no guide)
 */
fun liveCard(item: BaseItemDto): LiveCard? =
    if (item.type == BaseItemKind.TV_CHANNEL) {
        LiveCard(item.id, item.name, item.imageTags?.get(ImageType.PRIMARY), null)
    } else {
        item.channelId?.let { channelId ->
            val start = item.startDate
            val end = item.endDate
            LiveCard(
                channelId = channelId,
                channelName = item.channelName,
                logoTag = item.channelPrimaryImageTag,
                programme =
                    if (start != null && end != null) {
                        LiveProgramme(
                            id = item.id,
                            channelId = channelId,
                            name = item.name,
                            episodeTitle = item.episodeTitle,
                            episodeNumber = item.indexNumber,
                            start = start,
                            end = end,
                            thumbTag = item.imageTags?.get(ImageType.THUMB),
                            primaryTag = item.imageTags?.get(ImageType.PRIMARY),
                        )
                    } else {
                        null
                    },
            )
        }
    }

/**
 * A kid's channel as a home row item, for a channel with nothing airing: it plays the channel when clicked
 */
fun KidsChannel.asItem(): BaseItem =
    BaseItem(
        BaseItemDto(
            id = id,
            type = BaseItemKind.TV_CHANNEL,
            name = name,
            channelNumber = number,
            imageTags = imageTag?.let { mapOf(ImageType.PRIMARY to it) },
        ),
        false,
    )

/**
 * The kids home's Watch live row: what airs on the kid's channels, in the kid's channel order, and each channel with
 * nothing airing as a card of its own (no guide), so a channel never drops out of the row. Programmes on a channel
 * not in [channels] (the channels not read yet) keep their place after them.
 */
fun withKidsChannels(
    programmes: List<BaseItem?>,
    channels: List<KidsChannel>,
): List<BaseItem?> {
    if (channels.isEmpty()) return programmes
    val byChannel = programmes.filterNotNull().groupBy { it.data.channelId }
    val known = channels.map { it.id }.toSet()
    return channels.flatMap { channel -> byChannel[channel.id] ?: listOf(channel.asItem()) } +
        programmes.filter { it == null || it.data.channelId !in known }
}

/**
 * The kids home's rows with the kid's [channels] in its Watch live rows, see [withKidsChannels]. A Watch live row the
 * server answered empty (a gap, or channels without a guide) shows the channels.
 */
fun withKidsLiveChannels(
    rows: List<HomeRowLoadingState>,
    channels: List<KidsChannel>,
): List<HomeRowLoadingState> =
    if (channels.isEmpty()) {
        rows
    } else {
        rows.map { row ->
            if (row is HomeRowLoadingState.Success && row.rowType is HomeRowConfig.TvPrograms) {
                row.copy(items = withKidsChannels(row.items, channels))
            } else {
                row
            }
        }
    }
