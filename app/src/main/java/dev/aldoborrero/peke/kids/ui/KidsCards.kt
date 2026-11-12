package dev.aldoborrero.peke.kids.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.LocalPekeFocusScale
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekePalette
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.theme.pekeFocus
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/**
 * The kids' cards from the canvas boards: a rounded picture with its title under it, the focused one's title in the
 * text colour and the others' in "text, secondary". A picture that is missing shows the title on a tile in one of the
 * theme's colours, as the boards draw them.
 *
 * [modifier] goes on the card's column; the caller's focus requester and key handling reach the card through it, as
 * with Wholphin's cards.
 *
 * @param played draws the watched badge in the top right corner
 * @param playPercent draws the progress inside the card's bottom edge when between 0 and 100
 * @param subtitle a smaller second line under the title, in "text, secondary"; an empty one keeps its room
 * @param description what TalkBack reads for the picture, the title unless that says less than the card
 */
@Composable
fun KidsPictureCard(
    item: BaseItem?,
    title: String?,
    imageType: ImageType,
    width: Dp,
    height: Dp,
    corner: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleSize: Int = 20,
    played: Boolean = false,
    favorite: Boolean = false,
    playPercent: Double = 0.0,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    useSeriesForPrimary: Boolean? = null,
    subtitle: String? = null,
    description: String? = title,
) {
    val imageUrlService = LocalImageUrlService.current
    val fillHeight = with(LocalDensity.current) { height.roundToPx() }
    val imageUrl =
        remember(item, imageType, fillHeight, useSeriesForPrimary) {
            item?.let {
                it.imageUrlOverride
                    ?: imageUrlService.getItemImageUrl(it, imageType, fillHeight = fillHeight, useSeriesForPrimary = useSeriesForPrimary)
            }
        }
    // The focus itself, which Peke's focus treatment follows too. Only the title reads it, so a focus change doesn't
    // recompose the card
    var hasFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)
    Column(
        // The board's 10 px, widened so the focused card's scale-up, border and ring clear the title
        verticalArrangement = Arrangement.spacedBy(maxOf(TitleGap, PekeFocus.clearance(height))),
        modifier = modifier.width(width),
    ) {
        // The picture or its tile covers the whole card: the surface colour only shows while the picture loads, so it
        // is drawn then and not under every picture on every frame
        val loadingColor = MaterialTheme.colorScheme.surface
        KidsCardSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = shape,
            interactionSource = interactionSource,
            containerColor = Color.Transparent,
            modifier = Modifier.size(width, height).onFocusChanged { hasFocus = it.isFocused },
        ) {
            KidsCardPicture(imageUrl = imageUrl, title = title, id = item?.id, description = description, loadingColor = loadingColor)
            if (played && (playPercent <= 0 || playPercent >= 100)) {
                KidsWatchedBadge(Modifier.align(Alignment.TopEnd).padding(10.cpx))
            }
            if (favorite) {
                KidsFavouriteBadge(Modifier.align(Alignment.TopEnd).padding(10.cpx))
            }
            if (playPercent > 0 && playPercent < 100) {
                KidsProgress(
                    fraction = (playPercent / 100).toFloat(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(14.cpx),
                )
            }
        }
        KidsPictureCardTitle(title = title, subtitle = subtitle, size = titleSize, width = width, focused = { hasFocus })
    }
}

/**
 * [KidsCardTitle] under a [KidsPictureCard]: the focused card grows from its centre, and the title moves with its left edge
 */
@Composable
private fun KidsPictureCardTitle(
    title: String?,
    subtitle: String?,
    size: Int,
    width: Dp,
    focused: () -> Boolean,
) {
    val hasFocus = focused()
    val growth by animateFloatAsState(
        targetValue = if (hasFocus) PekeFocus.scale(LocalPekeFocusScale.current) - 1 else 0f,
        animationSpec = tween(PekeFocus.ANIMATION_MILLIS),
        label = "card growth",
    )
    Column(Modifier.graphicsLayer { translationX = -width.toPx() * growth / 2 }) {
        KidsCardTitle(title = title, focused = hasFocus, size = size)
        if (subtitle != null) {
            // DESIGN's supporting line
            Text(
                text = subtitle,
                fontSize = 18.csp,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A card's clickable surface with Peke's focus and no tv-material scale or border
 *
 * @param grow false for cards as wide as the page, which keep their size and only gain the border and ring
 */
@Composable
fun KidsCardSurface(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    grow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        shape = CardDefaults.shape(shape),
        scale = PekeFocus.cardScale,
        border = PekeFocus.cardBorder(),
        colors = CardDefaults.colors(containerColor = containerColor, focusedContainerColor = containerColor),
        modifier = modifier.pekeFocus(shape, grow),
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

/**
 * The picture of a card, or its title on a theme-coloured tile when there is none or it can't load
 *
 * @param loadingColor fills the card while the picture loads, for a card that draws no colour under it
 */
@Composable
fun BoxScope.KidsCardPicture(
    imageUrl: String?,
    title: String?,
    id: UUID?,
    description: String? = title,
    loadingColor: Color? = null,
) {
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl != null && !imageError) {
        val placeholder = remember(loadingColor) { loadingColor?.let { ColorPainter(it) } }
        AsyncImage(
            model = imageUrl,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            onError = { imageError = true },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val colors = LocalPekeColors.current
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier.fillMaxSize().background(tileColor(colors, id)),
        ) {
            Text(
                text = title.orEmpty(),
                fontSize = 17.csp,
                fontWeight = FontWeight.W900,
                color = colors.onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.cpx),
            )
        }
    }
}

/**
 * The boards' tile colours, in turn: primary, secondary, tertiary, time
 */
fun tileColor(
    colors: PekePalette,
    id: UUID?,
): Color {
    val tiles = listOf(colors.primary, colors.secondary, colors.tertiary, colors.time)
    return tiles[Math.floorMod(id?.hashCode() ?: 0, tiles.size)]
}

/**
 * A channel's picture on a tile in one of the theme's colours, or Peke's TV in "on primary" when it has none or it can't
 * load (LiveGuidePekeTV)
 */
@Composable
fun KidsChannelPicture(
    id: UUID?,
    imageUrl: String?,
    name: String?,
    size: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(RoundedCornerShape(corner)).background(tileColor(colors, id)),
    ) {
        if (imageUrl != null && !imageError) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onError = { imageError = true },
                modifier = Modifier.size(size * .75f),
            )
        } else {
            Icon(
                painter = painterResource(PekeIcon.LIVE.outline),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(size * .55f),
            )
        }
    }
}

/**
 * The title under a card: 20 px, bolder and in the text colour while the card has focus
 */
@Composable
fun KidsCardTitle(
    title: String?,
    focused: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 20,
) {
    Text(
        text = title.orEmpty(),
        fontSize = size.csp,
        fontWeight = if (focused) FontWeight.W900 else FontWeight.W800,
        color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The progress inside a card's bottom edge: a 10 px pill, the played part in the surface colour on a see-through track
 */
@Composable
fun KidsProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(10.cpx)
                .background(colors.outline.copy(alpha = .3f), CircleShape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(10.cpx)
                    .background(colors.surface, CircleShape),
        )
    }
}

/**
 * Watched: Peke's watched icon in the secondary colour on a round badge of the page colour
 */
@Composable
fun KidsWatchedBadge(modifier: Modifier = Modifier) {
    val colors = LocalPekeColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(30.cpx).background(colors.background, CircleShape),
    ) {
        Icon(
            painter = painterResource(PekeIcon.WATCHED.outline),
            contentDescription = stringResource(R.string.played),
            tint = colors.secondary,
            modifier = Modifier.size(22.cpx),
        )
    }
}

/**
 * A favourite: Peke's heart in the primary colour on a round badge of the page colour
 */
@Composable
fun KidsFavouriteBadge(modifier: Modifier = Modifier) {
    val colors = LocalPekeColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(30.cpx).background(colors.background, CircleShape),
    ) {
        Icon(
            painter = painterResource(PekeIcon.FAVOURITES.outline),
            contentDescription = stringResource(R.string.favorites),
            tint = colors.primary,
            modifier = Modifier.size(22.cpx),
        )
    }
}

/**
 * The LIVE badge: Small type in "on primary" on a primary rounded label
 */
@Composable
fun KidsLiveBadge(
    modifier: Modifier = Modifier,
    size: Int = 15,
) {
    val colors = LocalPekeColors.current
    Text(
        text = stringResource(R.string.kids_live_badge),
        fontSize = size.csp,
        fontWeight = FontWeight.W900,
        color = colors.onPrimary,
        modifier =
            modifier
                .background(colors.primary, RoundedCornerShape(10.cpx))
                .padding(horizontal = 10.cpx, vertical = 2.cpx),
    )
}

/**
 * Between a card and its title at least: past the focus ring (4.5 + 6 dp) and the scale-up of a small card
 */
private val TitleGap = 16.dp
