package dev.aldoborrero.peke.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.DisplayToggle
import dev.aldoborrero.peke.ui.nav.NavDrawerItem
import java.util.EnumSet

/**
 * Features that can be turned off for a restricted profile, such as a kids mode profile
 */
enum class RestrictedFeature {
    /** Context menus and More buttons on items */
    ITEM_MENUS,

    /** Deleting media */
    DELETE,

    /** Reordering playlists */
    EDIT_PLAYLISTS,

    /** "View more" at the end of home rows */
    VIEW_MORE,

    /** Debug info, playback speed and subtitle downloads in the player */
    ADVANCED_PLAYBACK,

    /** The settings entry in the nav drawer */
    SETTINGS,

    /** The now playing entry in the nav drawer */
    NOW_PLAYING,

    /** The search entry in the nav drawer */
    SEARCH,

    /** The server's libraries in the nav drawer, which then shows [InterfaceCustomization.navDrawerItems] instead */
    LIBRARIES,

    /** Live TV recordings: record buttons, series recordings, the DVR schedule (timers) and recording folders */
    LIVE_TV_RECORDINGS,
}

/**
 * Represents various UI preferences made available via [LocalInterfaceCustomization]
 *
 * @param restrictedFeatures features hidden for the current profile
 * @param navDrawerItems the nav drawer items shown instead of the libraries when [RestrictedFeature.LIBRARIES] is
 * restricted
 */
data class InterfaceCustomization(
    val enabledDisplayToggles: EnumSet<DisplayToggle>,
    val restrictedFeatures: Set<RestrictedFeature> = emptySet(),
    val navDrawerItems: List<NavDrawerItem> = emptyList(),
) {
    constructor(prefs: AppPreferences) : this(
        prefs.interfacePreferences.displayTogglesList.let {
            if (it.isEmpty()) {
                EnumSet.noneOf(DisplayToggle::class.java)
            } else {
                EnumSet.copyOf(it)
            }
        },
    )

    fun allows(feature: RestrictedFeature): Boolean = feature !in restrictedFeatures
}

val LocalInterfaceCustomization =
    staticCompositionLocalOf<InterfaceCustomization> {
        InterfaceCustomization(EnumSet.allOf(DisplayToggle::class.java))
    }
