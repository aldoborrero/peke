package dev.aldoborrero.peke.ui.detail.series

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ChosenStreams
import dev.aldoborrero.peke.data.ExtrasItem
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.kids.ui.KidsCardRow
import dev.aldoborrero.peke.kids.ui.KidsChip
import dev.aldoborrero.peke.kids.ui.KidsDetailsHeader
import dev.aldoborrero.peke.kids.ui.KidsEpisodeCardSize
import dev.aldoborrero.peke.kids.ui.KidsMinCardGap
import dev.aldoborrero.peke.kids.ui.KidsPictureCard
import dev.aldoborrero.peke.kids.ui.KidsPlayButtons
import dev.aldoborrero.peke.kids.ui.KidsSeasonsDialog
import dev.aldoborrero.peke.kids.ui.episodeLabel
import dev.aldoborrero.peke.ui.cards.ExtrasRow
import dev.aldoborrero.peke.ui.cards.ItemRowTitle
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.data.ChooseVersionParams
import dev.aldoborrero.peke.ui.formatSeasonNumber
import dev.aldoborrero.peke.ui.playback.isPlayKeyUp
import dev.aldoborrero.peke.ui.rememberInt
import dev.aldoborrero.peke.ui.theme.FocusRoom
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

/**
 * A season's page for a kid (DetailsPekeTV): the series' poster, title, the season and episode chips, the overview,
 * "Play episode N" and the favourite, then the episodes as wide cards. The season chip opens the Seasons list
 * (KSeasonsPekeTV) instead of Wholphin's tab row. What each button and card does is the same as on
 * [SeriesOverviewContent]; the people rows, never shown to kids (004), aren't here.
 */
@Composable
fun KidsSeriesOverviewContent(
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: EpisodeList,
    seasonExtras: List<ExtrasItem>,
    chosenStreams: ChosenStreams?,
    position: SeriesOverviewPosition,
    firstItemFocusRequester: FocusRequester,
    episodeRowFocusRequester: FocusRequester,
    extrasRowFocusRequester: FocusRequester,
    onChangeSeason: (Int) -> Unit,
    onFocusEpisode: (Int) -> Unit,
    onClick: (BaseItem) -> Unit,
    playOnClick: (Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    onChooseVersion: (BaseItem, MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSeason by rememberSaveable(position.seasonTabIndex) { mutableIntStateOf(position.seasonTabIndex) }
    var showSeasons by remember { mutableStateOf(false) }
    var requestFocusAfterSeason by remember { mutableStateOf(false) }
    val season = seasons.getOrNull(selectedSeason)
    val episodeList = (episodes as? EpisodeList.Success)?.episodes
    val focusedEpisode = episodeList?.getOrNull(position.episodeRowIndex)

    Column(
        verticalArrangement = Arrangement.spacedBy(32.cpx),
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 48.cpx, start = 48.cpx, end = 48.cpx, bottom = 48.cpx),
    ) {
        KidsDetailsHeader(
            poster = series,
            title = series.name,
            overview = series.data.overview,
            chips = {
                val seasonName = season?.name ?: season?.data?.indexNumber?.let { formatSeasonNumber(it) }
                if (seasonName != null) {
                    KidsChip(text = seasonName, onClick = if (seasons.size > 1) ({ showSeasons = true }) else null)
                }
                episodeList?.let {
                    KidsChip(text = pluralStringResource(R.plurals.kids_episode_count, it.size, it.size))
                }
            },
            buttons = {
                focusedEpisode?.let { ep ->
                    val number = ep.data.indexNumber
                    KidsPlayButtons(
                        playText = number?.let { stringResource(R.string.kids_play_episode, it) } ?: stringResource(R.string.play),
                        resumePosition =
                            ep.data.userData
                                ?.playbackPositionTicks
                                ?.ticks ?: Duration.ZERO,
                        watched = ep.data.userData?.played ?: false,
                        favorite = ep.data.userData?.isFavorite ?: false,
                        playOnClick = playOnClick,
                        watchOnClick = {
                            watchOnClick()
                            episodeRowFocusRequester.tryRequestFocus()
                        },
                        favoriteOnClick = favoriteOnClick,
                        chooseVersionParams =
                            remember(chosenStreams, ep) {
                                ChooseVersionParams(
                                    chosenStreams = chosenStreams,
                                    mediaSources = ep.data.mediaSources.orEmpty(),
                                    onChooseVersion = { onChooseVersion(ep, it) },
                                )
                            },
                    )
                }
            },
        )

        when (val eps = episodes) {
            EpisodeList.Loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(14.cpx)) {
                    ItemRowTitle(stringResource(R.string.episodes), startPadding = 0.dp)
                    LoadingPage()
                }
            }

            is EpisodeList.Error -> {
                Column(verticalArrangement = Arrangement.spacedBy(14.cpx)) {
                    ItemRowTitle(stringResource(R.string.episodes), startPadding = 0.dp)
                    ErrorMessage(eps.message, eps.exception)
                }
            }

            is EpisodeList.Success -> {
                if (requestFocusAfterSeason) {
                    // Changing seasons, so move focus once the new episodes are loaded
                    LaunchedEffect(Unit) {
                        firstItemFocusRequester.tryRequestFocus()
                        requestFocusAfterSeason = false
                    }
                }
                val state = rememberLazyListState(position.episodeRowIndex)
                var epPosition by rememberInt(position.episodeRowIndex)
                KidsCardRow(
                    title = stringResource(R.string.episodes),
                    room = FocusRoom.of(KidsEpisodeCardSize, KidsMinCardGap),
                    state = state,
                    rowModifier = Modifier.focusRestorer(episodeRowFocusRequester),
                ) {
                    itemsIndexed(eps.episodes) { episodeIndex, episode ->
                        val interactionSource = remember { MutableInteractionSource() }
                        if (interactionSource.collectIsFocusedAsState().value) {
                            onFocusEpisode.invoke(episodeIndex)
                        }
                        val cardModifier =
                            Modifier
                                .then(
                                    if (episodeIndex == position.episodeRowIndex) {
                                        Modifier.focusRequester(firstItemFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                ).then(
                                    if (episodeIndex == epPosition) Modifier.focusRequester(episodeRowFocusRequester) else Modifier,
                                ).onKeyEvent {
                                    if (episode != null && isPlayKeyUp(it)) {
                                        onClick.invoke(episode)
                                        true
                                    } else {
                                        false
                                    }
                                }
                        KidsPictureCard(
                            item = episode,
                            title = episodeLabel(episode?.data?.indexNumber, episode?.name),
                            imageType = ImageType.PRIMARY,
                            width = KidsEpisodeCardSize.width,
                            height = KidsEpisodeCardSize.height,
                            corner = 22.cpx,
                            titleSize = 18,
                            played = episode?.data?.userData?.played ?: false,
                            playPercent = episode?.data?.userData?.playedPercentage ?: 0.0,
                            useSeriesForPrimary = false,
                            interactionSource = interactionSource,
                            onClick = {
                                epPosition = episodeIndex
                                if (episode != null) onClick.invoke(episode)
                            },
                            onLongClick = { epPosition = episodeIndex },
                            modifier = cardModifier,
                        )
                    }
                }
            }
        }

        if (seasonExtras.isNotEmpty()) {
            ExtrasRow(
                extras = seasonExtras,
                onClickItem = onClickExtra,
                onLongClickItem = { _, _ -> },
                modifier = Modifier.fillMaxWidth().focusRequester(extrasRowFocusRequester),
            )
        }
    }

    if (showSeasons) {
        KidsSeasonsDialog(
            seasons = seasons,
            selected = selectedSeason,
            onPick = {
                if (it != selectedSeason) {
                    selectedSeason = it
                    onChangeSeason(it)
                    requestFocusAfterSeason = true
                }
            },
            onDismissRequest = { showSeasons = false },
        )
    }
}
