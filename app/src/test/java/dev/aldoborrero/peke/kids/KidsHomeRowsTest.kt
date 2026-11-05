package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidsHomeSection
import dev.aldoborrero.peke.kids.data.KidsHomeSectionEntry
import dev.aldoborrero.peke.kids.home.KidsHomeRows
import dev.aldoborrero.peke.services.viewOptionsForCollectionType
import dev.aldoborrero.peke.ui.main.settings.Library
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
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

    @Test
    fun `the kids keep watching row asks for one card per series`() {
        val row = KidsHomeRows.build(KidProfile(userId = 1), listOf(cartoons)).rows[0]
        assertTrue((row as HomeRowConfig.ContinueWatchingCombined).onePerSeries)
        assertFalse(HomeRowConfig.ContinueWatchingCombined().onePerSeries)
    }

    @Test
    fun `keep watching keeps each series' last played episode where the series first shows`() {
        val george = UUID.randomUUID()
        val peppa = UUID.randomUUID()
        val now = LocalDateTime.of(2026, 11, 4, 18, 0)

        fun item(
            name: String,
            series: UUID?,
            played: LocalDateTime? = null,
            percent: Double? = null,
        ) = BaseItem(
            BaseItemDto(
                id = UUID.randomUUID(),
                name = name,
                type = if (series == null) BaseItemKind.MOVIE else BaseItemKind.EPISODE,
                seriesId = series,
                userData =
                    UserItemDataDto(
                        playbackPositionTicks = 0L,
                        playCount = 0,
                        isFavorite = false,
                        lastPlayedDate = played,
                        playedPercentage = percent,
                        played = false,
                        key = "",
                        itemId = UUID.randomUUID(),
                    ),
            ),
        )
        val items =
            listOf(
                item("George 5, next up", george),
                item("Film", null, now.minusHours(1)),
                item("Peppa 2, half", peppa, now.minusDays(1), 50.0),
                item("George 4, yesterday", george, now.minusDays(1), 30.0),
                item("George 6, an hour ago", george, now.minusHours(1), 10.0),
                item("Peppa 3, same time, further along", peppa, now.minusDays(1), 80.0),
                item("Another film", null),
            )
        assertEquals(
            listOf("George 6, an hour ago", "Film", "Peppa 3, same time, further along", "Another film"),
            KidsHomeRows.onePerSeries(items).map { it.name },
        )
        // Nothing to drop: the row stays as it came
        val films = listOf(item("Film", null), item("Another film", null))
        assertEquals(films, KidsHomeRows.onePerSeries(films))
    }
}
