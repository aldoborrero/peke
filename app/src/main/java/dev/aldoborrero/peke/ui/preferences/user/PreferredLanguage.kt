package dev.aldoborrero.peke.ui.preferences.user

import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.util.ConcatStringProvider
import dev.aldoborrero.peke.ui.util.EmptyStringProvider
import dev.aldoborrero.peke.ui.util.ResStringProvider
import dev.aldoborrero.peke.ui.util.StringProvider
import dev.aldoborrero.peke.ui.util.StringStringProvider

/**
 * UI information for displaying a preferred language
 */
sealed interface PreferredLanguageType {
    val displayString: StringProvider

    data class ServerProfile(
        val name: String?,
    ) : PreferredLanguageType {
        override val displayString: StringProvider
            get() =
                ConcatStringProvider(
                    " - ",
                    buildList {
                        add(ResStringProvider(R.string.use_user_profile))
                        if (name.isNotNullOrBlank()) {
                            add(StringStringProvider(name))
                        }
                    },
                )
    }

    data object AnyLanguage : PreferredLanguageType {
        override val displayString: StringProvider
            get() = ResStringProvider(R.string.any_language)
    }

    data class Language(
        val iso: String,
        val name: String,
    ) : PreferredLanguageType {
        override val displayString: StringProvider
            get() = StringStringProvider(name)
    }

    data object Divider : PreferredLanguageType {
        override val displayString: StringProvider
            get() = EmptyStringProvider
    }
}

data class PreferredLanguage(
    val selected: PreferredLanguageType = PreferredLanguageType.ServerProfile(null),
    val options: List<PreferredLanguageType> = emptyList(),
)
