package com.orbit.home.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Makes a full-screen overlay swallow touches that land on its own background.
 *
 * A plain `background()` colour paints pixels but registers no pointer node, so
 * an empty area of an overlay — a title bar, the space beside a Save/Cancel row,
 * header text — falls straight through to the wheel, the dock or the ⋮ button on
 * the home screen underneath. On the editors that is worse than it sounds: the
 * Save/Cancel row sits exactly over the dock (P0-06).
 *
 * This puts a pointer node on the overlay's own bounds, so the hit test stops
 * there instead of continuing to the screen below. It never consumes anything,
 * so the overlay's own rows and buttons still receive their events normally.
 */
fun Modifier.blockTouchesBehind(): Modifier = pointerInput(Unit) {}
