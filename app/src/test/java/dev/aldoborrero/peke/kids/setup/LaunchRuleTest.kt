package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/**
 * One case for each row of the first-screen table in `contracts/tv-flows.md`
 */
class LaunchRuleTest {
    private val server = JellyfinServer(id = UUID.randomUUID(), name = "home", url = "http://jf.local", version = null)

    private fun kid(rowId: Int) =
        JellyfinUser(rowId = rowId, id = UUID.randomUUID(), name = "kid$rowId", serverId = server.id, accessToken = "t$rowId")

    @Test
    fun `loading shows the starting page, never a list`() {
        assertEquals(LaunchDestination.Starting, LaunchRule.destination(TvState.Loading))
    }

    @Test
    fun `not set up starts setup at the server step`() {
        assertEquals(LaunchDestination.Setup(TvSetupStep.SERVER), LaunchRule.destination(TvState.NotSetUp))
    }

    @Test
    fun `in setup resumes at its step`() {
        assertEquals(
            LaunchDestination.Setup(TvSetupStep.ADMIN),
            LaunchRule.destination(TvState.InSetup(TvSetupStep.ADMIN)),
        )
        // The administrator session is never stored: past the admin step, setup asks for a fresh sign-in itself
        assertEquals(
            LaunchDestination.Setup(TvSetupStep.KIDS),
            LaunchRule.destination(TvState.InSetup(TvSetupStep.KIDS)),
        )
    }

    @Test
    fun `set up with no kids shows that this TV has none`() {
        assertEquals(LaunchDestination.NoKids(server), LaunchRule.destination(TvState.SetUp(server, emptyList())))
    }

    @Test
    fun `set up with one kid opens that kid`() {
        val kid = kid(1)
        assertEquals(LaunchDestination.Kid(server, kid), LaunchRule.destination(TvState.SetUp(server, listOf(kid))))
    }

    @Test
    fun `set up with several kids opens the picker, whoever was last`() {
        val kids = listOf(kid(1), kid(2), kid(3))
        assertEquals(LaunchDestination.Picker(server, kids), LaunchRule.destination(TvState.SetUp(server, kids, lastKidRowId = 2)))
    }
}
