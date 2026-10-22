package com.github.damontecres.wholphin.kids

import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.kids.home.KidsHomeRows
import com.github.damontecres.wholphin.kids.livetv.KidsLiveTvService
import com.github.damontecres.wholphin.kids.server.KidsPluginService
import com.github.damontecres.wholphin.kids.server.KidsServerStatus
import com.github.damontecres.wholphin.services.ProfilePolicy
import com.github.damontecres.wholphin.ui.main.settings.Library
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kid profiles get the fixed kids home, never use Seerr, and kids settings are offered where the server has the Kids
 * plugin
 */
@Singleton
class KidsProfilePolicy
    @Inject
    constructor(
        private val kidsModeService: KidsModeService,
        private val pluginService: KidsPluginService,
        private val liveTvService: KidsLiveTvService,
    ) : ProfilePolicy {
        override suspend fun homeSettings(
            user: JellyfinUser,
            libraries: suspend () -> List<Library>,
        ): HomePageSettings? {
            val profile = kidsModeService.profileFor(user) ?: return null
            val liveTv = liveTvService.channelsFor(user, profile).isNotEmpty()
            return KidsHomeRows.build(profile, libraries(), liveTv)
        }

        // A kid's home is built from their profile, which can arrive after the user switch or change on the server (a
        // new revision), and from whether they have live TV channels, which are read after the profile
        override val homeChanges: Flow<Unit> =
            combine(
                kidsModeService.state
                    .filter { it != KidsModeState.Loading }
                    .map { it.profile?.let { profile -> profile.userId to profile.revision } },
                liveTvService.available,
            ) { profile, liveTv -> profile to liveTv }
                .distinctUntilChanged()
                .drop(1)
                .map { }

        override suspend fun allowsDiscover(user: JellyfinUser): Boolean = kidsModeService.profileFor(user) == null

        // An outdated plugin still shows the entry, which says to update it
        override val settingsAvailable: Flow<Boolean> =
            pluginService.current.map { it.status == KidsServerStatus.AVAILABLE || it.outdated }

        // The guide shows only the kid's effective channels, as the plugin lists them
        override suspend fun liveTvChannelIds(user: JellyfinUser): Set<UUID>? {
            val profile = kidsModeService.profileFor(user) ?: return null
            return liveTvService.channelsFor(user, profile).map { it.id }.toSet()
        }
    }
