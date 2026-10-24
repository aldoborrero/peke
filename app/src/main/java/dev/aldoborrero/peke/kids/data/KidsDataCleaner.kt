package dev.aldoborrero.peke.kids.data

import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.kids.server.KidsServerDao
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the kids database in step with the app database's users and servers, which the separate database can't
 * reference with foreign keys
 */
@Singleton
class KidsDataCleaner
    @Inject
    constructor(
        private val serverDao: JellyfinServerDao,
        private val kidProfileDao: KidProfileDao,
        private val kidsServerDao: KidsServerDao,
    ) {
        suspend fun onUserRemoved(userRowId: Int) {
            kidProfileDao.delete(userRowId)
        }

        suspend fun onServerRemoved(serverId: UUID) {
            kidProfileDao.deleteForServer(serverId)
            kidsServerDao.delete(serverId)
        }

        /**
         * Removes every kid profile and every kids server, with its cached lock and verifier, for a reset of this TV.
         * Also the rows of servers the app database no longer has.
         */
        suspend fun eraseAll() {
            kidProfileDao.allUserIds().forEach { kidProfileDao.delete(it) }
            kidsServerDao.allServerIds().forEach { kidsServerDao.delete(it) }
        }

        /**
         * Removes kids rows whose user or server no longer exists, such as after a crash between the two databases'
         * deletes
         */
        suspend fun sweep() {
            val servers = withContext(PekeDispatchers.IO) { serverDao.getServers() }
            val serverIds = servers.map { it.server.id }.toSet()
            val userRowIds = servers.flatMap { it.users }.map { it.rowId }.toSet()
            val orphanKids = kidProfileDao.allUserIds().filter { it !in userRowIds }
            val orphanServers = kidsServerDao.allServerIds().filter { it !in serverIds }
            orphanKids.forEach { kidProfileDao.delete(it) }
            orphanServers.forEach { kidsServerDao.delete(it) }
            if (orphanKids.isNotEmpty() || orphanServers.isNotEmpty()) {
                Timber.i("Removed %d orphan kid profiles and %d orphan kids servers", orphanKids.size, orphanServers.size)
            }
        }
    }
