package dev.aldoborrero.peke.ui.search

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.updateSearchPreferences
import dev.aldoborrero.peke.services.FavoriteWatchManager
import dev.aldoborrero.peke.services.KeyValueService
import dev.aldoborrero.peke.services.LiveTvService
import dev.aldoborrero.peke.services.MediaManagementService
import dev.aldoborrero.peke.services.NavDrawerService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.services.deleteItem
import dev.aldoborrero.peke.services.tvAccess
import dev.aldoborrero.peke.ui.ProgramItemFields
import dev.aldoborrero.peke.ui.SlimItemFields
import dev.aldoborrero.peke.ui.components.ContextMenuProvider
import dev.aldoborrero.peke.ui.components.VoiceInputManager
import dev.aldoborrero.peke.ui.components.baseItemKinds
import dev.aldoborrero.peke.ui.data.RowColumn
import dev.aldoborrero.peke.ui.detail.livetv.ProgramDialogState
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.ui.toBaseItems
import dev.aldoborrero.peke.util.DataLoadingState
import dev.aldoborrero.peke.util.ExceptionHandler
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.SearchRelevance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val appPreferences: DataStore<AppPreferences>,
        val voiceInputManager: VoiceInputManager,
        val userPreferencesService: UserPreferencesService,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val mediaManagementService: MediaManagementService,
        private val serverReportService: ServerReportService,
        private val liveTvService: LiveTvService,
        private val keyValueService: KeyValueService,
        private val navDrawerService: NavDrawerService,
    ) : ViewModel(),
        ContextMenuProvider {
        private val _state = MutableStateFlow(SearchState())
        val state: StateFlow<SearchState> = _state
        val position = MutableStateFlow(RowColumn(0, 0))

        private var currentQuery: String? = null
        private var combinedMode = false

        private val _programDialogState = MutableStateFlow(ProgramDialogState())
        val programDialogState: StateFlow<ProgramDialogState> = _programDialogState

        private var userLibraryTypes: Set<BaseItemKind> = emptySet()

        init {
            init()
        }

        private fun init() {
            viewModelScope.launchDefault {
                userLibraryTypes =
                    navDrawerService.state.value.allLibraries
                        .flatMap { it.collectionType.baseItemKinds }
                        .toSet()

                val excludedSearchableTypes =
                    serverRepository.currentUser?.id?.let { userId ->
                        try {
                            keyValueService
                                .get<List<BaseItemKind>>(
                                    userId,
                                    EXCLUDED_SEARCHABLE_TYPES_KEY,
                                    emptyList(),
                                ).firstOrNull()
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error occurred fetching excluded search types")
                            emptyList()
                        }
                    } ?: emptyList()
                val searchableTypes = determineSearchableTypes(excludedSearchableTypes)
                val possibleSearchableTypes = determineSearchableTypes(emptyList())
                _state.update {
                    it.copy(
                        includedSearchableTypes = searchableTypes,
                        possibleSearchableTypes = possibleSearchableTypes,
                        excludedSearchableTypes = excludedSearchableTypes,
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                searchableTypes.forEach { put(it, SearchResult.NoQuery) }
                            },
                    )
                }
            }
        }

        private fun determineSearchableTypes(excludedSearchableTypes: List<BaseItemKind>): List<BaseItemKind> {
            val tvAccess = serverRepository.currentUserDto?.tvAccess == true
            val searchableTypes =
                allSearchableTypes.filter {
                    val excluded = excludedSearchableTypes.contains(it)
                    // Only include if there's a relevant, accessible library
                    val hasLibrary = userLibraryTypes.contains(it)
                    when (it) {
                        // No library type for person
                        BaseItemKind.PERSON -> !excluded

                        // Remove live tv search if user doesn't have access
                        BaseItemKind.TV_CHANNEL,
                        BaseItemKind.LIVE_TV_PROGRAM,
                        BaseItemKind.TV_PROGRAM,
                        BaseItemKind.PROGRAM,
                        -> tvAccess && !excluded

                        else -> hasLibrary && !excluded
                    }
                }
            return searchableTypes
        }

        fun search(
            query: String?,
            combined: Boolean = false,
            force: Boolean = false,
        ) {
            if (currentQuery == query && combinedMode == combined && !force) {
                return
            }
            currentQuery = query
            combinedMode = combined
            if (query.isNotNullOrBlank()) {
                _state.update {
                    it.copy(
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                it.includedSearchableTypes.forEach {
                                    put(it, SearchResult.Searching)
                                }
                            },
                        combinedResults = SearchResult.Searching,
                    )
                }
                if (combined) {
                    searchCombined(query)
                } else {
                    state.value.includedSearchableTypes.forEach { type ->
                        searchType(query, type)
                    }
                }
            } else {
                _state.update {
                    it.copy(
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                it.includedSearchableTypes.forEach {
                                    put(it, SearchResult.NoQuery)
                                }
                            },
                        combinedResults = SearchResult.NoQuery,
                    )
                }
            }
        }

        private fun searchType(
            query: String,
            type: BaseItemKind,
        ) {
            viewModelScope.launchIO {
                try {
                    val items: List<BaseItem> =
                        when (type) {
                            BaseItemKind.LIVE_TV_PROGRAM -> {
                                val request =
                                    GetItemsRequest(
                                        searchTerm = query,
                                        recursive = true,
                                        includeItemTypes = listOf(type),
                                        fields = ProgramItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    )
                                api.itemsApi.getItems(request).toBaseItems(api, false)
                            }

                            BaseItemKind.MUSIC_ARTIST -> {
                                api.artistsApi
                                    .getArtists(
                                        searchTerm = query,
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    ).toBaseItems(api, false)
                            }

                            BaseItemKind.PERSON -> {
                                api.personsApi
                                    .getPersons(
                                        searchTerm = query,
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                    ).toBaseItems(api, false)
                            }

                            else -> {
                                val request =
                                    GetItemsRequest(
                                        searchTerm = query,
                                        recursive = true,
                                        includeItemTypes = listOf(type),
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    )
                                api.itemsApi.getItems(request).toBaseItems(api, false)
                            }
                        }
                    val sorted =
                        items.sortedWith(
                            compareBy<BaseItem> { SearchRelevance.score(it, query) },
                        )
                    Timber.v("Search finished for %s, %s results", type, sorted.size)
                    _state.value.results[type] = SearchResult.Success(sorted)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception searching for $type")
                    _state.value.results[type] = SearchResult.Error(ex)
                }
            }
        }

        private fun searchCombined(query: String) {
            viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                try {
                    Timber.v("Starting searchCombined")
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes = state.value.includedSearchableTypes,
                            fields = SlimItemFields,
                            limit = SEARCH_LIMIT,
                        )

                    val result = api.itemsApi.getItems(request).content
                    val items =
                        result.items.map {
                            BaseItem(it, false)
                        }
                    val sorted =
                        items.sortedWith(
                            compareBy<BaseItem> { SearchRelevance.score(it, query) },
                        )
                    Timber.v("searchCombined complete %s results", sorted.size)
                    _state.update { it.copy(combinedResults = SearchResult.Success(sorted)) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception in combined search")
                    _state.update { it.copy(combinedResults = SearchResult.Error(ex)) }
                }
            }
        }

        fun setCombinedResults(enabled: Boolean) {
            viewModelScope.launchIO {
                appPreferences.updateData {
                    it.updateSearchPreferences {
                        combinedSearchResults = enabled
                    }
                }
            }
        }

        fun setVoiceSearchButtonVisible(visible: Boolean) {
            viewModelScope.launchIO {
                appPreferences.updateData {
                    it.updateSearchPreferences {
                        showVoiceSearchButton = visible
                    }
                }
            }
        }

        init {
            addCloseable(voiceInputManager)
        }

        override fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

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
                    refreshItem(item.id)
                }
            }
        }

        private suspend fun refreshItem(itemId: UUID) {
            try {
                val position = position.value
                val searchResult =
                    if (combinedMode) {
                        state.value.combinedResults
                    } else {
                        state.value.includedSearchableTypes.getOrNull(position.row)?.let {
                            state.value.results[it]
                        }
                    } ?: return
                val items = (searchResult as? SearchResult.Success)?.items ?: return

                Timber.v("Item refresh: position=%s", position)
                val item = items.getOrNull(position.column)
                // Exact item deleted (eg a movie) or deleted item was within the series
                if (item != null && item.id == itemId) {
                    val newItem =
                        api.userLibraryApi
                            .getItem(item.id)
                            .content
                            .let { BaseItem(it) }
                    val newList =
                        SearchResult.Success(
                            items.toMutableList().apply {
                                set(position.column, newItem)
                            },
                        )
                    if (combinedMode) {
                        _state.update {
                            it.copy(
                                combinedResults = newList,
                            )
                        }
                    } else {
                        state.value.includedSearchableTypes.getOrNull(position.row)?.let { type ->
                            state.value.results[type] = newList
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing item %s", itemId)
                showToast(context, "Error refreshing")
            }
        }

        override fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                favoriteWatchManager.setWatched(itemId, played)
                refreshItem(itemId)
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
                refreshItem(itemId)
            }
        }

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)

        fun fetchProgramForDialog(programId: UUID) {
            _programDialogState.update { it.copy(loading = DataLoadingState.Loading) }
            viewModelScope.launchDefault {
                try {
                    val result = liveTvService.fetchProgramForDialog(programId)
                    _programDialogState.update { it.copy(loading = DataLoadingState.Success(result)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching program $programId")
                    _programDialogState.update { it.copy(loading = DataLoadingState.Error(ex)) }
                }
            }
        }

        fun cancelRecording(
            series: Boolean,
            timerId: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                try {
                    val result = liveTvService.cancelRecording(series, timerId)
                    // TODO update program card?
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error canceling timer %s, series=%s", timerId, series)
                    showToast(context, "Error: ${ex.localizedMessage}")
                }
            }
        }

        fun record(
            programId: UUID,
            series: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    liveTvService.record(programId, series)
                    // TODO update program card?
                    refreshItem(programId)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error recording %s, series=%s", programId, series)
                    showToast(context, "Error: ${ex.localizedMessage}")
                }
            }
        }

        fun onClickExcludeSearchableType(type: BaseItemKind) {
            viewModelScope.launchIO {
                try {
                    val state = state.value
                    val updateSearch: Boolean
                    val newExcludes =
                        state.excludedSearchableTypes.toMutableList().apply {
                            if (type in this) {
                                updateSearch = true
                                remove(type)
                            } else {
                                // If combined, need to update, otherwise just hide the newly excluded results
                                updateSearch = combinedMode
                                add(type)
                            }
                        }
                    Timber.v("newExcludes=%s", newExcludes)
                    serverRepository.currentUser?.id?.let { userId ->
                        keyValueService.save(userId, EXCLUDED_SEARCHABLE_TYPES_KEY, newExcludes)
                    }
                    val searchableTypes = determineSearchableTypes(newExcludes)
                    _state.update {
                        it.copy(
                            includedSearchableTypes = searchableTypes,
                            excludedSearchableTypes = newExcludes,
                        )
                    }
                    if (updateSearch && currentQuery.isNotNullOrBlank()) {
                        Timber.d("Need to update search")
                        search(currentQuery, combinedMode, true)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception toggling %s", type)
                    showToast(context, "An error occurred: ${ex.localizedMessage}")
                }
            }
        }

        companion object {
            private const val EXCLUDED_SEARCHABLE_TYPES_KEY = "excludedSearchableTypes"
        }
    }

sealed interface SearchResult {
    data object NoQuery : SearchResult

    data object Searching : SearchResult

    data class Error(
        val ex: Exception,
    ) : SearchResult

    data class Success(
        val items: List<BaseItem?>,
    ) : SearchResult
}

data class SearchState(
    val results: SnapshotStateMap<BaseItemKind, SearchResult> = SnapshotStateMap(),
    val combinedResults: SearchResult = SearchResult.NoQuery,
    val possibleSearchableTypes: List<BaseItemKind> = emptyList(),
    val includedSearchableTypes: List<BaseItemKind> = emptyList(),
    val excludedSearchableTypes: List<BaseItemKind> = emptyList(),
)

private val allSearchableTypes =
    // Peke is for kids: videos and their collections only (no music, people, photos, or channels and programs outside
    // the kid's guide)
    listOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.EPISODE,
        BaseItemKind.BOX_SET,
        BaseItemKind.MUSIC_VIDEO,
        BaseItemKind.PLAYLIST,
        BaseItemKind.VIDEO,
    )

private const val SEARCH_LIMIT = 50
