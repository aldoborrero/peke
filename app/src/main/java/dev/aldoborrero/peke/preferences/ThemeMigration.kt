package dev.aldoborrero.peke.preferences

import androidx.datastore.core.DataMigration

/**
 * Moves a TV that stored one of Wholphin's themes (numbers 0–7) to PekeTV, once. A number above Peke's own themes,
 * from a newer Peke, stays as it is; it reads as PekeTV here ([dev.aldoborrero.peke.ui.theme.pekePalette]).
 */
object ThemeMigration : DataMigration<AppPreferences> {
    private val WHOLPHIN_THEMES = 0..7

    override suspend fun shouldMigrate(currentData: AppPreferences): Boolean =
        currentData.interfacePreferences.appThemeColorsValue in WHOLPHIN_THEMES

    override suspend fun migrate(currentData: AppPreferences): AppPreferences =
        currentData.updateInterfacePreferences { appThemeColors = AppThemeColors.PEKE_TV }

    override suspend fun cleanUp() = Unit
}
