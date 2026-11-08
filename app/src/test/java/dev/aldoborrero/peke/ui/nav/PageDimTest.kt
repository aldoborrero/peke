package dev.aldoborrero.peke.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The page behind the open sidebar fades to the same pixels as with a layer's alpha, overlapping cards included, without
 * the layer (HomeDrawerPekeTV)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h180dp-mdpi")
class PageDimTest {
    @get:Rule
    val rule = createComposeRule()

    private val background = Color(0xFF1F2747)

    /** Cards on a surface, one over another, with rounded corners: what a layer and a veil could fade differently */
    @Composable
    private fun Page(modifier: Modifier) {
        Box(Modifier.fillMaxSize().background(background)) {
            Box(modifier.fillMaxSize()) {
                Row(Modifier.padding(16.dp).background(Color(0xFF2E3A66), RoundedCornerShape(12.dp))) {
                    Box(Modifier.padding(8.dp).size(80.dp, 60.dp).background(Color(0xFFFFDA70), RoundedCornerShape(8.dp)))
                    Box(Modifier.padding(8.dp).size(80.dp, 60.dp).background(Color(0x80FF927E), RoundedCornerShape(8.dp)))
                }
                Box(Modifier.padding(start = 60.dp, top = 50.dp).size(120.dp, 60.dp).background(Color(0xCC65D9CE)))
            }
        }
    }

    private var page by mutableStateOf<Modifier?>(null)

    private fun capture(modifier: Modifier): PixelMap {
        if (page == null) rule.setContent { page?.let { Page(it) } }
        page = modifier
        rule.waitForIdle()
        return rule.onRoot().captureToImage().toPixelMap()
    }

    private fun Color.near(other: Color) = abs(red - other.red) < .02f && abs(green - other.green) < .02f && abs(blue - other.blue) < .02f

    private fun assertSame(alpha: Float) {
        val layered = capture(Modifier.graphicsLayer { this.alpha = alpha })
        val veiled = capture(Modifier.dimmedTo(background) { alpha })
        for (y in 0 until layered.height) {
            for (x in 0 until layered.width) {
                assertTrue("at $x,$y: ${layered[x, y]} vs ${veiled[x, y]}", layered[x, y].near(veiled[x, y]))
            }
        }
    }

    @Test
    fun `the open sidebar's dim matches a layer at 35 percent`() = assertSame(.35f)

    @Test
    fun `halfway through opening it matches too`() = assertSame(.7f)

    @Test
    fun `closed, the page is untouched`() = assertSame(1f)
}
