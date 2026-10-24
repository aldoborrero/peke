package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.test.item
import dev.aldoborrero.peke.ui.main.clickDestination
import dev.aldoborrero.peke.ui.main.playKeyDestination
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.util.ResStringProvider
import dev.aldoborrero.peke.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/**
 * Spec 005 FR-001: in kids mode the Play key goes where OK goes
 */
class KidsPlayKeyTest {
    private val items = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.SERIES).map { BaseItem(item(it)) }

    private fun row(config: HomeRowConfig) = HomeRowLoadingState.Success(ResStringProvider(0), emptyList(), rowType = config)

    @Test
    fun `on the home the play key follows the click rule`() {
        val rows =
            listOf(
                row(HomeRowConfig.ContinueWatching()),
                row(HomeRowConfig.NextUp()),
                row(HomeRowConfig.ContinueWatchingCombined()),
                row(HomeRowConfig.ByParent(parentId = UUID.randomUUID())),
                null,
            )
        rows.forEach { row ->
            items.forEach {
                assertEquals(
                    "${row?.rowType} ${it.type}",
                    row.clickDestination(it, clickToPlay = true, kidsMode = true),
                    row.playKeyDestination(it, clickToPlay = true, kidsMode = true),
                )
            }
        }
    }

    @Test
    fun `outside keep watching the play key opens the details page`() {
        items.forEach {
            assertEquals(it.destination(), row(HomeRowConfig.ByParent(parentId = UUID.randomUUID())).playKeyDestination(it, true, true))
        }
    }

    @Test
    fun `grown-ups keep playing from the play key`() {
        items.forEach {
            assertEquals(
                Destination.Playback(it),
                row(HomeRowConfig.ByParent(parentId = UUID.randomUUID())).playKeyDestination(it, false, false),
            )
        }
    }

    @Test
    fun `in grids the play key opens the details page`() {
        items.forEach { assertEquals(it.destination(), kidsPlayKeyDestination(it)) }
    }
}
