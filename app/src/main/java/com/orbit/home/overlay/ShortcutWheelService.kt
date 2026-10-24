package com.orbit.home.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.orbit.home.R
import com.orbit.home.animation.WheelMotionState
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.AppLauncher
import com.orbit.home.apps.AppRepository
import com.orbit.home.apps.InstalledApp
import com.orbit.home.diagnostics.Diagnostics
import com.orbit.home.preferences.AppPreferences
import com.orbit.home.preferences.HomeSettings
import com.orbit.home.ui.CentreMode
import com.orbit.home.ui.CircularAppWheel
import com.orbit.home.ui.rememberOrbitHaptics
import com.orbit.home.ui.theme.OrbitColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import com.orbit.home.apps.CircleIcon
import com.orbit.home.FloatingAppsPickerActivity
import com.orbit.home.ShortcutPickerActivity
import com.orbit.home.shortcuts.ShortcutCatalog
import com.orbit.home.shortcuts.ShortcutCatalogResult
import com.orbit.home.shortcuts.ShortcutLauncher
import com.orbit.home.shortcuts.ShortcutLaunchResult
import com.orbit.home.shortcuts.ShortcutSelection
import com.orbit.home.ui.RingShortcut
import com.orbit.home.ui.ShortcutWheel
import com.orbit.home.ui.centreTaps
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.orbit.home.apps.CentreImageStore
import com.orbit.home.apps.CentrePicture
import com.orbit.home.preferences.CentreChoice
import com.orbit.home.preferences.CentreTarget

/**
 * The floating shortcut wheel: a small wheel docked to any edge of the screen, drawn over
 * other apps.
 *
 * It is a foreground service because Android will not let a window that stays over
 * other apps be hosted by a service it is free to kill — and a foreground service
 * has to show a notification.
 *
 * ## Two windows, constant frames
 *
 * Opening and closing must not change any window's bounds: Android animates a window
 * whose origin or size changes, and that glide stacks on our own animation (it is what
 * made the handle appear to arrive from the upper left after a close, and made the
 * wheel snap back and re-settle after an open). So:
 *
 *  - [wheelRoot] is the half of the wheel's square that faces into the screen,
 *    constant for a given edge and position. Collapsed it is not touchable and has
 *    window alpha 0, so it takes no touch from the app below (and Android 12's
 *    untrusted-touch rule does not block touches through it).
 *  - [handleRoot] is a small touch target at the edge, constant.
 *
 * A transition only changes [progress]; both layers read it in the graphics-layer
 * phase, so nothing recomposes or relayouts per frame. Flags and alpha are the only
 * window parameters that change, and neither moves the window.
 *
 * ## Four edges
 *
 * The wheel docks to the left, top, right or bottom edge ([dockEdge]) of the *usable*
 * area, which is the screen less the status bar, navigation bar and cutout, so the bar
 * never sits under the system UI. Dragging the handle along its edge slides it; carrying
 * the drag into the zone of another edge hops it there. Pulling it away from the edge
 * brings the wheel out, as it always has. [EdgeDockGeometry] holds the maths for all of it.
 */
class ShortcutWheelService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var preferences: AppPreferences
    private lateinit var repository: AppRepository
    private lateinit var iconLoader: AppIconLoader
    private lateinit var owner: OverlayLifecycleOwner
    private lateinit var wheelRoot: RawTouchLayout
    private lateinit var handleRoot: RawTouchLayout
    private lateinit var wheelParams: WindowManager.LayoutParams
    private lateinit var handleParams: WindowManager.LayoutParams

    /** A plain Service is not a LifecycleOwner, so it carries its own scope. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The wheel's square, even so that half of it is a whole number of pixels. */
    private var wheelSizePx = 0
    private var pullDistancePx = 0f
    /** How far the handle's touch target reaches into the screen from its edge. */
    private var handleThicknessPx = 0

    /** How long the handle's touch target is along its edge. */
    private var handleLengthPx = 0

    /** How close to another edge a dragging finger must get before the handle hops to it. */
    private var snapZonePx = 0f

    /** How much nearer another edge must be than the current one before the bar hops to it. */
    private var hopHysteresisPx = 0f

    /** How much nearer another edge must be than the current one before a carried bar hops to it. */
    private var carryHysteresisPx = 0f

    /**
     * True from the moment the bar hops to another edge until its new windows have taken
     * their new shape. Both the tab and the wheel are drawn invisible meanwhile, so the
     * one or two frames in which the content is already for the new edge but the window is
     * still the old one are never seen.
     */
    private var hopHidden by mutableStateOf(false)
    private var hopJob: Job? = null

    /**
     * The glide from one edge to the next. [hopFrom] is where the handle window was when the bar left
     * its edge; until [hopGlide] reaches 1 the window is drawn that far of the way from there to where
     * the edge and the finger now say it belongs. A window cannot be animated by the system without
     * the jump, so it is moved a frame at a time here, on an ease-out curve, instead of vanishing and
     * reappearing.
     */
    private var hopGlide by mutableFloatStateOf(1f)
    private var hopFrom: EdgeDockGeometry.Rect? = null
    private var hopAnimator: ValueAnimator? = null

    /** True once a held-still finger has picked the handle up to be carried to any edge. */
    private var carrying by mutableStateOf(false)

    /**
     * How far the system's idea of a window's origin is from the screen's, in pixels. Zero when
     * the window flags do what they should; measured and corrected at run time when they do not,
     * so the bar lands where the maths says on every Android version and navigation mode.
     */
    private var originShiftX = 0
    private var originShiftY = 0
    private var calibrationJob: Job? = null

    /** What was last given to the system as gesture-exclusion, so it is not re-sent each frame. */
    private var exclusionKey = Long.MIN_VALUE

    private var settle: ValueAnimator? = null

    /** True while a finger is on the handle, so settings cannot yank it mid-drag. */
    private var dragging = false

    /**
     * Where the wheel is heading, as opposed to where [progress] currently is. Decides
     * which window takes touches.
     */
    private var open = false

    /** One pending drag-driven frame update, so a burst of events causes one pass. */
    private var positionPending = false

    /**
     * A dragged position (edge and place along it) that is saved but not yet written, so stale
     * settings cannot undo it.
     */
    private var pendingOffset: Float? = null

    /**
     * Where the finger says the reveal should be, as opposed to where [progress] has got to.
     *
     * Kept apart so the drawing can chase the finger rather than jump to it: see
     * [PULL_SMOOTHING] for why that is what makes the pull feel smooth.
     */
    private var pullTarget = 0f

    /**
     * How tall the keyboard is right now, in pixels. 0 when it is not up.
     *
     * Only ever a lift, never a saved position: it is added to the window's placement for
     * as long as the keyboard is on screen and forgotten the moment it goes.
     */
    private var keyboardLiftPx = 0

    /**
     * True once the user has moved the handle while the keyboard was up.
     *
     * The lift is a convenience, not a rule. The moment it gets in the way of a deliberate
     * drag it stands aside, and it does not come back for the rest of this keyboard visit —
     * otherwise the handle would spring back up under the finger that just moved it down.
     */
    private var movedWhileLifted = false

    /** Which ring is showing. Driven only by the stored setting, never written optimistically. */
    private var mode by mutableStateOf(HomeSettings.MODE_APP)

    /** The saved shortcuts, resolved and decoded, ready for the ring. */
    private var ringShortcuts by mutableStateOf<List<RingShortcut>>(emptyList())

    /** The floating wheel's chosen centre, if the user has chosen one. */
    private var centrePicture by mutableStateOf<CentrePicture?>(null)

    /** Bumped to ask for the catalog again without changing the mode. */
    private val shortcutRefresh = MutableStateFlow(0)

    /**
     * The last thing that went wrong loading shortcuts, if anything.
     *
     * Kept rather than only toasted, because a load can now finish while the wheel is in app
     * mode — where a toast about shortcuts would be noise. The mode collector reports it the
     * moment the user actually switches.
     */
    private var shortcutProblem: Int? = null
    private var problemShown = false

    private fun reportShortcutProblem(@StringRes id: Int) {
        shortcutProblem = id
        if (mode == HomeSettings.MODE_SHORTCUT && !problemShown) {
            problemShown = true
            notice(id)
        }
    }

    /** When the mode last changed, so a third tap cannot be read as "open the picker". */
    private var lastModeSwitchUptime = 0L

    private fun notice(@StringRes id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()

    /** Switches between the two rings. Only once the wheel is actually pulled out. */
    private fun toggleMode() {
        if (progress < OUT_THRESHOLD) return
        val next = if (mode == HomeSettings.MODE_SHORTCUT) HomeSettings.MODE_APP
        else HomeSettings.MODE_SHORTCUT
        lastModeSwitchUptime = SystemClock.uptimeMillis()
        scope.launch(Dispatchers.IO) { preferences.setFloatingMode(next) }
    }

    /** Opens the picker, unless a double tap just happened. */
    private fun openPicker() {
        if (progress < OUT_THRESHOLD || mode != HomeSettings.MODE_SHORTCUT) return
        if (SystemClock.uptimeMillis() - lastModeSwitchUptime < MODE_SWITCH_GUARD_MS) return
        runCatching {
            startActivity(
                Intent(this, ShortcutPickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { notice(R.string.shortcut_failed_toast) }
        requestClose()
    }

    /**
     * Opens the floating wheel's app editor, unless a double tap just happened.
     *
     * The app-mode counterpart of [openPicker]: one tap on the centre edits what is on the wheel,
     * and which editor that is depends on the mode the wheel is in.
     */
    private fun openAppPicker() {
        if (progress < OUT_THRESHOLD || mode != HomeSettings.MODE_APP) return
        if (SystemClock.uptimeMillis() - lastModeSwitchUptime < MODE_SWITCH_GUARD_MS) return
        runCatching {
            startActivity(
                Intent(this, FloatingAppsPickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { notice(R.string.picker_failed_toast) }
        requestClose()
    }

    /** Starts one saved shortcut. Every failure leaves the wheel open and the saved list intact. */
    private fun launchShortcut(item: RingShortcut) {
        when (val r = ShortcutLauncher.start(this, item.key)) {
            ShortcutLaunchResult.Started -> requestClose()
            ShortcutLaunchResult.Gone -> {
                notice(R.string.shortcut_gone_toast)
                shortcutRefresh.value += 1
            }
            ShortcutLaunchResult.NoAccess -> notice(R.string.shortcut_no_access_toast)
            is ShortcutLaunchResult.Failed -> notice(R.string.shortcut_failed_toast)
        }
    }

    /** Cached until the display configuration changes. */
    private var cachedBounds: android.graphics.Rect? = null

    /** The system bars' and cutout's size. Cached like the bounds, and re-read when a drag starts. */
    private var cachedInsets: EdgeDockGeometry.Insets? = null
    private var previewing = false
    private var previewOnly = false
    private var prePreviewProgress = 0f
    private var loadedApps: List<String>? = null
    private var loadedInitialized: Boolean? = null
    private var loadedTick = -1

    /**
     * False when onCreate bailed out — no permission to draw, or the system refused the
     * foreground promotion. Nothing was built in that case, so there is nothing to run.
     */
    private var ready = false

    /**
     * Bumped on every install, removal or update, so the wheel's app list is re-resolved
     * even though the saved selection did not change. Without it a wheel left on "all
     * apps" never showed a new install, and a chosen app that was uninstalled stayed on
     * the ring until the service happened to restart.
     */
    private val packageTick = MutableStateFlow(0)
    /**
     * The wheel is closed when the screen goes off.
     *
     * It is drawn over everything, so leaving it out while the phone is in a pocket means a
     * later touch lands on the wheel rather than on what the user meant to touch. Closing it
     * is the same thing a tap outside does, so there is nothing new to learn.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) requestClose()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            packageTick.value += 1
        }
    }

    /**
     * Compose state, not plain fields: the wheel is drawn from these, so they have
     * to be observable or the overlay would render once and never update.
     *
     * [progress] is 0 docked, 1 out. It is read only inside graphics-layer lambdas.
     */
    private var progress by mutableFloatStateOf(0f)
    private var offsetFraction by mutableFloatStateOf(HomeSettings.DEFAULT_SHORTCUT_OFFSET)

    /**
     * Which edge the wheel is docked to. Compose state, because the tab and the wheel are drawn
     * differently on each edge. [offsetFraction] is the position along it.
     */
    private var dockEdge by mutableStateOf(DockEdge.DEFAULT)
    private var apps by mutableStateOf<List<InstalledApp>>(emptyList())
    private var look by mutableStateOf(HomeSettings.DEFAULT_SHORTCUT_LOOK)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        preferences = AppPreferences(this)
        repository = AppRepository(this)
        iconLoader = AppIconLoader(this)
        windowManager = getSystemService(WindowManager::class.java)

        val density = resources.displayMetrics.density
        wheelSizePx = EdgeDockGeometry.evenSize((WINDOW_DP.value * density).toInt())
        pullDistancePx = PULL_DISTANCE.value * density
        handleThicknessPx = (HANDLE_TOUCH_WIDTH.value * density).toInt()
        handleLengthPx = (HANDLE_TOUCH_LENGTH.value * density).toInt()
        snapZonePx = SNAP_ZONE.value * density
        hopHysteresisPx = HOP_HYSTERESIS.value * density
        carryHysteresisPx = CARRY_HYSTERESIS.value * density

        // Going foreground comes first and is guarded: startForegroundService() promises the
        // system it will happen, and an exception thrown from here is an app crash.
        val foreground = runCatching { startInForeground() }
            .onFailure { Diagnostics.warn("shortcut wheel could not go foreground", it) }
            .isSuccess
        // Without the permission no window can ever be added, so the service would sit in
        // the notification shade doing nothing while the switch says "on". This is the
        // state a revoked permission leaves behind after the system restarts the service.
        if (!foreground || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        try {
            addWindows()
        } catch (t: Exception) {
            Diagnostics.warn("shortcut wheel could not attach its overlay windows", t)
            ready = false
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return
        }
        // The service is usable only after both required windows are attached.
        ready = true
        startKeyboardWatch()

        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 1. The mode, on its own. Instant, and never cancelled by a query — the ring can
        //    switch the moment the user double-taps, because the shortcuts are already in hand.
        scope.launch {
            preferences.settings.map { it.floatingMode }.distinctUntilChanged().collect { stored ->
                mode = if (stored == HomeSettings.MODE_SHORTCUT) HomeSettings.MODE_SHORTCUT
                else HomeSettings.MODE_APP
                // The last load failed while the wheel was in app mode: try again now, and say so
                // if it still fails.
                if (mode == HomeSettings.MODE_SHORTCUT && shortcutProblem != null) {
                    problemShown = false
                    shortcutRefresh.value += 1
                }
            }
        }

        // 2. The shortcuts, loaded whatever the mode is and never cleared on a mode switch. This
        //    is what makes switching instant: the ring is already populated before it is shown,
        //    instead of being emptied and then waiting on a query.
        scope.launch {
            combine(
                // The centre rides along with the shortcuts: this is already the effect that runs
                // whenever the floating wheel's settings change, so the picture is reloaded at the
                // same moments the ring is.
                preferences.settings
                    .map { it.floatingShortcuts to it.centreFloating }
                    .distinctUntilChanged(),
                packageTick,
                shortcutRefresh
            ) { pair, _, _ -> pair }.collectLatest { (keys, centreChoice) ->
                centrePicture = CentreImageStore(this@ShortcutWheelService)
                    .pictureFor(centreChoice, CentreTarget.FLOATING)
                if (keys.isEmpty()) {
                    ringShortcuts = emptyList()
                    shortcutProblem = null
                    return@collectLatest
                }
                when (val r = withContext(Dispatchers.IO) {
                    ShortcutCatalog.queryKeys(this@ShortcutWheelService, keys)
                }) {
                    is ShortcutCatalogResult.Available -> {
                        shortcutProblem = null
                        problemShown = false
                        ringShortcuts = withContext(Dispatchers.Default) {
                            ShortcutSelection.resolve(keys, r.items).map { resolved ->
                                RingShortcut(
                                    key = resolved.key,
                                    label = resolved.item.label,
                                    icon = runCatching {
                                        CircleIcon.render(resolved.icon, RING_ICON_PX)
                                            ?.asImageBitmap()
                                    }.getOrNull()
                                )
                            }
                        }
                    }
                    // A failure leaves the ring AND the saved keys exactly as they were.
                    ShortcutCatalogResult.NoLauncherAccess ->
                        reportShortcutProblem(R.string.shortcut_no_access_toast)
                    is ShortcutCatalogResult.Failed -> {
                        Diagnostics.warn("shortcut catalog failed: ${r.detail}")
                        reportShortcutProblem(R.string.shortcut_failed_toast)
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        scope.launch {
            combine(preferences.settings, packageTick) { s, tick -> s to tick }.collectLatest { (settings, tick) ->
                // Not while a finger is on it: reapplying the saved position mid-drag
                // would pull the wheel out from under the gesture.
                // Switched on while the settings preview was open: the wheel is now the
                // user's, so closing the panel must not take it down.
                if (settings.shortcutEnabled) previewOnly = false
                // Not while a finger is on it, and not while a just-dragged position is still
                // being written: a stale emission would snap the wheel back and forward again.
                if (!dragging && pendingOffset == null) {
                    offsetFraction = settings.shortcutOffsetY
                    dockEdge = DockEdge.fromId(settings.shortcutEdge)
                }
                look = settings.shortcutLook
                // Apps are re-resolved only when the selection actually changes.
                if (loadedApps != settings.shortcutApps ||
                    loadedInitialized != settings.shortcutInitialized ||
                    loadedTick != tick
                ) {
                    // Read first, apply, and only then mark as loaded, so a
                    // cancellation part-way through cannot leave the wheel believing
                    // it has apps it never applied.
                    val wanted = settings.shortcutApps
                    val wantedInitialized = settings.shortcutInitialized
                    val installed = withContext(Dispatchers.IO) {
                        repository.loadLaunchableApps(packageName)
                    }
                    val byId = installed.associateBy { it.id }
                    apps = if (!wantedInitialized) {
                        installed
                    } else {
                        wanted.mapNotNull { byId[it] }
                    }
                    loadedApps = wanted
                    loadedInitialized = wantedInitialized
                    loadedTick = tick
                }
                // Safe at any moment, including mid-transition: open and close no
                // longer depend on window bounds, and this is a no-op unless the
                // vertical position really changed.
                if (!dragging) applyFrames()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ready) {
            // onCreate bailed out (see there). Do not ask to be restarted.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // The settings screen is a different part of the app from this service, so it
        // has to ask for a preview rather than setting some shared flag.
        when (intent?.action) {
            ACTION_PREVIEW_OPEN -> enterPreview(intent.getBooleanExtra(EXTRA_ENABLED, true))
            ACTION_PREVIEW_CLOSE -> leavePreview()
        }
        return START_STICKY
    }

    /**
     * Brings the wheel out so its settings can be changed against the real thing.
     *
     * A preview is not the same as the feature being on. If the user has the floating
     * wheel switched off, this still shows it and closing the panel takes it away
     * again — the saved switch is never touched.
     */
    private fun enterPreview(enabled: Boolean) {
        if (!previewing) {
            previewing = true
            // Only the first entry remembers, so repeated opens cannot overwrite the
            // state that was there before the panel was opened.
            prePreviewProgress = progress
            previewOnly = !enabled
        }
        setRevealed(true)
    }

    private fun leavePreview() {
        if (!previewing) return
        previewing = false
        if (previewOnly) {
            previewOnly = false
            // Started for nothing but this preview, so take the window down.
            stopSelf()
        } else {
            // Put back what was there before, so closing the panel does not leave the
            // wheel out when the user had it docked.
            setRevealed(prePreviewProgress >= OUT_THRESHOLD)
        }
    }

    override fun onDestroy() {
        running = false
        cancelSettle()
        hopAnimator?.cancel()
        runCatching { if (::handleRoot.isInitialized) windowManager.removeView(handleRoot) }
        runCatching { if (::wheelRoot.isInitialized) windowManager.removeView(wheelRoot) }
        runCatching { if (::owner.isInitialized) owner.destroy() }
        runCatching { unregisterReceiver(packageReceiver) }
        runCatching { unregisterReceiver(screenOffReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    // --- The windows ---

    private fun newRoot(content: @Composable () -> Unit): RawTouchLayout =
        RawTouchLayout(this).apply {
            attachOverlayOwners(owner)
            addView(
                ComposeView(context).apply { setContent { content() } },
                android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

    private fun newParams(flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            // The real bounds are set by applyFrames; these are only starting values.
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            // LEFT, not START: every frame is computed from a left origin, and START flips to
            // the right edge under an RTL locale, which put the wheel off-screen.
            gravity = Gravity.TOP or Gravity.LEFT
            // Pin the window's coordinate space to the physical screen. Every frame here is
            // computed from the real screen (rawX/rawY, currentWindowMetrics, the system-bar
            // insets). Without these the system is free to lay an overlay out inside the area
            // below the status bar, which shifts the whole bar by the status bar's height:
            // too low at the bottom (under the navigation buttons), too high at the top.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            disableMoveAnimation(this)
        }

    /**
     * Stops Android gliding a window from its old position to its new one.
     *
     * The bar is moved by the finger, frame by frame; the system's own decelerating glide on
     * top of that made it trail behind the finger and drift when it hopped to another edge.
     * The flag is hidden API, so this is best effort: if it is not there the bar behaves as it
     * did before.
     */
    private fun disableMoveAnimation(params: WindowManager.LayoutParams) {
        runCatching {
            val field = WindowManager.LayoutParams::class.java.getField("privateFlags")
            field.setInt(params, field.getInt(params) or PRIVATE_FLAG_NO_MOVE_ANIMATION)
        }
    }

    private fun addWindows() {
        owner = OverlayLifecycleOwner().also { it.create() }

        wheelRoot = newRoot { WheelLayer() }.apply {
            onOutsideTouch = { requestClose() }
        }
        handleRoot = newRoot { HandleLayer() }

        // Collapsed to begin with: the wheel window takes no touches and is invisible,
        // the handle window is the only thing that can be touched.
        wheelParams = newParams(WHEEL_FLAGS_UNTOUCHABLE).apply { alpha = 0f }
        handleParams = newParams(HANDLE_FLAGS_TOUCHABLE)

        try {
            applyFrames()
            // The wheel is added first, so the handle sits above it.
            windowManager.addView(wheelRoot, wheelParams)
            windowManager.addView(handleRoot, handleParams)
            calibrateOrigin()
        } catch (t: Exception) {
            // Neither a half-wheel nor a handle with no wheel is a usable feature. Roll back
            // whichever view attached successfully; onDestroy also performs defensive cleanup.
            runCatching {
                if (::handleRoot.isInitialized && handleRoot.isAttachedToWindow) {
                    windowManager.removeView(handleRoot)
                }
            }
            runCatching {
                if (::wheelRoot.isInitialized && wheelRoot.isAttachedToWindow) {
                    windowManager.removeView(wheelRoot)
                }
            }
            runCatching { if (::owner.isInitialized) owner.destroy() }
            throw t
        }
    }

    /**
     * The real window area.
     *
     * Not [android.util.DisplayMetrics]: on a phone with a cutout or curved edges
     * that can disagree with where the screen actually ends.
     */
    private fun screenBounds(): android.graphics.Rect {
        cachedBounds?.let { return it }
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val m = resources.displayMetrics
            android.graphics.Rect(0, 0, m.widthPixels, m.heightPixels)
        }
        cachedBounds = bounds
        return bounds
    }

    /**
     * How much of each side of the screen the system keeps for itself: the status bar, the
     * navigation bar and any cutout.
     *
     * From Android 11 this is exact, and is the bar's size whether the navigation is buttons or
     * gestures. Before that there is no way to ask an overlay, so it is estimated from the
     * system's own dimensions: the status bar always, and the navigation bar when the device has
     * one and is upright. Good enough to keep the bar off the buttons; not a promise on a
     * landscape phone with its buttons at the side.
     */
    private fun systemInsets(): EdgeDockGeometry.Insets {
        cachedInsets?.let { return it }
        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val i = windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                EdgeDockGeometry.Insets(i.left, i.top, i.right, i.bottom)
            }.getOrDefault(EdgeDockGeometry.Insets.NONE)
        } else {
            legacyInsets()
        }
        cachedInsets = insets
        return insets
    }

    private fun legacyInsets(): EdgeDockGeometry.Insets {
        fun dimen(name: String): Int {
            val id = resources.getIdentifier(name, "dimen", "android")
            return if (id > 0) resources.getDimensionPixelSize(id) else 0
        }
        val hasNavigationBar = resources.getIdentifier("config_showNavigationBar", "bool", "android")
            .let { it > 0 && resources.getBoolean(it) }
        val upright = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        return EdgeDockGeometry.Insets(
            left = 0,
            top = dimen("status_bar_height"),
            right = 0,
            bottom = if (hasNavigationBar && upright) dimen("navigation_bar_height") else 0
        )
    }

    /** The screen less the system bars and cutout: the area the bar docks to the edges of. */
    private fun baseArea(): EdgeDockGeometry.Rect {
        val screen = screenBounds()
        return EdgeDockGeometry.usableArea(screen.width(), screen.height(), systemInsets())
    }

    /**
     * Where the bar may dock right now.
     *
     * The usable area, except on the bottom edge while the keyboard is up: there the edge itself
     * is lifted clear of the keyboard, so the bar rests above it instead of over its keys. The
     * side edges lift the bar's position instead (see [effectiveFraction]) and the top edge has
     * nothing to lift out of the way of.
     */
    private fun dockArea(): EdgeDockGeometry.Rect {
        val area = baseArea()
        if (dockEdge != DockEdge.BOTTOM || keyboardLiftPx <= 0) return area
        val limit = screenBounds().height() - keyboardLiftPx - keyboardGapPx()
        // limit comes from keyboardGapPx(), which is a Float; area is whole pixels.
        val bottom = minOf(area.bottom, limit.toInt()).coerceAtLeast(area.y)
        return area.copy(height = bottom - area.y)
    }

    /**
     * The clear space kept between the bar and the keyboard's top edge.
     *
     * resources.displayMetrics, not LocalDensity: this is a plain function, not a composable, so
     * the Compose density is not in scope here.
     */
    private fun keyboardGapPx(): Float =
        (KEYBOARD_GAP + KEYBOARD_CLEARANCE).value * resources.displayMetrics.density

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // The one time the screen can change shape.
        cachedBounds = null
        cachedInsets = null
        super.onConfigurationChanged(newConfig)
        applyFrames()
        // A rotation can move where the system measures from (the bars change sides).
        calibrateOrigin()
    }

    /**
     * Checks where the handle window really is on the screen against where it was asked to be,
     * and absorbs any difference.
     *
     * Every frame here is worked out in screen coordinates, but the system decides what a
     * window's x and y are measured from, and it has not always decided the same way (below the
     * status bar, inside a cutout, past a side navigation bar). Rather than trust that, this
     * reads the window's true position once it has settled and corrects for what it finds. With
     * the flags set in [newParams] the difference is zero and nothing happens.
     *
     * A reading is only acted on when two in a row agree, so a window that is still sliding
     * into place cannot be mistaken for an offset.
     */
    private fun calibrateOrigin() {
        calibrationJob?.cancel()
        calibrationJob = scope.launch {
            delay(ORIGIN_SETTLE_MS)
            var previous: Pair<Int, Int>? = null
            var samples = 0
            var corrections = 0
            while (samples < ORIGIN_MAX_SAMPLES && corrections < 2) {
                samples++
                val error = measureOriginError() ?: return@launch
                if (error.first == 0 && error.second == 0) return@launch
                if (error == previous) {
                    originShiftX += error.first
                    originShiftY += error.second
                    Diagnostics.warn(
                        "shortcut wheel window origin was off by ${error.first},${error.second}px; corrected"
                    )
                    applyFrames()
                    corrections++
                    previous = null
                    delay(ORIGIN_SETTLE_MS)
                } else {
                    previous = error
                    delay(ORIGIN_SAMPLE_MS)
                }
            }
        }
    }

    /** True position minus requested position of the handle window, or null if it cannot be read now. */
    private fun measureOriginError(): Pair<Int, Int>? {
        if (!::handleRoot.isInitialized) return null
        if (!handleRoot.isAttachedToWindow || !handleRoot.isLaidOut || dragging) return null
        val at = IntArray(2)
        handleRoot.getLocationOnScreen(at)
        val errorX = at[0] - (handleParams.x + originShiftX)
        val errorY = at[1] - (handleParams.y + originShiftY)
        // Bigger than any system bar: a bad reading, not an offset.
        val limit = (ORIGIN_MAX_SHIFT.value * resources.displayMetrics.density).toInt()
        if (abs(errorX) > limit || abs(errorY) > limit) return null
        return errorX to errorY
    }

    /** Whether the person has animations switched on in system settings. */
    private fun motionEnabled(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }.getOrDefault(true)

    /**
     * The bar moves to another edge. Everything that has to be true for the new edge becomes
     * true now, in this order: first the tab and wheel are hidden, then the edge changes, then
     * the windows take their new shape (the caller applies the frames straight after it has
     * worked out the new position). Only when the windows have had time to settle does the tab
     * come back, springing out from under the finger to its edge.
     */
    private fun beginHop(edge: DockEdge) {
        hopJob?.cancel()
        hopHidden = false
        // Where the handle is drawn right now, mid-glide included, so a second hop carries on from it.
        val from = if (::handleParams.isInitialized) EdgeDockGeometry.Rect(
            handleParams.x + originShiftX, handleParams.y + originShiftY,
            handleParams.width, handleParams.height
        ) else null
        dockEdge = edge
        hopAnimator?.cancel()
        if (from == null || !motionEnabled()) {
            hopFrom = null
            hopGlide = 1f
            return
        }
        hopFrom = from
        hopGlide = 0f
        hopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = HOP_GLIDE_MS
            // Fast off the mark, long soft landing: the curve Apple's own UI leans on.
            interpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener {
                hopGlide = it.animatedValue as Float
                applyFrames(handleOnly = true)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    hopFrom = null
                    hopGlide = 1f
                    applyFrames(handleOnly = true)
                }
            })
            start()
        }
    }

    private fun lerpRect(
        a: EdgeDockGeometry.Rect, b: EdgeDockGeometry.Rect, t: Float
    ): EdgeDockGeometry.Rect {
        fun mix(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
        return EdgeDockGeometry.Rect(mix(a.x, b.x), mix(a.y, b.y), mix(a.width, b.width), mix(a.height, b.height))
    }

    /**
     * Keeps the system's back gesture from stealing a pull on the handle when it is on a side
     * edge, where an inward swipe is exactly a Back swipe. Best effort: the system may ignore
     * the request from an overlay, in which case things are as they were.
     */
    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!::handleRoot.isInitialized) return
        val side = dockEdge.isSide
        val w = handleParams.width
        val h = handleParams.height
        val key = if (side) w.toLong() * 100_000L + h else -1L
        if (key == exclusionKey) return
        exclusionKey = key
        runCatching {
            handleRoot.systemGestureExclusionRects =
                if (side) listOf(android.graphics.Rect(0, 0, w, h)) else emptyList()
        }
    }

    /**
     * The keyboard's height, or 0 when it cannot be known.
     *
     * A service overlay is not a normal window, so it is not handed the keyboard's insets
     * the way a screen is. currentWindowMetrics does report them, but only from Android 11;
     * below that there is no dependable way to see the keyboard from an overlay at all. So
     * this returns 0 there, which means no lift and exactly the behaviour those versions
     * have today — the feature is absent rather than wrong.
     */
    private fun keyboardHeightPx(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return runCatching {
            windowManager.currentWindowMetrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.ime()).bottom
        }.getOrDefault(0)
    }

    /**
     * Where the handle actually sits along its edge: the saved fraction, lifted clear of the
     * keyboard when the edge is a side one.
     *
     * Only ever raises it. If the saved position is already above the keyboard there is
     * nothing to do, and a position below the keyboard's top edge is pulled up to just
     * clear it. The saved value is never touched — this is a view of it, not a change to it.
     */
    private val effectiveFraction: Float
        get() {
            // Only the side edges slide up and down into the keyboard's way. The bottom edge is
            // lifted as a whole by [dockArea], and the top edge is nowhere near it.
            if (!dockEdge.isSide || keyboardLiftPx <= 0 || movedWhileLifted) return offsetFraction
            val screen = screenBounds()
            // The lowest the wheel's centre may sit with the keyboard up.
            val lowestCentre = screen.height() - keyboardLiftPx - keyboardGapPx() - wheelSizePx / 2f
            val lifted = EdgeDockGeometry.fractionForCentreAlong(
                lowestCentre, dockEdge, baseArea(), wheelSizePx, handleLengthPx
            )
            return minOf(offsetFraction, lifted)
        }

    /**
     * Puts both windows where the current edge and position say.
     *
     * This is the only code that changes a window's bounds, and it runs only for a
     * deliberate move, a configuration change or start-up. It never runs as
     * part of opening or closing, so Android has no frame change to animate during
     * one. A call with unchanged bounds does nothing.
     */
    private fun applyFrames(handleOnly: Boolean = false) {
        if (!::wheelRoot.isInitialized || !::handleRoot.isInitialized) return
        val edge = dockEdge
        val area = dockArea()
        val fraction = effectiveFraction
        // While the bar is dragged along its edge the wheel is out of sight, so moving its
        // window as well was a second window update per frame for nothing. It catches up when
        // the finger lifts, and straight away on a hop (see the drag handler).
        if (!handleOnly) {
            setBounds(
                wheelRoot,
                wheelParams,
                EdgeDockGeometry.wheelWindow(edge, area, wheelSizePx, fraction, handleLengthPx)
            )
        }
        val handleTarget = EdgeDockGeometry.handleWindow(
            edge, area, wheelSizePx, handleThicknessPx, handleLengthPx, fraction, handleLengthPx
        )
        val glideFrom = hopFrom
        setBounds(
            handleRoot,
            handleParams,
            if (glideFrom != null && hopGlide < 1f) lerpRect(glideFrom, handleTarget, hopGlide)
            else handleTarget
        )
        updateGestureExclusion()
    }

    /**
     * Watches for the keyboard appearing and disappearing.
     *
     * Polled, because there is no callback an overlay can register for this — the system
     * tells a focused window, and this is not one. A quarter-second is quick enough that the
     * lift looks immediate and cheap enough that it does not matter: the check is a single
     * metrics read, and nothing is redrawn unless the height actually changed.
     */
    private fun startKeyboardWatch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        scope.launch {
            while (true) {
                val height = keyboardHeightPx()
                if (height != keyboardLiftPx) {
                    val gone = height == 0
                    // Whether this is the keyboard arriving, as opposed to changing height
                    // while it is already up — a suggestion strip appearing does that.
                    val appeared = keyboardLiftPx == 0
                    keyboardLiftPx = height
                    // A fresh keyboard visit starts fresh: the lift applies again until the
                    // user moves it. A height change mid-visit is not a fresh visit, so it
                    // must not undo a position the user set by hand.
                    if (gone || appeared) movedWhileLifted = false
                    if (gone || !dragging) applyFrames()
                }
                delay(KEYBOARD_POLL_MS)
            }
        }
    }

    private fun setBounds(
        root: RawTouchLayout,
        params: WindowManager.LayoutParams,
        bounds: EdgeDockGeometry.Rect
    ) {
        val wantX = bounds.x - originShiftX
        val wantY = bounds.y - originShiftY
        if (params.x == wantX && params.y == wantY &&
            params.width == bounds.width && params.height == bounds.height
        ) {
            return
        }
        val previousX = params.x
        val previousY = params.y
        val previousWidth = params.width
        val previousHeight = params.height
        params.x = wantX
        params.y = wantY
        params.width = bounds.width
        params.height = bounds.height
        // Not attached yet during start-up: the bounds are simply used by addView.
        if (root.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(root, params) }
                .onFailure {
                    // Do not let the in-memory params claim that a failed layout update was applied.
                    // A later configuration/position refresh must be able to retry it.
                    params.x = previousX
                    params.y = previousY
                    params.width = previousWidth
                    params.height = previousHeight
                    Diagnostics.warn("could not move a shortcut wheel window", it)
                }
        }
    }

    /**
     * Decides which window takes touches and whether the wheel window is visible.
     *
     * Idempotent, and it changes flags and alpha only, which do not move a window, so
     * it is safe to call from anywhere and as often as needed.
     *
     *  - The wheel window is armed (alpha 1) from the moment a finger lands on the
     *    handle until it has fully closed again, so it is already there when the first
     *    pixel of the wheel is revealed. Otherwise it is alpha 0: invisible, and
     *    Android 12's rule against touches passing through opaque overlays does not
     *    apply to it.
     *  - It takes touches only while open. The handle window takes them only while not.
     */
    private fun refreshWindowState() {
        if (!::wheelRoot.isInitialized || !::handleRoot.isInitialized) return
        val armed = open || dragging || progress > 0f
        val wheelFlags = if (open) WHEEL_FLAGS_TOUCHABLE else WHEEL_FLAGS_UNTOUCHABLE
        val handleFlags = if (open) HANDLE_FLAGS_UNTOUCHABLE else HANDLE_FLAGS_TOUCHABLE
        val wheelAlpha = if (armed) 1f else 0f

        if (wheelParams.flags != wheelFlags || wheelParams.alpha != wheelAlpha) {
            val previousFlags = wheelParams.flags
            val previousAlpha = wheelParams.alpha
            wheelParams.flags = wheelFlags
            wheelParams.alpha = wheelAlpha
            if (wheelRoot.isAttachedToWindow) {
                runCatching { windowManager.updateViewLayout(wheelRoot, wheelParams) }
                    .onFailure {
                        // Roll back the bookkeeping as well as logging; otherwise the next refresh
                        // would believe the failed flags/alpha were already applied and never retry.
                        wheelParams.flags = previousFlags
                        wheelParams.alpha = previousAlpha
                        Diagnostics.warn("could not update the wheel window", it)
                    }
            }
        }
        if (handleParams.flags != handleFlags) {
            val previousFlags = handleParams.flags
            handleParams.flags = handleFlags
            if (handleRoot.isAttachedToWindow) {
                runCatching { windowManager.updateViewLayout(handleRoot, handleParams) }
                    .onFailure {
                        handleParams.flags = previousFlags
                        Diagnostics.warn("could not update the handle window", it)
                    }
            }
        }
    }

    /**
     * Coalesces drag-driven vertical updates to one per message-loop turn, so a burst
     * of touch samples causes one pair of window updates.
     */
    private fun requestPosition() {
        if (positionPending) return
        positionPending = true
        handleRoot.post {
            positionPending = false
            // The wheel stays put unless some of it is showing (a finger caught it mid-close).
            applyFrames(handleOnly = progress <= 0f)
        }
    }

    // --- Open and close ---

    /**
     * Closes the wheel because the user did something *else*: tapped outside it, tapped
     * empty window, or launched an app.
     *
     * Not while the settings panel is open. The panel is part of another window, so
     * every touch on it arrives here as an "outside" touch; honouring it is what made
     * the wheel tuck itself away the moment a slider was touched. During a preview the
     * wheel stays out until the panel itself closes ([leavePreview]).
     */
    private fun requestClose() {
        if (previewing) return
        setRevealed(false)
    }

    /**
     * Stops a running settle without letting its end handler run.
     *
     * [ValueAnimator.cancel] calls the end listener, and an end listener that commits
     * the old target would snap [progress] to the endpoint the animator was heading
     * for — on every interruption, whoever interrupted it. Removing the listeners
     * first means a cancelled animator does nothing at all.
     */
    private fun cancelSettle() {
        settle?.let {
            it.removeAllListeners()
            it.removeAllUpdateListeners()
            it.cancel()
        }
        settle = null
    }

    /**
     * Settles the wheel to open or closed.
     *
     * The animator writes [progress] and nothing else. No window is resized, moved or
     * reconfigured for the duration, so what is on screen is exactly what the
     * interpolator says, and the same on the way in as on the way out.
     */
    private fun setRevealed(revealed: Boolean) {
        // Every time the wheel is opened, refresh the shortcuts in the background. The cached list
        // shows instantly; this keeps it current for the next time without making the user wait.
        if (revealed && !open) shortcutRefresh.value += 1
        open = revealed
        cancelSettle()
        refreshWindowState()
        val target = if (revealed) 1f else 0f
        if (abs(progress - target) < SETTLE_EPSILON) {
            progress = target
            refreshWindowState()
            return
        }
        val animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = SETTLE_MS
            // Material's fast-out-slow-in curve, built from the framework.
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { update -> progress = update.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Listeners are removed on cancel, so this only runs for a settle
                    // that completed. Commit the exact endpoint.
                    progress = target
                    settle = null
                    // Fully closed: the wheel window can go invisible again.
                    refreshWindowState()
                }
            })
        }
        settle = animator
        animator.start()
    }

    // --- The layers ---

    /**
     * The docked tab, and the pull / tap / reposition gesture.
     *
     * The bar is drawn at the same place in the same window in every state; only its
     * alpha follows [progress], and that is read in the graphics-layer phase so a
     * frame of the transition recomposes nothing.
     */
    @Composable
    private fun HandleLayer() {
        // Screen coordinates, not reading order: every frame here is a left-origin screen
        // rectangle, so "end" must mean the right-hand side even under an RTL locale or the tab
        // would be drawn against the wrong side of its own window.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            HandleContent()
        }
    }

    @Composable
    private fun HandleContent() {
        val density = LocalDensity.current
        val haptics = rememberOrbitHaptics()
        val edge = dockEdge
        // After a hop the tab springs out from under the finger to its new edge. While the
        // hop is settling it is held at 0 (and drawn invisible by [hopHidden] at once, which
        // is a plain state read, so the first frame after the hop is already hidden).
        val motionOn = remember(hopHidden) { motionEnabled() }
        val arrive by animateFloatAsState(
            targetValue = if (hopHidden) 0f else 1f,
            animationSpec = if (hopHidden || !motionOn) snap()
            else spring(dampingRatio = 0.72f, stiffness = 380f)
        )
        // Picking the bar up makes it a little bigger under the finger.
        val lift by animateFloatAsState(
            targetValue = if (carrying) 1f else 0f,
            animationSpec = if (motionOn) spring(dampingRatio = 0.7f, stiffness = 500f) else snap()
        )
        val handleDescription = remember { getString(R.string.shortcut_handle_cd) }
        val handleOpenAction = remember { getString(R.string.shortcut_handle_open_action) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // For TalkBack: a labelled button that opens the wheel. Touch is handled by the
                // raw pointer code below, which TalkBack's double tap does not reach.
                .semantics(mergeDescendants = true) {
                    contentDescription = handleDescription
                    role = Role.Button
                    onClick(label = handleOpenAction) {
                        setRevealed(true)
                        true
                    }
                }
                // Keyed on nothing: keying on the pull state would restart this
                // gesture partway through a drag.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        // A finger takes over from any settling animation at once.
                        // Cancelling does not run the old end handler (see cancelSettle),
                        // so progress simply carries on from where it had got to.
                        cancelSettle()
                        dragging = true
                        refreshWindowState()
                        down.consume()
                        // The bars may have changed since the last touch (a different
                        // navigation mode, a hidden status bar), so read them afresh.
                        cachedInsets = null
                        // Everything is measured from where the finger landed, in
                        // screen coordinates: the handle window moves under the finger
                        // during a reposition, so local coordinates freeze.
                        val startX = handleRoot.rawX
                        val startY = handleRoot.rawY
                        val startProgress = progress
                        pullTarget = progress
                        // The edge the finger landed on. A pull is measured against this one
                        // for the whole gesture, even if a drag later carries the bar away.
                        val startEdge = dockEdge
                        // Where the bar is drawn, not where it is saved. With the keyboard
                        // up those differ by the lift, and starting from the saved value
                        // meant the lift clamped the bar for the whole drag and it jumped
                        // to the saved place on release.
                        val startOffset = effectiveFraction
                        // How far the bar's centre is from the finger along the edge, kept for
                        // the whole drag so the bar slides with the finger instead of jumping
                        // to centre itself under it. Zero once the bar hops to another edge:
                        // that offset was measured along the old edge's axis.
                        var grabOffset = EdgeDockGeometry.centreAlong(
                            startEdge, dockArea(), wheelSizePx, startOffset, handleLengthPx
                        ) - (if (startEdge.isSide) startY else startX)
                        val slop = viewConfiguration.touchSlop
                        // 0 undecided, 1 pull (towards or away from the screen's inside),
                        // 2 move (along the edge, and onto others).
                        var axis = 0
                        // The edge the bar hopped away from during this drag. Going straight
                        // back needs a margin (see edgeForDrag), so a finger near a corner
                        // cannot make it flip back and forth; a fresh hop needs none.
                        var lastHopFrom: DockEdge? = null
                        // Holding still picks the bar up: from then on it follows the finger
                        // anywhere, onto any edge, with no need to start the drag along the edge.
                        val pickupAt = down.uptimeMillis + minOf(viewConfiguration.longPressTimeoutMillis.toLong(), PICKUP_MS)
                        while (true) {
                            val event = if (axis == 0) {
                                val wait = pickupAt - SystemClock.uptimeMillis()
                                if (wait > 0) {
                                    withTimeoutOrNull(wait) { awaitPointerEvent(PointerEventPass.Initial) }
                                } else {
                                    null
                                }
                            } else {
                                awaitPointerEvent(PointerEventPass.Initial)
                            }
                            if (event == null) {
                                axis = 2
                                carrying = true
                                if (keyboardLiftPx > 0) movedWhileLifted = true
                                haptics.confirm()
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val dx = handleRoot.rawX - startX
                            val dy = handleRoot.rawY - startY
                            // Inward is positive: the way the wheel comes out, whichever edge
                            // the bar is on. On the right edge that is leftwards.
                            val pull = when (startEdge) {
                                DockEdge.RIGHT -> -dx
                                DockEdge.LEFT -> dx
                                DockEdge.BOTTOM -> -dy
                                DockEdge.TOP -> dy
                            }
                            // Along the edge, the other way round.
                            val slide = if (startEdge.isSide) dy else dx
                            if (axis == 0) {
                                // Nothing moves until the finger has actually travelled.
                                if (abs(dx) < slop && abs(dy) < slop) continue
                                axis = if (abs(pull) >= abs(slide)) 1 else 2
                                // Decided here rather than on release, so the lift stands
                                // aside for the whole drag and the bar follows the finger
                                // from the first movement. A tap never reaches this: it
                                // needs travel to get past the line above.
                                if (axis == 2 && keyboardLiftPx > 0) movedWhileLifted = true
                                // The handle has been picked up to be slid along the edge. A
                                // light tap says so; a reposition used to start silently.
                                if (axis == 2) haptics.tap()
                            }
                            if (axis == 1) {
                                // Pulling INTO the screen brings it out. Only state changes: no
                                // window is touched while the finger pulls.
                                //
                                // Where the finger says it should be.
                                pullTarget = (startProgress + pull / pullDistancePx).coerceIn(0f, 1f)
                                // And then a step towards it rather than a jump to it.
                                //
                                // Drawing each sample straight made the pull stutter. Two
                                // reasons: a hand is not steady, so every wobble went
                                // straight to the screen, and the first sample lands after
                                // the touch slop, so the wheel used to jump the whole slop
                                // distance in one frame. Moving a fixed fraction of the way
                                // each sample absorbs both, and because a still finger
                                // sends no movement there is nothing left to catch up on
                                // when the finger stops. The short trail behind the finger
                                // reads as weight rather than as lag.
                                progress = EdgeDockGeometry.chase(progress, pullTarget, PULL_SMOOTHING)
                            } else {
                                val fingerX = handleRoot.rawX
                                val fingerY = handleRoot.rawY
                                // Carried into another edge's zone, the bar hops to it. Anywhere
                                // else it stays on its edge and just follows the finger along it.
                                val hopTo = EdgeDockGeometry.edgeForCarry(
                                    dockEdge, baseArea(), fingerX, fingerY,
                                    carryHysteresisPx, lastHopFrom, hopHysteresisPx
                                )
                                val hopped = hopTo != dockEdge
                                if (hopped) {
                                    lastHopFrom = dockEdge
                                    beginHop(hopTo)
                                    grabOffset = 0f
                                    haptics.tap()
                                }
                                // Where the finger puts the bar's centre along the edge, turned
                                // into a fraction of the whole travel: 0 is one end of the edge
                                // and 1 the other. The travel already leaves out the wheel's own
                                // size, so the bar stays wholly inside the usable area at both ends.
                                var centre = (if (dockEdge.isSide) fingerY else fingerX) + grabOffset
                                // Slid past the end of the edge: roll round the corner onto the
                                // next edge, carrying on from the matching end of it.
                                var rolled = false
                                val past = EdgeDockGeometry.pastEnd(dockEdge, dockArea(), centre)
                                if (!hopped && past != 0) {
                                    val next = EdgeDockGeometry.cornerHop(dockEdge, past > 0)
                                    lastHopFrom = dockEdge
                                    beginHop(next.edge)
                                    haptics.tap()
                                    offsetFraction = next.fraction
                                    val landed = EdgeDockGeometry.centreAlong(
                                        dockEdge, dockArea(), wheelSizePx, next.fraction, handleLengthPx
                                    )
                                    grabOffset = landed - (if (dockEdge.isSide) fingerY else fingerX)
                                    centre = landed
                                    rolled = true
                                }
                                offsetFraction = EdgeDockGeometry.fractionForCentreAlong(
                                    centre, dockEdge, dockArea(), wheelSizePx, handleLengthPx
                                ).coerceIn(
                                    HomeSettings.SHORTCUT_OFFSET_MIN,
                                    HomeSettings.SHORTCUT_OFFSET_MAX
                                )
                                // A hop reshapes both windows now, with the position already
                                // right for the new edge; a plain slide is coalesced.
                                if (hopped || rolled) applyFrames() else requestPosition()
                            }
                        }
                        dragging = false
                        carrying = false
                        // The keyboard watch skips applyFrames() while a finger is down, so a
                        // change that happened during the drag is applied now, before the
                        // cancel branch decides where to settle. Idempotent: unchanged bounds
                        // do nothing, so this is safe on a plain tap too.
                        applyFrames()
                        if (handleRoot.lastWasCancel) {
                            // Android took the gesture away. Neither a tap nor a pull
                            // the user finished, so settle to the nearer endpoint and
                            // do not save a half-done reposition.
                            setRevealed(progress > PULL_SETTLE)
                        } else {
                            val moved = abs(handleRoot.rawX - startX) + abs(handleRoot.rawY - startY)
                            // A tap that never became a drag counts as asking for it.
                            // axis 0: the finger never left the touch slop in either direction,
                            // which is a tap by definition (a diagonal wobble can add up to more
                            // than TAP_SLOP and used to be ignored). axis 2: a reposition,
                            // which must not also pop the wheel out.
                            val revealTarget = if (
                                axis == 0 ||
                                (axis == 1 && moved < with(density) { TAP_SLOP.toPx() })
                            ) {
                                true
                            } else {
                                progress > PULL_SETTLE
                            }
                            // The floating handle owns its own WindowManager gesture; it used
                            // to reveal/close the wheel with no haptic feedback at all.
                            if (revealTarget != open) haptics.confirm()
                            setRevealed(revealTarget)
                            // Only a reposition has anything to save, and only a
                            // reposition stands the lift aside.
                            //
                            // This used to run for any gesture that ended on the handle,
                            // including a plain tap — so the first tap after the keyboard
                            // opened dropped the lift and the bar fell back to its saved
                            // place. A tap is not a decision about where the bar belongs,
                            // and it must not move it.
                            if (axis == 2) {
                                if (keyboardLiftPx > 0) movedWhileLifted = true
                                // The edge and the place along it go together, in one write.
                                val saveEdge = dockEdge.id
                                val save = offsetFraction
                                pendingOffset = save
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            preferences.setShortcutDock(saveEdge, save)
                                        }
                                    } finally {
                                        pendingOffset = null
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // The tab lies along its edge: tall and thin on the side edges, wide and thin on the
            // top and bottom ones. It sits against the edge and is rounded on the sides that face
            // into the screen.
            val side = edge.isSide
            Box(
                modifier = Modifier
                    .align(
                        when (edge) {
                            DockEdge.RIGHT -> Alignment.CenterEnd
                            DockEdge.LEFT -> Alignment.CenterStart
                            DockEdge.TOP -> Alignment.TopCenter
                            DockEdge.BOTTOM -> Alignment.BottomCenter
                        }
                    )
                    .width(if (side) HANDLE_WIDTH else HANDLE_HEIGHT)
                    .height(if (side) HANDLE_HEIGHT else HANDLE_WIDTH)
                    // The bar fades as the wheel comes out, read in the graphics-layer phase so a frame of the
                    // transition recomposes nothing. This is how it behaved before the edge audit took it away.
                    .graphicsLayer {
                        val a = if (hopHidden) 0f else arrive
                        // Fades in over the first part of a glide, so the pill is never seen
                        // squeezed into a window that still has the old edge's shape.
                        val glide = (hopGlide * 4f).coerceIn(0f, 1f)
                        alpha = (1f - progress * HANDLE_FADE).coerceIn(0f, 1f) * a.coerceIn(0f, 1f) * glide
                        val scale = (0.8f + 0.2f * arrive) * (0.9f + 0.1f * glide) * (1f + 0.15f * lift)
                        scaleX = scale
                        scaleY = scale
                        // Out from under the finger: starts inward of the edge and settles on it.
                        val slide = (1f - arrive) * HOP_SLIDE.toPx()
                        translationX = when (edge) {
                            DockEdge.RIGHT -> -slide
                            DockEdge.LEFT -> slide
                            else -> 0f
                        }
                        translationY = when (edge) {
                            DockEdge.BOTTOM -> -slide
                            DockEdge.TOP -> slide
                            else -> 0f
                        }
                    }

                    .background(
                        color = OrbitColors.Accent,
                        // Rounded on every corner, so the bar is a full pill with no flat edge
                        // pressed against the screen. It used to be rounded only on its
                        // inward-facing side, with the screen-edge corners square.
                        shape = RoundedCornerShape(10.dp)
                    )
            )
        }
    }

    /**
     * The wheel, drawn at its full size inside a window that is only its inward-facing half.
     *
     * The wheel is measured at its true size and placed so the half that faces into the screen
     * is the half inside the window; its distance from fully-out is a graphics-layer
     * translation driven by [progress]. The window is the clip, so nothing is revealed that is
     * not inside it, and nothing about the window changes while the wheel travels.
     *
     * On the right edge, for instance, the window is the wheel's left half, the wheel sits at
     * (0, 0), and it slides out to the right:
     *
     *   screenWheelCentreX = windowX + wheelSizePx / 2 + travelX(progress)
     *
     * The other edges are the same with the axes and signs turned: see [EdgeDockGeometry].
     */
    @Composable
    private fun WheelLayer() {
        // The same shared friction as every other wheel, so all of them coast alike.
        val decay = remember {
            exponentialDecay<Float>(frictionMultiplier = WheelMotionState.SPIN_FRICTION)
        }
        // One motion state per ring, so neither inherits the other's rotation or fling.
        val appWheel = remember { WheelMotionState(decay) }
        val shortcutWheel = remember { WheelMotionState(decay) }
        LaunchedEffect(mode) {
            appWheel.cancelFling()
            shortcutWheel.cancelFling()
        }
        LaunchedEffect(ringShortcuts.map { it.key }) {
            shortcutWheel.focusOnIds(ringShortcuts.map { it.key })
        }
        val size = wheelSizePx
        // Read here, in composition: a change of edge redraws the wheel facing its new way.
        val edge = dockEdge
        // The icons rest facing into the screen, in the middle of the half that shows.
        val focusAngle = EdgeDockGeometry.focusAngleDegrees(edge)

        Box(modifier = Modifier.fillMaxSize()) {
            // Anywhere in the window that is not the wheel counts as outside it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Not clickable: that would put an invisible button in the
                        // accessibility tree.
                        detectTapGestures { requestClose() }
                    }
            )
            Layout(
                modifier = Modifier.fillMaxSize().clipToBounds(),
                content = {
                    val ringModifier = Modifier
                        .graphicsLayer {
                            alpha = if (hopHidden) 0f else 1f
                            translationX = EdgeDockGeometry.travelX(edge, progress, size)
                            translationY = EdgeDockGeometry.travelY(edge, progress, size)
                        }
                        .centreTaps(
                            // One tap on the centre edits what is on the wheel, and which
                            // editor that is depends on the mode: the floating wheel's apps in
                            // app mode, its shortcuts in shortcut mode. A double tap still
                            // switches between the two modes.
                            onSingle = {
                                if (mode == HomeSettings.MODE_SHORTCUT) openPicker()
                                else openAppPicker()
                            },
                            onDouble = { toggleMode() }
                        )
                    if (mode == HomeSettings.MODE_SHORTCUT) {
                        ShortcutWheel(
                            items = ringShortcuts,
                            wheel = shortcutWheel,
                            iconScale = look.iconScale,
                            onLaunch = ::launchShortcut,
                            onAddForAccessibility = ::openPicker,
                            modifier = ringModifier,
                            focusAngleDegrees = focusAngle,
                            radiusScale = look.radiusScale,
                            centreScale = look.centreScale,
                            visibleCount = look.visibleCount,
                            addLabel = getString(R.string.shortcut_add_cd),
                            centrePicture = centrePicture
                        )
                    } else {
                        CircularAppWheel(
                            apps = apps,
                            wheel = appWheel,
                            iconScale = look.iconScale,
                            iconLoader = iconLoader,
                            onLaunch = { app ->
                                launch(app)
                                requestClose()
                            },
                            modifier = ringModifier,
                            focusAngleDegrees = focusAngle,
                            labelColor = Color.White,
                            centreMode = CentreMode.LOGO,
                            centrePicture = centrePicture,
                            radiusScale = look.radiusScale,
                            centreScale = look.centreScale,
                            visibleCount = look.visibleCount,
                            passthroughOutsideRing = false
                        )
                    }
                }
            ) { measurables, constraints ->
                val placeable = measurables.first().measure(Constraints.fixed(size, size))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // Absolute, not mirrored for RTL: the origin is where the visible half sits.
                    placeable.place(
                        EdgeDockGeometry.wheelOriginX(edge, size),
                        EdgeDockGeometry.wheelOriginY(edge, size)
                    )
                }
            }
        }
    }

    private fun launch(app: InstalledApp) {
        // AppLauncher.launch catches its own exceptions and returns a Result, so wrapping it
        // in runCatching meant this onFailure never fired and a failed launch was silent.
        // The Result is inspected directly instead.
        AppLauncher.launch(this, app).onFailure {
            Diagnostics.warn("shortcut wheel could not launch ${app.id}", it)
            // The home screen says so for the same failure; a silent overlay looks like a dead icon.
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // --- Foreground ---

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.shortcut_channel_name),
                    // Low: it is a "this is running" notice, not something to be told
                    // about twice.
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.orbit.home.SetupActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.shortcut_notification_title))
            .setContentText(getString(R.string.shortcut_notification_body))
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        /** True between onCreate and onDestroy, so nothing starts the service just to talk to it. */
        @Volatile
        private var running = false

        private const val CHANNEL_ID = "orbit-shortcut-wheel"
        private const val NOTIFICATION_ID = 4711

        /** The floating wheel is deliberately small; it is a shortcut, not the ring. */
        private val WINDOW_DP = 210.dp

        /** The tab's visible size. Narrow: it is a pull tab, not a button. */
        private val HANDLE_WIDTH = 8.dp
        private val HANDLE_HEIGHT = 40.dp

        /** The handle window's length along its edge: a 48dp-plus target around the 40dp tab. */
        private val HANDLE_TOUCH_LENGTH = 56.dp

        /** How far inward of its edge the tab starts when it springs in after a hop. */
        private val HOP_SLIDE = 22.dp

        /** Time for the windows to take their new shape after a hop before the tab returns. */
        private const val HOP_SETTLE_MS = 64L

        /** How long the bar takes to glide from one edge to the next. */
        private const val HOP_GLIDE_MS = 320L

        private const val ORIGIN_SETTLE_MS = 350L
        private const val ORIGIN_SAMPLE_MS = 120L
        private const val ORIGIN_MAX_SAMPLES = 8

        /** An offset larger than this is a bad reading, not a window origin. */
        private val ORIGIN_MAX_SHIFT = 160.dp

        /**
         * The touch target behind the tab. Wider than the tab itself and widened again
         * on request: the visible graphic stays 8dp so it does not intrude, but the
         * area you can actually hit is more forgiving.
         */
        private val HANDLE_TOUCH_WIDTH = 36.dp

        /** How long the settle takes after a tap or a release. */
        private const val SETTLE_MS = 150L


        /** Below this the wheel counts as already at the endpoint. */
        private const val SETTLE_EPSILON = 0.001f
        /** How fast the tab fades as the wheel comes out. */
        private const val HANDLE_FADE = 3f

        /**
         * How close a dragging finger must get to another edge before the bar hops onto it.
         *
         * Smaller than half the wheel on purpose. The bar can only slide until the whole wheel is
         * inside the screen, which leaves a finger half a wheel from the end of a side edge; a
         * zone as big as that would hop the bar off the edge every time it was slid to its end.
         */
        private val SNAP_ZONE = 56.dp

        /**
         * Another edge must be this much nearer to the finger than the current one before the
         * bar hops. Near a corner the two distances are almost equal, and without a margin the
         * bar flickered between edges on tiny finger movements.
         */
        private val HOP_HYSTERESIS = 16.dp

        /** A carried bar hops once another edge is this much nearer the finger than its own. */
        private val CARRY_HYSTERESIS = 20.dp

        /** How long a held-still finger waits before it picks the bar up, at most. */
        private const val PICKUP_MS = 260L

        /** How far you have to pull for the wheel to come fully out. */
        private val PULL_DISTANCE = 80.dp

        /** Past this much of the pull, releasing leaves it out. */
        private const val PULL_SETTLE = 0.3f

        /**
         * How much of the gap to the finger [progress] closes on each sample.
         *
         * Low enough to smooth out a hand's wobble and the slop jump at the start of a
         * pull; high enough that a quick pull is not left visibly short of where the
         * finger got to.
         */
        private const val PULL_SMOOTHING = 0.45f

        /** Movement under this counts as a tap rather than a drag. */
        private val TAP_SLOP = 12.dp

        /** How much clear space to leave between the handle and the keyboard's top edge. */
        private val KEYBOARD_GAP = 12.dp

        /**
         * How much more than that, because the top of a keyboard is rarely where the typing ends.
         *
         * Most keyboards keep a strip above their keys - a row of suggestions, a text field, a send
         * button. Lifting only clear of the keys leaves the bar resting on that strip, and a thumb
         * reaching for send lands on the wheel instead of the message. This is about the height of one
         * of those strips.
         */
        private val KEYBOARD_CLEARANCE = 48.dp

        /** How often to check whether the keyboard has come or gone. */
        private const val KEYBOARD_POLL_MS = 250L

        /** How long after a mode switch a tap is ignored, so a third tap cannot open the picker. */
        private const val MODE_SWITCH_GUARD_MS = 600L

        /** The size a shortcut icon is decoded at for the ring. */
        private const val RING_ICON_PX = 192

        /** Above this, a preview that was docked before is put back out. */
        private const val OUT_THRESHOLD = 0.5f

        /**
         * Window flags. Only these and the wheel window's alpha ever change after a
         * window is added, and none of them moves it.
         *
         * Identical to the flags the single window used before, so the wheel sits on
         * exactly the same pixels as it did.
         */
        private const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        /** WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION (hidden). */
        private const val PRIVATE_FLAG_NO_MOVE_ANIMATION = 0x00000040

        /** Open: takes touches, and hears about touches outside it so a tap there closes it. */
        private const val WHEEL_FLAGS_TOUCHABLE =
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        /** Collapsed: takes nothing. */
        private const val WHEEL_FLAGS_UNTOUCHABLE =
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        private const val HANDLE_FLAGS_TOUCHABLE = BASE_FLAGS
        private const val HANDLE_FLAGS_UNTOUCHABLE =
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        /** Starts the overlay. Safe to call when it is already running. */
        fun start(context: Context) {
            val intent = Intent(context, ShortcutWheelService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Diagnostics.warn("could not start the shortcut wheel", it) }
        }

        /**
         * Brings the floating wheel out for its settings panel.
         *
         * [shortcutEnabled] decides whether this is a preview of a running feature or
         * a temporary one for a feature that is switched off.
         */
        fun showSettingsPreview(context: Context, shortcutEnabled: Boolean) {
            val intent = Intent(context, ShortcutWheelService::class.java)
                .setAction(ACTION_PREVIEW_OPEN)
                .putExtra(EXTRA_ENABLED, shortcutEnabled)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Diagnostics.warn("could not start the shortcut preview", it) }
        }

        fun closeSettingsPreview(context: Context) {
            // startService would create the service if it is not running, and then leave a
            // foreground service and a docked handle behind with the feature switched off.
            if (!running) return
            runCatching {
                context.startService(
                    Intent(context, ShortcutWheelService::class.java).setAction(ACTION_PREVIEW_CLOSE)
                )
            }.onFailure { Diagnostics.warn("could not close the shortcut preview", it) }
        }

        private const val ACTION_PREVIEW_OPEN = "com.orbit.home.PREVIEW_OPEN"
        private const val ACTION_PREVIEW_CLOSE = "com.orbit.home.PREVIEW_CLOSE"
        private const val EXTRA_ENABLED = "enabled"

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ShortcutWheelService::class.java)) }
                .onFailure { Diagnostics.warn("could not stop the shortcut wheel", it) }
        }
    }
}
