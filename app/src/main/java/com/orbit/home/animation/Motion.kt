package com.orbit.home.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * The app's single reduced-motion policy (directive Phase 9, remediation 3.14).
 *
 * Android expresses "reduce motion" as an animator duration scale: 0 means the
 * user has asked for no animation. Every transition in the app asks this object
 * rather than branching on the flag itself, so the policy cannot be applied in one
 * place and forgotten in another — which is exactly what had happened: the wheel
 * coast and three overlays honoured it, while the wheel action menu, the pager
 * scroll and the launch reveal did not.
 *
 * When motion is off, a change still *happens* — it simply arrives instantly
 * rather than sliding. Nothing becomes undiscoverable.
 */
object Motion {

    /** True when the system asks for animations to be switched off. */
    fun animationsDisabled(durationScale: Float): Boolean = durationScale <= 0f

    /** Enter transition for something fading in, such as the action menu. */
    fun enterFade(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None else fadeIn()

    /** Exit transition matching [enterFade]. */
    fun exitFade(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None else fadeOut()

    /**
     * Spec for a value animation. Returns a snap when motion is off, so the value
     * still reaches its target — it just does so in one frame.
     */
    fun <T> tween(reduced: Boolean, durationMillis: Int = 220): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = LinearOutSlowInEasing)

    /**
     * Whether the launch reveal (a clip animation growing from the tapped icon)
     * should play. With motion off the app simply opens, which is the same
     * information without the movement.
     */
    fun launchRevealEnabled(reduced: Boolean): Boolean = !reduced
}

/**
 * How stiff the drawer's rise and fall are.
 *
 * Higher is quicker. The framework's own medium is 1500, which was slightly slow to arrive for
 * something opened by a deliberate pull; the next named step up, StiffnessHigh, is 10000 and far
 * too abrupt. This sits between the two.
 */
private const val DRAWER_STIFFNESS = 2400f

/**
 * How long the drawer takes to fade while it rises.
 *
 * Shorter than the rise, so the fade finishes first and the last of the movement is the drawer
 * settling into place rather than appearing.
 */
private const val DRAWER_FADE_MS = 160
