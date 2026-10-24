package dev.aldoborrero.peke.services

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app's pages are on screen, between the activity's `onStart` and `onStop`. While they are, the pages
 * decide who is current, and work in the background must not switch user under them.
 */
@Singleton
class AppVisibility
    @Inject
    constructor() {
        @Volatile
        var started: Boolean = false
    }
