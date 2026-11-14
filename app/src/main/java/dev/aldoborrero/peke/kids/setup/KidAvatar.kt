package dev.aldoborrero.peke.kids.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.aldoborrero.peke.ui.Cards
import dev.aldoborrero.peke.ui.cards.rememberIdColor
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.theme.LocalPekeFocusScale
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.pekeFocus
import java.util.UUID

/**
 * What shows for a kid: their avatar, else their Jellyfin picture, else their initial
 */
sealed interface KidPicture {
    data class Avatar(
        val avatar: PekeAvatar,
    ) : KidPicture

    data class Image(
        val url: String,
    ) : KidPicture

    data object Initial : KidPicture
}

/**
 * The [KidPicture] for a kid with [avatar] (from the Kids plugin) and Jellyfin picture [imageUrl]
 *
 * @param imageFailed the picture at [imageUrl] couldn't be loaded
 */
fun kidPicture(
    avatar: PekeAvatar?,
    imageUrl: String?,
    imageFailed: Boolean,
): KidPicture =
    when {
        avatar != null -> KidPicture.Avatar(avatar)
        imageUrl.isNotNullOrBlank() && !imageFailed -> KidPicture.Image(imageUrl)
        else -> KidPicture.Initial
    }

/**
 * A kid's avatar (picked by a grown-up in the Kids plugin), their Jellyfin picture, or their initial on their own
 * colour when they have neither or the picture can't load
 *
 * @param alpha of the colour behind the initial
 * @param initialStyle of the initial, smaller for a small avatar such as the sidebar's
 */
@Composable
fun KidAvatar(
    id: UUID,
    name: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    avatar: PekeAvatar? = null,
    alpha: Float = .6f,
    initialStyle: TextStyle = MaterialTheme.typography.displaySmall,
) {
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    when (val picture = kidPicture(avatar, imageUrl, imageError)) {
        is KidPicture.Avatar -> {
            // The avatar carries its own round background
            Image(
                painter = painterResource(picture.avatar.drawable),
                contentDescription = name,
                modifier = modifier.clip(CircleShape),
            )
        }

        is KidPicture.Image -> {
            AsyncImage(
                model = picture.url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { imageError = true },
                modifier =
                    modifier
                        .background(rememberIdColor(id, alpha), CircleShape)
                        .clip(CircleShape),
            )
        }

        KidPicture.Initial -> {
            Box(
                modifier = modifier.background(rememberIdColor(id, alpha), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = remember(id, name) { (name ?: id.toString()).firstOrNull()?.uppercase() ?: "?" },
                    style = initialStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A round card with a kid's avatar and name below, as Wholphin's user list showed users
 *
 * @param selected shows a check mark, for picking kids
 * @param caption a line under the name, such as that a grown-up is needed
 * @param avatar the avatar a grown-up picked in the Kids plugin, shown before [imageUrl]
 * @param badge an icon over the avatar instead of the check mark
 */
@Composable
fun KidAvatarCard(
    id: UUID,
    name: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: PekeAvatar? = null,
    selected: Boolean = false,
    caption: String? = null,
    badge: ImageVector? = null,
) {
    val size = Cards.serverUserCircle
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(Modifier.size(size)) {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxSize().pekeFocus(CircleShape),
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                border =
                    ClickableSurfaceDefaults.border(
                        border =
                            if (selected) {
                                Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = CircleShape)
                            } else {
                                Border.None
                            },
                        focusedBorder = Border.None,
                    ),
                scale = PekeFocus.surfaceScale,
                interactionSource = interactionSource,
            ) {
                KidAvatar(id = id, name = name, imageUrl = imageUrl, avatar = avatar, modifier = Modifier.fillMaxSize())
            }
            val icon = badge ?: if (selected) Icons.Default.CheckCircle else null
            icon?.let { KidBadge(it, size, interactionSource) }
        }
        Text(
            text = name ?: "",
            // DESIGN's Headline, for kid names on profiles
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(size),
        )
        caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(size),
            )
        }
    }
}

/**
 * The badge sits on the avatar's edge at the bottom end, over its border and focus ring, outside the round surface
 * that would clip it, and grows with the avatar while it's focused
 */
@Composable
private fun BoxScope.KidBadge(
    icon: ImageVector,
    circle: Dp,
    interactionSource: MutableInteractionSource,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val target = if (focused) PekeFocus.scale(LocalPekeFocusScale.current) else 1f
    val scale by animateFloatAsState(target, tween(PekeFocus.ANIMATION_MILLIS), label = "badge")
    val onEdge = circle / 2 * COS_45
    Box(
        modifier =
            Modifier
                .matchParentSize()
                // Above the avatar, which is above its neighbours while focused and draws its focus ring over itself
                .zIndex(2f)
                // Scaled about the avatar's centre, as the avatar is
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(x = onEdge, y = onEdge)
                    .size(KidBadgeSize)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .testTag(KID_BADGE_TAG),
        )
    }
}

private const val COS_45 = 0.70710677f

/**
 * The check mark or lock on a [KidAvatarCard], centred on the avatar's edge
 */
internal val KidBadgeSize = 40.dp

internal const val KID_BADGE_TAG = "kid_badge"
