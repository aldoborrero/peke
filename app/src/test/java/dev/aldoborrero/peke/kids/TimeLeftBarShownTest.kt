package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.ui.timeLeftBarShown
import dev.aldoborrero.peke.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The time-left bar shows only on the playback page (spec 022 FR-001)
 */
class TimeLeftBarShownTest {
    private val id = UUID.randomUUID()

    @Test
    fun `the bar is drawn on the playback page, at the top or the bottom`() {
        listOf(Destination.Playback(id, 0L), Destination.PlaybackList(id)).forEach { page ->
            assertTrue("$page", timeLeftBarShown(page, KidsTimerBarPosition.TOP))
            assertTrue("$page", timeLeftBarShown(page, KidsTimerBarPosition.BOTTOM))
        }
    }

    @Test
    fun `no bar on any other page`() {
        listOf(
            null,
            Destination.Home(),
            Destination.Search(),
            Destination.Favorites,
            Destination.KidsLiveTv,
            Destination.MediaItem(id, BaseItemKind.MOVIE),
            Destination.SeriesOverview(id, BaseItemKind.SERIES),
            Destination.KidsResume(Destination.Playback(id, 0L)),
            Destination.KidRules,
            Destination.HomeSettings,
            Destination.KidsTimesUp,
            Destination.KidsChannelRefused,
        ).forEach { page ->
            assertFalse("$page", timeLeftBarShown(page, KidsTimerBarPosition.TOP))
            assertFalse("$page", timeLeftBarShown(page, KidsTimerBarPosition.BOTTOM))
        }
    }

    @Test
    fun `hidden is never drawn, not even while playing`() {
        assertFalse(timeLeftBarShown(Destination.Playback(id, 0L), KidsTimerBarPosition.HIDDEN))
        assertFalse(timeLeftBarShown(Destination.PlaybackList(id), KidsTimerBarPosition.HIDDEN))
    }
}
