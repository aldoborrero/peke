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
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.filter.DefaultTvFilterOptions
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.CollectionFolderFilter
import dev.aldoborrero.peke.data.model.GetItemsFilter
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.components.CollectionFolderView
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.GenreCardGrid
import dev.aldoborrero.peke.ui.components.RecommendedTvShow
import dev.aldoborrero.peke.ui.components.StudioCardGrid
import dev.aldoborrero.peke.ui.components.TabDetails
import dev.aldoborrero.peke.ui.components.TabbedPage
import dev.aldoborrero.peke.ui.components.ViewOptionsPoster
import dev.aldoborrero.peke.ui.data.SeriesSortOptions
import dev.aldoborrero.peke.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import javax.inject.Inject

@HiltViewModel
class CollectionFolderTvViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
    ) : ViewModel()

@Composable
fun CollectionFolderTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderTvViewModel = hiltViewModel(),
) {
    val tabs =
        remember {
            listOf(
                TabDetails(R.string.recommended),
                TabDetails(R.string.library),
                TabDetails(R.string.genres),
                TabDetails(R.string.studios),
            )
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
            // Recommended
            0 -> {
                RecommendedTvShow(
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

            // Library
            1 -> {
                CollectionFolderView(
                    preferences = preferences,
                    itemId = destination.itemId,
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.SERIES),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = SeriesSortOptions,
                    filterOptions = DefaultTvFilterOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    onClickItem = onClickItem,
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Genres
            2 -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    collectionType = CollectionType.TVSHOWS,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Studios
            3 -> {
                StudioCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}
