package dev.aldoborrero.peke.ui.detail.movie

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.ChosenStreams
import dev.aldoborrero.peke.data.ExtrasItem
import dev.aldoborrero.peke.data.ItemPlaybackRepository
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.Chapter
import dev.aldoborrero.peke.data.model.ItemPlayback
import dev.aldoborrero.peke.data.model.Person
import dev.aldoborrero.peke.data.model.Trailer
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.ExtrasService
import dev.aldoborrero.peke.services.FavoriteWatchManager
import dev.aldoborrero.peke.services.MediaManagementService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.PeopleFavorites
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.StreamChoiceService
import dev.aldoborrero.peke.services.TrailerService
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.services.deleteItem
import dev.aldoborrero.peke.ui.ItemRowFields
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.letNotEmpty
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.util.DataLoadingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = MovieViewModel.Factory::class)
class MovieViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val itemPlaybackRepository: ItemPlaybackRepository,
        val streamChoiceService: StreamChoiceService,
        val serverReportService: ServerReportService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val peopleFavorites: PeopleFavorites,
        private val trailerService: TrailerService,
        private val extrasService: ExtrasService,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val mediaManagementService: MediaManagementService,
        @Assisted val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): MovieViewModel
        }

        private val _state = MutableStateFlow(MovieState())
        val state: StateFlow<MovieState> = _state

        init {
            init()
            viewModelScope.launchDefault {
                mediaManagementService.collectCanDelete(state.map { it.movie }) { canDelete ->
                    _state.update {
                        it.copy(canDelete = canDelete)
                    }
                }
            }
        }

        private suspend fun getMovie(): BaseItem {
            val item =
                api.userLibraryApi.getItem(itemId).content.let {
                    BaseItem(it)
                }
            return item
        }

        fun init(): Job =
            viewModelScope.launchDefault {
                val movie =
                    try {
                        getMovie()
                    } catch (ex: Exception) {
                        Timber.e(ex, "Failed to fetch movie %s", itemId)
                        _state.update { it.copy(loading = DataLoadingState.Error(ex)) }
                        return@launchDefault
                    }
                val chosenStreams =
                    itemPlaybackRepository.getSelectedTracks(
                        itemId,
                        movie,
                        userPreferencesService.getCurrent(),
                    )
                val remoteTrailers = trailerService.getRemoteTrailers(movie)
                val chapters = Chapter.fromDto(movie.data)
                _state.update {
                    it.copy(
                        loading = DataLoadingState.Success(movie),
                        chosenStreams = chosenStreams,
                        trailers = remoteTrailers,
                        chapters = chapters,
                    )
                }
                backdropService.submit(movie)
                viewModelScope.launchIO {
                    trailerService.getLocalTrailers(movie).letNotEmpty { localTrailers ->
                        _state.update {
                            it.copy(
                                trailers = localTrailers + remoteTrailers,
                            )
                        }
                    }
                }
                viewModelScope.launchIO {
                    val people = peopleFavorites.getPeopleFor(movie)
                    _state.update {
                        it.copy(
                            people = people,
                        )
                    }
                }
                viewModelScope.launchIO {
                    val extras = extrasService.getExtras(itemId)
                    _state.update {
                        it.copy(
                            extras = extras,
                        )
                    }
                }

                if (state.value.similar.isEmpty()) {
                    val similar =
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = serverRepository.currentUser?.id,
                                    itemId = itemId,
                                    fields = ItemRowFields,
                                    limit = 25,
                                ),
                            ).content.items
                            .map { BaseItem(it) }

                    _state.update { it.copy(similar = similar) }
                }
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launchDefault {
            try {
                favoriteWatchManager.setWatched(itemId, played)
                getMovie().let { movie ->
                    _state.update {
                        it.copy(loading = DataLoadingState.Success(movie))
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error updating watch status for movie %s", itemId)
                showToast(context, "Something went wrong...")
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launchDefault {
            try {
                favoriteWatchManager.setFavorite(itemId, favorite)
                val movie = getMovie()
                _state.update {
                    it.copy(loading = DataLoadingState.Success(movie))
                }
                if (itemId != movie.id) {
                    viewModelScope.launchIO {
                        val people = peopleFavorites.getPeopleFor(movie)
                        _state.update { it.copy(people = people) }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error updating favorite  %s", itemId)
                showToast(context, "Something went wrong...")
            }
        }

        fun savePlayVersion(
            item: BaseItem,
            sourceId: UUID,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val result = itemPlaybackRepository.savePlayVersion(item.id, sourceId)
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, prefs)
                    }
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun saveTrackSelection(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            trackIndex: Int,
            type: MediaStreamType,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val result =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = itemPlayback,
                        trackIndex = trackIndex,
                        type = type,
                    )
                val chosen = itemPlaybackRepository.getChosenItemFromPlayback(item, result, prefs)
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        fun clearChosenStreams(chosenStreams: ChosenStreams?) {
            viewModelScope.launchIO {
                itemPlaybackRepository.deleteChosenStreams(chosenStreams)
                state.value.movie?.let { item ->
                    val result =
                        itemPlaybackRepository.getSelectedTracks(
                            itemId,
                            item,
                            userPreferencesService.getCurrent(),
                        )
                    _state.update { it.copy(chosenStreams = result) }
                }
            }
        }

        fun deleteItem(item: BaseItem) {
            deleteItem(context, mediaManagementService, item) {
                navigationManager.goBack()
            }
        }
    }

data class MovieState(
    val loading: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val trailers: List<Trailer> = emptyList(),
    val people: List<Person> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val extras: List<ExtrasItem> = emptyList(),
    val similar: List<BaseItem> = emptyList(),
    val chosenStreams: ChosenStreams? = null,
    val canDelete: Boolean = false,
) {
    val movie: BaseItem? = (loading as? DataLoadingState.Success<BaseItem>)?.data
}
