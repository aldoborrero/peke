package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.playback.KidsAutoplayPolicy
import dev.aldoborrero.peke.kids.playback.inARow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsAutoplayPolicyTest {
    @Test
    fun `off by default`() {
        assertFalse(KidsAutoplayPolicy.allowNext(KidProfile(userId = 1), 0))
    }

    @Test
    fun `capped when enabled`() {
        val profile = KidProfile(userId = 1, autoplayEnabled = true, autoplayCap = 2)
        assertTrue(KidsAutoplayPolicy.allowNext(profile, 0))
        assertTrue(KidsAutoplayPolicy.allowNext(profile, 1))
        assertFalse(KidsAutoplayPolicy.allowNext(profile, 2))
    }

    @Test
    fun `out of range caps are clamped`() {
        assertFalse(KidsAutoplayPolicy.allowNext(KidProfile(userId = 1, autoplayEnabled = true, autoplayCap = -1), 1))
        assertTrue(KidsAutoplayPolicy.allowNext(KidProfile(userId = 1, autoplayEnabled = true, autoplayCap = 50), 9))
        assertFalse(KidsAutoplayPolicy.allowNext(KidProfile(userId = 1, autoplayEnabled = true, autoplayCap = 50), 10))
    }

    @Test
    fun `cap 0 is no limit, it never stops, and off still wins`() {
        val unlimited = KidProfile(userId = 1, autoplayEnabled = true, autoplayCap = KidProfile.UNLIMITED_AUTOPLAY_CAP)
        assertEquals(0, unlimited.validAutoplayCap)
        listOf(0, 1, 10, 11, 500).forEach { assertTrue(KidsAutoplayPolicy.allowNext(unlimited, it)) }
        assertFalse(KidsAutoplayPolicy.allowNext(unlimited.copy(autoplayEnabled = false), 0))
    }

    @Test
    fun `no in-a-row chip with no limit`() {
        assertNull(inARow(playedInARow = 4, cap = KidProfile(userId = 1, autoplayCap = 0).validAutoplayCap))
    }
}
