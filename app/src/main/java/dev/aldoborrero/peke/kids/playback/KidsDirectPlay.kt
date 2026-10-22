package dev.aldoborrero.peke.kids.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.PlaylistCreator
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * In kids mode, opening a video or a series plays it right away instead of showing its details page
 */
object KidsDirectPlay {
    private val PLAYABLE = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO, BaseItemKind.MUSIC_VIDEO)

    fun handles(destination: Destination): Boolean =
        when (destination) {
            is Destination.MediaItem -> destination.type in PLAYABLE || destination.type == BaseItemKind.SERIES
            is Destination.SeriesOverview -> true
            else -> false
        }
}

@HiltViewModel
class KidsDirectPlayViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val playlistCreator: PlaylistCreator,
    ) : ViewModel() {
        fun play(destination: Destination) {
            viewModelScope.launch(PekeDispatchers.IO) {
                val item =
                    try {
                        resolve(destination)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not resolve %s", destination)
                        null
                    }
                withContext(PekeDispatchers.Main) {
                    // Swap the details page for the player, so Back returns to where the kid was
                    navigationManager.goBack()
                    if (item != null) navigationManager.navigateTo(Destination.Playback(item))
                }
                if (item == null) showToast(context, context.getString(R.string.kids_nothing_to_play))
            }
        }

        private suspend fun resolve(destination: Destination): BaseItem? =
            when (destination) {
                is Destination.SeriesOverview -> {
                    destination.seasonEpisode?.episodeId?.let { getItem(it) } ?: nextUp(destination.itemId)
                }

                is Destination.MediaItem -> {
                    if (destination.type == BaseItemKind.SERIES) nextUp(destination.itemId) else getItem(destination.itemId)
                }

                else -> {
                    null
                }
            }

        private suspend fun getItem(itemId: UUID): BaseItem =
            BaseItem(api.userLibraryApi.getItem(itemId = itemId, userId = serverRepository.currentUser?.id).content)

        /**
         * Same choice as the series Play button, see [PlaylistCreator.nextUpEpisode]
         */
        private suspend fun nextUp(seriesId: UUID): BaseItem? = playlistCreator.nextUpEpisode(seriesId)?.let { getItem(it.id) }
    }

@Composable
fun KidsDirectPlayPage(
    destination: Destination,
    modifier: Modifier = Modifier,
    viewModel: KidsDirectPlayViewModel = hiltViewModel(),
) {
    LaunchedEffect(destination) { viewModel.play(destination) }
    LoadingPage(modifier)
}
