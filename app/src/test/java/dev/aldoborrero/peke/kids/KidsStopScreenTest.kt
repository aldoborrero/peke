package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.ui.KidsTrouble
import dev.aldoborrero.peke.kids.ui.stopMood
import dev.aldoborrero.peke.ui.theme.BuddyMood
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The buddy narrates the stops (design/DESIGN.md, The PekeTV buddy; spec 007 FR-022, FR-023)
 */
class KidsStopScreenTest {
    @Test
    fun `each stop has its mood`() {
        assertEquals(BuddyMood.WAVING, stopMood(KidsDenial.TIME_UP))
        assertEquals(BuddyMood.SLEEPING, stopMood(KidsDenial.BEDTIME))
        // A break isn't a mood of its own: the buddy, waiting with the kid
        assertEquals(BuddyMood.HAPPY, stopMood(KidsDenial.SITTING_OVER))
    }

    @Test
    fun `a kid's error is either the server out of reach or something that won't play`() {
        assertEquals(KidsTrouble.OFFLINE, KidsTrouble.of(UnknownHostException("jf.local")))
        assertEquals(KidsTrouble.OFFLINE, KidsTrouble.of(RuntimeException("load failed", SocketTimeoutException())))
        assertEquals(KidsTrouble.OFFLINE, KidsTrouble.of(IOException("reset")))
        assertEquals(KidsTrouble.OOPS, KidsTrouble.of(IllegalStateException("decoder")))
        assertEquals(KidsTrouble.OOPS, KidsTrouble.of(null))
        assertEquals(BuddyMood.SEARCHING, KidsTrouble.OFFLINE.mood)
        assertEquals(BuddyMood.PUZZLED, KidsTrouble.OOPS.mood)
    }
}
