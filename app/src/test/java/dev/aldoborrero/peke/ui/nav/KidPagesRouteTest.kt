package dev.aldoborrero.peke.ui.nav

import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.data.KidProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/**
 * A user who isn't a kid, or whom the server couldn't answer for, goes back to the launch rule: there is no grown-up
 * shell any more (spec 004)
 */
class KidPagesRouteTest {
    private val kid = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "kid", serverId = UUID.randomUUID(), accessToken = "k")

    @Test
    fun `a kid gets their pages`() {
        val active = KidsModeState.Active(KidProfile(userId = kid.rowId), kid)
        assertEquals(KidPagesRoute.SHOW, kidPagesRoute(active, unanswered = false))
        assertEquals(KidPagesRoute.SHOW, kidPagesRoute(active, unanswered = true))
    }

    @Test
    fun `nothing shows while it isn't known whether the user is a kid`() {
        assertEquals(KidPagesRoute.WAIT, kidPagesRoute(KidsModeState.Loading, unanswered = false))
    }

    @Test
    fun `an unanswered user goes through the launch rule`() {
        assertEquals(KidPagesRoute.LAUNCH_RULE, kidPagesRoute(KidsModeState.Loading, unanswered = true))
    }

    @Test
    fun `a user the server says isn't a kid goes through the launch rule`() {
        assertEquals(KidPagesRoute.LAUNCH_RULE, kidPagesRoute(KidsModeState.Inactive, unanswered = false))
        assertEquals(KidPagesRoute.LAUNCH_RULE, kidPagesRoute(KidsModeState.Inactive, unanswered = true))
    }
}
