package com.orbit.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.InstalledApp
import com.orbit.home.ui.theme.OrbitColors
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/** The dock's icon size. Fixed: there is no appearance setting any more. */
private val DOCK_ICON_SIZE = 52.dp

/**
 * How far the dock must be pulled up before the drawer opens.
 *
 * The same distance as the strip below the dock uses, because it is the same gesture and the two
 * must not disagree.
 */
private val DOCK_SWIPE_UP = 48.dp

/** How far up, as a share of the whole pull, a drawer must be let go to stay open. */
internal const val DRAWER_OPEN_FRACTION = 0.1f

/** How fast an upward flick, in px/s, opens the drawer however short it was. */
internal const val DRAWER_FLING_VELOCITY = 400f

/** How fast a downward flick, in px/s, closes the open drawer. Higher than opening, so a close is deliberate. */
internal const val DRAWER_CLOSE_FLING_VELOCITY = 1200f

/** How long before the dock will open the drawer again after doing so once. */
private const val REARM_MILLIS = 500L

/**
 * The dock: up to five apps above the navigation bar.
 *
 * Dragging it upwards opens the all-apps drawer. Nothing on the dock changes when
 * that happens — a tap or long press still belongs to the icon under the finger.
 */
@Composable
fun DockRow(
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    onLaunch: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Called continuously while the dock is dragged upwards, with how far the pull has got from 0 to
     * 1, where 1 is the distance that means "open".
     *
     * Continuously, not once: the drawer follows the finger with it, so a half-finished pull leaves a
     * half-visible drawer instead of one that either arrived or did not.
     */
    onDrawerPull: (Float) -> Unit = {},
    /**
     * Called when the finger lifts: whether the drawer should open, and the finger's vertical velocity in
     * px/s (negative is up) so the drawer can carry on at the speed it was thrown.
     */
    onDrawerRelease: (Boolean, Float) -> Unit = { _, _ -> },
    /**
     * How far the finger has to travel for the drawer to be all the way up, in px. 0 means "use the old
     * short distance". The drawer slides in from the bottom, so for it to stay under the finger this has
     * to be most of the screen's height and not a few dp.
     */
    pullDistancePx: Float = 0f,
) {
    val density = LocalDensity.current
    val px = with(density) { DOCK_ICON_SIZE.roundToPx() }
    val openThreshold = with(density) { DOCK_SWIPE_UP.toPx() }
    val travel = if (pullDistancePx > 0f) pullDistancePx else openThreshold
    val haptics = rememberOrbitHaptics()
    // Guards against opening twice from one pull, and re-arms itself in case the
    // gesture is cancelled by the drawer appearing over the finger.
    var armed by remember { mutableStateOf(true) }

    LaunchedEffect(armed) {
        if (!armed) {
            delay(REARM_MILLIS)
            armed = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            // Hand-rolled rather than draggable, for the same reason the strip below the dock
            // is: draggable reports nothing until the touch slop has been passed, and the dim
            // has to start on the very first movement. Reading the pointer events directly
            // means the screen starts going darker the moment a finger on the dock moves.
            //
            // Read at the Initial pass and consumed only once the pull is clearly upward, so a
            // tap never gets taken from the icon underneath: it still opens its app.
            .pointerInput(travel) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val startY = down.position.y
                    val startX = down.position.x
                    val slop = viewConfiguration.touchSlop
                    var decided = false
                    var upward = false
                    var travelled = 0f
                    var pulling = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dy = change.position.y - startY
                            val dx = change.position.x - startX
                            if (!decided) {
                                // Half the usual slop, so the drawer starts on the first small movement; and
                                // a slightly diagonal swipe still counts as upward.
                                if (abs(dy) > slop * 0.5f || abs(dx) > slop * 0.5f) {
                                    decided = true
                                    upward = dy < 0f && abs(dy) * 1.4f > abs(dx)
                                }
                            }
                            if (!decided || !upward) continue
                            pulling = true
                            change.consume()
                            travelled = dy.coerceAtMost(0f)
                            if (!armed) continue
                            // How far the pull has got, as a share of the distance that means "open".
                            // Reported as it happens so the drawer can follow the finger; the decision is
                            // made when the finger lifts, not here.
                            onDrawerPull((-travelled / travel).coerceIn(0f, 1f))
                        }
                        // Let go. It opens if it was pulled far enough, or thrown fast enough, and it carries
                        // on at the speed the finger left it - so it settles like something with weight and
                        // not like something that was snapped.
                        if (pulling) {
                            pulling = false
                            val vy = tracker.calculateVelocity().y
                            val open = (-travelled / travel) > DRAWER_OPEN_FRACTION ||
                                vy < -DRAWER_FLING_VELOCITY
                            if (open) {
                                armed = false
                                haptics.confirm()
                            }
                            onDrawerRelease(open, vy)
                        }
                    } finally {
                        // Torn down mid-pull: settle the drawer rather than leave it parked half way.
                        if (pulling) onDrawerRelease((-travelled / travel) > DRAWER_OPEN_FRACTION, 0f)
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // No pill behind the icons: the dock sits on the wallpaper like everything else, and a
                // translucent slab under it reads as a shadow the dock does not cast.
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (app in apps) {
                val icon = rememberAppIcon(app.id, px, iconLoader)
                AppIcon(
                    label = app.label,
                    icon = icon,
                    size = DOCK_ICON_SIZE,
                    // A plain tap, with no double-tap hook anywhere. Waiting to see
                    // whether a second tap was coming is what used to make every tap
                    // feel slow, and there is nothing to offer on a second tap now.
                    // for a possible second tap before firing onClick, which is what
                    // made every single dock tap land a beat late. The dock's editor is
                    // offered by two taps on two different apps instead, tracked by the
                    // caller, so a tap opens its app immediately.
                    onClick = { onLaunch(app) }
                )
            }
        }
    }
}
