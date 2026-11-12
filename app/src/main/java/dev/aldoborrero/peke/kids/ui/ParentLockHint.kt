package dev.aldoborrero.peke.kids.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 * What to press for [type]: the arrows or the number keys, never both (spec 019 D11)
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
            PinArrowRow(modifier, withDigits = false)
        }
    }
}
