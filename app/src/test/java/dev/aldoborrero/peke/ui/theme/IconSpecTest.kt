package dev.aldoborrero.peke.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The icon sources in design/assets/icons follow design/DESIGN.md's Icons rules, so drift fails the build: a 48 grid
 * with nothing outside the live area, 3.5 strokes with round caps and joins, explicit fills, corner radii from the
 * scale, the highlight only where it's listed, both sets drawn alike, and two-tone icons kept off the dark screens
 */
class IconSpecTest {
    private val sources = File("../design/assets/icons")

    private val names = PekeIcon.entries.map { it.source }

    private fun svg(
        name: String,
        outline: Boolean,
    ): Element {
        val file = if (outline) File(sources, "outline/$name.svg") else File(sources, "$name.svg")
        return DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun shapes(root: Element): List<Element> =
        buildList {
            fun walk(el: Element) {
                for (i in 0 until el.childNodes.length) {
                    val child = el.childNodes.item(i) as? Element ?: continue
                    if (child.tagName == "g") {
                        walk(child)
                    } else if (child.tagName in DRAWN) {
                        add(child)
                    }
                }
            }
            walk(root)
        }

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    private fun Element.number(name: String): Double = attr(name)?.toDouble() ?: 0.0

    private fun forEachIcon(block: (name: String, outline: Boolean, root: Element) -> Unit) {
        names.forEach { name -> listOf(false, true).forEach { outline -> block(name, outline, svg(name, outline)) } }
    }

    @Test
    fun everyIconIsOnThe48Grid() {
        forEachIcon { name, outline, root ->
            assertEquals("$name outline=$outline", "0 0 48 48", root.attr("viewBox"))
        }
    }

    @Test
    fun strokesAreRoundAndThreeAndAHalfWide() {
        forEachIcon { name, outline, root ->
            // Set once, on the root
            assertEquals("$name outline=$outline cap", "round", root.attr("stroke-linecap"))
            assertEquals("$name outline=$outline join", "round", root.attr("stroke-linejoin"))
            shapes(root).forEach { el ->
                assertEquals("$name ${el.tagName} cap", null, el.attr("stroke-linecap"))
                assertEquals("$name ${el.tagName} join", null, el.attr("stroke-linejoin"))
                val width = el.attr("stroke-width")?.toDouble() ?: return@forEach
                val allowed = if (name in THIN_DETAILS) setOf(3.5, 3.0) else setOf(3.5)
                assertTrue("$name outline=$outline ${el.tagName} stroke $width", width in allowed)
            }
        }
    }

    @Test
    fun everyShapeHasAnExplicitFill() {
        forEachIcon { name, outline, root ->
            shapes(root).forEach { el ->
                val fill = el.attr("fill")
                val stroke = el.attr("stroke")
                if (outline) {
                    assertTrue("$name ${el.tagName} fill $fill", fill == "none" || fill == "currentColor")
                    assertTrue("$name ${el.tagName} stroke $stroke", stroke == null || stroke == "currentColor")
                } else {
                    assertTrue("$name ${el.tagName} fill $fill", fill == "none" || fill in PALETTE)
                    assertTrue("$name ${el.tagName} stroke $stroke", stroke == null || stroke == OUTLINE)
                }
                if (stroke != null) assertTrue("$name ${el.tagName} stroke width", el.attr("stroke-width") != null)
            }
        }
    }

    @Test
    fun cornersComeFromTheScale() {
        forEachIcon { name, outline, root ->
            shapes(root).filter { it.tagName == "rect" }.forEach { el ->
                val rx = el.number("rx")
                assertTrue("$name outline=$outline rx $rx", rx in RADII)
                assertEquals("$name rx and ry", null, el.attr("ry"))
                // Never more than half the short side, which would turn the corners elliptical
                assertTrue("$name rx $rx", rx <= min(el.number("width"), el.number("height")) / 2)
            }
        }
    }

    @Test
    fun nothingLeavesTheLiveArea() {
        forEachIcon { name, outline, root ->
            val ink = bounds(root)
            val (low, high) = if (name in ROUND) 3.5 to 44.5 else 4.0 to 44.0
            val inside =
                ink.minX >= low - EPSILON &&
                    ink.minY >= low - EPSILON &&
                    ink.maxX <= high + EPSILON &&
                    ink.maxY <= high + EPSILON
            assertTrue("$name outline=$outline ink $ink", inside)
        }
    }

    @Test
    fun bothSetsDrawTheSameIcon() {
        names.forEach { name ->
            val twoTone = bounds(svg(name, outline = false))
            val outline = bounds(svg(name, outline = true))
            listOf(
                twoTone.minX to outline.minX,
                twoTone.minY to outline.minY,
                twoTone.maxX to outline.maxX,
                twoTone.maxY to outline.maxY,
            ).forEach { (a, b) -> assertEquals("$name: $twoTone vs $outline", a, b, 0.05) }
        }
    }

    @Test
    fun theHighlightIsOnlyOnTheListedIcons() {
        names.forEach { name ->
            val highlights =
                shapes(svg(name, outline = false)).count {
                    it.tagName == "circle" && it.attr("fill") == "#FFFFFF" && it.attr("stroke") == null
                }
            assertEquals(name, if (name in HIGHLIGHTED) 1 else 0, highlights)
            val outlineHighlights = shapes(svg(name, outline = true)).count { it.attr("fill") == "#FFFFFF" }
            assertEquals("$name outline", 0, outlineHighlights)
        }
    }

    /**
     * Two-tone icons are drawn only on a light surface, a chip or a pill (DESIGN.md, Icons): their navy outline is the
     * PekeTV background. No screen has such a surface yet, so no code outside PekeArt.kt uses them.
     */
    @Test
    fun twoToneIconsAreNotDrawnOnTheDarkScreens() {
        val twoTone = Regex("""\.twoTone\b|R\.drawable\.ic_peke_(?!outline_)""")
        val uses =
            File("src/main/java")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "PekeArt.kt" }
                .flatMap { file ->
                    file
                        .readLines()
                        .withIndex()
                        .filter { twoTone.containsMatchIn(it.value) }
                        .map { "${file.name}:${it.index + 1}" }
                }.toList()
        assertEquals(emptyList<String>(), uses)
    }

    private fun bounds(root: Element): Rectangle2D {
        var ink: Rectangle2D? = null
        shapes(root).forEach { el ->
            val shape = shape(el)
            val width = el.attr("stroke-width")?.toDouble()
            val drawn =
                if (width != null && el.attr("stroke") != null) {
                    BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(shape)
                } else {
                    shape
                }
            val b = tightBounds(drawn)
            ink = ink?.createUnion(b) ?: b
        }
        return ink!!
    }

    private fun tightBounds(shape: Shape): Rectangle2D {
        val it = shape.getPathIterator(null, 0.005)
        val c = DoubleArray(6)
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        while (!it.isDone) {
            if (it.currentSegment(c) != PathIterator.SEG_CLOSE) {
                minX = min(minX, c[0])
                minY = min(minY, c[1])
                maxX = max(maxX, c[0])
                maxY = max(maxY, c[1])
            }
            it.next()
        }
        return Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY)
    }

    private fun shape(el: Element): Shape =
        when (el.tagName) {
            "rect" -> {
                val r = el.number("rx") * 2
                RoundRectangle2D.Double(el.number("x"), el.number("y"), el.number("width"), el.number("height"), r, r)
            }

            "circle" -> {
                val r = el.number("r")
                Ellipse2D.Double(el.number("cx") - r, el.number("cy") - r, r * 2, r * 2)
            }

            "path" -> {
                pathData(el.getAttribute("d"))
            }

            else -> {
                error("${el.tagName} isn't used by the icons")
            }
        }

    /**
     * SVG path data, with the commands the icons use: M, L, H, V, Q, C, A and Z, absolute or relative
     */
    private fun pathData(d: String): Path2D {
        val tokens = Regex("""[A-Za-z]|-?(\d+\.?\d*|\.\d+)""").findAll(d).map { it.value }.toList()
        val p = Path2D.Double()
        var i = 0
        var command = 'M'
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0

        fun next() = tokens[i++].toDouble()
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) command = tokens[i++][0]
            val rel = command.isLowerCase()
            val ox = if (rel) x else 0.0
            val oy = if (rel) y else 0.0
            when (command.uppercaseChar()) {
                'M' -> {
                    x = ox + next()
                    y = oy + next()
                    p.moveTo(x, y)
                    startX = x
                    startY = y
                    command = if (rel) 'l' else 'L'
                }

                'L' -> {
                    x = ox + next()
                    y = oy + next()
                    p.lineTo(x, y)
                }

                'H' -> {
                    x = ox + next()
                    p.lineTo(x, y)
                }

                'V' -> {
                    y = oy + next()
                    p.lineTo(x, y)
                }

                'Q' -> {
                    val cx = ox + next()
                    val cy = oy + next()
                    x = ox + next()
                    y = oy + next()
                    p.quadTo(cx, cy, x, y)
                }

                'C' -> {
                    val c1x = ox + next()
                    val c1y = oy + next()
                    val c2x = ox + next()
                    val c2y = oy + next()
                    x = ox + next()
                    y = oy + next()
                    p.curveTo(c1x, c1y, c2x, c2y, x, y)
                }

                'A' -> {
                    val rx = next()
                    val ry = next()
                    val rotation = next()
                    val large = next() != 0.0
                    val sweep = next() != 0.0
                    val ex = ox + next()
                    val ey = oy + next()
                    arc(p, x, y, rx, ry, rotation, large, sweep, ex, ey)
                    x = ex
                    y = ey
                }

                'Z' -> {
                    p.closePath()
                    x = startX
                    y = startY
                }

                else -> {
                    error("Path command $command isn't supported")
                }
            }
        }
        return p
    }

    /**
     * An SVG elliptical arc (SVG 1.1, appendix F.6), appended to [p] from (x0, y0) to (x, y)
     */
    private fun arc(
        p: Path2D,
        x0: Double,
        y0: Double,
        rx0: Double,
        ry0: Double,
        rotation: Double,
        large: Boolean,
        sweep: Boolean,
        x: Double,
        y: Double,
    ) {
        val phi = Math.toRadians(rotation)
        val dx = (x0 - x) / 2
        val dy = (y0 - y) / 2
        val x1 = cos(phi) * dx + sin(phi) * dy
        val y1 = -sin(phi) * dx + cos(phi) * dy
        var rx = abs(rx0)
        var ry = abs(ry0)
        val scale = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
        if (scale > 1) {
            rx *= sqrt(scale)
            ry *= sqrt(scale)
        }
        val num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        val den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        val k = (if (large == sweep) -1 else 1) * sqrt(max(0.0, num / den))
        val cx1 = k * rx * y1 / ry
        val cy1 = -k * ry * x1 / rx
        val cx = cos(phi) * cx1 - sin(phi) * cy1 + (x0 + x) / 2
        val cy = sin(phi) * cx1 + cos(phi) * cy1 + (y0 + y) / 2
        val start = atan2((y1 - cy1) / ry, (x1 - cx1) / rx)
        var extent = atan2((-y1 - cy1) / ry, (-x1 - cx1) / rx) - start
        if (sweep && extent < 0) extent += 2 * PI
        if (!sweep && extent > 0) extent -= 2 * PI
        // Arc2D measures angles counter-clockwise with y up, so both are negated
        val arc =
            Arc2D.Double(-rx, -ry, rx * 2, ry * 2, -Math.toDegrees(start), -Math.toDegrees(extent), Arc2D.OPEN)
        val t = AffineTransform.getTranslateInstance(cx, cy).apply { rotate(phi) }
        p.append(t.createTransformedShape(arc).getPathIterator(null), true)
    }

    companion object {
        private const val EPSILON = 0.01
        private val DRAWN = setOf("path", "rect", "circle", "ellipse", "line", "polyline", "polygon")

        private const val OUTLINE = "#1F2747"

        /**
         * Navy, coral, sunny, teal, lilac and white: PekeTV's colours
         */
        private val PALETTE = setOf(OUTLINE, "#FF927E", "#FFDA70", "#65D9CE", "#B5A0EF", "#FFFFFF")
        private val RADII = setOf(4.0, 7.0, 9.0)

        /**
         * Icons whose small inner glyph is stroked at 3, where 3.5 would clog
         */
        private val THIN_DETAILS = setOf("play", "next-episode", "episodes")

        /**
         * Icons whose round outer shape may reach 3.5–44.5
         */
        private val ROUND = setOf("play", "pause", "next-episode", "settings", "time-left", "favourites", "bedtime", "search")

        /**
         * Icons with the white highlight: the round, shiny objects
         */
        private val HIGHLIGHTED = setOf("favourites", "search", "time-left", "voice")
    }
}
