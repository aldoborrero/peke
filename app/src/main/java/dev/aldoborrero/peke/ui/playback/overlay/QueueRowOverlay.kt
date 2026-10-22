package dev.aldoborrero.peke.ui.playback.overlay

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.PlaylistItem
import dev.aldoborrero.peke.ui.cards.SeasonCard
import dev.aldoborrero.peke.ui.components.HiddenFocusBox
import dev.aldoborrero.peke.ui.ifElse
import dev.aldoborrero.peke.ui.playback.ControllerViewState
import dev.aldoborrero.peke.ui.tryRequestFocus

@Composable
fun QueueRowOverlay(
    queue: List<PlaylistItem>,
    controllerViewState: ControllerViewState,
    nextState: OverlayViewState,
    onChangeState: (OverlayViewState) -> Unit,
    onClickPlaylist: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    downState: OverlayViewState? = null,
) = ItemRowOverlay(
    title = stringResource(R.string.queue),
    items = remember(queue) { queue.map { it.item } },
    controllerViewState = controllerViewState,
    upState = nextState,
    downState = downState,
    onChangeState = onChangeState,
    onClickItem = onClickPlaylist,
    modifier = modifier,
)

/**
 * A row of items in the playback overlay, such as the queue
 */
@Composable
fun ItemRowOverlay(
    title: String,
    items: List<BaseItem>,
    controllerViewState: ControllerViewState,
    upState: OverlayViewState,
    downState: OverlayViewState?,
    onChangeState: (OverlayViewState) -> Unit,
    onClickItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val hiddenFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        HiddenFocusBox(hiddenFocusRequester) {
            onChangeState.invoke(upState)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(focusRequester)
                    .onFocusChanged {
                        if (it.hasFocus) {
                            controllerViewState.pulseControls()
                        }
                    },
        ) {
            itemsIndexed(items) { index, item ->
                val interactionSource =
                    remember { MutableInteractionSource() }
                val isFocused =
                    interactionSource.collectIsFocusedAsState().value
                LaunchedEffect(isFocused) {
                    if (isFocused) controllerViewState.pulseControls()
                }
                SeasonCard(
                    item = item,
                    onClick = {
                        onClickItem.invoke(item)
                        controllerViewState.hideControls()
                    },
                    onLongClick = {},
                    imageHeight = 140.dp,
                    interactionSource = interactionSource,
                    modifier =
                        Modifier.ifElse(
                            index == 0,
                            Modifier.focusRequester(focusRequester),
                        ),
                )
            }
        }
        downState?.let { OverlayStateLabel(it, onChangeState, Modifier.padding(bottom = 8.dp)) }
    }
}
