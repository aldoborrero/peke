package dev.aldoborrero.peke.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.ui.Cards
import dev.aldoborrero.peke.ui.cards.GenreCard
import dev.aldoborrero.peke.ui.cards.ItemRow
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.Genre
import dev.aldoborrero.peke.ui.util.StringProvider
import dev.aldoborrero.peke.util.DataLoadingState

@Composable
fun DiscoverGenreRow(
    title: StringProvider,
    items: DataLoadingState<List<Genre>>,
    onClickItem: (Int, Genre) -> Unit,
    onCardFocus: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLongClickItem: (Int, Genre) -> Unit = { _, _ -> },
) {
    when (items) {
        is DataLoadingState.Error -> {
            ErrorMessage(items.message, items.exception, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                Text(
                    text = title.getString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is DataLoadingState.Success<List<Genre>> -> {
            ItemRow(
                title = title.getString(),
                items = items.data,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                cardContent = { index, item, mod, onClick, onLongClick ->
                    GenreCard(
                        genre = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        modifier =
                            mod
                                .height(Cards.heightEpisode)
                                .onFocusChanged {
                                    if (it.isFocused) onCardFocus.invoke(index)
                                },
                    )
                },
                modifier = modifier,
                horizontalPadding = 16.dp,
                showViewMore = false,
                viewMoreCardContent = {},
            )
        }
    }
}
