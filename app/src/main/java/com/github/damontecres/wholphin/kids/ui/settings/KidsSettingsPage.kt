package com.github.damontecres.wholphin.kids.ui.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.kids.data.KidProfile
import com.github.damontecres.wholphin.kids.data.KidsHomeSection
import com.github.damontecres.wholphin.kids.gate.LockState
import com.github.damontecres.wholphin.kids.gate.ParentLockType
import com.github.damontecres.wholphin.kids.gate.PasscodeHasher
import com.github.damontecres.wholphin.kids.server.KidProfileMapper
import com.github.damontecres.wholphin.kids.timer.KidsTimerBarPosition
import com.github.damontecres.wholphin.kids.ui.KidsPasscodeDialog
import com.github.damontecres.wholphin.kids.ui.ParentLockHint
import com.github.damontecres.wholphin.kids.ui.parentLockSymbol
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.ui.preferences.ChoicePreference
import com.github.damontecres.wholphin.ui.preferences.ClickPreference
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference
import com.github.damontecres.wholphin.ui.preferences.NavDrawerPreferenceListItem
import com.github.damontecres.wholphin.ui.preferences.SetPinFlow
import com.github.damontecres.wholphin.ui.preferences.SwitchPreference
import com.github.damontecres.wholphin.ui.search.SearchForDialog
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

private sealed interface LockDialog {
    data class Verify(
        val type: ParentLockType,
    ) : LockDialog

    /**
     * Ask for the current code before changing it to a new code of [type]
     */
    data class Current(
        val type: ParentLockType,
    ) : LockDialog

    data class NewCode(
        val type: ParentLockType,
        val current: String?,
    ) : LockDialog
}

@StringRes
private fun lockTypeName(type: ParentLockType): Int =
    when (type) {
        ParentLockType.NUMERIC -> R.string.kids_lock_numeric
        ParentLockType.REMOTE -> R.string.kids_lock_remote
    }

@StringRes
private fun lockTypeSummary(type: ParentLockType): Int =
    when (type) {
        ParentLockType.NUMERIC -> R.string.kids_lock_numeric_summary
        ParentLockType.REMOTE -> R.string.kids_lock_remote_summary
    }

/**
 * Kids mode settings: the household parent code, which signed-in users are kid profiles, and each kid's options.
 * Everything but the timer bar is stored by the Jellyfin Kids plugin on the server.
 */
@Composable
fun KidsSettingsPage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        KidsSettingsContent(
            modifier =
                Modifier
                    .fillMaxWidth(.4f)
                    .fillMaxHeight()
                    .align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun KidsSettingsContent(
    modifier: Modifier = Modifier,
    viewModel: KidsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val timerBarPosition by viewModel.timerBarPosition.collectAsState(KidsTimerBarPosition.TOP)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<LockDialog?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<Pair<KidProfile, BaseItemKind>?>(null) }
    val focusRequester = remember { FocusRequester() }

    val wrongPasscode = stringResource(R.string.kids_gate_wrong_passcode)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text =
                message.count?.let { context.resources.getQuantityString(message.id, it, it) }
                    ?: context.getString(message.id)
            showToast(context, text, Toast.LENGTH_LONG)
        }
    }
    LaunchedEffect(state.loading, state.lock, state.verified) {
        val lockType = state.lockType
        if (!state.loading && lockType != null && !state.verified) dialog = LockDialog.Verify(lockType)
    }

    // Without a code or a server to check one, grown-ups only see why
    val unavailable = state.pluginOutdated || state.lock == LockState.Unknown || state.lock == LockState.PluginMissing
    val showContent = !state.loading && (state.verified || state.needsAdmin || unavailable)
    AnimatedVisibility(
        visible = showContent,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            focusRequester.tryRequestFocus()
        }
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        ) {
            Text(
                text = stringResource(R.string.kids_settings),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            )
            LazyColumn(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.pluginOutdated || state.needsAdmin || (unavailable && !state.verified)) {
                    item(key = "message") {
                        ClickPreference(
                            title = stringResource(R.string.kids_mode),
                            summary =
                                stringResource(
                                    when {
                                        state.pluginOutdated -> R.string.kids_plugin_outdated_summary
                                        state.needsAdmin -> R.string.kids_admin_needed
                                        state.lock == LockState.PluginMissing -> R.string.kids_plugin_gone
                                        else -> R.string.kids_cant_reach_server
                                    },
                                ),
                            onClick = {},
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                    return@LazyColumn
                }
                groupTitle(R.string.kids_grown_ups)
                item(key = "lock") {
                    val lockType = state.lockType
                    ChoicePreference(
                        title = stringResource(R.string.kids_household_code),
                        summary =
                            lockType?.let { stringResource(lockTypeName(it)) }
                                ?: stringResource(R.string.kids_set_code_first),
                        possibleValues = ParentLockType.entries,
                        selectedIndex = lockType?.ordinal ?: -1,
                        onValueChange = { index ->
                            val type = ParentLockType.entries[index]
                            dialog = if (state.isAdmin) LockDialog.NewCode(type, null) else LockDialog.Current(type)
                        },
                        valueDisplay = { _, type -> Text(stringResource(lockTypeName(type))) },
                        subtitleDisplay = { _, type -> { Text(stringResource(lockTypeSummary(type))) } },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
                val lockType = state.lockType ?: return@LazyColumn
                item(key = "change_code") {
                    ClickPreference(
                        title = stringResource(R.string.kids_change_passcode),
                        summary = stringResource(R.string.kids_household_code_summary),
                        onClick = {
                            dialog = if (state.isAdmin) LockDialog.NewCode(lockType, null) else LockDialog.Current(lockType)
                        },
                    )
                }
                item(key = "timer_bar") {
                    val values = stringArrayResource(R.array.kids_timer_bar_positions).toList()
                    ChoicePreference(
                        title = stringResource(R.string.kids_timer_bar),
                        summary = values[timerBarPosition.ordinal],
                        possibleValues = values,
                        selectedIndex = timerBarPosition.ordinal,
                        onValueChange = { viewModel.setTimerBarPosition(KidsTimerBarPosition.entries[it]) },
                    )
                }

                groupTitle(R.string.kids_profiles)
                items(state.users, key = { "user_${it.user.rowId}" }) { row ->
                    SwitchPreference(
                        title = row.user.name ?: row.user.id.toString(),
                        value = row.profile != null,
                        onClick = { viewModel.setKid(row.user, row.profile == null) },
                        summaryOn = stringResource(R.string.kids_profile_on),
                        summaryOff = stringResource(R.string.kids_profile_off),
                    )
                }

                state.users.forEach { row ->
                    val profile = row.profile ?: return@forEach
                    kidProfileSettings(
                        name = row.user.name.orEmpty(),
                        profile = profile,
                        pickedNames = state.pickedNames,
                        allowanceSupported = state.allowanceSupported,
                        sessionLimitSupported = state.sessionLimitSupported,
                        liveTvSupported = state.liveTvSupported,
                        viewModel = viewModel,
                        onPick = { kind -> picker = profile to kind },
                    )
                }

                serverRestrictions(state.currentPolicy)
            }
        }
    }

    picker?.let { (profile, kind) ->
        SearchForDialog(
            onDismissRequest = { picker = null },
            searchType = kind,
            onClick = { item ->
                viewModel.addPick(profile, item)
                picker = null
            },
        )
    }

    when (val current = dialog) {
        null -> {}

        is LockDialog.Verify -> {
            KidsPasscodeDialog(
                title = R.string.kids_gate_passcode,
                type = current.type,
                attempt = attempt,
                // Without passing the lock there is nothing to show here
                onDismissRequest = { viewModel.navigationManager.goBack() },
                onConfirm = { entered ->
                    scope.launch {
                        if (viewModel.verify(entered)) {
                            dialog = null
                        } else {
                            showToast(context, wrongPasscode, Toast.LENGTH_SHORT)
                            attempt++
                        }
                    }
                },
            )
        }

        is LockDialog.Current -> {
            val type = state.lockType ?: current.type
            KidsPasscodeDialog(
                title = R.string.kids_gate_current_passcode,
                type = type,
                attempt = attempt,
                onDismissRequest = { dialog = null },
                onConfirm = { entered -> dialog = LockDialog.NewCode(current.type, entered) },
            )
        }

        is LockDialog.NewCode -> {
            key(current.type) {
                SetPinFlow(
                    currentPin = null,
                    onAddPin = { code ->
                        dialog = null
                        scope.launch { viewModel.setPasscode(code, current.type, current.current) }
                    },
                    onRemovePin = {},
                    onDismissRequest = { dialog = null },
                    isValid = { PasscodeHasher.isValid(it, current.type) },
                    symbolFor = parentLockSymbol(current.type),
                    hint = { ParentLockHint(current.type, it) },
                )
            }
        }
    }
}

/**
 * A group title, like Wholphin's preference groups
 */
private fun LazyListScope.groupTitle(
    @StringRes title: Int,
) = item { GroupTitle(stringResource(title)) }

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
    )
}

@Suppress("UNCHECKED_CAST")
private fun LazyListScope.kidProfileSettings(
    name: String,
    profile: KidProfile,
    pickedNames: Map<java.util.UUID, String>,
    allowanceSupported: Boolean,
    sessionLimitSupported: Boolean,
    liveTvSupported: Boolean,
    viewModel: KidsSettingsViewModel,
    onPick: (BaseItemKind) -> Unit,
) {
    val id = profile.userId
    item(key = "kid_${id}_title") { GroupTitle(name) }
    if (allowanceSupported) {
        item(key = "kid_${id}_allowance") {
            MinutesChoice(
                title = stringResource(R.string.kids_daily_allowance),
                subtitle = stringResource(R.string.kids_daily_allowance_summary),
                none = stringResource(R.string.kids_unlimited),
                options = KidsSettingsViewModel.withCurrent(KidsSettingsViewModel.ALLOWANCE_OPTIONS, profile.dailyAllowanceMinutes),
                value = profile.dailyAllowanceMinutes,
                onValueChange = { value -> viewModel.update(id) { it.copy(dailyAllowanceMinutes = value) } },
            )
        }
    }
    if (sessionLimitSupported) {
        item(key = "kid_${id}_session_limit") {
            MinutesChoice(
                title = stringResource(R.string.kids_session_limit),
                subtitle = stringResource(R.string.kids_session_limit_summary),
                none = stringResource(R.string.no_limit),
                options = KidsSettingsViewModel.withCurrent(KidsSettingsViewModel.SESSION_LIMIT_OPTIONS, profile.sessionLimitMinutes),
                value = profile.sessionLimitMinutes,
                onValueChange = { value -> viewModel.update(id) { it.copy(sessionLimitMinutes = value) } },
            )
        }
    }
    if (liveTvSupported) {
        // Picking needs an administrator (the plugin's channel list), so the TV only says how many and where
        item(key = "kid_${id}_live_tv") {
            val count = KidProfileMapper.liveTvChannelIds(profile).size
            ClickPreference(
                title = stringResource(R.string.kids_live_tv_channels),
                summary =
                    if (count == 0) {
                        stringResource(R.string.kids_live_tv_none)
                    } else {
                        pluralStringResource(R.plurals.kids_live_tv_count, count, count)
                    },
                onClick = {},
            )
        }
    }
    KidProfileSettings.preferences(profile).forEach { pref ->
        pref as AppPreference<KidProfile, Any>
        item(key = "kid_${id}_${pref.title}") {
            ComposablePreference(
                preference = pref,
                value = pref.getter.invoke(profile),
                onValueChange = { value -> viewModel.update(id) { pref.setter.invoke(it, value) } },
                onNavigate = viewModel.navigationManager::navigateTo,
            )
        }
    }

    item(key = "kid_${id}_picked_title") { GroupTitle(stringResource(R.string.kids_picked)) }
    items(profile.pickedItemIds, key = { "kid_${id}_picked_$it" }) { itemId ->
        val itemName = pickedNames[itemId].orEmpty().ifBlank { itemId.toString() }
        ClickPreference(
            title = itemName,
            summary = stringResource(R.string.kids_picked_remove, itemName),
            onClick = { viewModel.removePick(profile, itemId) },
        )
    }
    item(key = "kid_${id}_add_collection") {
        ClickPreference(title = stringResource(R.string.kids_picked_add_collection), onClick = { onPick(BaseItemKind.BOX_SET) })
    }
    item(key = "kid_${id}_add_playlist") {
        ClickPreference(title = stringResource(R.string.kids_picked_add_playlist), onClick = { onPick(BaseItemKind.PLAYLIST) })
    }

    item(key = "kid_${id}_sections_title") { GroupTitle(stringResource(R.string.kids_sections)) }
    itemsIndexed(profile.sections, key = { _, entry -> "kid_${id}_section_${entry.section}" }) { index, entry ->
        NavDrawerPreferenceListItem(
            title = sectionName(entry.section),
            pinned = entry.visible,
            moveUpAllowed = index > 0,
            moveDownAllowed = index < profile.sections.lastIndex,
            onClick = { viewModel.toggleSection(profile, index) },
            onMoveUp = { viewModel.moveSection(profile, index, -1) },
            onMoveDown = { viewModel.moveSection(profile, index, 1) },
            modifier = Modifier.animateItem(),
        )
    }
}

/**
 * A choice of minutes, where null is [none]
 */
@Composable
private fun MinutesChoice(
    title: String,
    subtitle: String,
    none: String,
    options: List<Int?>,
    value: Int?,
    onValueChange: (Int?) -> Unit,
) {
    @Composable
    fun label(minutes: Int?) = minutes?.let { pluralStringResource(R.plurals.kids_minutes_option, it, it) } ?: none
    ChoicePreference(
        title = title,
        summary = label(value),
        possibleValues = options,
        selectedIndex = options.indexOf(value),
        onValueChange = { onValueChange(options[it]) },
        valueDisplay = { _, minutes -> Text(label(minutes)) },
        subtitleDisplay = { index, _ -> if (index == 0) ({ Text(subtitle) }) else null },
    )
}

@Composable
private fun sectionName(section: KidsHomeSection): String =
    stringResource(
        when (section) {
            KidsHomeSection.KEEP_WATCHING -> R.string.kids_section_keep_watching
            KidsHomeSection.PICKED -> R.string.kids_picked
            KidsHomeSection.LIBRARIES -> R.string.kids_section_libraries
            KidsHomeSection.NEW -> R.string.kids_section_new
            KidsHomeSection.FAVOURITES -> R.string.favorites
        },
    )

/**
 * What the server restricts for the current kid, read only: it is changed in the Jellyfin dashboard
 */
private fun LazyListScope.serverRestrictions(policy: PolicySummary?) {
    groupTitle(R.string.kids_server_restrictions)
    if (policy != null) {
        item(key = "restrictions") {
            Column {
                if (policy.unrestricted) {
                    ClickPreference(
                        title = stringResource(R.string.kids_restrictions_none),
                        summary = stringResource(R.string.kids_restrictions_warning),
                        onClick = {},
                    )
                }
                ClickPreference(
                    title =
                        policy.maxParentalRating?.let { stringResource(R.string.kids_restriction_max_rating, it) }
                            ?: stringResource(R.string.kids_restriction_no_max_rating),
                    onClick = {},
                )
                ClickPreference(
                    title =
                        if (policy.blocksUnrated) {
                            stringResource(R.string.kids_restriction_unrated_blocked)
                        } else {
                            stringResource(R.string.kids_restriction_unrated_allowed)
                        },
                    onClick = {},
                )
                ClickPreference(
                    title =
                        if (policy.allLibraries) {
                            stringResource(R.string.kids_restriction_all_libraries)
                        } else {
                            pluralStringResource(R.plurals.kids_restriction_some_libraries, policy.libraryCount, policy.libraryCount)
                        },
                    onClick = {},
                )
                ClickPreference(
                    title =
                        stringResource(
                            if (policy.hasSchedule) R.string.kids_restriction_schedule else R.string.kids_restriction_no_schedule,
                        ),
                    onClick = {},
                )
            }
        }
    }
    item(key = "restrictions_hint") {
        ClickPreference(
            title = stringResource(R.string.kids_restrictions_where),
            summary = stringResource(R.string.kids_restrictions_hint),
            onClick = {},
        )
    }
}
