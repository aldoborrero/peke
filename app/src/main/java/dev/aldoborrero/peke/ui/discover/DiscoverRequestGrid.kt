package dev.aldoborrero.peke.ui.discover

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.filter.DiscoverFilter
import dev.aldoborrero.peke.data.filter.DiscoverFilterBy
import dev.aldoborrero.peke.data.filter.DiscoverSort
import dev.aldoborrero.peke.data.filter.DiscoverSortAndDirection
import dev.aldoborrero.peke.data.filter.FilterValueOption
import dev.aldoborrero.peke.data.filter.discoverMovieFilters
import dev.aldoborrero.peke.data.filter.discoverTvFilters
import dev.aldoborrero.peke.data.model.DiscoverItem
import dev.aldoborrero.peke.services.FilterOptionCache
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SeerrApi
import dev.aldoborrero.peke.services.SeerrService
import dev.aldoborrero.peke.ui.cards.DiscoverItemCard
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.detail.CardGrid
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.tryRequestFocus
import dev.aldoborrero.peke.util.DataLoadingState
import dev.aldoborrero.peke.util.DiscoverMovieRequestHandler
import dev.aldoborrero.peke.util.DiscoverRequestPager
import dev.aldoborrero.peke.util.DiscoverRequestType
import dev.aldoborrero.peke.util.DiscoverTvRequestHandler
import dev.aldoborrero.peke.util.TrendingRequestHandler
import dev.aldoborrero.peke.util.UpcomingMovieRequestHandler
import dev.aldoborrero.peke.util.UpcomingTvRequestHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

@HiltViewModel(assistedFactory = DiscoverRequestViewModel.Factory::class)
class DiscoverRequestViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val api: SeerrApi,
        private val filterOptionCache: FilterOptionCache,
        @Assisted val type: DiscoverRequestType,
        @Assisted startIndex: Int,
        @Assisted initialFilter: DiscoverFilter,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                type: DiscoverRequestType,
                startIndex: Int,
                initialFilter: DiscoverFilter,
            ): DiscoverRequestViewModel
        }

        private val _state = MutableStateFlow(DiscoverRequestState())
        val state: StateFlow<DiscoverRequestState> = _state

        init {
            viewModelScope.launchDefault {
                val (sortOptions, potentialFilterOptions) =
                    when (type) {
                        DiscoverRequestType.DISCOVER_TV -> DiscoverSort.entries.toList() to discoverTvFilters
                        DiscoverRequestType.DISCOVER_MOVIES -> DiscoverSort.entries.toList() to discoverMovieFilters
                        DiscoverRequestType.TRENDING -> emptyList<DiscoverSort>() to emptyList()
                        DiscoverRequestType.UPCOMING_TV -> emptyList<DiscoverSort>() to emptyList()
                        DiscoverRequestType.UPCOMING_MOVIES -> emptyList<DiscoverSort>() to emptyList()
                        DiscoverRequestType.UNKNOWN -> emptyList<DiscoverSort>() to emptyList()
                    }
                val sortAndDirection =
                    when (type) {
                        DiscoverRequestType.DISCOVER_TV -> DiscoverSortAndDirection()
                        DiscoverRequestType.DISCOVER_MOVIES -> DiscoverSortAndDirection()
                        DiscoverRequestType.TRENDING -> DiscoverSortAndDirection()
                        DiscoverRequestType.UPCOMING_TV -> DiscoverSortAndDirection()
                        DiscoverRequestType.UPCOMING_MOVIES -> DiscoverSortAndDirection()
                        DiscoverRequestType.UNKNOWN -> DiscoverSortAndDirection()
                    }
                val filterOptions =
                    if (potentialFilterOptions.isNotEmpty()) {
                        // If the initial filter includes filtering (eg genres), then don't allow for changing it
                        potentialFilterOptions.filter { it.get(initialFilter) == null }
                    } else {
                        potentialFilterOptions
                    }
                _state.update {
                    it.copy(
                        startIndex = startIndex,
                        sortOptions = sortOptions,
                        filterOptions = filterOptions,
                        sortAndDirection = sortAndDirection,
                    )
                }
                fetchData(sortAndDirection, initialFilter, startIndex)
            }
        }

        private var fetchJob: Job? = null

        private suspend fun fetchData(
            sortAndDirection: DiscoverSortAndDirection,
            filter: DiscoverFilter,
            startIndex: Int = 0,
        ) {
            fetchJob?.cancel()
            try {
                _state.update {
                    it.copy(
                        filter = filter,
                        sortAndDirection = sortAndDirection,
                        loading = DataLoadingState.Loading,
                    )
                }
                val filter = filter.copy(sortBy = sortAndDirection)
                fetchJob =
                    viewModelScope.launchDefault {
                        val pager =
                            when (type) {
                                DiscoverRequestType.DISCOVER_TV -> {
                                    DiscoverRequestPager(
                                        api,
                                        DiscoverTvRequestHandler(filter),
                                        seerrService::createDiscoverItem,
                                        viewModelScope,
                                    )
                                }

                                DiscoverRequestType.DISCOVER_MOVIES -> {
                                    DiscoverRequestPager(
                                        api,
                                        DiscoverMovieRequestHandler(filter),
                                        seerrService::createDiscoverItem,
                                        viewModelScope,
                                    )
                                }

                                DiscoverRequestType.TRENDING -> {
                                    DiscoverRequestPager(
                                        api,
                                        TrendingRequestHandler,
                                        seerrService::createDiscoverItem,
                                        viewModelScope,
                                    )
                                }

                                DiscoverRequestType.UPCOMING_TV -> {
                                    DiscoverRequestPager(
                                        api,
                                        UpcomingTvRequestHandler,
                                        seerrService::createDiscoverItem,
                                        viewModelScope,
                                    )
                                }

                                DiscoverRequestType.UPCOMING_MOVIES -> {
                                    DiscoverRequestPager(
                                        api,
                                        UpcomingMovieRequestHandler,
                                        seerrService::createDiscoverItem,
                                        viewModelScope,
                                    )
                                }

                                DiscoverRequestType.UNKNOWN -> {
                                    throw IllegalArgumentException("Cannot display grid for DiscoverRequestType.UNKNOWN")
                                }
                            }.init(startIndex)
                        _state.update {
                            it.copy(
                                loading = DataLoadingState.Success(pager),
                            )
                        }
                    }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error initializing %s", type)
                _state.update {
                    it.copy(
                        loading = DataLoadingState.Error(ex),
                    )
                }
            }
        }

        fun onSortChange(newSort: DiscoverSortAndDirection) {
            viewModelScope.launchDefault {
                _state.update { it.copy(startIndex = 0) }
                fetchData(newSort, state.value.filter)
            }
        }

        fun onFilterChange(newFilter: DiscoverFilter) {
            viewModelScope.launchDefault {
                _state.update { it.copy(startIndex = 0) }
                fetchData(state.value.sortAndDirection, newFilter)
            }
        }

        suspend fun getPossibleFilterValues(filter: DiscoverFilterBy<*>): List<FilterValueOption> =
            filterOptionCache.getFilterOptionValues(null, filter)
    }

data class DiscoverRequestState(
    val loading: DataLoadingState<List<DiscoverItem?>> = DataLoadingState.Pending,
    val startIndex: Int = 0,
    val sortOptions: List<DiscoverSort> = emptyList(),
    val filterOptions: List<DiscoverFilterBy<*>> = emptyList(),
    val filter: DiscoverFilter = DiscoverFilter(),
    val sortAndDirection: DiscoverSortAndDirection = DiscoverSortAndDirection(),
)

@Composable
fun DiscoverRequestGrid(
    destination: Destination.DiscoverMoreResult,
    showTitle: Boolean,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModelKey: String? = null,
    viewModel: DiscoverRequestViewModel =
        hiltViewModel<DiscoverRequestViewModel, DiscoverRequestViewModel.Factory>(
            key = viewModelKey,
            creationCallback = {
                it.create(
                    destination.type,
                    destination.startIndex,
                    destination.initialFilter,
                )
            },
        ),
) {
    val state by viewModel.state.collectAsState()

    var showHeader by rememberSaveable { mutableStateOf(state.startIndex < 6) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        DiscoverGridHeader(
            showHeader = showHeader,
            showTitle = showTitle,
            title =
                destination.titleOverride?.getString()
                    ?: stringResource(destination.type.stringRes),
            sortAndDirection = state.sortAndDirection,
            onSortChange = viewModel::onSortChange,
            sortOptions = state.sortOptions,
            getPossibleFilterValues = viewModel::getPossibleFilterValues,
            onClickShowViewOptions = {},
            currentFilter = state.filter,
            filterOptions = state.filterOptions,
            onFilterChange = viewModel::onFilterChange,
            modifier = Modifier.fillMaxWidth(),
        )
        when (val s = state.loading) {
            DataLoadingState.Pending,
            DataLoadingState.Loading,
            -> {
                LoadingPage(Modifier)
            }

            is DataLoadingState.Error -> {
                ErrorMessage(s, Modifier)
            }

            is DataLoadingState.Success<List<DiscoverItem?>> -> {
                val gridFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                CardGrid(
                    initialPosition = state.startIndex,
                    pager = s.data,
                    onClickItem = { index, item ->
                        viewModel.navigationManager.navigateTo(item.destination)
                    },
                    onLongClickItem = { index, item -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = gridFocusRequester,
                    showJumpButtons = false,
                    showLetterButtons = false,
                    cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                        DiscoverItemCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = mod,
                            width = Dp.Unspecified,
                        )
                    },
                    columns = 6,
                    positionCallback = { columns, index ->
                        showHeader = index < columns
                        positionCallback.invoke(columns, index)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
