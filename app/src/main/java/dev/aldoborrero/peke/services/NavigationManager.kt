package dev.aldoborrero.peke.services

import androidx.navigation3.runtime.NavBackStack
import dev.aldoborrero.peke.ui.nav.Destination
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating between pages and manages the app's back stack
 */
@Singleton
class NavigationManager
    @Inject
    constructor() {
        var backStack: MutableList<Destination> = NavBackStack(Destination.Home())

        /**
         * The kid's pages, set aside while grown-up pages show outside them ([openApart])
         */
        private var setAside: MutableList<Destination>? = null

        /**
         * The kid's pages, also while they are set aside: what is saved with the activity
         */
        val kidBackStack: List<Destination> get() = setAside ?: backStack

        /**
         * Whether pages show outside a kid's pages, such as Settings from the kids picker
         */
        val isApart: Boolean get() = setAside != null

        /**
         * Show pages from [start] outside a kid's pages, such as Settings from the kids picker: the kid's pages are set
         * aside until [closeApart], so pages that navigate, such as settings, stay among these
         *
         * @return the pages' back stack
         */
        fun openApart(start: Destination): MutableList<Destination> {
            synchronized(this) {
                if (setAside == null) setAside = backStack
                backStack = NavBackStack(start)
            }
            log()
            return backStack
        }

        /**
         * Put the kid's pages set aside by [openApart] back; nothing happens if none were
         */
        fun closeApart() {
            synchronized(this) {
                setAside?.let { backStack = it }
                setAside = null
            }
        }

        /**
         * Go to the specified [dev.aldoborrero.peke.ui.nav.Destination]
         */
        fun navigateTo(destination: Destination) {
            backStack.add(destination)
            log()
        }

        /**
         * Go to the specified [Destination], but reset the back stack to Home first
         */
        fun navigateToFromDrawer(destination: Destination) {
            goToHome()
            backStack.add(destination)
            log()
        }

        /**
         * Go to the previous page
         */
        fun goBack() {
            synchronized(this) {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            }
            log()
        }

        /**
         * Swap [expected] for [with] (or only remove it when [with] is null), but only while [expected] is the page on
         * top: a result that comes back after the user left that page changes nothing (spec 005 FR-003)
         *
         * @return whether [expected] was on top and was replaced
         */
        fun replaceTop(
            expected: Destination,
            with: Destination?,
        ): Boolean {
            synchronized(this) {
                if (backStack.size < 2 || backStack.lastOrNull() != expected) return false
                backStack.removeLastOrNull()
                if (with != null) backStack.add(with)
            }
            log()
            return true
        }

        /**
         * Go all the way back to the home page
         */
        fun goToHome() {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack[0] !is Destination.Home) {
                backStack[0] = Destination.Home()
            }
            log()
        }

        /**
         * Go all the way back to the home page, and reload it from scratch
         */
        fun reloadHome() {
            goToHome()
            val id = (backStack[0] as Destination.Home).id + 1
            backStack[0] = Destination.Home(id)
            log()
        }

        /**
         * Resets the backstack to the specified destination
         */
        fun replace(destination: Destination) {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack.isEmpty()) {
                backStack.add(0, destination)
            } else {
                backStack[0] = destination
            }
            log()
        }

        private fun log() {
            val dest = backStack.lastOrNull().toString()
            Timber.i("Current Destination: %s", dest)
            ACRA.errorReporter.putCustomData("destination", dest)
        }
    }
