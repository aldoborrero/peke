package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsHomeSection
import dev.aldoborrero.peke.kids.data.KidsHomeSectionEntry
import dev.aldoborrero.peke.kids.home.KidsHomeRows
import dev.aldoborrero.peke.services.viewOptionsForCollectionType
import dev.aldoborrero.peke.ui.main.settings.Library
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KidsHomeRowsTest {
    private val cartoons = Library(UUID.randomUUID(), "Cartoons", BaseItemKind.COLLECTION_FOLDER, CollectionType.TVSHOWS, false)
    private val movies = Library(UUID.randomUUID(), "Movies", BaseItemKind.COLLECTION_FOLDER, CollectionType.MOVIES, false)
    private val liveTv = Library(UUID.randomUUID(), "Live TV", BaseItemKind.COLLECTION_FOLDER, CollectionType.LIVETV, false)
    private val music = Library(UUID.randomUUID(), "Music", BaseItemKind.COLLECTION_FOLDER, CollectionType.MUSIC, false)

    @Test
    fun `default order with picks and libraries`() {
        val pick = UUID.randomUUID()
        val rows =
            KidsHomeRows.build(KidProfile(userId = 1, pickedItemIds = listOf(pick)), listOf(cartoons, movies)).rows
        assertTrue(rows[0] is HomeRowConfig.ContinueWatchingCombined)
        assertEquals(pick, (rows[1] as HomeRowConfig.ByParent).parentId)
        val cartoonsRow = rows[2] as HomeRowConfig.ByParent
        assertEquals(cartoons.itemId, cartoonsRow.parentId)
        assertEquals(ItemSortBy.SORT_NAME, cartoonsRow.sort?.sort)
        assertEquals(movies.itemId, (rows[3] as HomeRowConfig.ByParent).parentId)
        assertEquals(cartoons.itemId, (rows[4] as HomeRowConfig.RecentlyAdded).parentId)
        assertEquals(movies.itemId, (rows[5] as HomeRowConfig.RecentlyAdded).parentId)
        assertEquals(BaseItemKind.SERIES, (rows[6] as HomeRowConfig.Favorite).kind)
        assertEquals(BaseItemKind.MOVIE, (rows[7] as HomeRowConfig.Favorite).kind)
        assertEquals(8, rows.size)
    }

    @Test
    fun `live tv and music libraries are left out`() {
        val rows = KidsHomeRows.build(KidProfile(userId = 1), listOf(liveTv, music, cartoons)).rows
        val parents = rows.filterIsInstance<HomeRowConfig.ByParent>().map { it.parentId }
        assertEquals(listOf(cartoons.itemId), parents)
    }

    @Test
    fun `hidden sections and custom order are respected`() {
        val profile =
            KidProfile(
                userId = 1,
                sections =
                    listOf(
                        KidsHomeSectionEntry(KidsHomeSection.NEW),
                        KidsHomeSectionEntry(KidsHomeSection.KEEP_WATCHING, visible = false),
                        KidsHomeSectionEntry(KidsHomeSection.PICKED),
                        KidsHomeSectionEntry(KidsHomeSection.LIBRARIES),
                        KidsHomeSectionEntry(KidsHomeSection.FAVOURITES, visible = false),
                    ),
            )
        val rows = KidsHomeRows.build(profile, listOf(cartoons)).rows
        assertTrue(rows[0] is HomeRowConfig.RecentlyAdded)
        assertTrue(rows[1] is HomeRowConfig.ByParent)
        assertEquals(2, rows.size)
    }

    @Test
    fun `live TV adds what is on now after keep watching`() {
        val rows = KidsHomeRows.build(KidProfile(userId = 1), listOf(cartoons), liveTv = true).rows
        assertTrue(rows[0] is HomeRowConfig.ContinueWatchingCombined)
        assertTrue(rows[1] is HomeRowConfig.TvPrograms)
        assertEquals(1, rows.count { it is HomeRowConfig.TvPrograms })
        // First when keep watching is hidden
        val profile =
            KidProfile(
                userId = 1,
                sections = KidsHomeSectionEntry.normalize(listOf(KidsHomeSectionEntry(KidsHomeSection.KEEP_WATCHING, visible = false))),
            )
        assertTrue(KidsHomeRows.build(profile, listOf(cartoons), liveTv = true).rows[0] is HomeRowConfig.TvPrograms)
        // Without channels, no live TV row
        assertTrue(KidsHomeRows.build(KidProfile(userId = 1), listOf(cartoons)).rows.none { it is HomeRowConfig.TvPrograms })
    }

    @Test
    fun `rows use the regular home view options`() {
        val rows = KidsHomeRows.build(KidProfile(userId = 1), listOf(cartoons)).rows
        val cartoonsRow = rows.filterIsInstance<HomeRowConfig.ByParent>().single()
        assertEquals(viewOptionsForCollectionType(CollectionType.TVSHOWS), cartoonsRow.viewOptions)
    }
}
