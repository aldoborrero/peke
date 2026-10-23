package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.data.model.HomePageSettings
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.ui.main.settings.Library
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Lets a kind of profile, such as kids mode's kid profiles, change how shared services treat a user
 *
 * [None] changes nothing.
 */
interface ProfilePolicy {
    /**
     * A fixed home for [user] instead of their own home settings, or null to use those
     *
     * @param libraries the user's libraries, only read when needed
     */
    suspend fun homeSettings(
        user: JellyfinUser,
        libraries: suspend () -> List<Library>,
    ): HomePageSettings?

    /**
     * Emits whenever the current user's fixed home changes after it was first built, so it is built again
     */
    val homeChanges: Flow<Unit>

    /**
     * Whether the profile settings entry is shown for the current server
     */
    val settingsAvailable: Flow<Boolean>

    /**
     * The only live TV channels [user] may see in the guide, or null for no limit
     */
    suspend fun liveTvChannelIds(user: JellyfinUser): Set<UUID>?

    object None : ProfilePolicy {
        override suspend fun homeSettings(
            user: JellyfinUser,
            libraries: suspend () -> List<Library>,
        ): HomePageSettings? = null

        override val homeChanges: Flow<Unit> = emptyFlow()

        override val settingsAvailable: Flow<Boolean> = flowOf(false)

        override suspend fun liveTvChannelIds(user: JellyfinUser): Set<UUID>? = null
    }
}
