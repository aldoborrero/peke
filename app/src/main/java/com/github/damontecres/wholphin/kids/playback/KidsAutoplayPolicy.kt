package com.github.damontecres.wholphin.kids.playback

import com.github.damontecres.wholphin.kids.data.KidProfile

/**
 * Kids mode never autoplays endlessly: off by default, and capped at a number of episodes in a row when enabled
 */
object KidsAutoplayPolicy {
    /**
     * @param autoplayedInARow how many episodes were autoplayed since the last user interaction
     */
    fun allowNext(
        profile: KidProfile,
        autoplayedInARow: Int,
    ): Boolean = profile.autoplayEnabled && autoplayedInARow < profile.validAutoplayCap
}
