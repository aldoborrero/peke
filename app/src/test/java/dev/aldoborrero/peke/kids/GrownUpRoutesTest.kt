package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.ui.GrownUpRoute
import dev.aldoborrero.peke.kids.ui.grownUpRoute
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class GrownUpRoutesTest {
    private val itemId = UUID.randomUUID()

    private val allowed =
        listOf(
            Destination.KidsSettings,
            Destination.SubtitleSettings(false),
            Destination.License,
            Destination.Debug,
            Destination.UpdateApp,
        ) +
            listOf(
                PreferenceScreenOption.PEKE,
                PreferenceScreenOption.EXO_PLAYER,
                PreferenceScreenOption.MPV,
                PreferenceScreenOption.HDR_FORMATS,
                PreferenceScreenOption.SCREENSAVER,
                PreferenceScreenOption.SKIP_SEGMENTS,
                PreferenceScreenOption.EXPERIMENTAL,
            ).map { Destination.Settings(it) }

    private val blocked =
        listOf(
            Destination.Home(),
            Destination.Search(),
            Destination.Favorites,
            Destination.NowPlaying,
            Destination.KidsLiveTv,
            Destination.HomeSettings,
            Destination.UserAppPreferences,
            Destination.MediaItem(itemId, BaseItemKind.MOVIE),
            Destination.SeriesOverview(itemId, BaseItemKind.SERIES),
            Destination.Playback(itemId, 0L),
            Destination.Settings(PreferenceScreenOption.BASIC),
            Destination.Settings(PreferenceScreenOption.ADVANCED),
        )

    @Test
    fun grownUpsReachOnlyTheSetupPages() {
        allowed.forEach { assertEquals(it.toString(), GrownUpRoute.Show(it), grownUpRoute(it, lockEngaged = false)) }
        blocked.forEach { assertEquals(it.toString(), GrownUpRoute.Shell, grownUpRoute(it, lockEngaged = false)) }
    }

    @Test
    fun anEngagedLockNeverOpensTheShell() {
        (allowed + blocked).forEach {
            assertEquals(it.toString(), GrownUpRoute.KidsUserList, grownUpRoute(it, lockEngaged = true))
        }
    }

    @Test
    fun anUnknownLockWaits() {
        (allowed + blocked).forEach { assertEquals(GrownUpRoute.Loading, grownUpRoute(it, lockEngaged = null)) }
    }
}
