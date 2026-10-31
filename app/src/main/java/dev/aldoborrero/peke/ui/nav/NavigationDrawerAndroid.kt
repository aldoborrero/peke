/*
 * This file contains code copied and modified from:
 * https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-androidx-tv-material-release/tv/tv-material/src/main/java/androidx/tv/material3/
 *
 * Their license & required attribution is below.
 *
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.aldoborrero.peke.ui.nav

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Surface
import androidx.tv.material3.rememberDrawerState
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.theme.rail
import dev.aldoborrero.peke.ui.tryRequestFocus

/**
 * This is a re-implementation of [androidx.tv.material3.ModalNavigationDrawer].
 *
 * It changes padding, sizing, and animations that couldn't be changed in the Androidx implementation. Peke draws it as
 * the kids' sidebar of the canvas (HomeRailPekeTV, HomeDrawerPekeTV): a narrow rail in the rail colour that opens over
 * the page instead of pushing it.
 *
 * Peke keeps one drawer per nav entry, all sharing [drawerState]: only the [activeEntry] (the page on top) takes focus
 * or opens and closes the drawer, and when it opens, focus goes to [initialFocus], the selected item.
 */
@Composable
fun ModalNavigationDrawer(
    drawerContent: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    activeEntry: Boolean = true,
    initialFocus: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()

        // Over the page, which it covers while open
        DrawerSheet(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .zIndex(1f),
            drawerState = drawerState,
            content = drawerContent,
            activeEntry = activeEntry,
            initialFocus = initialFocus,
        )
    }
}

@Composable
private fun DrawerSheet(
    modifier: Modifier = Modifier,
    drawerState: DrawerState = remember { DrawerState() },
    activeEntry: Boolean = true,
    initialFocus: FocusRequester? = null,
    content: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
) {
    // indicates that the drawer has been set to its initial state and has grabbed focus if
    // necessary. Controls whether focus is used to decide the state of the drawer going forward.
    var initializationComplete: Boolean by remember { mutableStateOf(false) }
    var focusState by remember { mutableStateOf<FocusState?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(drawerState.currentValue, activeEntry) {
        if (activeEntry && drawerState.currentValue == DrawerValue.Open && focusState?.hasFocus == false) {
            // used to grab focus if the drawer state is set to Open on start. Peke: on the selected item, never the
            // sheet's first item, which is the kid's profile and leads to the parent gate (spec 005 FR-013)
            if (initialFocus != null) initialFocus.tryRequestFocus() else focusRequester.requestFocus()
        }
        initializationComplete = true
    }

    val open = drawerState.currentValue == DrawerValue.Open
    // Both are read in the layout phase, so the animation doesn't recompose the sheet every frame
    val width =
        animateDpAsState(
            targetValue = if (open) DrawerOpenWidth else DrawerRailWidth,
            animationSpec = spring(stiffness = DrawerAnimationStiffness),
            label = "drawer width",
        )
    val sidePadding =
        animateDpAsState(
            targetValue = if (open) DrawerOpenPadding else (DrawerRailWidth - DrawerTileSize) / 2,
            animationSpec = spring(stiffness = DrawerAnimationStiffness),
            label = "drawer padding",
        )
    val internalModifier =
        Modifier
            .focusRequester(focusRequester)
            .fillMaxHeight()
            .layout { measurable, constraints ->
                val sheetWidth = width.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                val placeable = measurable.measure(constraints.copy(minWidth = sheetWidth, maxWidth = sheetWidth))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            // adding passed-in modifier here to ensure the size is set before other size based modifiers.
            .then(modifier)
            .background(LocalPekeColors.current.rail)
            .layout { measurable, constraints ->
                val side = sidePadding.value.roundToPx()
                val placeable = measurable.measure(constraints.offset(horizontal = -2 * side))
                layout(constraints.constrainWidth(placeable.width + 2 * side), placeable.height) {
                    placeable.placeRelative(side, 0)
                }
            }.padding(vertical = 32.cpx)
            .onFocusChanged {
                focusState = it

                // Peke: only the entry on screen drives the drawer state it shares with the other entries
                if (initializationComplete && activeEntry) {
                    drawerState.setValue(if (it.hasFocus) DrawerValue.Open else DrawerValue.Closed)
                }
            }.focusProperties {
                // Focus coming in from the page, as Left from a kid page, lands on the selected item like opening does,
                // never on whatever is level with it, such as the kid's profile next to the search box
                onEnter = { initialFocus?.tryRequestFocus() }
            }.focusGroup()

    Box(modifier = internalModifier) {
        remember(open) { NavigationDrawerScopeImpl(open) }.apply {
            content(drawerState.currentValue)
        }
    }
}

internal class NavigationDrawerScopeImpl(
    override val hasFocus: Boolean,
) : NavigationDrawerScope

/**
 * The closed rail: 104 canvas px
 */
internal val DrawerRailWidth = 104.cpx

/**
 * The open sheet: 380 canvas px
 */
internal val DrawerOpenWidth = 380.cpx

/**
 * The sheet's padding on each side while open: 24 canvas px
 */
internal val DrawerOpenPadding = 24.cpx

/**
 * A rail tile: 64 canvas px
 */
internal val DrawerTileSize = 64.cpx

/**
 * The icons: 36 canvas px
 */
internal val DrawerIconSize = 36.cpx
internal val DrawerAnimationStiffness = Spring.StiffnessMedium

/**
 * Room above and below the sidebar's list for a focused row's border and ring, which the list would otherwise cut
 */
internal val DrawerRingRoom = PekeFocus.clearance(DrawerTileSize, grow = false)

/**
 * Lets the sidebar's list draw [DrawerRingRoom] past its top and bottom, given as its content padding, without moving its
 * rows: a lazy list clips what its rows draw past its edges, such as the first and last rows' focus rings
 */
internal fun Modifier.drawerRingRoom(): Modifier =
    layout { measurable, constraints ->
        val room = DrawerRingRoom.roundToPx()
        val placeable =
            measurable.measure(
                constraints.copy(
                    minHeight = constraints.minHeight + 2 * room,
                    maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + 2 * room else Constraints.Infinity,
                ),
            )
        layout(placeable.width, placeable.height - 2 * room) { placeable.place(0, -room) }
    }

/**
 * An entry of the sidebar: a 64 px tile with its icon while the rail is closed, a row with the icon and the name while
 * open. The page you're on ([current]) sits on the surface colour; the focused entry is primary, with "on primary" for
 * its icon and text ([LocalContentColor]), and Peke's focus border and ring. Every entry keeps one geometry in every state:
 * focus doesn't grow it, so its icon and name stay put, and its ring fits inside the sidebar's padding.
 */
@Composable
internal fun NavigationDrawerScope.NavigationDrawerItem(
    onClick: () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    height: Dp = DrawerTileSize,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = LocalPekeColors.current
    val shape = RoundedCornerShape(22.cpx)
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (current) colors.surface else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = colors.text,
                focusedContainerColor = colors.primary,
                focusedContentColor = colors.onPrimary,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .pekeFocus(shape, grow = false),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (hasFocus) Arrangement.spacedBy(18.cpx) else Arrangement.Center,
            // The whole tile, so the closed rail centres every icon on the same line
            modifier = Modifier.fillMaxSize().padding(horizontal = if (hasFocus) 16.cpx else 0.dp),
        ) {
            leadingContent()
            if (hasFocus) {
                Column(Modifier.weight(1f)) { content() }
            }
        }
    }
}

/**
 * The text colour of a sidebar entry: "on primary" while focused, the text colour for the page you're on, "text,
 * secondary" otherwise
 */
@Composable
internal fun drawerTextColor(
    current: Boolean,
    focused: Boolean,
) = when {
    focused -> LocalPekeColors.current.onPrimary
    current -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
