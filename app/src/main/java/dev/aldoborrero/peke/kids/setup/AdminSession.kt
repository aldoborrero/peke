package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsCaller
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.services.hilt.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.MissingBaseUrlException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectDto
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The outcome of an administrator sign-in
 */
sealed interface AdminSignIn {
    data class Opened(
        val session: AdminSession,
    ) : AdminSignIn

    /**
     * The account signed in but isn't a server administrator; its session was ended right away
     */
    data object NotAdmin : AdminSignIn

    /**
     * Wrong user name or password
     */
    data object Wrong : AdminSignIn

    /**
     * Quick Connect is turned off on the server
     */
    data object QuickConnectOff : AdminSignIn

    data object Unreachable : AdminSignIn

    data class Failed(
        val status: Int,
    ) : AdminSignIn
}

/**
 * A server administrator signed in for one grown-up action: setting the TV up, adding a kid or recovering the parent
 * code (research R4). It lives only in memory, in its own [api] with a device id of its own, so ending it doesn't end a
 * kid's session; nothing of it is written to the device. [close] ends it on the server when the action finishes, and
 * [use] does that for the screen that opened it, also when it is cancelled. If the app dies first nothing was stored,
 * and the server's token expires or is removed from the dashboard.
 */
class AdminSession internal constructor(
    val server: JellyfinServer,
    val userId: UUID,
    val name: String?,
    /**
     * Carries the token. Only for the action's own calls: never hand it to `ServerRepository` or anything that stores.
     */
    val api: ApiClient,
    private val dispatcher: CoroutineDispatcher,
    private val onClosed: (AdminSession) -> Unit = {},
) {
    val deviceId: String = api.deviceInfo.id

    private val closed = AtomicBoolean(false)

    val isClosed: Boolean get() = closed.get()

    /**
     * This session for the Kids plugin's calls, from its own device
     */
    val caller: KidsCaller get() = KidsCaller(api.accessToken.orEmpty(), userId, deviceId)

    /**
     * End the session on the server, best effort, and drop the token. Only the first call does anything.
     */
    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        onClosed(this)
        withContext(NonCancellable + dispatcher) {
            endSession(api)
            api.update(accessToken = null)
        }
    }

    override fun toString(): String = "AdminSession(server=${server.id}, userId=$userId, deviceId=$deviceId, closed=$isClosed)"
}

/**
 * Run [block] with this session and close it afterwards, however [block] ends
 */
suspend fun <T> AdminSession.use(block: suspend (AdminSession) -> T): T =
    try {
        block(this)
    } finally {
        close()
    }

/**
 * Signs a server administrator in for an [AdminSession]. The password goes only into the sign-in request.
 *
 * It knows every session it opened that isn't closed yet, so [closeAll] can end them when the app goes to the
 * background: a grown-up who leaves the TV mid-action doesn't leave an administrator session for a kid to find.
 */
@Singleton
class AdminSessions
    @Inject
    constructor(
        private val jellyfin: Jellyfin,
        private val deviceInfo: DeviceInfo,
        private val kidsApi: KidsApi,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        private val open = ConcurrentHashMap.newKeySet<AdminSession>()

        /**
         * Whether a session is open, for tests and checks
         */
        val anyOpen: Boolean get() = open.isNotEmpty()

        /**
         * Close every open session, best effort, as [AdminSession.close] does
         */
        suspend fun closeAll() {
            open.toList().forEach { it.close() }
        }

        suspend fun open(
            server: JellyfinServer,
            username: String,
            password: String,
        ): AdminSignIn {
            val api = newApi(server)
            val result =
                try {
                    api.userApi.authenticateUserByName(username = username, password = password).content
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    return failure(ex, quickConnect = false)
                }
            return opened(server, api, result)
        }

        /**
         * Sign in with Quick Connect from this session's own device: [onCode] shows the code, which the administrator
         * approves on another device. Waits until then, or until cancelled.
         */
        suspend fun openWithQuickConnect(
            server: JellyfinServer,
            onCode: suspend (String) -> Unit,
        ): AdminSignIn {
            val api = newApi(server)
            val result =
                try {
                    val request = api.quickConnectApi.initiateQuickConnect().content
                    onCode(request.code)
                    // Polled through KidsApi, which never logs the secret in the URL as the SDK does
                    var approved = false
                    while (!approved) {
                        delay(QUICK_CONNECT_POLL_MILLIS)
                        approved =
                            when (val state = kidsApi.quickConnectApproved(server.url, request.secret)) {
                                is KidsResult.Ok -> state.value
                                KidsResult.Forbidden -> return AdminSignIn.QuickConnectOff
                                KidsResult.Unreachable -> return AdminSignIn.Unreachable
                                is KidsResult.Failed -> return AdminSignIn.Failed(state.status)
                                else -> return AdminSignIn.Failed(0)
                            }
                    }
                    api.userApi.authenticateWithQuickConnect(QuickConnectDto(secret = request.secret)).content
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    return failure(ex, quickConnect = true)
                }
            return opened(server, api, result)
        }

        private fun newApi(server: JellyfinServer): ApiClient =
            jellyfin.createApi(baseUrl = server.url, deviceInfo = deviceInfo.copy(id = UUID.randomUUID().toString()))

        private suspend fun opened(
            server: JellyfinServer,
            api: ApiClient,
            result: AuthenticationResult,
        ): AdminSignIn {
            val token = result.accessToken ?: return AdminSignIn.Failed(0)
            val user = result.user ?: return AdminSignIn.Failed(0)
            api.update(accessToken = token)
            val session = AdminSession(server, user.id, user.name, api, dispatcher) { open -= it }
            open += session
            if (user.policy?.isAdministrator != true) {
                session.close()
                return AdminSignIn.NotAdmin
            }
            return AdminSignIn.Opened(session)
        }

        companion object {
            const val QUICK_CONNECT_POLL_MILLIS = 5_000L

            /**
             * 401 or 403 is a wrong user name or password, or Quick Connect turned off; no HTTP answer is unreachable
             */
            private fun failure(
                ex: Exception,
                quickConnect: Boolean,
            ): AdminSignIn =
                when {
                    ex is InvalidStatusException && (ex.status == 401 || ex.status == 403) -> {
                        if (quickConnect) AdminSignIn.QuickConnectOff else AdminSignIn.Wrong
                    }

                    ex is InvalidStatusException -> {
                        AdminSignIn.Failed(ex.status)
                    }

                    ex is TimeoutException ||
                        ex is SecureConnectionException ||
                        ex is MissingBaseUrlException ||
                        ex.cause is IOException -> {
                        AdminSignIn.Unreachable
                    }

                    else -> {
                        Timber.w("Administrator sign-in failed: %s", ex.javaClass.simpleName)
                        AdminSignIn.Failed(0)
                    }
                }
        }
    }

/**
 * Best effort, with one retry, since a session left behind only shows up in the server's device list
 */
private suspend fun endSession(api: ApiClient) {
    repeat(2) { attempt ->
        try {
            api.sessionApi.reportSessionEnded()
            return
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w("Could not end the administrator session (try %d): %s", attempt + 1, ex.javaClass.simpleName)
        }
    }
}
