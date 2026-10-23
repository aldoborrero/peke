package dev.aldoborrero.peke.ui.detail

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.FavoriteWatchManager
import dev.aldoborrero.peke.services.HomeSettingsService
import dev.aldoborrero.peke.services.MediaManagementService
import dev.aldoborrero.peke.services.MusicService
import dev.aldoborrero.peke.services.NavDrawerService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.StreamChoiceService
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.services.deleteItem
import dev.aldoborrero.peke.services.tvAccess
import dev.aldoborrero.peke.ui.cards.GridCard
import dev.aldoborrero.peke.ui.components.ContextMenuProvider
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.GenreCardGrid
import dev.aldoborrero.peke.ui.components.GridTitle
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.components.StudioCardGrid
import dev.aldoborrero.peke.ui.components.rememberContextMenu
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.playback.scale
import dev.aldoborrero.peke.ui.tryRequestFocus
import dev.aldoborrero.peke.ui.util.StringProvider
import dev.aldoborrero.peke.ui.util.StringStringProvider
import dev.aldoborrero.peke.util.ApiRequestPager
import dev.aldoborrero.peke.util.ExceptionHandler
import dev.aldoborrero.peke.util.HomeRowLoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = HomeRowGridViewModel.Factory::class)
class HomeRowGridViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val navigationManager: NavigationManager,
        private val mediaManagementService: MediaManagementService,
        private val musicService: MusicService,
        val streamChoiceService: StreamChoiceService,
        val serverReportService: ServerReportService,
        @Assisted private val title: StringProvider,
        @Assisted private val rowConfig: HomeRowConfig,
    ) : ViewModel(),
        ContextMenuProvider {
        @AssistedFactory
        interface Factory {
            fun create(
                title: StringProvider,
                rowConfig: HomeRowConfig,
            ): HomeRowGridViewModel
        }

        private val _state = MutableStateFlow(HomeRowGridState())
        val state: StateFlow<HomeRowGridState> = _state

        init {
            viewModelScope.launchDefault {
                when (rowConfig) {
                    is HomeRowConfig.Genres,
                    is HomeRowConfig.Studios,
                    -> {
                        // Genres & Studios will be looked up by another ViewModel, so just return
                        _state.update {
                            it.copy(
                                loading =
                                    HomeRowLoadingState.Success(
                                        title,
                                        emptyList(),
                                        rowConfig.viewOptions,
                                    ),
                            )
                        }
                        return@launchDefault
                    }

                    else -> {}
                }
                try {
                    val preferences = userPreferencesService.getCurrent()
                    val prefs = preferences.appPreferences.homePagePreferences
                    serverRepository.currentUserDto?.let { userDto ->
                        val libraries =
                            navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                        val result =
                            homeSettingsService.fetchDataForRow(
                                row = rowConfig,
                                scope = viewModelScope,
                                prefs = prefs,
                                userDto = userDto,
                                libraries = libraries,
                                isRefresh = true,
                                limit = 1_000, // TODO
                                usePaging = true,
                            )
                        Timber.v(
                            "Got %s items for %s",
                            (result as? HomeRowLoadingState.Success)?.items?.size,
                            rowConfig,
                        )
                        _state.update { it.copy(loading = result) }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching: %s", rowConfig)
                    _state.update {
                        it.copy(
                            loading =
                                HomeRowLoadingState.Error(
                                    title,
                                    null,
                                    ex,
                                ),
                        )
                    }
                }
            }
        }

        override fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                favoriteWatchManager.setWatched(itemId, played)
                (state.value.loading as? HomeRowLoadingState.Success)?.let {
                    (it.items as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
                (state.value.loading as? HomeRowLoadingState.Success)?.let {
                    (it.items as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

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
                    // TODO refresh
                }
            }
        }
    }

data class HomeRowGridState(
    val loading: HomeRowLoadingState = HomeRowLoadingState.Pending(StringStringProvider("")),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeRowGrid(
    preferences: UserPreferences,
    destination: Destination.MoreHomeRow,
    modifier: Modifier = Modifier,
    viewModel: HomeRowGridViewModel =
        hiltViewModel<HomeRowGridViewModel, HomeRowGridViewModel.Factory>(
            creationCallback = { it.create(destination.title, destination.config) },
        ),
) {
    val state by viewModel.state.collectAsState()
    val contextMenu = rememberContextMenu(preferences, viewModel)
    val gridFocusRequester = remember { FocusRequester() }
    val viewOptions = destination.config.viewOptions

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        GridTitle(destination.title.getString())

        when (val st = state.loading) {
            is HomeRowLoadingState.Error -> {
                ErrorMessage(st.message, st.exception, Modifier.fillMaxSize())
            }

            is HomeRowLoadingState.Loading,
            is HomeRowLoadingState.Pending,
            -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            is HomeRowLoadingState.Success -> {
                when (destination.config) {
                    is HomeRowConfig.Genres -> {
                        GenreCardGrid(
                            itemId = destination.config.parentId,
                            includeItemTypes = null,
                            modifier = Modifier.fillMaxSize(),
                            initialPosition = destination.initialPosition,
                            collectionType = CollectionType.UNKNOWN,
                        )
                    }

                    is HomeRowConfig.Studios -> {
                        StudioCardGrid(
                            itemId = destination.config.parentId,
                            includeItemTypes = null,
                            modifier = Modifier.fillMaxSize(),
                            initialPosition = destination.initialPosition,
                        )
                    }

                    else -> {
                        val onClickItem =
                            remember {
                                { index: Int, item: BaseItem ->
                                    viewModel.navigateTo(item.destination(index))
                                }
                            }
                        LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                        CardGrid(
                            pager = st.items,
                            onClickItem = onClickItem,
                            onLongClickItem = contextMenu::showContextMenu,
                            onClickPlay = { _, _ -> },
                            letterPosition = { -1 },
                            gridFocusRequester = gridFocusRequester,
                            showJumpButtons = false,
                            showLetterButtons = false,
                            modifier = Modifier.fillMaxSize(),
                            initialPosition = destination.initialPosition,
                            positionCallback = { columns, newPosition ->
                            },
                            cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                                GridCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    imageContentScale = viewOptions.contentScale.scale,
                                    imageAspectRatio = viewOptions.aspectRatio.ratio,
                                    imageType = viewOptions.imageType,
                                    showTitle = true, // viewOptions.showTitles,
                                    fillWidth = widthPx,
                                    modifier = mod,
                                )
                            },
                            columns = if (viewOptions.aspectRatio.ratio > 1f) 4 else 6,
                            spacing = viewOptions.spacing.dp,
                            bringIntoViewSpec = LocalBringIntoViewSpec.current,
                        )
                    }
                }
            }
        }
    }
    contextMenu.Compose()
}
