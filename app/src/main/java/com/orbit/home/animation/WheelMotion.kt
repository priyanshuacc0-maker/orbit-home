package com.orbit.home.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

const val TWO_PI: Float = (2.0 * PI).toFloat()
const val HALF_PI: Float = (PI / 2.0).toFloat()

/**
 * Normalizes an angle into the (-PI, PI] range so that differences across the
 * angular wrap boundary stay small.
 */
fun normalizeAngle(radians: Float): Float {
    var a = radians % TWO_PI
    if (a <= -PI) a += TWO_PI
    if (a > PI) a -= TWO_PI
    return a
}

/** Signed shortest angular difference from [from] to [to]. */
fun angleDelta(from: Float, to: Float): Float = normalizeAngle(to - from)

/** Wraps an integer index into [0, count). Handles negative values. */
fun wrapIndex(index: Int, count: Int): Int {
    if (count <= 0) return 0
    val m = index % count
    return if (m < 0) m + count else m
}

/**
 * Holds the continuous wheel rotation offset (in item units) plus the discrete
 * angular step for the current layout, and mediates drag / fling motion.
 *
 * The offset is intentionally a single continuous float: the logical browsing
 * position is derived from it (rounded) rather than mutated directly, which is
 * what keeps icons from teleporting when the underlying index wraps.
 *
 * Drag updates are plain (non-suspending) writes so they can be called from
 * Compose's restricted pointer-gesture scope; the decay fling runs in a
 * supplied [CoroutineScope] and is cancelled whenever a new drag begins.
 */
class WheelMotionState(private val decaySpec: DecayAnimationSpec<Float>) {

    var offset by mutableFloatStateOf(0f)
        private set

    /** Radians per visible slot for the current layout; set on every layout pass. */
    var stepRadians: Float = 1f

    /**
     * Distance from the wheel centre to the outer edge of the icon ring, set on
     * every layout pass. The gesture layer uses it to tell a touch that landed on
     * the ring from one that did not (P0-10).
     */
    var ringOuterPx: Float = 0f

    /**
     * The shared rule for whether a tap may launch an app (remediation 1.3).
     *
     * Replaces the old public `interruptedCoast` flag: a tap that merely stops a
     * spinning wheel must not launch whatever slid under the finger (P0-08), and
     * the ring and the centre must agree on that.
     */

    /**
     * When false, a release stops the wheel where it is instead of coasting on.
     * Driven by the system's reduced-motion preference.
     */
    var coastingEnabled: Boolean = true

    /**
     * Called each time the wheel's nearest slot changes because of *motion*: a finger drag, a
     * fling, or the settle that follows. Never called for programmatic jumps ([focusOnIds],
     * [reset]), so a list edit cannot produce a phantom detent. The haptic tick hangs off this
     * rather than off recomposition, so it fires on the frame the slot is crossed and not on a
     * later frame, and cannot be restarted or dropped by a recomposition.
     */
    var onSlotCrossed: (() -> Unit)? = null

    private var flingJob: Job? = null

    /** Moves the offset as part of a gesture or animation, announcing any slot crossing. */
    private fun moveTo(newOffset: Float) {
        val before = offset.roundToInt()
        offset = newOffset
        if (offset.roundToInt() != before) onSlotCrossed?.invoke()
    }

    /** The app the wheel is currently showing, so a list change can keep it. */
    private var focusedId: String? = null

    /** Records which app the wheel is showing. */
    fun rememberFocused(id: String?) {
        focusedId = id
    }

    /**
     * Keeps the wheel pointing at the same app after the list changes. Removing
     * one app from the wheel used to throw the whole ring back to slot 0, which
     * made editing the selection disorienting (P1-02). Falls back to slot 0 when
     * that app is no longer there.
     */
    fun focusOnIds(ids: List<String>) {
        if (ids.isEmpty()) {
            reset()
            return
        }
        cancelFling()
        val keep = focusedId?.let { ids.indexOf(it) } ?: -1
        offset = (if (keep >= 0) keep else 0).toFloat()
    }

    /** Applies finger-driven rotation. Positive [deltaAngle] rotates the ring with the finger. */
    fun applyDrag(deltaAngle: Float) {
        if (stepRadians <= 0f) return
        moveTo(offset - deltaAngle / stepRadians)
    }

    /**
     * Called on every finger-down. Stops a coasting wheel immediately.
     *
     * This used to also record that the gesture had interrupted a coast, so the tap
     * that stopped the wheel was swallowed. That is gone: a tap on an icon launches
     * it, whether the wheel was spinning or not.
     */
    fun onPointerDown() {
        cancelFling()
    }

    /**
     * Brings the wheel to rest on an exact slot. Without this the ring stopped
     * wherever a fling decayed to, or wherever a slow release left it, so it
     * never settled on an icon (P0-08). Reduced motion snaps instead of animating.
     */
    fun settle(scope: CoroutineScope) {
        val target = offset.roundToInt().toFloat()
        if (offset == target) return
        if (!coastingEnabled) {
            offset = target
            return
        }
        flingJob?.cancel()
        flingJob = scope.launch {
            Animatable(offset).animateTo(target, settleSpec) { moveTo(value) }
        }
    }

    /** Continues rotation with decay after a fast release, then settles on a slot. */
    fun startFling(scope: CoroutineScope, velocityItemsPerSecond: Float) {
        if (!coastingEnabled || abs(velocityItemsPerSecond) < MIN_FLING_VELOCITY) {
            // Too slow to coast: still come to rest on an icon.
            settle(scope)
            return
        }
        flingJob?.cancel()
        flingJob = scope.launch {
            val anim = Animatable(offset)
            anim.animateDecay(velocityItemsPerSecond, decaySpec) {
                moveTo(value)
            }
            val target = offset.roundToInt().toFloat()
            if (offset != target) anim.animateTo(target, settleSpec) { moveTo(value) }
        }
    }

    /** Cancels any in-flight animation (called when a new gesture begins). */
    fun cancelFling() {
        flingJob?.cancel()
        flingJob = null
    }

    /** Cancels motion and returns the wheel to the first slot (used on mode change). */
    fun reset() {
        cancelFling()
        offset = 0f
    }

    companion object {
        /**
         * How quickly a spin loses speed, shared by every wheel — the home-screen
         * wheels, any wheel added later, and the floating shortcut wheel.
         *
         * Below 1 a flick keeps the wheel turning for a long time, which is the
         * fidget-spinner feel. Raise it to make the wheel settle sooner.
         */
        const val SPIN_FRICTION = 0.8f

        const val MIN_FLING_VELOCITY = 0.5f

        /** Firm but not springy, so the wheel lands rather than wobbles. */
        val settleSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }
}
