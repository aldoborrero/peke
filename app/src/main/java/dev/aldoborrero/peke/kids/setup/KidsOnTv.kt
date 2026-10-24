package dev.aldoborrero.peke.kids.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.services.hilt.IoDispatcher
import dev.aldoborrero.peke.services.hilt.forKid
import dev.aldoborrero.peke.ui.components.ConfirmDialog
import dev.aldoborrero.peke.ui.components.TextButton
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import javax.inject.Inject

/**
 * Ends a kid's session on the server, with the kid's own token and device id (research R2), so a sibling's session
 * on this TV stays
 */
class KidSignOut
    @Inject
    constructor(
        private val jellyfin: Jellyfin,
        private val deviceInfo: DeviceInfo,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        /**
         * Best effort: a session left behind, such as when the server can't be reached, only stays in the server's
         * device list until it expires or is removed there
         *
         * @return whether the server ended it
         */
        suspend fun signOut(
            server: JellyfinServer,
            kid: JellyfinUser,
        ): Boolean {
            val token = kid.accessToken ?: return true
            return withContext(dispatcher) {
                try {
                    jellyfin
                        .createApi(baseUrl = server.url, accessToken = token, deviceInfo = deviceInfo.forKid(kid.id))
                        .sessionApi
                        .reportSessionEnded()
                    true
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w("Could not end the session of %s: %s", kid.rowId, ex.javaClass.simpleName)
                    false
                }
            }
        }
    }

data class KidsOnTvState(
    val server: JellyfinServer? = null,
    val kids: List<PickerKid> = emptyList(),
    val removing: Boolean = false,
)

/**
 * The kids on this TV, from the parent menu (spec FR-014): removing one signs them out and forgets them on this TV
 * only, and adding goes through the setup's administrator, kids and sign-in steps ([SetupFlowViewModel.addKids])
 */
@HiltViewModel
class KidsOnTvViewModel
    @Inject
    constructor(
        tvSetupRepository: TvSetupRepository,
        private val serverRepository: ServerRepository,
        private val kidSignOut: KidSignOut,
        private val allowanceService: KidsAllowanceService,
        private val launcher: TvLauncher,
        private val setupNavigationManager: SetupNavigationManager,
        private val jellyfin: Jellyfin,
    ) : ViewModel() {
        private val removing = MutableStateFlow(false)

        val state: StateFlow<KidsOnTvState> =
            combine(tvSetupRepository.state, removing, ::kidsOnTvState)
                .stateIn(viewModelScope, SharingStarted.Eagerly, kidsOnTvState(tvSetupRepository.state.value, false))

        private fun kidsOnTvState(
            tv: TvState,
            removing: Boolean,
        ): KidsOnTvState {
            val setUp = tv as? TvState.SetUp ?: return KidsOnTvState(removing = removing)
            val api = jellyfin.createApi(baseUrl = setUp.server.url)
            return KidsOnTvState(
                server = setUp.server,
                kids = setUp.kids.map { PickerKid(it, api.imageApi.getUserImageUrl(it.id), needsGrownUp(it)) },
                removing = removing,
            )
        }

        /**
         * Sign [kid] out on the server, best effort, and forget them on this TV: their user row and their kids data.
         * They stay in the household and on other TVs. Then the launch rule picks the first screen, "This TV has no
         * kids" when they were the last one.
         */
        fun remove(kid: JellyfinUser): Job =
            viewModelScope.launch {
                val server = state.value.server ?: return@launch
                if (removing.value) return@launch
                removing.value = true
                try {
                    kidSignOut.signOut(server, kid)
                    serverRepository.removeUser(kid)
                    allowanceService.forget(listOf(kid.rowId))
                } finally {
                    removing.value = false
                }
                launcher.launch()
            }

        /**
         * Add kids with the administrator's sign-in
         *
         * @param fromKid opened from a kid's parent menu, so the steps return to that kid
         */
        fun add(fromKid: Boolean = false) {
            val server = state.value.server ?: return
            setupNavigationManager.navigateTo(SetupDestination.AddKids(server, fromKid))
        }

        /**
         * Back to the kid whose menu opened this page, or else to the picker
         */
        fun close(fromKid: Boolean): Job = viewModelScope.launch { if (fromKid) launcher.resume() else launcher.launch() }
    }

/**
 * The kids on this TV, with Add a kid. Picking a kid asks whether to remove them, with Cancel focused first.
 *
 * @param fromKid opened from a kid's parent menu, so leaving returns to that kid
 */
@Composable
fun KidsOnTvPage(
    fromKid: Boolean,
    modifier: Modifier = Modifier,
    viewModel: KidsOnTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirm by remember { mutableStateOf<JellyfinUser?>(null) }
    val rowFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    BackHandler { viewModel.close(fromKid) }
    LaunchedEffect(state.kids.isEmpty()) { if (state.kids.isEmpty()) addFocus.tryRequestFocus() else rowFocus.tryRequestFocus() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.peke_menu_kids_on_tv),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.peke_kids_on_tv_hint),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(.6f),
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
                        onClick = { if (!state.removing) confirm = kid.user },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(
                    stringRes = R.string.peke_kids_on_tv_add,
                    onClick = { viewModel.add(fromKid) },
                    enabled = !state.removing,
                    modifier = Modifier.focusRequester(addFocus),
                )
                TextButton(
                    stringRes = R.string.peke_setup_done,
                    onClick = { viewModel.close(fromKid) },
                )
            }
        }
    }
    confirm?.let { kid ->
        val name = kid.name ?: kid.id.toString()
        ConfirmDialog(
            title = stringResource(R.string.peke_kids_on_tv_remove, name),
            body = stringResource(R.string.peke_kids_on_tv_remove_hint, name),
            onCancel = { confirm = null },
            onConfirm = {
                confirm = null
                viewModel.remove(kid)
            },
        )
    }
}
