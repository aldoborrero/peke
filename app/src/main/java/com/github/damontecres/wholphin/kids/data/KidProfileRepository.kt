package com.github.damontecres.wholphin.kids.data

import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.kids.gate.KidsAuth
import com.github.damontecres.wholphin.kids.gate.ParentLockService
import com.github.damontecres.wholphin.kids.server.KidProfileMapper
import com.github.damontecres.wholphin.kids.server.KidsApi
import com.github.damontecres.wholphin.kids.server.KidsFeatures
import com.github.damontecres.wholphin.kids.server.KidsPluginService
import com.github.damontecres.wholphin.kids.server.KidsResult
import com.github.damontecres.wholphin.kids.server.KidsServerStatus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class SaveResult {
    SAVED,

    /**
     * The profile changed elsewhere; the cache now has the server's current values
     */
    CONFLICT,

    /**
     * Pass the parent gate (again) first
     */
    NEEDS_GRANT,
    UNREACHABLE,
    FAILED,
}

/**
 * Kid profiles from the Jellyfin Kids plugin, cached in [KidProfileDao]
 *
 * The server decides who is a kid. Reads update the cache only on a definite answer, so errors keep the last known
 * profiles (fail closed). Changes go to the server, authorised by the parent grant or an administrator, and are
 * based on the cached revision so that edits made elsewhere are never overwritten.
 */
@Singleton
class KidProfileRepository
    @Inject
    constructor(
        private val kidsApi: KidsApi,
        private val kidProfileDao: KidProfileDao,
        private val serverDao: JellyfinServerDao,
        private val pluginService: KidsPluginService,
        private val parentLockService: ParentLockService,
    ) {
        private suspend fun available(server: JellyfinServer) = pluginService.state(server.id).status == KidsServerStatus.AVAILABLE

        /**
         * Read [user]'s profile from the server with the user's own token
         */
        suspend fun refresh(
            server: JellyfinServer,
            user: JellyfinUser,
        ) {
            val token = user.accessToken ?: return
            if (!available(server)) return
            when (val result = kidsApi.profile(server.url, token, user.id)) {
                is KidsResult.Ok -> store(user, result.value)
                KidsResult.NotAKid, KidsResult.NotFound -> deleteIfNotAKid(server, user, result)
                else -> Timber.d("Keeping the cached kid profile of %s: %s", user.rowId, result)
            }
        }

        /**
         * After a 404, remove [user]'s profile only if it is the plugin's own "not a kid" answer and the plugin is
         * still there. A 404 because the plugin went away keeps kids mode (fail closed).
         *
         * @return whether the profile was removed
         */
        private suspend fun deleteIfNotAKid(
            server: JellyfinServer,
            user: JellyfinUser,
            result: KidsResult<*>,
        ): Boolean {
            // Ask again either way, so a plugin that went away is noticed
            val available = pluginService.confirmAvailable(server)
            if (result != KidsResult.NotAKid || !available) {
                Timber.w("Keeping the kid profile of %s after a 404 (plugin available=%s)", user.rowId, available)
                return false
            }
            kidProfileDao.delete(user.rowId)
            return true
        }

        /**
         * Update the cache for every signed-in user of [server]: who is a kid, and the profiles that changed
         */
        suspend fun refreshServer(
            server: JellyfinServer,
            preferred: JellyfinUser? = null,
        ) {
            if (!available(server)) return
            val users = withContext(WholphinDispatchers.IO) { serverDao.getServer(server.id)?.users }.orEmpty()
            val token = (preferred?.accessToken ?: users.firstNotNullOfOrNull { it.accessToken }) ?: return
            val refs =
                when (val result = kidsApi.profiles(server.url, token)) {
                    is KidsResult.Ok -> result.value.mapNotNull { ref -> ref.userId.toUUIDOrNull()?.let { it to ref.revision } }.toMap()
                    else -> return
                }
            users.forEach { user ->
                val revision = refs[user.id]
                if (revision == null) {
                    kidProfileDao.delete(user.rowId)
                } else if (kidProfileDao.get(user.rowId)?.revision != revision) {
                    refresh(server, user)
                }
            }
        }

        suspend fun mark(user: JellyfinUser): SaveResult {
            val auth = parentLockService.auth() ?: return SaveResult.NEEDS_GRANT
            val body = KidProfileMapper.toUpdate(KidProfile(userId = user.rowId), 0, sendSessionLimit(auth))
            return handle(user, auth, kidsApi.putProfile(auth.server.url, auth.token, auth.grant, user.id, body))
        }

        suspend fun unmark(user: JellyfinUser): SaveResult {
            val auth = parentLockService.auth() ?: return SaveResult.NEEDS_GRANT
            return when (val result = kidsApi.deleteProfile(auth.server.url, auth.token, auth.grant, user.id)) {
                is KidsResult.Ok -> {
                    kidProfileDao.delete(user.rowId)
                    SaveResult.SAVED
                }

                KidsResult.NotAKid, KidsResult.NotFound -> {
                    if (deleteIfNotAKid(auth.server, user, result)) SaveResult.SAVED else SaveResult.FAILED
                }

                else -> {
                    failure(result)
                }
            }
        }

        /**
         * Save [profile] on the server, based on its cached revision
         */
        suspend fun save(
            user: JellyfinUser,
            profile: KidProfile,
        ): SaveResult {
            val auth = parentLockService.auth() ?: return SaveResult.NEEDS_GRANT
            val body = KidProfileMapper.toUpdate(profile, profile.revision, sendSessionLimit(auth))
            return handle(user, auth, kidsApi.putProfile(auth.server.url, auth.token, auth.grant, user.id, body))
        }

        private suspend fun sendSessionLimit(auth: KidsAuth) = pluginService.state(auth.server.id).has(KidsFeatures.SESSION_LIMIT)

        private suspend fun handle(
            user: JellyfinUser,
            auth: KidsAuth,
            result: KidsResult<JsonObject>,
        ): SaveResult =
            when (result) {
                is KidsResult.Ok -> {
                    store(user, result.value)
                    SaveResult.SAVED
                }

                is KidsResult.Conflict -> {
                    // Marking a user who already is a kid also ends here, with their current profile
                    result.current?.let { store(user, it) } ?: refresh(auth.server, user)
                    SaveResult.CONFLICT
                }

                KidsResult.NotAKid, KidsResult.NotFound -> {
                    // No longer a kid, if the plugin says so
                    if (deleteIfNotAKid(auth.server, user, result)) SaveResult.CONFLICT else SaveResult.FAILED
                }

                else -> {
                    failure(result)
                }
            }

        private fun failure(result: KidsResult<*>): SaveResult =
            when (result) {
                KidsResult.Forbidden -> {
                    parentLockService.dropGrant()
                    SaveResult.NEEDS_GRANT
                }

                KidsResult.Unreachable -> {
                    SaveResult.UNREACHABLE
                }

                else -> {
                    Timber.w("Kids change failed: %s", result)
                    SaveResult.FAILED
                }
            }

        private suspend fun store(
            user: JellyfinUser,
            obj: JsonObject,
        ) {
            kidProfileDao.upsert(KidProfileMapper.fromServer(user.rowId, obj))
        }
    }
