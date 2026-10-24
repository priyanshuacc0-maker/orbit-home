package com.orbit.home.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

/**
 * The centre's tap target.
 *
 * The same rule the app wheel uses privately — a fraction of the wheel with a floor in dp — so
 * the two agree about where the centre is. The app wheel's copy is private, so this is the one
 * copy the new files share. Keep them equal.
 */
internal fun Density.centreHitRadius(minDim: Float): Float = max(minDim * 0.14f, 48.dp.toPx())

/** What a press in the centre turned out to be. */
internal enum class CentreTap { NONE, SINGLE, DOUBLE }

/**
 * Watches a press that began in the centre and says what it was.
 *
 * Consumes nothing, and runs on the `Final` pass, so it observes touches without taking them
 * from anything else. That is what lets it sit alongside the ring's spin loop rather than
 * fighting it: the loop ignores a touch that starts in the centre, and this ignores everything
 * else, so the two never act on the same touch.
 *
 * The single is deliberately held back until the double-tap window has passed. A double tap that
 * also fired the single would open the picker on the way to changing mode, which is the exact
 * failure the brief warns about.
 */
internal suspend fun AwaitPointerEventScope.awaitCentreTap(
    down: PointerInputChange,
    isInCentre: (Offset) -> Boolean,
    pass: PointerEventPass
): CentreTap {
    if (!releasedAsTap(down, pass)) return CentreTap.NONE
    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown(requireUnconsumed = false, pass = pass)
    } ?: return CentreTap.SINGLE
    // A second press somewhere else on the wheel is not a double tap on the centre.
    if (!isInCentre(second.position)) return CentreTap.SINGLE
    return if (releasedAsTap(second, pass)) CentreTap.DOUBLE else CentreTap.SINGLE
}

/**
 * Whether a press ended as a tap: released, without wandering past the slop and without being
 * held long enough to be something else.
 */
private suspend fun AwaitPointerEventScope.releasedAsTap(
    down: PointerInputChange,
    pass: PointerEventPass
): Boolean {
    val slop = viewConfiguration.touchSlop
    val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
    while (true) {
        val event = awaitPointerEvent(pass)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (!change.pressed) return change.uptimeMillis <= deadline
        if ((change.position - down.position).getDistance() > slop) return false
    }
}

/**
 * Adds the centre's two taps to a wheel.
 *
 * Keyed on `Unit` and reading the callbacks through `rememberUpdatedState`, so changing mode or
 * the item list never restarts the gesture — restarting it would cancel a press in progress,
 * which is how a wheel ends up feeling like it ignored a tap.
 */
@Composable
internal fun Modifier.centreTaps(onSingle: () -> Unit, onDouble: () -> Unit): Modifier {
    val single by rememberUpdatedState(onSingle)
    val double by rememberUpdatedState(onDouble)
    return pointerInput(Unit) {
        awaitEachGesture {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            // The floating wheel has no vertical offset, so the centre is simply the middle.
            val centre = Offset(w / 2f, h / 2f)
            val hit = centreHitRadius(min(w, h))
            val inCentre = { p: Offset -> (p - centre).getDistance() < hit }

            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!inCentre(down.position)) return@awaitEachGesture

            when (awaitCentreTap(down, inCentre, PointerEventPass.Final)) {
                CentreTap.SINGLE -> single()
                CentreTap.DOUBLE -> double()
                CentreTap.NONE -> Unit
            }
        }
    }
}
