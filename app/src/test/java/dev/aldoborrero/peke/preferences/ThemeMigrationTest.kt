package dev.aldoborrero.peke.preferences

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMigrationTest {
    private fun stored(number: Int): AppPreferences =
        AppPreferences
            .newBuilder()
            .setInterfacePreferences(InterfacePreferences.newBuilder().setAppThemeColorsValue(number))
            .build()

    @Test
    fun everyWholphinThemeBecomesPekeTv() =
        runTest {
            // 0 was Wholphin's purple (and what proto3 reads when nothing was stored), 1–7 the other Wholphin themes
            (0..7).forEach { number ->
                val prefs = stored(number)
                assertTrue("migrates $number", ThemeMigration.shouldMigrate(prefs))
                assertEquals(AppThemeColors.PEKE_TV, ThemeMigration.migrate(prefs).interfacePreferences.appThemeColors)
            }
        }

    @Test
    fun pekeThemesStay() =
        runTest {
            listOf(AppThemeColors.PEKE_TV, AppThemeColors.MANDARINA, AppThemeColors.CARAMELO, AppThemeColors.PAPEL)
                .forEach { assertFalse("keeps $it", ThemeMigration.shouldMigrate(stored(it.number))) }
        }

    @Test
    fun aThemeFromANewerPekeIsNotRewritten() =
        runTest {
            // Read as PekeTV (ThemeSelectionTest), but left for the newer version to read again
            assertFalse(ThemeMigration.shouldMigrate(stored(42)))
        }

    @Test
    fun theMigrationKeepsTheRestOfThePreferences() =
        runTest {
            val prefs =
                stored(5).updateInterfacePreferences { showClock = true }
            val migrated = ThemeMigration.migrate(prefs)
            assertTrue(migrated.interfacePreferences.showClock)
            assertEquals(AppThemeColors.PEKE_TV, migrated.interfacePreferences.appThemeColors)
        }

    @Test
    fun aFreshInstallStartsOnPekeTv() {
        val defaults = AppPreferencesSerializer().defaultValue
        assertEquals(AppThemeColors.PEKE_TV, defaults.interfacePreferences.appThemeColors)
        assertEquals(AppThemeColors.PEKE_TV, AppPreference.ThemeColors.defaultValue)
    }
}
