package dev.aldoborrero.peke.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.aldoborrero.peke.data.model.CollectionFolderFilter
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.ui.components.CollectionFolderView
import dev.aldoborrero.peke.ui.components.ViewOptionsSquare
import dev.aldoborrero.peke.ui.data.PlaylistSortOptions
import dev.aldoborrero.peke.ui.preferences.PreferencesViewModel
import java.util.UUID

@Composable
fun CollectionFolderPlaylist(
    preferences: UserPreferences,
    itemId: UUID,
    recursive: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    var showHeader by rememberSaveable { mutableStateOf(true) }
    CollectionFolderView(
        preferences = preferences,
        onClickItem = { _, item -> preferencesViewModel.navigationManager.navigateTo(item.destination()) },
        itemId = itemId,
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = PlaylistSortOptions,
        modifier = modifier,
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = ViewOptionsSquare,
        playEnabled = false,
    )
}
