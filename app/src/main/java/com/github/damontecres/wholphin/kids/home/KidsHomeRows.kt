package com.github.damontecres.wholphin.kids.home

import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.SUPPORTED_HOME_PAGE_SETTINGS_VERSION
import com.github.damontecres.wholphin.kids.data.KidProfile
import com.github.damontecres.wholphin.kids.data.KidsHomeSection
import com.github.damontecres.wholphin.services.viewOptionsForCollectionType
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.main.settings.Library
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

/**
 * Builds the fixed kids home from Wholphin's own row types and default view options
 */
object KidsHomeRows {
    /**
     * Library types that make sense as a kids row
     */
    private val LIBRARY_TYPES =
        setOf(
            CollectionType.MOVIES,
            CollectionType.TVSHOWS,
            CollectionType.HOMEVIDEOS,
            CollectionType.MUSICVIDEOS,
            CollectionType.UNKNOWN,
        )

    /**
     * @param liveTv whether the kid has live TV channels, which adds a "Watch live" row of what is on now right after
     * keep watching (or first, if that is hidden)
     */
    fun build(
        profile: KidProfile,
        libraries: List<Library>,
        liveTv: Boolean = false,
    ): HomePageSettings {
        val kidsLibraries = libraries.filter { it.collectionType in LIBRARY_TYPES && !it.isRecordingFolder }
        val rows =
            profile.sections
                .filter { it.visible }
                .flatMap { entry ->
                    when (entry.section) {
                        KidsHomeSection.KEEP_WATCHING -> {
                            listOf(HomeRowConfig.ContinueWatchingCombined())
                        }

                        KidsHomeSection.PICKED -> {
                            profile.pickedItemIds.map { HomeRowConfig.ByParent(parentId = it) }
                        }

                        KidsHomeSection.LIBRARIES -> {
                            kidsLibraries.map { library ->
                                HomeRowConfig.ByParent(
                                    parentId = library.itemId,
                                    sort = SortAndDirection(ItemSortBy.SORT_NAME, SortOrder.ASCENDING),
                                    viewOptions = viewOptionsForCollectionType(library.collectionType),
                                )
                            }
                        }

                        // Same as the regular default home: the server's latest media per library
                        KidsHomeSection.NEW -> {
                            kidsLibraries.map { library ->
                                HomeRowConfig.RecentlyAdded(
                                    parentId = library.itemId,
                                    viewOptions = viewOptionsForCollectionType(library.collectionType),
                                )
                            }
                        }

                        KidsHomeSection.FAVOURITES -> {
                            listOf(
                                HomeRowConfig.Favorite(kind = BaseItemKind.SERIES),
                                HomeRowConfig.Favorite(kind = BaseItemKind.MOVIE),
                            )
                        }
                    }
                }
        val withLiveTv =
            if (liveTv) {
                // 0 when keep watching is hidden
                val at = rows.indexOfFirst { it is HomeRowConfig.ContinueWatchingCombined } + 1
                rows.toMutableList().apply { add(at, HomeRowConfig.TvPrograms()) }
            } else {
                rows
            }
        return HomePageSettings(rows = withLiveTv, version = SUPPORTED_HOME_PAGE_SETTINGS_VERSION)
    }
}
