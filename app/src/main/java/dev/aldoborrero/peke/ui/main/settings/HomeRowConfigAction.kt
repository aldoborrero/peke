package dev.aldoborrero.peke.ui.main.settings

sealed interface HomeRowConfigAction {
    data object Combine : HomeRowConfigAction

    data object Split : HomeRowConfigAction
}
