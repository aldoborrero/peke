package dev.aldoborrero.peke.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Shapes

/**
 * design/DESIGN.md's corners at 0.75 dp per canvas pixel: small 16 px (chips, small cards), medium 24 px (home cards,
 * posters, list rows), large 36 px (dialogs). Pills and avatars use CircleShape.
 */
val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(27.dp),
        extraLarge = RoundedCornerShape(27.dp),
    )
