package dev.aldoborrero.peke.kids

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 005 FR-018: only the Kids plugin's messages reach a kid's screen (P4)
 */
class KidsServerMessagesTest {
    @Test
    fun `messages from other accounts never show`() {
        listOf(null, "", "Hello", "Dinner's ready", "Screen time!").forEach {
            assertFalse(it.toString(), KidsServerMessages.shouldShow(it, hidden = false))
        }
    }

    @Test
    fun `the plugin's messages show unless Peke already says the same`() {
        assertTrue(KidsServerMessages.shouldShow("Screen time", hidden = false))
        assertTrue(KidsServerMessages.shouldShow(" time's up ", hidden = false))
        assertFalse(KidsServerMessages.shouldShow("Screen time", hidden = true))
    }
}
