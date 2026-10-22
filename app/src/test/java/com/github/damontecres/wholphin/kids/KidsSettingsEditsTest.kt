package com.github.damontecres.wholphin.kids

import com.github.damontecres.wholphin.kids.data.KidsHomeSection
import com.github.damontecres.wholphin.kids.data.KidsHomeSectionEntry
import com.github.damontecres.wholphin.kids.ui.settings.KidsSettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class KidsSettingsEditsTest {
    private val sections = KidsHomeSectionEntry.DEFAULT

    @Test
    fun `a section is moved by name, so an edit made meanwhile doesn't move another one`() {
        val moved = KidsSettingsViewModel.moveSection(sections, KidsHomeSection.PICKED, -1)
        assertEquals(KidsHomeSection.PICKED, moved[0].section)
        assertEquals(KidsHomeSection.KEEP_WATCHING, moved[1].section)

        // The latest profile already has Picked first: moving it up again changes nothing
        assertEquals(moved, KidsSettingsViewModel.moveSection(moved, KidsHomeSection.PICKED, -1))
        assertEquals(sections, KidsSettingsViewModel.moveSection(sections, KidsHomeSection.FAVOURITES, 1))
    }
}
