package dev.aldoborrero.peke.kids.playback

import dev.aldoborrero.peke.kids.data.KidProfile

/**
 * Kids mode autoplay: off by default, and capped at a number of episodes in a row when enabled, unless a grown-up chose
 * no limit (cap 0, spec 030)
 */
object KidsAutoplayPolicy {
    /**
     * Seconds the next-up countdown runs before autoplaying, fixed in Peke (the plugin profile has no field for it)
     */
    const val COUNTDOWN_SECONDS = 15L

    /**
     * @param autoplayedInARow how many episodes were autoplayed since the last user interaction
     */
    fun allowNext(
        profile: KidProfile,
        autoplayedInARow: Int,
    ): Boolean {
        val cap = profile.validAutoplayCap
        return profile.autoplayEnabled && (cap == KidProfile.UNLIMITED_AUTOPLAY_CAP || autoplayedInARow < cap)
    }
}
