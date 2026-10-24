package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.ui.nav.Destination

/**
 * Where the remote's Play key on a grid card takes a kid: the item's details page, as OK does. Only "Continue
 * watching" on the home plays straight away (DESIGN "Opening videos", spec 005 FR-001).
 */
fun kidsPlayKeyDestination(item: BaseItem): Destination = item.destination()
