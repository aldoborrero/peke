package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.ui.barMinutes
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimeLeftBarTest {
    @Test
    fun `the bar shows whole minutes, rounded up, and never 0 while time is left`() {
        assertEquals(12, barMinutes(12.minutes))
        assertEquals(12, barMinutes(11.minutes + 1.seconds))
        assertEquals(1, barMinutes(20.seconds))
        assertEquals(1, barMinutes(0.seconds))
    }
}
