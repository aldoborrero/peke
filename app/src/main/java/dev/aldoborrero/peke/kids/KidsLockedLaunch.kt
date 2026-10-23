package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.RestoredSession
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.gate.mostRecentKidServer
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where an app launch goes while the TV is locked to kids mode and the last session didn't bring a user back: to the
 * kids side, never to the server list. That is the only kid on this TV, signed in again if they have a saved token
 * that still works and their profile doesn't require a login, or else the kids' user list of the server whose kid
 * was used most recently, which only lists kids while locked.
 */
@Singleton
class KidsLockedLaunch
    @Inject
    constructor(
        private val kidsModeService: KidsModeService,
        private val serverRepository: ServerRepository,
        private val serverDao: JellyfinServerDao,
    ) {
        /**
         * @param restored the restored session, null when restoring it failed
         * @param signInAutomatically whether a kid may be signed in without being picked
         * @return where to go instead of [restored]'s destination, null to keep it
         */
        suspend fun destination(
            restored: RestoredSession?,
            signInAutomatically: Boolean,
        ): SetupDestination? {
            if (restored is RestoredSession.Success || !kidsModeService.isLockedNow()) return null
            val kidIds = kidsModeService.kidUserRowIds()
            val servers = withContext(PekeDispatchers.IO) { serverDao.getServers() }
            val kidServer = mostRecentKidServer(servers, kidIds) ?: return null
            val onlyKid = servers.flatMap { s -> s.users.filter { it.rowId in kidIds } }.singleOrNull()
            // Only a saved session that may be used without signing in again; anything else needs the list
            if (signInAutomatically && onlyKid != null && onlyKid.accessToken != null && !onlyKid.requireLogin) {
                val current =
                    try {
                        serverRepository.tryChangeUser(onlyKid.serverId, onlyKid.id)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not sign the kid in again")
                        null
                    }
                if (current != null) return SetupDestination.AppContent(current)
            }
            return SetupDestination.UserList(kidServer.server)
        }
    }
