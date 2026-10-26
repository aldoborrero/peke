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
import dev.aldoborrero.peke.kids.KidsItemTypes
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
 * In kids mode, an item opened from keep watching on the home plays right away instead of showing its details page,
 * and anything kids don't get (music, people, photos) goes back with a message instead of opening a page. Opened from
 * anywhere else, kids get the item's details page.
 */
object KidsDirectPlay {
    /**
     * Where a kid's click on [item] in keep watching goes: straight back into it. An item that already goes to the
     * player (such as an extra, or one with a destination override) keeps its destination.
     */
    fun resume(item: BaseItem): Destination =
        when (val destination = item.destination()) {
            is Destination.MediaItem, is Destination.SeriesOverview -> Destination.KidsResume(destination)
            else -> destination
        }

    fun handles(destination: Destination): Boolean = destination is Destination.KidsResume || KidsItemTypes.refuses(destination)
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
                // Swap this loading page for the player, so Back returns to where the kid was. If the kid went back, or
                // time's up came on top, while it loaded, the answer is dropped (spec 005 FR-003, FR-004)
                val stillOnTop =
                    withContext(PekeDispatchers.Main) {
                        navigationManager.replaceTop(destination, item?.let { Destination.Playback(it) })
                    }
                if (item == null && stillOnTop) showToast(context, context.getString(R.string.kids_nothing_to_play))
            }
        }

        private suspend fun resolve(destination: Destination): BaseItem? =
            when (destination) {
                is Destination.KidsResume -> {
                    resolve(destination.target)
                }

                is Destination.SeriesOverview -> {
                    destination.seasonEpisode?.episodeId?.let { getItem(it) } ?: nextUp(destination.itemId)
                }

                is Destination.MediaItem -> {
                    when {
                        KidsItemTypes.refuses(destination) -> null
                        destination.type == BaseItemKind.SERIES -> nextUp(destination.itemId)
                        else -> getItem(destination.itemId)
                    }
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
