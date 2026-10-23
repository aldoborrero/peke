package dev.aldoborrero.peke.kids.ui

import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption

/**
 * What a signed-in user who is not a kid gets for a destination: Peke is for kids, so grown-ups only set the TV up
 */
sealed interface GrownUpRoute {
    /**
     * Not known yet whether the device is locked to kids, or whether the user is a kid
     */
    data object Loading : GrownUpRoute

    /**
     * The device is locked to kids, so a non-kid here is unexpected (for example a kid whose cached profile was lost):
     * back to the kids' user list, where only the parent gate leads out
     */
    data object KidsUserList : GrownUpRoute

    /**
     * The server couldn't say whether the user is a kid and the device isn't locked: not the grown-up shell, but a
     * page to ask again
     */
    data object Unanswered : GrownUpRoute

    data object Shell : GrownUpRoute

    data class Show(
        val destination: Destination,
    ) : GrownUpRoute
}

private val settingsScreens =
    setOf(
        PreferenceScreenOption.PEKE,
        PreferenceScreenOption.EXO_PLAYER,
        PreferenceScreenOption.MPV,
        PreferenceScreenOption.HDR_FORMATS,
        PreferenceScreenOption.SCREENSAVER,
        PreferenceScreenOption.SKIP_SEGMENTS,
        PreferenceScreenOption.EXPERIMENTAL,
    )

/**
 * Only for a user the server said is not a kid ([dev.aldoborrero.peke.kids.KidsModeState.Inactive])
 *
 * @param lockEngaged whether the device's kids lock is engaged, null until known
 */
fun grownUpRoute(
    destination: Destination,
    lockEngaged: Boolean?,
): GrownUpRoute =
    when (lockEngaged) {
        null -> {
            GrownUpRoute.Loading
        }

        true -> {
            GrownUpRoute.KidsUserList
        }

        false -> {
            when (destination) {
                Destination.KidsSettings,
                is Destination.SubtitleSettings,
                Destination.License,
                Destination.Debug,
                Destination.UpdateApp,
                -> {
                    GrownUpRoute.Show(destination)
                }

                is Destination.Settings -> {
                    if (destination.screen in settingsScreens) GrownUpRoute.Show(destination) else GrownUpRoute.Shell
                }

                else -> {
                    GrownUpRoute.Shell
                }
            }
        }
    }

/**
 * What a user gets while it isn't known whether they are a kid ([dev.aldoborrero.peke.kids.KidsModeState.Loading]):
 * they have no cached kid profile, and the server hasn't said they aren't one. Never the grown-up shell.
 *
 * @param unanswered whether asking the server got no definite answer
 * ([dev.aldoborrero.peke.kids.KidsModeService.unanswered])
 * @param lockEngaged whether the device's kids lock is engaged, null until known
 */
fun unknownUserRoute(
    unanswered: Boolean,
    lockEngaged: Boolean?,
): GrownUpRoute =
    when {
        !unanswered || lockEngaged == null -> GrownUpRoute.Loading
        lockEngaged -> GrownUpRoute.KidsUserList
        else -> GrownUpRoute.Unanswered
    }
