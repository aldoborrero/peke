package dev.aldoborrero.peke.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekePalette
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.rail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The open sidebar draws every row with one geometry: focus changes its colours, border and ring, never its size or
 * where its icon sits, and the border and ring stay inside the sidebar with room to spare (HomeDrawerPekeTV)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DrawerFocusGeometryTest {
    @get:Rule
    val rule = createComposeRule()

    private val page = Color(0xFF00FF00)
    private lateinit var colors: PekePalette
    private var density = 1f
    private val requesters = mapOf("Search" to FocusRequester(), "Home" to FocusRequester())
    private var composed = false

    @Composable
    private fun Drawer() {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
        PekeTheme {
            colors = LocalPekeColors.current
            density = LocalDensity.current.density
            ModalNavigationDrawer(
                drawerState = remember { DrawerState(DrawerValue.Open) },
                initialFocus = requesters.getValue("Search"),
                drawerContent = { value ->
                    // A lazy list as the sidebar's, which clips what its rows draw past its own edges
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                        contentPadding = PaddingValues(vertical = DrawerRingRoom),
                        modifier = Modifier.fillMaxHeight().drawerRingRoom(),
                    ) {
                        items(listOf("Search" to PekeIcon.SEARCH, "Home" to PekeIcon.HOME)) { (name, icon) ->
                            IconNavItem(
                                text = name,
                                icon = painterResource(icon.outline),
                                onClick = {},
                                selected = name == "Home",
                                drawerOpen = value == DrawerValue.Open,
                                modifier = Modifier.focusRequester(requesters.getValue(name)),
                            )
                        }
                    }
                },
            ) {
                Box(Modifier.fillMaxSize().background(page))
            }
        }
    }

    private fun capture(focus: String): PixelMap {
        if (!composed) rule.setContent { Drawer() }
        composed = true
        rule.runOnIdle { requesters.getValue(focus).requestFocus() }
        // Past the focus animation
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        return rule.onRoot().captureToImage().toPixelMap()
    }

    private fun px(dp: Float) = (dp * density).roundToInt()

    private fun Color.near(other: Color) = abs(red - other.red) < .04f && abs(green - other.green) < .04f && abs(blue - other.blue) < .04f

    /** The sidebar's top padding, then each 64 px row and the gap after it */
    private fun rowCentre(index: Int) = px(32.cpx.value + 32.cpx.value + index * (64.cpx.value + ROW_GAP.value))

    /** Where the focused row's pill begins and ends along its centre line: inside the focus border around it */
    private fun PixelMap.focusedPill(y: Int): IntRange {
        val border = (0 until px(DrawerOpenWidth.value)).filter { this[it, y].near(colors.focusBorder) }
        val left = (border.first() until border.last()).first { !this[it, y].near(colors.focusBorder) }
        val right = (border.last() downTo border.first()).first { !this[it, y].near(colors.focusBorder) }
        return left..right
    }

    /** Where the selected row's pill, in the surface colour, begins and ends along its centre line */
    private fun PixelMap.selectedPill(y: Int): IntRange {
        val xs = (0 until px(DrawerOpenWidth.value)).filter { this[it, y].near(colors.surface) }
        return xs.first()..xs.last()
    }

    /**
     * The leftmost pixel of a row's icon, anywhere in the row's height: the first that is none of the row's fill (taken
     * where the row has nothing drawn), the sidebar, the focus border or the ring
     */
    private fun PixelMap.iconLeft(
        index: Int,
        from: Int = 0,
    ): Int {
        // The icon's own height, clear of the ring of the row below
        val half = px(DrawerIconSize.value / 2) - 2
        val y = rowCentre(index)
        val fill = this[px(DrawerOpenWidth.value - DrawerOpenPadding.value - 12f), y]
        val plain = listOf(fill, colors.rail, colors.focusBorder, colors.primary)
        return (from until px(DrawerOpenWidth.value / 2))
            .first { x -> (y - half..y + half).any { yy -> plain.none { this[x, yy].near(it) } } }
    }

    @Test
    fun `a focused row has the same pill as the selected one`() {
        val image = capture(focus = "Search")
        val focused = image.focusedPill(rowCentre(0))
        val selected = image.selectedPill(rowCentre(1))
        assertEquals("left edge", selected.first.toFloat(), focused.first.toFloat(), 2f)
        assertEquals("right edge", selected.last.toFloat(), focused.last.toFloat(), 2f)
    }

    @Test
    fun `a row's icon stays put when it gains or loses focus`() {
        val image = capture(focus = "Search")
        val focused = image.iconLeft(0, from = image.focusedPill(rowCentre(0)).first + 2)
        val unfocused = capture(focus = "Home").iconLeft(0)
        assertEquals(unfocused.toFloat(), focused.toFloat(), 2f)
    }

    @Test
    fun `the focus border and ring stay inside the sidebar with room to spare`() {
        val image = capture(focus = "Search")
        val sheet = px(DrawerOpenWidth.value)
        val spare = px(SPARE)
        for (y in 0 until image.height) {
            for (x in 0 until spare) assertTrue("left edge at $x,$y", image[x, y].near(colors.rail))
            for (x in sheet - spare until sheet) assertTrue("right edge at $x,$y", image[x, y].near(colors.rail))
            for (x in sheet until sheet + px(16f)) assertTrue("page at $x,$y", image[x, y].near(page))
        }
    }

    @Test
    fun `the first row's ring isn't cut at the top of the list`() {
        val image = capture(focus = "Search")
        // Halfway through the ring above the row, which starts below the sidebar's 32 px padding
        val y = px(32.cpx.value - PekeFocus.BorderWidth.value - PekeFocus.RingWidth.value / 2)
        assertTrue(image[px(DrawerOpenWidth.value / 2), y].near(colors.primary))
    }

    @Test
    fun `the closed rail leaves room for the border and ring around its tiles`() {
        val side = (DrawerRailWidth - DrawerTileSize) / 2
        assertTrue(PekeFocus.outset(DrawerTileSize, grow = false).value + SPARE <= side.value)
        assertTrue(PekeFocus.outset(DrawerTileSize, grow = false).value + SPARE <= DrawerOpenPadding.value)
    }

    private companion object {
        val ROW_GAP = 12.cpx

        /** Room the ring leaves before the sidebar's edges, in dp */
        const val SPARE = 3f
    }
}
