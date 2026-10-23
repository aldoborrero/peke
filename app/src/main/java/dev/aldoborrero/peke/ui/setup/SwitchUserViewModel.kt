package dev.aldoborrero.peke.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.PekeApplication
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.KidsLockSignIn
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.filterUsersForKidsLock
import dev.aldoborrero.peke.kids.kidsLockSignIn
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.util.LoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@HiltViewModel(assistedFactory = SwitchUserViewModel.Factory::class)
class SwitchUserViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        val jellyfin: Jellyfin,
        val serverRepository: ServerRepository,
        val serverDao: JellyfinServerDao,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val imageUrlService: ImageUrlService,
        private val kidsModeService: KidsModeService,
        @Assisted val server: JellyfinServer,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(server: JellyfinServer): SwitchUserViewModel
        }

        private val _state = MutableStateFlow(SwitchUserState())
        val state: StateFlow<SwitchUserState> = _state

        private var quickConnectJob: Job? = null
        private var usersJob: Job? = null

        fun clearSwitchUserState() {
            _state.update { it.copy(switchUserState = LoadingState.Pending) }
        }

        fun resetAttempts() {
            _state.update { it.copy(loginAttempts = 0) }
        }

        fun init() {
            // Opening the list keeps the most recent session: it changes when a user is picked or signs in, and is
            // cleared only when a user or server is removed, so leaving the app from here restores it next launch
            quickConnectJob?.cancel()
            usersJob?.cancel()
            _state.update { SwitchUserState() }
            allUsers = null
            // When the kids lock or who is a kid changes, for example right after a kid signed in and locked the
            // device, narrow the list already shown at once, without the network. Only a kid who must appear and
            // isn't in it yet needs the server.
            usersJob =
                viewModelScope.launchIO {
                    combine(kidsModeService.locked.filterNotNull(), kidsModeService.kidRowIds) { locked, kids -> locked to kids }
                        .distinctUntilChanged()
                        .collectLatest { (_, kids) ->
                            val loaded = allUsers
                            if (loaded == null || missesKid(loaded, kids)) loadUsers() else showUsers(loaded)
                        }
                }

            viewModelScope.launchIO {
                try {
                    val quickConnect by
                        jellyfin
                            .createApi(
                                server.url,
                                httpClientOptions =
                                    HttpClientOptions(
                                        requestTimeout = 6.seconds,
                                        connectTimeout = 6.seconds,
                                        socketTimeout = 6.seconds,
                                    ),
                            ).quickConnectApi
                            .getQuickConnectEnabled()
                    _state.update { it.copy(quickConnectEnabled = quickConnect) }
                } catch (_: CancellationException) {
                    // no-op, user may have canceled
                } catch (ex: Exception) {
                    Timber.w(ex, "Error checking quick connect for server ${server.url}")
                    _state.update { it.copy(quickConnectEnabled = false) }
                }
            }
        }

        /**
         * Every user of the server as last loaded, before the kids lock narrows them, null until loaded
         */
        @Volatile
        private var allUsers: List<JellyfinUserAndImage>? = null

        private suspend fun loadUsers() {
            try {
                val serverUsers = getUsers()
                val (serverVersion, supported) = checkServerVersion()
                allUsers = serverUsers
                val (users, locked) = usersToShow(serverUsers)
                _state.update {
                    it.copy(
                        loading = LoadingState.Success,
                        users = users,
                        kidsLocked = locked,
                        serverVersion = serverVersion,
                        serverVersionSupported = supported,
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error fetching users for server $server")
                // A failed reload keeps the list that is shown, narrowed to who may be picked now
                val loaded = allUsers
                if (loaded != null) {
                    showUsers(loaded)
                } else if (_state.value.loading !is LoadingState.Success) {
                    _state.update {
                        it.copy(
                            loading = LoadingState.Error(ex),
                        )
                    }
                }
            }
        }

        /**
         * Show [users] as far as the kids lock allows right now, without the network
         */
        private suspend fun showUsers(users: List<JellyfinUserAndImage>) {
            val (shown, locked) = usersToShow(users)
            _state.update { it.copy(users = shown, kidsLocked = locked) }
        }

        private suspend fun usersToShow(users: List<JellyfinUserAndImage>): Pair<List<JellyfinUserAndImage>, Boolean> {
            val locked = kidsModeService.isLockedNow()
            return filterUsersForKidsLock(users, { it.user }, kidsModeService.kidUserRowIds(), locked) to locked
        }

        /**
         * Whether a kid saved on this device for this server is missing from [users], so the list has to be loaded
         * again for their tile
         */
        private suspend fun missesKid(
            users: List<JellyfinUserAndImage>,
            kids: Set<Int>,
        ): Boolean {
            val shown = users.filter { it.known }.map { it.user.rowId }.toSet()
            val saved = withContext(PekeDispatchers.IO) { serverDao.getServer(server.id)?.users.orEmpty() }
            return saved.any { it.rowId in kids && it.rowId !in shown }
        }

        fun trySwitchUser(user: JellyfinUser): Deferred<SwitchUserResult> =
            viewModelScope.async(PekeDispatchers.IO) {
                // The list only shows kids while locked; this holds even if a stale tile is picked
                if (kidsModeService.isLockedNow() && kidsModeService.profileFor(user) == null) {
                    Timber.w("Refusing to switch to a user who isn't a kid while locked to kids mode")
                    return@async SwitchUserResult.Error(context.getString(R.string.kids_sign_in_locked), false)
                }
                try {
                    val current = serverRepository.changeUser(server, user)
                    kidsModeService.lockIfKid(current.user)
                    navigationManager.reloadHome()
                    setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                    SwitchUserResult.Success
                } catch (ex: InvalidStatusException) {
                    when (ex.status) {
                        401 -> {
                            Timber.w(ex, "Error switching user 401")
                            SwitchUserResult.Error(
                                context.getString(R.string.login_credentials_expired),
                                true,
                            )
                        }

                        403 -> {
                            Timber.w(ex, "Error switching user 403")
                            SwitchUserResult.Error(
                                context.getString(R.string.login_not_authorized),
                                false,
                            )
                        }

                        else -> {
                            Timber.e(ex, "Error switching user")
                            SwitchUserResult.Error(ex.localizedMessage, true)
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error switching user")
                    SwitchUserResult.Error(ex.localizedMessage, true)
                }
            }

        fun login(
            server: JellyfinServer,
            existingUser: JellyfinUser?,
            username: String,
            password: String,
        ) {
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                try {
                    val api = jellyfin.createApi(baseUrl = server.url)
                    val authenticationResult by api.userApi.authenticateUserByName(
                        username = username,
                        password = password,
                    )
                    val signIn = signInRow(server, existingUser, authenticationResult) ?: return@launchIO
                    val current =
                        serverRepository.changeUser(server.url, authenticationResult, signIn.row)
                    kidsModeService.lockIfKidAfterSignIn(current)
                    setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                } catch (ex: Exception) {
                    Timber.e(ex, "Error logging in user")
                    if (ex is InvalidStatusException && ex.status == 401) {
                        _state.update { it.copy(switchUserState = LoadingState.Error("Invalid username or password")) }
                    } else if (ex is InvalidStatusException && ex.status == 403) {
                        setSignInRefused()
                    } else {
                        setError("Error during login", ex)
                    }
                }
            }
        }

        fun initiateQuickConnect(
            server: JellyfinServer,
            existingUser: JellyfinUser?,
        ) {
            quickConnectJob?.cancel()
            quickConnectJob =
                viewModelScope.launchIO {
                    try {
                        val api = jellyfin.createApi(server.url)
                        var quickConnectStatus =
                            api
                                .quickConnectApi
                                .initiateQuickConnect()
                                .content
                        _state.update { it.copy(quickConnectStatus = quickConnectStatus) }

                        while (!quickConnectStatus.authenticated) {
                            delay(5_000L)
                            quickConnectStatus =
                                api.quickConnectApi
                                    .getQuickConnectState(
                                        secret = quickConnectStatus.secret,
                                    ).content
                            _state.update { it.copy(quickConnectStatus = quickConnectStatus) }
                        }
                        val authenticationResult by api.userApi.authenticateWithQuickConnect(
                            QuickConnectDto(secret = quickConnectStatus.secret),
                        )
                        val signIn = signInRow(server, existingUser, authenticationResult)
                        if (signIn == null) {
                            _state.update { it.copy(quickConnectStatus = null) }
                            return@launchIO
                        }
                        val current =
                            serverRepository.changeUser(
                                server.url,
                                authenticationResult,
                                signIn.row,
                            )
                        kidsModeService.lockIfKidAfterSignIn(current)
                        setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                    } catch (_: CancellationException) {
                        // no-op, user may have canceled
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error during quick connect")
                        if (ex is InvalidStatusException && ex.status == 401) {
                            _state.update {
                                it.copy(
                                    quickConnectEnabled = false,
                                    quickConnectStatus = null,
                                )
                            }
                        }
                        if (ex is InvalidStatusException && ex.status == 403) {
                            _state.update { it.copy(quickConnectStatus = null) }
                            setSignInRefused()
                        } else {
                            setError("Error with Quick Connect", ex)
                        }
                    }
                }
        }

        /**
         * Which row a sign-in is stored in. While locked to kids mode, a sign-in may only renew a kid's own row, as
         * that same Jellyfin user, also when it came through Add User or Quick Connect for a kid already saved on this
         * device. A refused sign-in changes nothing, its new session is ended, and null is returned.
         */
        private suspend fun signInRow(
            server: JellyfinServer,
            existingUser: JellyfinUser?,
            result: AuthenticationResult,
        ): KidsLockSignIn.Allowed? {
            val signedInUserId = result.user?.id
            val signedInServerId = result.serverId?.toUUIDOrNull()
            val locked = kidsModeService.isLockedNow()
            val savedKid =
                if (locked && existingUser == null && signedInUserId != null && signedInServerId != null) {
                    serverDao.getUser(signedInServerId, signedInUserId)?.takeIf { kidsModeService.profileFor(it) != null }
                } else {
                    null
                }
            val signIn = kidsLockSignIn(locked, existingUser, savedKid, signedInUserId, signedInServerId)
            if (signIn is KidsLockSignIn.Allowed) return signIn
            Timber.w("Refusing to sign in as another user while locked to kids mode")
            result.accessToken?.let { token ->
                try {
                    jellyfin.createApi(server.url, accessToken = token).sessionApi.reportSessionEnded()
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not end the refused session")
                }
            }
            val message =
                existingUser?.name?.let { context.getString(R.string.kids_sign_in_same_user, it) }
                    ?: context.getString(R.string.kids_sign_in_locked)
            _state.update {
                it.copy(
                    loginAttempts = it.loginAttempts + 1,
                    switchUserState = LoadingState.Error(message),
                )
            }
            return null
        }

        /**
         * Jellyfin refused a sign-in with valid credentials (HTTP 403): the account is at its maximum number of
         * sessions, disabled, or not allowed from this network. The response doesn't say which.
         */
        private fun setSignInRefused() {
            _state.update {
                it.copy(
                    loginAttempts = it.loginAttempts + 1,
                    switchUserState = LoadingState.Error(context.getString(R.string.login_sign_in_refused)),
                )
            }
        }

        fun cancelQuickConnect() {
            quickConnectJob?.cancel()
            _state.update {
                it.copy(
                    quickConnectStatus = null,
                )
            }
        }

        fun removeUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                serverRepository.removeUser(user)
                allUsers = allUsers?.filterNot { it.known && it.user.rowId == user.rowId }
                loadUsers()
            }
        }

        private suspend fun getUsers(): List<JellyfinUserAndImage> =
            withContext(PekeDispatchers.IO) {
                val api = jellyfin.createApi(server.url)
                val knownUsers =
                    serverDao
                        .getServer(server.id)
                        ?.users
                        .orEmpty()
                        .map {
                            JellyfinUserAndImage(
                                user = it,
                                imageUrl = api.imageApi.getUserImageUrl(it.id),
                                known = true,
                            )
                        }
                val knownUserIds = knownUsers.map { it.user.id }
                val publicUsers =
                    api.userApi
                        .getPublicUsers()
                        .content
                        .map {
                            JellyfinUser(
                                id = it.id,
                                name = it.name,
                                serverId = server.id,
                                accessToken = null,
                            )
                        }.filter { it.id !in knownUserIds }
                        .map {
                            JellyfinUserAndImage(
                                user = it,
                                imageUrl = api.imageApi.getUserImageUrl(it.id),
                                known = false,
                            )
                        }

                val users = knownUsers + publicUsers
                if (kidsModeService.isLockedNow()) {
                    // Learn who is a kid on this server before deciding who may be picked
                    kidsModeService.refreshServer(server)
                } else {
                    // Everyone may be picked, so the list doesn't wait for the server; if the refresh finds kids and
                    // that locks the device, the list is narrowed (see init)
                    viewModelScope.launchIO { kidsModeService.refreshServer(server) }
                }
                users
            }

        private suspend fun checkServerVersion(): Pair<String?, ServerVersionSupported> {
            val api = jellyfin.createApi(server.url)
            val systemInfo by api.systemApi.getPublicSystemInfo()
            val serverVersion = systemInfo.version?.let { ServerVersion.fromString(it) }
            return if (serverVersion == null) {
                systemInfo.version to ServerVersionSupported.UNKNOWN
            } else if (serverVersion < PekeApplication.minimumServerVersion) {
                systemInfo.version to ServerVersionSupported.NOT_SUPPORTED
            } else {
                systemInfo.version to ServerVersionSupported.SUPPORTED
            }
        }

        private fun setError(
            msg: String? = null,
            ex: Exception? = null,
        ) {
            _state.update {
                it.copy(
                    loginAttempts = it.loginAttempts + 1,
                    switchUserState = LoadingState.Error(msg, ex),
                )
            }
        }
    }

data class JellyfinUserAndImage(
    val user: JellyfinUser,
    val imageUrl: String?,
    val known: Boolean,
)

data class SwitchUserState(
    // LoadingState for fetching available users
    val loading: LoadingState = LoadingState.Pending,
    val quickConnectEnabled: Boolean = false,
    val quickConnectStatus: QuickConnectResult? = null,
    val users: List<JellyfinUserAndImage> = emptyList(),
    // LoadingState for while adding/switching users
    val switchUserState: LoadingState = LoadingState.Pending,
    val loginAttempts: Int = 0,
    val serverVersion: String? = null,
    val serverVersionSupported: ServerVersionSupported = ServerVersionSupported.UNKNOWN,
    // Whether the device is locked to kid profiles, which hides adding or removing users and switching servers
    val kidsLocked: Boolean = false,
)

/**
 * Result of trying to switch users via [SwitchUserViewModel.trySwitchUser]
 */
sealed interface SwitchUserResult {
    data object Success : SwitchUserResult

    data class Error(
        val errorMessage: String?,
        val showLogin: Boolean,
    ) : SwitchUserResult
}
