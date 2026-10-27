package dev.aldoborrero.peke.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import dev.aldoborrero.peke.R

/**
 * Nunito (SIL Open Font License 1.1), bundled at the three weights Peke uses. A lighter weight asked for anywhere
 * draws as Bold, the closest bundled one.
 */
val NunitoFamily =
    FontFamily(
        Font(R.font.nunito_bold, FontWeight.W700),
        Font(R.font.nunito_extrabold, FontWeight.W800),
        Font(R.font.nunito_black, FontWeight.W900),
    )

/**
 * A style from design/DESIGN.md's type table, given in canvas pixels: 1 px is 0.75 sp on a TV
 */
private fun style(
    px: Float,
    weight: FontWeight,
) = TextStyle(
    fontFamily = NunitoFamily,
    fontWeight = weight,
    fontSize = (px * 0.75f).sp,
    lineHeight = (px * 0.75f * 1.25f).sp,
)

/**
 * DESIGN's styles where it names them (Display, Title, Headline, Section, Body, Supporting, Small), the nearest one
 * for the others, and nothing under Small's 15 px
 */
val AppTypography =
    Typography(
        displayLarge = style(56f, FontWeight.W900),
        displayMedium = style(48f, FontWeight.W900),
        displaySmall = style(44f, FontWeight.W900),
        headlineLarge = style(48f, FontWeight.W900),
        headlineMedium = style(36f, FontWeight.W900),
        headlineSmall = style(32f, FontWeight.W900),
        titleLarge = style(24f, FontWeight.W900),
        titleMedium = style(22f, FontWeight.W800),
        titleSmall = style(20f, FontWeight.W800),
        bodyLarge = style(22f, FontWeight.W800),
        bodyMedium = style(18f, FontWeight.W800),
        bodySmall = style(16f, FontWeight.W800),
        labelLarge = style(20f, FontWeight.W800),
        labelMedium = style(16f, FontWeight.W800),
        labelSmall = style(15f, FontWeight.W800),
    )

/**
 * [AppTypography] for the Material 3 components Peke still uses
 */
val AppMaterialTypography =
    with(AppTypography) {
        androidx.compose.material3.Typography(
            displayLarge = displayLarge,
            displayMedium = displayMedium,
            displaySmall = displaySmall,
            headlineLarge = headlineLarge,
            headlineMedium = headlineMedium,
            headlineSmall = headlineSmall,
            titleLarge = titleLarge,
            titleMedium = titleMedium,
            titleSmall = titleSmall,
            bodyLarge = bodyLarge,
            bodyMedium = bodyMedium,
            bodySmall = bodySmall,
            labelLarge = labelLarge,
            labelMedium = labelMedium,
            labelSmall = labelSmall,
        )
    }
