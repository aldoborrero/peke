package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.home.ChannelPicture
import dev.aldoborrero.peke.kids.home.HomeTimeLeft
import dev.aldoborrero.peke.kids.home.KidsCardKind
import dev.aldoborrero.peke.kids.home.cardGap
import dev.aldoborrero.peke.kids.home.cardSize
import dev.aldoborrero.peke.kids.home.channelPicture
import dev.aldoborrero.peke.kids.home.homeTimeLeft
import dev.aldoborrero.peke.kids.home.kidsCardKind
import dev.aldoborrero.peke.kids.timer.KidsLimits
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KidsHomeLayoutTest {
    @Test
    fun `keep watching rows are wide, live rows are channels, everything else is posters`() {
        assertEquals(KidsCardKind.WIDE, kidsCardKind(HomeRowConfig.ContinueWatchingCombined()))
        assertEquals(KidsCardKind.WIDE, kidsCardKind(HomeRowConfig.ContinueWatching()))
        assertEquals(KidsCardKind.WIDE, kidsCardKind(HomeRowConfig.NextUp()))
        assertEquals(KidsCardKind.CHANNEL, kidsCardKind(HomeRowConfig.TvPrograms()))
        assertEquals(KidsCardKind.CHANNEL, kidsCardKind(HomeRowConfig.TvChannels()))
        val id = UUID.randomUUID()
        assertEquals(KidsCardKind.POSTER, kidsCardKind(HomeRowConfig.ByParent(parentId = id)))
        assertEquals(KidsCardKind.POSTER, kidsCardKind(HomeRowConfig.RecentlyAdded(parentId = id)))
        assertEquals(KidsCardKind.POSTER, kidsCardKind(null))
    }

    @Test
    fun `the home shows the time left unless there is no limit or the bar is hidden`() {
        val limits = KidsLimits(remaining = 45.minutes, total = 60.minutes, allowanceRemaining = 45.minutes)
        assertEquals(
            HomeTimeLeft(minutes = 45, today = true, nearEnd = false),
            homeTimeLeft(limits, KidsTimerBarPosition.TOP),
        )
        assertEquals(
            HomeTimeLeft(minutes = 45, today = true, nearEnd = false),
            homeTimeLeft(limits, KidsTimerBarPosition.BOTTOM),
        )
        assertNull(homeTimeLeft(limits, KidsTimerBarPosition.HIDDEN))
        // Unlimited, or not known yet
        assertNull(homeTimeLeft(KidsLimits(), KidsTimerBarPosition.TOP))
        assertNull(homeTimeLeft(KidsLimits(remaining = 5.minutes, total = 0.minutes), KidsTimerBarPosition.TOP))
    }

    @Test
    fun `the time left says today only when the day's allowance is what runs out`() {
        val sitting =
            KidsLimits(
                remaining = 20.minutes,
                total = 30.minutes,
                allowanceRemaining = 45.minutes,
                sittingRemaining = 20.minutes,
            )
        assertEquals(HomeTimeLeft(minutes = 20, today = false, nearEnd = false), homeTimeLeft(sitting, KidsTimerBarPosition.TOP))
    }

    @Test
    fun `the time left grows from the first warning point, like the bar, and rounds up`() {
        val limits =
            KidsLimits(
                remaining = 4.minutes + 10.seconds,
                total = 60.minutes,
                allowanceRemaining = 4.minutes + 10.seconds,
                firstWarning = 5.minutes,
            )
        assertEquals(HomeTimeLeft(minutes = 5, today = true, nearEnd = true), homeTimeLeft(limits, KidsTimerBarPosition.TOP))
    }

    @Test
    fun `a row leaves room for a focused card's scale-up, border and ring before the next card`() {
        KidsCardKind.entries.forEach { kind ->
            val width = kind.cardSize.width.value
            // DESIGN: 7% scale-up, half of it on each side, then the 6 px border and 8 px ring (10.5 dp), scaled too
            val overhang = width * .035f + 10.5f * 1.07f
            val gap = kind.cardGap.value
            assertTrue("$kind gap $gap dp under the ${overhang}dp overhang and 4 dp to spare", gap >= overhang + 4f)
            // and never below DESIGN's 22 px (16.5 dp)
            assertTrue("$kind gap $gap dp", gap >= 16.5f)
        }
    }

    @Test
    fun `a live card shows its channel's picture when Jellyfin has one`() {
        val channel = UUID.randomUUID()
        val programme =
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.PROGRAM,
                channelId = channel,
                channelPrimaryImageTag = "tag",
            )
        assertEquals(ChannelPicture(channel, "tag"), channelPicture(programme))
        assertEquals(ChannelPicture(channel, null), channelPicture(programme.copy(channelPrimaryImageTag = null)))

        val tv =
            BaseItemDto(
                id = channel,
                type = BaseItemKind.TV_CHANNEL,
                imageTags = mapOf(ImageType.PRIMARY to "own"),
            )
        assertEquals(ChannelPicture(channel, "own"), channelPicture(tv))
        assertEquals(ChannelPicture(channel, null), channelPicture(tv.copy(imageTags = null)))

        assertNull(channelPicture(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.PROGRAM)))
    }
}
