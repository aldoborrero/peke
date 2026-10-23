package dev.aldoborrero.peke.ui.main.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.ui.formatTypeName
import dev.aldoborrero.peke.ui.ifElse
import dev.aldoborrero.peke.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun HomeSettingsFavoriteList(
    favoriteTypeOptions: List<BaseItemKind>,
    onClick: (BaseItemKind) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        TitleText(
            stringResource(R.string.add_row_for, stringResource(R.string.favorites)),
        )
        HomeSettingsLazyColumn(
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(favoriteTypeOptions) { index, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(formatTypeName(type)),
                    onClick = { onClick.invoke(type) },
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
        }
    }
}
