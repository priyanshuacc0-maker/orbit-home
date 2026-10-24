package com.orbit.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one motion every floating surface (colour menu, calculator, open folder, app menu) uses, so they
 * all arrive the same way.
 *
 * In: a soft spring that grows the surface out of the thing it belongs to, with the faintest overshoot.
 * Out: a short ease-in, quicker than the way in, because leaving should never make you wait.
 */
internal const val POP_EXIT_MS = 140

/** 0 while hidden, 1 when fully shown; plays the entrance as soon as [ready] is true. */
@Composable
internal fun rememberPopProgress(ready: Boolean = true): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(ready) {
        if (ready) progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 380f))
    }
    return progress
}

/** Plays the exit. The caller removes the surface once this returns. */
internal suspend fun Animatable<Float, androidx.compose.animation.core.AnimationVector1D>.popOut() {
    animateTo(0f, tween(POP_EXIT_MS, easing = FastOutLinearInEasing))
}

/**
 * Scales, fades and slides a surface by [progress], growing from [origin].
 *
 * [rise] is how far it travels as it arrives: positive comes up from below, negative down from above.
 */
internal fun Modifier.popTransform(
    progress: () -> Float,
    origin: TransformOrigin = TransformOrigin.Center,
    from: Float = 0.9f,
    rise: Dp = 0.dp
): Modifier = graphicsLayer {
    val p = progress()
    val s = from + (1f - from) * p
    scaleX = s
    scaleY = s
    alpha = (p * 1.6f).coerceIn(0f, 1f)
    translationY = (1f - p) * rise.toPx()
    transformOrigin = origin
}
