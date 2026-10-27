package dev.aldoborrero.peke.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import dev.aldoborrero.peke.R

/**
 * Peke's interface icons (design/assets/icons), on a 48 grid, used at 48, 32 and 24 dp
 *
 * @param source the file name in design/assets/icons
 * @param twoTone the filled icon in PekeTV's colours, baked in: PekeTV only, and only on a light surface such as a chip,
 * since its navy outline is the PekeTV background
 * @param outline the one-colour icon, drawn white to be tinted with the theme's roles
 */
enum class PekeIcon(
    val source: String,
    @param:DrawableRes val twoTone: Int,
    @param:DrawableRes val outline: Int,
) {
    AUDIO("audio", R.drawable.ic_peke_audio, R.drawable.ic_peke_outline_audio),
    BACK("back", R.drawable.ic_peke_back, R.drawable.ic_peke_outline_back),
    BEDTIME("bedtime", R.drawable.ic_peke_bedtime, R.drawable.ic_peke_outline_bedtime),
    BREAK("break", R.drawable.ic_peke_break, R.drawable.ic_peke_outline_break),
    EPISODES("episodes", R.drawable.ic_peke_episodes, R.drawable.ic_peke_outline_episodes),
    FAVOURITES("favourites", R.drawable.ic_peke_favourites, R.drawable.ic_peke_outline_favourites),
    GROWN_UPS("grown-ups", R.drawable.ic_peke_grown_ups, R.drawable.ic_peke_outline_grown_ups),
    HOME("home", R.drawable.ic_peke_home, R.drawable.ic_peke_outline_home),
    LIVE("live", R.drawable.ic_peke_live, R.drawable.ic_peke_outline_live),
    NEXT_EPISODE("next-episode", R.drawable.ic_peke_next_episode, R.drawable.ic_peke_outline_next_episode),
    PAUSE("pause", R.drawable.ic_peke_pause, R.drawable.ic_peke_outline_pause),
    PICKS("picks", R.drawable.ic_peke_picks, R.drawable.ic_peke_outline_picks),
    PLAY("play", R.drawable.ic_peke_play, R.drawable.ic_peke_outline_play),
    SEARCH("search", R.drawable.ic_peke_search, R.drawable.ic_peke_outline_search),
    SETTINGS("settings", R.drawable.ic_peke_settings, R.drawable.ic_peke_outline_settings),
    SUBTITLES("subtitles", R.drawable.ic_peke_subtitles, R.drawable.ic_peke_outline_subtitles),
    SWITCH_KID("switch-kid", R.drawable.ic_peke_switch_kid, R.drawable.ic_peke_outline_switch_kid),
    TIME_LEFT("time-left", R.drawable.ic_peke_time_left, R.drawable.ic_peke_outline_time_left),
    VOICE("voice", R.drawable.ic_peke_voice, R.drawable.ic_peke_outline_voice),
    WATCHED("watched", R.drawable.ic_peke_watched, R.drawable.ic_peke_outline_watched),
}

/**
 * The PekeTV buddy's moods (design/assets/mascot) and where each one shows (DESIGN.md, The PekeTV buddy)
 *
 * @param source the mood's suffix of `peke-tv-buddy` in design/assets/mascot
 */
enum class BuddyMood(
    val source: String,
    @param:DrawableRes val drawable: Int,
) {
    /**
     * The logo, starting up, the grown-up setup header
     */
    HAPPY("peke-tv-buddy", R.drawable.peke_buddy),

    /**
     * Time's up for today
     */
    WAVING("-waving", R.drawable.peke_buddy_waving),

    /**
     * Outside allowed hours
     */
    SLEEPING("-sleeping", R.drawable.peke_buddy_sleeping),

    /**
     * Something won't play
     */
    PUZZLED("-puzzled", R.drawable.peke_buddy_puzzled),

    /**
     * Can't reach the server
     */
    SEARCHING("-searching", R.drawable.peke_buddy_searching),

    /**
     * Episodes in a row reached
     */
    CHEERING("-cheering", R.drawable.peke_buddy_cheering),
}

/**
 * The six kid avatars (design/assets/avatars), each on its own round background
 *
 * @param id the value of a kid profile's `Avatar` in the Jellyfin Kids plugin (Kids API 1.1, feature `avatar`)
 */
enum class PekeAvatar(
    val id: String,
    @param:DrawableRes val drawable: Int,
    val background: Color,
) {
    LEO("leo", R.drawable.avatar_leo, Color(0xFFFF927E)),
    MAX("max", R.drawable.avatar_max, Color(0xFFFFDA70)),
    NICO("nico", R.drawable.avatar_nico, Color(0xFF65D9CE)),
    LUCY("lucy", R.drawable.avatar_lucy, Color(0xFFB5A0EF)),
    SARA("sara", R.drawable.avatar_sara, Color(0xFFC8CDE6)),
    MIA("mia", R.drawable.avatar_mia, Color(0xFFFFFFFF)),
    ;

    companion object {
        /**
         * The avatar the plugin named, or null for none: the plugin sends the ids lowercase, and anything else is a
         * picture this app doesn't ship
         */
        fun of(id: String?): PekeAvatar? = entries.firstOrNull { it.id == id }
    }
}
