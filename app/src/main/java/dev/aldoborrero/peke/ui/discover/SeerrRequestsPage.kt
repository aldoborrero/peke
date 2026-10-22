package dev.aldoborrero.peke.ui.discover

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.api.seerr.model.MediaRequest
import dev.aldoborrero.peke.data.model.DiscoverItem
import dev.aldoborrero.peke.data.model.SeerrItemType
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SeerrServerRepository
import dev.aldoborrero.peke.services.SeerrService
import dev.aldoborrero.peke.ui.cards.DiscoverItemCard
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.detail.CardGrid
import dev.aldoborrero.peke.ui.detail.CardGridItem
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.tryRequestFocus
import dev.aldoborrero.peke.util.DataLoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SeerrRequestsViewModel
    @Inject
    constructor(
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(SeerrRequestsState.EMPTY)

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
            }
            seerrServerRepository.connection
                .onEach { user ->
                    state.update { it.copy(requests = DataLoadingState.Loading) }
                    if (user != null) {
                        val semaphore = Semaphore(3)
                        val mediaRequests =
                            seerrService.api.requestApi
                                .requestGet()
                                .results
                                .orEmpty()
                        val requests =
                            mediaRequests.mapNotNull { request ->
                                if (request.media?.tmdbId != null) {
                                    viewModelScope.async(PekeDispatchers.IO) {
                                        semaphore.withPermit {
                                            val type = SeerrItemType.fromString(request.type)
                                            when (type) {
                                                SeerrItemType.MOVIE -> {
                                                    seerrService.api.moviesApi
                                                        .movieMovieIdGet(
                                                            movieId = request.media.tmdbId,
                                                        ).let { seerrService.createDiscoverItem(it) }
                                                }

                                                SeerrItemType.TV -> {
                                                    seerrService.api.tvApi
                                                        .tvTvIdGet(tvId = request.media.tmdbId)
                                                        .let { seerrService.createDiscoverItem(it) }
                                                }

                                                SeerrItemType.PERSON -> {
                                                    null
                                                }

                                                SeerrItemType.UNKNOWN -> {
                                                    null
                                                }
                                            }?.let { RequestGridItem(request, it) }
                                        }
                                    }
                                } else {
                                    Timber.v("No TMDB ID for request %s", request.id)
                                    null
                                }
                            }
                        val results = requests.awaitAll().filterNotNull()

                        state.update { it.copy(requests = DataLoadingState.Success(results)) }
                    }
                }.catch { ex ->
                    Timber.e(ex, "Error fetching requests")
                    state.update { it.copy(requests = DataLoadingState.Error(ex)) }
                }.launchIn(viewModelScope)
        }

        fun updateBackdrop(item: DiscoverItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                }
            }
        }
    }

data class SeerrRequestsState(
    val requests: DataLoadingState<List<RequestGridItem>>,
) {
    companion object {
        val EMPTY = SeerrRequestsState(DataLoadingState.Pending)
    }
}

data class RequestGridItem(
    val request: MediaRequest,
    val item: DiscoverItem,
) : CardGridItem {
    override val gridId: String = request.id.toString()
    override val playable: Boolean = false
    override val sortName: String = request.updatedAt ?: "0000"
}

@Composable
fun SeerrRequestsPage(
    focusRequesterOnEmpty: FocusRequester?,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeerrRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState(SeerrRequestsState.EMPTY)
    val columns = 6

    when (val state = state.requests) {
        is DataLoadingState.Error -> {
            ErrorMessage(state.message, state.exception, modifier.focusable())
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier.focusable())
        }

        is DataLoadingState.Success<List<RequestGridItem>> -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (state.data.isNotEmpty()) {
                    focusRequester.tryRequestFocus()
                } else {
                    focusRequesterOnEmpty?.tryRequestFocus()
                }
            }
            Column(modifier = modifier) {
//                Text(
//                    text = stringResource(R.string.request),
//                    style = MaterialTheme.typography.displaySmall,
//                    color = MaterialTheme.colorScheme.onBackground,
//                    textAlign = TextAlign.Center,
//                    modifier = Modifier.fillMaxWidth(),
//                )
                if (state.data.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CardGrid(
                        pager = state.data,
                        onClickItem = { index: Int, item: RequestGridItem ->
                            viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item.item))
                        },
                        onLongClickItem = { index: Int, item: RequestGridItem ->
                        },
                        onClickPlay = { _, item ->
                        },
                        letterPosition = { c: Char -> 0 },
                        gridFocusRequester = focusRequester,
                        showJumpButtons = false,
                        showLetterButtons = false,
                        spacing = 16.dp,
                        cardContent = @Composable { (item, index, onClick, onLongClick, widthPx, mod) ->
                            DiscoverItemCard(
                                item = item?.item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showOverlay = true,
                                modifier = mod,
                            )
                        },
                        columns = columns,
                        positionCallback = positionCallback,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
