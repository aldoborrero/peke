package dev.aldoborrero.peke.kids.data

import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.gate.KidsAuth
import dev.aldoborrero.peke.kids.gate.ParentLockService
import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsApi
import dev.aldoborrero.peke.kids.server.KidsCaller
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsResult
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
 *
 * Next to the cached profiles it remembers, for this run of the app, which users the server said are not kids
 * ([notKids]): a user without a cached profile is only a grown-up once the server said so.
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

        private val _notKids = MutableStateFlow<Set<Int>>(emptySet())

        /**
         * The users (rows) the server said in this run of the app have no kid profile: the plugin answered "not a
         * kid" or left them out of its profile list, or the server definitely has no usable Kids plugin. Not stored,
         * so after a restart a user without a cached profile waits for the server again.
         */
        val notKids: StateFlow<Set<Int>> = _notKids

        private fun answeredNotAKid(rowId: Int) = _notKids.update { it + rowId }

        /**
         * Read [user]'s profile from the server with the user's own token
         *
         * @return whether the server gave a definite answer: a profile, or that [user] isn't a kid
         */
        suspend fun refresh(
            server: JellyfinServer,
            user: JellyfinUser,
        ): Boolean {
            val token = user.accessToken ?: return false
            if (!available(server)) {
                // Without a usable plugin nobody new is a kid there, but only once the server said so in this run
                if (!pluginService.answered(server.id)) return false
                answeredNotAKid(user.rowId)
                return true
            }
            return when (val result = kidsApi.profile(server.url, KidsCaller(token, user.id), user.id)) {
                is KidsResult.Ok -> {
                    store(user, result.value)
                    true
                }

                KidsResult.NotAKid, KidsResult.NotFound -> {
                    // A cached profile kept after an unclear 404 (fail closed) still makes them a kid
                    deleteIfNotAKid(server, user, result)
                    answeredNotAKid(user.rowId)
                    true
                }

                else -> {
                    Timber.d("Keeping the cached kid profile of %s: %s", user.rowId, result)
                    false
                }
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
            answeredNotAKid(user.rowId)
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
            val users = withContext(PekeDispatchers.IO) { serverDao.getServer(server.id)?.users }.orEmpty()
            val caller =
                (listOfNotNull(preferred) + users).firstNotNullOfOrNull { u -> u.accessToken?.let { KidsCaller(it, u.id) } } ?: return
            val refs =
                when (val result = kidsApi.profiles(server.url, caller)) {
                    is KidsResult.Ok -> result.value.mapNotNull { ref -> ref.userId.toUUIDOrNull()?.let { it to ref.revision } }.toMap()
                    else -> return
                }
            users.forEach { user ->
                val revision = refs[user.id]
                if (revision == null) {
                    kidProfileDao.delete(user.rowId)
                    answeredNotAKid(user.rowId)
                } else if (kidProfileDao.get(user.rowId)?.revision != revision) {
                    refresh(server, user)
                }
            }
        }

        suspend fun mark(user: JellyfinUser): SaveResult {
            val auth = parentLockService.auth() ?: return SaveResult.NEEDS_GRANT
            val body = KidProfileMapper.toUpdate(KidProfile(userId = user.rowId), 0, sendSessionLimit(auth))
            return handle(user, auth, kidsApi.putProfile(auth.server.url, auth.caller, auth.grant, user.id, body))
        }

        suspend fun unmark(user: JellyfinUser): SaveResult {
            val auth = parentLockService.auth() ?: return SaveResult.NEEDS_GRANT
            return when (val result = kidsApi.deleteProfile(auth.server.url, auth.caller, auth.grant, user.id)) {
                is KidsResult.Ok -> {
                    kidProfileDao.delete(user.rowId)
                    answeredNotAKid(user.rowId)
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
            return handle(user, auth, kidsApi.putProfile(auth.server.url, auth.caller, auth.grant, user.id, body))
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
            kidProfileDao.upsert(KidProfileMapper.fromServer(user.rowId, user.serverId, obj))
            _notKids.update { it - user.rowId }
        }
    }
