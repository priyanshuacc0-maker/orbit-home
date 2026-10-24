package com.orbit.home.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.orbit.home.animation.WheelGeometry
import com.orbit.home.animation.WheelMotionState
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.orbit.home.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.orbit.home.apps.CentrePicture

/**
 * One shortcut as the ring draws it.
 *
 * A UI-layer type only: an [ImageBitmap] is not something to store, and the key is the only part
 * that ever reaches DataStore.
 */
@Immutable
data class RingShortcut(
    val key: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * The shortcut ring.
 *
 * A second wheel, built for shortcuts rather than apps. It mirrors `CircularAppWheel`'s geometry,
 * its placement and its spin loop, so the ring is the same size, sits in exactly the same place
 * and settles the same way — while staying its own component, so the wheel every other part of
 * the app depends on is not touched at all. A mistake here can make the shortcut ring look wrong;
 * it cannot break the home screen.
 *
 * Each slot is an [AppIcon] positioned with an offset, exactly as the app wheel does it, so a tap
 * on a shortcut comes from the icon's own click. That is why there is no hit test here: nothing
 * can disagree with the drawing about where a slot is.
 *
 * The centre is drawn as a half-disc. This is not decoration — the wheel's window is only the half
 * of the wheel's square that faces into the screen, so the centre sits exactly on the window's outer
 * edge and anything centred there is clipped to half. The plus therefore lives in the *visible* half
 * of the disc, and the disc's own width keeps it clear of the ring icons.
 *
 * Deliberately not a list of fake apps: a shortcut has a package, a shortcut id and a label, none
 * of which is an app id, and dressing one up as the other would quietly corrupt the icon cache,
 * the focus memory and the launcher.
 */
@Composable
fun ShortcutWheel(
    items: List<RingShortcut>,
    wheel: WheelMotionState,
    iconScale: Float,
    onLaunch: (RingShortcut) -> Unit,
    /** Accessibility only. The centre's pointer handling lives in [centreTaps]. */
    onAddForAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
    focusAngleDegrees: Float = 270f,
    radiusScale: Float = 1f,
    centreScale: Float = 1f,
    visibleCount: Int = WheelGeometry.DEFAULT_SLOTS,
    addLabel: String = "Add shortcuts",
    /** A centre the user chose, in place of the mark that would be there. */
    centrePicture: CentrePicture? = null
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberOrbitHaptics()
    // Read through rememberUpdatedState so a new list never restarts the gesture below.
    val currentItems by rememberUpdatedState(items)
    val count = items.size

    // This wheel had no haptics at all: the app wheel ticked per slot, the shortcut wheel was
    // silent. Same hook, same feel.
    DisposableEffect(wheel, haptics) {
        val tick: () -> Unit = { haptics.slotTick() }
        wheel.onSlotCrossed = tick
        onDispose { if (wheel.onSlotCrossed === tick) wheel.onSlotCrossed = null }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val minDim = minOf(w, h)
                    val centre = Offset(w / 2f, h / 2f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downDist = (down.position - centre).getDistance()

                    val ringOuter = wheel.ringOuterPx.takeIf { it > 0f } ?: (minDim * 0.45f)
                    val touchBound = ringOuter + WHEEL_TOUCH_MARGIN_DP.toPx()
                    if (downDist > touchBound) return@awaitEachGesture

                    // The centre belongs to centreTaps. Leaving it alone here is what stops the
                    // two handlers acting on the same touch.
                    if (downDist < centreHitRadius(minDim)) return@awaitEachGesture

                    // Nothing to spin.
                    if (currentItems.size < 2) return@awaitEachGesture

                    // Otherwise: spin, exactly as the app wheel does.
                    var lastAngle = atan2(down.position.y - centre.y, down.position.x - centre.x)
                    var dragging = false
                    val tracker = VelocityTracker()
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, Offset(wheel.offset, 0f))

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (dragging) {
                                change.consume()
                                wheel.startFling(scope, tracker.calculateVelocity().x)
                            }
                            break
                        }

                        val dist = hypot(change.position.x - centre.x, change.position.y - centre.y)
                        val angle = atan2(change.position.y - centre.y, change.position.x - centre.x)

                        if (!dragging) {
                            val moved = (change.position - down.position).getDistance()
                            if (moved > viewConfiguration.touchSlop && dist > minDim * 0.15f) {
                                dragging = true
                                wheel.cancelFling()
                                lastAngle = angle
                            }
                        }

                        if (dragging) {
                            change.consume()
                            val delta = angleDelta(lastAngle, angle)
                            lastAngle = angle
                            wheel.applyDrag(delta)
                            tracker.addPosition(change.uptimeMillis, Offset(wheel.offset, 0f))
                        }
                    }
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minDim = minOf(widthPx, heightPx)
        val geo = WheelGeometry(
            size = minDim,
            iconScale = iconScale,
            count = count,
            focusAngle = Math.toRadians((focusAngleDegrees - 90f).toDouble()).toFloat(),
            requestedCount = visibleCount,
            radiusScale = radiusScale,
            centreScale = centreScale,
            edgeMarginPx = with(density) { EDGE_MARGIN_DP.toPx() }
        )
        val iconDp = with(density) { geo.iconPx.toDp() }
        val centre = Offset(widthPx / 2f, heightPx / 2f)

        SideEffect {
            wheel.stepRadians = geo.step
            wheel.ringOuterPx = geo.radius + geo.iconPx / 2f
            wheel.rememberFocused(
                if (count > 0) currentItems[wrapIndex(wheel.offset.roundToInt(), count)].key else null
            )
        }

        // The centre: the same mark the app wheel draws, in the same place and at the same size,
        // so half of it sits past the screen edge exactly as the app wheel's does. The only
        // difference is the colours — white ring, purple planet instead of purple and white.
        //
        // Deliberately no disc behind it and no shrinking to fit the visible half: that would be
        // a different mark from the app wheel's, and the two are meant to read as one logo.
        //
        // Semantics only, no pointer input: the centre's taps belong to centreTaps, and a click
        // here would compete with it.
        val chosenCentre = centrePicture
        if (chosenCentre != null) {
            // The user's own centre, in the same place and at the same size as the mark it
            // replaces, so half of it still sits past the screen edge.
            Image(
                bitmap = chosenCentre.image,
                contentDescription = addLabel,
                contentScale = if (chosenCentre.crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(with(density) { geo.logoPx.toDp() })
                    .clip(CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = addLabel
                        onClick(label = addLabel) { onAddForAccessibility(); true }
                    }
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_orbit_logo_shortcut),
                contentDescription = addLabel,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(with(density) { geo.logoPx.toDp() })
                    .semantics {
                        role = Role.Button
                        contentDescription = addLabel
                        onClick(label = addLabel) { onAddForAccessibility(); true }
                    }
            )
        }

        // The ring. Placement is the app wheel's: the middle plus radius times the angle, with no
        // extra translate — slotCenter already includes the half-size, and doing both would put
        // every icon at twice its distance from the middle.
        if (count > 0) {
            val offsetValue = wheel.offset
            val baseF = floor(offsetValue)
            val base = baseF.toInt()
            val frac = offsetValue - baseF
            val shown = geo.visibleCount
            for (k in 0 until shown) {
                val j = k - shown / 2
                val item = currentItems[wrapIndex(base + j, count)]
                val angle = geo.focusAngle + (j - frac) * geo.step
                val x = centre.x + geo.radius * cos(angle)
                val y = centre.y + geo.radius * sin(angle)
                AppIcon(
                    label = item.label,
                    icon = item.icon,
                    size = iconDp,
                    onClick = { onLaunch(item) },
                    modifier = Modifier.offset {
                        IntOffset(
                            (x - geo.iconPx / 2f).roundToInt(),
                            (y - geo.iconPx / 2f).roundToInt()
                        )
                    }
                )
            }
        }
    }
}

/** Wraps a slot index into range, so the ring has no seam. */
private fun wrapIndex(index: Int, count: Int): Int =
    if (count <= 0) 0 else ((index % count) + count) % count

/** The shortest way round from one angle to another, so a spin cannot jump the long way. */
private fun angleDelta(from: Float, to: Float): Float {
    var delta = to - from
    while (delta > Math.PI) delta -= (2 * Math.PI).toFloat()
    while (delta < -Math.PI) delta += (2 * Math.PI).toFloat()
    return delta
}

/** How far past the ring a touch still belongs to the wheel. The app wheel's copy is private. */
private val WHEEL_TOUCH_MARGIN_DP = 16.dp

/** Space kept clear between the ring and the window edge. */
private val EDGE_MARGIN_DP = 4.dp
