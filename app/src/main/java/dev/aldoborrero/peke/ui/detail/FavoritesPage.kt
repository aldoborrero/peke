package dev.aldoborrero.peke.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.filter.DefaultForFavoritesFilterOptions
import dev.aldoborrero.peke.data.model.CollectionFolderFilter
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.ui.KidsEmptyMessage
import dev.aldoborrero.peke.kids.ui.isKidScreen
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.ui.OneTimeLaunchedEffect
import dev.aldoborrero.peke.ui.components.CollectionFolderViewContent
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.GridClickActions
import dev.aldoborrero.peke.ui.components.KeyedTabbedPage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.components.defaultViewOptions
import dev.aldoborrero.peke.ui.components.rememberContextMenu
import dev.aldoborrero.peke.ui.data.rememberSortOptions
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.tryRequestFocus

@Composable
fun FavoritesPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect { viewModel.init() }
    val state by viewModel.state.collectAsState()

    var showTabs by rememberSaveable { mutableStateOf(true) }

    when (val s = state.loadingState) {
        is FavoritesLoadingState.Error -> {
            ErrorMessage(s.message, s.exception, modifier)
        }

        FavoritesLoadingState.Loading,
        FavoritesLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        FavoritesLoadingState.NoFavorites -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    modifier
                        .focusRequester(focusRequester)
                        .focusable(),
            ) {
                if (isKidScreen()) {
                    // The buddy and how to add a favourite
                    KidsEmptyMessage(R.string.kids_favourites_hint)
                } else {
                    Text(
                        text = stringResource(R.string.no_favorites_found),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier,
                    )
                }
            }
        }

        FavoritesLoadingState.Success -> {
//    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            val kidName = (LocalKidsMode.current as? KidsModeState.Active)?.user?.name
            Column(modifier = modifier) {
                if (kidName != null) KidsFavouritesHeader(kidName)
                KeyedTabbedPage(
                    selectedTabKey = state.tabKey,
                    updateSelectedTabKey = viewModel::updateSelectedTabKey,
                    tabs = state.tabs,
                    showTabs = showTabs,
                    isShowClock = state.isShowClock,
                    modifier = Modifier.weight(1f),
                ) { type, tabDetails ->
                    val collectionState = state.favorites[type]
                    if (collectionState != null) {
                        val provider = remember(type) { viewModel.createTypedProvider(type) }
                        val contextMenu = rememberContextMenu(preferences, provider)
                        val actions =
                            remember(type) {
                                GridClickActions(
                                    onClickItem = { _, item ->
                                        viewModel.navigationManager.navigateTo(
                                            item.destination(),
                                        )
                                    },
                                    onLongClickItem = contextMenu::showContextMenu,
                                )
                            }
                        CollectionFolderViewContent(
                            preferences = preferences,
                            state = collectionState,
                            savedPosition = 0,
                            itemId = remember { viewModel.libraryDisplayItemId(type) },
                            initialFilter = CollectionFolderFilter(),
                            recursive = true,
                            actions = actions,
                            sortOptions = rememberSortOptions(type),
                            // TODO playEnabled = true for movies & episodes
                            playEnabled = false,
                            defaultViewOptions = type.defaultViewOptions,
                            viewActions = provider,
                            provider = provider,
                            showTitle = false,
                            positionCallback = { columns, index ->
                                showTabs = index < columns
                            },
                            focusRequesterOnEmpty = null,
                            filterOptions = DefaultForFavoritesFilterOptions,
                        )

                        contextMenu.Compose()
                    } else {
                        ErrorMessage("Invalid tab $type", null)
                    }
                }
            }
        }
    }
}

/**
 * KFavoritesPekeTV's title: Peke's heart and "{Kid}'s favourites", and how to add one
 */
@Composable
private fun KidsFavouritesHeader(kidName: String) {
    val colors = LocalPekeColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.cpx),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 48.cpx, top = 44.cpx, bottom = 8.dp),
    ) {
        Icon(
            painter = painterResource(PekeIcon.FAVOURITES.outline),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(52.cpx),
        )
        Text(
            text = stringResource(R.string.kids_favourites_title, kidName),
            style = MaterialTheme.typography.displaySmall,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .background(colors.surface, RoundedCornerShape(24.cpx))
                    .padding(horizontal = 22.cpx, vertical = 16.cpx),
        ) {
            Icon(
                painter = painterResource(PekeIcon.FAVOURITES.outline),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(30.cpx),
            )
            Text(
                text = stringResource(R.string.kids_favourites_hint),
                fontSize = 19.csp,
                fontWeight = FontWeight.W800,
                color = colors.text,
            )
        }
    }
}
