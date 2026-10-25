package com.orbit.home.ui

import androidx.compose.foundation.Image
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.animation.WheelGeometry
import com.orbit.home.animation.WheelMotionState
import com.orbit.home.animation.angleDelta
import com.orbit.home.animation.wrapIndex
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.InstalledApp
import com.orbit.home.ui.theme.OrbitColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.orbit.home.apps.CentrePicture


/**
 * What the middle of the wheel shows.
 *
 * This used to be a user setting. It is now fixed: the focused app's icon when
 * there are apps, the Orbit logo when there are none. Keeping the enum means the
 * wheel's own layout code is untouched.
 */
enum class CentreMode { LOGO, APP }

/**
 * The circular app wheel.
 *
 * Gestures: drag the ring to spin, fling to coast. Holding the wheel's *actual
 * visible centre* opens the two-action menu (Customize / Move) — it never starts
 * moving the wheel by itself. Movement happens only in explicit move mode.
 *
 * The centre used for hit testing is exactly the centre the ring is drawn
 * around: `(width/2, height/2 + verticalOffset * height)`. Keeping those two in
 * agreement is what makes the wheel grabbable again after it has been moved.
 */
@Composable
fun CircularAppWheel(
    apps: List<InstalledApp>,
    wheel: WheelMotionState,
    iconScale: Float,
    iconLoader: AppIconLoader,
    onLaunch: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
    focusAngleDegrees: Float = 135f,
    /**
     * The visible region in the wheel's own coordinates, if it is drawn somewhere that
     * is cut off — the floating wheel is, since half of it is deliberately outside the
     * display. A slot whose icon would not fit entirely inside is not drawn, so a ring
     * can rotate against a screen edge without ever showing a sliced icon.
     *
     * Both boundaries matter: during the pull the window grows while the wheel moves,
     * so an icon can fail to fit on either side.
     */

    labelColor: Color = OrbitColors.TextPrimary,
    centreMode: CentreMode = CentreMode.LOGO,
    /**
     * A centre the user chose, drawn in the middle in place of whatever would have been there.
     *
     * On the home screen that middle shows the app you are on, so a picture chosen for it replaces
     * that icon. That is what makes the setting mean something: it is the home screen's centre that
     * can be changed, and a middle that went on showing the app would hide the choice.
     */
    centrePicture: CentrePicture? = null,
    /**
     * A video for the centre, drawn by the caller.
     *
     * A slot rather than a player: the wheel has no business knowing what a surface is, and the
     * settings previews and the floating wheel draw this same wheel without one. The modifier it is
     * handed is already the centre's square, clipped to a circle — so a video overflows it and is
     * cut exactly as a picture is.
     */
    centreVideo: (@Composable (Modifier) -> Unit)? = null,
    verticalOffset: Float = 0f,
    radiusScale: Float = 1f,
    centreScale: Float = 1f,
    visibleCount: Int = WheelGeometry.DEFAULT_SLOTS,
    /** True while explicit move mode is active for the wheel. */
    moveActive: Boolean = false,
    onMoveVertical: (Float) -> Unit = {},
    onMoveEnd: () -> Unit = {},
    /** A plain tap on the centre, used to open the app shown there (P0-09). */
    onCentreTap: (InstalledApp) -> Unit = {},
    /**
     * What a tap on the centre does when the centre is a video.
     *
     * A video in the middle is something to watch, so a tap stops it and a second tap starts it —
     * what a tap does in any player. Opening the app the wheel happens to be pointing at would be a
     * tap that does something other than what it is on.
     *
     * Null when there is no video, and then the centre behaves as it always did.
     */
    onCentreVideoTap: (() -> Unit)? = null,
    /**
     * One nudge of the wheel's position, for the screen reader.
     *
     * A hold on the centre is how the wheel is repositioned, and a hold is not something a
     * screen reader can perform. Without this the only way to move the wheel would be a
     * gesture, which is the one thing the accessibility guidance says not to leave as the
     * only route.
     */
    onMoveBy: (Float) -> Unit = {},
    moveUpLabel: String = "",
    moveDownLabel: String = "",
    /**
     * The empty-wheel call to action. Null now that there is no app picker — the
     * wheel shows every installed app, so it is only ever empty when the device
     * has none, and a button that does nothing is worse than no button (P0-13).
     */
    onAddApps: (() -> Unit)? = null,
    /**
     * When true, a gesture starting well outside the icon ring is left
     * unconsumed so the parent page swiper can take it.
     */
    passthroughOutsideRing: Boolean = false,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberOrbitHaptics()

    // Screen space puts 0 deg at +x (right) with y down, so shift by -90 to make
    // 0 deg = Top.
    val focusAngle = Math.toRadians((focusAngleDegrees - 90f).toDouble()).toFloat()

    // Latest values for the long-lived gesture loop. These are read through
    // state, so a settings change never restarts — and therefore cancels — a
    // gesture that is in progress.
    val currentOffset by rememberUpdatedState(verticalOffset)
    val currentMoveActive by rememberUpdatedState(moveActive)
    val currentApps by rememberUpdatedState(apps)
    val currentPassthrough by rememberUpdatedState(passthroughOutsideRing)
    val currentOnCentreTap by rememberUpdatedState(onCentreTap)
    val currentOnCentreVideoTap by rememberUpdatedState(onCentreVideoTap)
    val currentCentrePicture by rememberUpdatedState(centrePicture)
    val currentOnAddApps by rememberUpdatedState(onAddApps)
    val currentCentreMode by rememberUpdatedState(centreMode)
    val currentOnMoveVertical by rememberUpdatedState(onMoveVertical)
    val currentOnMoveEnd by rememberUpdatedState(onMoveEnd)
    val currentOnMoveBy by rememberUpdatedState(onMoveBy)

    // True from the moment a hold on the centre is recognised until the finger comes up. The
    // wheel shrinks a little while it is on: that is the whole message, and there is no text.
    var adjusting by remember { mutableStateOf(false) }

    BoxWithConstraints(
        // Keyed on Unit, not on apps.size: the old key restarted the gesture loop
        // whenever the app count changed, which could drop a drag that was already
        // in progress (P1-03). The changing values are read through
        // rememberUpdatedState instead.
        modifier = modifier
            .graphicsLayer {
                val shrink = if (adjusting) ADJUST_SCALE else 1f
                scaleX = shrink
                scaleY = shrink
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(moveUpLabel) { currentOnMoveBy(MOVE_STEP); true },
                    CustomAccessibilityAction(moveDownLabel) { currentOnMoveBy(-MOVE_STEP); true }
                )
            }
            .pointerInput(Unit) {
            val tracker = VelocityTracker()

            awaitEachGesture {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val minDim = min(w, h)
                // The effective centre: exactly where the ring is rendered.
                val centre = Offset(w / 2f, h / 2f + currentOffset * h)

                val down = awaitFirstDown(requireUnconsumed = false)
                // A finger-down stops a coasting wheel at once. It no longer forbids
                // this gesture from launching: tapping an icon opens it, spinning or
                // not.
                wheel.onPointerDown()
                val downDist = hypot(down.position.x - centre.x, down.position.y - centre.y)

                // The wheel is drawn inside a box the size of the whole area, so
                // without a bound a touch at the top of the screen, or beside the
                // dock, would spin it. A touch belongs to the wheel only when it
                // lands on the ring or just outside it. Anything further is left
                // alone — which also lets the page swiper take a horizontal drag
                // from out there, so a swipe beside the wheel still changes wheel.
                val ringOuter = wheel.ringOuterPx.takeIf { it > 0f } ?: (minDim * 0.45f)
                val touchBound = ringOuter + with(density) { WHEEL_TOUCH_MARGIN.toPx() }
                if (downDist > touchBound) return@awaitEachGesture

                // A touch well clear of the ring belongs to the page swiper.
                if (currentPassthrough && downDist > minDim * 0.52f) {
                    return@awaitEachGesture
                }
                // So does a swipe begun in the narrow strip along either screen
                // edge, which is where people reach for a page change (P0-10).
                val edgeZonePx = with(density) { EDGE_SWIPE_ZONE.toPx() }
                if (currentPassthrough &&
                    (down.position.x < edgeZonePx || down.position.x > w - edgeZonePx)
                ) {
                    return@awaitEachGesture
                }

                val centreTargetPx = density.centreHitRadius(minDim)

                if (currentMoveActive) {
                    // Explicit move mode: drag vertically from anywhere inside
                    // the ring, so a practical grab area is enough.
                    if (downDist > minDim * 0.55f) return@awaitEachGesture
                    var lastY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            currentOnMoveEnd()
                            break
                        }
                        change.consume()
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        currentOnMoveVertical(dy / h)
                    }
                    return@awaitEachGesture
                }

                if (downDist < centreTargetPx) {
                    // The centre does two things.
                    //
                    // A tap opens the app shown there. Before that, a tap on a large icon with
                    // a name under it did nothing at all, which read as a broken screen
                    // (P0-09). In logo mode there is nothing to open, so the tap stays inert.
                    //
                    // A hold frees the wheel instead: it shrinks a little to say so, and for as
                    // long as the finger is down the wheel can be dragged up and down to put it
                    // where the user wants it. That is how an icon is moved on a home screen,
                    // and it is the same idea — no text, no menu, the shrink is the message.
                    var released = false
                    var moving = false
                    var lastY = down.position.y
                    // An absolute deadline rather than a timer restarted by each sample. A
                    // finger held down still sends the odd event, and restarting the clock on
                    // every one would mean the hold never arrived for anyone whose hand is not
                    // perfectly still.
                    val holdUntil = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                    while (true) {
                        if (!moving) {
                            val left = holdUntil - SystemClock.uptimeMillis()
                            val event = if (left <= 0L) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                            if (event == null) {
                                // Held long enough, and not yet dragged: the wheel is free.
                                moving = true
                                adjusting = true
                                haptics.press()
                                lastY = down.position.y
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                break
                            }
                            continue
                        }
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        currentOnMoveVertical((change.position.y - lastY) / h)
                        lastY = change.position.y
                    }
                    if (moving) {
                        adjusting = false
                        currentOnMoveEnd()
                    } else if (released) {
                        val videoTap = currentOnCentreVideoTap
                        if (videoTap != null) {
                            // The middle is a video, so the tap belongs to the video.
                            videoTap()
                        } else if (currentCentrePicture == null &&
                            currentCentreMode == CentreMode.APP && currentApps.isNotEmpty()
                        ) {
                            // Nothing to open when the middle is the user's own picture: the app it
                            // would have opened is not the thing on screen, so opening it would be
                            // a surprise rather than a shortcut.
                            currentOnCentreTap(
                                currentApps[wrapIndex(wheel.offset.roundToInt(), currentApps.size)]
                            )
                        }
                    }
                    return@awaitEachGesture
                }

                // Otherwise: spin the ring, exactly as before.
                var lastAngle = atan2(down.position.y - centre.y, down.position.x - centre.x)
                var dragging = false
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
                        val dx = abs(change.position.x - down.position.x)
                        val dy = abs(change.position.y - down.position.y)
                        // A clearly horizontal drag that did not start on the icon
                        // ring is a page swipe. Handing it back here — before
                        // anything has been consumed — is what makes paging work
                        // across the whole wheel page instead of only the bands
                        // above and below the circle (P0-10).
                        val ringOuter =
                            if (wheel.ringOuterPx > 0f) wheel.ringOuterPx else minDim * 0.45f
                        if (currentPassthrough &&
                            moved > viewConfiguration.touchSlop &&
                            dx > dy * 1.7f &&
                            downDist > ringOuter
                        ) {
                            return@awaitEachGesture
                        }
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
        val minDim = min(widthPx, heightPx)
        val count = apps.size
        val center = Offset(widthPx / 2f, heightPx / 2f)
        val shiftY = verticalOffset * heightPx
        val edgeMargin = with(density) { 4.dp.toPx() }

        val geo = WheelGeometry(
            size = minDim,
            iconScale = iconScale,
            count = count,
            focusAngle = focusAngle,
            requestedCount = visibleCount,
            radiusScale = radiusScale,
            centreScale = centreScale,
            edgeMarginPx = edgeMargin
        )
        val radius = geo.radius
        val iconPx = geo.iconPx
        val shownCount = geo.visibleCount
        val step = geo.step
        // Publishing the step is a side effect, not composition work.
        SideEffect {
            wheel.stepRadians = step
            // The gesture layer needs to know where the ring ends (P0-10).
            wheel.ringOuterPx = radius + iconPx / 2f
            // So a later list change can keep showing the same app (P1-02).
            // Computed inline: focusIdx is declared further down this scope.
            wheel.rememberFocused(
                if (count > 0) apps[wrapIndex(wheel.offset.roundToInt(), count)].id else null
            )
        }

        // A tick as each slot goes past, so the wheel can be felt as well as seen (P1-01).
        // Driven by the motion itself, not by recomposition: the old LaunchedEffect ran a frame
        // late, was restarted on every slot of a fast spin (dropping ticks), and also fired when
        // the offset was moved by a list edit rather than by the user.
        DisposableEffect(wheel, haptics) {
            val tick: () -> Unit = { haptics.slotTick() }
            wheel.onSlotCrossed = tick
            onDispose { if (wheel.onSlotCrossed === tick) wheel.onSlotCrossed = null }
        }

        val offsetValue = wheel.offset
        val baseF = floor(offsetValue)
        val base = baseF.toInt()
        val frac = offsetValue - baseF
        val focusIdx = if (count > 0) wrapIndex(offsetValue.roundToInt(), count) else 0

        val iconPxInt = iconPx.roundToInt()
        val centerIconPxInt = geo.centerIconPx.roundToInt()

        // Warm the icon cache for the slots about to rotate into view (plus a
        // margin), and for the centre icon at its current size.
        // Asked for, not loaded here. `base` changes on every slot of a spin, so a
        // suspending load in this effect is cancelled mid-flight each time the wheel
        // moves — which is what made fast rotation look like it was loading. The
        // request hands the work to a queue that outlives this effect.
        LaunchedEffect(base, apps, iconPxInt, centerIconPxInt, shownCount) {
            val n = apps.size
            if (n > 0) {
                val ringWindow = shownCount + 4
                for (k in -ringWindow..ringWindow) {
                    iconLoader.request(apps[wrapIndex(base + k, n)].id, iconPxInt)
                }
                for (k in -2..2) {
                    iconLoader.request(apps[wrapIndex(base + k, n)].id, centerIconPxInt)
                }
            }
        }

        // Nothing selected. This used to be a blank screen with no explanation
        // and no way out, reachable simply by unticking everything in the wheel
        // editor or by a slow first scan (P0-13). Say what is wrong and offer
        // the fix.
        if (count == 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, shiftY.roundToInt()) }
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_orbit_logo),
                    contentDescription = null,
                    modifier = Modifier.size(with(density) { geo.logoPx.toDp() }),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.no_selection_title),
                    color = labelColor,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.no_selection_body),
                    color = labelColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (currentOnAddApps != null) {
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { currentOnAddApps?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrbitColors.Accent,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(stringResource(R.string.edit_selection_action))
                    }
                }
            }
            return@BoxWithConstraints
        }

        val videoCentre = centreVideo
        val chosenCentre = centrePicture
        if (videoCentre != null) {
            videoCentre(
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, shiftY.roundToInt()) }
                    .size(with(density) { geo.logoPx.toDp() })
                    .clip(CircleShape)
            )
        } else if (chosenCentre != null) {
            // Cropped to a circle the way a profile picture is, or fitted when it is one of our
            // own marks — those are drawn to sit inside their square.
            Image(
                bitmap = chosenCentre.image,
                contentDescription = null,
                contentScale = if (chosenCentre.crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, shiftY.roundToInt()) }
                    .size(with(density) { geo.logoPx.toDp() })
                    .clip(CircleShape)
            )
        } else if (centreMode == CentreMode.APP) {
            val focusedApp = apps[focusIdx]
            val centerIconDp = with(density) { geo.centerIconPx.toDp() }
            val centerIcon = rememberAppIcon(focusedApp.id, centerIconPxInt, iconLoader)
            // The icon alone, and nothing with it.
            //
            // The app's name used to sit under it inside a centred column, which pushed the icon up
            // by half the height of the name and the space above it. So the middle of the wheel was
            // never the middle of the icon: the one thing that is meant to look deliberate looked
            // slightly off, and the name was the reason.
            //
            // The name is not lost. The centre is the app the ring is pointing at, and that app
            // carries its name in the ring and in what a screen reader announces; the icon here says
            // it too, without drawing it.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, shiftY.roundToInt()) }
                    .size(centerIconDp)
            ) {
                if (centerIcon != null) {
                    Image(
                        bitmap = centerIcon,
                        contentDescription = focusedApp.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        } else {
            val logoDp = with(density) { geo.logoPx.toDp() }
            Image(
                painter = painterResource(R.drawable.ic_orbit_logo),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, shiftY.roundToInt()) }
                    .size(logoDp),
                contentScale = ContentScale.Fit
            )
        }

        // Visible ring icons — all drawn identically, at their original size and
        // opacity, wherever they sit on the ring.
        val iconDp = with(density) { iconPx.toDp() }
        for (k in 0 until shownCount) {
            val j = k - shownCount / 2
            val idx = wrapIndex(base + j, count)
            val app = apps[idx]
            val angle = focusAngle + (j - frac) * step
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle) + shiftY
            val icon = rememberAppIcon(app.id, iconPxInt, iconLoader)

            // Skipped rather than sliced: half an icon against the screen edge reads
            // as a rendering fault rather than as a wheel.
            // Every assigned slot is drawn, whatever the viewport currently shows of
            // it. Nothing here decides visibility: the overlay window clips, so an
            // icon emerges from behind the edge and retreats behind it as its pixels
            // move, instead of vanishing whole and reappearing when it happens to fit.
            AppIcon(
                label = app.label,
                icon = icon,
                size = iconDp,
                // A tap that only stopped a spinning wheel is swallowed, so it
                // cannot open whatever slid under the finger (P0-08).
                onClick = { onLaunch(app) },
                modifier = Modifier.offset {
                    IntOffset(
                        (x - iconPx / 2f).roundToInt(),
                        (y - iconPx / 2f).roundToInt()
                    )
                }
            )
        }
    }
}

/** The strip along each screen edge where a swipe always belongs to the pager. */
/**
 * How far beyond the ring a touch may land and still belong to the wheel.
 *
 * The ring's outer edge already includes the icons, so this is a small halo on top
 * of that — enough to make grabbing the wheel forgiving, not so much that the empty
 * space around it starts responding.
 */
private val WHEEL_TOUCH_MARGIN = 16.dp

private val EDGE_SWIPE_ZONE = 24.dp

/** The centre hit target is never smaller than this, whatever the wheel size. */
private val CENTRE_TARGET_MIN = 48.dp

/** How much the wheel shrinks while a hold has it free to be moved. Deliberately slight. */
private const val ADJUST_SCALE = 0.96f

/** How far one screen-reader nudge moves the wheel, as a fraction of the area's height. */
private const val MOVE_STEP = 0.03f
