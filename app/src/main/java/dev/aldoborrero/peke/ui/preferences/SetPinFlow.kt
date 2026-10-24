package dev.aldoborrero.peke.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.setup.PinArrowRow
import dev.aldoborrero.peke.kids.setup.PinEntryCreate
import dev.aldoborrero.peke.kids.setup.pinSymbol
import dev.aldoborrero.peke.ui.components.BasicDialog

enum class PinFlowState {
    ENTER_PIN_TO_REMOVE,
    ENTER_PIN,
    CONFIRM_PIN,
}

/**
 * @param isValid whether a new PIN can be used
 * @param symbolFor which keys are part of the PIN, see [PinEntryCreate]
 * @param hint shown below the entered PIN, see [PinEntryCreate]
 */
@Composable
fun SetPinFlow(
    currentPin: String?,
    onAddPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onDismissRequest: () -> Unit,
    isValid: (String) -> Boolean = { it.length >= 4 },
    symbolFor: (Key) -> String? = ::pinSymbol,
    hint: @Composable (Modifier) -> Unit = { PinArrowRow(it) },
) {
    val context = LocalContext.current
    var flowState by
        remember {
            mutableStateOf(if (currentPin.isNullOrBlank()) PinFlowState.ENTER_PIN else PinFlowState.ENTER_PIN_TO_REMOVE)
        }
    var pendingPin by remember { mutableStateOf("") }

    AnimatedContent(flowState) { state ->
        BasicDialog(
            onDismissRequest = onDismissRequest,
        ) {
            when (state) {
                PinFlowState.ENTER_PIN_TO_REMOVE -> {
                    PinEntryCreate(
                        title = R.string.enter_pin,
                        onTextChange = {
                            if (it == currentPin) onRemovePin.invoke()
                        },
                        onConfirm = null,
                        symbolFor = symbolFor,
                        hint = hint,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                PinFlowState.ENTER_PIN -> {
                    PinEntryCreate(
                        title = R.string.enter_pin,
                        onTextChange = {},
                        onConfirm = {
                            if (isValid(it)) {
                                pendingPin = it
                                flowState = PinFlowState.CONFIRM_PIN
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.pin_too_short),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                        symbolFor = symbolFor,
                        hint = hint,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                PinFlowState.CONFIRM_PIN -> {
                    PinEntryCreate(
                        title = R.string.confirm_pin,
                        onTextChange = {},
                        onConfirm = {
                            if (it == pendingPin) {
                                onAddPin.invoke(pendingPin)
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.incorrect),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                flowState = PinFlowState.ENTER_PIN
                            }
                        },
                        symbolFor = symbolFor,
                        hint = hint,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
