package dev.aldoborrero.peke.kids

import androidx.compose.runtime.compositionLocalOf

/**
 * The current [KidsModeState], provided at the root of the UI
 */
val LocalKidsMode = compositionLocalOf<KidsModeState> { KidsModeState.Loading }

/**
 * Whether the device is locked to kid profiles ([KidsModeService.locked]), null until known; provided at the root of
 * the UI
 */
val LocalKidsLocked = compositionLocalOf<Boolean?> { null }

/**
 * Whether the current kid has live TV ([dev.aldoborrero.peke.kids.livetv.KidsLiveTvService.available]);
 * provided at the root of the UI
 */
val LocalKidsLiveTv = compositionLocalOf { false }
