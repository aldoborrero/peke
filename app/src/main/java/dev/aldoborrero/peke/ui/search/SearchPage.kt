package dev.aldoborrero.peke.ui.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.isActive
import dev.aldoborrero.peke.kids.kidsPlayKeyDestination
import dev.aldoborrero.peke.kids.ui.KidsEmptyMessage
import dev.aldoborrero.peke.kids.ui.isKidScreen
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.ui.cards.GridCard
import dev.aldoborrero.peke.ui.cards.ItemRow
import dev.aldoborrero.peke.ui.cards.ItemRowTitle
import dev.aldoborrero.peke.ui.components.ExpandableFaButton
import dev.aldoborrero.peke.ui.components.SearchEditTextBox
import dev.aldoborrero.peke.ui.components.VoiceSearchButton
import dev.aldoborrero.peke.ui.components.rememberContextMenu
import dev.aldoborrero.peke.ui.data.RowColumn
import dev.aldoborrero.peke.ui.detail.CardGrid
import dev.aldoborrero.peke.ui.detail.CardGridItem
import dev.aldoborrero.peke.ui.detail.GridItemDetails
import dev.aldoborrero.peke.ui.detail.livetv.ProgramDialog
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.onMain
import dev.aldoborrero.peke.ui.titleStringRes
import dev.aldoborrero.peke.ui.tryRequestFocus
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

internal const val SEARCH_ROW = 0
internal const val TAB_ROW = SEARCH_ROW + 1
internal const val COMBINED_ROW = TAB_ROW + 1
internal const val RESULTS_START = COMBINED_ROW + 1

@Composable
fun SearchPage(
    initialQuery: String,
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val kidsMode = LocalKidsMode.current.isActive
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by viewModel.state.collectAsState()
    val programDialogState by viewModel.programDialogState.collectAsState()

    // Start with current preferences, but collect updates when view options change
    val prefs =
        viewModel.userPreferencesService.flow
            .collectAsState(userPreferences)
            .value.appPreferences.interfacePreferences.searchPreferences
    val combinedMode = prefs.combinedSearchResults
    val voiceSearchButtonVisible = prefs.showVoiceSearchButton

//    val query = rememberTextFieldState()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focusRequesters =
        remember(state.includedSearchableTypes.size) { List(RESULTS_START + state.includedSearchableTypes.size) { FocusRequester() } }

    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var showFilterTypeDialog by rememberSaveable { mutableStateOf(false) }
    var searchClicked by rememberSaveable(query) { mutableStateOf(false) }
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var showProgramDialog by remember { mutableStateOf(false) }

    val position by viewModel.position.collectAsState()

    fun setPosition(pos: RowColumn) {
        Timber.v("pos=%s", pos)
        viewModel.position.value = pos
    }

    val contextMenu = rememberContextMenu(userPreferences, viewModel)

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            viewModel.voiceInputManager.stopListening()
        }
    }

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        searchClicked = true
        viewModel.search(searchQuery, combinedMode)
    }

    LaunchedEffect(query, combinedMode) {
        when {
            immediateSearchQuery == query -> {
                immediateSearchQuery = null
            }

            else -> {
                delay(750.milliseconds)
                viewModel.search(query, combinedMode)
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(position.row)?.tryRequestFocus()
    }
    val onClickItem = { _: Int, item: BaseItem ->
        Timber.v("Clicked %s, type=%s", item.id, item.type)
        if (item.type == BaseItemKind.TV_PROGRAM || item.type == BaseItemKind.PROGRAM || item.type == BaseItemKind.LIVE_TV_PROGRAM) {
            viewModel.fetchProgramForDialog(item.id)
            showProgramDialog = true
        } else {
            viewModel.navigationManager.navigateTo(item.destination())
        }
    }
    val onLongClickItem = { rowIndex: Int, index: Int, item: BaseItem ->
        setPosition(RowColumn(rowIndex, index))
        contextMenu.showContextMenu(index, item)
    }
    val onPlayItem = { _: Int, item: BaseItem ->
        viewModel.navigationManager.navigateTo(if (kidsMode) kidsPlayKeyDestination(item) else Destination.Playback(item))
    }

    val positionCallback = { _: Int, _: Int -> }

    LaunchedEffect(
        searchClicked,
        state,
        combinedMode,
    ) {
        if (!searchClicked || position.row > TAB_ROW) return@LaunchedEffect

        withContext(PekeDispatchers.IO) {
            // Want to focus on the first successful row after all the ones before it are finished searching
            val results =
                if (combinedMode) {
                    listOf(state.combinedResults)
                } else {
                    state.includedSearchableTypes.map { state.results[it] }
                }
            val firstSuccess =
                results.indexOfFirst { it is SearchResult.Success }
            if (firstSuccess >= 0) {
                val anyBeforeSearching =
                    results.subList(0, firstSuccess).any { it is SearchResult.Searching }
                if (!anyBeforeSearching) {
                    val targetRow =
                        if (combinedMode) {
                            COMBINED_ROW
                        } else {
                            firstSuccess
                        }
//                    setPosition(RowColumn(targetRow, 0))
                    onMain { focusRequesters.getOrNull(targetRow)?.tryRequestFocus() }
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            var isSearchActive by remember { mutableStateOf(false) }
            var isTextFieldFocused by remember { mutableStateOf(false) }
            val textFieldFocusRequester = remember { FocusRequester() }

            BackHandler(isTextFieldFocused) {
                when {
                    isSearchActive -> {
                        isSearchActive = false
                        keyboardController?.hide()
                    }

                    else -> {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .focusGroup()
                        .focusRestorer(textFieldFocusRequester)
                        .focusRequester(focusRequesters[SEARCH_ROW]),
            ) {
                AnimatedVisibility(
                    visible = voiceSearchButtonVisible,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                ) {
                    VoiceSearchButton(
                        onSpeechResult = { spokenText ->
                            query = spokenText
                            triggerImmediateSearch(spokenText)
                        },
                        voiceInputManager = viewModel.voiceInputManager,
                        enabled = prefs.kidsVoiceSearch,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }

                SearchEditTextBox(
                    value = query,
                    onValueChange = {
                        isSearchActive = true
                        query = it
                    },
                    onSearchClick = { triggerImmediateSearch(query) },
                    readOnly = !isSearchActive,
                    modifier =
                        Modifier
                            .focusRequester(textFieldFocusRequester)
                            .onFocusChanged { state ->
                                isTextFieldFocused = state.isFocused
                                if (!state.isFocused) isSearchActive = false
                            }.onPreviewKeyEvent { event ->
                                val isActivationKey =
                                    event.key in listOf(Key.DirectionCenter, Key.Enter)
                                if (event.type == KeyEventType.KeyUp && isActivationKey && !isSearchActive) {
                                    isSearchActive = true
                                    keyboardController?.show()
                                    true
                                } else {
                                    false
                                }
                            },
                )

                ExpandableFaButton(
                    title = R.string.view_options,
                    iconStringRes = R.string.fa_sliders,
                    onClick = { showViewOptions = true },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                combinedMode -> {
                    SearchCombinedResults(
                        result = state.combinedResults,
                        focusRequester = focusRequesters[COMBINED_ROW],
                        onClickItem = onClickItem,
                        onLongClickItem = { index, item ->
                            onLongClickItem(COMBINED_ROW, index, item)
                        },
                        onPlayItem = onPlayItem,
                        onClickPosition = { setPosition(it) },
                        positionCallback = positionCallback,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Every kind searched and nothing found: kids get the buddy rather than a blank page
                isKidScreen() &&
                    state.includedSearchableTypes.isNotEmpty() &&
                    state.includedSearchableTypes.all { (state.results[it] as? SearchResult.Success)?.items?.isEmpty() == true } -> {
                    KidsEmptyMessage(R.string.kids_search_empty, Modifier.fillMaxSize())
                }

                else -> {
                    LazyColumn(
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 44.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.focusGroup(),
                    ) {
                        itemsIndexed(state.includedSearchableTypes) { index, type ->
                            val rowIndex = RESULTS_START + index
                            val result = state.results.getOrDefault(type, SearchResult.Searching)
                            val focusRequester =
                                remember(state.includedSearchableTypes.size) {
                                    focusRequesters.getOrNull(rowIndex) ?: FocusRequester()
                                }
                            SearchRowResult(
                                title = type.titleStringRes,
                                result = result,
                                rowIndex = rowIndex,
                                position = position,
                                focusRequester = focusRequester,
                                onClickItem = onClickItem,
                                onLongClickItem = { index, item ->
                                    onLongClickItem(rowIndex, index, item)
                                },
                                onClickPosition = { setPosition(it) },
                                modifier = Modifier.fillMaxWidth(),
                                cardContent = { index, item, mod, onClick, onLongClick ->
                                    SearchPageCard(
                                        item = item,
                                        type = type,
                                        onClick = {
                                            setPosition(RowColumn(rowIndex, index))
                                            onClick.invoke()
                                        },
                                        onLongClick = onLongClick,
                                        modifier = mod,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        contextMenu.Compose()
    }

    if (showViewOptions) {
        SearchViewOptionsDialog(
            combinedResults = combinedMode,
            onCombinedResultsChange = viewModel::setCombinedResults,
            voiceSearchButtonVisible = voiceSearchButtonVisible,
            onVoiceSearchButtonVisibleChange = viewModel::setVoiceSearchButtonVisible,
            onClickFilterTypes = { showFilterTypeDialog = true },
            onDismissRequest = { showViewOptions = false },
        )
    }

    if (showFilterTypeDialog) {
        SearchTypeOptionsDialog(
            onDismissRequest = { showFilterTypeDialog = false },
            searchableTypes = state.possibleSearchableTypes,
            excludedSearchableTypes = state.excludedSearchableTypes,
            onClick = viewModel::onClickExcludeSearchableType,
        )
    }

    if (showProgramDialog) {
        val context = LocalContext.current
        val onDismissRequest = { showProgramDialog = false }
        ProgramDialog(
            state = programDialogState.loading,
            canRecord = true,
            onDismissRequest = onDismissRequest,
            onWatch = {
                onDismissRequest.invoke()
                val channelId = it.data.channelId
                if (channelId != null) {
                    viewModel.navigationManager.navigateTo(
                        Destination.Playback(
                            itemId = channelId,
                            positionMs = 0L,
                        ),
                    )
                } else {
                    Toast.makeText(context, "Program has no channel ID", Toast.LENGTH_LONG).show()
                }
            },
            onRecord = { program, series ->
                viewModel.record(
                    programId = program.id,
                    series = series,
                )
                onDismissRequest.invoke()
            },
            onCancelRecord = { program, series ->
                viewModel.cancelRecording(
                    series = series,
                    timerId = if (series) program.data.seriesTimerId else program.data.timerId,
                )
                onDismissRequest.invoke()
            },
        )
    }
}

@Composable
fun SearchCombinedResults(
    result: SearchResult,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onPlayItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (result) {
        SearchResult.NoQuery -> {}

        SearchResult.Searching -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = stringResource(R.string.searching),
                modifier = modifier.padding(16.dp),
            )
        }

        is SearchResult.Error -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = result.ex.localizedMessage ?: "Error occurred during search",
                messageColor = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(16.dp),
            )
        }

        is SearchResult.Success -> {
            if (result.items.isEmpty() && isKidScreen()) {
                KidsEmptyMessage(R.string.kids_search_empty, modifier.padding(16.dp))
            } else if (result.items.isEmpty()) {
                SearchResultPlaceholder(
                    title = stringResource(R.string.results),
                    message = stringResource(R.string.no_results),
                    modifier = modifier.padding(16.dp),
                )
            } else {
                SearchGrid(
                    items = result.items,
                    focusRequester = focusRequester,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onPlayItem = onPlayItem,
                    onClickPosition = onClickPosition,
                    positionCallback = positionCallback,
                    cardContent = { details ->
                        GridCard(
                            item = details.item,
                            onClick = details.onClick,
                            onLongClick = details.onLongClick,
                            modifier = details.mod,
                            fillWidth = details.widthPx,
                        )
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun <T : CardGridItem> SearchGrid(
    items: List<T?>,
    focusRequester: FocusRequester,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    onPlayItem: (Int, T) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    cardContent: @Composable (GridItemDetails<T>) -> Unit,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier,
    ) {
        ItemRowTitle(stringResource(R.string.results))

        CardGrid(
            pager = items,
            onClickItem = { index, item ->
                onClickPosition.invoke(RowColumn(COMBINED_ROW, index))
                onClickItem.invoke(index, item)
            },
            onLongClickItem = onLongClickItem,
            onClickPlay = { index, item ->
                onClickPosition.invoke(RowColumn(COMBINED_ROW, index))
                onPlayItem.invoke(index, item)
            },
            letterPosition = { -1 },
            gridFocusRequester = focusRequester,
            showJumpButtons = false,
            showLetterButtons = false,
            positionCallback = positionCallback,
            modifier = Modifier.fillMaxSize(),
            cardContent = cardContent,
        )
    }
}

@Composable
fun SearchRowResult(
    @StringRes title: Int,
    result: SearchResult,
    rowIndex: Int,
    position: RowColumn,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    cardContent: @Composable (
        index: Int,
        item: BaseItem?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
) {
    when (val r = result) {
        is SearchResult.Error -> {
            SearchResultPlaceholder(
                title = stringResource(title),
                // Kids never see the technical error (design/DESIGN.md, Words)
                message =
                    if (isKidScreen()) {
                        stringResource(R.string.kids_oops_title)
                    } else {
                        r.ex.localizedMessage
                            ?: "Error occurred during search"
                    },
                messageColor = MaterialTheme.colorScheme.error,
                modifier = Modifier,
            )
        }

        SearchResult.NoQuery -> {
            // no-op
        }

        SearchResult.Searching -> {
            SearchResultPlaceholder(
                title = stringResource(title),
                message = stringResource(R.string.searching),
                modifier = modifier,
            )
        }

        is SearchResult.Success -> {
            if (r.items.isNotEmpty()) {
                ItemRow(
                    title = stringResource(title),
                    items = r.items,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    modifier = modifier.focusRequester(focusRequester),
                    cardContent = cardContent,
                )
            }
        }
    }
}

@Composable
fun SearchResultPlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    messageColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = messageColor,
        )
    }
}
