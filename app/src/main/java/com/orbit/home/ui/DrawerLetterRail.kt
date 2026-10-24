package com.orbit.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.home.ui.theme.OrbitColors
import kotlin.math.roundToInt

/**
 * The A-Z strip down the drawer's right edge, as iOS's App Library list and Pixel's fast scroller
 * have it: touch it or drag along it and the list jumps to that letter, with a bubble showing which.
 *
 * It is a sibling of the list rather than a part of it, so it takes its own touches and never
 * competes with the list's scroll or the drawer's pull-down.
 */
@Composable
internal fun DrawerLetterRail(
    keys: List<String>,
    onScrub: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberOrbitHaptics()
    val currentKeys by rememberUpdatedState(keys)
    val currentScrub by rememberUpdatedState(onScrub)
    var active by remember { mutableStateOf<String?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableIntStateOf(1) }

    val bubble = 56.dp
    val bubblePx = with(LocalDensity.current) { bubble.roundToPx() }
    val gapPx = with(LocalDensity.current) { 12.dp.roundToPx() }

    // The strip is 24dp wide and most of the drawer's height; the bubble hangs off its left side.
    Box(modifier = modifier.fillMaxHeight(0.86f).width(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun hit(y: Float) {
                            fingerY = y
                            val k = currentKeys
                            val slot = DrawerIndex.slotAt(k.size, y / heightPx)
                            val key = k.getOrNull(slot) ?: return
                            if (key != active) {
                                active = key
                                haptics.slotTick()
                                currentScrub(key)
                            }
                        }
                        hit(down.position.y)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            hit(change.position.y)
                            change.consume()
                        }
                        active = null
                    }
                }
        ) {
            keys.forEach { key ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        color = if (key == active) OrbitColors.Accent else OrbitColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        val shown = active
        if (shown != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = -(bubblePx + gapPx),
                            y = (fingerY.roundToInt() - bubblePx / 2)
                                .coerceIn(0, (heightPx - bubblePx).coerceAtLeast(0))
                        )
                    }
                    .size(bubble)
                    .clip(RoundedCornerShape(18.dp))
                    .background(OrbitColors.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shown,
                    color = OrbitColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}
