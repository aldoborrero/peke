package dev.aldoborrero.peke.kids.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.gate.LockChange
import dev.aldoborrero.peke.kids.gate.PasscodeHasher
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.setup.KidAvatar
import dev.aldoborrero.peke.kids.timer.AddTimeAvailability
import dev.aldoborrero.peke.kids.timer.AddTimePreview
import dev.aldoborrero.peke.kids.timer.TodayChange
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.components.DialogItem
import dev.aldoborrero.peke.ui.components.DialogItemText
import dev.aldoborrero.peke.ui.components.DialogPopup
import dev.aldoborrero.peke.ui.preferences.SetPinFlow
import dev.aldoborrero.peke.ui.theme.cpx

/**
 * The single way to grown-up control: the household parent code, then a menu for grown-ups (`contracts/tv-flows.md`,
 * spec 006 `contracts/grown-up-screens.md`). Nothing in it unlocks the TV; resetting it is the only way out of kids
 * mode. On a server whose plugin went away before this TV cached the code, a server administrator's sign-in only
 * allows resetting this TV.
 *
 * @param withKid whether a kid's session is open behind the gate; from the picker or the "no kids" page there is none,
 * and the code opens Settings straight away
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
    // The app went to the background since the gate opened: it closes, so the kid who comes back finds neither the
    // menu nor a code being set
    val epoch by viewModel.epoch.collectAsState()
    val openedAt = remember { epoch }
    LaunchedEffect(epoch) { if (epoch != openedAt) onDismissRequest() }
    val context = LocalContext.current
    val stage by viewModel.stage.collectAsState()
    val limits by viewModel.timeLimits.state.collectAsState()
    val snapshot by viewModel.timeLimits.allowanceService.snapshot
        .collectAsState()
    val reachable by viewModel.timeLimits.allowanceService.reachable
        .collectAsState()
    val plugin by viewModel.pluginService.current.collectAsState()
    val current by viewModel.current.collectAsState()
    val canSwitchKid by viewModel.canSwitchKid.collectAsState()
    val notice by viewModel.notices.current.collectAsState()
    val kidName = current?.user?.name
    // The menu's pages need the grant, so choosing one closes the gate without ending it; anything else that closes
    // the menu ends it
    val leaveMenu = {
        viewModel.leaveMenu()
        onDismissRequest()
    }
    val leaveFor = { open: () -> Unit ->
        onDismissRequest()
        open()
    }

    when (val s = stage) {
        is GateStage.Passcode -> {
            val type = s.type ?: return
            // The page says why a code was refused (spec 019); recovery is OK with no code, which the page says too
            ParentCodePage(
                title = R.string.kids_gate_title,
                type = type,
                attempt = s.attempt,
                refusal = s.refusal,
                backLabel = gateBackLabel(kidName.takeIf { withKid }),
                forgotHint = true,
                onDismissRequest = onDismissRequest,
                onConfirm = { viewModel.submit(it, onDone = onDismissRequest, onPassed = onPassed) },
            )
        }

        is GateStage.RecoveryCode -> {
            key(s.type) {
                SetPinFlow(
                    currentPin = null,
                    onAddPin = { code ->
                        viewModel.setRecoveredCode(code) { result ->
                            // A pill on the menu, or on Settings without a kid (spec 021 D11)
                            viewModel.notices.post(
                                when (result) {
                                    LockChange.Done -> GrownUpNotice(R.string.kids_gate_code_changed)
                                    LockChange.Unreachable -> GrownUpNotice(R.string.kids_cant_reach_server, problem = true)
                                    else -> GrownUpNotice(R.string.kids_save_failed, problem = true)
                                },
                            )
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
            // The explanation is text that focus skips (F-G5): focus starts on the action
            DialogPopup(
                showDialog = true,
                title = stringResource(R.string.kids_parent_code),
                dialogItems =
                    listOf(
                        DialogItemText(stringResource(if (s.notSet) R.string.kids_gate_no_code else R.string.kids_gate_no_code_offline)),
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
                title = stringResource(R.string.kids_parent_code),
                dialogItems =
                    listOf(
                        DialogItemText(stringResource(R.string.kids_gate_recovery_only)),
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
                title = stringResource(pluginGoneTitle(plugin.outdated)),
                dialogItems =
                    listOf(
                        DialogItemText(stringResource(R.string.kids_gate_plugin_missing)),
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
                onQuickConnect = viewModel::startQuickConnect,
                onPassword = viewModel::usePassword,
                onDismissRequest = { if (!viewModel.backFromSignIn()) onDismissRequest() },
            )
        }

        GateStage.AdminMenu -> {
            DialogPopup(
                showDialog = true,
                title = stringResource(pluginGoneTitle(plugin.outdated)),
                dialogItems =
                    listOf(
                        DialogItemText(stringResource(pluginGoneSummary(plugin.outdated))),
                        DialogItem(stringResource(R.string.peke_menu_reset), dismissOnClick = true) { viewModel.openReset() },
                    ),
                onDismissRequest = onDismissRequest,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        GateStage.Menu -> {
            if (onPassed != null) {
                // A recovery on a page that asked for the code: back to that page, as a right code would
                LaunchedEffect(Unit) {
                    onPassed()
                    onDismissRequest()
                }
                return
            }
            if (!withKid) {
                // Without a kid there is nothing but Settings, which holds Kids on this TV (spec 006 FR-018)
                LaunchedEffect(Unit) { leaveFor(viewModel::openThisTv) }
                return
            }
            val now = viewModel.now()
            val availability =
                AddTimeAvailability.of(
                    pluginAvailable = plugin.status == KidsServerStatus.AVAILABLE && plugin.has(KidsFeatures.ALLOWANCE),
                    status = snapshot?.status,
                    limits = limits,
                    reachable = reachable,
                    canEndBreak = plugin.has(KidsFeatures.BREAK_END),
                    now = now,
                )
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
            val pluginGone =
                if (plugin.status == KidsServerStatus.GONE) {
                    stringResource(pluginGoneTitle(plugin.outdated)) to stringResource(pluginGoneSummary(plugin.outdated))
                } else {
                    null
                }
            val addTime =
                when (availability) {
                    AddTimeAvailability.Available -> {
                        MenuAddTime.Available
                    }

                    is AddTimeAvailability.OnBreak -> {
                        val reason =
                            availability.endsAt?.let { stringResource(R.string.kids_add_time_on_break, formatClock(context, it)) }
                                ?: stringResource(R.string.kids_add_time_on_break_unknown)
                        if (availability.canEnd) MenuAddTime.KeepWatching(reason) else MenuAddTime.Unavailable(reason)
                    }

                    AddTimeAvailability.Bedtime -> {
                        MenuAddTime.Unavailable(stringResource(R.string.kids_add_time_bedtime_now))
                    }

                    AddTimeAvailability.Unlimited -> {
                        MenuAddTime.Unavailable(stringResource(R.string.kids_add_time_unlimited, kidName.orEmpty()))
                    }

                    AddTimeAvailability.Offline -> {
                        MenuAddTime.Unavailable(stringResource(R.string.kids_add_time_offline))
                    }
                }
            val avatar by viewModel.avatar.collectAsState()
            val imageUrl = current?.user?.id?.let { LocalImageUrlService.current.getUserImageUrl(it) }
            // Back leaves the menu without a page to open, and ends the grant
            GrownUpWindow(onBack = leaveMenu) {
                GrownUpMenuPage(
                    kidName = kidName,
                    todayLeft = todayLeft,
                    sittingLeft = sittingLeft,
                    pluginGone = pluginGone,
                    addTime = addTime,
                    canSwitchKid = canSwitchKid,
                    notice = notice,
                    onNoticeShown = viewModel.notices::shown,
                    onAddTime = viewModel::openAddTime,
                    onKeepWatching = {
                        viewModel.keepWatching { result ->
                            if (result == TodayChange.DONE) {
                                onDismissRequest()
                            } else {
                                val message =
                                    if (result ==
                                        TodayChange.UNREACHABLE
                                    ) {
                                        R.string.kids_cant_reach_server
                                    } else {
                                        R.string.kids_save_failed
                                    }
                                viewModel.notices.post(GrownUpNotice(message, problem = true))
                            }
                        }
                    },
                    onSwitchKid = { leaveFor(viewModel::switchKid) },
                    onRules = { leaveFor(viewModel::openRules) },
                    onSettings = { leaveFor(viewModel::openThisTv) },
                    avatar =
                        current?.user?.let { user ->
                            {
                                KidAvatar(
                                    id = user.id,
                                    name = user.name,
                                    imageUrl = imageUrl,
                                    avatar = avatar,
                                    alpha = 1f,
                                    initialStyle = boardText(48, FontWeight.W900),
                                    modifier = Modifier.size(112.cpx),
                                )
                            }
                        },
                )
            }
        }

        is GateStage.AddTime -> {
            val preview = AddTimePreview.of(limits, s.minutes, viewModel.now(), viewModel.schedules)
            // Back returns to the menu, with the grant still live
            GrownUpWindow(onBack = viewModel::backToMenu) {
                AddTimePage(
                    kidName = kidName,
                    index = s.index,
                    sending = s.sending,
                    error = s.error,
                    preview = preview,
                    onStep = viewModel::stepAddTime,
                    onConfirm = viewModel::sendAddTime,
                )
            }
        }

        is GateStage.Added -> {
            var undone by remember { mutableStateOf<TodayChange?>(null) }
            val done = {
                viewModel.closeAdded()
                onDismissRequest()
            }
            GrownUpWindow(onBack = done) {
                AddTimeAddedPage(
                    kidName = kidName,
                    minutes = s.minutes,
                    until = s.until,
                    canUndo = s.bonusId != null,
                    undoSeconds = s.undoSeconds,
                    undoing = s.undoing,
                    undoError = s.undoError,
                    undone = undone,
                    // The page says it was taken back, then returns to the kid (spec 021 D6)
                    onUndo = { viewModel.undoAddTime { undone = it } },
                    onDone = done,
                )
            }
        }
    }
}

/**
 * What the code page's Back pill says: "Back to {Kid}" inside a kid's session, else "Back" (spec 019 D4)
 */
@Composable
fun gateBackLabel(kidName: String?): String =
    kidName?.let { stringResource(R.string.kids_gate_back_to, it) } ?: stringResource(R.string.kids_gate_back)

/**
 * Why kids mode no longer works with the server: its plugin is too old, or gone
 */
@StringRes
fun pluginGoneTitle(outdated: Boolean): Int = if (outdated) R.string.kids_plugin_outdated else R.string.kids_plugin_gone

@StringRes
fun pluginGoneSummary(outdated: Boolean): Int = if (outdated) R.string.kids_plugin_outdated_summary else R.string.kids_plugin_gone_summary
