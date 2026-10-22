package dev.aldoborrero.peke.services.hilt

import android.content.Context
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.model.DeviceInfo
import java.security.MessageDigest

/**
 * Peke's device on the Jellyfin server: the TV's own name, with an id derived from the SDK's so it never equals
 * the id another app (such as Wholphin) sends from the same TV, which would make the server revoke that app's session
 */
fun pekeDeviceInfo(context: Context): DeviceInfo {
    val device = androidDevice(context)
    return DeviceInfo(id = pekeDeviceId(device.id), name = device.name)
}

fun pekeDeviceId(baseId: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest("$baseId:peke".toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)
