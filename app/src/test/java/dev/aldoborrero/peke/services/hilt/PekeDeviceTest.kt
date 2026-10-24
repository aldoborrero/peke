package dev.aldoborrero.peke.services.hilt

import org.jellyfin.sdk.model.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PekeDeviceTest {
    private val androidId = "9774d56d682e549c"
    private val installId = pekeDeviceId(androidId)
    private val kid = UUID.fromString("5f1c0000-0000-0000-0000-000000000001")
    private val sibling = UUID.fromString("5f1c0000-0000-0000-0000-000000000002")

    @Test
    fun deviceIdIsStableAndDiffersFromTheSdkId() {
        assertEquals(installId, pekeDeviceId(androidId))
        assertEquals(32, installId.length)
        assertTrue(installId.isHex())
        assertNotEquals(androidId, installId)
        assertNotEquals(installId, pekeDeviceId("another-tv"))
    }

    @Test
    fun kidDeviceIdIsStableForTheSameInstallAndUser() {
        val id = pekeKidDeviceId(installId, kid)
        assertEquals(id, pekeKidDeviceId(installId, kid))
        assertEquals(32, id.length)
        assertTrue(id.isHex())
    }

    @Test
    fun kidDeviceIdDiffersBetweenUsers() {
        assertNotEquals(pekeKidDeviceId(installId, kid), pekeKidDeviceId(installId, sibling))
    }

    @Test
    fun kidDeviceIdDiffersFromTheInstallId() {
        assertNotEquals(installId, pekeKidDeviceId(installId, kid))
        // And between installs, for the same kid
        assertNotEquals(pekeKidDeviceId(installId, kid), pekeKidDeviceId(pekeDeviceId("another-tv"), kid))
    }

    @Test
    fun kidDeviceKeepsTheTvName() {
        val device = DeviceInfo(id = installId, name = "Living room TV").forKid(kid)
        assertEquals(pekeKidDeviceId(installId, kid), device.id)
        assertEquals("Living room TV", device.name)
    }

    private fun String.isHex() = all { it in '0'..'9' || it in 'a'..'f' }
}
