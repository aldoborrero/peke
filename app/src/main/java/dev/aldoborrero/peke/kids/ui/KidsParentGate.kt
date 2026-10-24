package dev.aldoborrero.peke.kids.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.gate.LockChange
import dev.aldoborrero.peke.kids.gate.PasscodeHasher
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.timer.BonusResult
import dev.aldoborrero.peke.ui.components.DialogItem
import dev.aldoborrero.peke.ui.components.DialogItemEntry
import dev.aldoborrero.peke.ui.components.DialogPopup
import dev.aldoborrero.peke.ui.preferences.SetPinFlow

/**
 * The single way to grown-up control: the household parent code, then a menu for grown-ups (`contracts/tv-flows.md`).
 * Nothing in it unlocks the TV; resetting it is the only way out of kids mode. On a server whose plugin went away
 * before this TV cached the code, a server administrator's sign-in only allows resetting this TV.
 *
 * @param withKid whether a kid's session is open behind the gate; from the picker or the "no kids" page there is none,
 * and the menu leaves out that kid's items
 * @param onPassed where to go once the code is right, instead of the menu, such as a kid picked while another is
 * stopped
 */
@Composable
fun KidsParentGate(
    onDismissRequest: () -> Unit,
    withKid: Boolean = true,
    onPassed: (() -> Unit)? = null,
    viewModel: KidsParentGateViewModel = hiltViewModel(),
) {
    viewModel.withKid = withKid
    DisposableEffect(Unit) { onDispose { viewModel.reset() } }
    val context = LocalContext.current
    val stage by viewModel.stage.collectAsState()
    val limits by viewModel.timeLimits.state.collectAsState()
    val snapshot by viewModel.timeLimits.allowanceService.snapshot
        .collectAsState()
    val plugin by viewModel.pluginService.current.collectAsState()
    val resources = context.resources

    when (val s = stage) {
        is GateStage.Passcode -> {
            val type = s.type ?: return
            KidsPasscodeDialog(
                title = R.string.kids_gate_passcode,
                type = type,
                attempt = s.attempt,
                onDismissRequest = onDismissRequest,
                onConfirm = {
                    viewModel.submit(it, onDone = onDismissRequest, onPassed = onPassed) { refusal ->
                        val message =
                            when (refusal) {
                                GateRefusal.Wrong -> {
                                    resources.getString(R.string.kids_gate_wrong_passcode)
                                }

                                is GateRefusal.Wait -> {
                                    resources.getQuantityString(
                                        R.plurals.kids_gate_wait,
                                        refusal.seconds,
                                        refusal.seconds,
                                    )
                                }

                                GateRefusal.CannotCheck -> {
                                    resources.getString(R.string.kids_gate_cannot_check)
                                }
                            }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        is GateStage.WrongCode -> {
            // Try again comes first and is focused; recovery needs a server administrator
            DialogPopup(
                showDialog = true,
                title = stringResource(R.string.kids_gate_wrong_passcode),
                dialogItems =
                    listOf(
                        DialogItem(stringResource(R.string.peke_setup_try_again)) { viewModel.retryCode() },
                        DialogItem(stringResource(R.string.kids_gate_forgot)) { viewModel.forgotCode() },
                    ),
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        is GateStage.RecoveryCode -> {
            key(s.type) {
                SetPinFlow(
                    currentPin = null,
                    onAddPin = { code ->
                        viewModel.setRecoveredCode(code) { result ->
                            val message =
                                when (result) {
                                    LockChange.Done -> R.string.kids_gate_code_changed
                                    LockChange.Unreachable -> R.string.kids_cant_reach_server
                                    else -> R.string.kids_save_failed
                                }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemovePin = {},
                    onDismissRequest = onDismissRequest,
                    isValid = { PasscodeHasher.isValid(it, s.type) },
                    symbolFor = parentLockSymbol(s.type),
                    hint = { ParentLockHint(s.type, it) },
                )
            }
        }

        is GateStage.NoCode -> {
            DialogPopup(
                showDialog = true,
                title = stringResource(R.string.kids_grown_ups),
                dialogItems =
                    listOf(
                        DialogItem(
                            headlineContent = {
                                Text(stringResource(if (s.notSet) R.string.kids_gate_no_code else R.string.kids_gate_no_code_offline))
                            },
                            dismissOnClick = true,
                            onClick = {},
                        ),
                        DialogItem(stringResource(if (s.notSet) R.string.kids_gate_set_code_admin else R.string.kids_gate_forgot)) {
                            viewModel.forgotCode()
                        },
                    ),
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        GateStage.RecoveryOnly -> {
            DialogPopup(
                showDialog = true,
                title = stringResource(R.string.kids_grown_ups),
                dialogItems =
                    listOf(
                        DialogItem(
                            headlineContent = { Text(stringResource(R.string.kids_gate_recovery_only)) },
                            onClick = {},
                        ),
                        DialogItem(stringResource(R.string.kids_gate_forgot)) { viewModel.forgotCode() },
                    ),
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        GateStage.PluginMissing -> {
            DialogPopup(
                showDialog = true,
                title = stringResource(R.string.kids_grown_ups),
                dialogItems =
                    listOf(
                        DialogItem(
                            headlineContent = { Text(stringResource(pluginGoneTitle(plugin.outdated))) },
                            supportingContent = { Text(stringResource(R.string.kids_gate_plugin_missing)) },
                            onClick = {},
                        ),
                        DialogItem(stringResource(R.string.kids_admin_sign_in)) { viewModel.startAdminSignIn() },
                    ),
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        is GateStage.AdminSignIn -> {
            KidsAdminSignInDialog(
                stage = s,
                onSubmit = viewModel::adminSignIn,
                onDismissRequest = onDismissRequest,
            )
        }

        GateStage.AdminMenu -> {
            DialogPopup(
                showDialog = true,
                title = stringResource(R.string.kids_grown_ups),
                dialogItems =
                    listOf(
                        DialogItem(
                            headlineContent = { Text(stringResource(pluginGoneTitle(plugin.outdated))) },
                            onClick = {},
                        ),
                        DialogItem(stringResource(R.string.peke_menu_reset), dismissOnClick = true) { viewModel.openReset() },
                    ),
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        GateStage.Menu -> {
            val status = snapshot?.status
            // The server decides; adding minutes to today's allowance is the only time a grown-up can give here
            val canAddTime =
                plugin.status == KidsServerStatus.AVAILABLE &&
                    status != null &&
                    !status.unlimited &&
                    (limits.denial == null || limits.denial == KidsDenial.TIME_UP)
            val todayLeft =
                limits.allowanceRemaining?.takeIf { !limits.expired }?.let {
                    val left = it.inWholeMinutes.toInt().coerceAtLeast(1)
                    pluralStringResource(R.plurals.kids_minutes_left_today, left, left)
                }
            val sittingLeft =
                limits.sittingRemaining?.takeIf { !limits.expired }?.let {
                    val left = it.inWholeMinutes.toInt().coerceAtLeast(1)
                    pluralStringResource(R.plurals.kids_minutes_left_sitting, left, left)
                }
            val breakLeft =
                limits.breakRemaining?.let {
                    val left = it.inWholeMinutes.toInt().coerceAtLeast(1)
                    pluralStringResource(R.plurals.kids_break_left, left, left)
                }
            val items =
                buildList<DialogItemEntry> {
                    if (plugin.status == KidsServerStatus.GONE) {
                        add(
                            DialogItem(
                                headlineContent = { Text(stringResource(pluginGoneTitle(plugin.outdated))) },
                                supportingContent = { Text(stringResource(pluginGoneSummary(plugin.outdated))) },
                                onClick = {},
                            ),
                        )
                        add(DialogItem.divider())
                    }
                    if (withKid) {
                        if (canAddTime) {
                            add(DialogItem(stringResource(R.string.kids_add_time)) { viewModel.chooseMinutes() })
                        }
                        listOfNotNull(todayLeft, sittingLeft, breakLeft).forEach { line ->
                            add(DialogItem(headlineContent = { Text(line) }, onClick = {}))
                        }
                        add(DialogItem.divider())
                        add(DialogItem(stringResource(R.string.kids_settings), dismissOnClick = true) { viewModel.openSettings() })
                    }
                    add(DialogItem(stringResource(R.string.peke_menu_kids_on_tv), dismissOnClick = true) { viewModel.openKidsOnTv() })
                    if (withKid) {
                        add(DialogItem(stringResource(R.string.peke_menu_this_tv), dismissOnClick = true) { viewModel.openThisTv() })
                    }
                    add(DialogItem(stringResource(R.string.license_info), dismissOnClick = true) { viewModel.openLicense() })
                    add(DialogItem.divider())
                    add(DialogItem(stringResource(R.string.peke_menu_reset), dismissOnClick = true) { viewModel.openReset() })
                }
            DialogPopup(
                showDialog = true,
                title = stringResource(R.string.kids_grown_ups),
                dialogItems = items,
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        GateStage.Minutes -> {
            DialogPopup(
                showDialog = true,
                title = stringResource(R.string.kids_add_time),
                dialogItems =
                    KidsParentGateViewModel.MINUTE_OPTIONS.map { minutes ->
                        DialogItem(
                            headlineContent = { Text(pluralStringResource(R.plurals.kids_minutes_option, minutes, minutes)) },
                            dismissOnClick = true,
                            onClick = {
                                viewModel.addTime(minutes) { result ->
                                    val message =
                                        when (result) {
                                            BonusResult.ADDED -> null
                                            BonusResult.NEEDS_GRANT -> R.string.kids_gate_cannot_check
                                            BonusResult.UNREACHABLE -> R.string.kids_cant_reach_server
                                            BonusResult.FAILED -> R.string.kids_save_failed
                                        }
                                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                }
                            },
                        )
                    },
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }
    }
}

/**
 * Why kids mode no longer works with the server: its plugin is too old, or gone
 */
@StringRes
fun pluginGoneTitle(outdated: Boolean): Int = if (outdated) R.string.kids_plugin_outdated else R.string.kids_plugin_gone

@StringRes
fun pluginGoneSummary(outdated: Boolean): Int = if (outdated) R.string.kids_plugin_outdated_summary else R.string.kids_plugin_gone_summary
