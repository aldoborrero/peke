package dev.aldoborrero.peke.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * design/DESIGN.md: the canvas boards are drawn at 1280×720 and a TV lays out 960×540 dp, so 1 canvas px is 0.75 dp.
 * Layout code writes the board's own numbers, `320.cpx`, so a screen can be checked against its board line by line.
 */
const val CANVAS_PX = 0.75f

/**
 * A board's length in pixels, as dp
 */
val Int.cpx: Dp get() = (this * CANVAS_PX).dp

/**
 * A board's length in pixels, as dp
 */
val Float.cpx: Dp get() = (this * CANVAS_PX).dp

/**
 * A board's font size in pixels, as sp
 */
val Int.csp: TextUnit get() = (this * CANVAS_PX).sp

/**
 * The kids' sidebar, a shade darker than the page (`#1A2140` on the PekeTV boards)
 */
val PekePalette.rail: Color get() = lerp(background, Color.Black, RAIL_SHADE)

private const val RAIL_SHADE = .16f
