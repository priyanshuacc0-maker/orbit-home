package com.orbit.home.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.Settings
import android.os.Build
import android.net.Uri
import android.content.Intent
import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.home.R
import com.orbit.home.animation.Motion
import com.orbit.home.animation.WheelMotionState
import com.orbit.home.preferences.HomeSettings
import com.orbit.home.preferences.WheelLook
import com.orbit.home.ui.settings.SettingsArea
import com.orbit.home.preferences.CentreTarget
import com.orbit.home.ui.settings.CentreAdjustScreen
import com.orbit.home.ui.settings.SettingsCentrePage
import com.orbit.home.ui.settings.SettingsFloatingWheelPage
import com.orbit.home.ui.settings.SettingsRoute
import com.orbit.home.ui.settings.SettingsWallpaperPage
import com.orbit.home.ui.settings.SettingsWheelEntry
import com.orbit.home.ui.settings.SettingsWheelPage
import com.orbit.home.ui.theme.OrbitColors
import com.orbit.home.viewmodel.HomeViewModel
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.abs
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import android.view.TextureView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.orbit.home.preferences.CentreTransform
import androidx.compose.runtime.key
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.orbit.home.ui.TripleTapCounter
import androidx.compose.ui.res.painterResource

/**
 * The home screen: one or more wheels, a dock, and the all-apps drawer.
 *
 * Each wheel is its own page with its own apps and its own shape, so they are
 * genuinely independent rather than copies. Swiping moves between them; the dots
 * below the wheel show where you are. Holding the middle of a wheel opens that
 * wheel's controls, and the three-dot menu adds another wheel.
 */


/** How long two dock taps may be apart and still count as the dock's editor gesture. */

/**
 * How far up from the bottom edge the drawer's pull-up works.
 *
 * Sized to sit in the strip under the dock and over the navigation area, without
 * taking layout space from the dock itself.
 */
private val BELOW_DOCK_PULL_HEIGHT = 72.dp

/** How far to pull before the drawer opens. Matches the dock's own threshold feel. */
private val DRAWER_PULL = 48.dp

/**
 * How hard the drawer springs to whichever end the pull was heading for when it was let go.
 *
 * A spring rather than a duration: the drawer has weight, and it should land on the target rather than
 * arrive at a time.
 */
private const val DRAWER_SPRING = 600f

/**
 * Damping of that spring. 0.9 is just short of critical: it lands crisply with at most a whisper of
 * overshoot (the panel is clamped in place, so the overshoot is felt in the speed, not seen as a gap).
 * Stiffness 600 and damping 0.9 are the all-apps progress spring Launcher3 itself uses.
 */
private const val DRAWER_DAMPING = 0.9f

/** How far, as a share of the drawer's travel, a downward pull must go before it closes on release. */
private const val DRAWER_CLOSE_FRACTION = 0.25f

/** How much of the screen's height a finger travels to bring the drawer all the way up. */
private const val DRAWER_TRAVEL_FRACTION = 0.5f

/** How dark the home screen goes behind a fully raised drawer's edge, while it is moving. */
private const val DRAWER_SCRIM_ALPHA = 0.45f

/** How long the wheel must be still before the centre's video plays again. */
/**
 * How long the wheel takes to catch up with the finger while it is being moved.
 *
 * Short enough to feel attached to the finger, long enough that the stream of small movements a
 * drag produces reads as one motion rather than as steps.
 */
private const val MOVE_FOLLOW_MS = 90



@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The centres' pictures, read from storage rather than derived from a setting, so they arrive
    // on their own flow.
    val centrePictures by viewModel.centrePictures.collectAsStateWithLifecycle()
    // A picture picked but not yet framed, if there is one.
    val centreImport by viewModel.centreImport.collectAsStateWithLifecycle()
    // The home wheel's centre video, if it has one.
    val centreVideoSources by viewModel.centreVideos.collectAsStateWithLifecycle()
    // The hidden wallpaper, decoded and ready to draw.
    val hiddenWallpaper by viewModel.hiddenWallpaper.collectAsStateWithLifecycle()
    val haptics = rememberOrbitHaptics()


    // Friction is the only thing that decides how long a spin coasts. 1.5 stopped the
    // wheel in a couple of turns; under 1 it keeps going like a fidget spinner.
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = WheelMotionState.SPIN_FRICTION) }
    // One motion state per wheel, so each spins, coasts and settles on its own.
    val wheelStates = remember { mutableMapOf<Int, WheelMotionState>() }

    // The wheel a hold has freed to be moved, and where it has been dragged to. Held here
    // rather than written to settings on every frame: the wheel is drawn from this while the
    // finger is down, and the position is saved once, when the finger comes up.
    var movingPage by remember { mutableIntStateOf(-1) }
    var movingOffset by remember { mutableFloatStateOf(0f) }

    // The saved position is written to disk and comes back through the settings flow, so for a
    // moment after the finger comes up this screen is still holding the old one. Drawing that
    // old position for even a single frame is the flicker: the wheel snapped back to where it
    // had been and then jumped forward again. The draft is held until the saved value arrives,
    // and only then let go.
    LaunchedEffect(state.wheels, movingPage) {
        val page = movingPage
        if (page >= 0) {
            val saved = state.wheels.getOrNull(page)?.look?.offsetY
            if (saved != null && abs(saved - movingOffset) < 0.001f) movingPage = -1
        }
    }
    fun wheelAt(index: Int): WheelMotionState =
        wheelStates.getOrPut(index) { WheelMotionState(decay) }

    // Nothing is drawn until the settings have arrived. The state begins as a single empty wheel,
    // so a cold start would otherwise flash that wheel's "no apps" message before the apps appeared
    // — and the wallpaper showing for a moment is a calmer thing to see than a wrong message.
    val pageCount = if (state.settingsLoaded) state.wheels.size else 0
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // The pager is the source of truth for what is on screen; the view model is told
    // so the rest of the app agrees.
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentPage(pagerState.currentPage)
    }
    // A light detent as the home screen turns to another wheel. Skips the first value (the page
    // the screen opened on is not a page turn).
    var lastPageHaptic by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pagerState.currentPage, pageCount) {
        val page = pagerState.currentPage
        if (pageCount > 0) {
            if (lastPageHaptic >= 0 && lastPageHaptic != page) haptics.pageSnap()
            lastPageHaptic = page
        }
    }
    // ...and when something moves the current page (adding a wheel), the pager
    // follows it.
    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage)
        }
    }

    // The wheel whose controls are open, and the values being edited. Kept here
    // rather than in the view model so dragging a slider redraws the wheel without
    // writing to disk on every frame; the whole set is saved once, on close.
    // Saves the main wheel's pending shape and clears the draft. Used both when the
    // main customiser closes and when the shortcut customiser takes over from it, so
    // switching between them never discards unsaved changes.

    var menuOpen by remember { mutableStateOf(false) }

    // The wheel a removal is waiting on confirmation for, or -1. Removing loses that
    // wheel's apps and sizes, so it asks first.
    var pendingRemovePage by remember { mutableIntStateOf(-1) }

    // Which of the two things the shortcut wheel's Edit offers.

    // Which dock app was tapped last, and when, so two different ones in quick
    // succession can offer the dock's editor without any tap having to wait.
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message
    // Keyed on the id, not the text: the same failure twice in a row is two events.
    LaunchedEffect(state.messageId) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    // A press of the system Home button closes everything. The view model clears the
    // screens it owns; the pieces this screen owns are cleared here, and the
    // customiser's values are kept — exactly as its own ✕ would do.
    LaunchedEffect(state.homePresses) {
        if (state.homePresses > 0) {
            // Nothing this screen owns survives a Home press any more; the panels that
            // used to be cleared here are gone, and the rest is the view model's.
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    // How far a finger has to travel to bring the drawer all the way up: most of the screen, so the
    // drawer stays under the finger instead of arriving after a few millimetres.
    val drawerTravelPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx() * DRAWER_TRAVEL_FRACTION
    }

    /**
     * How far the drawer has come up, 0 to 1.
     *
     * The gestures write to it as the finger moves, so the drawer follows the pull; letting go springs
     * it the rest of the way to whichever end the pull was heading for. It is how far up the screen the
     * drawer panel is: 0 is below the bottom edge, 1 is in place.
     */
    val drawer = remember { Animatable(if (state.drawerOpen) 1f else 0f) }
    // Where the pull currently is, or -1 when no finger is on it.
    //
    // A plain value rather than snapping the drawer from the gesture: a pointer gesture runs in a
    // restricted scope and cannot launch a coroutine, and snapping a spring is suspending. The
    // gesture writes here and the effect below moves the drawer, which keeps the gesture free of
    // anything it is not allowed to do.
    var drawerPull by remember { mutableFloatStateOf(-1f) }
    // Where the last pull was let go, until the spring has picked it up. Between the finger leaving and the
    // spring's first frame the panel is drawn from this, so it never jumps back to where it was before the pull.
    var drawerHeld by remember { mutableFloatStateOf(-1f) }
    // What is on screen right now: the finger's pull while one is down, else the spring. Read at draw time
    // (inside graphicsLayer) so the panel moves in the same frame as the finger, with no effect in between,
    // and the whole home screen does not recompose on every move.
    // An app being carried out of the drawer onto the home grid. While it is set the drawer is kept
    // composed - but invisible - because the icon that owns the finger lives in it, and removing that
    // node would cancel the drag.
    var appDrag by remember { mutableStateOf<ExternalAppDrag?>(null) }
    // Where the carried app is. Kept apart from [appDrag] and read only at layout / inside the grid, so a
    // finger move no longer recomposes this whole screen (that was the stutter, unlike a drag within the grid).
    var appDragPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val drawerShown: () -> Float = {
        when {
            drawerPull >= 0f -> drawerPull
            drawerHeld >= 0f -> drawerHeld
            else -> drawer.value
        }
    }
    // Only changes when the drawer appears or goes, which is all composition needs to know.
    val drawerVisible by remember { derivedStateOf { drawerShown() > 0f || appDrag != null } }
    // Bumped when a pull is let go, so the spring runs even when the drawer's open state did not change
    // (a pull that was not far enough goes back to closed, which is "closed" both before and after).
    var drawerSettle by remember { mutableIntStateOf(0) }
    // Where the panel was when a pull down began, 0 to 1.
    var dismissBase by remember { mutableFloatStateOf(1f) }
    // The finger's speed at release, in drawer-lengths per second, handed to the spring.
    var drawerReleaseVelocity by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.drawerOpen, drawerSettle) {
        if (drawerPull < 0f) {
            if (drawerHeld >= 0f) {
                drawer.snapTo(drawerHeld)
                drawerHeld = -1f
            }
            drawer.animateTo(
                targetValue = if (state.drawerOpen) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = DRAWER_DAMPING,
                    stiffness = DRAWER_SPRING,
                    visibilityThreshold = 0.001f
                ),
                initialVelocity = drawerReleaseVelocity
            )
            drawerReleaseVelocity = 0f
        }
    }
    fun releaseDrawer(open: Boolean, velocityY: Float) {
        drawerReleaseVelocity = (-velocityY / drawerTravelPx).coerceIn(-10f, 10f)
        if (drawerPull >= 0f) drawerHeld = drawerPull
        drawerPull = -1f
        drawerSettle++
        viewModel.setDrawerOpen(open)
    }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Nothing plays while the screen is not the one being looked at.
    var appVisible by remember { mutableStateOf(true) }
    // Read from the lifecycle observer below, which is created once.
    val drawerOpenNow by rememberUpdatedState(state.drawerOpen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    appVisible = false
                    // A finger that was on a pull when the screen was taken away never gets to lift
                    // inside this app, so settle the drawer here or it is parked mid-way on return.
                    if (drawerPull >= 0f) releaseDrawer(open = drawerOpenNow, velocityY = 0f)
                }
                Lifecycle.Event.ON_START -> appVisible = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Asked for when the shortcut wheel is switched on. The service runs either
    // way; this only decides whether its notice is visible.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* either way the overlay works */ }

    // The keyboard has to go the moment the drawer starts to close. Letting the
    // search field be disposed at the end of the exit animation meant the keyboard
    // lingered well after the drawer had gone.
    LaunchedEffect(state.drawerOpen) {
        if (!state.drawerOpen) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    // Back closes whatever is on top, innermost first.
    BackHandler(enabled = state.editingShortcutWheel) { viewModel.setEditingShortcutWheel(false) }
    BackHandler(enabled = state.editingShortcutApps) { viewModel.setEditingShortcutApps(false) }
    BackHandler(enabled = !state.editingShortcutApps && !state.editingShortcutWheel && state.editingDock) {
        viewModel.setEditingDock(false)
    }
    BackHandler(enabled = !state.editingShortcutApps && !state.editingDock && state.editingApps) {
        viewModel.setEditingApps(false)
    }
    BackHandler(
        enabled = !state.editingDock && !state.editingApps &&
            state.drawerOpen
    ) {
        viewModel.setDrawerOpen(false)
    }

    // Registered after the editors on purpose. Compose hands Back to the innermost enabled
    // handler, so this has to come later in the composition to take precedence while
    // Settings is open — otherwise an editor handler left enabled would swallow it.
    //
    // Settings is the layer UNDER the pickers it opens (wheel apps, floating-wheel apps) and the
    // centre framing screen, so it only takes Back when none of those are showing. Otherwise Back
    // used to pop the Settings page hidden behind the picker and leave the picker on screen.
    BackHandler(
        enabled = state.settingsRoute != null &&
            !state.editingApps && !state.editingDock &&
            !state.editingShortcutApps && !state.editingShortcutWheel &&
            centreImport == null
    ) {
        if (state.settingsRoute == SettingsRoute.HOME) viewModel.closeSettings()
        else viewModel.setSettingsRoute(SettingsRoute.HOME)
    }

    // The centre framing screen is above everything, so Back cancels it first.
    BackHandler(enabled = centreImport != null) { viewModel.cancelCentreImport() }

    // The bottom of the app.
    //
    // On the home screen itself there is nothing for Back to close, and letting the press
    // through finishes the activity — which is what a HOME activity does by default. The
    // system then restarts it immediately, and because the manifest marks this activity as one
    // that does not need saved state, it comes back from scratch. The wheel's position lives in
    // the composition, so it was thrown back to its starting slot every single time Back was
    // pressed after spinning it.
    //
    // Back on a launcher's home screen should do nothing, which is what it does from Android 12
    // onwards anyway. Enabled only when nothing else is open, so it can never take a press from
    // a screen that has something to close.
    BackHandler(
        enabled = state.settingsRoute == null &&
            !state.drawerOpen &&
            !state.editingApps &&
            !state.editingDock &&
            !state.editingShortcutApps &&
            !state.editingShortcutWheel
    ) {
        // Nothing to close. Deliberately empty.
    }

    // Where the dock begins, so a tap on it is not a tap on the wallpaper behind it.
    var dockTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val tripleTap = remember { TripleTapCounter() }

    // True while anything is drawn over the home screen. The home content must not react to, or
    // be reachable by, touches or screen readers while this is set.
    val homeCovered = state.settingsRoute != null || state.drawerOpen || drawerVisible ||
        state.editingApps || state.editingDock ||
        state.editingShortcutApps || state.editingShortcutWheel ||
        centreImport != null
    // Read from inside the gesture lambda, which is created once.
    val homeCoveredNow by rememberUpdatedState(homeCovered)
    // Taps made before a panel opened must not count towards a triple tap after it closes.
    LaunchedEffect(homeCovered) { if (homeCovered) tripleTap.reset() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Three taps on the home screen, away from the wheel and the dock, swap the wallpaper.
            //
            // The wheel consumes the touches that land on it — it does that already — and the dock
            // says where it starts, so "the wallpaper" is everything left, which is what the gesture
            // is meant to mean. Nothing is consumed here, so a tap that a child has already taken
            // never reaches this at all.
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // A panel's empty space does not consume its taps, so they bubble up to
                    // this box. Without the check, three taps on a Settings page swapped the
                    // wallpaper behind it.
                    if (!homeCoveredNow && offset.y < dockTopPx &&
                        tripleTap.tap(SystemClock.uptimeMillis())
                    ) {
                        // The wallpaper swap is otherwise silent, and three taps with no
                        // feedback reads as nothing having happened.
                        haptics.confirm()
                        viewModel.toggleHiddenWallpaper()
                    }
                }
            }
            // Quickstep-style: swipe up on empty home space and the drawer follows the finger.
            //
            // Watches the normal pass, after the children, and only takes a gesture nobody else
            // has consumed: the wheel, dock, pager and icons keep their own touches, and this
            // picks up what lands on bare wallpaper. It waits for the touch slop and for the
            // movement to be mostly upward before claiming it, so horizontal paging and taps are
            // left alone. Release goes through the same spring as the dock and bottom-edge pulls.
            .pointerInput(drawerTravelPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    if (homeCoveredNow) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var claimed = false
                    var originY = down.position.y
                    var travelled = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!claimed && change.isConsumed) return@awaitEachGesture
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!claimed) {
                                if (abs(dy) < slop * 0.6f && abs(dx) < slop * 0.6f) continue
                                // Sideways or downward: not ours. A little diagonal still is.
                                if (dy > 0f || abs(dx) > abs(dy) * 1.2f) return@awaitEachGesture
                                claimed = true
                                originY = change.position.y
                            }
                            change.consume()
                            travelled = (change.position.y - originY).coerceAtMost(0f)
                            drawerPull = (-travelled / drawerTravelPx).coerceIn(0f, 1f)
                        }
                        if (claimed) {
                            claimed = false
                            val vy = tracker.calculateVelocity().y
                            releaseDrawer(
                                open = (-travelled / drawerTravelPx) > DRAWER_OPEN_FRACTION ||
                                    vy < -DRAWER_FLING_VELOCITY,
                                velocityY = vy
                            )
                        }
                    } finally {
                        // The gesture was torn down mid-pull (the handler restarted, the screen
                        // went away). Without this the drawer stays parked wherever the finger
                        // last was, which is the "stuck in the middle" a person sees.
                        if (claimed) {
                            releaseDrawer(
                                open = (-travelled / drawerTravelPx) > DRAWER_OPEN_FRACTION,
                                velocityY = 0f
                            )
                        }
                    }
                }
            }
    ) {
        // The hidden wallpaper, when it is the one showing.
        //
        // Drawn by the launcher rather than set as the phone's. That is what makes the switch
        // instant, keeps it to this app, and means a triple tap cannot repaint every other app on
        // the phone or be refused by anybody.
        val hidden = hiddenWallpaper
        if (state.hiddenWallpaperShowing && hidden != null) {
            Image(
                bitmap = hidden,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // The pull-up works below the dock as well as on it, which is where a thumb
        // reaching from the bottom actually lands.
        //
        // It is an overlay on the full-screen box, not a row in the dock's column:
        // as a row it occupied its own height and pushed the dock up off the bottom
        // edge, which left a gap. Overlaid, it takes no layout space at all, so the
        // dock sits exactly where it did before.
        //
        // How far down it reaches is the app's own window. With gesture navigation
        // that runs to the very bottom of the screen, behind the home indicator, so
        // the strip covers the full bottom edge. With three-button navigation the
        // system owns the bar itself and the strip stops at its top edge — an app
        // cannot receive touches on the system bar in either mode.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom))
                .height(BELOW_DOCK_PULL_HEIGHT)
                .pointerInput(drawerTravelPx) {
                    // Hand-rolled rather than detectVerticalDragGestures, because that waits
                    // for the touch slop before it reports anything at all, and the dim has
                    // to start on the very first movement to be worth having. Reading the
                    // pointer events directly means the screen starts going darker the
                    // moment the finger moves, instead of after the slop and then only once
                    // the line was already crossed.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val startY = down.position.y
                        var travelled = 0f
                        var decided = false
                        var upward = false
                        var pulling = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val dy = change.position.y - startY
                                if (!decided) {
                                    // A single pixel of movement is enough. Downward is not a
                                    // pull, so it is ignored rather than dimming the screen.
                                    if (abs(dy) > 1f) {
                                        decided = true
                                        upward = dy < 0f
                                    }
                                }
                                if (!decided || !upward) continue
                                // From here the gesture is ours: this strip is empty space, so
                                // nothing else wanted it.
                                change.consume()
                                pulling = true
                                travelled = dy.coerceAtMost(0f)
                                // Reported as it happens, so the drawer follows the finger.
                                drawerPull = (-travelled / drawerTravelPx).coerceIn(0f, 1f)
                            }
                            // Let go. Far enough, or fast enough, and it opens, carrying on at the speed the
                            // finger left it; short of that it goes back.
                            if (pulling) {
                                pulling = false
                                val vy = tracker.calculateVelocity().y
                                releaseDrawer(
                                    open = (-travelled / drawerTravelPx) > DRAWER_OPEN_FRACTION ||
                                        vy < -DRAWER_FLING_VELOCITY,
                                    velocityY = vy
                                )
                            }
                        } finally {
                            // Torn down mid-pull: settle rather than leave the drawer parked half way.
                            if (pulling) {
                                releaseDrawer(
                                    open = (-travelled / drawerTravelPx) > DRAWER_OPEN_FRACTION,
                                    velocityY = 0f
                                )
                            }
                        }
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                // Touch is blocked by the panels above; this stops a screen reader landing on the
                // hidden menu, wheel and dock behind them too.
                .then(if (homeCovered) Modifier.clearAndSetSemantics { } else Modifier)
        ) {
            // Top strip: the layout switch on the left, and the menu on the right.
            //
            // The switch shows what you are on rather than what pressing it will do — a wheel while
            // the home is the wheel, a grid while it is the grid — so it reads as a switch and not
            // as a command whose result you have to remember.
            //
            // It stands where a plain way into the drawer used to, which is no loss: the grid is
            // every app the drawer holds, so the icon is still a way to all of them that is not a
            // drag, and the dock's pull-up opens the drawer exactly as it always did.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleHomeGrid() }) {
                    Icon(
                        painter = painterResource(
                            if (state.homeGrid) R.drawable.ic_mode_wheel else R.drawable.ic_mode_grid
                        ),
                        contentDescription = stringResource(
                            if (state.homeGrid) R.string.home_layout_wheel
                            else R.string.home_layout_grid
                        ),
                        tint = OrbitColors.TextSecondary
                    )
                }
                Spacer(Modifier.weight(1f))
                ClockPill(
                    colourIndex = state.clockColour,
                    onPickColour = viewModel::setClockColour,
                    calcDark = state.calcDark,
                    onCalcDark = viewModel::setCalcDark
                )
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.menu_more),
                            tint = OrbitColors.TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_open)) },
                            onClick = {
                                menuOpen = false
                                viewModel.openSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_add_wheel)) },
                            onClick = {
                                menuOpen = false
                                // Shaped like the wheel being viewed, so a second
                                // wheel does not mean re-doing the sizes.
                                viewModel.addWheel(pagerState.currentPage)
                            }
                        )
                        // The first wheel is the home screen's own and cannot go, so
                        // removing is only offered once there is another one.
                        if (pagerState.currentPage > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_remove_wheel)) },
                                onClick = {
                                    menuOpen = false
                                    pendingRemovePage = pagerState.currentPage
                                }
                            )
                        }
                    }
                }
            }

            // The home screen itself: the wheel, or a grid of the same apps.
            if (state.homeGrid) {
                GridHome(
                    allApps = state.allApps,
                    homeApps = state.homeApps,
                    // Until the stored settings have loaded, homeInitialized is only its default
                    // (false). Reporting "initialised" until then stops the grid seeding a starting
                    // layout over an arrangement that simply has not been read yet.
                    homeInitialized = state.homeInitialized || !state.settingsLoaded,
                    onSaveLayout = { ids, cells, folders -> viewModel.setGridLayout(ids, cells, folders) },
                    order = state.gridOrder,
                    folders = state.gridFolders,
                    iconLoader = viewModel.iconLoader,
                    onLaunch = viewModel::launch,
                    externalDrag = appDrag,
                    externalPosition = { appDragPos },
                    onExternalDragDone = { appDrag = null },
                    reducedMotion = state.reducedMotion,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    val resolved = state.wheels.getOrNull(page) ?: return@HorizontalPager
                    val wheel = wheelAt(page)
                    val centreVideoSource = centreVideoSources.getOrNull(page)

                    // Each wheel owns its player. A page that is off-screen or covered is paused,
                    // and disposing/re-keying this page releases the player bound to its old file.
                    val centrePlayer = remember(centreVideoSource?.file) {
                        val file = centreVideoSource?.file ?: return@remember null
                        if (!file.isFile || file.length() <= 0L) return@remember null
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                            repeatMode = Player.REPEAT_MODE_ALL
                            volume = 0f
                            prepare()
                            // Where it had got to, if this wheel's video has been watched before.
                            val resumeAt = viewModel.centreVideoPosition(page)
                            if (resumeAt > 0L) seekTo(resumeAt)
                        }
                    }
                    DisposableEffect(centrePlayer) {
                        onDispose {
                            // Hand the position back before letting the player go, so coming back to this
                            // wheel carries on rather than starting again.
                            centrePlayer?.let { viewModel.rememberCentreVideoPosition(page, it.currentPosition) }
                            centrePlayer?.release()
                        }
                    }
                    // Whether this wheel's video is stopped, from the view model rather than from this page's own
    // memory, so leaving the wheel and coming back does not start it again.
    val centreVideoStopped by viewModel.centreVideoStopped.collectAsStateWithLifecycle()
    val centreVideoHeld = page in centreVideoStopped

                    // The wheel being edited draws from the draft, so moving a slider
                    // shows its effect on the wheel immediately. Every other wheel draws
                    // from what is saved. Without this, a change only appeared once the
                    // panel was closed and the draft was written.
                    // The stored look. There is no draft any more: the wheel is edited in
                    // Settings, which saves it, so the home screen always draws what is saved.
                    val wheelLook = resolved.look

                    // The wheel's position, eased. A drag sends a stream of small movements and
                    // following each one exactly makes the wheel read as stepping rather than
                    // sliding; a short ease turns that stream into one motion.
                    val drawnOffset by animateFloatAsState(
                        targetValue = if (movingPage == page) movingOffset else wheelLook.offsetY,
                        animationSpec = tween(
                            durationMillis = if (state.reducedMotion) 0 else MOVE_FOLLOW_MS,
                            easing = LinearOutSlowInEasing
                        ),
                        label = "wheelMove"
                    )

                    // Reduced motion: a release stops this wheel rather than coasting on.
                    SideEffect { wheel.coastingEnabled = !state.reducedMotion }

                    // Keep this wheel pointing at the same app when its contents change.
                    val ids = resolved.apps.map { it.id }
                    LaunchedEffect(ids) { wheel.focusOnIds(ids) }

                    // The centre's video plays whenever the home screen is the thing being looked at.
                    //
                    // It used to stop the moment the wheel moved, on the reasoning that a spin is when
                    // nobody is looking at the middle. But a wheel being turned is not a wheel being
                    // ignored - it is the opposite - and stopping the one thing in the middle every
                    // time it moves makes the centre look broken rather than calm. So it plays while
                    // the wheel turns.
                    //
                    // Nothing plays while a settings page or the drawer covers the home screen, or
                    // while the app is not on screen at all.
                    // Everything that covers the home screen: a settings page, the drawer, and the
                    // pickers and editors that open over it. The decoder has no business running
                    // behind a screen nobody is looking at.
                    val centreCovered = state.settingsRoute != null || state.drawerOpen ||
                        state.editingApps || state.editingDock ||
                        state.editingShortcutApps || state.editingShortcutWheel

                    LaunchedEffect(
                        centrePlayer, centreCovered, appVisible, state.reducedMotion,
                        centreVideoHeld, pagerState.currentPage, page
                    ) {
                        val player = centrePlayer ?: return@LaunchedEffect
                        if (page != pagerState.currentPage || centreCovered || !appVisible || centreVideoHeld) {
                            player.pause()
                            return@LaunchedEffect
                        }
                        if (state.reducedMotion) {
                            // The app's rule is that a system asking for less movement gets less: the
                            // wheel stops coasting and the move stops gliding. A video in the centre is
                            // the most movement there is, so it does not play. It is not left blank
                            // either — seeking while paused decodes a frame and holds it, so the centre
                            // is still rather than empty.
                            player.pause()
                            player.seekTo(0L)
                            return@LaunchedEffect
                        }
                        player.play()
                    }

                    CircularAppWheel(
                        apps = resolved.apps,
                        wheel = wheel,
                        iconScale = wheelLook.iconScale,
                        iconLoader = viewModel.iconLoader,
                        onLaunch = viewModel::launch,
                        modifier = Modifier.fillMaxSize(),
                        labelColor = Color.White,
                        centreMode = CentreMode.APP,
                        // A centre the user chose stands in the middle in place of the app icon. A
                        // video is drawn to a surface by the player, so it is handed over as a slot
                        // rather than as a picture.
                        centrePicture = centrePictures.home.getOrNull(page),
                        centreVideo = if (centrePlayer != null && centreVideoSource != null) {
                            { m -> CentreVideoView(centrePlayer, centreVideoSource.transform, m) }
                        } else {
                            null
                        },
                        // A tap on this wheel's video stops it and a second tap starts it.
                        onCentreVideoTap = if (centrePlayer != null && centreVideoSource != null) {
                            { viewModel.toggleCentreVideoStopped(page) }
                        } else {
                            null
                        },
                        // While a hold has this wheel free, it is drawn where the finger has put
                        // it; the rest of the time, where it was saved. Eased rather than followed
                        // exactly, so the wheel glides to where the finger is instead of stepping
                        // from one sample to the next.
                        verticalOffset = drawnOffset,
                        radiusScale = wheelLook.radiusScale,
                        centreScale = wheelLook.centreScale,
                        visibleCount = wheelLook.visibleCount,
                        onCentreTap = viewModel::launch,
                        onMoveVertical = { delta ->
                            // A hold, then a drag. The starting point is whatever the wheel was
                            // last saved at, so a second hold carries on from where the first left
                            // it rather than jumping.
                            if (movingPage != page) {
                                movingPage = page
                                movingOffset = wheelLook.offsetY
                            }
                            movingOffset = (movingOffset + delta)
                                .coerceIn(-WheelLook.MAX_OFFSET, WheelLook.MAX_OFFSET)
                        },
                        onMoveEnd = {
                            // Saved once, on release. Nothing is written if the wheel was not
                            // actually dragged: movingPage is only set by a movement. The draft is
                            // deliberately not cleared here — see the effect above.
                            val target = movingPage
                            if (target >= 0) {
                                viewModel.setWheelOffsetY(target, movingOffset)
                            }
                        },
                        // The screen reader's route to the same thing. A hold is not a gesture it
                        // can perform, so moving would otherwise be unreachable without one.
                        onMoveBy = { step ->
                            state.wheels.getOrNull(page)?.let { settled ->
                                viewModel.setWheelOffsetY(page, settled.look.offsetY + step)
                            }
                        },
                        moveUpLabel = stringResource(R.string.wheel_move_up),
                        moveDownLabel = stringResource(R.string.wheel_move_down),
                        passthroughOutsideRing = false
                    )
                }

                // Which wheel you are on. Only worth showing once there is more than one.
                if (state.wheels.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(state.wheels.size) { index ->
                            val here = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (here) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (here) OrbitColors.Accent else OrbitColors.Guide
                                    )
                            )
                        }
                    }
                }
            }


            if (state.dockApps.isNotEmpty()) {
                DockRow(
                    apps = state.dockApps,
                    iconLoader = viewModel.iconLoader,
                    // A tap opens the app, every time. The two-tap editor gesture is gone:
                    // it could not work, because the first tap launched its app before the
                    // second could be made. Settings, Dock apps is how the dock is changed.
                    onLaunch = viewModel::launch,
                    onDrawerPull = { far -> drawerPull = far },
                    // The finger is off it now, so the spring is in charge again.
                    onDrawerRelease = { open, velocityY -> releaseDrawer(open, velocityY) },
                    pullDistancePx = drawerTravelPx,
                    onReorder = viewModel::saveDockOrder,
                    onRemove = { app ->
                        viewModel.saveDockOrder(state.dockApps.map { it.id }.filter { it != app.id })
                    },
                    // Where the dock begins, so the triple tap knows where the wallpaper stops.
                    modifier = Modifier.onGloballyPositioned {
                        dockTopPx = it.positionInRoot().y
                    }
                )
            }
        }


        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        )

        // Slides up from the bottom edge under the finger, the way every launcher's drawer does. A fade
        // reads as one screen dissolving into another; a panel that rises reads as a surface being
        // pulled up over the home screen, and a pull that stops halfway leaves it half up.
        //
        // Behind it a dark scrim comes in with it, so the home screen recedes as the drawer arrives, and
        // the panel's top corners are round while it is moving and square once it is in place.
        if (drawerVisible) {
            Box(modifier = Modifier.fillMaxSize().blockTouchesBehind()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = drawerShown().coerceIn(0f, 1f) * DRAWER_SCRIM_ALPHA }
                        .background(Color.Black)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = drawerShown().coerceIn(0f, 1f)
                            // Carrying an app out: the drawer fades away with its slide, so the home
                            // screen is what the app is being dropped onto.
                            alpha = if (appDrag != null) p else 1f
                            translationY = size.height * (1f - p)
                            val corner = 28.dp.toPx() * (1f - p)
                            shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                            clip = true
                        }
                ) {
                AllAppsOverlay(
                    allApps = state.allApps,
                    iconLoader = viewModel.iconLoader,
                    onLaunch = viewModel::launch,
                    onClose = { viewModel.setDrawerOpen(false) },
                    // Pulling the open drawer down moves it with the finger, and letting go flings it
                    // closed or back open on the same spring as the opening pull, carrying the
                    // finger's speed.
                    onDismissDrag = { px ->
                        // The first move of a pull remembers where the panel is, so grabbing it while it is
                        // still moving does not make it jump to fully open.
                        if (drawerPull < 0f) dismissBase = drawerShown()
                        drawerPull = (dismissBase - px / drawerTravelPx).coerceIn(0f, 1f)
                    },
                    onDismissRelease = { px, vy ->
                        val at = (dismissBase - px / drawerTravelPx).coerceIn(0f, 1f)
                        releaseDrawer(
                            open = when {
                                vy > DRAWER_CLOSE_FLING_VELOCITY -> false
                                vy < -DRAWER_FLING_VELOCITY -> true
                                // Nothing was pulled: let it carry on to wherever it was already heading.
                                px <= 0f -> state.drawerOpen
                                else -> at > 1f - DRAWER_CLOSE_FRACTION
                            },
                            velocityY = vy
                        )
                    },
                    grid = state.drawerGrid,
                    onToggleGrid = { viewModel.setDrawerGrid(!state.drawerGrid) },
                    // The home screen's own drawer, so this is where an app can be put on it.
                    onAddToHome = { app -> viewModel.addAppToGridHome(app.id) },
                    homeAppIds = state.homeApps.toSet(),
                    // Hold and drag an icon out to put it on a grid home, as Quickstep does. Not offered
                    // for the wheel, or before the grid has been arranged (Add to home refuses then too).
                    onAppDragStart = if (state.homeGrid && state.homeInitialized) {
                        { app, at ->
                            appDragPos = at
                            appDrag = ExternalAppDrag(app.id, at.x, at.y)
                            viewModel.setDrawerOpen(false)
                        }
                    } else null,
                    onAppDragMove = { at -> appDragPos = at },
                    onAppDragEnd = { at ->
                        appDragPos = at
                        appDrag = appDrag?.copy(x = at.x, y = at.y, released = true)
                    },
                    onAppDragCancel = { appDrag = null }
                )
                }
            }
        }

        // The app under the finger while it is carried out of the drawer.
        run {
            val carry = appDrag
            val carryApp = carry?.let { d -> state.allApps.firstOrNull { it.id == d.id } }
            if (carry != null && carryApp != null && !carry.released) {
                val ghostW = 80.dp
                val ghostH = 96.dp
                val wPx = with(LocalDensity.current) { ghostW.toPx() }
                val hPx = with(LocalDensity.current) { ghostH.toPx() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset((appDragPos.x - wPx / 2f).roundToInt(), (appDragPos.y - hPx / 2f).roundToInt()) }
                        .width(ghostW)
                        .height(ghostH)
                        .graphicsLayer {
                            scaleX = 1.08f
                            scaleY = 1.08f
                            alpha = 0.92f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AppCell(app = carryApp, iconLoader = viewModel.iconLoader, onLaunch = {})
                }
            }
        }

        // The picker covers everything, including the controls that opened it.
        // Settings is a screen destination, drawn over the launcher rather than floating
        // over it. Rendered after the home content so nothing shows through and nothing
        // underneath takes a touch.
        state.settingsRoute?.let { route ->
            val backToList = { viewModel.setSettingsRoute(SettingsRoute.HOME) }
            // One touch-blocking layer for every Settings page. The pages are plain coloured
            // columns whose title bars and gaps register no pointer node, so touches there used to
            // fall through to the home screen's menu, clock, wheel and dock underneath.
            Box(modifier = Modifier.fillMaxSize().blockTouchesBehind()) {
                when (route) {
                    // The existing drawer, hosted here rather than rebuilt. Closing it returns
                    // to the Settings list; launching from it leaves Settings altogether, so
                    // coming back to Orbit Home lands on the launcher rather than a drawer that
                    // was left open behind a page the user has already left.
                    SettingsRoute.APPS -> AllAppsOverlay(
                        allApps = state.allApps,
                        iconLoader = viewModel.iconLoader,
                        onLaunch = { app ->
                            viewModel.closeSettings()
                            viewModel.launch(app)
                        },
                        onClose = backToList,
                        grid = state.drawerGrid,
                        onToggleGrid = { viewModel.setDrawerGrid(!state.drawerGrid) }
                    )

                    // The existing picker, capped at the dock's five, saving through the same
                    // path the home screen uses. No second store for dock apps.
                    SettingsRoute.DOCK -> EditAppsScreen(
                        allApps = state.allApps,
                        initialSelection = state.dockApps.map { it.id },
                        iconLoader = viewModel.iconLoader,
                        title = stringResource(R.string.settings_dock),
                        onSave = { ids ->
                            viewModel.setDockApps(ids)
                            backToList()
                        },
                        onCancel = backToList,
                        maxSelection = HomeSettings.DOCK_MAX
                    )

                    // The wheel page. The preview is the page's own, with its own motion
                    // state, so nothing here can spin or launch the real wheel.
                    SettingsRoute.APPS_WHEEL -> SettingsWheelPage(
                        wheels = state.wheels.map {
                            SettingsWheelEntry(apps = it.apps, look = it.look)
                        },
                        iconLoader = viewModel.iconLoader,
                        // Opens on the wheel the home screen is showing, so the thing being
                        // edited is the thing the user was just looking at.
                        initialIndex = state.currentPage,
                        matchHintSeen = state.wheelMatchHintSeen,
                        onSaveLook = { i, look -> viewModel.setWheelLook(i, look) },
                        onMatchAll = { look -> viewModel.matchAllWheelLooks(look) },
                        onMatchHintShown = { viewModel.markWheelMatchHintSeen() },
                        onEditApps = { i ->
                            viewModel.setSettingsWheelIndex(i)
                            viewModel.setEditingApps(true)
                        },
                        onBack = backToList,
                        // The same centre the home screen's wheel is drawn with, so the page's
                        // preview and the wheel behind it cannot disagree.
                        centrePictures = centrePictures.home
                    )

                    // The floating wheel. The demo is drawn inside the page and never asks
                    // the service for its window, so no overlay covers the controls.
                    SettingsRoute.FLOATING_WHEEL -> SettingsFloatingWheelPage(
                        apps = state.shortcutApps.mapNotNull { id ->
                            state.allApps.firstOrNull { it.id == id }
                        },
                        iconLoader = viewModel.iconLoader,
                        look = state.shortcutLook,
                        offsetY = state.shortcutOffsetY,
                        enabled = state.shortcutEnabled,
                        // From state, refreshed on every resume. Evaluated here it went stale
                        // after the permission screen and the toggle sent the user back to it.
                        canDrawOverlays = state.canDrawOverlays,
                        onRequestOverlayPermission = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                    )
                                )
                            }
                        },
                        onSave = { look, offset ->
                            viewModel.setShortcutLook(look)
                            viewModel.setShortcutOffsetY(offset)
                        },
                        onToggleEnabled = { want ->
                            // Same two permissions the home customiser asks for, in the same
                            // order: drawing over other apps, then notifications for the
                            // foreground service that keeps the wheel alive.
                            if (want && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.setShortcutEnabled(want)
                        },
                        onEditApps = { viewModel.setEditingShortcutApps(true) },
                        onBack = backToList,
                        // And the floating wheel's own centre for its demo.
                        centrePicture = centrePictures.floating
                    )

                    SettingsRoute.WALLPAPER -> SettingsWallpaperPage(
                        hiddenShowing = state.hiddenWallpaperShowing,
                        hiddenWallpaper = hiddenWallpaper,
                        onSetHidden = { uri -> viewModel.setHiddenWallpaper(uri) },
                        onToggleHidden = { viewModel.toggleHiddenWallpaper() },
                        onBack = backToList
                    )

                    // Choosing what the wheels' centres show. The preview inside draws its own wheel,
                    // so nothing here touches the home screen's.
                    SettingsRoute.CENTRE -> SettingsCentrePage(
                        pictures = centrePictures,
                        homeChoices = state.centreHome,
                        floatingChoice = state.centreFloating,
                        homeWheels = state.wheels.map { SettingsWheelEntry(apps = it.apps, look = it.look) },
                        initialHomeWheelIndex = state.currentPage,
                        // The floating preview is the floating wheel, so it gets that wheel's apps
                        // and its shape rather than the home screen's.
                        floatingApps = state.shortcutApps.mapNotNull { id ->
                            state.allApps.firstOrNull { it.id == id }
                        },
                        floatingLook = state.shortcutLook,
                        floatingOffsetY = state.shortcutOffsetY,
                        homeVideos = centreVideoSources,
                        reducedMotion = state.reducedMotion,
                        iconLoader = viewModel.iconLoader,
                        onChoose = { target, wheelIndex, choice ->
                            viewModel.setCentre(target, choice, wheelIndex)
                        },
                        onImport = { target, wheelIndex, uri ->
                            viewModel.beginCentreImport(target, uri, wheelIndex)
                        },
                        onBack = backToList
                    )

                    else -> SettingsArea(
                        route = route,
                        hapticsEnabled = state.hapticsEnabled,
                        onHapticsChange = viewModel::setHapticsEnabled,
                        onOpen = viewModel::setSettingsRoute,
                        onBack = {
                            if (route == SettingsRoute.HOME) viewModel.closeSettings()
                            else backToList()
                        }
                    )
                }
            }
        }

        // Framing a picture for a centre. Over everything, because it is a step in a task that is
        // not finished until it is kept or left, and nothing behind it should take a touch.
        val pendingCentre = centreImport
        if (pendingCentre != null) {
            Box(modifier = Modifier.fillMaxSize().blockTouchesBehind()) {
                CentreAdjustScreen(
                    // The still: the picture itself, or a video's opening frame.
                    image = pendingCentre.still,
                    onCancel = { viewModel.cancelCentreImport() },
                    onDone = { framed, framing -> viewModel.finishCentreImport(framed, framing) },
                    // The floating wheel's centre is half outside the screen, so its guide is half a
                    // circle: promising a whole one would promise what the screen cannot show.
                    edgeMounted = pendingCentre.target == CentreTarget.FLOATING
                )
            }
        }

        // The app picker, reached from either the home customiser or the Settings wheel
        // page. Whichever index that page was on is the one being edited.
        // Only Settings opens this now, so it is always the wheel that page was on.
        val pickerIndex = state.settingsWheelIndex
        if (state.editingApps && pickerIndex >= 0) {
            val page = state.wheels.getOrNull(pickerIndex)
            EditAppsScreen(
                allApps = state.allApps,
                // An untouched wheel opens fully ticked, because it is showing every
                // installed app.
                initialSelection = if (page?.initialized == true) {
                    page.selection
                } else {
                    state.allApps.map { it.id }
                },
                iconLoader = viewModel.iconLoader,
                title = stringResource(R.string.wheel_apps_title),
                onSave = { viewModel.setWheelApps(pickerIndex, it) },
                onCancel = { viewModel.setEditingApps(false) }
            )
        }

        if (pendingRemovePage > 0) {
            AlertDialog(
                onDismissRequest = { pendingRemovePage = -1 },
                title = { Text(stringResource(R.string.remove_wheel_title)) },
                text = { Text(stringResource(R.string.remove_wheel_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        val page = pendingRemovePage
                        pendingRemovePage = -1
                        viewModel.removeWheel(page)
                    }) {
                        Text(
                            text = stringResource(R.string.remove_wheel_confirm),
                            color = OrbitColors.Accent
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRemovePage = -1 }) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = OrbitColors.TextSecondary
                        )
                    }
                }
            )
        }

        if (state.editingShortcutWheel) {
            // Anything outside the panel closes it. This sits behind the panel, so the
            // panel's own controls win, and it also stops the touch reaching the home
            // screen underneath, which a panel that is "open" should not be driving.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { viewModel.setEditingShortcutWheel(false) }
                    }
            )
            // The floating wheel's own customiser. No app row — that is the other
            // half of the Edit choice — and no shortcut row, since it is the shortcut.
            WheelCustomizer(
                look = state.shortcutLook,
                onLookChange = { viewModel.setShortcutLook(it) },
                onEditApps = {},
                onClose = { viewModel.setEditingShortcutWheel(false) },
                showEditApps = false,
                showShortcutRow = false,
                // Move runs along the screen edge here, not within a page.
                // The same deliberate top-right placement as the standard customiser,
                // rather than the default, which left this one at the top-left.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(end = 12.dp, top = 56.dp)
            )
        }

        if (state.editingShortcutApps) {
            EditAppsScreen(
                allApps = state.allApps,
                // Untouched opens fully ticked, because it is showing every app.
                initialSelection = if (state.shortcutInitialized) {
                    state.shortcutApps
                } else {
                    state.allApps.map { it.id }
                },
                iconLoader = viewModel.iconLoader,
                title = stringResource(R.string.shortcut_apps_title),
                onSave = { viewModel.setShortcutApps(it) },
                onCancel = { viewModel.setEditingShortcutApps(false) }
            )
        }

        if (state.editingDock) {
            EditAppsScreen(
                allApps = state.allApps,
                // Only what is actually in the dock, so an app that has since been
                // uninstalled does not quietly hold one of the five places.
                initialSelection = state.dockApps.map { it.id },
                iconLoader = viewModel.iconLoader,
                title = stringResource(R.string.dock_apps_title),
                maxSelection = HomeSettings.DOCK_MAX,
                onSave = { viewModel.setDockApps(it) },
                onCancel = { viewModel.setEditingDock(false) }
            )
        }
    }
}

/**
 * How long the dock's "Edit dock" offer stays on screen: long enough to notice and
 * reach for, short enough that it never becomes clutter.
 */

/**
 * The centre's video, drawn by the player into a surface.
 *
 * The hardware decoder writes straight into this view, which is the whole reason it is smooth: no
 * frame is ever decoded by hand, converted, and drawn. The framing is applied as a layer transform
 * on the view — the same numbers the framing screen produced, as fractions of the frame, so they
 * mean the same thing at the centre's size as they did at the screen's.
 *
 * The video's own shape comes from the player rather than being stored, because the player is the
 * only thing that knows it for certain.
 */
@Composable
private fun CentreVideoView(player: ExoPlayer, transform: CentreTransform, modifier: Modifier) {
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // The frame's side, in dp: the transform is measured against it.
        val side = maxWidth
        // A quarter turn swaps which side of the video is its width.
        val turned = transform.quarterTurns % 2 != 0
        val across = if (turned) videoHeight else videoWidth
        val down = if (turned) videoWidth else videoHeight

        val drawnWidth = side * transform.widthFraction
        val drawnHeight = if (across > 0 && down > 0) {
            drawnWidth * (down.toFloat() / across)
        } else {
            drawnWidth
        }

        // Keyed on the player, so a new one gets a surface of its own.
        //
        // AndroidView's factory runs once, when the view is first created — so attaching the player
        // there attached the first one and never the second. Choosing another video built a new
        // player that was never given a surface, and the centre went blank; switching to a picture
        // only appeared to cure it because that removed this view, so the next video built a fresh
        // one.
        //
        // The key forces a new view per player, which also means no frame of the old video is left
        // on the surface, and update attaches on every pass besides — so the attachment is not
        // something that happens once and is then hoped for.
        key(player) {
            AndroidView(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = side * transform.offsetXFraction,
                        y = side * transform.offsetYFraction
                    )
                    .size(width = drawnWidth, height = drawnHeight)
                    .graphicsLayer { rotationZ = 90f * transform.quarterTurns },
                // A bare TextureView rather than a PlayerView: it is a surface and nothing else,
                // with no controller and no shutter to switch off. It composes with the wheel's
                // transforms and clipping, which a SurfaceView would not.
                factory = { ctx -> TextureView(ctx) },
                update = { view -> player.setVideoTextureView(view) },
                onRelease = { view -> player.clearVideoTextureView(view) }
            )
        }
    }
}

/**
 * The time and the date, in the top strip between the two icons.
 *
 * There rather than in the middle of the screen: the middle belongs to the wheel, and a clock that moved
 * with it would be a clock you had to look for.
 *
 * It ticks on the minute. A second hand would be a wakeup every second on a phone that is meant to be
 * calm, and nothing here is read at second resolution. The tick is worked out to land on the next minute
 * rather than a fixed second, so it cannot drift behind the clock it is showing.
 */
@Composable
private fun ClockPill(
    colourIndex: Int,
    onPickColour: (Int) -> Unit,
    calcDark: Boolean,
    onCalcDark: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var choosing by remember { mutableStateOf(false) }
    var calculating by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val colour = CLOCK_COLOURS.getOrElse(colourIndex) { CLOCK_COLOURS[3] }
    // On a light bar the text has to go dark, or the time is white on white.
    val ink = if (colourIndex >= 8) CLOCK_INK_ON_LIGHT else OrbitColors.TextPrimary
    val inkSoft = if (colourIndex >= 8) CLOCK_INK_ON_LIGHT_SOFT else OrbitColors.TextSecondary
    val timeText = now.format(CLOCK_TIME)
    val dateText = now.format(CLOCK_DATE)
    val clockDescription = stringResource(R.string.clock_pill_description, timeText, dateText)
    val chooseColourLabel = stringResource(R.string.clock_choose_colour)
    val openCalculatorLabel = stringResource(R.string.clock_open_calculator)
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colour)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            // Double tap opens the calculator, press and hold opens the colour chooser. Both are also
            // exposed as semantic actions so TalkBack and switch access can reach them.
            .semantics(mergeDescendants = true) {
                contentDescription = clockDescription
                role = Role.Button
                onClick(label = openCalculatorLabel) {
                    calculating = true
                    true
                }
                onLongClick(label = chooseColourLabel) {
                    choosing = true
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { calculating = true },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        choosing = true
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (calculating) {
            CalculatorDialog(
                dark = calcDark,
                onDarkChange = onCalcDark,
                onDismiss = { calculating = false }
            )
        }
        if (choosing) {
            ClockColourChooser(
                chosen = colourIndex,
                onPick = { onPickColour(it) },
                onDismiss = { choosing = false }
            )
        }
        Text(
            text = timeText,
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .width(1.dp)
                .height(22.dp)
                .background(inkSoft.copy(alpha = 0.5f))
        )
        Text(
            text = dateText,
            color = inkSoft,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** The ten, darkest first. The index is what is stored. */
private val CLOCK_COLOURS = listOf(
    0xFF000000, 0xFF0B0B0D, 0xFF141418, 0xFF1F1F25, 0xFF2B2B33,
    0xFF3C3C46, 0xFF5C5C68, 0xFF8C8C98, 0xFFD9D9DF, 0xFFFFFFFF
).map { Color(it) }

private val CLOCK_INK_ON_LIGHT = Color(0xFF101014)
private val CLOCK_INK_ON_LIGHT_SOFT = Color(0xFF3A3A44)

/**
 * The colours, under the bar.
 *
 * A popup rather than a menu, so a tap anywhere else closes it without a decision - which is what a
 * colour picker wants and what a menu does not give you.
 */
@Composable
private fun ClockColourChooser(chosen: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val progress = rememberPopProgress()
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            progress.popOut()
            onDismiss()
        }
    }
    val shape = RoundedCornerShape(28.dp)
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, 140),
        onDismissRequest = { dismiss() },
        properties = PopupProperties(focusable = true)
    ) {
        // The margin is part of the popup, so the panel never reaches the screen's edges.
        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .popTransform(
                        progress = { progress.value },
                        origin = TransformOrigin(0.5f, 0f),
                        from = 0.88f,
                        rise = (-10).dp
                    )
                    .clip(shape)
                    .background(OrbitColors.SurfaceHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.clock_bg_title),
                        color = OrbitColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { dismiss() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = OrbitColors.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    for (row in 0 until 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0 until 5) {
                                val index = row * 5 + col
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onPick(index) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(CLOCK_COLOURS[index])
                                            .then(
                                                if (index == chosen) {
                                                    Modifier.border(2.dp, Color.White, CircleShape)
                                                } else {
                                                    Modifier.border(1.dp, OrbitColors.Guide, CircleShape)
                                                }
                                            )
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(CLOCK_COLOUR_NAMES[index]),
                                        color = if (index == chosen) OrbitColors.TextPrimary else OrbitColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        minLines = 2,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        if (row == 0) Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

private val CLOCK_COLOUR_NAMES = listOf(
    R.string.clock_colour_0, R.string.clock_colour_1, R.string.clock_colour_2, R.string.clock_colour_3,
    R.string.clock_colour_4, R.string.clock_colour_5, R.string.clock_colour_6, R.string.clock_colour_7,
    R.string.clock_colour_8, R.string.clock_colour_9
)

private val CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm")
private val CLOCK_DATE = DateTimeFormatter.ofPattern("EEE, d MMM")
