package com.orbit.home.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration

/**
 * What a drawer icon reports while it is being dragged out. All positions are in ROOT coordinates, so
 * the home screen can read them without knowing where the drawer's list happens to be scrolled to.
 */
internal class AppDragCallbacks(
    val onStart: (Offset) -> Unit,
    val onMove: (Offset) -> Unit,
    val onEnd: (Offset) -> Unit,
    val onCancel: () -> Unit,
    /** The icon was held and let go without being carried anywhere: show its menu. */
    val onHold: () -> Unit,
    /** True the moment the icon is picked up, false when the gesture is over, however it ends. */
    val onPickUp: (Boolean) -> Unit = {}
)

/**
 * Press and hold, then drag, to carry an app out of the drawer.
 *
 * - A quick drag before the hold is not touched, so the list still scrolls.
 * - After the hold the touch is consumed, so the list stops scrolling and the tap handler underneath
 *   can never open the app when the finger lifts.
 * - Held and lifted without moving calls [AppDragCallbacks.onHold] (the app's menu), as before.
 *
 * This lives on the icon, which stays composed for the whole drag: the home screen hides the drawer
 * instead of removing it, because removing the node that owns the pointer would cancel the gesture.
 */
internal fun Modifier.appDragSource(
    callbacks: AppDragCallbacks?,
    /** Read the held touch before the icon's own tap handler, so a lift can never also launch the app. */
    early: Boolean = false
): Modifier {
    if (callbacks == null) return this
    return composed {
        val current by rememberUpdatedState(callbacks)
        val haptics = rememberOrbitHaptics()
        val slop = LocalViewConfiguration.current.touchSlop
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

        Modifier
            .onGloballyPositioned { coords = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    haptics.press()
                    current.onPickUp(true)
                    fun rootOf(local: Offset): Offset = coords?.localToRoot(local) ?: local

                    var dragging = false
                    var last = held.position
                    val origin = held.position
                    held.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent(if (early) PointerEventPass.Initial else PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == held.id }
                            if (change == null) {
                                if (dragging) current.onCancel()
                                return@awaitEachGesture
                            }
                            last = change.position
                            if (!change.pressed) {
                                change.consume()
                                if (dragging) current.onEnd(rootOf(last)) else current.onHold()
                                return@awaitEachGesture
                            }
                            if (!dragging && (last - origin).getDistance() > slop) {
                                dragging = true
                                current.onStart(rootOf(last))
                            }
                            if (dragging) {
                                current.onMove(rootOf(last))
                            }
                            change.consume()
                        }
                    } finally {
                        current.onPickUp(false)
                    }
                }
            }
    }
}
