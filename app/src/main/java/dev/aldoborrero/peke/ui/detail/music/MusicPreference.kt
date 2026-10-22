package dev.aldoborrero.peke.ui.detail.music

import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.AppSwitchPreference
import dev.aldoborrero.peke.preferences.updateMusicPreferences

fun getMusicPreferences() =
    listOf(
        AppSwitchPreference<AppPreferences>(
            title = R.string.show_album_cover,
            defaultValue = false,
            getter = { it.musicPreferences.showAlbumArt },
            setter = { prefs, value ->
                prefs.updateMusicPreferences { showAlbumArt = value }
            },
        ),
        AppSwitchPreference<AppPreferences>(
            title = R.string.show_visualizer,
            defaultValue = false,
            getter = { it.musicPreferences.showVisualizer },
            setter = { prefs, value ->
                prefs.updateMusicPreferences { showVisualizer = value }
            },
        ),
        AppSwitchPreference<AppPreferences>(
            title = R.string.show_backdrop,
            defaultValue = false,
            getter = { it.musicPreferences.showBackdrop },
            setter = { prefs, value ->
                prefs.updateMusicPreferences { showBackdrop = value }
            },
        ),
        AppSwitchPreference<AppPreferences>(
            title = R.string.show_lyrics,
            defaultValue = false,
            getter = { it.musicPreferences.showLyrics },
            setter = { prefs, value ->
                prefs.updateMusicPreferences { showLyrics = value }
            },
        ),
    )
