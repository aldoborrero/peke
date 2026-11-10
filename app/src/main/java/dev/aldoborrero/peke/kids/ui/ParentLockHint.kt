package dev.aldoborrero.peke.kids.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.gate.ParentLockKeys
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.setup.PinArrowRow

/**
 * Which keys make up a code of [type], for [dev.aldoborrero.peke.kids.setup.PinEntryCreate]
 */
fun parentLockSymbol(type: ParentLockType): (Key) -> String? = { ParentLockKeys.symbol(it.nativeKeyCode, type)?.toString() }

/**
 * What to press for [type]: the accepted keys
 */
@Composable
fun ParentLockHint(
    type: ParentLockType,
    modifier: Modifier = Modifier,
) {
    when (type) {
        ParentLockType.NUMERIC -> {
            Text(
                text = stringResource(R.string.pin_digits_hint),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = modifier,
            )
        }

        ParentLockType.REMOTE -> {
            val extras = remember { ParentLockKeys.availableExtras() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier,
            ) {
                PinArrowRow()
                extras.forEach { ExtraKeyHint(it) }
            }
        }
    }
}

/**
 * Shows a remote button that can be part of the code
 */
@Composable
internal fun ExtraKeyHint(extra: ParentLockKeys.Extra) {
    val color = MaterialTheme.colorScheme.onSurface
    val buttonColor =
        when (extra) {
            // The colours of the physical buttons
            ParentLockKeys.Extra.RED -> Color(0xFFE53935)

            ParentLockKeys.Extra.GREEN -> Color(0xFF43A047)

            ParentLockKeys.Extra.YELLOW -> Color(0xFFFDD835)

            ParentLockKeys.Extra.BLUE -> Color(0xFF1E88E5)

            else -> null
        }
    when {
        buttonColor != null -> {
            Box(
                Modifier
                    .size(16.dp)
                    .background(buttonColor, CircleShape),
            )
        }

        extra == ParentLockKeys.Extra.PLAY_PAUSE -> {
            Icon(painterResource(R.drawable.baseline_play_arrow_24), contentDescription = null, tint = color)
        }

        extra == ParentLockKeys.Extra.REWIND -> {
            Icon(painterResource(R.drawable.baseline_fast_rewind_24), contentDescription = null, tint = color)
        }

        extra == ParentLockKeys.Extra.FAST_FORWARD -> {
            Icon(painterResource(R.drawable.baseline_fast_forward_24), contentDescription = null, tint = color)
        }

        else -> {
            Text(
                text =
                    when (extra) {
                        ParentLockKeys.Extra.CHANNEL_UP -> "CH+"
                        ParentLockKeys.Extra.CHANNEL_DOWN -> "CH−"
                        else -> "i"
                    },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
                color = color,
            )
        }
    }
}
