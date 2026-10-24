package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.data.KidProfileRepository
import dev.aldoborrero.peke.kids.ui.isGrownUp
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.services.hilt.DefaultCoroutineScope
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                LaunchDestination.Setup(TvSetupStep.SERVER)
            }

            is TvState.InSetup -> {
                LaunchDestination.Setup(state.step)
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
 *
 * It also knows whose pages the kids side's back stack ([NavigationManager.backStack]) holds ([pagesOf]), so one kid's
 * pages never show for another (C12): opening a kid keeps their own pages and starts anyone else's from their home.
 */
@Singleton
class TvLauncher
    @Inject
    constructor(
        private val tvSetupRepository: TvSetupRepository,
        private val serverRepository: ServerRepository,
        private val serverDao: JellyfinServerDao,
        private val kidProfileRepository: KidProfileRepository,
        private val setupNavigationManager: SetupNavigationManager,
        private val navigationManager: NavigationManager,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        /**
         * The `users.rowId` of the kid whose pages [NavigationManager.backStack] holds, or null when it is nobody's yet,
         * such as a back stack restored without knowing whose it was
         */
        @Volatile
        var pagesOf: Int? = null

        /**
         * Go to the first screen for this TV's state, waiting, from local data only, for a state [ready] accepts. The
         * kids side's back stack loses its grown-up pages ([isGrownUp]) on the way: they need the parent gate again.
         */
        suspend fun launch(ready: (TvState) -> Boolean = { it != TvState.Loading }) {
            val state =
                withTimeoutOrNull(TV_STATE_TIMEOUT_MILLIS) { tvSetupRepository.state.first(ready) }
                    ?: tvSetupRepository.state.value
            withContext(PekeDispatchers.Main) { dropGrownUpPages() }
            val destination = LaunchRule.destination(state)
            Timber.d("Launch destination: %s", destination)
            if (state is TvState.SetUp) refreshKids(state.server)
            // A setup in progress keeps its screen; its administrator session, if any, ended when the app went to the
            // background, and the setup asks for a fresh sign-in
            if (destination is LaunchDestination.Setup && setupNavigationManager.backStack.firstOrNull() is SetupDestination.Setup) return
            setupNavigationManager.navigateTo(screen(destination))
        }

        /**
         * Back to the current kid's pages, for a grown-up leaving a page the parent menu opened from them; without a
         * current kid of this TV, the first screen [launch] picks
         */
        suspend fun resume() {
            val current = serverRepository.current.value
            if (current != null && tvSetupRepository.state.value.isCurrentKid(current)) {
                setupNavigationManager.navigateTo(open(current.server, current.user))
            } else {
                launch()
            }
        }

        private suspend fun screen(destination: LaunchDestination): SetupDestination =
            when (destination) {
                LaunchDestination.Starting -> {
                    SetupDestination.Loading
                }

                is LaunchDestination.Setup -> {
                    serverRepository.clearCurrent()
                    SetupDestination.Setup(destination.step)
                }

                is LaunchDestination.NoKids -> {
                    serverRepository.clearCurrent()
                    SetupDestination.NoKids
                }

                // A kid who needs a grown-up shows so on the picker
                is LaunchDestination.Kid -> {
                    open(destination.server, destination.kid)
                }

                is LaunchDestination.Picker -> {
                    serverRepository.clearCurrent()
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
         * [kid] as the current user, from their stored row so it opens without the server; the user is refreshed in
         * the background. A kid who already is current keeps their session, and their own pages. A kid whose row is gone
         * or has no token any more, which the state read a moment ago may not show yet, goes to the picker, which shows
         * them as needing a grown-up or not at all.
         */
        private suspend fun open(
            server: JellyfinServer,
            kid: JellyfinUser,
        ): SetupDestination {
            val stored = withContext(PekeDispatchers.IO) { serverDao.getServer(server.id) }?.users?.firstOrNull { it.rowId == kid.rowId }
            if (stored?.accessToken == null) {
                serverRepository.clearCurrent()
                return SetupDestination.KidsPicker
            }
            // Another kid's pages, or pages nobody knows the owner of, don't carry over
            if (pagesOf != stored.rowId) {
                withContext(PekeDispatchers.Main) { navigationManager.reloadHome() }
                pagesOf = stored.rowId
            }
            val current = serverRepository.current.value
            if (current != null && current.server.id == server.id && current.user.rowId == stored.rowId &&
                current.user.accessToken == stored.accessToken
            ) {
                return SetupDestination.AppContent(current)
            }
            val restored = serverRepository.restoreUser(server, stored)
            if (serverRepository.currentUserDto == null) {
                // No configuration of theirs is cached: wait a moment for the server's, so their home and first
                // playback don't start with the defaults
                withTimeoutOrNull(USER_CONFIG_TIMEOUT_MILLIS) { refreshUser() }
            } else {
                scope.launch { refreshUser() }
            }
            return SetupDestination.AppContent(restored)
        }

        private suspend fun refreshUser() {
            try {
                serverRepository.updateUserDto()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // Offline: the kid stays with their last known rules
                Timber.w("Could not refresh the user: %s", ex.javaClass.simpleName)
            }
        }

        /**
         * Ask the plugin, in the background, which of this TV's users are still kids: one unmarked in the dashboard
         * is signed out and goes from this TV (spec 004, edge cases)
         */
        private fun refreshKids(server: JellyfinServer) {
            scope.launch {
                try {
                    kidProfileRepository.refreshServer(server)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w("Could not refresh this TV's kids: %s", ex.javaClass.simpleName)
                }
            }
        }

        /**
         * Drop the grown-up pages from the kids side's back stack, keeping the kid's own; an empty one starts at home
         */
        private fun dropGrownUpPages() {
            val backStack = navigationManager.backStack
            if (backStack.none { it.isGrownUp() }) return
            Timber.i("Leaving the grown-up pages")
            backStack.removeAll { it.isGrownUp() }
            if (backStack.isEmpty()) backStack.add(Destination.Home())
        }

        companion object {
            /**
             * How long a launch waits for the TV's state, from local data only, before showing the starting page
             */
            private const val TV_STATE_TIMEOUT_MILLIS = 5_000L

            /**
             * How long opening a kid waits for their configuration from the server, when none is cached
             */
            private const val USER_CONFIG_TIMEOUT_MILLIS = 3_000L
        }
    }
