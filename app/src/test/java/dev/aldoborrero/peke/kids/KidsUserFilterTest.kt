package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.JellyfinUser
import org.junit.Assert.assertEquals
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

    private fun signIn(
        locked: Boolean,
        existingUser: JellyfinUser?,
        signedInUserId: UUID?,
        signedInServerId: UUID? = serverId,
        savedKid: JellyfinUser? = null,
    ) = kidsLockSignIn(locked, existingUser, savedKid, signedInUserId, signedInServerId)

    @Test
    fun `while locked a kid's row can only be signed in again as that kid`() {
        assertEquals(KidsLockSignIn.Allowed(kid), signIn(locked = true, existingUser = kid, signedInUserId = kid.id))
        // Another account, for example approved by Quick Connect from a phone, is refused
        assertEquals(KidsLockSignIn.Refused, signIn(locked = true, existingUser = kid, signedInUserId = adult.id))
        assertEquals(KidsLockSignIn.Refused, signIn(locked = true, existingUser = kid, signedInUserId = null))
        assertEquals(
            KidsLockSignIn.Refused,
            signIn(locked = true, existingUser = kid, signedInUserId = kid.id, signedInServerId = UUID.randomUUID()),
        )
        // A picked kid's row isn't handed to another saved kid
        assertEquals(KidsLockSignIn.Refused, signIn(locked = true, existingUser = kid, signedInUserId = adult.id, savedKid = adult))
    }

    @Test
    fun `while locked a saved kid signing in through Add User renews their row`() {
        assertEquals(KidsLockSignIn.Allowed(kid), signIn(locked = true, existingUser = null, signedInUserId = kid.id, savedKid = kid))
        assertEquals(
            KidsLockSignIn.Refused,
            signIn(locked = true, existingUser = null, signedInUserId = kid.id, signedInServerId = UUID.randomUUID(), savedKid = kid),
        )
    }

    @Test
    fun `while locked adding anyone who isn't a saved kid is refused`() {
        assertEquals(KidsLockSignIn.Refused, signIn(locked = true, existingUser = null, signedInUserId = kid.id))
        assertEquals(KidsLockSignIn.Refused, signIn(locked = true, existingUser = null, signedInUserId = adult.id))
        assertEquals(KidsLockSignIn.Refused, signIn(locked = true, existingUser = null, signedInUserId = adult.id, savedKid = kid))
    }

    @Test
    fun `without the lock any sign-in is allowed`() {
        assertEquals(KidsLockSignIn.Allowed(kid), signIn(locked = false, existingUser = kid, signedInUserId = adult.id))
        assertEquals(KidsLockSignIn.Allowed(null), signIn(locked = false, existingUser = null, signedInUserId = adult.id))
    }
}
