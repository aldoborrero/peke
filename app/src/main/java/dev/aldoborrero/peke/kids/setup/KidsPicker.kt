package dev.aldoborrero.peke.kids.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.isStopped
import dev.aldoborrero.peke.kids.ui.KidsParentGate
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.ui.components.Button
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.imageApi
import java.time.Instant
import javax.inject.Inject

/**
 * What picking a kid on the picker does (spec FR-012a, FR-019)
 */
enum class PickAction {
    /**
     * Their home, or their stop screen when they are stopped
     */
    OPEN,

    /**
     * The parent gate first, then their home: another kid is stopped
     */
    GATE_THEN_OPEN,

    /**
     * The parent gate, then the administrator to sign them in again: their session doesn't work
     */
    GATE_THEN_SIGN_IN,
}

/**
 * Whether [kid]'s session can't be used without a grown-up: they have no token
 */
fun needsGrownUp(kid: JellyfinUser): Boolean = kid.accessToken == null

/**
 * The picker's rule: picking is free, except for a kid who needs a grown-up, and for a kid other than the last one
 * while the last one is stopped, so a kid whose time is up can't carry on as a sibling. Kids never type a code.
 */
fun pickAction(
    kid: JellyfinUser,
    lastKidRowId: Int?,
    lastKidStopped: Boolean,
): PickAction =
    when {
        needsGrownUp(kid) -> PickAction.GATE_THEN_SIGN_IN
        kid.rowId == lastKidRowId -> PickAction.OPEN
        lastKidStopped -> PickAction.GATE_THEN_OPEN
        else -> PickAction.OPEN
    }

data class PickerKid(
    val user: JellyfinUser,
    val imageUrl: String?,
    val needsGrownUp: Boolean,
)

/**
 * A pick waiting for the parent gate
 */
data class PickerGate(
    val kid: JellyfinUser,
    val action: PickAction,
)

data class PickerState(
    val server: JellyfinServer? = null,
    val kids: List<PickerKid> = emptyList(),
    val gate: PickerGate? = null,
)

/**
 * "Who's watching?": this TV's kids, from [TvSetupRepository] only, so it opens without the server
 */
@HiltViewModel
class KidsPickerViewModel internal constructor(
    private val tvSetupRepository: TvSetupRepository,
    private val allowanceService: KidsAllowanceService,
    private val launcher: TvLauncher,
    private val setupNavigationManager: SetupNavigationManager,
    private val jellyfin: Jellyfin,
    private val now: () -> Instant,
) : ViewModel() {
    @Inject
    constructor(
        tvSetupRepository: TvSetupRepository,
        allowanceService: KidsAllowanceService,
        launcher: TvLauncher,
        setupNavigationManager: SetupNavigationManager,
        jellyfin: Jellyfin,
    ) : this(tvSetupRepository, allowanceService, launcher, setupNavigationManager, jellyfin, Instant::now)

    private val gate = MutableStateFlow<PickerGate?>(null)

    val state: StateFlow<PickerState> =
        combine(tvSetupRepository.state, gate, ::pickerState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, pickerState(tvSetupRepository.state.value, null))

    private fun pickerState(
        tv: TvState,
        gate: PickerGate?,
    ): PickerState {
        val setUp = tv as? TvState.SetUp ?: return PickerState(gate = gate)
        val api = jellyfin.createApi(baseUrl = setUp.server.url)
        return PickerState(
            server = setUp.server,
            kids = setUp.kids.map { PickerKid(it, api.imageApi.getUserImageUrl(it.id), needsGrownUp(it)) },
            gate = gate,
        )
    }

    fun pick(kid: JellyfinUser): Job =
        viewModelScope.launch {
            val tv = tvSetupRepository.state.value as? TvState.SetUp ?: return@launch
            val last = tv.lastKidRowId
            val lastStopped = last != null && last != kid.rowId && isStopped(allowanceService.cachedStatus(last), now())
            when (val action = pickAction(kid, last, lastStopped)) {
                PickAction.OPEN -> launcher.openKid(tv.server, kid)
                else -> gate.value = PickerGate(kid, action)
            }
        }

    /**
     * The parent gate passed for the waiting pick
     */
    fun gatePassed(): Job =
        viewModelScope.launch {
            val pending = gate.value ?: return@launch
            gate.value = null
            val tv = tvSetupRepository.state.value as? TvState.SetUp ?: return@launch
            when (pending.action) {
                PickAction.OPEN, PickAction.GATE_THEN_OPEN -> {
                    launcher.openKid(tv.server, pending.kid)
                }

                PickAction.GATE_THEN_SIGN_IN -> {
                    setupNavigationManager.navigateTo(SetupDestination.SignInAgain(tv.server, pending.kid))
                }
            }
        }

    fun gateDismissed() {
        gate.value = null
    }
}

/**
 * The kids picker (spec FR-009): only this TV's kids, then Grown-ups. Focus starts on the first kid, never on
 * Grown-ups.
 */
@Composable
fun KidsPicker(
    modifier: Modifier = Modifier,
    viewModel: KidsPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var grownUps by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.kids.isNotEmpty()) { if (state.kids.isNotEmpty()) rowFocus.tryRequestFocus() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.peke_picker_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                modifier =
                    Modifier
                        .focusRestorer(firstFocus)
                        .focusRequester(rowFocus),
            ) {
                itemsIndexed(state.kids, key = { _, kid -> kid.user.rowId }) { index, kid ->
                    KidAvatarCard(
                        id = kid.user.id,
                        name = kid.user.name,
                        imageUrl = kid.imageUrl,
                        caption = if (kid.needsGrownUp) stringResource(R.string.peke_picker_needs_grown_up) else null,
                        badge = if (kid.needsGrownUp) Icons.Default.Lock else null,
                        onClick = { viewModel.pick(kid.user) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
            GrownUpsButton(onClick = { grownUps = true })
        }
    }
    state.gate?.let {
        KidsParentGate(onDismissRequest = viewModel::gateDismissed, withKid = false, onPassed = { viewModel.gatePassed() })
    }
    if (grownUps) {
        KidsParentGate(onDismissRequest = { grownUps = false }, withKid = false)
    }
}

/**
 * "This TV has no kids": only Grown-ups, which asks for the parent code. Nothing is focused until the remote moves.
 */
@Composable
fun NoKidsPage(modifier: Modifier = Modifier) {
    var grownUps by remember { mutableStateOf(false) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(.6f),
        ) {
            Text(
                text = stringResource(R.string.peke_no_kids_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.peke_no_kids_hint),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(8.dp))
            GrownUpsButton(onClick = { grownUps = true })
        }
    }
    if (grownUps) {
        KidsParentGate(onDismissRequest = { grownUps = false }, withKid = false)
    }
}

@Composable
private fun GrownUpsButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.kids_grown_ups))
            Spacer(Modifier.width(4.dp))
        }
    }
}
