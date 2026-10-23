package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.JellyfinUser
import java.util.UUID

/**
 * While the device is locked to kids mode, only known kid profiles may be picked
 */
fun <T> filterUsersForKidsLock(
    users: List<T>,
    user: (T) -> JellyfinUser,
    kidRowIds: Set<Int>,
    locked: Boolean,
): List<T> = if (locked) users.filter { user(it).rowId != 0 && user(it).rowId in kidRowIds } else users

sealed interface KidsLockSignIn {
    /**
     * Store the sign-in in [row], or in a new row when null
     */
    data class Allowed(
        val row: JellyfinUser?,
    ) : KidsLockSignIn

    data object Refused : KidsLockSignIn
}

/**
 * Whether a sign-in may be stored while the device is [locked] to kids mode, and in which row. While locked it may
 * only renew a kid's row as that same Jellyfin user: the row being signed in again ([existingUser]), or, when no row
 * was picked (Add User, Quick Connect), the kid already saved on this device for the signed-in account ([savedKid]).
 * Otherwise any account, for example one approved by Quick Connect from another phone, would take over the kid's
 * place on the TV.
 */
fun kidsLockSignIn(
    locked: Boolean,
    existingUser: JellyfinUser?,
    savedKid: JellyfinUser?,
    signedInUserId: UUID?,
    signedInServerId: UUID?,
): KidsLockSignIn {
    if (!locked) return KidsLockSignIn.Allowed(existingUser)
    val row = existingUser ?: savedKid
    return if (row != null && signedInUserId == row.id && signedInServerId == row.serverId) {
        KidsLockSignIn.Allowed(row)
    } else {
        KidsLockSignIn.Refused
    }
}
