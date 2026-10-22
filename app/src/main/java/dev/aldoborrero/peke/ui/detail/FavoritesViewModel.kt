package dev.aldoborrero.peke.ui.detail

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.LibraryDisplayInfoDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.filter.FilterValueOption
import dev.aldoborrero.peke.data.filter.ItemFilterBy
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.GetItemsFilter
import dev.aldoborrero.peke.data.model.LibraryDisplayInfo
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.FavoriteWatchManager
import dev.aldoborrero.peke.services.FilterOptionCache
import dev.aldoborrero.peke.services.MediaManagementService
import dev.aldoborrero.peke.services.NavDrawerService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.RememberedTabService
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.StreamChoiceService
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.services.deleteItem
import dev.aldoborrero.peke.ui.SlimItemFields
import dev.aldoborrero.peke.ui.collectLatestIn
import dev.aldoborrero.peke.ui.components.CollectionFolderState
import dev.aldoborrero.peke.ui.components.CollectionFolderViewActions
import dev.aldoborrero.peke.ui.components.ContextMenuProvider
import dev.aldoborrero.peke.ui.components.TabDetails
import dev.aldoborrero.peke.ui.components.ViewOptions
import dev.aldoborrero.peke.ui.components.baseItemKinds
import dev.aldoborrero.peke.ui.components.defaultViewOptions
import dev.aldoborrero.peke.ui.data.SortAndDirection
import dev.aldoborrero.peke.ui.equalsNotNull
import dev.aldoborrero.peke.ui.formatTypeName
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.main.settings.Library
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.nav.NavDrawerItem
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.util.ApiRequestPager
import dev.aldoborrero.peke.util.BlockingList
import dev.aldoborrero.peke.util.DataLoadingState
import dev.aldoborrero.peke.util.ExceptionHandler
import dev.aldoborrero.peke.util.GetArtistsHandler
import dev.aldoborrero.peke.util.GetItemsRequestHandler
import dev.aldoborrero.peke.util.GetPersonsHandler
import dev.aldoborrero.peke.util.LoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.successValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import timber.log.Timber
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val navigationManager: NavigationManager,
        private val serverRepository: ServerRepository,
        private val navDrawerService: NavDrawerService,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        val streamChoiceService: StreamChoiceService,
        val serverReportService: ServerReportService,
        private val filterOptionCache: FilterOptionCache,
        private val rememberedTabService: RememberedTabService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(FavoritesPageState())
        val state: StateFlow<FavoritesPageState> = _state

        fun init() {
            viewModelScope.launchIO {
                val rememberTabs =
                    userPreferencesService
                        .getCurrent()
                        .appPreferences.interfacePreferences.rememberSelectedTab

                setupPossibleTypes()

                val favoriteTypeOptions = state.value.favoriteTypeOptions
                if (favoriteTypeOptions.isNotEmpty()) {
                    val tabKey =
                        if (rememberTabs) {
                            val rememberedTab =
                                rememberedTabService
                                    .getRememberedTab(NavDrawerItem.Favorites.id)
                                    ?.let {
                                        BaseItemKind.entries[it]
                                    }
                            if (rememberedTab != null && rememberedTab in favoriteTypeOptions) {
                                rememberedTab
                            } else {
                                favoriteTypeOptions.first()
                            }
                        } else {
                            favoriteTypeOptions.first()
                        }
                    val jobs =
                        favoriteTypeOptions.associateWith { type -> initialFetchForType(type) }
                    initialFetchAndWait(tabKey, jobs)
                } else {
                    Timber.w("No available types to check for favorites!")
                    _state.update {
                        it.copy(loadingState = FavoritesLoadingState.NoFavorites)
                    }
                }

                userPreferencesService.flow
                    .map { it.appPreferences.interfacePreferences.showClock }
                    .collectLatestIn(viewModelScope) { isShowClock ->
                        _state.update { it.copy(isShowClock = isShowClock) }
                    }
            }
        }

        @VisibleForTesting
        internal fun setupPossibleTypes() {
            val availableTypes =
                navDrawerService.state.value.allLibraries
                    .possibleFavoriteTypes(false)
                    .toSet()
            val favoriteTypeOptions = favoriteOptions.filter { it in availableTypes }
            _state.update {
                it.copy(
                    favoriteTypeOptions = favoriteTypeOptions,
                )
            }
        }

        /**
         * Starts queries for favorites, waiting for [firstTabKey] to complete
         *
         * Exposed for testing
         */
        internal suspend fun initialFetchAndWait(
            firstTabKey: BaseItemKind,
            jobs: Map<BaseItemKind, Deferred<DataLoadingState<List<BaseItem?>>>>,
        ) {
            val completed =
                jobs[firstTabKey]?.let {
                    when (val result = it.await()) {
                        DataLoadingState.Loading,
                        DataLoadingState.Pending,
                        -> false

                        is DataLoadingState.Error,
                        -> true

                        is DataLoadingState.Success<List<BaseItem?>> -> result.data.isNotEmpty()
                    }
                } == true
            if (completed) {
                Timber.v("Got completed for %s", firstTabKey)
                _state.update {
                    it.copy(
                        loadingState = FavoritesLoadingState.Success,
                        tabKey = firstTabKey,
                    )
                }
            } else {
                // If the first key wasn't successful, wait in order until first successful & non-empty
                val favoriteTypeOptions = state.value.favoriteTypeOptions
                val firstCompleted =
                    favoriteTypeOptions.firstOrNull { type ->
                        val job = jobs[type] ?: return@firstOrNull false
                        val result = job.await()
                        result is DataLoadingState.Error ||
                            (result is DataLoadingState.Success<List<BaseItem?>> && result.data.isNotEmpty())
                    }
                _state.update {
                    if (firstCompleted != null) {
                        it.copy(
                            loadingState = FavoritesLoadingState.Success,
                            tabKey = firstCompleted,
                        )
                    } else {
                        // None were error or successful & non-empty
                        // Check if they were all empty which means the user has no favorites at all
                        val allEmpty =
                            jobs.all { (_, job) ->
                                val result = job.await()
                                result is DataLoadingState.Success<List<BaseItem?>> && result.data.isEmpty()
                            }
                        if (allEmpty) {
                            it.copy(
                                loadingState = FavoritesLoadingState.NoFavorites,
                            )
                        } else {
                            // This means a least one failed
                            val firstError =
                                jobs.entries.firstOrNull { (_, job) ->
                                    job.await() is DataLoadingState.Error
                                }
                            if (firstError != null) {
                                it.copy(
                                    loadingState = FavoritesLoadingState.Success,
                                    tabKey = firstError.key,
                                )
                            } else {
                                // This shouldn't happen
                                it.copy(
                                    loadingState = FavoritesLoadingState.Error("An unknown error occurred"),
                                )
                            }
                        }
                    }
                }
            }
        }

        /**
         * Do the initial fetch for a [BaseItemKind] which will look up any existing saved sort and filter
         */
        private fun initialFetchForType(type: BaseItemKind): Deferred<DataLoadingState<List<BaseItem?>>> =
            viewModelScope.async(PekeDispatchers.IO) {
                try {
                    val collectionState =
                        CollectionFolderState(
                            item = DataLoadingState.Loading,
                            items = DataLoadingState.Loading,
                            backgroundLoading = LoadingState.Loading,
                            viewOptions = ViewOptions(),
                        )
                    _state.value.favorites[type] = collectionState

                    val libraryDisplayInfo =
                        serverRepository.currentUser?.let { user ->
                            libraryDisplayInfoDao.getItem(user, libraryDisplayItemId(type))
                        }
                    val sortAndDirection =
                        libraryDisplayInfo?.sortAndDirection ?: SortAndDirection.DEFAULT
                    val filter = libraryDisplayInfo?.filter ?: GetItemsFilter(favorite = true)
                    val viewOptions = libraryDisplayInfo?.viewOptions ?: type.defaultViewOptions
                    fetchType(type, sortAndDirection, filter, viewOptions)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching favorites for %s", type)
                    updateForException(type, ex)
                    DataLoadingState.Error(ex)
                }
            }

        /**
         * Query for favorites of the specified [BaseItemKind] using the specified sort and filter
         */
        @VisibleForTesting
        internal suspend fun fetchType(
            type: BaseItemKind,
            sortAndDirection: SortAndDirection,
            filter: GetItemsFilter,
            viewOptions: ViewOptions,
        ): DataLoadingState<List<BaseItem?>> {
            val collectionState =
                CollectionFolderState(
                    item = DataLoadingState.Loading,
                    items = DataLoadingState.Loading,
                    backgroundLoading = LoadingState.Loading,
                    viewOptions = ViewOptions(),
                )
            _state.value.favorites[type] = collectionState
            try {
                val pager = createPager(type, filter, sortAndDirection)
                _state.value.favorites[type] =
                    collectionState.copy(
                        item = DataLoadingState.Success(null),
                        items = DataLoadingState.Success(pager),
                        backgroundLoading = LoadingState.Success,
                        sortAndDirection = sortAndDirection,
                        filter = filter,
                        viewOptions = viewOptions,
                    )
                Timber.v("Got %s favorites for %s", pager.size, type)
                _state.update {
                    val newTabs =
                        it.createMap {
                            if (pager.isNotEmpty()) {
                                put(type, TabDetails(formatTypeName(type)))
                            } else {
                                remove(type)
                            }
                        }
                    it.copy(
                        tabs = newTabs,
                    )
                }
                return DataLoadingState.Success(pager)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                updateForException(type, ex)
                return DataLoadingState.Error(ex)
            }
        }

        /**
         * If fetching data for a type fails, this should be called to update the state with the error
         */
        private fun updateForException(
            type: BaseItemKind,
            ex: Exception,
        ) {
            _state.update {
                // Want to store the error for the tab
                _state.value.favorites[type] =
                    CollectionFolderState(
                        item = DataLoadingState.Success(null),
                        items = DataLoadingState.Error(ex),
                        backgroundLoading = LoadingState.Success,
                        viewOptions = type.defaultViewOptions,
                    )
                // And make sure the tab is included
                val newTabs =
                    it.createMap {
                        put(type, TabDetails(formatTypeName(type)))
                    }
                it.copy(tabs = newTabs)
            }
        }

        private fun createGetItemsRequest(
            type: BaseItemKind,
            filter: GetItemsFilter,
            sortAndDirection: SortAndDirection,
        ): GetItemsRequest =
            filter.applyTo(
                GetItemsRequest(
                    isFavorite = true,
                    recursive = true,
                    includeItemTypes = listOf(type),
                    fields = SlimItemFields,
                    sortBy = listOf(sortAndDirection.sort),
                    sortOrder = listOf(sortAndDirection.direction),
                ),
                overwriteIncludeTypes = false,
            )

        private fun createGetArtistsRequest(
            filter: GetItemsFilter,
            sortAndDirection: SortAndDirection,
        ): GetArtistsRequest =
            filter.applyTo(
                GetArtistsRequest(
                    isFavorite = true,
                    fields = SlimItemFields,
                    sortBy = listOf(sortAndDirection.sort),
                    sortOrder = listOf(sortAndDirection.direction),
                ),
            )

        private fun createGetPersonsRequest(filter: GetItemsFilter): GetPersonsRequest =
            filter.applyTo(
                GetPersonsRequest(
                    isFavorite = true,
                    fields = SlimItemFields,
                ),
            )

        private suspend fun createPager(
            type: BaseItemKind,
            filter: GetItemsFilter,
            sortAndDirection: SortAndDirection,
        ): ApiRequestPager<*> =
            when (type) {
                BaseItemKind.MUSIC_ARTIST -> {
                    val request = createGetArtistsRequest(filter, sortAndDirection)
                    ApiRequestPager(api, request, GetArtistsHandler, viewModelScope, pageSize = 50)
                }

                BaseItemKind.PERSON -> {
                    val request = createGetPersonsRequest(filter)
                    ApiRequestPager(api, request, GetPersonsHandler, viewModelScope, pageSize = 50)
                }

                else -> {
                    val request = createGetItemsRequest(type, filter, sortAndDirection)
                    ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope, pageSize = 50)
                }
            }.init()

        fun libraryDisplayItemId(type: BaseItemKind): String {
            // Previous version used hardcoded keys before more types were supported
            // So override those type names so that user's settings are maintained
            val typeKey =
                when (type) {
                    BaseItemKind.MOVIE -> "movies"
                    BaseItemKind.SERIES -> "series"
                    BaseItemKind.EPISODE -> "episodes"
                    BaseItemKind.VIDEO -> "videos"
                    BaseItemKind.PLAYLIST -> "playlists"
                    BaseItemKind.PERSON -> "people"
                    else -> type.serialName
                }
            return "${NavDrawerItem.Favorites.id}_$typeKey"
        }

        private fun collectionStateFor(type: BaseItemKind): CollectionFolderState? = state.value.favorites[type]

        fun updateSelectedTabKey(newKey: BaseItemKind) {
            viewModelScope.launchDefault { backdropService.clearBackdrop() }
            viewModelScope.launchIO {
                val rememberTabs =
                    userPreferencesService
                        .getCurrent()
                        .appPreferences.interfacePreferences.rememberSelectedTab
                if (rememberTabs) {
                    rememberedTabService.saveRememberedTab(
                        NavDrawerItem.Favorites.id,
                        newKey.ordinal,
                    )
                }
            }
            _state.update { it.copy(tabKey = newKey) }
        }

        private suspend fun refreshAfterMutate(
            type: BaseItemKind,
            position: Int,
            itemId: UUID,
        ) {
            try {
                val pager =
                    collectionStateFor(type)?.let {
                        ((it.items as? DataLoadingState.Success)?.data as? ApiRequestPager<*>)
                    }
                pager?.refreshItem(position, itemId)
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing after mutate %s", itemId)
                showToast(context, "Error refreshing after item mutate")
            }
        }

        private suspend fun refreshAfterDelete(
            type: BaseItemKind,
            position: Int,
            deletedItem: BaseItem,
        ) {
            try {
                val pager =
                    collectionStateFor(type)?.let {
                        ((it.items as? DataLoadingState.Success)?.data as? ApiRequestPager<*>)
                    }

                position.let {
                    Timber.v("Item deleted: position=%s, id=%s", it, deletedItem.id)
                    val item = pager?.get(it)
                    // Exact item deleted (eg a movie) or deleted item was within the series
                    if (item?.id == deletedItem.id ||
                        equalsNotNull(item?.data?.id, deletedItem.data.seriesId)
                    ) {
                        pager?.refreshPagesAfter(position)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing after deleted item %s", deletedItem.id)
                showToast(context, "Error refreshing after item deleted")
            }
        }

        private fun saveLibraryDisplayInfo(
            type: BaseItemKind,
            newFilter: GetItemsFilter,
            newSort: SortAndDirection,
            viewOptions: ViewOptions?,
        ) {
            serverRepository.currentUser?.let { user ->
                viewModelScope.launchIO {
                    val libraryDisplayInfo =
                        LibraryDisplayInfo(
                            userId = user.rowId,
                            itemId = libraryDisplayItemId(type),
                            sort = newSort.sort,
                            direction = newSort.direction,
                            filter = newFilter,
                            viewOptions = viewOptions,
                        )
                    libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                }
            }
        }

        private fun saveViewOptions(
            type: BaseItemKind,
            viewOptions: ViewOptions,
        ) {
            viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                collectionStateFor(type)?.let { collectionState ->
                    saveLibraryDisplayInfo(
                        type = type,
                        newFilter = collectionState.filter,
                        newSort = collectionState.sortAndDirection,
                        viewOptions = viewOptions,
                    )
                    state.value.favorites[type] =
                        collectionState.copy(
                            viewOptions = viewOptions,
                        )
                    if (!viewOptions.showBackdrop) {
                        backdropService.clearBackdrop()
                    }
                }
            }
        }

        fun createTypedProvider(type: BaseItemKind) = TypedProvider(type)

        /**
         * This provides context menu actions and other actions on a per [BaseItemKind] basis
         */
        @Immutable
        inner class TypedProvider(
            val type: BaseItemKind,
        ) : ContextMenuProvider,
            CollectionFolderViewActions {
            override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

            override fun navigateTo(destination: Destination) = navigationManager.navigateTo(destination)

            override fun canDelete(
                item: BaseItem,
                appPreferences: AppPreferences,
            ): Boolean = mediaManagementService.canDelete(item, appPreferences)

            override fun deleteItem(
                index: Int,
                item: BaseItem,
            ) {
                deleteItem(context, mediaManagementService, item) {
                    viewModelScope.launchDefault {
                        refreshAfterDelete(type, index, item)
                    }
                }
            }

            override fun setWatched(
                position: Int,
                itemId: UUID,
                played: Boolean,
            ) {
                viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                    favoriteWatchManager.setWatched(itemId, played)
                    refreshAfterMutate(type, position, itemId)
                }
            }

            override fun setFavorite(
                position: Int,
                itemId: UUID,
                favorite: Boolean,
            ) {
                viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                    favoriteWatchManager.setWatched(itemId, favorite)
                    collectionStateFor(type)?.let { collectionState ->
                        // TODO use pager.refreshPagesAfter ??
                        fetchType(
                            type,
                            collectionState.sortAndDirection,
                            collectionState.filter,
                            collectionState.viewOptions,
                        )
                    }
                }
            }

            override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)

            override fun updateBackdrop(item: BaseItem) {
                viewModelScope.launchIO {
                    backdropService.submit(item)
                }
            }

            override fun onSortChange(
                sortAndDirection: SortAndDirection,
                recursive: Boolean,
                filter: GetItemsFilter,
            ) {
                viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                    Timber.v("onSortChange: type=%s, sortAndDirection=%s", type, sortAndDirection)
                    collectionStateFor(type)?.let { collectionState ->
                        saveLibraryDisplayInfo(
                            type = type,
                            newFilter = filter,
                            newSort = sortAndDirection,
                            viewOptions = collectionState.viewOptions,
                        )
                        state.value.favorites[type] =
                            collectionState.copy(
                                filter = filter,
                                sortAndDirection = sortAndDirection,
                            )
                        fetchType(type, sortAndDirection, filter, collectionState.viewOptions)
                    }
                }
            }

            override fun onFilterChange(
                newFilter: GetItemsFilter,
                recursive: Boolean,
            ) {
                viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                    Timber.v("onFilterChange: type=%s, newFilter=%s", type, newFilter)
                    collectionStateFor(type)?.let { collectionState ->
                        saveLibraryDisplayInfo(
                            type = type,
                            newFilter = newFilter,
                            newSort = collectionState.sortAndDirection,
                            viewOptions = collectionState.viewOptions,
                        )
                        state.value.favorites[type] =
                            collectionState.copy(
                                filter = newFilter,
                            )
                        fetchType(
                            type,
                            collectionState.sortAndDirection,
                            newFilter,
                            collectionState.viewOptions,
                        )
                    }
                }
            }

            override suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
                // TODO use includeItemTypes?
                filterOptionCache.getFilterOptionValues(
                    null,
                    filterOption,
                )

            override suspend fun positionOfLetter(letter: Char): Int? =
                collectionStateFor(type)?.let { collectionState ->
                    when (type) {
                        BaseItemKind.MUSIC_ARTIST -> {
                            val request =
                                createGetArtistsRequest(
                                    collectionState.filter,
                                    collectionState.sortAndDirection,
                                ).copy(
                                    enableUserData = false,
                                    limit = 0,
                                    enableTotalRecordCount = true,
                                )
                            GetArtistsHandler.execute(api, request).content.totalRecordCount
                        }

                        BaseItemKind.PERSON -> {
                            null
                        }

                        else -> {
                            val request =
                                createGetItemsRequest(
                                    type,
                                    collectionState.filter,
                                    collectionState.sortAndDirection,
                                ).copy(
                                    enableUserData = false,
                                    limit = 0,
                                    enableTotalRecordCount = true,
                                )
                            GetItemsRequestHandler.execute(api, request).content.totalRecordCount
                        }
                    }
                }

            override fun saveViewOptions(viewOptions: ViewOptions) {
                saveViewOptions(type, viewOptions)
            }

            override fun onClickRandom() {
                viewModelScope.launchIO {
                    try {
                        collectionStateFor(type)?.let { collectionState ->
                            val random =
                                (collectionState.items.successValue as? BlockingList<BaseItem?>)?.randomBlocking()
                            Timber.d("Got random item: %s", random?.id)
                            random?.destination()?.let {
                                navigateTo(random.destination())
                            }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error getting random")
                        showToast(context, "Error: ${ex.localizedMessage}}")
                    }
                }
            }
        }
    }

data class FavoritesPageState(
    val loadingState: FavoritesLoadingState = FavoritesLoadingState.Pending,
    val favoriteTypeOptions: List<BaseItemKind> = emptyList(),
    val favorites: SnapshotStateMap<BaseItemKind, CollectionFolderState> = SnapshotStateMap(),
    val tabs: SortedMap<BaseItemKind, TabDetails> = TreeMap(compareBy { favoriteOptions.indexOf(it) }),
    val tabKey: BaseItemKind = favoriteOptions.first(),
    val isShowClock: Boolean = true,
)

sealed interface FavoritesLoadingState {
    data object Pending : FavoritesLoadingState

    data object Loading : FavoritesLoadingState

    data object Success : FavoritesLoadingState

    data object NoFavorites : FavoritesLoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : FavoritesLoadingState {
        constructor(exception: Throwable) : this(null, exception)

        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}

/**
 * List of supported favorite types
 *
 * This is also the order they will appear in the UI
 */
val favoriteOptions =
    // Peke is for kids: videos and their collections only (no music, people, photos or live TV channels outside the
    // kid's guide)
    listOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.EPISODE,
        BaseItemKind.BOX_SET,
        BaseItemKind.MUSIC_VIDEO,
        BaseItemKind.PLAYLIST,
        BaseItemKind.VIDEO,
    )

/**
 * Creates a new tab map from the current state applying the supplied block which can contain mutations
 */
private inline fun FavoritesPageState.createMap(block: TreeMap<BaseItemKind, TabDetails>.() -> Unit) =
    TreeMap<BaseItemKind, TabDetails>(compareBy { favoriteOptions.indexOf(it) })
        .apply {
            putAll(this@createMap.tabs)
            block.invoke(this)
        }

/**
 * Given the list of libraries, return the possible favorite types
 */
fun List<Library>.possibleFavoriteTypes(sort: Boolean) =
    flatMap { it.collectionType.baseItemKinds }
        .distinct()
        .filter { it in favoriteOptions }
        .let { list ->
            if (sort) {
                list.sortedWith(compareBy { favoriteOptions.indexOf(it) })
            } else {
                list
            }
        }
