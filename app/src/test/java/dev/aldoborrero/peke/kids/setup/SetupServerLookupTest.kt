package dev.aldoborrero.peke.kids.setup

import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Spec 005 FR-014: HTTPS whenever it answers, plain HTTP only when nothing else does, and then flagged
 */
class SetupServerLookupTest {
    private val id = UUID.randomUUID()

    private fun result(
        address: String,
        score: RecommendedServerInfoScore,
        setUp: Boolean = true,
    ) = RecommendedServerInfo(
        address = address,
        responseTime = 10,
        score = score,
        issues = emptyList(),
        systemInfo =
            Result.success(
                PublicSystemInfo(id = id.toString(), serverName = "home", version = "10.11.0", startupWizardCompleted = setUp),
            ),
    )

    @Test
    fun `https wins over a better scored http`() {
        val found =
            SetupServerLookup.pick(
                listOf(
                    result("http://jf.local:8096", RecommendedServerInfoScore.GREAT),
                    result("https://jf.local", RecommendedServerInfoScore.GOOD),
                ),
            ) as ServerLookup.Found
        assertEquals("https://jf.local", found.server.url)
        assertFalse(found.insecure)
        assertEquals(id, found.server.id)
    }

    @Test
    fun `the best score wins inside a scheme`() {
        val found =
            SetupServerLookup.pick(
                listOf(
                    result("https://jf.local:8920", RecommendedServerInfoScore.OK),
                    result("https://jf.local", RecommendedServerInfoScore.GREAT),
                ),
            ) as ServerLookup.Found
        assertEquals("https://jf.local", found.server.url)
    }

    @Test
    fun `only http answering is flagged as insecure`() {
        val found =
            SetupServerLookup.pick(
                listOf(
                    result("http://jf.local:8096", RecommendedServerInfoScore.GOOD),
                    result("http://jf.local", RecommendedServerInfoScore.OK),
                    result("https://jf.local", RecommendedServerInfoScore.BAD),
                ),
            ) as ServerLookup.Found
        assertEquals("http://jf.local:8096", found.server.url)
        assertTrue(found.insecure)
    }

    @Test
    fun `bad answers and servers not set up are ignored`() {
        assertEquals(ServerLookup.NotFound, SetupServerLookup.pick(listOf(result("https://jf.local", RecommendedServerInfoScore.BAD))))
        assertEquals(
            ServerLookup.NotFound,
            SetupServerLookup.pick(listOf(result("https://jf.local", RecommendedServerInfoScore.GREAT, setUp = false))),
        )
        assertEquals(ServerLookup.NotFound, SetupServerLookup.pick(emptyList()))
    }
}
