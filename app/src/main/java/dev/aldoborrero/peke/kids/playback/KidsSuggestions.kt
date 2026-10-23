package dev.aldoborrero.peke.kids.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.ItemRowFields
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Other videos to suggest in the kids player, from the server's "More like this"
 */
@HiltViewModel
class KidsSuggestionsViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _items = MutableStateFlow<List<BaseItem>>(emptyList())
        val items: StateFlow<List<BaseItem>> = _items

        private var loadedFor: UUID? = null

        fun load(item: BaseItem) {
            // Episodes have few similar items of their own, so suggest from their series
            val sourceId = item.data.seriesId ?: item.id
            if (sourceId == loadedFor) return
            loadedFor = sourceId
            viewModelScope.launch(PekeDispatchers.IO) {
                _items.value =
                    try {
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = serverRepository.currentUser?.id,
                                    itemId = sourceId,
                                    fields = ItemRowFields,
                                    limit = 20,
                                ),
                            ).content.items
                            .map { BaseItem(it) }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not load suggestions for %s", sourceId)
                        emptyList()
                    }
            }
        }

        /**
         * Replace the current video with [item]. Series go through [KidsDirectPlay] to find the episode to play.
         */
        fun open(item: BaseItem) {
            navigationManager.goBack()
            navigationManager.navigateTo(if (item.playable) Destination.Playback(item) else item.destination())
        }
    }

/**
 * @return the suggestions for [item], empty unless [enabled]
 */
@Composable
fun rememberKidsSuggestions(
    item: BaseItem?,
    enabled: Boolean,
): Pair<List<BaseItem>, (BaseItem) -> Unit> {
    // Outside kids mode there is nothing to load, so no view model is created
    if (!enabled) return emptyList<BaseItem>() to {}
    val viewModel: KidsSuggestionsViewModel = hiltViewModel()
    LaunchedEffect(item?.id) {
        if (item != null) viewModel.load(item)
    }
    val items by viewModel.items.collectAsState()
    return items to viewModel::open
}
