package dev.aldoborrero.peke.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.kids.home.KidsCardKind
import dev.aldoborrero.peke.kids.home.cardSize
import dev.aldoborrero.peke.kids.home.focusRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 028: a kids home row's focused last card and a grown-up page's focus rings stay inside the TV-safe edge
 */
class TvSafeMarginsTest {
    private val eps = 0.01f

    @Test
    fun theEdgeIsAndroidTvsOverscanGuidance() {
        assertEquals(58f, SafeArea.EdgeHorizontal.value, eps)
        assertEquals(28f, SafeArea.EdgeVertical.value, eps)
    }

    /**
     * The row is laid out [FocusRoom.side] past the screen's right edge (bleedHorizontally) and scrolled to its end, so
     * its last card ends [FocusRoom.end] before the row's end; the focus then draws [PekeFocus.outset] past the card
     */
    @Test
    fun aHomeRowsFocusedLastCardStopsInsideTheEdge() {
        KidsCardKind.entries.forEach { kind ->
            val room = kind.focusRoom.toScreenEnd()
            val cardEndFromScreenEdge = room.end - room.side
            val ringEndFromScreenEdge = cardEndFromScreenEdge - PekeFocus.outset(kind.cardSize.width, grow = true)
            assertTrue(
                "$kind: ring ends $ringEndFromScreenEdge from the edge",
                ringEndFromScreenEdge >= SafeArea.EdgeHorizontal,
            )
            assertEquals(SafeArea.EdgeHorizontal + PekeFocus.Spare, ringEndFromScreenEdge, 0.01.dp)
        }
    }

    @Test
    fun aHomeRowStillStartsLinedUpWithItsTitle() {
        KidsCardKind.entries.forEach { kind ->
            val room = kind.focusRoom.toScreenEnd()
            assertEquals(room.side, room.padding.calculateLeftPadding(LayoutDirection.Ltr))
            assertEquals(room.end, room.padding.calculateRightPadding(LayoutDirection.Ltr))
        }
    }

    @Test
    fun otherRowsKeepTheirSymmetricRoom() {
        val room = FocusRoom.of(KidsCardKind.WIDE.cardSize)
        assertEquals(room.side, room.end)
    }

    @Test
    fun aGrownUpPagesRingsStayInsideTheEdge() {
        val ring = PekeFocus.outset(0.dp, grow = false)
        assertEquals(SafeArea.EdgeHorizontal, SafeArea.PageHorizontal - ring, 0.01.dp)
        assertEquals(SafeArea.EdgeVertical, SafeArea.PageVertical - ring, 0.01.dp)
        assertEquals(SafeArea.PageHorizontal, SafeArea.page.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(SafeArea.PageHorizontal, SafeArea.page.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(SafeArea.PageVertical, SafeArea.page.calculateTopPadding())
        assertEquals(SafeArea.PageVertical, SafeArea.page.calculateBottomPadding())
    }

    private fun assertEquals(
        expected: Dp,
        actual: Dp,
        delta: Dp,
    ) = assertEquals(expected.value, actual.value, delta.value)
}
