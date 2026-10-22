package dev.aldoborrero.peke.services

import android.content.Context
import com.mayakapps.kache.InMemoryKache
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.filter.CommunityRatingFilter
import dev.aldoborrero.peke.data.filter.DecadeFilter
import dev.aldoborrero.peke.data.filter.FavoriteFilter
import dev.aldoborrero.peke.data.filter.FilterBy
import dev.aldoborrero.peke.data.filter.FilterValueOption
import dev.aldoborrero.peke.data.filter.FilterVideoType
import dev.aldoborrero.peke.data.filter.GenreFilter
import dev.aldoborrero.peke.data.filter.OfficialRatingFilter
import dev.aldoborrero.peke.data.filter.PlayedFilter
import dev.aldoborrero.peke.data.filter.StudioFilter
import dev.aldoborrero.peke.data.filter.VideoTypeFilter
import dev.aldoborrero.peke.data.filter.YearFilter
import dev.aldoborrero.peke.services.hilt.IoCoroutineScope
import dev.aldoborrero.peke.ui.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.filterApi
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.yearsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

/**
 * Get the possible values for filters in a library
 */
@Singleton
class FilterOptionCache
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoCoroutineScope private val ioCoroutineScope: CoroutineScope,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        private val cache =
            InMemoryKache<FilterOptionCacheKey, List<FilterValueOption>>(16) {
                creationScope = ioCoroutineScope
                expireAfterWriteDuration = 1.hours
            }

        /**
         * Gets the possible values for a filter
         *
         * For example, the possible genres in the parent ID
         */
        suspend fun getFilterOptionValues(
            parentId: UUID?,
            filterOption: FilterBy<*, *>,
        ): List<FilterValueOption> {
            val cacheKey = FilterOptionCacheKey(serverRepository.currentUser?.id, parentId, filterOption)
            return try {
                cache
                    .getOrPut(cacheKey) { (userId, parentId, filterOption) ->
                        getFilterOptionValues(userId, parentId, filterOption)
                    }.orEmpty()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error fetching options for %s", filterOption)
                showToast(context, "Error occurred: ${ex.localizedMessage}")
                emptyList()
            }
        }

        private suspend fun getFilterOptionValues(
            userId: UUID?,
            parentId: UUID?,
            filterOption: FilterBy<*, *>,
        ) = when (filterOption) {
            GenreFilter -> {
                api.genresApi
                    .getGenres(
                        parentId = parentId,
                        userId = userId,
                    ).content.items
                    .map { FilterValueOption(it.name ?: "", it.id) }
            }

            StudioFilter -> {
                api.studiosApi
                    .getStudios(
                        parentId = parentId,
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.SERIES),
                    ).content.items
                    .map { FilterValueOption(it.name ?: "", it.id) }
            }

            FavoriteFilter,
            PlayedFilter,
            -> {
                listOf(
                    FilterValueOption("True", null),
                    FilterValueOption("False", null),
                )
            }

            OfficialRatingFilter -> {
                val ratings =
                    api.localizationApi
                        .getParentalRatings()
                        .content
                        .associate {
                            it.name to it.value
                        }
                api.filterApi
                    .getQueryFiltersLegacy(
                        parentId = parentId,
                        userId = userId,
                    ).content.officialRatings
                    ?.map { r ->
                        val value = ratings[r] ?: 0
                        FilterValueOption(r, value)
                    }?.sortedBy { it.value as Int }
                    .orEmpty()
            }

            VideoTypeFilter -> {
                FilterVideoType.entries.map {
                    FilterValueOption(it.readable, it)
                }
            }

            YearFilter -> {
                api.yearsApi
                    .getYears(
                        parentId = parentId,
                        userId = userId,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                    ).content.items
                    .mapNotNull {
                        it.name?.toIntOrNull()?.let { FilterValueOption(it.toString(), it) }
                    }
            }

            DecadeFilter -> {
                val items = TreeSet<Int>()
                api.yearsApi
                    .getYears(
                        parentId = parentId,
                        userId = userId,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                    ).content.items
                    .mapNotNullTo(items) {
                        it.name
                            ?.toIntOrNull()
                            ?.div(10)
                            ?.times(10)
                    }
                items.toList().sorted().map { FilterValueOption("$it's", it) }
            }

            CommunityRatingFilter -> {
                (1..10).map {
                    FilterValueOption("$it", it)
                }
            }
        }

        private data class FilterOptionCacheKey(
            val userId: UUID?,
            val parentId: UUID?,
            val filterOption: FilterBy<*, *>,
        )
    }
