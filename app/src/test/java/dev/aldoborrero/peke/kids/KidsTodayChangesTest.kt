package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.gate.KidsAuth
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsBonus
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.server.KidsServerState
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.server.KidsStatus
import dev.aldoborrero.peke.kids.timer.BonusOutcome
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.TodayChange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/**
 * Undoing a bonus and ending a break (plugin 0.0.9.0, Kids client API 1.2): what each answer means for the grown-up
 */
class KidsTodayChangesTest {
    private val serverId = UUID.randomUUID()
    private val server = JellyfinServer(serverId, "home", "http://jf.local", null)
    private val leo = JellyfinUser(rowId = 1, id = UUID.randomUUID(), name = "Leo", serverId = serverId, accessToken = "t1")
    private val api = mockk<KidsApi>()
    private val lock =
        mockk<ParentLockService>(relaxed = true) {
            every { auth() } returns KidsAuth(server, leo, "t1", "g-1")
        }

    private fun TestScope.service(features: String = "$KIDS_FEATURES,allowance,bonusUndo,breakEnd"): KidsAllowanceService {
        val plugin =
            mockk<KidsPluginService> {
                every { current } returns MutableStateFlow(KidsServerState.ABSENT)
                coEvery { state(serverId) } returns KidsServerState(KidsServer(serverId, 1, features), KidsServerStatus.AVAILABLE)
            }
        return KidsAllowanceService(
            mockk<ServerRepository>(relaxed = true),
            mockk(relaxed = true) { every { state } returns MutableStateFlow(dev.aldoborrero.peke.kids.KidsModeState.Inactive) },
            plugin,
            lock,
            api,
            mockk(relaxed = true),
            FakeClock(),
            backgroundScope,
        )
    }

    @Test
    fun `a bonus keeps its id and the server's undo window only when the plugin can undo`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { api.bonus(any(), any(), "g-1", leo.id, 15) } returns KidsResult.Ok(KidsBonus(KidsStatus(), 42, 590))
            assertEquals(BonusOutcome(BonusResult.ADDED, 42, 590), service().addBonus(15))
            assertEquals(BonusOutcome(BonusResult.ADDED, null, 590), service(KIDS_FEATURES).addBonus(15))
        }

    @Test
    fun `undo answers`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            coEvery { api.undoBonus(any(), any(), "g-1", leo.id, 42) } returns KidsResult.Ok(KidsStatus(timeUp = true))
            assertEquals(TodayChange.DONE, service.undoBonus(42))

            // "No such bonus.": undone on another TV or cleared by Reset today
            coEvery { api.undoBonus(any(), any(), any(), any(), 42) } returns KidsResult.NotFound
            assertEquals(TodayChange.ALREADY_UNDONE, service.undoBonus(42))

            // "This bonus can no longer be undone."
            coEvery { api.undoBonus(any(), any(), any(), any(), 42) } returns KidsResult.Conflict(null)
            assertEquals(TodayChange.TOO_LATE, service.undoBonus(42))

            coEvery { api.undoBonus(any(), any(), any(), any(), 42) } returns KidsResult.NotAKid
            assertEquals(TodayChange.FAILED, service.undoBonus(42))

            coEvery { api.undoBonus(any(), any(), any(), any(), 42) } returns KidsResult.Forbidden
            assertEquals(TodayChange.NEEDS_GRANT, service.undoBonus(42))
            verify { lock.dropGrant() }
        }

    @Test
    fun `ending a break`() =
        runTest(UnconfinedTestDispatcher()) {
            val service = service()
            coEvery { api.endBreak(any(), any(), "g-1", leo.id) } returns KidsResult.Ok(KidsStatus())
            assertEquals(TodayChange.DONE, service.endBreak())
            // A plugin without the route: nothing was ended
            coEvery { api.endBreak(any(), any(), any(), any()) } returns KidsResult.NotFound
            assertEquals(TodayChange.FAILED, service.endBreak())
            coEvery { api.endBreak(any(), any(), any(), any()) } returns KidsResult.Unreachable
            assertEquals(TodayChange.UNREACHABLE, service.endBreak())
        }
}
