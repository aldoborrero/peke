package dev.aldoborrero.peke.kids

import android.content.Context
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.server.KidsServerDao
import dev.aldoborrero.peke.kids.setup.TvSetupDao
import dev.aldoborrero.peke.kids.setup.TvSetupEntity
import dev.aldoborrero.peke.kids.timer.MonotonicClock
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.io.IOException
import java.util.UUID

/**
 * A [KidProfileDao] in memory. A profile's server is its [KidProfile.serverId], or else [serverOf] of its user row.
 */
class FakeKidProfileDao(
    private val serverOf: (Int) -> UUID? = { null },
) : KidProfileDao {
    val profiles = MutableStateFlow<Map<Int, KidProfile>>(emptyMap())

    override fun observe(userId: Int): Flow<KidProfile?> = profiles.map { it[userId] }

    override suspend fun get(userId: Int): KidProfile? = profiles.value[userId]

    override suspend fun allUserIds(): List<Int> = profiles.value.keys.toList()

    override fun observeUserIds(): Flow<List<Int>> = profiles.map { it.keys.toList() }

    override fun observeCount(): Flow<Int> = profiles.map { it.size }

    private fun KidProfile.server(): UUID? = serverId ?: serverOf(userId)

    override suspend fun userIdsForServer(serverId: UUID): List<Int> =
        profiles.value.values
            .filter {
                it.server() == serverId
            }.map { it.userId }

    override fun observeCountForServer(serverId: UUID): Flow<Int> = profiles.map { p -> p.values.count { it.server() == serverId } }

    override suspend fun upsert(profile: KidProfile) {
        profiles.value += (profile.userId to profile)
    }

    override suspend fun delete(userId: Int) {
        profiles.value -= userId
    }

    override suspend fun deleteForServer(serverId: UUID) {
        profiles.value = profiles.value.filterValues { it.server() != serverId }
    }
}

class FakeKidsServerDao : KidsServerDao {
    val servers = MutableStateFlow<Map<UUID, KidsServer>>(emptyMap())

    override suspend fun get(serverId: UUID): KidsServer? = servers.value[serverId]

    override fun observe(serverId: UUID): Flow<KidsServer?> = servers.map { it[serverId] }

    override suspend fun upsert(server: KidsServer) {
        servers.value += (server.serverId to server)
    }

    override suspend fun insertIfAbsent(server: KidsServer) {
        if (server.serverId !in servers.value) servers.value += (server.serverId to server)
    }

    override suspend fun updateInfo(
        serverId: UUID,
        apiVersion: Int?,
        features: String,
        checkedAt: Long,
    ) = change(serverId) { it.copy(apiVersion = apiVersion, features = features, checkedAt = checkedAt) }

    override suspend fun updateLock(
        serverId: UUID,
        lockType: String?,
        verifier: String?,
        changedAt: String?,
    ) = change(serverId) { it.copy(lockType = lockType, verifier = verifier, lockChangedAt = changedAt) }

    override suspend fun updateOfflineThrottle(
        serverId: UUID,
        failures: Int,
        blockedAt: Long?,
    ) = change(serverId) { it.copy(offlineFailures = failures, offlineBlockedAt = blockedAt) }

    private fun change(
        serverId: UUID,
        block: (KidsServer) -> KidsServer,
    ) {
        servers.value[serverId]?.let { servers.value += (serverId to block(it)) }
    }

    override suspend fun delete(serverId: UUID) {
        servers.value -= serverId
    }

    override suspend fun allServerIds(): List<UUID> = servers.value.keys.toList()
}

/**
 * The features kids mode needs, as stored in [KidsServer.features] and as `/Kids/Info` lists them
 */
val KIDS_FEATURES = KidsFeatures.REQUIRED.joinToString(",")
val KIDS_FEATURES_JSON = KidsFeatures.REQUIRED.joinToString(",") { "\"$it\"" }

class FakeClock(
    var now: Long = 1_000_000L,
) : MonotonicClock {
    override fun elapsedMillis(): Long = now
}

/**
 * A fake Kids plugin: answers requests by method and path, and records them
 */
class FakeKidsServer {
    data class Answer(
        val code: Int,
        val body: String = "",
        val headers: Map<String, String> = emptyMap(),
    )

    data class Recorded(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String?,
    )

    val requests = mutableListOf<Recorded>()
    private val answers = mutableMapOf<String, Answer>()

    /**
     * Answers that throw, as if the network failed
     */
    var offline = false

    /**
     * Called with each request before it is answered, for example to change something while the request is on its way
     */
    var onRequest: (Recorded) -> Unit = {}

    fun on(
        method: String,
        path: String,
        code: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
    ) {
        answers["$method $path"] = Answer(code, body, headers)
    }

    private val interceptor =
        Interceptor { chain ->
            val request = chain.request()
            if (offline) throw IOException("offline")
            val path = request.url.encodedPath
            val recorded = Recorded(request.method, path, request.headers.associate { it.first to it.second }, request.bodyText())
            requests += recorded
            onRequest(recorded)
            val answer = answers["${request.method} $path"] ?: Answer(404)
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(answer.code)
                .message("")
                .apply { answer.headers.forEach { (k, v) -> header(k, v) } }
                .body(answer.body.toResponseBody("application/json".toMediaType()))
                .build()
        }

    private val client by lazy { OkHttpClient.Builder().addInterceptor(interceptor).build() }
    val clientInfo = ClientInfo("Peke", "test")
    val deviceInfo = DeviceInfo(DEVICE_ID, "TV")

    /**
     * A Jellyfin SDK instance whose calls this fake answers
     */
    val jellyfin: Jellyfin by lazy {
        val factory = OkHttpFactory(client)
        createJellyfin {
            context = mockk<Context>(relaxed = true)
            this.clientInfo = this@FakeKidsServer.clientInfo
            this.deviceInfo = this@FakeKidsServer.deviceInfo
            apiClientFactory = factory
            socketConnectionFactory = factory
        }
    }

    fun api(dispatcher: CoroutineDispatcher): KidsApi = KidsApi(client, jellyfin, clientInfo, deviceInfo, dispatcher)

    private fun Request.bodyText(): String? = body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }

    companion object {
        const val DEVICE_ID = "device-1"
    }
}

/**
 * A [TvSetupDao] in memory. With [unreadable] set, reading the row fails as a corrupt row would.
 */
class FakeTvSetupDao(
    initial: TvSetupEntity? = null,
) : TvSetupDao {
    val row = MutableStateFlow(initial)
    var unreadable = false

    override fun observe(): Flow<TvSetupEntity?> =
        row.map {
            if (unreadable) throw IllegalArgumentException("unreadable row")
            it
        }

    override suspend fun get(): TvSetupEntity? {
        if (unreadable) throw IllegalArgumentException("unreadable row")
        return row.value
    }

    override suspend fun upsert(setup: TvSetupEntity) {
        row.value = setup
    }

    override suspend fun delete() {
        row.value = null
    }
}

/**
 * Jellyfin's answer to a sign-in (`Users/AuthenticateByName` or `Users/AuthenticateWithQuickConnect`), with the
 * fields the SDK requires
 */
fun signInAnswer(
    isAdministrator: Boolean,
    token: String,
    userId: UUID = UUID.randomUUID(),
): String {
    val flags =
        listOf(
            "IsHidden",
            "EnableCollectionManagement",
            "EnableSubtitleManagement",
            "EnableLyricManagement",
            "IsDisabled",
            "EnableUserPreferenceAccess",
            "EnableRemoteControlOfOtherUsers",
            "EnableSharedDeviceControl",
            "EnableRemoteAccess",
            "EnableLiveTvManagement",
            "EnableLiveTvAccess",
            "EnableMediaPlayback",
            "EnableAudioPlaybackTranscoding",
            "EnableVideoPlaybackTranscoding",
            "EnablePlaybackRemuxing",
            "ForceRemoteSourceTranscoding",
            "EnableContentDeletion",
            "EnableContentDownloading",
            "EnableSyncTranscoding",
            "EnableMediaConversion",
            "EnableAllDevices",
            "EnableAllChannels",
            "EnableAllFolders",
            "EnablePublicSharing",
        ).joinToString(",") { "\"$it\":false" }
    val counts =
        listOf("InvalidLoginAttemptCount", "LoginAttemptsBeforeLockout", "MaxActiveSessions", "RemoteClientBitrateLimit")
            .joinToString(",") { "\"$it\":0" }
    val policy =
        """{"IsAdministrator":$isAdministrator,$flags,$counts,"AuthenticationProviderId":"a","PasswordResetProviderId":"p","SyncPlayAccess":"None"}"""
    val user =
        """{"Id":"$userId","HasPassword":true,"HasConfiguredPassword":true,"HasConfiguredEasyPassword":false,"Policy":$policy}"""
    return """{"User":$user,"AccessToken":"$token"}"""
}
