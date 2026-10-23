package dev.aldoborrero.peke.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.CollectionFolderFilter
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.components.CollectionFolderView
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.components.TabDetails
import dev.aldoborrero.peke.ui.components.TabbedPage
import dev.aldoborrero.peke.ui.components.ViewOptions
import dev.aldoborrero.peke.ui.data.VideoSortOptions
import dev.aldoborrero.peke.ui.detail.livetv.DvrSchedule
import dev.aldoborrero.peke.ui.detail.livetv.TvGuideGrid
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.util.LocalInterfaceCustomization
import dev.aldoborrero.peke.ui.util.RestrictedFeature
import dev.aldoborrero.peke.ui.util.StringStringProvider
import dev.aldoborrero.peke.util.DataLoadingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LiveTvCollectionViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(LiveTvRecordingFolderState())

        init {
            viewModelScope.launchIO {
                try {
                    val folders =
                        api.liveTvApi
                            .getRecordingFolders(userId = serverRepository.currentUser?.id)
                            .content.items
                            .map { TabId(it.name ?: "Recordings", it.id) }
                    state.value =
                        LiveTvRecordingFolderState(DataLoadingState.Success(folders))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching recording folders")
                    state.value =
                        LiveTvRecordingFolderState(DataLoadingState.Error(ex))
                }
            }
        }
    }

data class LiveTvRecordingFolderState(
    val folders: DataLoadingState<List<TabId>> = DataLoadingState.Pending,
)

data class TabId(
    val title: String,
    val id: UUID,
)

@Composable
fun CollectionFolderLiveTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: LiveTvCollectionViewModel = hiltViewModel(),
) {
    if (!LocalInterfaceCustomization.current.allows(RestrictedFeature.LIVE_TV_RECORDINGS)) {
        // The guide alone: no DVR schedule and no recording folders
        TvGuideGrid(
            preferences = preferences,
            onRowPosition = {},
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val state by viewModel.state.collectAsState()

    when (val st = state.folders) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<List<TabId>> -> {
            val tabs =
                remember {
                    listOf(
                        TabDetails(R.string.tv_guide),
                        TabDetails(R.string.tv_dvr_schedule),
                    ) +
                        st.data.map {
                            TabDetails(StringStringProvider(it.title))
                        }
                }
            val onClickItem =
                remember {
                    { position: Int, item: BaseItem ->
                        viewModel.navigationManager.navigateTo(item.destination())
                    }
                }

            var showHeader by rememberSaveable { mutableStateOf(true) }
            TabbedPage(
                itemId = destination.itemId.toString(),
                tabs = tabs,
                modifier = modifier,
                showTabs = showHeader,
            ) { tabIndex, tabDetails ->
                when (tabIndex) {
                    0 -> {
                        TvGuideGrid(
                            preferences = preferences,
                            onRowPosition = {
                                showHeader = it <= 0
                            },
                            Modifier
                                .fillMaxSize()
                                .focusRequester(tabDetails.contentFocusRequester),
                        )
                    }

                    1 -> {
                        DvrSchedule(
                            requestFocusAfterLoading = true,
                            focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .focusRequester(tabDetails.contentFocusRequester),
                        )
                    }

                    else -> {
                        val folderIndex = tabIndex - 2
                        if (folderIndex in st.data.indices) {
                            CollectionFolderView(
                                preferences = preferences,
                                onClickItem = onClickItem,
                                itemId = st.data[folderIndex].id,
                                initialFilter = CollectionFolderFilter(),
                                showTitle = false,
                                recursive = false,
                                sortOptions = VideoSortOptions,
                                modifier = Modifier.focusRequester(tabDetails.contentFocusRequester),
                                positionCallback = { columns, position ->
                                    showHeader = position < columns
                                },
                                playEnabled = false,
                                defaultViewOptions = ViewOptions(),
                                focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                            )
                        } else {
                            ErrorMessage("Invalid tab index $tabIndex", null)
                        }
                    }
                }
            }
        }
    }
}
