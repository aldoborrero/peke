package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.playback.InARow
import dev.aldoborrero.peke.kids.playback.inARow
import dev.aldoborrero.peke.kids.playback.kidsPlayerTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KidsPlayerTest {
    @Test
    fun `the in-a-row chip shows only with a cap, counting the episode that is ending`() {
        assertNull(inARow(playedInARow = 0, cap = null))
        assertNull(inARow(playedInARow = 3, cap = 0))
        // The first episode is ending: nothing autoplayed yet
        assertEquals(InARow(1, 3), inARow(playedInARow = 0, cap = 3))
        assertEquals(InARow(2, 3), inARow(playedInARow = 1, cap = 3))
        // Never past the cap
        assertEquals(InARow(3, 3), inARow(playedInARow = 7, cap = 3))
    }

    @Test
    fun `the player's title is the show and the episode on one line`() {
        assertEquals("Space Pals · Episode 4", kidsPlayerTitle("Space Pals", "Episode 4"))
        assertEquals("Moon Party", kidsPlayerTitle("Moon Party", null))
        assertEquals("Episode 4", kidsPlayerTitle(null, "Episode 4"))
        assertNull(kidsPlayerTitle(null, null))
    }
}
