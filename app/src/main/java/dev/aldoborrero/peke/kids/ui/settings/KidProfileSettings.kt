package dev.aldoborrero.peke.kids.ui.settings

import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.data.KidProfile
import dev.aldoborrero.peke.preferences.AppPreference
import dev.aldoborrero.peke.preferences.AppSliderPreference
import dev.aldoborrero.peke.preferences.AppSwitchPreference

/**
 * Each kid's options, shown with Wholphin's [dev.aldoborrero.peke.ui.preferences.ComposablePreference]
 */
object KidProfileSettings {
    val SearchAllowed =
        AppSwitchPreference<KidProfile>(
            title = R.string.kids_search_allowed,
            defaultValue = false,
            getter = { it.searchAllowed },
            setter = { prefs, value -> prefs.copy(searchAllowed = value) },
        )

    val Autoplay =
        AppSwitchPreference<KidProfile>(
            title = R.string.kids_autoplay,
            defaultValue = false,
            getter = { it.autoplayEnabled },
            setter = { prefs, value -> prefs.copy(autoplayEnabled = value) },
        )

    val AutoplayCap =
        AppSliderPreference<KidProfile>(
            title = R.string.kids_autoplay_cap,
            defaultValue = KidProfile.DEFAULT_AUTOPLAY_CAP.toLong(),
            min = KidProfile.MIN_AUTOPLAY_CAP.toLong(),
            max = KidProfile.MAX_AUTOPLAY_CAP.toLong(),
            getter = { it.validAutoplayCap.toLong() },
            setter = { prefs, value -> prefs.copy(autoplayCap = value.toInt()) },
            summarizer = { it?.toString() },
        )

    fun preferences(profile: KidProfile): List<AppPreference<KidProfile, *>> =
        buildList {
            add(SearchAllowed)
            add(Autoplay)
            if (profile.autoplayEnabled) add(AutoplayCap)
        }
}
