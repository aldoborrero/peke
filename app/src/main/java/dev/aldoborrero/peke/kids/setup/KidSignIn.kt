package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.services.hilt.IoDispatcher
import dev.aldoborrero.peke.services.hilt.forKid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
import org.jellyfin.sdk.model.api.UserPolicy
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * The outcome of signing a kid in on this TV (`contracts/server-calls.md`)
 */
sealed interface KidSignInResult {
    /**
     * @param outsideHours the kid is outside their allowed hours right now (their Jellyfin access schedule, or the Kids
     * plugin's bedtime): the token works, but the server refuses it until their time comes
     */
    data class SignedIn(
        val user: JellyfinUser,
        val outsideHours: Boolean = false,
    ) : KidSignInResult

    /**
     * Quick Connect is off on the server: turn it on, or type the kid's password once
     */
    data object QuickConnectOff : KidSignInResult

    /**
     * The kid's account may only be used on the devices picked in the dashboard. This TV can't be picked there: the
     * kid's device id for it never signed in, so it isn't listed. Allowing all devices for the kid is what works.
     */
    data object DeviceNotAllowed : KidSignInResult

    /**
     * The administrator's session no longer works, for example after the server restarted: sign in again
     */
    data object AdminExpired : KidSignInResult

    /**
     * The kid is signed in on as many devices as the server allows
     */
    data object TooManySessions : KidSignInResult

    /**
     * The code expired again and again
     */
    data object CodeExpired : KidSignInResult

    /**
     * The server doesn't know the kid
     */
    data object UnknownUser : KidSignInResult

    /**
     * The account is turned off: the server gave a token but refuses it with 401, or with 403 and the kid's policy says
     * it's disabled. With the password, a sign-in the server refuses with 403, as it does outside the kid's allowed
     * hours too: no token is given then.
     */
    data object AccountRefused : KidSignInResult

    data object WrongPassword : KidSignInResult

    data object Unreachable : KidSignInResult

    data class Failed(
        val status: Int,
    ) : KidSignInResult
}

/**
 * Signs a kid in on this TV, with a session of their own made with their own device id (research R1, R2)
 *
 * With Quick Connect, the TV starts a request as the kid's device, the in-memory [AdminSession] approves it for the kid,
 * and the TV redeems the secret for the kid's token: nobody types the kid's password. Without it, the kid's password
 * is typed once and only sent with the sign-in. Either way the token is checked with `Users/Me` before it is saved, and
 * it is saved only through [ServerRepository.saveUser], which doesn't make the kid current: the pages on screen stay
 * whoever's they were. The secret and the password live only in these calls' locals and are never logged.
 */
class KidSignIn
    @Inject
    constructor(
        private val jellyfin: Jellyfin,
        private val deviceInfo: DeviceInfo,
        private val serverRepository: ServerRepository,
        private val serverDao: JellyfinServerDao,
        private val kidProfileRepository: KidProfileRepository,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        suspend fun withQuickConnect(
            admin: AdminSession,
            kidId: UUID,
        ): KidSignInResult =
            withContext(dispatcher) {
                val server = admin.server
                val kidApi = kidApi(server, kidId)
                try {
                    if (!kidApi.quickConnectApi.getQuickConnectEnabled().content) return@withContext KidSignInResult.QuickConnectOff
                    repeat(QUICK_CONNECT_ATTEMPTS) {
                        val request = kidApi.quickConnectApi.initiateQuickConnect().content
                        val approved =
                            try {
                                admin.api.quickConnectApi
                                    .authorizeQuickConnect(code = request.code, userId = kidId)
                                    .content
                            } catch (ex: InvalidStatusException) {
                                when (ex.status) {
                                    // Expired: start a new request
                                    404 -> return@repeat

                                    400 -> return@withContext KidSignInResult.UnknownUser

                                    // Quick Connect was on a moment ago: it was turned off since, or else the
                                    // administrator's session no longer works
                                    401 -> return@withContext if (quickConnectEnabled(
                                            kidApi,
                                        )
                                    ) {
                                        KidSignInResult.AdminExpired
                                    } else {
                                        KidSignInResult.QuickConnectOff
                                    }

                                    403 -> return@withContext refusedOnDevice(admin, kidId)

                                    else -> return@withContext KidSignInResult.Failed(ex.status)
                                }
                            }
                        if (!approved) return@withContext KidSignInResult.Failed(0)
                        val result = kidApi.userApi.authenticateWithQuickConnect(QuickConnectDto(secret = request.secret)).content
                        return@withContext save(server, kidId, kidApi, result, admin)
                    }
                    KidSignInResult.CodeExpired
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    failure(ex)
                }
            }

        /**
         * Sign [kidId] in with the password typed once, for a server with Quick Connect off
         */
        suspend fun withPassword(
            server: JellyfinServer,
            kidId: UUID,
            username: String,
            password: String,
        ): KidSignInResult =
            withContext(dispatcher) {
                val kidApi = kidApi(server, kidId)
                try {
                    val result =
                        try {
                            kidApi.userApi.authenticateUserByName(username = username, password = password).content
                        } catch (ex: InvalidStatusException) {
                            // Jellyfin answers a wrong password with 401, and an account turned off, outside its
                            // allowed hours or kept off remote access with 403 (`UserManager.AuthenticateUser`)
                            when (ex.status) {
                                401 -> return@withContext KidSignInResult.WrongPassword
                                403 -> return@withContext KidSignInResult.AccountRefused
                                else -> throw ex
                            }
                        }
                    save(server, kidId, kidApi, result, admin = null)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    failure(ex)
                }
            }

        private fun kidApi(
            server: JellyfinServer,
            kidId: UUID,
        ): ApiClient = jellyfin.createApi(baseUrl = server.url, deviceInfo = deviceInfo.forKid(kidId))

        /**
         * Whether Quick Connect is on, asked again; an answer that can't be read counts as on, so a 401 then says to sign
         * in again rather than to turn on what may be on
         */
        private suspend fun quickConnectEnabled(api: ApiClient): Boolean =
            try {
                api.quickConnectApi.getQuickConnectEnabled().content
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not check Quick Connect again: %s", ex.javaClass.simpleName)
                true
            }

        /**
         * Check the new token with `Users/Me`, then save the kid and cache their profile, so this TV lists them
         *
         * A 401 is a token the server doesn't take. A 403 is one it knows but won't serve right now: outside the kid's
         * access schedule (Jellyfin's default authorization policy) or the Kids plugin's bedtime. Only a kid whose
         * policy, read as [admin], says the account is turned off loses it then; any other kid keeps it and can watch
         * when their time comes.
         */
        private suspend fun save(
            server: JellyfinServer,
            kidId: UUID,
            kidApi: ApiClient,
            result: AuthenticationResult,
            admin: AdminSession?,
        ): KidSignInResult {
            val token = result.accessToken ?: return KidSignInResult.Failed(0)
            kidApi.update(accessToken = token)
            val outsideHours =
                try {
                    kidApi.userApi.getCurrentUser()
                    false
                } catch (ex: InvalidStatusException) {
                    if (ex.status != 401 && ex.status != 403) throw ex
                    if (ex.status == 401 || isDisabled(admin, kidId)) {
                        // A token nobody can use: end its session rather than leave it in the dashboard
                        endSession(kidApi)
                        return KidSignInResult.AccountRefused
                    }
                    true
                }
            val existing = serverDao.getServer(server.id)?.users?.firstOrNull { it.id == kidId }
            // Saved, not made current: the kid whose pages are on screen, if any, stays theirs
            val saved = serverRepository.saveUser(server.url, result, existing)
            if (!kidProfileRepository.refresh(saved.server, saved.user)) {
                Timber.w("Signed kid %s in, but couldn't read their profile", kidId)
                return KidSignInResult.Unreachable
            }
            // Unmarked in the dashboard meanwhile: the refresh signed them out again
            if (!kidProfileRepository.isKid(saved.user)) return KidSignInResult.UnknownUser
            return KidSignInResult.SignedIn(saved.user, outsideHours)
        }

        /**
         * Whether [kidId]'s account is turned off, from their policy read as [admin]. Without an answer it isn't known to
         * be, so a token the server knows is kept.
         */
        private suspend fun isDisabled(
            admin: AdminSession?,
            kidId: UUID,
        ): Boolean = admin?.let { policyOf(it, kidId) }?.isDisabled == true

        /**
         * Authorize answered 403: the kid may not use this device, or has too many sessions. Jellyfin doesn't say
         * which outside development builds, so the kid's policy, read as the administrator, tells: a kid limited to some
         * devices that don't include this TV's id for them can't use it, and otherwise a session limit is what's left.
         * A policy that can't be read, or one with neither, is a plain failure.
         */
        private suspend fun refusedOnDevice(
            admin: AdminSession,
            kidId: UUID,
        ): KidSignInResult {
            val policy = policyOf(admin, kidId)
            val device = deviceInfo.forKid(kidId).id
            return when {
                policy == null -> KidSignInResult.Failed(403)
                !policy.enableAllDevices && device !in policy.enabledDevices.orEmpty() -> KidSignInResult.DeviceNotAllowed
                policy.maxActiveSessions >= 1 -> KidSignInResult.TooManySessions
                else -> KidSignInResult.Failed(403)
            }
        }

        /**
         * [kidId]'s policy, read as [admin], or null if it can't be read
         */
        private suspend fun policyOf(
            admin: AdminSession,
            kidId: UUID,
        ): UserPolicy? =
            try {
                admin.api.userApi
                    .getUserById(kidId)
                    .content.policy
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not read the kid's policy: %s", ex.javaClass.simpleName)
                null
            }

        private suspend fun endSession(api: ApiClient) {
            try {
                api.sessionApi.reportSessionEnded()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w("Could not end a refused kid session: %s", ex.javaClass.simpleName)
            }
        }

        companion object {
            /**
             * How many Quick Connect requests are tried while their codes expire
             */
            const val QUICK_CONNECT_ATTEMPTS = 3

            private fun failure(ex: Exception): KidSignInResult =
                when {
                    ex is InvalidStatusException -> {
                        KidSignInResult.Failed(ex.status)
                    }

                    ex is TimeoutException ||
                        ex is SecureConnectionException ||
                        ex is MissingBaseUrlException ||
                        ex is IOException ||
                        ex.cause is IOException -> {
                        KidSignInResult.Unreachable
                    }

                    else -> {
                        Timber.w("Kid sign-in failed: %s", ex.javaClass.simpleName)
                        KidSignInResult.Failed(0)
                    }
                }
        }
    }
