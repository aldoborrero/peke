package dev.aldoborrero.peke.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.LibraryDisplayInfoDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.filter.DefaultFilterOptions
import dev.aldoborrero.peke.data.filter.FilterValueOption
import dev.aldoborrero.peke.data.filter.ItemFilterBy
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.CollectionFolderFilter
import dev.aldoborrero.peke.data.model.GetItemsFilter
import dev.aldoborrero.peke.data.model.GetItemsFilterOverride
import dev.aldoborrero.peke.data.model.LibraryDisplayInfo
import dev.aldoborrero.peke.kids.KidsItemTypes
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.isActive
import dev.aldoborrero.peke.kids.kidsPlayKeyDestination
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.FavoriteWatchManager
import dev.aldoborrero.peke.services.FilterOptionCache
import dev.aldoborrero.peke.services.MediaManagementService
import dev.aldoborrero.peke.services.MusicService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.StreamChoiceService
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.services.deleteItem
import dev.aldoborrero.peke.ui.SlimItemFields
import dev.aldoborrero.peke.ui.data.SortAndDirection
import dev.aldoborrero.peke.ui.detail.music.addToQueue
import dev.aldoborrero.peke.ui.equalsNotNull
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.rememberInt
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.ui.toServerString
import dev.aldoborrero.peke.ui.tryRequestFocus
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = CollectionFolderViewModel.Factory::class)
class CollectionFolderViewModel
    @AssistedInject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        val serverRepository: ServerRepository,
        private val libraryDisplayInfoDao: LibraryDisplayInfoDao,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val navigationManager: NavigationManager,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val musicService: MusicService,
        val streamChoiceService: StreamChoiceService,
        val serverReportService: ServerReportService,
        private val filterOptionCache: FilterOptionCache,
        @Assisted val itemId: String,
        @Assisted initialSortAndDirection: SortAndDirection?,
        @Assisted("recursive") private val recursive: Boolean,
        @Assisted private val collectionFilter: CollectionFolderFilter,
        @Assisted("useSeriesForPrimary") private val useSeriesForPrimary: Boolean,
        @Assisted defaultViewOptions: ViewOptions,
    ) : ViewModel(),
        ContextMenuProvider,
        CollectionFolderViewActions {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: String,
                initialSortAndDirection: SortAndDirection?,
                @Assisted("recursive") recursive: Boolean,
                collectionFilter: CollectionFolderFilter,
                @Assisted("useSeriesForPrimary") useSeriesForPrimary: Boolean,
                defaultViewOptions: ViewOptions,
            ): CollectionFolderViewModel
        }

        private val _state = MutableStateFlow(CollectionFolderState(viewOptions = defaultViewOptions))
        val state: StateFlow<CollectionFolderState> = _state

        var position: Int
            get() = savedStateHandle.get<Int>("position") ?: 0
            set(value) {
                savedStateHandle["position"] = value
            }

        init {
            viewModelScope.launchIO {
                try {
                    val item =
                        itemId.toUUIDOrNull()?.let {
                            api.userLibraryApi
                                .getItem(it)
                                .content
                                .let(::BaseItem)
                        }

                    val libraryDisplayInfo =
                        serverRepository.currentUser?.let { user ->
                            val id = collectionFilter.libraryDisplayInfoIdOverride ?: itemId
                            libraryDisplayInfoDao.getItem(user, id)
                        }
                    val ancestorDisplayInfo =
                        if (libraryDisplayInfo?.viewOptions == null && item != null) {
                            getAncestorDisplayInfo(item)
                        } else {
                            null
                        }
                    _state.update {
                        it.copy(
                            viewOptions =
                                libraryDisplayInfo?.viewOptions
                                    ?: ancestorDisplayInfo?.viewOptions
                                    ?: defaultViewOptions,
                        )
                    }

                    val sortAndDirection =
                        if (collectionFilter.useSavedLibraryDisplayInfo) {
                            libraryDisplayInfo?.sortAndDirection
                                ?: ancestorDisplayInfo?.sortAndDirection
                        } else {
                            null
                        } ?: initialSortAndDirection ?: SortAndDirection.DEFAULT

                    val filterToUse =
                        if (collectionFilter.useSavedLibraryDisplayInfo && libraryDisplayInfo?.filter != null) {
                            collectionFilter.filter.merge(libraryDisplayInfo.filter)
                        } else {
                            collectionFilter.filter
                        }

                    _state.update { it.copy(item = DataLoadingState.Success(item)) }
                    loadResults(true, sortAndDirection, recursive, filterToUse, useSeriesForPrimary)
                        .join()
                } catch (ex: Exception) {
                    Timber.e(ex, "Error during init")
                    _state.update {
                        it.copy(
                            item = DataLoadingState.Error(ex),
                        )
                    }
                }
            }
            mediaManagementService.deletedItemFlow
                .onEach { deletedItem ->
                    refreshAfterDelete(position, deletedItem.item)
                }.catch { ex ->
                    Timber.e(ex, "Error refreshing after deleted item")
                }.launchIn(viewModelScope)
        }

        private suspend fun refreshAfterDelete(
            position: Int,
            deletedItem: BaseItem,
        ) {
            try {
                val pager =
                    ((state.value.items as? DataLoadingState.Success)?.data as? ApiRequestPager<*>)
                position.let {
                    Timber.v("Item deleted: position=%s, id=%s", it, itemId)
                    val item = pager?.get(it)
                    // Exact item deleted (eg a movie) or deleted item was within the series
                    if (item?.id == deletedItem.id ||
                        equalsNotNull(item?.data?.id, deletedItem.data.seriesId)
                    ) {
                        pager?.refreshPagesAfter(position)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing after deleted item %s", itemId)
                showToast(context, "Error refreshing after item deleted")
            }
        }

        /**
         * For a sub-folder, returns the saved display info of the closest ancestor that has one
         */
        private suspend fun getAncestorDisplayInfo(item: BaseItem): LibraryDisplayInfo? {
            if (item.type != BaseItemKind.FOLDER || collectionFilter.libraryDisplayInfoIdOverride != null) {
                return null
            }
            val user = serverRepository.currentUser ?: return null
            return try {
                api.libraryApi
                    .getAncestors(item.id, user.id)
                    .content
                    .firstNotNullOfOrNull { ancestor -> libraryDisplayInfoDao.getItem(user, ancestor.id) }
            } catch (ex: Exception) {
                Timber.w(ex, "Error getting ancestors for %s", item.id)
                null
            }
        }

        private fun saveLibraryDisplayInfo(
            newFilter: GetItemsFilter = state.value.filter,
            newSort: SortAndDirection = state.value.sortAndDirection,
            viewOptions: ViewOptions? = state.value.viewOptions,
        ) {
            if (collectionFilter.useSavedLibraryDisplayInfo) {
                serverRepository.currentUser?.let { user ->
                    viewModelScope.launchIO {
                        val libraryDisplayInfo =
                            LibraryDisplayInfo(
                                userId = user.rowId,
                                itemId = collectionFilter.libraryDisplayInfoIdOverride ?: itemId,
                                sort = newSort.sort,
                                direction = newSort.direction,
                                filter = newFilter,
                                viewOptions = viewOptions,
                            )
                        libraryDisplayInfoDao.saveItem(libraryDisplayInfo)
                    }
                }
            }
        }

        override fun saveViewOptions(viewOptions: ViewOptions) {
            position = 0
            _state.update { it.copy(viewOptions = viewOptions) }
            viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                saveLibraryDisplayInfo(viewOptions = viewOptions)
                if (!viewOptions.showBackdrop) {
                    backdropService.clearBackdrop()
                }
            }
        }

        override fun onClickRandom() {
            viewModelScope.launchIO {
                try {
                    val random =
                        (state.value.items.successValue as? BlockingList<BaseItem?>)?.randomBlocking()
                    Timber.d("Got random item: %s", random?.id)
                    random?.destination()?.let {
                        navigateTo(random.destination())
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting random")
                    showToast(context, "Error: ${ex.localizedMessage}}")
                }
            }
        }

        override fun onFilterChange(
            newFilter: GetItemsFilter,
            recursive: Boolean,
        ) {
            Timber.v("onFilterChange: filter=%s", newFilter)
            saveLibraryDisplayInfo(newFilter)
            loadResults(
                false,
                state.value.sortAndDirection,
                recursive,
                newFilter,
                useSeriesForPrimary,
            )
        }

        override fun onSortChange(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ) {
            Timber.v(
                "onSortChange: sort=%s, recursive=%s, filter=%s",
                sortAndDirection,
                recursive,
                filter,
            )
            saveLibraryDisplayInfo(filter, sortAndDirection)
            loadResults(true, sortAndDirection, recursive, filter, useSeriesForPrimary)
        }

        private fun loadResults(
            resetState: Boolean,
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
            useSeriesForPrimary: Boolean,
        ) = viewModelScope.launch(PekeDispatchers.IO) {
            _state.update {
                it.copy(
                    items = DataLoadingState.Loading,
                    backgroundLoading = LoadingState.Loading,
                    sortAndDirection = sortAndDirection,
                    filter = filter,
                )
            }
            try {
                val newPager =
                    createPager(sortAndDirection, recursive, filter, useSeriesForPrimary).init()
                if (newPager.isNotEmpty()) newPager.getBlocking(0)
                _state.update {
                    it.copy(
                        items = DataLoadingState.Success(newPager),
                        backgroundLoading = LoadingState.Success,
                    )
                }
            } catch (ex: Exception) {
                Timber.e(
                    ex,
                    "Exception while loading data: sort=%s, filter=%s",
                    sortAndDirection,
                    filter,
                )
                _state.update {
                    it.copy(
                        items = DataLoadingState.Error(ex),
                        backgroundLoading = LoadingState.Pending,
                    )
                }
            }
        }

        private fun createPager(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
            useSeriesForPrimary: Boolean,
        ): ApiRequestPager<out Any> =
            when (filter.override) {
                GetItemsFilterOverride.NONE -> {
                    val request =
                        createGetItemsRequest(
                            sortAndDirection = sortAndDirection,
                            recursive = recursive,
                            filter = filter,
                        )
                    val newPager =
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            viewModelScope,
                            useSeriesForPrimary = useSeriesForPrimary,
                        )
                    newPager
                }

                GetItemsFilterOverride.PERSON -> {
                    val request =
                        filter.applyTo(
                            GetPersonsRequest(
                                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                            ),
                        )
                    val newPager =
                        ApiRequestPager(
                            api,
                            request,
                            GetPersonsHandler,
                            viewModelScope,
                            useSeriesForPrimary = useSeriesForPrimary,
                        )
                    newPager
                }

                GetItemsFilterOverride.ARTIST -> {
                    ApiRequestPager(
                        api,
                        createGetArtistsRequest(filter),
                        GetArtistsHandler,
                        viewModelScope,
                        useSeriesForPrimary = useSeriesForPrimary,
                    )
                }
            }

        /**
         * Shared with [positionOfLetter] so that the alphabet jump counts the same artists that are
         * actually paged through
         */
        private fun createGetArtistsRequest(filter: GetItemsFilter): GetArtistsRequest {
            val item = state.value.item.successValue
            return filter.applyTo(
                GetArtistsRequest(
                    parentId = item?.id,
                    enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                    fields = SlimItemFields,
                ),
            )
        }

        private fun createGetItemsRequest(
            sortAndDirection: SortAndDirection,
            recursive: Boolean,
            filter: GetItemsFilter,
        ): GetItemsRequest {
            val item = state.value.item.successValue
            val includeItemTypes =
                item
                    ?.data
                    ?.collectionType
                    ?.baseItemKinds
                    .orEmpty()
            val request =
                filter.applyTo(
                    GetItemsRequest(
                        parentId = item?.id,
                        enableImageTypes =
                            listOf(
                                ImageType.PRIMARY,
                                ImageType.THUMB,
                                ImageType.BACKDROP,
                                ImageType.LOGO,
                            ),
                        includeItemTypes = includeItemTypes,
                        recursive = recursive,
                        excludeItemTypes = KidsItemTypes.EXCLUDED,
                        excludeItemIds = item?.let { listOf(item.id) },
                        sortBy =
                            buildList {
                                if (sortAndDirection.sort != ItemSortBy.DEFAULT) {
                                    add(sortAndDirection.sort)
                                    if (sortAndDirection.sort != ItemSortBy.SORT_NAME) {
                                        add(ItemSortBy.SORT_NAME)
                                    }
                                    if (item?.data?.collectionType == CollectionType.MOVIES) {
                                        add(ItemSortBy.PRODUCTION_YEAR)
                                    }
                                }
                            },
                        sortOrder =
                            buildList {
                                if (sortAndDirection.sort != ItemSortBy.DEFAULT) {
                                    add(sortAndDirection.direction)
                                    if (sortAndDirection.sort != ItemSortBy.SORT_NAME) {
                                        add(SortOrder.ASCENDING)
                                    }
                                    if (item?.data?.collectionType == CollectionType.MOVIES) {
                                        add(SortOrder.ASCENDING)
                                    }
                                }
                            },
                        fields = SlimItemFields,
                    ),
                )
            return request
        }

        override suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption> =
            filterOptionCache.getFilterOptionValues(
                itemId.toUUID(),
                filterOption,
            )

        /**
         * The count must come from the same endpoint that [createPager] pages through, otherwise
         * the index refers to a different result set and lands past the end of the list.
         */
        override suspend fun positionOfLetter(letter: Char): Int? =
            withContext(PekeDispatchers.IO) {
                val filter = state.value.filter
                when (filter.override) {
                    GetItemsFilterOverride.ARTIST -> {
                        GetArtistsHandler.countMatching(
                            api = api,
                            request =
                                createGetArtistsRequest(filter).copy(
                                    enableImageTypes = null,
                                    fields = null,
                                    nameLessThan = letter.toString(),
                                    limit = 0,
                                    enableTotalRecordCount = true,
                                    enableUserData = false,
                                ),
                        )
                    }

                    // GetPersonsRequest has no nameLessThan or startIndex, so /Persons cannot be
                    // counted up to a letter at all
                    GetItemsFilterOverride.PERSON -> {
                        null
                    }

                    GetItemsFilterOverride.NONE -> {
                        GetItemsRequestHandler.countMatching(
                            api = api,
                            request =
                                createGetItemsRequest(
                                    sortAndDirection = state.value.sortAndDirection,
                                    recursive = recursive,
                                    filter = filter,
                                ).copy(
                                    enableImageTypes = null,
                                    fields = null,
                                    nameLessThan = letter.toString(),
                                    limit = 0,
                                    enableTotalRecordCount = true,
                                    enableUserData = false,
                                ),
                        )
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
                (state.value.items as? DataLoadingState.Success)?.let {
                    (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
                (state.value.items as? DataLoadingState.Success)?.let {
                    (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        override fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        override fun deleteItem(
            index: Int,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    refreshAfterDelete(index, item)
                }
            }
        }

        override fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun addToQueue(
            item: BaseItem,
            index: Int,
        ) = addToQueue(api, musicService, item, index)

        override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true
    }

data class CollectionFolderState(
    val item: DataLoadingState<BaseItem?> = DataLoadingState.Loading,
    val items: DataLoadingState<List<BaseItem?>> = DataLoadingState.Loading,
    val backgroundLoading: LoadingState = LoadingState.Loading,
    val sortAndDirection: SortAndDirection = SortAndDirection.DEFAULT,
    val filter: GetItemsFilter = GetItemsFilter(),
    val viewOptions: ViewOptions,
)

/**
 * Shows a collection folder as a grid
 *
 * This is the "Library" tab for Movies or TV shows
 */
@Composable
fun CollectionFolderView(
    preferences: UserPreferences,
    itemId: UUID,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModelKey: String? = itemId.toServerString(),
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
) = CollectionFolderView(
    preferences,
    itemId.toServerString(),
    initialFilter,
    recursive,
    GridClickActions(onClickItem = onClickItem),
    sortOptions,
    playEnabled,
    viewModelKey = viewModelKey,
    defaultViewOptions = defaultViewOptions,
    modifier = modifier,
    initialSortAndDirection = initialSortAndDirection,
    showTitle = showTitle,
    positionCallback = positionCallback,
    useSeriesForPrimary = useSeriesForPrimary,
    filterOptions = filterOptions,
    focusRequesterOnEmpty = focusRequesterOnEmpty,
)

@Composable
fun CollectionFolderView(
    preferences: UserPreferences,
    itemId: UUID,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    actions: GridClickActions,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModelKey: String? = itemId.toServerString(),
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
) = CollectionFolderView(
    preferences,
    itemId.toServerString(),
    initialFilter,
    recursive,
    actions,
    sortOptions,
    playEnabled,
    viewModelKey = viewModelKey,
    defaultViewOptions = defaultViewOptions,
    modifier = modifier,
    initialSortAndDirection = initialSortAndDirection,
    showTitle = showTitle,
    positionCallback = positionCallback,
    useSeriesForPrimary = useSeriesForPrimary,
    filterOptions = filterOptions,
    focusRequesterOnEmpty = focusRequesterOnEmpty,
)

@Composable
fun CollectionFolderView(
    preferences: UserPreferences,
    itemId: String,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    actions: GridClickActions,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    modifier: Modifier = Modifier,
    viewModelKey: String? = itemId,
    initialSortAndDirection: SortAndDirection? = null,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    useSeriesForPrimary: Boolean = true,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
    viewModel: CollectionFolderViewModel =
        hiltViewModel<CollectionFolderViewModel, CollectionFolderViewModel.Factory>(
            key = viewModelKey,
        ) {
            it.create(
                itemId = itemId,
                initialSortAndDirection = initialSortAndDirection,
                recursive = recursive,
                collectionFilter = initialFilter,
                useSeriesForPrimary = useSeriesForPrimary,
                defaultViewOptions = defaultViewOptions,
            )
        },
) {
    val state by viewModel.state.collectAsState()
    CollectionFolderViewContent(
        preferences = preferences,
        state = state,
        savedPosition = viewModel.position,
        itemId = itemId,
        initialFilter = initialFilter,
        recursive = recursive,
        actions = actions,
        sortOptions = sortOptions,
        playEnabled = playEnabled,
        defaultViewOptions = defaultViewOptions,
        viewActions = viewModel,
        modifier = modifier,
        provider = viewModel,
        showTitle = showTitle,
        positionCallback = { columns, index ->
            viewModel.position = index
            positionCallback?.invoke(columns, index)
        },
        filterOptions = filterOptions,
        focusRequesterOnEmpty = focusRequesterOnEmpty,
    )
}

interface CollectionFolderViewActions {
    fun navigateTo(destination: Destination)

    fun updateBackdrop(item: BaseItem)

    fun onSortChange(
        sortAndDirection: SortAndDirection,
        recursive: Boolean,
        filter: GetItemsFilter,
    )

    fun onFilterChange(
        newFilter: GetItemsFilter,
        recursive: Boolean,
    )

    suspend fun getFilterOptionValues(filterOption: ItemFilterBy<*>): List<FilterValueOption>

    suspend fun positionOfLetter(letter: Char): Int?

    fun saveViewOptions(viewOptions: ViewOptions)

    /**
     * Navigate to a random item
     */
    fun onClickRandom()
}

@Composable
fun CollectionFolderViewContent(
    preferences: UserPreferences,
    state: CollectionFolderState,
    savedPosition: Int,
    itemId: String,
    initialFilter: CollectionFolderFilter,
    recursive: Boolean,
    actions: GridClickActions,
    sortOptions: List<ItemSortBy>,
    playEnabled: Boolean,
    defaultViewOptions: ViewOptions,
    viewActions: CollectionFolderViewActions,
    modifier: Modifier = Modifier,
    provider: ContextMenuProvider,
    showTitle: Boolean = true,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    focusRequesterOnEmpty: FocusRequester? = null,
) {
    var position by rememberInt(savedPosition)

    // Track if leaving page due to clicking the random button so focus can be restored there
    var clickedRandom by rememberSaveable { mutableStateOf(false) }

    val contextMenu = rememberContextMenu(preferences, provider)
    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var filterDropdownShowing by remember { mutableStateOf(false) }
    val headerRowFocusRequester = remember { FocusRequester() }
    val filterButtonFocusRequester = remember { FocusRequester() }
    val randomButtonFocusRequester = remember { FocusRequester() }

    // A kid's Play key opens the details page (spec 005 FR-001)
    val kidsMode = LocalKidsMode.current.isActive
    val gridActions =
        remember(actions, kidsMode) {
            GridClickActions(
                onClickItem = { index, item ->
                    position = index
                    actions.onClickItem.invoke(index, item)
                },
                onLongClickItem = { index, item ->
                    position = index
                    if (actions.onLongClickItem != null) {
                        actions.onLongClickItem.invoke(index, item)
                    } else {
                        contextMenu.showContextMenu(index, item)
                    }
                },
                onClickPlayAll =
                    actions.onClickPlayAll ?: { shuffle ->
                        itemId.toUUIDOrNull()?.let {
                            val destination =
                                if (state.item.successValue?.type == BaseItemKind.PHOTO_ALBUM) {
                                    Destination.Slideshow(
                                        parentId = it,
                                        index = 0,
                                        filter = CollectionFolderFilter(filter = state.filter),
                                        sortAndDirection = state.sortAndDirection,
                                        recursive = true,
                                        startSlideshow = true,
                                    )
                                } else {
                                    Destination.PlaybackList(
                                        itemId = it,
                                        startIndex = 0,
                                        shuffle = shuffle,
                                        recursive = recursive,
                                        sortAndDirection = state.sortAndDirection,
                                        filter = state.filter,
                                    )
                                }
                            viewActions.navigateTo(destination)
                        }
                        Unit
                    },
                onClickPlayRemoteButton =
                    actions.onClickPlayRemoteButton ?: { index, item ->
                        val destination =
                            if (item.type == BaseItemKind.PHOTO_ALBUM) {
                                Destination.Slideshow(
                                    parentId = item.id,
                                    index = index,
                                    filter = CollectionFolderFilter(filter = state.filter),
                                    sortAndDirection = state.sortAndDirection,
                                    recursive = true,
                                    startSlideshow = true,
                                )
                            } else if (kidsMode) {
                                kidsPlayKeyDestination(item)
                            } else {
                                Destination.Playback(item)
                            }
                        viewActions.navigateTo(destination)
                    },
            )
        }
    Box(modifier = modifier) {
        when (val st = state.item) {
            DataLoadingState.Loading,
            DataLoadingState.Pending,
            -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            is DataLoadingState.Error -> {
                ErrorMessage(st, Modifier.fillMaxSize())
            }

            is DataLoadingState.Success<BaseItem?> -> {
                val item = st.successValue
                val title =
                    initialFilter.nameOverride
                        ?: item?.name
                        ?: item?.data?.collectionType?.name
                        ?: stringResource(R.string.collection)
                Column(modifier = Modifier.fillMaxSize()) {
                    var showHeader by rememberSaveable { mutableStateOf(true) }
                    val gridFocusRequester = remember { FocusRequester() }
                    val pager = remember(state.items) { state.items.successValue }

                    val focusedItem = pager?.getOrNull(position)
                    if (state.viewOptions.showBackdrop) {
                        LaunchedEffect(focusedItem) {
                            focusedItem?.let(viewActions::updateBackdrop)
                        }
                    }
                    CollectionFolderHeader(
                        listIsNotEmpty = state.items.successValue?.isNotEmpty() == true,
                        showHeader = showHeader || state.items !is DataLoadingState.Success,
                        showTitle = showTitle,
                        playEnabled = playEnabled,
                        title = title,
                        sortAndDirection = state.sortAndDirection,
                        onSortChange = {
                            viewActions.onSortChange(it, recursive, state.filter)
                        },
                        sortOptions = sortOptions,
                        currentFilter = state.filter,
                        onFilterChange = {
                            viewActions.onFilterChange(it, recursive)
                        },
                        getPossibleFilterValues = {
                            viewActions.getFilterOptionValues(it)
                        },
                        filterOptions = filterOptions,
                        onClickPlayAll = gridActions.onClickPlayAll!!,
                        onClickShowViewOptions = { showViewOptions = true },
                        onClickRandom = {
                            clickedRandom = true
                            viewActions.onClickRandom()
                        },
                        modifier = Modifier.focusRequester(headerRowFocusRequester),
                        onShowFilterDropdown = { filterDropdownShowing = it },
                        filterButtonFocusRequester = filterButtonFocusRequester,
                        randomButtonFocusRequester = randomButtonFocusRequester,
                    )

                    when (val pager = state.items) {
                        is DataLoadingState.Error -> {
                            LaunchedEffect(Unit) {
                                (focusRequesterOnEmpty ?: headerRowFocusRequester).tryRequestFocus()
                            }
                            ErrorMessage(pager, Modifier.fillMaxSize())
                        }

                        DataLoadingState.Loading,
                        DataLoadingState.Pending,
                        -> {
                            LoadingPage(Modifier.fillMaxSize())
                        }

                        is DataLoadingState.Success<List<BaseItem?>> -> {
                            LaunchedEffect(Unit) {
                                val focusRequester =
                                    when {
                                        contextMenu.isShowing -> null
                                        filterDropdownShowing -> filterButtonFocusRequester
                                        clickedRandom -> randomButtonFocusRequester
                                        pager.data.isNotEmpty() -> gridFocusRequester
                                        else -> focusRequesterOnEmpty ?: headerRowFocusRequester
                                    }
                                focusRequester?.tryRequestFocus()
                                clickedRandom = false
                            }
                            Box(Modifier.fillMaxSize()) {
                                if (state.viewOptions.type == ViewOptionsType.GRID) {
                                    CollectionFolderGrid(
                                        preferences = preferences,
                                        collectionType = item?.data?.collectionType,
                                        initialPosition = savedPosition,
                                        items = pager.data,
                                        sortAndDirection = state.sortAndDirection,
                                        modifier = Modifier.fillMaxSize(),
                                        gridFocusRequester = gridFocusRequester,
                                        onClickItem = gridActions.onClickItem,
                                        onLongClickItem = gridActions.onLongClickItem!!,
                                        positionCallback = { columns, pos ->
                                            showHeader = pos < columns
                                            position = pos
                                            positionCallback?.invoke(columns, pos)
                                        },
                                        letterPosition = { viewActions.positionOfLetter(it) ?: -1 },
                                        viewOptions = state.viewOptions,
                                        onClickPlay = gridActions.onClickPlayRemoteButton!!,
                                        focusedItem = focusedItem,
                                    )
                                } else {
                                    CollectionFolderList(
                                        preferences = preferences,
                                        collectionType = item?.data?.collectionType,
                                        initialPosition = savedPosition,
                                        items = pager.data,
                                        sortAndDirection = state.sortAndDirection,
                                        modifier = Modifier.fillMaxSize(),
                                        gridFocusRequester = gridFocusRequester,
                                        onClickItem = gridActions.onClickItem,
                                        onLongClickItem = gridActions.onLongClickItem!!,
                                        positionCallback = { columns, pos ->
                                            showHeader = pos < columns
                                            position = pos
                                            positionCallback?.invoke(columns, pos)
                                        },
                                        letterPosition = { viewActions.positionOfLetter(it) ?: -1 },
                                        viewOptions = state.viewOptions,
                                        onClickPlay = gridActions.onClickPlayRemoteButton!!,
                                        focusedItem = focusedItem,
                                    )
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    state.backgroundLoading == LoadingState.Loading,
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .padding(16.dp),
                                ) {
                                    CircularProgress(
                                        Modifier
                                            .background(
                                                MaterialTheme.colorScheme.background.copy(alpha = .25f),
                                                shape = CircleShape,
                                            ).size(64.dp)
                                            .padding(4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    contextMenu.Compose()
    AnimatedVisibility(showViewOptions) {
        ViewOptionsDialog(
            viewOptions = state.viewOptions,
            defaultViewOptions = defaultViewOptions,
            onDismissRequest = {
                showViewOptions = false
                viewActions.saveViewOptions(state.viewOptions)
            },
            onViewOptionsChange = viewActions::saveViewOptions,
        )
    }
}
