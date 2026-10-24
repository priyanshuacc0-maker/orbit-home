package com.orbit.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.math.roundToInt
import androidx.compose.runtime.key
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.orbit.home.apps.DockLayout
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex

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

/** How far above the dock an icon has to be carried before it is out of it and onto the home screen. */
private val DRAG_OUT = 40.dp

/**
 * Where the dock's icons row is, in root coordinates, written by [DockRow] and read by whoever decides
 * whether something carried from outside should be dropped on it. Plain fields, not state: it is only
 * ever read at the moment of a drop.
 */
internal class DockGeometry {
    var left = 0f
    var width = 0f
    var icon = 0f
}

/** How the icons settle into their slots: quick, soft, no bounce. */
private val SlotSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 650f)

/**
 * The dock: up to five apps above the navigation bar.
 *
 * Dragging it upwards opens the all-apps drawer. Nothing on the dock changes when
 * that happens — a tap or long press still belongs to the icon under the finger.
 *
 * Icons are laid out by hand, one slot each, and glide between slots: reordering, an app being carried
 * in from outside (a gap opens where it would land) and an icon being let go all animate rather than
 * jump.
 */
@Composable
internal fun DockRow(
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
    /** The dock's apps in their new order, once an icon has been dragged to a new place. */
    onReorder: (List<String>) -> Unit = {},
    /** Take an app off the dock (not off the phone). */
    onRemove: (InstalledApp) -> Unit = {},
    /**
     * Carrying an icon up out of the dock onto the home screen. All positions are in root coordinates.
     * [onDragOut] fires once as the icon leaves the dock; the home screen then draws it and takes the
     * drop. Not offered (null) when there is no grid home to drop onto.
     */
    onDragOut: ((InstalledApp, Offset) -> Unit)? = null,
    onDragOutMove: (Offset) -> Unit = {},
    onDragOutEnd: (Offset) -> Unit = {},
    onDragOutCancel: () -> Unit = {},
    /**
     * Where an app being carried in from outside the dock is, in root coordinates - but only while it
     * is over the dock and the dock can take it; null otherwise. While it is set the icons part to leave
     * a gap where it would land. A lambda, so the finger moving does not recompose the dock.
     */
    incoming: () -> Offset? = { null },
    /**
     * The app that was carried out of this dock and is still on its way somewhere. Its slot stays, empty,
     * until the home screen has decided: if it lands it goes from the dock, if not it fades back in.
     */
    carriedOutId: String? = null,
    geometry: DockGeometry? = null,
) {
    val density = LocalDensity.current
    val px = with(density) { DOCK_ICON_SIZE.roundToPx() }
    val iconPx = px.toFloat()
    val openThreshold = with(density) { DOCK_SWIPE_UP.toPx() }
    val travel = if (pullDistancePx > 0f) pullDistancePx else openThreshold
    val haptics = rememberOrbitHaptics()
    // Guards against opening twice from one pull, and re-arms itself in case the
    // gesture is cancelled by the drawer appearing over the finger.
    var armed by remember { mutableStateOf(true) }

    // Hold an icon for its menu, or carry it along the dock to put it somewhere else.
    var order by remember(apps) { mutableStateOf(apps.map { it.id }) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    // While an icon is picked up the dock's own pull-up must not take the finger away from it.
    var carrying by remember { mutableStateOf(false) }
    var rowLeft by remember { mutableFloatStateOf(0f) }
    var rowWidth by remember { mutableFloatStateOf(0f) }
    var rowTop by remember { mutableFloatStateOf(0f) }
    // The app that has been carried up out of the dock. The home screen draws it now, so its slot here
    // is left empty rather than showing a second copy.
    var outId by remember { mutableStateOf<String?>(null) }
    val dragOutPx = with(density) { DRAG_OUT.toPx() }

    val currentIncoming by rememberUpdatedState(incoming)
    // The slot an app carried in from outside would take, or null when nothing is over the dock. Derived,
    // so the dock only recomposes when the gap moves, not on every pixel of the finger.
    val hole by remember {
        derivedStateOf {
            currentIncoming()?.let {
                DockLayout.insertionIndex(it.x - rowLeft, order.size, rowWidth, iconPx)
            }
        }
    }
    LaunchedEffect(hole) { if (hole != null) haptics.tap() }

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
                                // The full slop, the same one the long press tolerates. Half of it let a
                                // hand that was merely holding an icon (to pick it up) drift into a
                                // "pull", which took the finger away and killed the pick-up. A slightly
                                // diagonal swipe still counts as upward.
                                if (abs(dy) > slop || abs(dx) > slop) {
                                    decided = true
                                    upward = !carrying && dragId == null && dy < 0f && abs(dy) * 1.4f > abs(dx)
                                }
                            }
                            if (!decided || !upward) continue
                            // An icon that was picked up after the pull began wins: let go of the pull.
                            if (carrying || dragId != null) {
                                if (pulling) {
                                    pulling = false
                                    onDrawerRelease(false, 0f)
                                }
                                upward = false
                                continue
                            }
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
        // The row the icons live in: one icon tall, icons placed in it by hand.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // No pill behind the icons: the dock sits on the wallpaper like everything else, and a
                // translucent slab under it reads as a shadow the dock does not cast.
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(DOCK_ICON_SIZE)
                .onGloballyPositioned {
                    val at = it.positionInRoot()
                    rowLeft = at.x
                    rowTop = at.y
                    rowWidth = it.size.width.toFloat()
                    geometry?.let { g ->
                        g.left = at.x
                        g.width = it.size.width.toFloat()
                        g.icon = iconPx
                    }
                }
        ) {
            val byId = apps.associateBy { it.id }
            val shown = order.filter { it in byId }
            val slots = shown.size + if (hole != null) 1 else 0
            shown.forEachIndexed { position, id ->
                val app = byId.getValue(id)
                key(id) {
                    val icon = rememberAppIcon(app.id, px, iconLoader)
                    val callbacks = remember(id, apps) {
                        AppDragCallbacks(
                            onStart = { at ->
                                dragId = id
                                dragPos = at
                                menuFor = null
                            },
                            onMove = { at ->
                                dragPos = at
                                if (onDragOut != null && outId == null && at.y < rowTop - dragOutPx) {
                                    // Carried clear of the dock: the home screen takes it from here.
                                    outId = id
                                    onDragOut(app, at)
                                } else if (outId == id) {
                                    if (at.y > rowTop) {
                                        // Brought back down onto the dock: it stays a dock icon.
                                        outId = null
                                        onDragOutCancel()
                                    } else {
                                        onDragOutMove(at)
                                    }
                                }
                                // Which slot the finger is over, from where the evenly spaced slots are.
                                val n = order.size
                                if (outId == null && n > 1 && rowWidth > 0f) {
                                    val target = DockLayout.nearestSlot(at.x - rowLeft, n, rowWidth, iconPx)
                                    val from = order.indexOf(id)
                                    if (from >= 0 && target != from) {
                                        order = order.toMutableList().apply {
                                            removeAt(from)
                                            add(target, id)
                                        }
                                        haptics.tap()
                                    }
                                }
                            },
                            onEnd = { at ->
                                dragId = null
                                if (outId == id) {
                                    outId = null
                                    if (at.y > rowTop) {
                                        // Let go back over the dock without a move in between: it stays.
                                        onDragOutCancel()
                                    } else {
                                        onDragOutEnd(at)
                                    }
                                    order = apps.map { it.id }
                                } else if (order != apps.map { it.id }) {
                                    onReorder(order)
                                }
                            },
                            onCancel = {
                                dragId = null
                                if (outId == id) {
                                    outId = null
                                    onDragOutCancel()
                                }
                                order = apps.map { it.id }
                            },
                            onHold = { menuFor = id },
                            onPickUp = { up ->
                                carrying = up
                                if (!up) {
                                    // However the gesture ended, nothing may be left lifted or half-handed over.
                                    if (outId == id) {
                                        outId = null
                                        onDragOutCancel()
                                    }
                                    if (dragId == id) {
                                        dragId = null
                                        order = apps.map { it.id }
                                    }
                                }
                            }
                        )
                    }
                    val slot = DockLayout.slotFor(position, hole)
                    val targetLeft = if (rowWidth > 0f) DockLayout.slotLeft(slot, slots, rowWidth, iconPx) else 0f
                    DockSlot(
                        targetLeft = targetLeft,
                        ready = rowWidth > 0f,
                        dragging = dragId == id,
                        hidden = outId == id || carriedOutId == id,
                        fingerLeft = { dragPos.x - rowLeft - iconPx / 2f },
                        fingerTop = { dragPos.y - rowTop - iconPx / 2f },
                        modifier = Modifier.appDragSource(callbacks, early = true)
                    ) {
                        AppIcon(
                            label = app.label,
                            icon = icon,
                            size = DOCK_ICON_SIZE,
                            // A plain tap, with no double-tap hook anywhere: a tap opens its app at once.
                            onClick = { onLaunch(app) }
                        )
                        if (menuFor == id) {
                            DrawerAppMenu(
                                app = app,
                                onDismiss = { menuFor = null },
                                onAddToHome = null,
                                onRemove = { onRemove(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One dock slot: an icon that glides to wherever its slot is, follows the finger exactly while it is
 * picked up, and glides from the finger into its slot when let go (instead of jumping there).
 */
@Composable
private fun DockSlot(
    targetLeft: Float,
    ready: Boolean,
    dragging: Boolean,
    hidden: Boolean,
    fingerLeft: () -> Float,
    fingerTop: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }

    // While carried, keep the animation values on the finger, so that on release the glide to the slot
    // starts from where the icon actually is.
    LaunchedEffect(dragging) {
        if (dragging) {
            snapshotFlow { fingerLeft() to fingerTop() }.collect { (fx, fy) ->
                x.snapTo(fx)
                y.snapTo(fy)
            }
        }
    }
    LaunchedEffect(targetLeft, ready, dragging) {
        if (!ready || dragging) return@LaunchedEffect
        if (!placed) {
            x.snapTo(targetLeft)
            y.snapTo(0f)
            placed = true
        } else {
            launch { y.animateTo(0f, SlotSpring) }
            x.animateTo(targetLeft, SlotSpring)
        }
    }

    val scale by animateFloatAsState(if (dragging) 1.15f else 1f, label = "dockScale")
    val alpha by animateFloatAsState(
        targetValue = when {
            hidden || !placed -> 0f
            dragging -> 0.92f
            else -> 1f
        },
        label = "dockAlpha"
    )

    Box(
        modifier = modifier
            .size(DOCK_ICON_SIZE)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = if (dragging) fingerLeft() else x.value
                translationY = if (dragging) fingerTop() else y.value
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        content = content
    )
}
