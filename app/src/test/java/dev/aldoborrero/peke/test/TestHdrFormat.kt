package dev.aldoborrero.peke.test

import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.HdrOverrideMode
import dev.aldoborrero.peke.preferences.PlaybackOverrides
import dev.aldoborrero.peke.preferences.getHdrRangeTypesFor
import dev.aldoborrero.peke.preferences.updatePlaybackOverrides
import dev.aldoborrero.peke.util.profile.PekeVideoRangeType
import org.junit.Assert
import org.junit.Test

class TestHdrFormat {
    private fun appPreferences(block: PlaybackOverrides.Builder.() -> Unit): AppPreferences =
        AppPreferences.getDefaultInstance().updatePlaybackOverrides(block)

    @Test
    fun `Test all auto returns empty sets`() {
        val prefs = appPreferences { }
        Assert.assertEquals(emptySet<String>(), prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = true))
        Assert.assertEquals(emptySet<String>(), prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true))
    }

    @Test
    fun `Test HDR10 override`() {
        val prefs = appPreferences { hdr10Override = HdrOverrideMode.HDR_OVERRIDE_ENABLE }
        Assert.assertEquals(
            setOf(PekeVideoRangeType.HDR10.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = true),
        )
        Assert.assertEquals(emptySet<String>(), prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true))
    }

    @Test
    fun `Test Dolby Vision Profile 5 override`() {
        val prefs = appPreferences { doviProfile5Override = HdrOverrideMode.HDR_OVERRIDE_DISABLE }
        Assert.assertEquals(
            setOf(PekeVideoRangeType.DOVI.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true),
        )
    }

    @Test
    fun `Test Dolby Vision Profile 7 override requires Jellyfin 10_11`() {
        val prefs = appPreferences { doviProfile7Override = HdrOverrideMode.HDR_OVERRIDE_ENABLE }
        Assert.assertEquals(
            emptySet<String>(),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = false),
        )
        Assert.assertEquals(
            setOf(PekeVideoRangeType.DOVI_WITH_EL.serialName, PekeVideoRangeType.DOVI_WITH_EL_HDR10_PLUS.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = true),
        )
    }

    @Test
    fun `Test Dolby Vision Profile 8 override`() {
        val prefs = appPreferences { doviProfile8Override = HdrOverrideMode.HDR_OVERRIDE_DISABLE }
        Assert.assertEquals(
            setOf(PekeVideoRangeType.DOVI_WITH_HDR10.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = false),
        )
        Assert.assertEquals(
            setOf(PekeVideoRangeType.DOVI_WITH_HDR10.serialName, PekeVideoRangeType.DOVI_WITH_HDR10_PLUS.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true),
        )
    }

    @Test
    fun `Test multiple formats set to the same mode are unioned`() {
        val prefs =
            appPreferences {
                hdr10Override = HdrOverrideMode.HDR_OVERRIDE_ENABLE
                hdr10PlusOverride = HdrOverrideMode.HDR_OVERRIDE_ENABLE
                doviProfile5Override = HdrOverrideMode.HDR_OVERRIDE_DISABLE
            }
        Assert.assertEquals(
            setOf(PekeVideoRangeType.HDR10.serialName, PekeVideoRangeType.HDR10_PLUS.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, jellyfinTenEleven = true),
        )
        Assert.assertEquals(
            setOf(PekeVideoRangeType.DOVI.serialName),
            prefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, jellyfinTenEleven = true),
        )
    }
}
