package dev.aldoborrero.peke.kids.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import java.util.UUID

/**
 * A kid's picture, or their initial on their own colour when there is none or it can't load
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
    alpha: Float = .6f,
    initialStyle: TextStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
) {
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    val color = rememberIdColor(id, alpha)
    Box(
        modifier = modifier.background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNotNullOrBlank() && !imageError) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { imageError = true },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
            )
        } else {
            Text(
                text = remember(id, name) { (name ?: id.toString()).firstOrNull()?.uppercase() ?: "?" },
                style = initialStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A round card with a kid's avatar and name below, as Wholphin's user list showed users
 *
 * @param selected shows a check mark, for picking kids
 * @param caption a line under the name, such as that a grown-up is needed
 * @param badge an icon over the avatar instead of the check mark
 */
@Composable
fun KidAvatarCard(
    id: UUID,
    name: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        Surface(
            onClick = onClick,
            modifier = Modifier.size(size),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            border =
                ClickableSurfaceDefaults.border(
                    border =
                        if (selected) {
                            Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = CircleShape)
                        } else {
                            Border.None
                        },
                    focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), shape = CircleShape),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(Modifier.fillMaxSize()) {
                KidAvatar(id = id, name = name, imageUrl = imageUrl, modifier = Modifier.fillMaxSize())
                val icon = badge ?: if (selected) Icons.Default.CheckCircle else null
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
        }
        Text(
            text = name ?: "",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
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
