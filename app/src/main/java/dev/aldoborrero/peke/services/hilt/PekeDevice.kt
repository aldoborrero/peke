package dev.aldoborrero.peke.services.hilt

import android.content.Context
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.model.DeviceInfo
import java.security.MessageDigest
import java.util.UUID

/**
 * Peke's device on the Jellyfin server: the TV's own name, with an id derived from the SDK's so it never equals the id
 * another app (such as Wholphin) sends from the same TV. A shared id would let a sign-in of the same user in one app
 * replace that user's session in the other; users other than the one signing in keep their tokens.
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

/**
 * The device id every call made with a kid's token goes out with: one per kid, derived from this install's [baseId]
 * (research R2). Jellyfin ends every live session of a device id when one of its tokens is signed out, so with their own
 * id a kid isn't dropped when a sibling or the administrator signs out.
 *
 * The server checks the token, not this id, so a token issued under the install's id keeps working. The Kids plugin
 * binds a parent grant to the device the token was issued on, which no header changes, and a kid's grant request and
 * its use go out with the same id anyway.
 */
fun pekeKidDeviceId(
    baseId: String,
    userId: UUID,
): String = pekeDeviceId("$baseId:$userId")

/**
 * This device as [userId]'s calls name it, see [pekeKidDeviceId]
 */
fun DeviceInfo.forKid(userId: UUID): DeviceInfo = copy(id = pekeKidDeviceId(id, userId))
