package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The first screen of a launch (`contracts/tv-flows.md`)
 */
sealed interface LaunchDestination {
    /**
     * Peke's starting-up page, until [TvState] is known
     */
    data object Starting : LaunchDestination

    /**
     * Setup at [step]. The administrator session is never stored, so past [TvSetupStep.ADMIN] it signs in again first.
     */
    data class Setup(
        val step: TvSetupStep,
        val signInAdminFirst: Boolean,
    ) : LaunchDestination

    /**
     * "This TV has no kids", with only Grown-ups
     */
    data class NoKids(
        val server: JellyfinServer,
    ) : LaunchDestination

    /**
     * The only kid's home, or their stop screen
     */
    data class Kid(
        val server: JellyfinServer,
        val kid: JellyfinUser,
    ) : LaunchDestination

    /**
     * The kids picker
     */
    data class Picker(
        val server: JellyfinServer,
        val kids: List<JellyfinUser>,
    ) : LaunchDestination
}

/**
 * Where every cold start, warm start and screensaver exit goes (spec FR-008): a set-up TV always opens on the kids
 * side, with the only kid or the picker, and never on a list of users or servers
 */
object LaunchRule {
    fun destination(state: TvState): LaunchDestination =
        when (state) {
            TvState.Loading -> {
                LaunchDestination.Starting
            }

            TvState.NotSetUp -> {
                LaunchDestination.Setup(TvSetupStep.SERVER, signInAdminFirst = false)
            }

            is TvState.InSetup -> {
                LaunchDestination.Setup(state.step, signInAdminFirst = state.step > TvSetupStep.ADMIN)
            }

            is TvState.SetUp -> {
                when (state.kids.size) {
                    0 -> LaunchDestination.NoKids(state.server)
                    1 -> LaunchDestination.Kid(state.server, state.kids.single())
                    else -> LaunchDestination.Picker(state.server, state.kids)
                }
            }
        }
}

/**
 * Shows the first screen [LaunchRule] picks, on every start and wherever the kids side starts over, such as after
 * setup or when the picker is closed
 */
@Singleton
class TvLauncher
    @Inject
    constructor(
        private val tvSetupRepository: TvSetupRepository,
        private val serverRepository: ServerRepository,
        private val setupNavigationManager: SetupNavigationManager,
        private val navigationManager: NavigationManager,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        /**
         * Go to the first screen for this TV's state, waiting, from local data only, for a state [ready] accepts
         */
        suspend fun launch(ready: (TvState) -> Boolean = { it != TvState.Loading }) {
            val state =
                withTimeoutOrNull(TV_STATE_TIMEOUT_MILLIS) { tvSetupRepository.state.first(ready) }
                    ?: tvSetupRepository.state.value
            val destination = LaunchRule.destination(state)
            Timber.d("Launch destination: %s", destination)
            // A setup in progress keeps its screen, and the administrator session it holds, across a warm start
            if (destination is LaunchDestination.Setup && setupNavigationManager.backStack.firstOrNull() is SetupDestination.Setup) return
            setupNavigationManager.navigateTo(screen(destination))
        }

        private suspend fun screen(destination: LaunchDestination): SetupDestination =
            when (destination) {
                LaunchDestination.Starting -> {
                    SetupDestination.Loading
                }

                is LaunchDestination.Setup -> {
                    SetupDestination.Setup(destination.step)
                }

                is LaunchDestination.NoKids -> {
                    SetupDestination.NoKids
                }

                // A kid who needs a grown-up shows so on the picker
                is LaunchDestination.Kid -> {
                    if (destination.kid.accessToken == null) SetupDestination.KidsPicker else open(destination.server, destination.kid)
                }

                is LaunchDestination.Picker -> {
                    SetupDestination.KidsPicker
                }
            }

        /**
         * Make [kid] current and show their home, or their stop screen when they are stopped
         */
        suspend fun openKid(
            server: JellyfinServer,
            kid: JellyfinUser,
        ) {
            setupNavigationManager.navigateTo(open(server, kid))
        }

        /**
         * [kid] as the current user, from the stored row so it opens without the server; the user is refreshed in the
         * background. A kid who already is current keeps their session.
         */
        private suspend fun open(
            server: JellyfinServer,
            kid: JellyfinUser,
        ): SetupDestination.AppContent {
            val current = serverRepository.current.value
            if (current != null && current.server.id == server.id && current.user.rowId == kid.rowId &&
                current.user.accessToken == kid.accessToken
            ) {
                return SetupDestination.AppContent(current)
            }
            // Another kid's pages don't carry over
            if (current != null) navigationManager.reloadHome()
            val restored = serverRepository.restoreUser(server, kid)
            scope.launch {
                try {
                    serverRepository.updateUserDto()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // Offline: the kid stays with their last known rules
                    Timber.w(ex, "Could not refresh the user")
                }
            }
            return SetupDestination.AppContent(restored)
        }

        companion object {
            /**
             * How long a launch waits for the TV's state, from local data only, before showing the starting page
             */
            private const val TV_STATE_TIMEOUT_MILLIS = 5_000L
        }
    }
