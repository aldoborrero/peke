package dev.aldoborrero.peke.kids.setup

import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser

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
