package dev.aldoborrero.peke.kids.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.ui.FontAwesome
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.components.DialogParams
import dev.aldoborrero.peke.ui.components.DialogPopup
import dev.aldoborrero.peke.ui.components.chooseVersionParams
import dev.aldoborrero.peke.ui.data.ChooseVersionParams
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekePalette
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import kotlin.math.roundToLong
import kotlin.time.Duration

/**
 * The colours of a season's number circle in the Seasons list (KSeasonsPekeTV): the theme's colours in turn with "on
 * primary" for the number, and on the focused row the page colour with the number in "time"
 *
 * @return the circle colour and the number colour
 */
fun seasonCircle(
    index: Int,
    focused: Boolean,
    palette: PekePalette,
): Pair<Color, Color> =
    if (focused) {
        palette.background to palette.time
    } else {
        val circles = listOf(palette.secondary, palette.tertiary, palette.time, palette.primary)
        circles[Math.floorMod(index, circles.size)] to palette.onPrimary
    }

/**
 * An episode under its card: "4. Lost Robot", or its name alone without a number
 */
fun episodeLabel(
    number: Int?,
    name: String?,
): String =
    when {
        number != null && name != null -> "$number. $name"
        number != null -> "$number"
        else -> name.orEmpty()
    }

/**
 * A film's length in whole minutes, rounded and at least 1; null when unknown
 */
fun runtimeMinutes(runtime: Duration?): Int? =
    runtime
        ?.takeIf { it.isPositive() }
        ?.let { (it.inWholeSeconds / 60.0).roundToLong().toInt().coerceAtLeast(1) }

/**
 * The top of a kids details page (DetailsPekeTV): the poster on the left (260×380), then the title in Display, the
 * chips, the overview and the buttons
 */
@Composable
fun KidsDetailsHeader(
    poster: BaseItem?,
    title: String?,
    overview: String?,
    modifier: Modifier = Modifier,
    chips: @Composable RowScope.() -> Unit = {},
    buttons: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(40.cpx),
        verticalAlignment = Alignment.Top,
        modifier = modifier,
    ) {
        KidsPoster(poster, title)
        Column(
            verticalArrangement = Arrangement.spacedBy(20.cpx),
            modifier = Modifier.padding(top = 12.cpx),
        ) {
            Text(
                text = title.orEmpty(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.cpx), content = chips)
            if (!overview.isNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 640.cpx),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.cpx),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.cpx),
                content = buttons,
            )
        }
    }
}

/**
 * A details page's poster: the item's picture, 260×380 with 30 px corners, not focusable
 */
@Composable
fun KidsPoster(
    item: BaseItem?,
    title: String?,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val height = 380.cpx
    val fillHeight = with(LocalDensity.current) { height.roundToPx() }
    val imageUrl =
        remember(item, fillHeight) {
            item?.let { it.imageUrlOverride ?: imageUrlService.getItemImageUrl(it, ImageType.PRIMARY, fillHeight = fillHeight) }
        }
    Box(modifier = modifier.size(260.cpx, height).clip(RoundedCornerShape(30.cpx))) {
        KidsCardPicture(imageUrl = imageUrl, title = title, id = item?.id)
    }
}

/**
 * A chip under the title: 18 px on the surface colour; with [onClick] it can take focus, like the season chip that
 * opens the Seasons list
 */
@Composable
fun KidsChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.cpx)
    val label: @Composable (Color) -> Unit = { color ->
        Text(
            text = text,
            fontSize = 18.csp,
            fontWeight = FontWeight.W800,
            color = color,
            modifier = Modifier.padding(horizontal = 16.cpx, vertical = 6.cpx),
        )
    }
    if (onClick == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surface, shape)) { label(MaterialTheme.colorScheme.onSurface) }
    } else {
        val colors = LocalPekeColors.current
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = PekeFocus.surfaceScale,
            border = PekeFocus.surfaceBorder(),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = colors.surface,
                    contentColor = colors.text,
                    focusedContainerColor = colors.primary,
                    focusedContentColor = colors.onPrimary,
                ),
            modifier = modifier.pekeFocus(shape),
        ) {
            label(LocalContentColor.current)
        }
    }
}

/**
 * The main action of a details page: a 72 px primary pill with Peke's play icon and 28 px text in "on primary"
 */
@Composable
fun KidsPlayPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    val shape = CircleShape
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                focusedContainerColor = colors.primary,
                focusedContentColor = colors.onPrimary,
            ),
        modifier = modifier.height(72.cpx).pekeFocus(shape),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.cpx),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 36.cpx).height(72.cpx),
        ) {
            Icon(painter = painterResource(PekeIcon.PLAY.outline), contentDescription = null, modifier = Modifier.size(28.cpx))
            Text(text = text, fontSize = 28.csp, fontWeight = FontWeight.W900, maxLines = 1)
        }
    }
}

/**
 * A 72 px round button on the surface colour, with a Peke icon or a FontAwesome glyph
 */
@Composable
fun KidsRoundButton(
    @StringRes description: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    @StringRes glyph: Int? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Int = 72,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = LocalPekeColors.current
    val focused by interactionSource.collectIsFocusedAsState()
    val contentDescription = stringResource(description)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        interactionSource = interactionSource,
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surface,
                focusedContainerColor = colors.primary,
            ),
        modifier = modifier.size(size.cpx).pekeFocus(CircleShape),
    ) {
        Box(Modifier.size(size.cpx), contentAlignment = Alignment.Center) {
            val color = if (focused) colors.onPrimary else tint
            if (icon != null) {
                Icon(painter = icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size((size * 30 / 72).cpx))
            } else if (glyph != null) {
                Text(text = stringResource(glyph), fontFamily = FontAwesome, fontSize = (size * 26 / 72).csp, color = color)
            }
        }
    }
}

/**
 * The buttons of an episode or a film for a kid: the Play pill ([playText]; it resumes where the kid stopped), then round
 * buttons for what the page already offered: restart when started, the version when there are several, watched and
 * favourite. Nothing is added; delete and the item menu are never there for kids (004).
 */
@Composable
fun RowScope.KidsPlayButtons(
    playText: String,
    resumePosition: Duration,
    watched: Boolean,
    favorite: Boolean,
    playOnClick: (Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    chooseVersionParams: ChooseVersionParams?,
    playFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val resources = LocalResources.current
    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }
    val colors = LocalPekeColors.current
    KidsPlayPill(
        text = playText,
        onClick = { playOnClick(resumePosition) },
        modifier = Modifier.focusRequester(playFocusRequester),
    )
    if (resumePosition > Duration.ZERO) {
        KidsRoundButton(description = R.string.restart, glyph = R.string.fa_arrows_rotate, onClick = { playOnClick(Duration.ZERO) })
    }
    if (chooseVersionParams != null && chooseVersionParams.mediaSources.size > 1) {
        KidsRoundButton(
            description = R.string.version,
            glyph = R.string.fa_file_video,
            onClick = {
                chooseVersion =
                    chooseVersionParams(
                        resources,
                        chooseVersionParams.mediaSources,
                        chooseVersionParams.chosenStreams
                            ?.source
                            ?.id
                            ?.toUUIDOrNull(),
                    ) { idx -> chooseVersionParams.onChooseVersion(chooseVersionParams.mediaSources[idx]) }
            },
        )
    }
    KidsRoundButton(
        description = if (watched) R.string.mark_unwatched else R.string.mark_watched,
        icon = painterResource(PekeIcon.WATCHED.outline),
        tint = if (watched) colors.secondary else colors.textSecondary,
        onClick = watchOnClick,
    )
    KidsFavouriteButton(favorite = favorite, onClick = favoriteOnClick)
    chooseVersion?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { chooseVersion = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
        )
    }
}

/**
 * The round favourite button: Peke's heart, in primary when it is a favourite
 */
@Composable
fun KidsFavouriteButton(
    favorite: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPekeColors.current
    KidsRoundButton(
        description = if (favorite) R.string.remove_favorite else R.string.add_favorite,
        icon = painterResource(PekeIcon.FAVOURITES.outline),
        tint = if (favorite) colors.primary else colors.textSecondary,
        onClick = onClick,
    )
}

/**
 * The Seasons list (KSeasonsPekeTV): a 640 px panel with a row per season, its number in a coloured circle, its name and
 * how many episodes it has. It opens on [selected]; picking a season calls [onPick] and closes.
 */
@Composable
fun KidsSeasonsDialog(
    seasons: List<BaseItem?>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = LocalPekeColors.current
    val focusRequesters = remember(seasons) { List(seasons.size) { FocusRequester() } }
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            // Room for a focused season's border and ring: the rows are as wide as the dialog, so focus doesn't grow them
            verticalArrangement = Arrangement.spacedBy(maxOf(18.cpx, PekeFocus.clearance(0.dp, grow = false))),
            modifier =
                Modifier
                    .widthIn(max = 640.cpx)
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(36.cpx))
                    .padding(36.cpx),
        ) {
            Text(
                text = stringResource(R.string.tv_seasons),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text,
            )
            seasons.forEachIndexed { index, season ->
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                val (circle, number) = seasonCircle(index, focused, colors)
                val shape = RoundedCornerShape(24.cpx)
                Surface(
                    onClick = {
                        onPick(index)
                        onDismissRequest()
                    },
                    interactionSource = interactionSource,
                    shape = ClickableSurfaceDefaults.shape(shape),
                    scale = PekeFocus.surfaceScale,
                    border = PekeFocus.surfaceBorder(),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = colors.background,
                            contentColor = colors.text,
                            focusedContainerColor = colors.primary,
                            focusedContentColor = colors.onPrimary,
                        ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequesters[index]).pekeFocus(shape, grow = false),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.cpx),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(PaddingValues(horizontal = 22.cpx, vertical = 16.cpx)),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(52.cpx).background(circle, CircleShape),
                        ) {
                            Text(
                                text = (season?.data?.indexNumber ?: (index + 1)).toString(),
                                fontSize = 26.csp,
                                fontWeight = FontWeight.W900,
                                color = number,
                            )
                        }
                        Text(
                            text = season?.name.orEmpty(),
                            fontSize = 26.csp,
                            fontWeight = FontWeight.W900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        season?.data?.childCount?.let { count ->
                            Text(
                                text = pluralStringResource(R.plurals.kids_episode_count, count, count),
                                fontSize = 18.csp,
                                fontWeight = FontWeight.W800,
                                color = if (focused) colors.onPrimary else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequesters.getOrNull(selected)?.tryRequestFocus() }
}
