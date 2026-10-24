package dev.aldoborrero.peke.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogRedactionTest {
    @Test
    fun `what would let someone sign in is hidden`() {
        val lines =
            listOf(
                "GET http://jf.local/QuickConnect/Connect?secret=abc123",
                "GET http://jf.local/Items?api_key=abc123&Fields=x",
                """Deserialization failed: {"Authenticated":true,"Secret":"abc123","Code":"1"}""",
                """{"User":{},"AccessToken":"abc123"}""",
                """MediaBrowser Client="Peke", DeviceId="d", Token="abc123"""",
            )
        lines.forEach { line ->
            val redacted = LogRedaction.redact(line)
            assertFalse(redacted, redacted.contains("abc123"))
        }
        assertEquals("GET http://jf.local/Items?api_key=******&Fields=x", LogRedaction.redact(lines[1]))
        // Everything else is left as it was
        assertEquals("Current Destination: Home(id=1)", LogRedaction.redact("Current Destination: Home(id=1)"))
    }
}
