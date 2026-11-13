package dev.aldoborrero.peke.kids.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.nav.Destination
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The parent menu's items that open a page, which Back from that page returns to (spec 025)
 */
enum class GrownUpMenuItem {
    RULES,
    SETTINGS,
}

/**
 * The page the parent menu opened in the kid's pages, so Back from it shows the menu again instead of the kid's page
 * below it (spec 025). Only that page: pages it opens in turn, such as a kid's rules from Settings, go back to it as
 * usual. Kept in memory only: after the app is killed Back leaves the page, and the grant is gone anyway.
 */
@Singleton
class GrownUpMenuReturn
    @Inject
    constructor(
        private val navigationManager: NavigationManager,
    ) {
        /**
         * Where the page sits in the back stack, and the page
         */
        private var page by mutableStateOf<Pair<Int, Destination>?>(null)

        /**
         * Open [destination] from the menu, on top of the kid's pages
         */
        fun open(destination: Destination) {
            navigationManager.navigateTo(destination)
            page = navigationManager.backStack.lastIndex to destination
        }

        /**
         * The menu item to focus when Back on [destination] shows the menu again, or null when [destination] isn't the
         * page the menu opened, or isn't on top
         */
        fun itemFor(destination: Destination): GrownUpMenuItem? {
            val (index, opened) = page ?: return null
            val stack = navigationManager.backStack
            if (navigationManager.isApart || opened != destination || stack.lastIndex != index || stack[index] != destination) {
                return null
            }
            return when (destination) {
                Destination.KidRules -> GrownUpMenuItem.RULES
                is Destination.Settings -> GrownUpMenuItem.SETTINGS
                else -> null
            }
        }

        /**
         * The menu shown again over [destination] closed: the page goes too, since the menu was the level above it
         */
        fun close(destination: Destination) {
            if (itemFor(destination) != null) navigationManager.replaceTop(destination, null)
            page = null
        }
    }

/**
 * A page Back can leave for the parent menu: with [item], Back shows [menu] over the page, focused on [item]; once the
 * menu closes, [onMenuClosed] removes the page. Back inside the page (a section's rows, a dialog) goes first.
 */
@Composable
fun BackToMenu(
    item: GrownUpMenuItem?,
    onMenuClosed: () -> Unit,
    menu: @Composable (item: GrownUpMenuItem, onDismissRequest: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    // Before the content, so Back handlers inside the page win while they are enabled
    BackHandler(enabled = item != null && !shown) { shown = true }
    content()
    if (shown && item != null) {
        menu(item) {
            shown = false
            onMenuClosed()
        }
    }
}
