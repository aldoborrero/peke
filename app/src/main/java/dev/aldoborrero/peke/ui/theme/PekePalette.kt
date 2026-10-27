package dev.aldoborrero.peke.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.aldoborrero.peke.preferences.AppThemeColors

/**
 * One of Peke's themes: the colour roles of design/DESIGN.md. Screens read them through [PekeTheme]'s colour scheme,
 * and the roles a colour scheme has no slot for through [LocalPekeColors].
 *
 * @param secondary live, TV and toggles that are on
 * @param time time left, stars and code dots
 * @param tertiary extras and posters
 * @param outline the mascot's and icons' outline
 * @param focusBorder the border around the focused item, inside the [primary] ring
 * @param isLight a light theme (Papel), for the daytime
 */
@Immutable
data class PekePalette(
    val name: String,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val time: Color,
    val tertiary: Color,
    val text: Color,
    val textSecondary: Color,
    val outline: Color,
    val focusBorder: Color,
    val isLight: Boolean = false,
)

/**
 * The default theme, after the PekeTV channel logo
 */
val PekeTvPalette =
    PekePalette(
        name = "PekeTV",
        background = Color(0xFF1F2747),
        surface = Color(0xFF28335B),
        primary = Color(0xFFFF927E),
        onPrimary = Color(0xFF1F2747),
        secondary = Color(0xFF65D9CE),
        time = Color(0xFFFFDA70),
        tertiary = Color(0xFFB5A0EF),
        text = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFC8CDE6),
        outline = Color(0xFF1F2747),
        focusBorder = Color(0xFFFFFFFF),
    )

val MandarinaPalette =
    PekePalette(
        name = "Mandarina",
        background = Color(0xFF12202B),
        surface = Color(0xFF1D3340),
        primary = Color(0xFFFF8A3D),
        onPrimary = Color(0xFF12202B),
        secondary = Color(0xFF80E2D8),
        time = Color(0xFFFFD23F),
        tertiary = Color(0xFFFF6F91),
        text = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB9C7D0),
        outline = Color(0xFF12202B),
        focusBorder = Color(0xFFFFFFFF),
    )

val CarameloPalette =
    PekePalette(
        name = "Caramelo",
        background = Color(0xFF1E1B18),
        surface = Color(0xFF2E2925),
        primary = Color(0xFFFF6B6B),
        onPrimary = Color(0xFF1E1B18),
        secondary = Color(0xFF5BD6A2),
        time = Color(0xFFFFC93C),
        tertiary = Color(0xFF8EC5FF),
        text = Color(0xFFFFF7EC),
        textSecondary = Color(0xFFCFC3B5),
        outline = Color(0xFF1E1B18),
        focusBorder = Color(0xFFFFFFFF),
    )

/**
 * The daytime theme; too bright for a dark room, so never the default. Its focus border is dark, since white
 * disappears on cream and white.
 */
val PapelPalette =
    PekePalette(
        name = "Papel",
        background = Color(0xFFFFF6E6),
        surface = Color(0xFFFFFFFF),
        primary = Color(0xFFC63F1A),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF1B5E31),
        time = Color(0xFF9A6200),
        tertiary = Color(0xFF2B7BC4),
        text = Color(0xFF1E2A33),
        textSecondary = Color(0xFF4F5B64),
        outline = Color(0xFF1E2A33),
        focusBorder = Color(0xFF1E2A33),
        isLight = true,
    )

/**
 * The palette for a stored theme number: Peke's four themes, and PekeTV for anything else (Wholphin's themes, nothing
 * stored, or a theme from a newer Peke)
 */
fun pekePalette(storedNumber: Int): PekePalette =
    when (storedNumber) {
        AppThemeColors.PEKE_TV_VALUE -> PekeTvPalette
        AppThemeColors.MANDARINA_VALUE -> MandarinaPalette
        AppThemeColors.CARAMELO_VALUE -> CarameloPalette
        AppThemeColors.PAPEL_VALUE -> PapelPalette
        else -> PekeTvPalette
    }

fun AppThemeColors.pekePalette(): PekePalette = if (this == AppThemeColors.UNRECOGNIZED) PekeTvPalette else pekePalette(number)

/**
 * The current theme's [PekePalette], for the roles the colour scheme has no slot for: [PekePalette.time],
 * [PekePalette.outline], [PekePalette.focusBorder] and [PekePalette.textSecondary]
 */
val LocalPekeColors = staticCompositionLocalOf { PekeTvPalette }
