package dev.aldoborrero.peke.ui.nav

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A check between a restricted profile and leaving it, such as kids mode's parent gate
 *
 * While one is provided through [LocalProfileGate], switching user from the nav drawer opens it instead, and the nav
 * drawer shows an entry for it. Back on the home page doesn't open it.
 *
 * @param title the nav drawer entry's text
 * @param icon the nav drawer entry's icon
 * @param content shows the check; once it passes it goes to `onPassed` when given, or else to the profile's own menu
 */
@Immutable
class ProfileGate(
    @param:StringRes val title: Int,
    val icon: ImageVector,
    val content: @Composable (onDismissRequest: () -> Unit, onPassed: (() -> Unit)?) -> Unit,
)

/**
 * The [ProfileGate] for the current profile, null when the profile may be left freely
 */
val LocalProfileGate = staticCompositionLocalOf<ProfileGate?> { null }
