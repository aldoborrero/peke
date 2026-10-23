package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.playback.KidsAutoplayPolicy
import org.junit.Assert.assertFalse
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
        assertFalse(KidsAutoplayPolicy.allowNext(KidProfile(userId = 1, autoplayEnabled = true, autoplayCap = 0), 1))
        assertTrue(KidsAutoplayPolicy.allowNext(KidProfile(userId = 1, autoplayEnabled = true, autoplayCap = 50), 9))
        assertFalse(KidsAutoplayPolicy.allowNext(KidProfile(userId = 1, autoplayEnabled = true, autoplayCap = 50), 10))
    }
}
