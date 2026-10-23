package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.services.hilt.pekeDeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PekeDeviceTest {
    @Test
    fun deviceIdIsStableAndDiffersFromTheSdkId() {
        val androidId = "9774d56d682e549c"
        val id = pekeDeviceId(androidId)
        assertEquals(id, pekeDeviceId(androidId))
        assertEquals(32, id.length)
        assertTrue(id.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(androidId, id)
        assertNotEquals(id, pekeDeviceId("another-tv"))
    }
}
