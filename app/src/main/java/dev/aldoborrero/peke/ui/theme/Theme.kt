package dev.aldoborrero.peke.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import dev.aldoborrero.peke.preferences.AppThemeColors

val LocalTheme =
    compositionLocalOf<AppThemeColors> { AppThemeColors.PEKE_TV }

// Material's error roles: DESIGN has none, and errors for kids don't use them
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)

/**
 * The tv-material colour scheme of [palette]: each slot reads a DESIGN role
 */
fun tvColorScheme(palette: PekePalette): ColorScheme =
    with(palette) {
        val error = if (isLight) ErrorLight else ErrorDark
        val onError = if (isLight) OnErrorLight else OnErrorDark
        (if (isLight) lightColorScheme() else darkColorScheme()).copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary,
            onPrimaryContainer = onPrimary,
            inversePrimary = primary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = secondary,
            onSecondaryContainer = onPrimary,
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = tertiary,
            onTertiaryContainer = onPrimary,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surface,
            onSurfaceVariant = textSecondary,
            surfaceTint = primary,
            inverseSurface = text,
            inverseOnSurface = background,
            error = error,
            onError = onError,
            errorContainer = error,
            onErrorContainer = onError,
            border = focusBorder,
            borderVariant = textSecondary,
            scrim = Color.Black,
        )
    }

/**
 * The Material 3 colour scheme of [palette], for the Material 3 components Peke still uses
 */
fun materialColorScheme(palette: PekePalette): androidx.compose.material3.ColorScheme =
    with(palette) {
        val error = if (isLight) ErrorLight else ErrorDark
        val onError = if (isLight) OnErrorLight else OnErrorDark
        val base =
            if (isLight) androidx.compose.material3.lightColorScheme() else androidx.compose.material3.darkColorScheme()
        base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary,
            onPrimaryContainer = onPrimary,
            inversePrimary = primary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = secondary,
            onSecondaryContainer = onPrimary,
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = tertiary,
            onTertiaryContainer = onPrimary,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surface,
            onSurfaceVariant = textSecondary,
            surfaceTint = primary,
            inverseSurface = text,
            inverseOnSurface = background,
            error = error,
            onError = onError,
            errorContainer = error,
            onErrorContainer = onError,
            outline = textSecondary,
            outlineVariant = surface,
            scrim = Color.Black,
            surfaceBright = surface,
            surfaceDim = background,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = surface,
            surfaceContainerLow = surface,
            surfaceContainerLowest = background,
        )
    }

/**
 * Peke's look: the stored theme's colours (PekeTV for anything Peke doesn't know), Nunito and the rounded shapes
 */
@Composable
fun PekeTheme(
    appThemeColors: AppThemeColors = AppThemeColors.PEKE_TV,
    content: @Composable () -> Unit,
) {
    val palette = appThemeColors.pekePalette()
    val tvScheme = remember(palette) { tvColorScheme(palette) }
    val materialScheme = remember(palette) { materialColorScheme(palette) }
    CompositionLocalProvider(
        LocalTheme provides appThemeColors,
        LocalPekeColors provides palette,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = materialScheme,
            typography = AppMaterialTypography,
        ) {
            MaterialTheme(
                colorScheme = tvScheme,
                shapes = AppShapes,
                typography = AppTypography,
                content = content,
            )
        }
    }
}
