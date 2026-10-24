package dev.aldoborrero.peke.services

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavKey
import dev.aldoborrero.peke.data.CurrentUser
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.setup.TvSetupStep
import kotlinx.serialization.Serializable
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating for setup
 */
@Singleton
class SetupNavigationManager
    @Inject
    constructor(
        private val navigationManager: NavigationManager,
    ) {
        var backStack: MutableList<SetupDestination> = mutableStateListOf(SetupDestination.Loading)

        /**
         * Go to the specified [SetupDestination]
         */
        fun navigateTo(destination: SetupDestination) {
            backStack[0] = destination
            log()
        }

        private fun log() {
            val dest = backStack.lastOrNull().toString()
            Timber.i("Current setup destination: %s", dest)
            ACRA.errorReporter.putCustomData("setupDestination", dest)
        }
    }

@Serializable
sealed interface SetupDestination : NavKey {
    @Serializable
    data object Loading : SetupDestination

    @Serializable
    data object ServerList : SetupDestination

    @Serializable
    data class UserList(
        val server: JellyfinServer,
    ) : SetupDestination

    @Serializable
    data class AppContent(
        val current: CurrentUser,
    ) : SetupDestination

    /**
     * Setting this TV up, from [step] (spec 004 US1). Past the server step the administrator signs in first, since
     * that session is never stored.
     */
    @Serializable
    data class Setup(
        val step: TvSetupStep = TvSetupStep.SERVER,
    ) : SetupDestination

    /**
     * Signing [kid], whose session no longer works, in again: the administrator signs in, then only that kid
     * (spec FR-019). Only after the parent gate.
     */
    @Serializable
    data class SignInAgain(
        val server: JellyfinServer,
        val kid: JellyfinUser,
    ) : SetupDestination

    /**
     * "Who's watching?": this TV's kids, and Grown-ups (spec FR-009)
     */
    @Serializable
    data object KidsPicker : SetupDestination

    /**
     * "This TV has no kids", with only Grown-ups
     */
    @Serializable
    data object NoKids : SetupDestination
}
