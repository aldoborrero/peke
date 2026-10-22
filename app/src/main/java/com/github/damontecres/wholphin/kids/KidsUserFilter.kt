package com.github.damontecres.wholphin.kids

import com.github.damontecres.wholphin.data.model.JellyfinUser
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

/**
 * Whether a sign-in may be stored while the device is [locked] to kids mode: only as the row being signed in again
 * ([existingUser], a kid), and only if the server signed in that same Jellyfin user. Otherwise any account, for
 * example one approved by Quick Connect from another phone, would take over the kid's place on the TV.
 */
fun kidsLockAllowsSignIn(
    locked: Boolean,
    existingUser: JellyfinUser?,
    signedInUserId: UUID?,
    signedInServerId: UUID?,
): Boolean =
    !locked ||
        (
            existingUser != null &&
                signedInUserId == existingUser.id &&
                signedInServerId == existingUser.serverId
        )
