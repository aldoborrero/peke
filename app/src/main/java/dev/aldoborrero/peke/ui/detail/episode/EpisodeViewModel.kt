package dev.aldoborrero.peke.ui.detail.episode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.ChosenStreams
import dev.aldoborrero.peke.data.ItemPlaybackRepository
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.ItemPlayback
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.FavoriteWatchManager
import dev.aldoborrero.peke.services.MediaManagementService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.StreamChoiceService
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.services.deleteItem
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.util.DataLoadingState
import dev.aldoborrero.peke.util.ExceptionHandler
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = EpisodeViewModel.Factory::class)
class EpisodeViewModel
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
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val mediaManagementService: MediaManagementService,
        @Assisted val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): EpisodeViewModel
        }

        private val _state = MutableStateFlow(EpisodeState())
        val state: StateFlow<EpisodeState> = _state

        val canDelete = MutableStateFlow(false)

        init {
            init()
            viewModelScope.launchDefault {
                mediaManagementService.collectCanDelete(
                    state.map { (it.episode as? DataLoadingState.Success<BaseItem?>)?.data },
                ) { canDelete ->
                    this@EpisodeViewModel.canDelete.update { canDelete }
                }
            }
        }

        private fun fetchAndSetItem() {
            viewModelScope.launchIO {
                try {
                    val item =
                        api.userLibraryApi.getItem(itemId).content.let {
                            BaseItem.from(it, api)
                        }
                    _state.update { it.copy(episode = DataLoadingState.Success(item)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting episode %s", itemId)
                    showToast(context, "Error updating episode")
                }
            }
        }

        fun init(): Job =
            viewModelScope.launchIO {
                try {
                    val prefs = userPreferencesService.getCurrent()
                    val item =
                        api.userLibraryApi.getItem(itemId).content.let {
                            BaseItem(it)
                        }
                    val chosenStreams =
                        itemPlaybackRepository.getSelectedTracks(item.id, item, prefs)
                    _state.update {
                        it.copy(
                            episode = DataLoadingState.Success(item),
                            chosenStreams = chosenStreams,
                        )
                    }
                    backdropService.submit(item)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting episode %s", itemId)
                    _state.update { it.copy(episode = DataLoadingState.Error(ex)) }
                }
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            fetchAndSetItem()
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            fetchAndSetItem()
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
                state.value.episode.let { item ->
                    if (item is DataLoadingState.Success<BaseItem>) {
                        val result =
                            itemPlaybackRepository.getSelectedTracks(
                                itemId,
                                item.data,
                                userPreferencesService.getCurrent(),
                            )
                        _state.update { it.copy(chosenStreams = result) }
                    }
                }
            }
        }

        fun deleteItem(item: BaseItem) {
            deleteItem(context, mediaManagementService, item) {
                navigationManager.goBack()
            }
        }
    }

data class EpisodeState(
    val episode: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val chosenStreams: ChosenStreams? = null,
)
