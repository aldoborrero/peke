package com.github.damontecres.wholphin.kids.server

import com.github.damontecres.wholphin.services.hilt.IoDispatcher
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.MissingBaseUrlException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The outcome of a call to the Kids plugin. Anything but [Ok] leaves cached state alone unless the caller decides
 * otherwise.
 */
sealed interface KidsResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : KidsResult<T>

    /**
     * 404 without the plugin's own answer, such as a route that does not exist because the plugin is gone
     */
    data object NotFound : KidsResult<Nothing>

    /**
     * 404 with the plugin's own "This user is not a kid." answer
     */
    data object NotAKid : KidsResult<Nothing>

    /**
     * 409, with the server's current object
     */
    data class Conflict(
        val current: JsonObject?,
    ) : KidsResult<Nothing>

    /**
     * 401 or 403: not allowed, for example because a grant expired
     */
    data object Forbidden : KidsResult<Nothing>

    data class Throttled(
        val retryAfterSeconds: Int,
    ) : KidsResult<Nothing>

    /**
     * 400, with the field names that failed validation
     */
    data class Invalid(
        val fields: Set<String>,
    ) : KidsResult<Nothing>

    data class Failed(
        val status: Int,
    ) : KidsResult<Nothing>

    /**
     * No HTTP answer at all, such as a network error or timeout
     */
    data object Unreachable : KidsResult<Nothing>
}

/**
 * Calls the Jellyfin Kids plugin's client API (`/Kids`, version 1)
 *
 * The Jellyfin SDK can't send the grant header or read error bodies, so this uses OkHttp directly, except for the
 * administrator check in [isAdministrator], which is Jellyfin's own sign-in. Every call names the server and the
 * user token it runs as, because kids calls are not always made as the current user.
 */
@Singleton
class KidsApi
    @Inject
    constructor(
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
        private val jellyfin: Jellyfin,
        private val clientInfo: ClientInfo,
        private val deviceInfo: DeviceInfo,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        suspend fun info(baseUrl: String): KidsResult<KidsInfo> = call(baseUrl, "Kids/Info", null) { decode(KidsInfo.serializer(), it) }

        suspend fun profiles(
            baseUrl: String,
            token: String,
        ): KidsResult<List<KidProfileRef>> =
            call(baseUrl, "Kids/Profiles", token) { decode(ListSerializer(KidProfileRef.serializer()), it) }

        suspend fun profile(
            baseUrl: String,
            token: String,
            userId: UUID,
        ): KidsResult<JsonObject> = call(baseUrl, "Kids/Users/$userId/Profile", token) { KidsJson.parseToJsonElement(it).jsonObject }

        suspend fun putProfile(
            baseUrl: String,
            token: String,
            grant: String?,
            userId: UUID,
            body: JsonObject,
        ): KidsResult<JsonObject> =
            call(baseUrl, "Kids/Users/$userId/Profile", token, grant, "PUT", body.toString().toJsonBody()) {
                KidsJson.parseToJsonElement(it).jsonObject
            }

        suspend fun deleteProfile(
            baseUrl: String,
            token: String,
            grant: String?,
            userId: UUID,
        ): KidsResult<Unit> = call(baseUrl, "Kids/Users/$userId/Profile", token, grant, "DELETE") { }

        suspend fun lock(
            baseUrl: String,
            token: String,
        ): KidsResult<LockStatus> = call(baseUrl, "Kids/Parent/Lock", token) { decode(LockStatus.serializer(), it) }

        suspend fun verify(
            baseUrl: String,
            token: String,
            code: String,
        ): KidsResult<VerifyResponse> {
            val body = buildJsonObject { put("Code", code) }.toString().toJsonBody()
            return call(baseUrl, "Kids/Parent/Verify", token, null, "POST", body) { decode(VerifyResponse.serializer(), it) }
        }

        suspend fun verifier(
            baseUrl: String,
            token: String,
            grant: String?,
        ): KidsResult<VerifierResponse> = call(baseUrl, "Kids/Parent/Verifier", token, grant) { decode(VerifierResponse.serializer(), it) }

        suspend fun setLock(
            baseUrl: String,
            token: String,
            grant: String?,
            request: SetLockRequest,
        ): KidsResult<LockStatus> {
            val body = KidsJson.encodeToString(SetLockRequest.serializer(), request).toJsonBody()
            return call(baseUrl, "Kids/Parent/Lock", token, grant, "PUT", body) { decode(LockStatus.serializer(), it) }
        }

        suspend fun status(
            baseUrl: String,
            token: String,
            userId: UUID,
        ): KidsResult<KidsStatus> = call(baseUrl, "Kids/Users/$userId/Status", token) { decode(KidsStatus.serializer(), it) }

        /**
         * The kid's effective live TV channels, with what is on now (`GET /Kids/Users/{id}/LiveTv/Channels`)
         */
        suspend fun liveTvChannels(
            baseUrl: String,
            token: String,
            userId: UUID,
        ): KidsResult<KidsItemsResult> =
            call(baseUrl, "Kids/Users/$userId/LiveTv/Channels", token) { decode(KidsItemsResult.serializer(), it) }

        suspend fun bonus(
            baseUrl: String,
            token: String,
            grant: String?,
            userId: UUID,
            minutes: Int,
        ): KidsResult<KidsStatus> {
            val body = buildJsonObject { put("Minutes", minutes) }.toString().toJsonBody()
            return call(baseUrl, "Kids/Users/$userId/WatchTime/Bonus", token, grant, "POST", body) {
                decode(KidsStatus.serializer(), it)
            }
        }

        /**
         * Sign in to Jellyfin as a one-off device, only to learn whether [username] is an administrator, then end that
         * session. The credentials and the token are never kept or logged.
         */
        suspend fun isAdministrator(
            baseUrl: String,
            username: String,
            password: String,
        ): KidsResult<Boolean> =
            withContext(dispatcher) {
                // A device of its own, so no session of this TV is replaced
                val device = deviceInfo.copy(id = UUID.randomUUID().toString())
                val result =
                    try {
                        jellyfin
                            .createApi(baseUrl = baseUrl, deviceInfo = device)
                            .userApi
                            .authenticateUserByName(username = username, password = password)
                            .content
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        return@withContext signInFailure(ex)
                    }
                result.accessToken?.let { token ->
                    endSession(jellyfin.createApi(baseUrl = baseUrl, accessToken = token, deviceInfo = device))
                }
                KidsResult.Ok(result.user?.policy?.isAdministrator == true)
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
                    Timber.w("Could not end the administrator check session (try %d): %s", attempt + 1, ex.javaClass.simpleName)
                }
            }
        }

        private suspend fun <T> call(
            baseUrl: String,
            path: String,
            token: String?,
            grant: String? = null,
            method: String = "GET",
            body: RequestBody? = null,
            parse: (String) -> T,
        ): KidsResult<T> {
            val url =
                baseUrl
                    .toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addPathSegments(path)
                    ?.build()
                    ?: return KidsResult.Unreachable
            val request =
                Request
                    .Builder()
                    .url(url)
                    .method(method, body)
                    .header("Accept", "application/json")
                    .apply {
                        if (token != null) header("Authorization", authorization(token))
                        if (grant != null) header(GRANT_HEADER, grant)
                    }.build()
            return withContext(dispatcher) {
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        val text = response.body.string()
                        when (response.code) {
                            in 200..299 -> {
                                try {
                                    KidsResult.Ok(parse(text))
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Unreadable answer from %s", path)
                                    KidsResult.Failed(response.code)
                                }
                            }

                            400 -> {
                                KidsResult.Invalid(errorFields(text))
                            }

                            401, 403 -> {
                                KidsResult.Forbidden
                            }

                            404 -> {
                                if (isNotAKid(text)) KidsResult.NotAKid else KidsResult.NotFound
                            }

                            409 -> {
                                KidsResult.Conflict(objectOrNull(text))
                            }

                            429 -> {
                                KidsResult.Throttled(retryAfter(response.header("Retry-After"), text))
                            }

                            else -> {
                                KidsResult.Failed(response.code)
                            }
                        }
                    }
                } catch (ex: IOException) {
                    Timber.d(ex, "Kids call %s failed", path)
                    KidsResult.Unreachable
                }
            }
        }

        private fun authorization(token: String) =
            AuthorizationHeaderBuilder.buildHeader(
                clientName = clientInfo.name,
                clientVersion = clientInfo.version,
                deviceId = deviceInfo.id,
                deviceName = deviceInfo.name,
                accessToken = token,
            )

        companion object {
            const val GRANT_HEADER = "X-Kids-Grant"
            private val JSON = "application/json".toMediaType()

            private fun String.toJsonBody() = toRequestBody(JSON)

            private fun <T> decode(
                serializer: KSerializer<T>,
                text: String,
            ): T = KidsJson.decodeFromString(serializer, text)

            private fun objectOrNull(text: String): JsonObject? =
                try {
                    KidsJson.parseToJsonElement(text).jsonObject
                } catch (_: Exception) {
                    null
                }

            /**
             * Field names of a ValidationProblemDetails body (`Errors`, or `errors` in camelCase)
             */
            internal fun errorFields(text: String): Set<String> {
                val obj = objectOrNull(text) ?: return emptySet()
                val errors = (obj["Errors"] ?: obj["errors"]) as? JsonObject ?: return emptySet()
                return errors.keys
            }

            /**
             * Whether a 404 body is the plugin's ProblemDetails for a user who is not a kid (`title`, or `Title`)
             */
            internal fun isNotAKid(text: String): Boolean {
                val obj = objectOrNull(text) ?: return false
                val title = (obj["title"] ?: obj["Title"]) as? JsonPrimitive ?: return false
                return title.isString && title.content.trim() == NOT_A_KID_TITLE
            }

            private const val NOT_A_KID_TITLE = "This user is not a kid."

            internal fun retryAfter(
                header: String?,
                text: String,
            ): Int =
                header?.toIntOrNull()
                    ?: objectOrNull(text)?.let { (it["RetryAfterSeconds"] as? JsonPrimitive)?.content?.toIntOrNull() }
                    ?: DEFAULT_RETRY_SECONDS

            private const val DEFAULT_RETRY_SECONDS = 30

            /**
             * Maps a failed administrator sign-in like the other calls: 401 or 403 is a wrong user name or password,
             * and no HTTP answer at all is unreachable
             */
            private fun signInFailure(ex: Exception): KidsResult<Nothing> =
                when {
                    ex is InvalidStatusException && (ex.status == 401 || ex.status == 403) -> KidsResult.Forbidden
                    ex is InvalidStatusException -> KidsResult.Failed(ex.status)
                    ex is TimeoutException || ex is SecureConnectionException || ex is MissingBaseUrlException -> KidsResult.Unreachable
                    ex.cause is IOException -> KidsResult.Unreachable
                    else -> KidsResult.Failed(0)
                }
        }
    }
