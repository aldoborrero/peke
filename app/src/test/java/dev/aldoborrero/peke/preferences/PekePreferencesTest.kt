package dev.aldoborrero.peke.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PekePreferencesTest {
    private val shown =
        pekePreferences.flatMap { group ->
            group.preferences + group.conditionalPreferences.flatMap { it.preferences }
        }

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
}
