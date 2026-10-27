package dev.aldoborrero.peke.kids.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.gate.LockChange
import dev.aldoborrero.peke.kids.gate.LockState
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.PasscodeHasher
import dev.aldoborrero.peke.ui.preferences.SetPinFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CodeChangeStep {
    data object Loading : CodeChangeStep

    /**
     * @param wrong the code typed before was wrong
     */
    data class Current(
        val type: ParentLockType,
        val attempt: Int = 0,
        val wrong: Boolean = false,
    ) : CodeChangeStep

    data class NewCode(
        val type: ParentLockType,
        val current: String,
    ) : CodeChangeStep

    data object Saving : CodeChangeStep

    /**
     * Finished, with a message for the grown-up
     */
    data class Done(
        val message: Int,
        val seconds: Int? = null,
    ) : CodeChangeStep
}

/**
 * Change the household's parent code from Settings › Household (spec 006 R10): the current code, then the new one
 * twice. The code keeps its type (numbers or remote buttons), which setup and the dashboard choose.
 */
@HiltViewModel
class ParentCodeChangeViewModel
    @Inject
    constructor(
        private val parentLockService: ParentLockService,
    ) : ViewModel() {
        private val _step = MutableStateFlow<CodeChangeStep>(CodeChangeStep.Loading)
        val step: StateFlow<CodeChangeStep> = _step

        private var wrongAttempts = 0

        init {
            viewModelScope.launch {
                _step.value =
                    when (val lock = parentLockService.lockState()) {
                        is LockState.Code -> CodeChangeStep.Current(lock.type)
                        LockState.Unknown -> CodeChangeStep.Done(R.string.kids_cant_reach_server)
                        else -> CodeChangeStep.Done(R.string.kids_code_cant_change)
                    }
            }
        }

        fun current(code: String) {
            val step = _step.value as? CodeChangeStep.Current ?: return
            _step.value = CodeChangeStep.NewCode(step.type, code)
        }

        fun newCode(code: String) {
            val step = _step.value as? CodeChangeStep.NewCode ?: return
            _step.value = CodeChangeStep.Saving
            viewModelScope.launch {
                _step.value =
                    when (val result = parentLockService.setCode(step.type, code, step.current)) {
                        LockChange.Done -> CodeChangeStep.Done(R.string.kids_gate_code_changed)
                        LockChange.WrongCurrent -> CodeChangeStep.Current(step.type, attempt = ++wrongAttempts, wrong = true)
                        is LockChange.Throttled -> CodeChangeStep.Done(R.plurals.kids_gate_wait, result.seconds)
                        LockChange.NeedsGrant -> CodeChangeStep.Done(R.string.kids_code_needs_grant)
                        LockChange.Unreachable -> CodeChangeStep.Done(R.string.kids_cant_reach_server)
                        LockChange.Invalid -> CodeChangeStep.Done(R.string.kids_save_failed)
                    }
            }
        }
    }

@Composable
fun ParentCodeChange(
    onDismissRequest: () -> Unit,
    viewModel: ParentCodeChangeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsState()
    when (val s = step) {
        CodeChangeStep.Loading, CodeChangeStep.Saving -> {}

        is CodeChangeStep.Current -> {
            LaunchedEffect(s) {
                if (s.wrong) {
                    Toast.makeText(context, R.string.kids_gate_wrong_passcode, Toast.LENGTH_SHORT).show()
                }
            }
            KidsPasscodeDialog(
                title = R.string.kids_gate_current_passcode,
                type = s.type,
                attempt = s.attempt,
                onDismissRequest = onDismissRequest,
                onConfirm = { if (it.isNotEmpty()) viewModel.current(it) },
            )
        }

        is CodeChangeStep.NewCode -> {
            key(s.type) {
                SetPinFlow(
                    currentPin = null,
                    onAddPin = viewModel::newCode,
                    onRemovePin = {},
                    onDismissRequest = onDismissRequest,
                    isValid = { PasscodeHasher.isValid(it, s.type) },
                    symbolFor = parentLockSymbol(s.type),
                    hint = { ParentLockHint(s.type, it) },
                )
            }
        }

        is CodeChangeStep.Done -> {
            LaunchedEffect(s) {
                val text =
                    s.seconds?.let { context.resources.getQuantityString(s.message, it, it) } ?: context.getString(s.message)
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                onDismissRequest()
            }
        }
    }
}
