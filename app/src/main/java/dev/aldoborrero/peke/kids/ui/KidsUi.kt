package dev.aldoborrero.peke.kids.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.LocalKidsLiveTv
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.kids.profile
import dev.aldoborrero.peke.preferences.DisplayToggle
import dev.aldoborrero.peke.ui.nav.LocalProfileGate
import dev.aldoborrero.peke.ui.nav.NavDrawerItem
import dev.aldoborrero.peke.ui.nav.ProfileGate
import dev.aldoborrero.peke.ui.util.InterfaceCustomization
import dev.aldoborrero.peke.ui.util.LocalInterfaceCustomization
import dev.aldoborrero.peke.ui.util.RestrictedFeature
import java.util.EnumSet

/**
 * Kids mode shows these in the nav drawer instead of the libraries: favourites, and the live TV guide when the kid has
 * channels (with none, there is no entry at all)
 */
fun kidsNavDrawerItems(liveTv: Boolean): List<NavDrawerItem> =
    if (liveTv) listOf(NavDrawerItem.Favorites, NavDrawerItem.LiveTv) else listOf(NavDrawerItem.Favorites)

/**
 * Kids mode hides ratings, which mean nothing to young children, and every [RestrictedFeature] except search when
 * [KidProfile.searchAllowed]
 */
fun kidsInterfaceCustomization(
    profile: KidProfile,
    liveTv: Boolean = false,
): InterfaceCustomization =
    InterfaceCustomization(
        enabledDisplayToggles = EnumSet.noneOf(DisplayToggle::class.java),
        restrictedFeatures =
            EnumSet.allOf(RestrictedFeature::class.java).apply {
                if (profile.searchAllowed) remove(RestrictedFeature.SEARCH)
            },
        navDrawerItems = kidsNavDrawerItems(liveTv),
    )

/**
 * Leaving a kid profile, or the app, needs a grown-up
 */
private val KidsProfileGate =
    ProfileGate(
        title = R.string.kids_grown_ups,
        icon = Icons.Default.Lock,
        content = { onDismissRequest, onPassed -> KidsParentGate(onDismissRequest = onDismissRequest, onPassed = onPassed) },
    )

/**
 * While a kid profile is current, replaces [LocalInterfaceCustomization] with kids mode's and provides the parent gate
 * as [LocalProfileGate]
 */
@Composable
fun ProvideKidsUi(content: @Composable () -> Unit) {
    val profile = LocalKidsMode.current.profile
    val customization = LocalInterfaceCustomization.current
    val searchAllowed = profile?.searchAllowed
    val liveTv = LocalKidsLiveTv.current
    val kidsCustomization =
        remember(profile != null, searchAllowed, liveTv, customization) {
            profile?.let { kidsInterfaceCustomization(it, liveTv) } ?: customization
        }
    CompositionLocalProvider(
        LocalInterfaceCustomization provides kidsCustomization,
        LocalProfileGate provides if (profile != null) KidsProfileGate else null,
        content = content,
    )
}
