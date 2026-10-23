package dev.aldoborrero.peke.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.aldoborrero.peke.data.filter.DefaultFilterOptions
import dev.aldoborrero.peke.data.filter.ItemFilterBy
import dev.aldoborrero.peke.data.model.CollectionFolderFilter
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.ui.components.CollectionFolderView
import dev.aldoborrero.peke.ui.components.ViewOptionsPoster
import dev.aldoborrero.peke.ui.components.ViewOptionsWide
import dev.aldoborrero.peke.ui.data.VideoSortOptions
import dev.aldoborrero.peke.ui.preferences.PreferencesViewModel
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID

@Composable
fun CollectionFolderGeneric(
    preferences: UserPreferences,
    itemId: UUID,
    usePosters: Boolean,
    recursive: Boolean,
    playEnabled: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    sortOptions: List<ItemSortBy> = VideoSortOptions,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    var showHeader by rememberSaveable { mutableStateOf(true) }
    val viewOptions =
        remember(usePosters) {
            if (usePosters) {
                ViewOptionsPoster
            } else {
                ViewOptionsWide
            }
        }
    CollectionFolderView(
        preferences = preferences,
        onClickItem = { index, item ->
            preferencesViewModel.navigationManager.navigateTo(item.destination(index))
        },
        itemId = itemId,
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = sortOptions,
        modifier = modifier,
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = viewOptions,
        playEnabled = playEnabled,
        filterOptions = filterOptions,
    )
}
