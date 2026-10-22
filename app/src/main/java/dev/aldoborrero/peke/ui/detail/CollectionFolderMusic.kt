package dev.aldoborrero.peke.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.CollectionFolderFilter
import dev.aldoborrero.peke.data.model.GetItemsFilter
import dev.aldoborrero.peke.data.model.GetItemsFilterOverride
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.MusicService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.components.CollectionFolderView
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.GenreCardGrid
import dev.aldoborrero.peke.ui.components.GridClickActions
import dev.aldoborrero.peke.ui.components.RecommendedMusic
import dev.aldoborrero.peke.ui.components.TabDetails
import dev.aldoborrero.peke.ui.components.TabbedPage
import dev.aldoborrero.peke.ui.components.ViewOptionsSquare
import dev.aldoborrero.peke.ui.data.AlbumSortOptions
import dev.aldoborrero.peke.ui.data.ArtistSortOptions
import dev.aldoborrero.peke.ui.data.SongSortOptions
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.util.ApiRequestPager
import dev.aldoborrero.peke.util.GetItemsRequestHandler
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

@HiltViewModel(assistedFactory = CollectionFolderMusicViewModel.Factory::class)
class CollectionFolderMusicViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val musicService: MusicService,
        private val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        @Assisted private val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): CollectionFolderMusicViewModel
        }

        fun play(item: BaseItem) {
            if (item.type == BaseItemKind.AUDIO) {
                viewModelScope.launchDefault {
                    musicService.setQueue(listOf(item), false)
                }
            }
        }

        fun onClick(
            index: Int,
            item: BaseItem,
        ) {
            navigationManager.navigateTo(item.destination())
        }

        fun onClickPlayAll(shuffle: Boolean) {
            viewModelScope.launchDefault {
                val request =
                    GetItemsRequest(
                        userId = serverRepository.currentUser?.id,
                        parentId = itemId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        recursive = true,
                    )
                val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
                musicService.setQueue(pager, 0, shuffle)
            }
        }

        fun onClickPlayRemoteButton(
            index: Int,
            item: BaseItem,
        ) {
            if (item.type == BaseItemKind.AUDIO) {
                viewModelScope.launchDefault {
                    musicService.setQueue(listOf(item), false)
                }
            }
        }
    }

@Composable
fun CollectionFolderMusic(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderMusicViewModel =
        hiltViewModel<CollectionFolderMusicViewModel, CollectionFolderMusicViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
) {
    val tabs =
        remember {
            listOf(
                TabDetails(R.string.recommended),
                TabDetails(R.string.albums),
                TabDetails(R.string.artists),
                TabDetails(R.string.genres),
                TabDetails(R.string.songs),
            )
        }
    var showHeader by rememberSaveable { mutableStateOf(true) }

    val actions =
        remember {
            GridClickActions(
                onClickItem = viewModel::onClick,
                onLongClickItem = null,
                onClickPlayAll = viewModel::onClickPlayAll,
                onClickPlayRemoteButton = viewModel::onClickPlayRemoteButton,
            )
        }
    TabbedPage(
        itemId = destination.itemId.toString(),
        tabs = tabs,
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, tabDetails ->
        when (tabIndex) {
            // Recommended
            0 -> {
                RecommendedMusic(
                    preferences = preferences,
                    parentId = destination.itemId,
                    onFocusPosition = { pos ->
                        showHeader = pos.row < 1
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Albums
            1 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_albums",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = AlbumSortOptions,
                    defaultViewOptions = ViewOptionsSquare,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Artists
            2 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_artists",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    override = GetItemsFilterOverride.ARTIST,
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = ArtistSortOptions,
                    defaultViewOptions = ViewOptionsSquare,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Genres
            3 -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                    collectionType = CollectionType.MUSIC,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Songs
            4 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_songs",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = SongSortOptions,
                    defaultViewOptions = ViewOptionsSquare,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}
