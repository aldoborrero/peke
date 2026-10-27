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
import androidx.compose.ui.res.painterResource
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
import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KnownKidsStatus
import dev.aldoborrero.peke.kids.timer.isStopped
import dev.aldoborrero.peke.kids.ui.KidsParentGate
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.ui.components.Button
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.imageApi
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
 * Whether [kid]'s session can't be used without a grown-up: they have no token, because the server refused it
 * ([dev.aldoborrero.peke.data.ServerRepository.forgetToken])
 */
fun needsGrownUp(kid: JellyfinUser): Boolean = kid.accessToken == null

/**
 * Whether the last kid on this TV stops a sibling from being picked (spec FR-012a), failing closed: with no last kid,
 * as on a TV just set up, nobody is stopped; a last kid whose status is unknown or unreadable is. A status the server
 * sent just now is the server's word; a cached one is counted down by this TV's monotonic clock ([isStopped]), so a
 * wall clock moved forward doesn't end a stop.
 *
 * @param lastKid whether a last kid of this TV is known
 * @param status that kid's last known status, or null if none could be read
 */
fun lastKidStopped(
    lastKid: Boolean,
    status: KnownKidsStatus?,
): Boolean =
    when {
        !lastKid -> false
        status == null -> true
        status.fresh -> status.status.denial != null
        else -> isStopped(status.status, status.sinceFetch)
    }

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

/**
 * @param avatar the avatar a grown-up picked in the Kids plugin, shown before [imageUrl]
 */
data class PickerKid(
    val user: JellyfinUser,
    val imageUrl: String?,
    val needsGrownUp: Boolean,
    val avatar: PekeAvatar? = null,
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
 *
 * A grown-up's parent grant ends once a kid is chosen ([ParentLockService.forgetGrants]): the picker comes after the
 * gate when switching kids (spec FR-012, FR-012a), and the kid picked must not open grown-up pages with that grant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KidsPickerViewModel
    @Inject
    constructor(
        private val tvSetupRepository: TvSetupRepository,
        private val allowanceService: KidsAllowanceService,
        private val launcher: TvLauncher,
        private val setupNavigationManager: SetupNavigationManager,
        private val parentLockService: ParentLockService,
        private val jellyfin: Jellyfin,
        private val kidAvatars: KidAvatars,
    ) : ViewModel() {
        private val gate = MutableStateFlow<PickerGate?>(null)

        /**
         * Opened by Switch kid in the parent menu: while the grant is live, a grown-up is choosing, so no pick asks for
         * the code again
         */
        private val grownUpPicking = parentLockService.consumePickGrant()

        // The avatars as cached for this TV's server, so the picker opens without the server
        private val avatars =
            tvSetupRepository.state.flatMapLatest { tv ->
                (tv as? TvState.SetUp)?.let { kidAvatars.observe(it.server.id) } ?: flowOf(emptyMap())
            }

        val state: StateFlow<PickerState> =
            combine(tvSetupRepository.state, gate, avatars, ::pickerState)
                .stateIn(viewModelScope, SharingStarted.Eagerly, pickerState(tvSetupRepository.state.value, null, emptyMap()))

        private fun pickerState(
            tv: TvState,
            gate: PickerGate?,
            avatars: Map<Int, PekeAvatar>,
        ): PickerState {
            val setUp = tv as? TvState.SetUp ?: return PickerState(gate = gate)
            val api = jellyfin.createApi(baseUrl = setUp.server.url)
            return PickerState(
                server = setUp.server,
                kids = setUp.kids.map { PickerKid(it, api.imageApi.getUserImageUrl(it.id), needsGrownUp(it), avatars[it.rowId]) },
                gate = gate,
            )
        }

        fun pick(kid: JellyfinUser): Job =
            viewModelScope.launch {
                val tv = tvSetupRepository.state.value as? TvState.SetUp ?: return@launch
                val last = tv.lastKidRowId
                // A last kid no longer on this TV was removed by a grown-up, through the gate: there's nobody to carry on as
                val lastKid = tv.kids.firstOrNull { it.rowId == last }
                val lastStopped =
                    last != kid.rowId &&
                        lastKidStopped(lastKid != null, lastKid?.let { allowanceService.lastKnownStatus(tv.server, it) })
                val action = pickAction(kid, last, lastStopped)
                when {
                    action == PickAction.OPEN -> open(tv, kid)
                    grownUpPicking && parentLockService.hasLiveGrant() -> proceed(tv, kid, action)
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
                proceed(tv, pending.kid, pending.action)
            }

        private suspend fun proceed(
            tv: TvState.SetUp,
            kid: JellyfinUser,
            action: PickAction,
        ) {
            when (action) {
                PickAction.OPEN, PickAction.GATE_THEN_OPEN -> {
                    open(tv, kid)
                }

                PickAction.GATE_THEN_SIGN_IN -> {
                    // The grant lets the sign-in page show; it ends when the sign-in is done or left
                    setupNavigationManager.navigateTo(SetupDestination.SignInAgain(tv.server, kid))
                }
            }
        }

        /**
         * [kid] is chosen: the grant of a gate passed to get here ends first, then their home opens
         */
        private suspend fun open(
            tv: TvState.SetUp,
            kid: JellyfinUser,
        ) {
            parentLockService.forgetGrants()
            launcher.openKid(tv.server, kid)
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
                // DESIGN's Title
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyRow(
                // Room around each avatar for its focus ring and scale-up
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
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
                        avatar = kid.avatar,
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
                style = MaterialTheme.typography.headlineLarge,
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
            Icon(painterResource(PekeIcon.GROWN_UPS.outline), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.kids_grown_ups))
            Spacer(Modifier.width(4.dp))
        }
    }
}
