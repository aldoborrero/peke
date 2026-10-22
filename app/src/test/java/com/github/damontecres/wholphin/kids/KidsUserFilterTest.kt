package com.github.damontecres.wholphin.kids

import com.github.damontecres.wholphin.data.model.JellyfinUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KidsUserFilterTest {
    private val serverId = UUID.randomUUID()
    private val adult = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "adult", serverId = serverId, accessToken = "a")
    private val kid = JellyfinUser(rowId = 2, id = UUID.randomUUID(), name = "kid", serverId = serverId, accessToken = "k")
    private val publicUser = JellyfinUser(id = UUID.randomUUID(), name = "public", serverId = serverId, accessToken = null)
    private val users = listOf(adult, kid, publicUser)

    @Test
    fun `everyone is listed when not locked`() {
        assertEquals(users, filterUsersForKidsLock(users, { it }, setOf(kid.rowId), locked = false))
    }

    @Test
    fun `only known kid profiles are listed when locked`() {
        assertEquals(listOf(kid), filterUsersForKidsLock(users, { it }, setOf(kid.rowId), locked = true))
    }

    @Test
    fun `users not saved on the device never pass when locked`() {
        assertEquals(emptyList<JellyfinUser>(), filterUsersForKidsLock(listOf(publicUser), { it }, setOf(0), locked = true))
    }

    @Test
    fun `while locked a kid's row can only be signed in again as that kid`() {
        assertTrue(kidsLockAllowsSignIn(locked = true, existingUser = kid, signedInUserId = kid.id, signedInServerId = serverId))
        // Another account, for example approved by Quick Connect from a phone, is refused
        assertFalse(kidsLockAllowsSignIn(locked = true, existingUser = kid, signedInUserId = adult.id, signedInServerId = serverId))
        assertFalse(kidsLockAllowsSignIn(locked = true, existingUser = kid, signedInUserId = null, signedInServerId = serverId))
        assertFalse(kidsLockAllowsSignIn(locked = true, existingUser = kid, signedInUserId = kid.id, signedInServerId = UUID.randomUUID()))
        // Adding a user stays blocked while locked
        assertFalse(kidsLockAllowsSignIn(locked = true, existingUser = null, signedInUserId = kid.id, signedInServerId = serverId))
    }

    @Test
    fun `without the lock any sign-in is allowed`() {
        assertTrue(kidsLockAllowsSignIn(locked = false, existingUser = kid, signedInUserId = adult.id, signedInServerId = serverId))
        assertTrue(kidsLockAllowsSignIn(locked = false, existingUser = null, signedInUserId = adult.id, signedInServerId = serverId))
    }
}
