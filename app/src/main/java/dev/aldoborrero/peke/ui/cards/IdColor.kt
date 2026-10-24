package dev.aldoborrero.peke.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import java.util.UUID

/**
 * Generate a consistent color for a UUID
 */
@Composable
fun rememberIdColor(
    id: UUID?,
    alpha: Float = 1f,
    nullColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): Color =
    remember(id, alpha) {
        if (id == null) {
            return@remember nullColor
        }
        // Generate a color based on the server ID hash, fallback to URL hash
        val hash = id.hashCode()
        val hue = (hash % 360).toFloat()
        val saturation = 0.6f + ((hash / 360) % 40).toFloat() / 100f // 0.6-1.0
        val brightness = 0.4f + ((hash / 14400) % 30).toFloat() / 100f // 0.4-0.7 (darker colors)

        // Convert HSV to RGB
        val c = brightness * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2f - 1))
        val m = brightness - c

        val (r, g, b) =
            when {
                hue < 60 -> Triple(c, x, 0f)
                hue < 120 -> Triple(x, c, 0f)
                hue < 180 -> Triple(0f, c, x)
                hue < 240 -> Triple(0f, x, c)
                hue < 300 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        Color(
            red = (r + m).coerceIn(0f, 1f),
            green = (g + m).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
            alpha = alpha,
        )
    }
