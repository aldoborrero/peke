package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.ui.episodeLabel
import dev.aldoborrero.peke.kids.ui.runtimeMinutes
import dev.aldoborrero.peke.kids.ui.seasonCircle
import dev.aldoborrero.peke.ui.theme.PekeTvPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KidsDetailsTest {
    @Test
    fun `season circles take the theme colours in turn, and the focused one the page colour with the number in time`() {
        val p = PekeTvPalette
        assertEquals(p.secondary to p.onPrimary, seasonCircle(0, focused = false, palette = p))
        assertEquals(p.tertiary to p.onPrimary, seasonCircle(1, focused = false, palette = p))
        assertEquals(p.time to p.onPrimary, seasonCircle(2, focused = false, palette = p))
        assertEquals(p.primary to p.onPrimary, seasonCircle(3, focused = false, palette = p))
        assertEquals(p.secondary to p.onPrimary, seasonCircle(4, focused = false, palette = p))
        assertEquals(p.background to p.time, seasonCircle(2, focused = true, palette = p))
    }

    @Test
    fun `an episode is its number and name, or its name alone`() {
        assertEquals("4. Lost Robot", episodeLabel(4, "Lost Robot"))
        assertEquals("Lost Robot", episodeLabel(null, "Lost Robot"))
        assertEquals("4", episodeLabel(4, null))
        assertEquals("", episodeLabel(null, null))
    }

    @Test
    fun `a film's length is in whole minutes, rounded, and none when unknown or zero`() {
        assertEquals(90, runtimeMinutes(90.minutes))
        assertEquals(24, runtimeMinutes(23.minutes + 40.seconds))
        assertEquals(1, runtimeMinutes(20.seconds))
        assertNull(runtimeMinutes(null))
        assertNull(runtimeMinutes(0.seconds))
    }
}
