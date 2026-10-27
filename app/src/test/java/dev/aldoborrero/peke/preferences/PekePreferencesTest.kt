package dev.aldoborrero.peke.preferences

import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.ui.isGrownUp
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceGroup
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PekePreferencesTest {
    private fun PreferenceGroup<AppPreferences>.all() = preferences + conditionalPreferences.flatMap { it.preferences }

    /**
     * Settings' own sub-screens, which its items open
     */
    private val subScreens =
        mapOf(
            PreferenceScreenOption.PEKE_PLAYBACK to pekePlaybackPreferences,
            PreferenceScreenOption.PEKE_ADVANCED to pekeAdvancedPreferences,
        )

    private val opened =
        pekePreferences
            .flatMap { it.all() }
            .mapNotNull { ((it as? AppDestinationPreference)?.destination as? Destination.Settings)?.screen }
            .filter { it in subScreens }

    /**
     * Everything Settings shows, also in the sub-screens it opens
     */
    private val shown =
        pekePreferences.flatMap { it.all() } + opened.flatMap { screen -> subScreens.getValue(screen).flatMap { it.all() } }

    @Test
    fun grownUpsGetThePlaybackSwitchesAndSetupSettings() {
        val expected =
            listOf(
                AppPreference.PlayerBackendPref,
                AppPreference.FfmpegPreference,
                AppPreference.DecodeAv1,
                AppPreference.MaxBitrate,
                AppPreference.MaxResolutionPref,
                AppPreference.RefreshRateSwitching,
                AppPreference.ResolutionSwitching,
                AppPreference.PlaybackDebugInfo,
                ExperimentalPreference.Enable,
                ExperimentalPreference.ExperimentalSettings,
                AppPreference.InstalledVersion,
                AppPreference.Update,
                AppPreference.UserInterfaceLanguage,
                AppPreference.SubtitleStyle,
                AppPreference.ScreensaverSettings,
                AppPreference.OssLicenseInfo,
            )
        expected.forEach { assertTrue("missing ${it.title}", it in shown) }
    }

    @Test
    fun settingsForGrownUpViewingAreLeftOut() {
        val excluded =
            listOf(
                AppPreference.ManageMedia,
                AppPreference.DisplayTogglesPref,
                AppPreference.ShowLogos,
                AppPreference.BackdropStylePref,
                AppPreference.SlideshowDuration,
                AppPreference.SlideshowPlayVideos,
                AppPreference.RememberSelectedTab,
                AppPreference.ShowNextUpTiming,
                AppPreference.UserProfileSettings,
                AppPreference.KidsMode,
            )
        excluded.forEach { assertTrue("unexpected ${it.title}", it !in shown) }
    }

    @Test
    fun aFreshInstallGetsItsDefaultsFromTheSerializer() {
        val defaults = AppPreferencesSerializer().defaultValue
        assertEquals(
            ScreensaverPreference.DimPercentage.defaultValue.toInt(),
            defaults.interfacePreferences.screensaverPreference.dimPercent,
        )
        assertEquals(AppPreference.MpvGpuNext.defaultValue, defaults.playbackPreferences.mpvOptions.useGpuNext)
    }

    @Test
    fun theLicenseAndResetAreInSettings() {
        // About has the version, the update check and the license (constitution VII), for the guarded Settings page
        val about = pekePreferences.single { it.title == R.string.about }.preferences
        assertEquals(
            listOf(
                AppPreference.InstalledVersion,
                AppPreference.Update,
                AppPreference.AutoCheckForUpdates,
                AppPreference.OssLicenseInfo,
            ),
            about,
        )
        assertTrue(AppPreference.OssLicenseInfo.destination.isGrownUp())
        // Reset this TV comes last, in a group of its own
        assertEquals(listOf(AppPreference.ResetThisTv), pekePreferences.last().preferences)
        assertTrue(pekePreferences.last().conditionalPreferences.isEmpty())
        assertEquals(1, shown.count { it == AppPreference.OssLicenseInfo })
    }

    @Test
    fun settingsKeepEveryItemInItsGroupOrSubScreen() {
        // Every item Settings had before the regrouping
        val before =
            listOf(
                AppPreference.UserInterfaceLanguage,
                AppPreference.SubtitleStyle,
                AppPreference.ScreensaverSettings,
                AppPreference.ShowClock,
                AppPreference.KidsVoiceSearch,
                AppPreference.ServerAddress,
                AppPreference.SkipForward,
                AppPreference.SkipBack,
                AppPreference.SkipBackOnResume,
                AppPreference.OneClickPause,
                AppPreference.ControllerTimeout,
                AppPreference.DpadSeekModePref,
                AppPreference.SkipSegments,
                AppPreference.GlobalContentScale,
                AppPreference.MaxResolutionPref,
                AppPreference.MaxBitrate,
                AppPreference.RefreshRateSwitching,
                AppPreference.ResolutionSwitching,
                AppPreference.PlaybackDebugInfo,
                AppPreference.PlayerBackendPref,
                AppPreference.ExoPlayerSettings,
                AppPreference.MpvSettings,
                AppPreference.FfmpegPreference,
                AppPreference.DecodeAv1,
                AppPreference.InstalledVersion,
                AppPreference.Update,
                AppPreference.AutoCheckForUpdates,
                AppPreference.OssLicenseInfo,
                AppPreference.SendAppLogs,
                AppPreference.SendCrashReports,
                AppPreference.DebugLogging,
                AppPreference.ImageDiskCacheSize,
                AppPreference.ClearImageCache,
                ExperimentalPreference.Enable,
                ExperimentalPreference.ExperimentalSettings,
                AppPreference.ResetThisTv,
            )
        before.forEach { assertTrue("missing ${it.title}", it in shown) }
        // Each item shows once
        assertEquals(shown.distinct(), shown)
    }

    @Test
    fun settingsAreGroupedWithPlaybackAndAdvancedOnSubScreens() {
        assertEquals(
            listOf(
                R.string.peke_settings_household,
                R.string.peke_settings_this_tv,
                R.string.more,
                R.string.about,
                R.string.peke_this_tv_start_over,
            ),
            pekePreferences.map { it.title },
        )
        // The household's parent code, then this TV, with its kids, the timer bar (spec 006 FR-019) and the theme (spec 007)
        assertEquals(listOf(AppPreference.ChangeParentCode), pekePreferences.first().all())
        assertEquals(
            listOf(
                AppPreference.ServerAddress,
                AppPreference.KidsOnThisTv,
                AppPreference.KidsVoiceSearch,
                AppPreference.KidsTimerBar,
                AppPreference.ThemeColors,
                AppPreference.ScreensaverSettings,
                AppPreference.ShowClock,
                AppPreference.UserInterfaceLanguage,
                AppPreference.SubtitleStyle,
            ),
            pekePreferences[1].all(),
        )
        assertEquals(
            listOf(AppPreference.PekePlaybackSettings, AppPreference.PekeAdvancedSettings),
            pekePreferences[2].all(),
        )
        assertEquals(listOf(PreferenceScreenOption.PEKE_PLAYBACK, PreferenceScreenOption.PEKE_ADVANCED), opened)
        // The sub-screens are settings pages, behind the parent gate like Settings
        listOf(AppPreference.PekePlaybackSettings, AppPreference.PekeAdvancedSettings).forEach {
            assertTrue(it.destination.isGrownUp())
        }
        // Playback and player: the playback group, then the backend with its Exo or MPV settings
        assertEquals(listOf(R.string.playback, R.string.player_backend), pekePlaybackPreferences.map { it.title })
        assertEquals(3, pekePlaybackPreferences.last().conditionalPreferences.size)
        // Advanced: logs, crash reports, the image cache and experimental, with its settings when on
        assertEquals(
            listOf(
                AppPreference.SendAppLogs,
                AppPreference.SendCrashReports,
                AppPreference.DebugLogging,
                AppPreference.ImageDiskCacheSize,
                AppPreference.ClearImageCache,
                ExperimentalPreference.Enable,
                ExperimentalPreference.ExperimentalSettings,
            ),
            pekeAdvancedPreferences.single().all(),
        )
    }
}
