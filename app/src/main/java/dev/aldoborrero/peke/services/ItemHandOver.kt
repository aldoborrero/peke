package dev.aldoborrero.peke.services

import dev.aldoborrero.peke.data.model.BaseItem
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands an item a page just read over to the page it opens, which would otherwise read it again. Not a cache: it
 * holds one item, the next [put] replaces it, and [take] empties it whether or not the id matches.
 */
@Singleton
class ItemHandOver
    @Inject
    constructor() {
        private var item: BaseItem? = null

        @Synchronized
        fun put(item: BaseItem) {
            this.item = item
        }

        /** The item handed over, if it is [id]'s */
        @Synchronized
        fun take(id: UUID): BaseItem? = item.also { item = null }?.takeIf { it.id == id }
    }
