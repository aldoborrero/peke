package dev.aldoborrero.peke.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import kotlinx.coroutines.launch

/**
 * The focus treatment of design/DESIGN.md, set once for the shared components: a border in the theme's focus-border
 * colour, then a ring in its primary colour, and a small scale-up on kid screens. Never colour alone, and motion is
 * transform only, so cheap TV boxes keep up.
 *
 * A component uses [pekeFocus] and turns off tv-material's own focus scale and border ([surfaceScale], [cardScale],
 * [surfaceBorder], [cardBorder]): tv-material draws one border per state and scales inside the component, so a ring
 * added from outside wouldn't grow with it.
 */
object PekeFocus {
    /**
     * 6 canvas px
     */
    val BorderWidth = 4.5.dp

    /**
     * 8 canvas px, outside the border
     */
    val RingWidth = 6.dp

    /**
     * Cards and avatars grow 6–8% on kid screens
     */
    const val KID_SCALE = 1.07f

    const val ANIMATION_MILLIS = 150

    /**
     * The focused scale: grown-up screens keep the border and ring but don't grow
     */
    fun scale(kidScreen: Boolean): Float = if (kidScreen) KID_SCALE else 1f

    /**
     * How far the focus draws past each edge of an item [size] long: half its growth when it [grow]s, then the border
     * and the ring, which grow with it
     */
    fun outset(
        size: Dp,
        grow: Boolean,
    ): Dp {
        val scale = if (grow) KID_SCALE else 1f
        return size * (scale - 1) / 2 + (BorderWidth + RingWidth) * scale
    }

    /**
     * Room left between a focused item's ring and whatever is next to it
     */
    val Spare = 4.dp

    /**
     * The gap that keeps a focused item [size] long, its border and its ring clear of its neighbour
     */
    fun clearance(
        size: Dp,
        grow: Boolean = true,
    ): Dp = outset(size, grow) + Spare

    val surfaceScale: ClickableSurfaceScale = ClickableSurfaceScale.None

    val cardScale: CardScale = CardScale.None

    @Composable
    @ReadOnlyComposable
    fun surfaceBorder(): ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
            disabledBorder = Border.None,
            focusedDisabledBorder = Border.None,
        )

    @Composable
    @ReadOnlyComposable
    fun cardBorder(): CardBorder =
        CardDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        )
}

/**
 * Whether focused items grow: true on kid screens, false around grown-up pages
 */
val LocalPekeFocusScale = compositionLocalOf { true }

/**
 * Draws Peke's focus around this item, in [shape], while the focusable after it in the chain has focus: the item
 * grows (on kid screens, unless not [grow]), rises above its neighbours, and gets the border and the ring outside its edge
 *
 *  grow false for rows that must keep their place, such as the sidebar's, which change only colour, border and
 * ring
 */
fun Modifier.pekeFocus(
    shape: Shape,
    grow: Boolean = true,
): Modifier = this then PekeFocusElement(shape, grow)

private data class PekeFocusElement(
    val shape: Shape,
    val grow: Boolean,
) : ModifierNodeElement<PekeFocusNode>() {
    override fun create() = PekeFocusNode(shape, grow)

    override fun update(node: PekeFocusNode) {
        node.shape = shape
        node.grow = grow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pekeFocus"
        properties["shape"] = shape
        properties["grow"] = grow
    }
}

private class PekeFocusNode(
    shape: Shape,
    var grow: Boolean,
) : Modifier.Node(),
    FocusEventModifierNode,
    LayoutModifierNode,
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    var shape: Shape = shape
        set(value) {
            if (field != value) ringsValid = false
            field = value
        }

    private var focused = false
    private var scale = Animatable(1f)

    // The border and the ring, made again only when the size, direction, density or shape change: the focused card
    // draws them on every frame of its scale animation
    private val borderPath = Path()
    private val ringPath = Path()
    private var borderStroke = Stroke(0f)
    private var ringStroke = Stroke(0f)
    private var ringsValid = false
    private var ringsSize = Size.Unspecified
    private var ringsDirection = LayoutDirection.Ltr
    private var ringsDensity = 0f

    override fun onReset() {
        focused = false
        scale = Animatable(1f)
        ringsValid = false
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == focused) return
        focused = focusState.isFocused
        val target = if (focused && grow) PekeFocus.scale(currentValueOf(LocalPekeFocusScale)) else 1f
        coroutineScope.launch { scale.animateTo(target, tween(PekeFocus.ANIMATION_MILLIS)) }
        invalidatePlacement()
        invalidateDraw()
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        // Above the neighbours while focused, so they don't cover the ring
        return layout(placeable.width, placeable.height) { placeable.place(0, 0, zIndex = if (focused) 1f else 0f) }
    }

    override fun ContentDrawScope.draw() {
        val colors = currentValueOf(LocalPekeColors)
        scale(scale.value) {
            this@draw.drawContent()
            if (focused) {
                updateRings()
                drawPath(borderPath, colors.focusBorder, style = borderStroke)
                drawPath(ringPath, colors.primary, style = ringStroke)
            }
        }
    }

    private fun DrawScope.updateRings() {
        if (ringsValid && ringsSize == size && ringsDirection == layoutDirection && ringsDensity == density) return
        val border = PekeFocus.BorderWidth.toPx()
        val ring = PekeFocus.RingWidth.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        borderPath.outside(outline, border / 2)
        ringPath.outside(outline, border + ring / 2)
        borderStroke = Stroke(border)
        ringStroke = Stroke(ring)
        ringsValid = true
        ringsSize = size
        ringsDirection = layoutDirection
        ringsDensity = density
    }
}

/**
 * Sets this path to the line [distance] outside [outline]'s edge, its corners grown by as much, for a stroke centred on it
 */
private fun Path.outside(
    outline: Outline,
    distance: Float,
) {
    rewind()
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect.inflate(distance))

        is Outline.Rounded -> addRoundRect(outline.roundRect.grow(distance))

        // Only in custom shapes: drawn on the edge
        is Outline.Generic -> addPath(outline.path)
    }
}

private fun CornerRadius.grow(by: Float) = CornerRadius(x + by, y + by)

private fun RoundRect.grow(by: Float) =
    RoundRect(
        left = left - by,
        top = top - by,
        right = right + by,
        bottom = bottom + by,
        topLeftCornerRadius = topLeftCornerRadius.grow(by),
        topRightCornerRadius = topRightCornerRadius.grow(by),
        bottomRightCornerRadius = bottomRightCornerRadius.grow(by),
        bottomLeftCornerRadius = bottomLeftCornerRadius.grow(by),
    )
