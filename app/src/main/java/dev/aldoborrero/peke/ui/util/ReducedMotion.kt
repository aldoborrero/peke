package dev.aldoborrero.peke.ui.util

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the TV's "Remove animations" accessibility setting is on, which sets the animator duration scale to 0.
 * Endless motion, as a pulse or a float, then stays still.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { isReducedMotion(resolver) }
}

fun isReducedMotion(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
