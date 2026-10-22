package com.github.damontecres.wholphin.ui.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.playback.isEnterKey
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

@Composable
fun PinEntry(
    onTextChange: (String) -> Unit,
    onClickServerAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.enter_pin),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        PinArrowRow(Modifier.align(Alignment.CenterHorizontally))
        PinEntryDots(input.length, Modifier.align(Alignment.CenterHorizontally))

        TextButton(
            onClick = onClickServerAuth,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            var str = input
                            str += pinSymbol(it.key) ?: return@onKeyEvent false
                            onTextChange.invoke(str)
                            input = str
                            return@onKeyEvent true
                        } else {
                            return@onKeyEvent false
                        }
                    },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.use_server_credentials),
                )
                Text(
                    text = stringResource(R.string.will_remove_pin),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/**
 * The PIN symbol for a key: a D-pad direction (U, R, D, L) or a digit
 */
fun pinSymbol(key: Key): String? =
    when (key) {
        Key.DirectionUp -> "U"
        Key.DirectionRight -> "R"
        Key.DirectionDown -> "D"
        Key.DirectionLeft -> "L"
        Key.Zero, Key.NumPad0 -> "0"
        Key.One, Key.NumPad1 -> "1"
        Key.Two, Key.NumPad2 -> "2"
        Key.Three, Key.NumPad3 -> "3"
        Key.Four, Key.NumPad4 -> "4"
        Key.Five, Key.NumPad5 -> "5"
        Key.Six, Key.NumPad6 -> "6"
        Key.Seven, Key.NumPad7 -> "7"
        Key.Eight, Key.NumPad8 -> "8"
        Key.Nine, Key.NumPad9 -> "9"
        else -> null
    }

/**
 * @param symbolFor maps a key to the symbol it adds to the PIN, or null to ignore the key
 * @param hint shown above the entry, describing which keys can be used
 */
@Composable
fun PinEntryCreate(
    @StringRes title: Int,
    onTextChange: (String) -> Unit,
    onConfirm: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    symbolFor: (Key) -> String? = ::pinSymbol,
    hint: @Composable (Modifier) -> Unit = { PinArrowRow(it) },
) {
    var input by remember { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .onKeyEvent {
                    if (isEnterKey(it)) {
                        // Confirm once per press: key down and key up both arrive here
                        if (it.type == KeyEventType.KeyUp) onConfirm?.invoke(input)
                        return@onKeyEvent true
                    }
                    val symbol = symbolFor(it.key) ?: return@onKeyEvent false
                    if (it.type == KeyEventType.KeyUp) {
                        val str = input + symbol
                        onTextChange.invoke(str)
                        input = str
                    }
                    // Extra keys such as media buttons also consume their key down, so they do not act elsewhere
                    return@onKeyEvent it.type == KeyEventType.KeyUp || pinSymbol(it.key) == null
                }.focusable(),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        hint(Modifier.align(Alignment.CenterHorizontally))
        PinEntryDots(input.length, Modifier.align(Alignment.CenterHorizontally))
        if (onConfirm != null) {
            Text(
                text = stringResource(R.string.press_enter_to_confirm),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun PinArrowRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val arrows =
            listOf(R.string.fa_arrow_left_long, R.string.fa_arrow_up_long, R.string.fa_arrow_right_long, R.string.fa_arrow_down_long)
        arrows.forEach {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontAwesome,
            )
        }
        Text(
            text = stringResource(R.string.pin_digits_hint),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun PinEntryDots(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            modifier
                .defaultMinSize(minWidth = 180.dp, minHeight = 40.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    shape = CircleShape,
                ).padding(vertical = 16.dp),
    ) {
        repeat(count) {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 1f))
                        .size(8.dp),
            )
        }
    }
}

@Composable
fun PinEntryDialog(
    onDismissRequest: () -> Unit,
    onTextChange: (String) -> Unit,
    onClickServerAuth: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        PinEntry(
            onTextChange = onTextChange,
            onClickServerAuth = onClickServerAuth,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@PreviewTvSpec
@Composable
private fun PinEntryPreview() {
    WholphinTheme {
        PinEntry(
            onTextChange = {},
            onClickServerAuth = {},
            modifier = Modifier,
        )
    }
}
