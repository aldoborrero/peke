package dev.aldoborrero.peke.ui.discover

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.TabDetails
import dev.aldoborrero.peke.ui.components.TabbedPage
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.nav.NavDrawerItem
import dev.aldoborrero.peke.util.DiscoverRequestType

@Composable
fun DiscoverPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
) {
    val tabs =
        remember {
            listOf(
                TabDetails(R.string.discover),
                TabDetails(R.string.request),
                TabDetails(R.string.search),
                TabDetails(R.string.movies_title),
                TabDetails(R.string.tv_shows_title),
            )
        }
    var showHeader by rememberSaveable { mutableStateOf(true) }

    TabbedPage(
        itemId = NavDrawerItem.Discover.id,
        tabs = tabs,
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, tabDetails ->
        when (tabIndex) {
            // Discover
            0 -> {
                SeerrDiscoverPage(
                    preferences = preferences,
                    positionCallback = { showHeader = it.row < 1 },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Requests
            1 -> {
                SeerrRequestsPage(
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                    positionCallback = { columns, index -> showHeader = index < columns },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Search
            2 -> {
                DiscoverSearchPage(
                    preferences = preferences,
                    positionCallback = { columns, index -> showHeader = index < columns },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Movies
            3 -> {
                DiscoverRequestGrid(
                    viewModelKey = "movies",
                    showTitle = false,
                    destination =
                        Destination.DiscoverMoreResult(
                            DiscoverRequestType.DISCOVER_MOVIES,
                            0,
                        ),
                    positionCallback = { columns, index -> showHeader = index < columns },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // TV
            4 -> {
                DiscoverRequestGrid(
                    viewModelKey = "tv",
                    showTitle = false,
                    destination =
                        Destination.DiscoverMoreResult(
                            DiscoverRequestType.DISCOVER_TV,
                            0,
                        ),
                    positionCallback = { columns, index -> showHeader = index < columns },
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
