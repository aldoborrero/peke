package dev.aldoborrero.peke.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 005 FR-006: the microphone shows only when a grown-up turned voice search on, the permission is granted and
 * the TV has a speech service
 */
class VoiceSearchShownTest {
    @Test
    fun `shown only when everything is in place`() {
        assertTrue(voiceSearchShown(enabled = true, granted = true, available = true))
    }

    @Test
    fun `hidden when any one is missing`() {
        assertFalse(voiceSearchShown(enabled = false, granted = true, available = true))
        assertFalse(voiceSearchShown(enabled = true, granted = false, available = true))
        assertFalse(voiceSearchShown(enabled = true, granted = true, available = false))
    }
}
