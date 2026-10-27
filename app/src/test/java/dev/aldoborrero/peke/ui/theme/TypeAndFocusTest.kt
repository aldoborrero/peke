package dev.aldoborrero.peke.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeAndFocusTest {
    private val styles =
        with(AppTypography) {
            mapOf(
                "displayLarge" to displayLarge,
                "displayMedium" to displayMedium,
                "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge,
                "headlineMedium" to headlineMedium,
                "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge,
                "titleMedium" to titleMedium,
                "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge,
                "bodyMedium" to bodyMedium,
                "bodySmall" to bodySmall,
                "labelLarge" to labelLarge,
                "labelMedium" to labelMedium,
                "labelSmall" to labelSmall,
            )
        }

    @Test
    fun everyStyleIsNunitoAtABoldWeightAndNeverTooSmall() {
        styles.forEach { (name, style) ->
            assertEquals(name, NunitoFamily, style.fontFamily)
            assertTrue("$name weight", style.fontWeight in listOf(FontWeight.W700, FontWeight.W800, FontWeight.W900))
            // DESIGN's Small: 15 px at 0.75 sp per px
            assertTrue("$name size", style.fontSize.value >= 11.25f)
        }
    }

    @Test
    fun designsStylesHaveTheirSizes() {
        fun TextStyle.check(
            name: String,
            px: Float,
            weight: FontWeight,
        ) {
            assertEquals(name, (px * 0.75f).sp, fontSize)
            assertEquals(name, weight, fontWeight)
        }
        with(AppTypography) {
            displayLarge.check("Display", 56f, FontWeight.W900)
            headlineLarge.check("Title", 48f, FontWeight.W900)
            headlineMedium.check("Headline", 36f, FontWeight.W900)
            titleLarge.check("Section", 24f, FontWeight.W900)
            bodyLarge.check("Body", 22f, FontWeight.W800)
            bodyMedium.check("Supporting", 18f, FontWeight.W800)
            labelMedium.check("Small", 16f, FontWeight.W800)
        }
    }

    @Test
    fun shapesFollowDesign() {
        assertEquals(RoundedCornerShape(12.dp), AppShapes.small)
        assertEquals(RoundedCornerShape(18.dp), AppShapes.medium)
        assertEquals(RoundedCornerShape(27.dp), AppShapes.large)
    }

    @Test
    fun focusIsBorderRingAndScaleOnKidScreensOnly() {
        assertEquals(4.5.dp, PekeFocus.BorderWidth)
        assertEquals(6.dp, PekeFocus.RingWidth)
        assertTrue(PekeFocus.KID_SCALE in 1.06f..1.08f)
        assertEquals(PekeFocus.KID_SCALE, PekeFocus.scale(kidScreen = true))
        assertEquals(1f, PekeFocus.scale(kidScreen = false))
    }
}
