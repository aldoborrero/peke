package dev.aldoborrero.peke.ui.detail.discover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.api.seerr.model.MediaInfo
import dev.aldoborrero.peke.api.seerr.model.MediaRequest
import dev.aldoborrero.peke.api.seerr.model.MovieDetails
import dev.aldoborrero.peke.api.seerr.model.RelatedVideo
import dev.aldoborrero.peke.api.seerr.model.RequestPostRequest
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.DiscoverItem
import dev.aldoborrero.peke.data.model.DiscoverRating
import dev.aldoborrero.peke.data.model.RemoteTrailer
import dev.aldoborrero.peke.data.model.RequestStatus
import dev.aldoborrero.peke.data.model.SeerrItemType
import dev.aldoborrero.peke.data.model.SeerrPermission
import dev.aldoborrero.peke.data.model.Trailer
import dev.aldoborrero.peke.data.model.hasPermission
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SeerrServerRepository
import dev.aldoborrero.peke.services.SeerrService
import dev.aldoborrero.peke.services.SeerrUserConfig
import dev.aldoborrero.peke.ui.equalsNotNull
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.util.DataLoadingState
import dev.aldoborrero.peke.util.LoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.successValue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel(assistedFactory = DiscoverMovieViewModel.Factory::class)
class DiscoverMovieViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        val serverRepository: ServerRepository,
        val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        @Assisted val item: DiscoverItem,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(item: DiscoverItem): DiscoverMovieViewModel
        }

        private val _state = MutableStateFlow(DiscoverMovieState())
        val state: StateFlow<DiscoverMovieState> = _state

        val userConfig = seerrServerRepository.current.map { it?.config }
        val request4kEnabled =
            seerrServerRepository.current.map { it?.request4kMovieEnabled ?: false }

        init {
            init()
        }

        private fun fetchAndSetItem(): Deferred<MovieDetails?> =
            viewModelScope.async(PekeDispatchers.IO) {
                try {
                    val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id)
                    _state.update { it.copy(movie = DataLoadingState.Success(movie)) }
                    movie
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating movie details")
                    null
                }
            }

        fun init(): Job =
            viewModelScope.launchIO {
                Timber.v("Init for movie %s", item.id)
                try {
                    val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id)
                    _state.update { it.copy(movie = DataLoadingState.Success(movie)) }
                    val discoveredItem = seerrService.createDiscoverItem(movie)
                    backdropService.submit(discoveredItem)

                    updateCanCancel()

                    viewModelScope.launchIO {
                        val rating =
                            getDiscoverRating(item.id) {
                                DiscoverRating(
                                    seerrService.api.moviesApi.movieMovieIdRatingsGet(movieId = item.id),
                                )
                            }
                        _state.update { it.copy(rating = rating) }
                    }
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.moviesApi
                                    .movieMovieIdSimilarGet(movieId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(similar = result) }
                        }
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.moviesApi
                                    .movieMovieIdRecommendationsGet(movieId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(recommended = result) }
                        }
                    }
                    val people =
                        movie.credits
                            ?.cast
                            ?.map { seerrService.createDiscoverItem(it) }
                            .orEmpty() +
                            movie.credits
                                ?.crew
                                ?.map { seerrService.createDiscoverItem(it) }
                                .orEmpty()
                    _state.update { it.copy(people = people) }
                    val trailers =
                        movie.relatedVideos
                            ?.filter { it.type == RelatedVideo.Type.TRAILER }
                            ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                            ?.map {
                                RemoteTrailer(it.name!!, it.url!!, it.site)
                            }.orEmpty()
                    _state.update { it.copy(trailers = trailers) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating movie details")
                    _state.update { it.copy(movie = DataLoadingState.Error(ex)) }
                }
            }

        private suspend fun updateCanCancel() {
            val user = userConfig.firstOrNull()
            val canCancel =
                canUserCancelRequest(
                    user,
                    state.value.movie.successValue
                        ?.mediaInfo
                        ?.requests,
                )
            _state.update { it.copy(canCancelRequest = canCancel) }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        fun goTo(
            mediaInfo: MediaInfo?,
            type: BaseItemKind,
        ) {
            viewModelScope.launchDefault {
                goToButtonDiscover(mediaInfo, type, context, navigationManager)
            }
        }

        fun request(request: MovieRequest) {
            viewModelScope.launchIO {
                if (request.movieId == null) {
                    Timber.w("Null movie ID")
                    return@launchIO
                }
                try {
                    seerrService.api.requestApi.requestPost(
                        RequestPostRequest(
                            is4k = request.is4k,
                            mediaId = request.movieId,
                            mediaType = RequestPostRequest.MediaType.MOVIE,
                            serverId =
                                when {
                                    request.profileId == null && request.folder == null -> null
                                    request.is4k -> request.data.server4kId
                                    else -> request.data.serverId
                                },
                            profileId = request.profileId,
                            rootFolder = request.folder,
                            tags = emptyList(),
                        ),
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error requesting %s", request.movieId)
                    showToast(context, "An error occurred")
                }
                fetchAndSetItem().await()
                updateCanCancel()
            }
        }

        fun cancelRequest(id: Int) {
            viewModelScope.launchIO {
                state.value.movie.successValue?.mediaInfo?.requests?.firstOrNull()?.let {
                    // TODO handle multiple requests? Or just delete self's request?
                    try {
                        seerrService.api.requestApi.requestRequestIdDelete(it.id.toString())
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error requesting %s", id)
                        showToast(context, "An error occurred")
                    }
                    fetchAndSetItem().await()
                    updateCanCancel()
                }
            }
        }

        fun requestOnClick() {
            viewModelScope.launchIO {
                try {
                    val data = seerrService.getProfilesAndFolders(SeerrItemType.MOVIE)
                    _state.update {
                        it.copy(
                            profileLoading = LoadingState.Success,
                            requestData = data,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting profiles & folders")
                    showToast(context, "Error getting profiles & folders: ${ex.localizedMessage}")
                    _state.update {
                        it.copy(
                            profileLoading = LoadingState.Success,
                        )
                    }
                }
            }
        }
    }

fun canUserCancelRequest(
    user: SeerrUserConfig?,
    requests: List<MediaRequest>?,
) = (user.hasPermission(SeerrPermission.MANAGE_REQUESTS) && requests?.isNotEmpty() == true) ||
    (
        // User requested this
        user.hasPermission(SeerrPermission.REQUEST) &&
            requests
                .orEmpty()
                .any {
                    equalsNotNull(it.requestedBy?.id, user?.id) &&
                        RequestStatus.from(it.status) < RequestStatus.APPROVED
                }
    )

data class DiscoverMovieState(
    val movie: DataLoadingState<MovieDetails> = DataLoadingState.Pending,
    val rating: DiscoverRating? = null,
    val seasons: List<RequestSeason> = emptyList(),
    val trailers: List<Trailer> = emptyList(),
    val people: List<DiscoverItem> = emptyList(),
    val similar: List<DiscoverItem> = emptyList(),
    val recommended: List<DiscoverItem> = emptyList(),
    val canCancelRequest: Boolean = false,
    val profileLoading: LoadingState = LoadingState.Pending,
    val requestData: SeerrRequestData = SeerrRequestData(),
)
