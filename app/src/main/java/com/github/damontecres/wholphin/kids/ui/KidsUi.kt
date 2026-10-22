package com.github.damontecres.wholphin.kids.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.kids.LocalKidsLiveTv
import com.github.damontecres.wholphin.kids.LocalKidsMode
import com.github.damontecres.wholphin.kids.data.KidProfile
import com.github.damontecres.wholphin.kids.profile
import com.github.damontecres.wholphin.preferences.DisplayToggle
import com.github.damontecres.wholphin.ui.nav.LocalProfileGate
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.ProfileGate
import com.github.damontecres.wholphin.ui.util.InterfaceCustomization
import com.github.damontecres.wholphin.ui.util.LocalInterfaceCustomization
import com.github.damontecres.wholphin.ui.util.RestrictedFeature
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
        content = { onDismissRequest -> KidsParentGate(onDismissRequest = onDismissRequest) },
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
