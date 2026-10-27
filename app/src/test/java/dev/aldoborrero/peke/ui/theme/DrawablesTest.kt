package dev.aldoborrero.peke.ui.theme

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.VectorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import dev.aldoborrero.peke.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The art converted from design/assets (spec 007 Phase 2): every picture inflates, with the size of its source
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class DrawablesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun vector(res: Int): VectorDrawable {
        val drawable = ContextCompat.getDrawable(context, res)
        assertTrue("$res is a vector", drawable is VectorDrawable)
        return drawable as VectorDrawable
    }

    private fun dp(px: Int) = (px * context.resources.displayMetrics.density).toInt()

    @Test
    fun theTwentyIconsExistInBothSets() {
        assertEquals(
            listOf(
                "audio",
                "back",
                "bedtime",
                "break",
                "episodes",
                "favourites",
                "grown-ups",
                "home",
                "live",
                "next-episode",
                "pause",
                "picks",
                "play",
                "search",
                "settings",
                "subtitles",
                "switch-kid",
                "time-left",
                "voice",
                "watched",
            ),
            PekeIcon.entries.map { it.source },
        )
        PekeIcon.entries.forEach {
            // Drawn on a 48 grid
            listOf(it.twoTone, it.outline).forEach { res ->
                val icon = vector(res)
                assertEquals("${it.source} width", dp(48), icon.intrinsicWidth)
                assertEquals("${it.source} height", dp(48), icon.intrinsicHeight)
            }
        }
    }

    @Test
    fun theBuddyHasItsSixMoods() {
        assertEquals(
            listOf("peke-tv-buddy", "-waving", "-sleeping", "-puzzled", "-searching", "-cheering"),
            BuddyMood.entries.map { it.source },
        )
        BuddyMood.entries.forEach {
            val buddy = vector(it.drawable)
            assertTrue("${it.source} has a size", buddy.intrinsicWidth > 0 && buddy.intrinsicHeight > 0)
        }
    }

    @Test
    fun theSixAvatarsHaveTheirBackgrounds() {
        val expected =
            mapOf(
                "leo" to 0xFFFF927E,
                "max" to 0xFFFFDA70,
                "nico" to 0xFF65D9CE,
                "lucy" to 0xFFB5A0EF,
                "sara" to 0xFFC8CDE6,
                "mia" to 0xFFFFFFFF,
            )
        assertEquals(expected.keys.toList(), PekeAvatar.entries.map { it.id })
        PekeAvatar.entries.forEach {
            assertEquals(it.id, expected.getValue(it.id).toInt(), it.background.toArgb())
            val avatar = vector(it.drawable)
            assertEquals(it.id, dp(200), avatar.intrinsicWidth)
        }
        // The plugin's ids, lowercase; anything else is no avatar (Kids API 1.1, contracts/kids-api-avatar.md)
        assertEquals(PekeAvatar.LUCY, PekeAvatar.of("lucy"))
        assertEquals(null, PekeAvatar.of("Lucy "))
        assertEquals(null, PekeAvatar.of("bob"))
        assertEquals(null, PekeAvatar.of(null))
    }

    @Test
    fun theLogoAndTheBannerAreVectors() {
        assertNotNull(vector(R.drawable.logo_peke))
        // The Android TV launcher banner: 320×180 px at xhdpi
        val banner = vector(R.drawable.ic_banner)
        assertEquals(dp(160), banner.intrinsicWidth)
        assertEquals(dp(90), banner.intrinsicHeight)
    }

    @Test
    fun theLauncherIconIsAdaptiveWithTheBuddy() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        assertTrue((icon as AdaptiveIconDrawable).foreground is VectorDrawable)
        assertNotNull(vector(R.drawable.ic_launcher_monochrome))
    }

    @Test
    @Config(sdk = [25])
    fun beforeAdaptiveIconsTheLauncherIconIsTheWholePicture() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertTrue(icon is VectorDrawable)
    }
}
