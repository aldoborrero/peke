package com.github.damontecres.wholphin.kids.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.kids.server.KidsDenial
import com.github.damontecres.wholphin.kids.server.KidsServerStatus
import com.github.damontecres.wholphin.kids.timer.BonusResult
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogItemEntry
import com.github.damontecres.wholphin.ui.components.DialogPopup

/**
 * The single way out of kids mode: the household parent code, then a menu for grown-ups. On a server whose plugin
 * went away before this TV cached the code, a server administrator's sign-in only allows turning kids mode off there.
 */
@Composable
fun KidsParentGate(
    onDismissRequest: () -> Unit,
    unlockOnly: Boolean = false,
    viewModel: KidsParentGateViewModel = hiltViewModel(),
) {
    viewModel.unlockOnly = unlockOnly
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
                    viewModel.submit(it, onDone = onDismissRequest) { refusal ->
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
                        DialogItem(stringResource(R.string.kids_turn_off_here), dismissOnClick = true) { viewModel.turnOffHere() },
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
                        add(DialogItem(stringResource(R.string.kids_turn_off_here), dismissOnClick = true) { viewModel.turnOffHere() })
                        add(DialogItem.divider())
                    }
                    if (canAddTime) {
                        add(DialogItem(stringResource(R.string.kids_add_time)) { viewModel.chooseMinutes() })
                    }
                    listOfNotNull(todayLeft, sittingLeft, breakLeft).forEach { line ->
                        add(DialogItem(headlineContent = { Text(line) }, onClick = {}))
                    }
                    add(DialogItem.divider())
                    add(DialogItem(stringResource(R.string.kids_settings), dismissOnClick = true) { viewModel.openSettings() })
                    add(DialogItem(stringResource(R.string.kids_leave), dismissOnClick = true) { viewModel.leave() })
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
