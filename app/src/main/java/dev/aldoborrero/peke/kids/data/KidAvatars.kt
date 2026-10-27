package dev.aldoborrero.peke.kids.data

import dev.aldoborrero.peke.kids.server.KidProfileMapper
import dev.aldoborrero.peke.kids.server.KidsFeatures
import dev.aldoborrero.peke.kids.server.KidsServerDao
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The avatars a grown-up picked for the kids in the Kids plugin's dashboard (plugin 0.0.8.0, feature `avatar`), from
 * the cached profiles, so the picker and the sidebar show them without the server
 */
@Singleton
class KidAvatars
    @Inject
    constructor(
        private val kidProfileDao: KidProfileDao,
        private val kidsServerDao: KidsServerDao,
    ) {
        /**
         * The avatar of each kid of [serverId] that has one, by device user row id. Empty while the plugin, as last
         * cached, doesn't offer avatars.
         */
        fun observe(serverId: UUID): Flow<Map<Int, PekeAvatar>> =
            combine(kidProfileDao.observeAll(), kidsServerDao.observe(serverId)) { profiles, server ->
                if (server?.has(KidsFeatures.AVATAR) != true) return@combine emptyMap()
                profiles
                    .filter { it.serverId == serverId }
                    .mapNotNull { profile -> PekeAvatar.of(KidProfileMapper.avatarId(profile))?.let { profile.userId to it } }
                    .toMap()
            }
    }
