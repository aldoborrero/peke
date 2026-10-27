package dev.aldoborrero.peke.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The palettes against design/DESIGN.md: its role values, and its contrast table with the minimums (spec 007 FR-005)
 */
class ContrastTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /**
     * DESIGN's value for [fg] on the background and on surfaces, each checked to ±0.01 and against [minimum]
     */
    private fun PekePalette.check(
        name: String,
        fg: Color,
        minimum: Double,
        onBackground: Double,
        onSurface: Double,
    ) {
        val bg = contrast(fg, background)
        val sf = contrast(fg, surface)
        assertEquals("$name on background", onBackground, bg, 0.01)
        assertEquals("$name on surface", onSurface, sf, 0.01)
        assertTrue("$name on background ≥ $minimum", bg >= minimum)
        assertTrue("$name on surface ≥ $minimum", sf >= minimum)
    }

    private data class Row(
        val text: Pair<Double, Double>,
        val textSecondary: Pair<Double, Double>,
        val time: Pair<Double, Double>,
        val onPrimary: Double,
        val primary: Pair<Double, Double>,
        val secondary: Pair<Double, Double>,
        val tertiary: Pair<Double, Double>,
        val focusBorder: Pair<Double, Double>,
        val primaryVsSecondary: Double,
        val lightnessMinimum: Double,
    )

    // DESIGN.md, "Contrast ratios (WCAG 2), each colour on the background / on surfaces"
    private val table =
        mapOf(
            PekeTvPalette to
                Row(
                    14.59 to 12.25,
                    9.26 to 7.77,
                    10.78 to 9.05,
                    6.71,
                    6.71 to 5.64,
                    8.59 to 7.21,
                    6.42 to 5.39,
                    14.59 to 12.25,
                    1.28,
                    1.28,
                ),
            MandarinaPalette to
                Row(
                    16.57 to 13.13,
                    9.58 to 7.59,
                    11.48 to 9.09,
                    7.07,
                    7.07 to 5.60,
                    10.88 to 8.62,
                    6.26 to 4.96,
                    16.57 to 13.13,
                    1.54,
                    1.5,
                ),
            CarameloPalette to
                Row(
                    16.13 to 13.54,
                    9.90 to 8.31,
                    11.16 to 9.36,
                    6.18,
                    6.18 to 5.18,
                    9.46 to 7.93,
                    9.45 to 7.93,
                    17.14 to 14.38,
                    1.53,
                    1.5,
                ),
            PapelPalette to
                Row(
                    13.65 to 14.64,
                    6.50 to 6.97,
                    4.75 to 5.10,
                    5.09,
                    4.74 to 5.09,
                    7.27 to 7.80,
                    4.14 to 4.44,
                    13.65 to 14.64,
                    1.53,
                    1.5,
                ),
        )

    @Test
    fun everyContrastCriticalPairMatchesDesign() {
        table.forEach { (palette, row) ->
            with(palette) {
                check("$name text", text, 4.5, row.text.first, row.text.second)
                check("$name text secondary", textSecondary, 4.5, row.textSecondary.first, row.textSecondary.second)
                check("$name time", time, 4.5, row.time.first, row.time.second)
                check("$name primary", primary, 3.0, row.primary.first, row.primary.second)
                check("$name secondary", secondary, 3.0, row.secondary.first, row.secondary.second)
                check("$name tertiary", tertiary, 3.0, row.tertiary.first, row.tertiary.second)
                check("$name focus border", focusBorder, 3.0, row.focusBorder.first, row.focusBorder.second)

                val onPrimaryRatio = contrast(onPrimary, primary)
                assertEquals("$name on primary", row.onPrimary, onPrimaryRatio, 0.01)
                assertTrue("$name on primary ≥ 4.5", onPrimaryRatio >= 4.5)

                val apart = contrast(primary, secondary)
                assertEquals("$name primary vs secondary", row.primaryVsSecondary, apart, 0.01)
                assertTrue("$name primary vs secondary ≥ ${row.lightnessMinimum}", apart >= row.lightnessMinimum - 0.005)
            }
        }
    }

    @Test
    fun rolesHaveDesignsValues() {
        fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
        val expected =
            mapOf(
                PekeTvPalette to
                    listOf(
                        "#1F2747",
                        "#28335B",
                        "#FF927E",
                        "#1F2747",
                        "#65D9CE",
                        "#FFDA70",
                        "#B5A0EF",
                        "#FFFFFF",
                        "#C8CDE6",
                        "#1F2747",
                        "#FFFFFF",
                    ),
                MandarinaPalette to
                    listOf(
                        "#12202B",
                        "#1D3340",
                        "#FF8A3D",
                        "#12202B",
                        "#80E2D8",
                        "#FFD23F",
                        "#FF6F91",
                        "#FFFFFF",
                        "#B9C7D0",
                        "#12202B",
                        "#FFFFFF",
                    ),
                CarameloPalette to
                    listOf(
                        "#1E1B18",
                        "#2E2925",
                        "#FF6B6B",
                        "#1E1B18",
                        "#5BD6A2",
                        "#FFC93C",
                        "#8EC5FF",
                        "#FFF7EC",
                        "#CFC3B5",
                        "#1E1B18",
                        "#FFFFFF",
                    ),
                PapelPalette to
                    listOf(
                        "#FFF6E6",
                        "#FFFFFF",
                        "#C63F1A",
                        "#FFFFFF",
                        "#1B5E31",
                        "#9A6200",
                        "#2B7BC4",
                        "#1E2A33",
                        "#4F5B64",
                        "#1E2A33",
                        "#1E2A33",
                    ),
            )
        expected.forEach { (p, values) ->
            val actual =
                listOf(
                    p.background,
                    p.surface,
                    p.primary,
                    p.onPrimary,
                    p.secondary,
                    p.time,
                    p.tertiary,
                    p.text,
                    p.textSecondary,
                    p.outline,
                    p.focusBorder,
                ).map(::hex)
            assertEquals(p.name, values, actual)
        }
        // Only Papel is light, and it draws its focus border dark (DESIGN: "Light themes use a dark focus border")
        assertEquals(listOf(false, false, false, true), expected.keys.map { it.isLight })
    }
}
