package dev.aldoborrero.peke.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.preferences.AppPreference
import dev.aldoborrero.peke.preferences.AppThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ThemeSelectionTest {
    private val choices =
        listOf(AppThemeColors.PEKE_TV, AppThemeColors.MANDARINA, AppThemeColors.CARAMELO, AppThemeColors.PAPEL)

    @Test
    fun eachPekeThemeHasItsPalette() {
        assertSame(PekeTvPalette, AppThemeColors.PEKE_TV.pekePalette())
        assertSame(MandarinaPalette, AppThemeColors.MANDARINA.pekePalette())
        assertSame(CarameloPalette, AppThemeColors.CARAMELO.pekePalette())
        assertSame(PapelPalette, AppThemeColors.PAPEL.pekePalette())
    }

    @Test
    fun anythingElseIsPekeTv() {
        assertSame(PekeTvPalette, AppThemeColors.APP_THEME_COLORS_UNSPECIFIED.pekePalette())
        assertSame(PekeTvPalette, AppThemeColors.UNRECOGNIZED.pekePalette())
        // Wholphin's stored numbers and a newer Peke's, as the stored number
        (0..7).forEach { assertSame(PekeTvPalette, pekePalette(it)) }
        assertSame(PekeTvPalette, pekePalette(42))
    }

    @Test
    fun theChoiceListsTheFourThemesInOrder() {
        val pref = AppPreference.ThemeColors
        assertEquals(AppThemeColors.PEKE_TV, pref.defaultValue)
        choices.forEachIndexed { index, theme ->
            assertEquals(theme, pref.indexToValue(index))
            assertEquals(index, pref.valueToIndex(theme))
        }
        // Anything else opens on PekeTV, and an index out of the list picks it
        assertEquals(0, pref.valueToIndex(AppThemeColors.UNRECOGNIZED))
        assertEquals(0, pref.valueToIndex(AppThemeColors.APP_THEME_COLORS_UNSPECIFIED))
        assertEquals(AppThemeColors.PEKE_TV, pref.indexToValue(9))
    }

    @Test
    fun theThemeNamesShipInEnglishAndSpanish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            listOf("PekeTV", "Mandarina", "Caramelo", "Papel (daytime)"),
            context.resources.getStringArray(R.array.app_theme_colors).toList(),
        )
        val spanish =
            context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale("es")) })
        assertEquals(
            listOf("PekeTV", "Mandarina", "Caramelo", "Papel (de día)"),
            spanish.resources.getStringArray(R.array.app_theme_colors).toList(),
        )
    }
}
