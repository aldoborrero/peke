package dev.aldoborrero.peke.kids.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.gate.GrownUpSessions
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.PasscodeHasher
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.PluginSupport
import dev.aldoborrero.peke.kids.server.SetLockRequest
import dev.aldoborrero.peke.kids.ui.LocalGrownUpPage
import dev.aldoborrero.peke.kids.ui.ParentLockHint
import dev.aldoborrero.peke.kids.ui.parentLockSymbol
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import dev.aldoborrero.peke.ui.components.BasicDialog
import dev.aldoborrero.peke.ui.components.CircularProgress
import dev.aldoborrero.peke.ui.components.EditTextBox
import dev.aldoborrero.peke.ui.components.TextButton
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.preferences.SetPinFlow
import dev.aldoborrero.peke.ui.preferences.VoiceSearchConsent
import dev.aldoborrero.peke.ui.theme.LocalPekeFocusScale
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * What an address typed or found on the network led to
 */
sealed interface ServerLookup {
    /**
     * @param insecure the server answers only on plain HTTP: the grown-up confirms a warning before it is used (spec 005
     *   FR-014)
     */
    data class Found(
        val server: JellyfinServer,
        val insecure: Boolean = false,
    ) : ServerLookup

    data object NotFound : ServerLookup
}

/**
 * Finds the household's Jellyfin server for setup, from an address or on the local network, as Wholphin's server
 * list did
 */
class SetupServerLookup
    @Inject
    constructor(
        private val jellyfin: Jellyfin,
    ) {
        /**
         * The best address Jellyfin's discovery recommends for [address], if it answers as a set-up Jellyfin server
         */
        suspend fun find(address: String): ServerLookup =
            try {
                pick(jellyfin.discovery.getRecommendedServers(address)).also {
                    if (it == ServerLookup.NotFound) Timber.w("No usable server at the address typed")
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Server lookup failed: %s", ex.javaClass.simpleName)
                ServerLookup.NotFound
            }

        /**
         * Servers answering on the local network
         */
        fun discover(): Flow<JellyfinServer> =
            jellyfin.discovery.discoverLocalServers().map { JellyfinServer(it.id.toUUID(), it.name, it.address, null) }

        companion object {
            /**
             * The address to use among the answers for one typed address (spec 005 FR-014): any HTTPS answer before every
             * plain HTTP one, the SDK's best score inside a scheme, and only set-up Jellyfin servers. The SDK tries the
             * HTTPS variant of a bare host or an http:// address too, so a server that has HTTPS gets it.
             */
            fun pick(results: Collection<RecommendedServerInfo>): ServerLookup {
                val usable =
                    results
                        .filter { it.score != RecommendedServerInfoScore.BAD }
                        .mapNotNull { result ->
                            val info = result.systemInfo.getOrNull()
                            val id = info?.id?.toUUIDOrNull()
                            if (id != null && info.startupWizardCompleted == true) {
                                result to JellyfinServer(id = id, name = info.serverName, url = result.address, version = info.version)
                            } else {
                                null
                            }
                        }.sortedBy { it.first.score }
                val best = usable.firstOrNull { isHttps(it.second.url) } ?: usable.firstOrNull() ?: return ServerLookup.NotFound
                return ServerLookup.Found(best.second, insecure = !isHttps(best.second.url))
            }

            private fun isHttps(url: String) = url.startsWith("https://", ignoreCase = true)
        }
    }

enum class SetupError {
    SERVER_NOT_FOUND,
    PLUGIN_NEEDED,
    NOT_ADMIN,
    WRONG_SIGN_IN,
    QUICK_CONNECT_OFF,
    UNREACHABLE,
    CODE_INVALID,

    /**
     * The administrator's session ended on the server, for example after a restart: sign in again
     */
    ADMIN_EXPIRED,
    FAILED,
}

/**
 * A household kid in the kids step
 *
 * @param onTv already signed in on this TV, so not signed in again
 */
data class SetupKid(
    val id: UUID,
    val name: String?,
    val imageUrl: String?,
    val onTv: Boolean = false,
)

sealed interface KidRowState {
    data object Waiting : KidRowState

    data object Working : KidRowState

    /**
     * @param outsideHours signed in, but outside their allowed hours right now
     */
    data class Done(
        val outsideHours: Boolean = false,
    ) : KidRowState

    data class Problem(
        val result: KidSignInResult,
    ) : KidRowState
}

data class SetupKidRow(
    val kid: SetupKid,
    val state: KidRowState,
)

/**
 * The setup step on screen (`contracts/tv-flows.md`, setup steps)
 */
sealed interface SetupScreen {
    data object Starting : SetupScreen

    data class Server(
        val discovered: List<JellyfinServer> = emptyList(),
        val checking: Boolean = false,
        val error: SetupError? = null,
        // A server that answers only on plain HTTP, waiting for the grown-up to confirm the warning (spec 005 FR-014)
        val httpWarning: JellyfinServer? = null,
    ) : SetupScreen

    /**
     * @param quickConnectCode the code to approve, while signing in with Quick Connect
     */
    data class Admin(
        val server: JellyfinServer,
        val quickConnectCode: String? = null,
        val checking: Boolean = false,
        val error: SetupError? = null,
    ) : SetupScreen

    data class Code(
        val server: JellyfinServer,
        val saving: Boolean = false,
        val error: SetupError? = null,
    ) : SetupScreen

    /**
     * @param adding adding kids to a set-up TV: only kids not on it yet are listed
     */
    data class Kids(
        val server: JellyfinServer,
        val kids: List<SetupKid> = emptyList(),
        val selected: Set<UUID> = emptySet(),
        val loading: Boolean = false,
        val error: SetupError? = null,
        val adding: Boolean = false,
    ) : SetupScreen

    data class SignIn(
        val server: JellyfinServer,
        val rows: List<SetupKidRow>,
        val finishing: Boolean = false,
    ) : SetupScreen {
        val canFinish: Boolean get() =
            !finishing && rows.any { it.state is KidRowState.Done } &&
                rows.none { it.state == KidRowState.Working }
    }

    /**
     * After a first setup, when one of the kids may search: offer voice search to the grown-up (spec 005 FR-009). The
     * TV is already set up and the administrator signed out.
     */
    data class Voice(
        val server: JellyfinServer,
    ) : SetupScreen
}

/**
 * Sets a TV up once (spec 004 US1): server, administrator, household code if missing, kids, and signing the kids in.
 * The steps reached are stored in [TvSetupRepository]; the administrator session only lives here, in memory, and is
 * closed when setup finishes or is left (research R4).
 *
 * [again] signs one kid whose session stopped working in again, with the same administrator and sign-in steps, and
 * [addKids] adds kids to a set-up TV with the administrator, kids and sign-in steps (spec FR-014).
 */
@HiltViewModel
class SetupFlowViewModel
    @Inject
    constructor(
        private val tvSetupRepository: TvSetupRepository,
        private val serverRepository: ServerRepository,
        private val serverLookup: SetupServerLookup,
        private val pluginService: KidsPluginService,
        private val kidsApi: KidsApi,
        private val adminSessions: AdminSessions,
        private val kidSignIn: KidSignIn,
        private val launcher: TvLauncher,
        private val jellyfin: Jellyfin,
        private val parentLockService: ParentLockService,
        private val grownUpSessions: GrownUpSessions,
        private val voiceSearch: VoiceSearchSetting,
        @param:DefaultCoroutineScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val _screen = MutableStateFlow<SetupScreen>(SetupScreen.Starting)
        val screen: StateFlow<SetupScreen> = _screen

        /**
         * Moves on when the app went to the background, which ended the administrator session and any sign-in
         */
        val epoch: StateFlow<Int> = grownUpSessions.epoch

        private var admin: AdminSession? = null
        private var closing: Job? = null
        private var againKid: JellyfinUser? = null
        private var adding = false
        private var addingFromKid = false
        private var started = false
        private var discovery: Job? = null
        private var quickConnect: Job? = null

        init {
            // The app went to the background: the administrator session was ended, so the steps that need it start over
            // from a fresh sign-in, and a sign-in on its way is dropped when it answers
            viewModelScope.launch { grownUpSessions.epoch.drop(1).collect { backgrounded() } }
        }

        private fun backgrounded() {
            quickConnect?.cancel()
            quickConnect = null
            admin = null
            _screen.update { s ->
                when (s) {
                    is SetupScreen.Admin -> SetupScreen.Admin(s.server)
                    is SetupScreen.Code, is SetupScreen.Kids, is SetupScreen.SignIn -> SetupScreen.Admin(serverOf(s))
                    SetupScreen.Starting, is SetupScreen.Server, is SetupScreen.Voice -> s
                }
            }
        }

        /**
         * Start setup at [step]. Past the server step the administrator signs in first, since that session isn't stored.
         */
        fun start(step: TvSetupStep): Job =
            viewModelScope.launch {
                if (started) return@launch
                started = true
                val server = if (step > TvSetupStep.SERVER) tvSetupRepository.server() else null
                _screen.value = if (server == null) SetupScreen.Server() else SetupScreen.Admin(server)
            }

        /**
         * Sign [kid] in again on [server], after the parent gate (spec FR-019)
         */
        fun again(
            server: JellyfinServer,
            kid: JellyfinUser,
        ): Job =
            viewModelScope.launch {
                if (started) return@launch
                started = true
                againKid = kid
                _screen.value = SetupScreen.Admin(server)
            }

        /**
         * Add kids to this set-up TV with [server]'s administrator, after the parent gate: the administrator signs in,
         * then the household's kids not on this TV yet are listed and the picked ones signed in
         *
         * @param fromKid opened from a kid's parent menu, so the steps return to that kid
         */
        fun addKids(
            server: JellyfinServer,
            fromKid: Boolean = false,
        ): Job =
            viewModelScope.launch {
                if (started) return@launch
                started = true
                adding = true
                addingFromKid = fromKid
                _screen.value = SetupScreen.Admin(server)
            }

        /**
         * Look for servers on the local network, for the server step
         */
        fun discover() {
            if (discovery?.isActive == true) return
            discovery =
                viewModelScope.launch {
                    try {
                        serverLookup.discover().collect { found ->
                            _screen.update { s ->
                                if (s is SetupScreen.Server) {
                                    s.copy(discovered = (s.discovered + found).distinctBy { it.url.lowercase().trim() })
                                } else {
                                    s
                                }
                            }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w("Server discovery failed: %s", ex.javaClass.simpleName)
                    }
                }
        }

        /**
         * The server step: find the server at [address] and check that it has the Kids plugin 0.0.7.0 or later
         */
        fun addServer(address: String): Job =
            viewModelScope.launch {
                val current = _screen.value as? SetupScreen.Server ?: return@launch
                if (current.checking) return@launch
                _screen.value = current.copy(checking = true, error = null)
                when (val found = serverLookup.find(address)) {
                    is ServerLookup.Found -> {
                        if (found.insecure) {
                            // Plain HTTP: the grown-up reads the warning first (spec 005 FR-014)
                            _screen.value = current.copy(httpWarning = found.server)
                        } else {
                            useServer(current, found.server)
                        }
                    }

                    ServerLookup.NotFound -> {
                        _screen.value = current.copy(error = SetupError.SERVER_NOT_FOUND)
                    }
                }
            }

        /**
         * The grown-up read the HTTP warning and uses the plain HTTP server anyway
         */
        fun confirmHttp(): Job =
            viewModelScope.launch {
                val current = _screen.value as? SetupScreen.Server ?: return@launch
                val server = current.httpWarning ?: return@launch
                if (current.checking) return@launch
                useServer(current.copy(httpWarning = null), server)
            }

        /**
         * Back from the HTTP warning: to the address, nothing stored
         */
        fun dismissHttp() {
            _screen.update { s -> if (s is SetupScreen.Server) s.copy(httpWarning = null, checking = false) else s }
        }

        /**
         * Check that [server] has the Kids plugin 0.0.7.0 or later, then store it and go on to the administrator
         */
        private suspend fun useServer(
            current: SetupScreen.Server,
            server: JellyfinServer,
        ) {
            _screen.value = current.copy(checking = true, error = null)
            when (kidsApi.pluginSupport(server.url)) {
                PluginSupport.SUPPORTED -> {}

                PluginSupport.NEEDED -> {
                    _screen.value = current.copy(error = SetupError.PLUGIN_NEEDED)
                    return
                }

                PluginSupport.UNREACHABLE -> {
                    _screen.value = current.copy(error = SetupError.UNREACHABLE)
                    return
                }
            }
            // The setup row first: a stored server without it would count as a set-up TV (FR-018)
            tvSetupRepository.markStep(server, TvSetupStep.ADMIN)
            serverRepository.addAndChangeServer(server)
            pluginService.check(server)
            discovery?.cancel()
            _screen.value = SetupScreen.Admin(server)
        }

        /**
         * The administrator step, with a user name and password. They are only passed on.
         */
        fun signIn(
            username: String,
            password: String,
        ): Job =
            viewModelScope.launch {
                val current = _screen.value as? SetupScreen.Admin ?: return@launch
                if (current.checking) return@launch
                _screen.value = current.copy(checking = true, error = null, quickConnectCode = null)
                val epoch = grownUpSessions.epoch.value
                opened(current.server, adminSessions.open(current.server, username, password), epoch)
            }

        /**
         * The administrator step with Quick Connect: a code to approve in another Jellyfin app
         */
        fun signInWithQuickConnect(): Job {
            quickConnect?.cancel()
            return viewModelScope
                .launch {
                    val current = _screen.value as? SetupScreen.Admin ?: return@launch
                    _screen.value = current.copy(checking = true, error = null, quickConnectCode = null)
                    val epoch = grownUpSessions.epoch.value
                    val result =
                        adminSessions.openWithQuickConnect(current.server) { code ->
                            _screen.update { s -> if (s is SetupScreen.Admin) s.copy(quickConnectCode = code) else s }
                        }
                    // Signed in: leaving the administrator step must no longer cancel the steps that follow
                    quickConnect = null
                    opened(current.server, result, epoch)
                }.also { quickConnect = it }
        }

        fun cancelQuickConnect() {
            quickConnect?.cancel()
            quickConnect = null
            _screen.update { s -> if (s is SetupScreen.Admin) s.copy(checking = false, quickConnectCode = null) else s }
        }

        /**
         * @param epoch [GrownUpSessions.epoch] when the sign-in started
         */
        private suspend fun opened(
            server: JellyfinServer,
            result: AdminSignIn,
            epoch: Int,
        ) {
            val session = (result as? AdminSignIn.Opened)?.session
            // The grown-up left the step, or the app went to the background, while it was signing in: the session isn't
            // used, so it ends right away
            if (grownUpSessions.epoch.value != epoch || _screen.value !is SetupScreen.Admin) {
                session?.close()
                return
            }
            if (session == null) {
                _screen.value = SetupScreen.Admin(server, error = adminError(result))
                return
            }
            admin?.close()
            admin = session
            againKid?.let { kid ->
                signInRows(server, listOf(SetupKid(kid.id, kid.name, imageUrl(server, kid.id))))
                return
            }
            if (adding) {
                loadKids(server)
                return
            }
            when (val lock = kidsApi.lock(server.url, session.caller)) {
                is KidsResult.Ok -> {
                    if (ParentLockService.parseType(lock.value.lockType) == null) {
                        tvSetupRepository.markStep(server, TvSetupStep.CODE)
                        _screen.value = SetupScreen.Code(server)
                    } else {
                        tvSetupRepository.markStep(server, TvSetupStep.KIDS)
                        loadKids(server)
                    }
                }

                else -> {
                    closeAdmin()
                    _screen.value =
                        SetupScreen.Admin(server, error = if (lock == KidsResult.Unreachable) SetupError.UNREACHABLE else SetupError.FAILED)
                }
            }
        }

        /**
         * The code step: set the household code, entered twice on screen
         */
        fun setCode(
            type: ParentLockType,
            code: String,
        ): Job =
            viewModelScope.launch {
                val current = _screen.value as? SetupScreen.Code ?: return@launch
                val session = admin ?: return@launch
                if (!PasscodeHasher.isValid(code, type)) {
                    _screen.value = current.copy(error = SetupError.CODE_INVALID)
                    return@launch
                }
                _screen.value = current.copy(saving = true, error = null)
                val result = kidsApi.setLock(current.server.url, session.caller, null, SetLockRequest(type.name, code))
                when (result) {
                    is KidsResult.Ok -> {
                        tvSetupRepository.markStep(current.server, TvSetupStep.KIDS)
                        loadKids(current.server)
                    }

                    is KidsResult.Invalid -> {
                        _screen.value = current.copy(error = SetupError.CODE_INVALID)
                    }

                    KidsResult.Unreachable -> {
                        _screen.value = current.copy(error = SetupError.UNREACHABLE)
                    }

                    else -> {
                        Timber.w("Could not set the household code: %s", result)
                        _screen.value = current.copy(error = SetupError.FAILED)
                    }
                }
            }

        /**
         * Read the household's kids again, after marking some in the dashboard
         */
        fun checkAgain(): Job =
            viewModelScope.launch {
                val current = _screen.value as? SetupScreen.Kids ?: return@launch
                loadKids(current.server, current.selected)
            }

        private suspend fun loadKids(
            server: JellyfinServer,
            selected: Set<UUID> = emptySet(),
        ) {
            val session = admin ?: return
            val previous = (_screen.value as? SetupScreen.Kids)?.kids.orEmpty()
            _screen.value = SetupScreen.Kids(server, previous, selected, loading = true, adding = adding)
            when (val result = kidsApi.householdKids(server.url, session.caller)) {
                is KidsResult.Ok -> {
                    // This TV's kids, signed in with a token: a kid unmarked and marked again in the dashboard, or one
                    // whose session stopped working, can be signed in again
                    val onTv =
                        (tvSetupRepository.state.value as? TvState.SetUp)
                            ?.takeIf { it.server.id == server.id }
                            ?.kids
                            .orEmpty()
                            .filter { it.accessToken != null }
                            .map { it.id }
                            .toSet()
                    val kids =
                        result.value
                            .map { SetupKid(it.userId, it.name, imageUrl(server, it.userId), it.userId in onTv) }
                            // Adding to a set-up TV lists only the kids not on it yet
                            .filter { !adding || !it.onTv }
                    val ids = kids.map { it.id }.toSet()
                    _screen.value = SetupScreen.Kids(server, kids, (selected + onTv).intersect(ids), adding = adding)
                }

                else -> {
                    val error = if (result == KidsResult.Unreachable) SetupError.UNREACHABLE else SetupError.FAILED
                    _screen.value = SetupScreen.Kids(server, previous, selected, error = error, adding = adding)
                }
            }
        }

        fun toggle(kidId: UUID) {
            _screen.update { s ->
                if (s !is SetupScreen.Kids || s.kids.firstOrNull { it.id == kidId }?.onTv != false) return@update s
                s.copy(selected = if (kidId in s.selected) s.selected - kidId else s.selected + kidId)
            }
        }

        /**
         * The kids step is done: sign the picked kids in. Setup can't go on without one.
         */
        fun confirmKids(): Job =
            viewModelScope.launch {
                val current = _screen.value as? SetupScreen.Kids ?: return@launch
                val picked = current.kids.filter { it.id in current.selected }
                if (picked.isEmpty() || current.loading) return@launch
                // Adding to a set-up TV leaves its setup done
                if (!adding) tvSetupRepository.markStep(current.server, TvSetupStep.SIGN_IN)
                signInRows(current.server, picked)
            }

        private suspend fun signInRows(
            server: JellyfinServer,
            kids: List<SetupKid>,
        ) {
            _screen.value =
                SetupScreen.SignIn(server, kids.map { SetupKidRow(it, if (it.onTv) KidRowState.Done() else KidRowState.Waiting) })
            kids.filter { !it.onTv }.forEach { signInKid(it.id) }
        }

        private suspend fun signInKid(kidId: UUID) {
            val session = admin ?: return
            setRow(kidId, KidRowState.Working)
            val result = kidSignIn.withQuickConnect(session, kidId)
            if (result == KidSignInResult.AdminExpired) {
                // The administrator's session no longer works: nothing else can be signed in with it
                setRow(kidId, KidRowState.Waiting)
                val server = serverOf(_screen.value)
                closeAdmin()
                _screen.value = SetupScreen.Admin(server, error = SetupError.ADMIN_EXPIRED)
                return
            }
            setRow(kidId, rowState(result))
        }

        /**
         * Try a kid again, for example after turning Quick Connect on or allowing this TV in the dashboard
         */
        fun retryKid(kidId: UUID): Job = viewModelScope.launch { signInKid(kidId) }

        /**
         * Sign a kid in with their password, typed once, when Quick Connect is off. The password is only passed on.
         */
        fun signInWithPassword(
            kidId: UUID,
            password: String,
        ): Job =
            viewModelScope.launch {
                val current = _screen.value as? SetupScreen.SignIn ?: return@launch
                val row = current.rows.firstOrNull { it.kid.id == kidId } ?: return@launch
                setRow(kidId, KidRowState.Working)
                setRow(kidId, rowState(kidSignIn.withPassword(current.server, kidId, row.kid.name.orEmpty(), password)))
            }

        private fun rowState(result: KidSignInResult) =
            if (result is KidSignInResult.SignedIn) KidRowState.Done(result.outsideHours) else KidRowState.Problem(result)

        private fun setRow(
            kidId: UUID,
            state: KidRowState,
        ) {
            _screen.update { s ->
                if (s !is SetupScreen.SignIn) return@update s
                s.copy(rows = s.rows.map { if (it.kid.id == kidId) it.copy(state = state) else it })
            }
        }

        /**
         * Setup is done once at least one kid is signed in (FR-007): the administrator session ends and the kids side
         * opens
         */
        fun finish(): Job =
            viewModelScope.launch {
                val current = _screen.value as? SetupScreen.SignIn ?: return@launch
                if (!current.canFinish) return@launch
                _screen.value = current.copy(finishing = true)
                val setUp = againKid == null && !adding
                if (setUp) tvSetupRepository.markDone(current.server)
                closeAdmin()
                if (setUp && voiceSearch.offerAfterSetup()) {
                    _screen.value = SetupScreen.Voice(current.server)
                    return@launch
                }
                if (setUp) launcher.launch { it is TvState.SetUp && it.kids.isNotEmpty() } else leave()
            }

        /**
         * The grown-up allowed the microphone on the voice search step: turn voice search on, then open the kids' side
         */
        fun turnOnVoice(): Job =
            viewModelScope.launch {
                if (_screen.value !is SetupScreen.Voice) return@launch
                voiceSearch.set(true)
                voiceDone().join()
            }

        /**
         * The voice search offer is done, turned on or not: the kids' side opens
         */
        fun voiceDone(): Job =
            viewModelScope.launch {
                if (_screen.value !is SetupScreen.Voice) return@launch
                launcher.launch { it is TvState.SetUp && it.kids.isNotEmpty() }
            }

        /**
         * Back goes to the previous step, closing the administrator session when leaving the steps that need it
         *
         * @return false on the first step, where Back quits
         */
        fun back(): Boolean =
            when (val s = _screen.value) {
                SetupScreen.Starting -> {
                    false
                }

                is SetupScreen.Server -> {
                    // Back on the HTTP warning returns to the address; on the step itself it quits
                    if (s.httpWarning != null) dismissHttp()
                    s.httpWarning != null
                }

                is SetupScreen.Admin -> {
                    // A sign-in on its way is waited for: its answer would come to a step that is gone. Quick Connect
                    // waits for another device, so Back stops it.
                    if (s.checking && quickConnect == null) return true
                    cancelQuickConnect()
                    if (againKid != null || adding) {
                        appScope.launch { leave() }
                    } else {
                        _screen.value = SetupScreen.Server()
                    }
                    true
                }

                is SetupScreen.Code, is SetupScreen.Kids -> {
                    closeAdminLater()
                    _screen.value = SetupScreen.Admin(serverOf(s))
                    true
                }

                is SetupScreen.Voice -> {
                    voiceDone()
                    true
                }

                is SetupScreen.SignIn -> {
                    if (s.rows.any { it.state == KidRowState.Working } || s.finishing) {
                        true
                    } else if (againKid != null) {
                        closeAdminLater()
                        _screen.value = SetupScreen.Admin(s.server)
                        true
                    } else {
                        val selected = s.rows.map { it.kid.id }.toSet()
                        _screen.value = SetupScreen.Kids(s.server, s.rows.map { it.kid }, selected, loading = true, adding = adding)
                        viewModelScope.launch { loadKids(s.server, selected) }
                        true
                    }
                }
            }

        /**
         * Out of the steps that sign kids in on a set-up TV: back to the kid whose menu opened them, or else the first
         * screen the launch rule picks
         */
        private suspend fun leave() {
            // The grant of the gate passed to get here ends with the steps
            parentLockService.forgetGrants()
            if (adding && addingFromKid) launcher.resume() else launcher.launch()
        }

        private fun serverOf(screen: SetupScreen): JellyfinServer =
            when (screen) {
                is SetupScreen.Admin -> screen.server
                is SetupScreen.Code -> screen.server
                is SetupScreen.Kids -> screen.server
                is SetupScreen.SignIn -> screen.server
                is SetupScreen.Voice -> screen.server
                SetupScreen.Starting, is SetupScreen.Server -> error("No server yet")
            }

        /**
         * Setup is left: the administrator session ends. Steps reached so far, and kids signed in, stay.
         */
        fun abandon(): Job =
            appScope.launch {
                closing?.join()
                closeAdmin()
            }

        private suspend fun closeAdmin() {
            val session = admin ?: return
            admin = null
            session.close()
        }

        private fun closeAdminLater() {
            val session = admin ?: return
            admin = null
            closing = appScope.launch { session.close() }
        }

        private fun imageUrl(
            server: JellyfinServer,
            userId: UUID,
        ): String = jellyfin.createApi(baseUrl = server.url).imageApi.getUserImageUrl(userId)

        override fun onCleared() {
            abandon()
        }

        companion object {
            private fun adminError(result: AdminSignIn): SetupError =
                when (result) {
                    AdminSignIn.NotAdmin -> SetupError.NOT_ADMIN
                    AdminSignIn.Wrong -> SetupError.WRONG_SIGN_IN
                    AdminSignIn.QuickConnectOff -> SetupError.QUICK_CONNECT_OFF
                    AdminSignIn.Unreachable -> SetupError.UNREACHABLE
                    is AdminSignIn.Failed, is AdminSignIn.Opened -> SetupError.FAILED
                }
        }
    }

/**
 * The setup flow, from [step]; with [againKid] only that kid is signed in again on [againServer], and with [addTo] kids
 * are added to that set-up server
 */
@Composable
fun SetupFlow(
    step: TvSetupStep,
    modifier: Modifier = Modifier,
    againServer: JellyfinServer? = null,
    againKid: JellyfinUser? = null,
    addTo: JellyfinServer? = null,
    addFromKid: Boolean = false,
    viewModel: SetupFlowViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        when {
            againServer != null && againKid != null -> viewModel.again(againServer, againKid)
            addTo != null -> viewModel.addKids(addTo, addFromKid)
            else -> viewModel.start(step)
        }
    }
    val screen by viewModel.screen.collectAsState()
    BackHandler(enabled = screen.let { it !is SetupScreen.Starting && (it !is SetupScreen.Server || it.httpWarning != null) }) {
        viewModel.back()
    }
    // A grown-up's screens: the focus border and ring without the kids' scale-up, and errors with their details
    CompositionLocalProvider(LocalPekeFocusScale provides false, LocalGrownUpPage provides true) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth(.6f)
                        .padding(vertical = 32.dp),
            ) {
                // The buddy and the wordmark head every step (design/DESIGN.md: the grown-up setup header)
                Image(
                    painter = painterResource(R.drawable.logo_peke),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.height(72.dp),
                )
                when (val s = screen) {
                    SetupScreen.Starting -> CircularProgress(Modifier.size(48.dp))
                    is SetupScreen.Server -> ServerStep(s, viewModel)
                    is SetupScreen.Admin -> AdminStep(s, viewModel)
                    is SetupScreen.Code -> CodeStep(s, viewModel)
                    is SetupScreen.Kids -> KidsStep(s, viewModel)
                    is SetupScreen.SignIn -> SignInStep(s, viewModel)
                    is SetupScreen.Voice -> VoiceStep(viewModel)
                }
            }
        }
    }
}

@Composable
private fun StepTitle(
    @StringRes title: Int,
    @StringRes hint: Int? = null,
) {
    Text(
        text = stringResource(title),
        // DESIGN's Title
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    hint?.let {
        Text(
            text = stringResource(it),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StepError(error: SetupError?) {
    error ?: return
    Text(
        text = stringResource(setupErrorMessage(error)),
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
}

@StringRes
private fun setupErrorMessage(error: SetupError): Int =
    when (error) {
        SetupError.SERVER_NOT_FOUND -> R.string.peke_setup_server_not_found
        SetupError.PLUGIN_NEEDED -> R.string.peke_setup_plugin_needed
        SetupError.NOT_ADMIN -> R.string.peke_setup_not_admin
        SetupError.WRONG_SIGN_IN -> R.string.kids_admin_wrong
        SetupError.QUICK_CONNECT_OFF -> R.string.peke_setup_quick_connect_off
        SetupError.UNREACHABLE -> R.string.kids_cant_reach_server
        SetupError.CODE_INVALID -> R.string.peke_setup_code_invalid
        SetupError.ADMIN_EXPIRED -> R.string.peke_setup_admin_expired
        SetupError.FAILED -> R.string.kids_admin_failed
    }

private fun hasLocalNetworkPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/**
 * Servers found on the network, or an address typed in, as Wholphin's add-server dialog
 */
@Composable
private fun ColumnScope.ServerStep(
    screen: SetupScreen.Server,
    viewModel: SetupFlowViewModel,
) {
    val context = LocalContext.current
    var enterAddress by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.discover() else enterAddress = true
        }
    LaunchedEffect(Unit) {
        if (hasLocalNetworkPermission(context)) {
            viewModel.discover()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
    StepTitle(R.string.peke_setup_server_title, R.string.peke_setup_server_hint)
    StepError(screen.error)
    if (screen.checking) {
        CircularProgress(Modifier.size(32.dp))
    }
    if (!enterAddress) {
        val firstFocus = remember { FocusRequester() }
        val addressFocus = remember { FocusRequester() }
        LaunchedEffect(screen.discovered.isNotEmpty()) {
            if (screen.discovered.isNotEmpty()) firstFocus.tryRequestFocus() else addressFocus.tryRequestFocus()
        }
        Text(
            text = stringResource(if (screen.discovered.isEmpty()) R.string.searching else R.string.discovered_servers),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
        ) {
            itemsIndexed(screen.discovered, key = { _, server -> server.url }) { index, server ->
                ListItem(
                    selected = false,
                    enabled = !screen.checking,
                    headlineContent = { Text(server.name?.ifBlank { null } ?: server.url) },
                    supportingContent = { Text(server.url) },
                    onClick = { viewModel.addServer(server.url) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        TextButton(
            stringRes = R.string.enter_server_address,
            onClick = { enterAddress = true },
            modifier = Modifier.focusRequester(addressFocus),
        )
    } else {
        var url by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
        Text(stringResource(R.string.enter_server_url))
        EditTextBox(
            value = url,
            onValueChange = { url = it },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) viewModel.addServer(url) }),
            modifier =
                Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
        )
        TextButton(
            stringRes = R.string.submit,
            onClick = { viewModel.addServer(url) },
            enabled = url.isNotNullOrBlank() && !screen.checking,
        )
    }
    screen.httpWarning?.let { server ->
        HttpWarningDialog(
            server = server,
            onConfirm = { viewModel.confirmHttp() },
            onDismissRequest = viewModel::dismissHttp,
        )
    }
}

/**
 * Before a plain HTTP server is used (spec 005 FR-014): what that means, in plain words. Focus starts on Back.
 */
@Composable
private fun HttpWarningDialog(
    server: JellyfinServer,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.tryRequestFocus() }
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(.5f),
        ) {
            Text(
                text = stringResource(R.string.peke_http_warning_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = server.url,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.peke_http_warning),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(stringRes = R.string.peke_http_warning_use, onClick = onConfirm)
                TextButton(
                    stringRes = R.string.peke_http_warning_back,
                    onClick = onDismissRequest,
                    modifier = Modifier.focusRequester(backFocus),
                )
            }
        }
    }
}

/**
 * The administrator's user name and password, or a Quick Connect code to approve in another Jellyfin app
 */
@Composable
private fun ColumnScope.AdminStep(
    screen: SetupScreen.Admin,
    viewModel: SetupFlowViewModel,
) {
    var useQuickConnect by remember { mutableStateOf(false) }
    StepTitle(R.string.peke_setup_admin_title, R.string.peke_setup_admin_hint)
    StepError(screen.error)
    if (useQuickConnect) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
        // A new code, and again when the app comes back from the background, which ended the last one
        val epoch by viewModel.epoch.collectAsState()
        LaunchedEffect(epoch) { viewModel.signInWithQuickConnect() }
        DisposableEffect(Unit) { onDispose { viewModel.cancelQuickConnect() } }
        val code = screen.quickConnectCode
        if (code == null) {
            if (screen.checking) CircularProgress(Modifier.size(32.dp))
        } else {
            Text(
                text = stringResource(R.string.peke_setup_quick_connect_hint),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = code,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(
            stringRes = R.string.peke_setup_use_password,
            onClick = {
                viewModel.cancelQuickConnect()
                useQuickConnect = false
            },
            modifier = Modifier.focusRequester(focusRequester),
        )
    } else {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        val usernameFocus = remember { FocusRequester() }
        val passwordFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { usernameFocus.tryRequestFocus() }
        // A refused attempt is typed again
        LaunchedEffect(screen.error) { if (screen.error != null) password = "" }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.focusGroup(),
        ) {
            CredentialsForm(
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                onSubmit = { if (username.isNotBlank() && !screen.checking) viewModel.signIn(username, password) },
                submitEnabled = username.isNotNullOrBlank() && !screen.checking,
                usernameFocusRequester = usernameFocus,
                passwordFocusRequester = passwordFocus,
            )
        }
        if (screen.checking) CircularProgress(Modifier.size(32.dp))
        TextButton(
            stringRes = R.string.peke_setup_quick_connect,
            onClick = { useQuickConnect = true },
            enabled = !screen.checking,
        )
    }
}

/**
 * The household code, of numbers or remote buttons, entered twice
 */
@Composable
private fun CodeStep(
    screen: SetupScreen.Code,
    viewModel: SetupFlowViewModel,
) {
    var type by remember { mutableStateOf<ParentLockType?>(null) }
    StepTitle(R.string.peke_setup_code_title, R.string.peke_setup_code_hint)
    StepError(screen.error)
    if (screen.saving) CircularProgress(Modifier.size(32.dp))
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(modifier = Modifier.fillMaxWidth(.8f)) {
        ParentLockType.entries.forEachIndexed { index, option ->
            ListItem(
                selected = false,
                enabled = !screen.saving,
                headlineContent = { Text(stringResource(lockTypeName(option))) },
                supportingContent = { Text(stringResource(lockTypeSummary(option))) },
                onClick = { type = option },
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
            )
        }
    }
    type?.let { chosen ->
        key(chosen) {
            SetPinFlow(
                currentPin = null,
                onAddPin = { code ->
                    type = null
                    viewModel.setCode(chosen, code)
                },
                onRemovePin = {},
                onDismissRequest = { type = null },
                isValid = { PasscodeHasher.isValid(it, chosen) },
                symbolFor = parentLockSymbol(chosen),
                hint = { ParentLockHint(chosen, it) },
            )
        }
    }
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
 * The household's kids, to pick the ones who use this TV
 */
@Composable
private fun KidsStep(
    screen: SetupScreen.Kids,
    viewModel: SetupFlowViewModel,
) {
    StepTitle(R.string.peke_setup_kids_title, if (screen.kids.isEmpty()) null else R.string.peke_setup_kids_hint)
    StepError(screen.error)
    if (screen.loading) CircularProgress(Modifier.size(32.dp))
    val focusRequester = remember { FocusRequester() }
    if (screen.kids.isEmpty()) {
        if (!screen.loading) {
            Text(
                text = stringResource(if (screen.adding) R.string.peke_setup_kids_all_on_tv else R.string.peke_setup_kids_none),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.peke_setup_kids_none_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
        TextButton(
            stringRes = R.string.peke_setup_check_again,
            onClick = { viewModel.checkAgain() },
            enabled = !screen.loading,
            modifier = Modifier.focusRequester(focusRequester),
        )
    } else {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            modifier = Modifier.focusRequester(focusRequester),
        ) {
            items(screen.kids, key = { it.id }) { kid ->
                KidAvatarCard(
                    id = kid.id,
                    name = kid.name,
                    imageUrl = kid.imageUrl,
                    selected = kid.id in screen.selected,
                    caption = if (kid.onTv) stringResource(R.string.peke_setup_on_tv) else null,
                    onClick = { viewModel.toggle(kid.id) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(
                stringRes = R.string.peke_setup_continue,
                onClick = { viewModel.confirmKids() },
                enabled = screen.selected.isNotEmpty() && !screen.loading,
            )
            TextButton(
                stringRes = R.string.peke_setup_check_again,
                onClick = { viewModel.checkAgain() },
                enabled = !screen.loading,
            )
        }
    }
}

/**
 * Each picked kid signing in, with what to do when it doesn't work
 */
@Composable
private fun SignInStep(
    screen: SetupScreen.SignIn,
    viewModel: SetupFlowViewModel,
) {
    var passwordFor by remember { mutableStateOf<SetupKid?>(null) }
    StepTitle(R.string.peke_setup_sign_in_title)
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(screen.canFinish) { if (screen.canFinish) doneFocus.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        screen.rows.forEach { row ->
            val name = row.kid.name ?: ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KidAvatar(id = row.kid.id, name = row.kid.name, imageUrl = row.kid.imageUrl, modifier = Modifier.size(56.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.width(560.dp),
                ) {
                    Text(text = name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    when (val state = row.state) {
                        KidRowState.Waiting -> {
                            Text(stringResource(R.string.peke_setup_kid_waiting))
                        }

                        KidRowState.Working -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgress(Modifier.size(20.dp))
                                Text(stringResource(R.string.peke_setup_kid_working))
                            }
                        }

                        is KidRowState.Done -> {
                            Text(stringResource(R.string.peke_setup_kid_done))
                            if (state.outsideHours) Text(stringResource(R.string.peke_setup_kid_outside_hours, name))
                        }

                        is KidRowState.Problem -> {
                            Text(text = kidProblem(state.result, name), color = MaterialTheme.colorScheme.error)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    stringRes =
                                        if (state.result ==
                                            KidSignInResult.QuickConnectOff
                                        ) {
                                            R.string.peke_setup_check_again
                                        } else {
                                            R.string.peke_setup_try_again
                                        },
                                    onClick = { viewModel.retryKid(row.kid.id) },
                                )
                                if (state.result == KidSignInResult.QuickConnectOff || state.result == KidSignInResult.WrongPassword) {
                                    TextButton(onClick = { passwordFor = row.kid }) {
                                        Text(stringResource(R.string.peke_setup_type_password, name))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    TextButton(
        stringRes = R.string.peke_setup_done,
        onClick = { viewModel.finish() },
        enabled = screen.canFinish,
        modifier = Modifier.focusRequester(doneFocus),
    )
    passwordFor?.let { kid ->
        KidPasswordDialog(
            kid = kid,
            onSubmit = { password ->
                passwordFor = null
                viewModel.signInWithPassword(kid.id, password)
            },
            onDismissRequest = { passwordFor = null },
        )
    }
}

/**
 * The last step of a first setup when a kid may search: voice search, turned on by the grown-up or left off (spec 005
 * FR-009). Focus starts on Not now, so OK without reading changes nothing.
 */
@Composable
private fun VoiceStep(viewModel: SetupFlowViewModel) {
    var asking by remember { mutableStateOf(false) }
    val notNowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { notNowFocus.tryRequestFocus() }
    StepTitle(R.string.peke_voice_search_explain_title, R.string.peke_voice_search_setup_hint)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(stringRes = R.string.peke_voice_search_turn_on, onClick = { asking = true })
        TextButton(
            stringRes = R.string.peke_voice_search_not_now,
            onClick = { viewModel.voiceDone() },
            modifier = Modifier.focusRequester(notNowFocus),
        )
    }
    if (asking) {
        VoiceSearchConsent(
            onEnabled = {
                asking = false
                viewModel.turnOnVoice()
            },
            onDismissRequest = { asking = false },
        )
    }
}

@Composable
private fun kidProblem(
    result: KidSignInResult,
    name: String,
): String =
    when (result) {
        KidSignInResult.DeviceNotAllowed -> stringResource(R.string.peke_setup_kid_device, name)
        KidSignInResult.TooManySessions -> stringResource(R.string.peke_setup_kid_sessions, name)
        KidSignInResult.CodeExpired -> stringResource(R.string.peke_setup_kid_code_expired)
        KidSignInResult.AccountRefused -> stringResource(R.string.peke_setup_kid_refused, name)
        KidSignInResult.QuickConnectOff -> stringResource(R.string.peke_setup_kid_quick_connect_off, name)
        KidSignInResult.UnknownUser -> stringResource(R.string.peke_setup_kid_unknown, name)
        KidSignInResult.WrongPassword -> stringResource(R.string.peke_setup_kid_wrong_password, name)
        KidSignInResult.Unreachable -> stringResource(R.string.kids_cant_reach_server)
        KidSignInResult.AdminExpired -> stringResource(R.string.peke_setup_admin_expired)
        is KidSignInResult.Failed, is KidSignInResult.SignedIn -> stringResource(R.string.peke_setup_kid_failed, name)
    }

/**
 * A kid's password, typed once. It lives only in this dialog's state and is passed to [onSubmit].
 */
@Composable
private fun KidPasswordDialog(
    kid: SetupKid,
    onSubmit: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .focusGroup()
                    .padding(16.dp)
                    .fillMaxWidth(.4f),
        ) {
            Text(
                text = stringResource(R.string.peke_setup_password_hint, kid.name.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            EditTextBox(
                value = password,
                onValueChange = { password = it },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                keyboardActions = KeyboardActions(onGo = { onSubmit(password) }),
                modifier =
                    Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth(),
            )
            TextButton(stringRes = R.string.login, onClick = { onSubmit(password) })
        }
    }
}
