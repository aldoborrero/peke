package dev.aldoborrero.peke.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.filter.CommunityRatingFilter
import dev.aldoborrero.peke.data.filter.DecadeFilter
import dev.aldoborrero.peke.data.filter.FavoriteFilter
import dev.aldoborrero.peke.data.filter.FilterValueOption
import dev.aldoborrero.peke.data.filter.FilterVideoType
import dev.aldoborrero.peke.data.filter.GenreFilter
import dev.aldoborrero.peke.data.filter.ItemFilterBy
import dev.aldoborrero.peke.data.filter.OfficialRatingFilter
import dev.aldoborrero.peke.data.filter.PlayedFilter
import dev.aldoborrero.peke.data.filter.StudioFilter
import dev.aldoborrero.peke.data.filter.VideoTypeFilter
import dev.aldoborrero.peke.data.filter.YearFilter
import dev.aldoborrero.peke.data.model.GetItemsFilter
import dev.aldoborrero.peke.ui.FontAwesome
import dev.aldoborrero.peke.ui.PreviewTvSpec
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.tryRequestFocus
import java.util.UUID

/**
 * Button for filtering data.
 *
 * Clicking on it will show a drop-down menu of filterable options. Many of these show a second drop-down menu when clicked.
 *
 * @see GetItemsFilter
 * @see ItemFilterBy
 */
@Composable
fun FilterByButton(
    filterOptions: List<ItemFilterBy<*>>,
    current: GetItemsFilter,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    modifier: Modifier = Modifier,
    onShow: ((Boolean) -> Unit)? = null,
) {
    var dropDown by remember { mutableStateOf(false) }
    var nestedDropDown by remember { mutableStateOf<ItemFilterBy<*>?>(null) }
    val filterCount = remember(current, filterOptions) { current.countFilters(filterOptions) }

    Box(modifier = modifier) {
        ExpandableFilterButton(
            filterCount = filterCount,
            onClick = {
                onShow?.invoke(true)
                dropDown = true
            },
            modifier = Modifier,
        )

        DropdownMenu(
            expanded = dropDown,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            onDismissRequest = {
                onShow?.invoke(false)
                dropDown = false
                nestedDropDown = null
            },
        ) {
            if (filterCount > 0) {
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                TvDropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = .8f),
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.remove),
                            color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        onFilterChange.invoke(current.delete(filterOptions))
                        dropDown = false
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier,
                )
                HorizontalDivider()
            }
            filterOptions
                .forEachIndexed { index, filterOption ->
                    val focusRequester = remember { FocusRequester() }
                    if (index == 0) {
                        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                    }
                    val currentValue = remember(current) { filterOption.get(current) }
                    TvDropdownMenuItem(
                        leadingIcon = {
                            if (currentValue != null) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Filter active",
                                )
                            }
                        },
                        text = {
                            Text(
                                text = stringResource(filterOption.stringRes),
                            )
                        },
                        onClick = {
                            nestedDropDown = filterOption
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
        }

        DropdownMenu(
            expanded = nestedDropDown != null,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
            offset = DpOffset(80.dp, 16.dp),
            onDismissRequest = {
                nestedDropDown = null
            },
        ) {
            nestedDropDown?.let { filterOption ->
                filterOption as ItemFilterBy<Any>
                val currentValue = remember(current) { filterOption.get(current) }

                var possibleValues by remember { mutableStateOf<List<FilterValueOption>?>(null) }
                LaunchedEffect(Unit) {
                    possibleValues = getPossibleValues.invoke(filterOption)
                }

                if (currentValue != null) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()
                    TvDropdownMenuItem(
                        elevation = 5.dp,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.Red.copy(alpha = .8f),
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.remove),
                                color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onFilterChange.invoke(filterOption.set(null, current))
                            nestedDropDown = null
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier,
                    )
                    HorizontalDivider()
                }

                if (possibleValues == null) {
                    CircularProgress(Modifier.size(48.dp))
                } else if (possibleValues?.isEmpty() == true) {
                    Text(
                        text = stringResource(R.string.no_results),
                    )
                } else {
                    possibleValues.orEmpty().forEachIndexed { index, value ->
                        val focusRequester = remember { FocusRequester() }
                        if (index == 0) {
                            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                        }

                        val isSelected =
                            remember(currentValue) {
                                when (filterOption) {
                                    GenreFilter,
                                    StudioFilter,
                                    -> {
                                        (currentValue as? List<UUID>)
                                            .orEmpty()
                                            .contains(value.value)
                                    }

                                    FavoriteFilter,
                                    PlayedFilter,
                                    -> {
                                        (currentValue as? Boolean) == value.name.toBoolean()
                                    }

                                    OfficialRatingFilter -> {
                                        (currentValue as? List<String>)
                                            .orEmpty()
                                            .contains(value.name)
                                    }

                                    VideoTypeFilter -> {
                                        (currentValue as? List<FilterVideoType>)
                                            .orEmpty()
                                            .contains(value.value)
                                    }

                                    YearFilter,
                                    DecadeFilter,
                                    -> {
                                        (currentValue as? List<Int>)
                                            .orEmpty()
                                            .contains(value.value)
                                    }

                                    CommunityRatingFilter -> {
                                        (currentValue as? Int) == value.value
                                    }
                                }
                            }
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        TvDropdownMenuItem(
                            elevation = 8.dp,
                            interactionSource = interactionSource,
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Filter active",
                                    )
                                }
                            },
                            text = {
                                if (filterOption == CommunityRatingFilter) {
                                    SimpleStarRating(
                                        "${value.name}+",
                                        starColor = if (focused) EmptyStarColor else FilledStarColor,
                                    )
                                } else {
                                    Text(
                                        text = value.name,
                                    )
                                }
                            },
                            onClick = {
                                val newFilter =
                                    when (filterOption) {
                                        GenreFilter,
                                        StudioFilter,
                                        -> {
                                            val list = (currentValue as? List<UUID>).orEmpty()
                                            val newValue =
                                                list
                                                    .toMutableList()
                                                    .apply {
                                                        if (isSelected) {
                                                            remove(value.value!!)
                                                        } else {
                                                            add(value.value!! as UUID)
                                                        }
                                                    }.takeIf { it.isNotEmpty() }
                                            filterOption.set(newValue, current)
                                        }

                                        FavoriteFilter,
                                        PlayedFilter,
                                        -> {
                                            val played = value.name.toBoolean()
                                            filterOption.set(played, current)
                                        }

                                        OfficialRatingFilter -> {
                                            val list = (currentValue as? List<String>).orEmpty()
                                            val newValue =
                                                list
                                                    .toMutableList()
                                                    .apply {
                                                        if (isSelected) {
                                                            remove(value.name)
                                                        } else {
                                                            add(value.name)
                                                        }
                                                    }.takeIf { it.isNotEmpty() }
                                            filterOption.set(newValue, current)
                                        }

                                        VideoTypeFilter -> {
                                            val list =
                                                (currentValue as? List<FilterVideoType>).orEmpty()
                                            val newValue =
                                                list
                                                    .toMutableList()
                                                    .apply {
                                                        if (isSelected) {
                                                            remove(value.value)
                                                        } else {
                                                            add(value.value as FilterVideoType)
                                                        }
                                                    }.takeIf { it.isNotEmpty() }
                                            filterOption.set(newValue, current)
                                        }

                                        YearFilter,
                                        DecadeFilter,
                                        -> {
                                            val list =
                                                (currentValue as? List<Int>).orEmpty()
                                            val newValue =
                                                list
                                                    .toMutableList()
                                                    .apply {
                                                        if (isSelected) {
                                                            remove(value.value)
                                                        } else {
                                                            add(value.value as Int)
                                                        }
                                                    }.takeIf { it.isNotEmpty() }
                                            filterOption.set(newValue, current)
                                        }

                                        CommunityRatingFilter -> {
                                            filterOption.set(
                                                value.value as? Int,
                                                current,
                                            )
                                        }
                                    }

                                onFilterChange.invoke(newFilter)
                                if (!filterOption.supportMultiple) {
                                    nestedDropDown = null
                                }
                            },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableFilterButton(
    filterCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconColor: Color = Color.Unspecified,
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = onClick,
        modifier =
            modifier.requiredSizeIn(
                minWidth = MinButtonSize,
                minHeight = MinButtonSize,
                maxHeight = MinButtonSize,
            ),
        contentPadding = PaddingValues(0.dp), // DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(min = if (filterCount > 0 || isFocused) (MinButtonSize - 12.dp) else MinButtonSize),
        ) {
            Text(
                text = stringResource(R.string.fa_filter),
                style = MaterialTheme.typography.titleSmall,
                color = iconColor,
                fontSize = 16.sp,
                fontFamily = FontAwesome,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        AnimatedVisibility(filterCount > 0) {
            Text(
                text = filterCount.toString(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        AnimatedVisibility(isFocused) {
            Text(
                text = stringResource(R.string.filter),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun ExpandableFilterButtonPreview() {
    PekeTheme {
        Column {
            ExpandableFilterButton(
                filterCount = 2,
                onClick = {},
            )

            ExpandableFilterButton(
                filterCount = 0,
                onClick = {},
            )
        }
    }
}
