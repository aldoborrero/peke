package dev.aldoborrero.peke.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.data.model.ServerUserConfig
import dev.aldoborrero.peke.kids.data.KidsDataCleaner
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.TvState
import dev.aldoborrero.peke.services.hilt.IoDispatcher
import dev.aldoborrero.peke.services.hilt.forKid
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles managing the current server & user as well as adding & removing new ones
 */
@Singleton
class ServerRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val jellyfin: Jellyfin,
        val serverDao: JellyfinServerDao,
        val apiClient: ApiClient,
        private val deviceInfo: DeviceInfo,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val tvSetupRepository: TvSetupRepository,
        private val kidsDataCleaner: KidsDataCleaner,
    ) {
        private var _current = MutableStateFlow<CurrentUser?>(null)
        val current: StateFlow<CurrentUser?> = _current

        private var _currentUserDto = MutableStateFlow<ServerUserConfig?>(null)
        val currentUserDto: ServerUserConfig? get() = _currentUserDto.value
        val currentUserDtoFlow: StateFlow<ServerUserConfig?> get() = _currentUserDto

        val currentServer: JellyfinServer? get() = _current.value?.server
        val currentServerFlow: Flow<JellyfinServer?> get() = _current.map { it?.server }
        val currentUser: JellyfinUser? get() = _current.value?.user

        @OptIn(ExperimentalCoroutinesApi::class)
        val currentUserFlow: Flow<JellyfinUser?>
            get() =
                _current
                    .flatMapLatest { user ->
                        if (user?.user != null) {
                            serverDao
                                .getUserFlow(user.user.serverId, user.user.id)
                                .distinctUntilChanged()
                        } else {
                            flow { emit(null) }
                        }
                    }

        /**
         * Adds a server to the app database and updated the [ApiClient] to the server's URL
         *
         * The current user is removed
         */
        suspend fun addAndChangeServer(server: JellyfinServer) {
            withContext(ioDispatcher) {
                serverDao.addOrUpdateServer(server)
            }
            apiClient.update(baseUrl = server.url, accessToken = null, deviceInfo = deviceInfo)
            _current.value = null
        }

        /**
         * Saves the server & user to the app database and updates the [ApiClient] to use this server & user
         */
        suspend fun changeUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ): CurrentUser =
            withContext(ioDispatcher) {
                if (server.id != user.serverId) {
                    throw IllegalStateException("User is not part of the server")
                }
                Timber.v("Changing user to ${user.name} on ${server.url}")
                apiClient.update(baseUrl = server.url, accessToken = user.accessToken, deviceInfo = deviceInfo.forKid(user.id))
                val userDto by apiClient.userApi.getCurrentUser()
                val updatedServer =
                    try {
                        val sysInfo by apiClient.systemApi.getPublicSystemInfo()
                        server.copy(name = sysInfo.serverName, version = sysInfo.version)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Exception fetching public system info")
                        server
                    }
                var updatedUser =
                    user.copy(
                        id = userDto.id,
                        name = userDto.name,
                    )
                serverDao.addOrUpdateServer(updatedServer)
                updatedUser = serverDao.addOrUpdateUser(updatedUser)

                val currentUser = CurrentUser(updatedServer, updatedUser)
                _current.value = currentUser
                _currentUserDto.value = ServerUserConfig(userDto)
                return@withContext currentUser
            }

        /**
         * Makes a saved user current from their stored rows, without asking the server, so a kid opens while it can't
         * be reached (research R4). [currentUserDto] stays unknown until [updateUserDto] refreshes it.
         */
        suspend fun restoreUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ): CurrentUser =
            withContext(ioDispatcher) {
                if (server.id != user.serverId) {
                    throw IllegalStateException("User is not part of the server")
                }
                Timber.v("Restoring user ${user.name} on ${server.url}")
                apiClient.update(baseUrl = server.url, accessToken = user.accessToken, deviceInfo = deviceInfo.forKid(user.id))
                val currentUser = CurrentUser(server, user)
                _currentUserDto.value = null
                _current.value = currentUser
                return@withContext currentUser
            }

        /**
         * Try to restore the session of this TV's last kid, or of its only kid, for work that runs without the app's
         * pages such as the home screen channels. Never a user who isn't one of this TV's kids, and nobody when the TV
         * isn't set up.
         *
         * Does not check if the user profile is protected
         */
        suspend fun restoreLastSession(): RestoredSession {
            val tv =
                withTimeoutOrNull(TV_STATE_TIMEOUT_MILLIS) { tvSetupRepository.state.first { it != TvState.Loading } }
                    as? TvState.SetUp
                    ?: return RestoredSession.None
            val kid = tv.kids.firstOrNull { it.rowId == tv.lastKidRowId } ?: tv.kids.singleOrNull()
            Timber.d("restoreLastSession: kid=%s", kid?.rowId)
            if (kid?.accessToken == null) return RestoredSession.ServerOnly(tv.server)
            val currentUser = tryChangeUser(tv.server.id, kid.id)
            return if (currentUser != null) RestoredSession.Success(currentUser) else RestoredSession.ServerOnly(tv.server)
        }

        /**
         * Try to change to the given server & user. If the user's profile is protected, returns null
         *
         * @return the server/user or null if the session could not be restored
         */
        suspend fun tryChangeUser(
            serverId: UUID?,
            userId: UUID?,
        ): CurrentUser? =
            withContext(ioDispatcher) {
                return@withContext if (serverId == null || userId == null) {
                    _current.value = null
                    null
                } else {
                    val serverAndUsers = serverDao.getServer(serverId)
                    if (serverAndUsers != null) {
                        val user = serverAndUsers.users.firstOrNull { it.id == userId }
                        if (user != null && !user.isProtected) {
                            changeUser(serverAndUsers.server, user)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }

        /**
         * Given a successful [AuthenticationResult], switch to the user that just authenticated
         */
        suspend fun changeUser(
            serverUrl: String,
            authenticationResult: AuthenticationResult,
            existingUser: JellyfinUser?,
        ) = withContext(ioDispatcher) {
            val accessToken = authenticationResult.accessToken
            if (accessToken != null) {
                val authedUser = authenticationResult.user
                val server =
                    authenticationResult.serverId?.toUUIDOrNull()?.let {
                        JellyfinServer(
                            id = it,
                            name = authedUser?.serverName,
                            url = serverUrl,
                            null,
                        )
                    }
                if (server != null) {
                    val user =
                        authedUser?.let {
                            if (existingUser != null) {
                                Timber.d("Re-using existing user")
                                existingUser.copy(
                                    // If the server authenticated via the server, always remove the PIN
                                    pin = null,
                                    accessToken = accessToken,
                                    lastUsed = ZonedDateTime.now(),
                                )
                            } else {
                                Timber.d("Creating new user")
                                JellyfinUser(
                                    id = it.id,
                                    name = it.name,
                                    serverId = server.id,
                                    accessToken = accessToken,
                                    lastUsed = ZonedDateTime.now(),
                                )
                            }
                        }
                    if (user != null) {
                        return@withContext changeUser(server, user)
                    } else {
                        throw IllegalArgumentException("Authentication result's user was null")
                    }
                } else {
                    throw IllegalArgumentException("Authentication result's serverId not valid: ${authenticationResult.serverId}")
                }
            } else {
                throw IllegalArgumentException("Authentication result's access token was null")
            }
        }

        suspend fun removeUser(user: JellyfinUser) {
            if (current.value?.user?.id == user.id) {
                withContext(PekeDispatchers.Main) {
                    _current.value = null
                }
                apiClient.update(accessToken = null, deviceInfo = deviceInfo)
            }
            withContext(ioDispatcher) {
                serverDao.deleteUser(user.serverId, user.id)
            }
            kidsDataCleaner.onUserRemoved(user.rowId)
        }

        suspend fun removeServer(server: JellyfinServer) {
            if (current.value?.server?.id == server.id) {
                withContext(PekeDispatchers.Main) {
                    _current.value = null
                }
                apiClient.update(baseUrl = null, accessToken = null, deviceInfo = deviceInfo)
            }
            withContext(ioDispatcher) {
                serverDao.deleteServer(server.id)
            }
            kidsDataCleaner.onServerRemoved(server.id)
        }

        suspend fun updateUserAuth(
            user: JellyfinUser,
            pin: String?,
            requireLogin: Boolean,
        ) {
            val newUser = user.copy(pin = pin, requireLogin = requireLogin)
            val updatedUser = serverDao.addOrUpdateUser(newUser)
            val cur = current.value
            if (cur?.user?.id == updatedUser.id && cur.server?.id == user.serverId) {
                // Updating current user, so push out the change
                current.value?.let {
                    val newCurrent = it.copy(user = updatedUser)
                    _current.value = newCurrent
                }
            }
        }

        /**
         * Update [currentUserDto] by querying the server
         */
        suspend fun updateUserDto() {
            val userDto by apiClient.userApi.getCurrentUser()
            updateUserDto(userDto)
        }

        /**
         * Update [currentUserDto] with the specified [UserDto]
         *
         * This will only update if the [UserDto] is for the [currentUser]
         */
        fun updateUserDto(userDto: UserDto) {
            _currentUserDto.update {
                if (currentUser?.id == userDto.id) ServerUserConfig(userDto) else it
            }
        }

        companion object {
            /**
             * How long [restoreLastSession] waits for this TV's state, from local data only
             */
            private const val TV_STATE_TIMEOUT_MILLIS = 5_000L
        }
    }

@Serializable
data class CurrentUser(
    val server: JellyfinServer,
    val user: JellyfinUser,
)

/**
 * The result of restoring a session
 */
sealed interface RestoredSession {
    /**
     * Session could not be restored
     */
    data object None : RestoredSession

    /**
     * Only the server is known: no kid could be restored
     */
    data class ServerOnly(
        val server: JellyfinServer,
    ) : RestoredSession

    /**
     * Session was restored successfully
     */
    data class Success(
        val currentUser: CurrentUser,
    ) : RestoredSession
}
