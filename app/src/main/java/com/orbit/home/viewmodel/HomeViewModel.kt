package com.orbit.home.viewmodel

import android.app.Application
import android.widget.Toast
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.home.animation.Motion
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.AppLauncher
import com.orbit.home.apps.AppRepository
import com.orbit.home.apps.DockDefaults
import com.orbit.home.apps.InstalledApp
import com.orbit.home.apps.changedPackages
import com.orbit.home.apps.inventoryChanged
import com.orbit.home.apps.RecentApps
import com.orbit.home.apps.WheelApps
import com.orbit.home.ui.settings.SettingsRoute
import com.orbit.home.diagnostics.Diagnostics
import com.orbit.home.overlay.ShortcutWheelService
import com.orbit.home.preferences.AppPreferences
import com.orbit.home.preferences.GridFolder
import com.orbit.home.preferences.HomeSettings
import com.orbit.home.preferences.WheelLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import android.net.Uri
import com.orbit.home.R
import com.orbit.home.apps.CentreImageStore
import com.orbit.home.apps.CentrePicture
import com.orbit.home.preferences.CentreChoice
import com.orbit.home.preferences.CentreTarget
import androidx.compose.ui.graphics.ImageBitmap
import android.os.Build
import com.orbit.home.apps.renderFramed
import com.orbit.home.preferences.CentreTransform
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.graphics.asAndroidBitmap
import com.orbit.home.apps.HiddenWallpaperStore

/** Everything the home screen needs to draw itself. */
data class HomeUiState(
    /** Every launchable app, in label order. */
    val allApps: List<InstalledApp> = emptyList(),
    /**
     * One entry per wheel, in page order. Never empty: there is always at least the
     * home screen's own wheel.
     */
    val wheels: List<ResolvedWheel> = listOf(ResolvedWheel()),
    /** Which wheel the home screen is showing. */
    val currentPage: Int = 0,
    /** The dock's apps, resolved against [allApps]. */
    val dockApps: List<InstalledApp> = emptyList(),
    /** Whether the drawer shows a grid rather than a list. Grid is the default. */
    val drawerGrid: Boolean = true,
    /** Whether haptic feedback is on (Settings > Haptic feedback). */
    val hapticsEnabled: Boolean = true,
    /** True until the first scan finishes. */
    val loading: Boolean = true,
    /** True once the stored settings have been read. */
    val settingsLoaded: Boolean = false,
    /** Whether the all-apps drawer is on screen. */
    val drawerOpen: Boolean = false,
    /** Whether the wheel's app picker is on screen. */
    val editingApps: Boolean = false,
    /** Whether the dock's app picker is on screen. */
    val editingDock: Boolean = false,
    /**
     * Where the Settings area is, or null for the launcher home screen.
     *
     * One value for the whole area rather than a Boolean per page: Back, the Android Home
     * button and the resume path all need one answer to "where am I", and a set of flags
     * can disagree with each other.
     */
    val settingsRoute: SettingsRoute? = null,
    /**
     * Which wheel the Settings page is editing.
     *
     * Held here rather than in the page so the app picker, which is rendered by the home
     * screen, knows which wheel the user was looking at when they asked to edit its apps.
     */
    val settingsWheelIndex: Int = 0,
    /** Whether the match-all explanation has been shown once already. */
    val wheelMatchHintSeen: Boolean = false,
    /** What each wheel's centre shows. */
    val centreHome: List<CentreChoice?> = emptyList(),
    val centreFloating: CentreChoice = CentreChoice.Default,
    /** Whether the floating shortcut wheel is switched on. */
    val shortcutEnabled: Boolean = false,
    /** Its apps, and whether the user has ever chosen them. */
    val shortcutApps: List<String> = emptyList(),
    val shortcutInitialized: Boolean = false,
    /** Whether its app picker is on screen. */
    val editingShortcutApps: Boolean = false,
    /** The floating wheel's own shape, edited from its own customiser. */
    val shortcutLook: WheelLook = HomeSettings.DEFAULT_SHORTCUT_LOOK,
    /** Where it sits along the screen edge. */
    val shortcutOffsetY: Float = HomeSettings.DEFAULT_SHORTCUT_OFFSET,
    /** Whether its customiser is on screen. */
    val editingShortcutWheel: Boolean = false,
    /**
     * Whether Android currently lets this app draw over others. Re-read on every return to
     * the launcher, because the permission is granted on a system screen this app cannot
     * observe. Defaults to true so the "needs permission" notice does not flash on the
     * first frame.
     */
    val canDrawOverlays: Boolean = true,
    /** Whether the hidden wallpaper is the one showing. */
    val hiddenWallpaperShowing: Boolean = false,
    val clockColour: Int = 3,
    /** Whether the calculator is in its dark theme. */
    val calcDark: Boolean = true,
    val clockX: Float = 0.5f,
    val clockY: Float = 0.06f,
    val clockTextScale: Float = 1f,
    val clockWidthScale: Float = 1f,
    /** Whether the home screen is a grid of apps rather than the wheel. */
    val homeGrid: Boolean = false,
    /** The order the grid home shows apps in, as app ids. */
    val gridOrder: List<String> = emptyList(),
    /** The apps the home screen holds, and whether it has been arranged yet. */
    val homeApps: List<String> = emptyList(),
    val homeInitialized: Boolean = false,
    /** The folders on the grid home. */
    val gridFolders: List<GridFolder> = emptyList(),
    /** The system's reduced-motion preference. */
    val reducedMotion: Boolean = false,
    /** A one-shot message to show, if anything went wrong. */
    val message: String? = null,
    /** Bumped for every message, so two identical messages in a row are still two events. */
    val messageId: Int = 0,
    /**
     * Counts presses of the system Home button while the launcher is on screen.
     * The home screen watches it to clear the pieces of state it owns itself.
     */
    val homePresses: Int = 0
) {
    /** The wheel on screen. Always exists, because the first is never removed. */
    val currentWheel: ResolvedWheel
        get() = wheels.getOrElse(currentPage) { wheels.firstOrNull() ?: ResolvedWheel() }
}

/**
 * One wheel, ready to draw: the apps on its ring, the ids the user chose, and its
 * own shape.
 */
data class ResolvedWheel(
    /** The apps on the ring: all of them until the user chooses for this wheel. */
    val apps: List<InstalledApp> = emptyList(),
    /** The ids the user chose, in pick order. */
    val selection: List<String> = emptyList(),
    /** False until the user has chosen, so the picker opens fully ticked. */
    val initialized: Boolean = false,
    /** This wheel's own shape. */
    val look: WheelLook = WheelLook(),
)

/**
 * The launcher's only view model.
 *
 * It keeps the installed-app list current, resolves the wheel and the dock, holds
 * the wheel's shape, and launches apps.
 */
/**
 * Something waiting to be framed for a wheel's centre.
 *
 * [still] is what the framing screen shows: the picture itself, or a video's opening frame. [video]
 * is the picked video when what was picked was one, and it is what decides whether the framing is
 * applied once and saved as a picture, or kept as numbers and applied to every frame.
 */
data class CentreImport(
    val target: CentreTarget,
    val still: ImageBitmap,
    val video: Uri? = null,
    val wheelIndex: Int = 0
)

/** A video for the home wheel's centre: the file, and the framing to draw it with. */
data class CentreVideoSource(val file: java.io.File, val transform: CentreTransform)

/** Per-wheel pictures for the home screen, and the floating wheel's single picture. */
data class CentrePictures(
    val home: List<CentrePicture?> = emptyList(),
    val floating: CentrePicture? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val preferences = AppPreferences(application)
    val iconLoader = AppIconLoader(application)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val centreStore = CentreImageStore(application)
    private val hiddenWallpaperStore = HiddenWallpaperStore(application)

    private val _centrePictures = MutableStateFlow(CentrePictures())

    /**
     * The pictures the two wheels' centres show.
     *
     * Kept apart from [state] because a picture is read from storage rather than derived from a
     * setting, so it cannot be produced inside the settings flow's own map.
     */
    val centrePictures: StateFlow<CentrePictures> = _centrePictures.asStateFlow()

    private val _centreImport = MutableStateFlow<CentreImport?>(null)

    /**
     * The picture being framed, if one is.
     *
     * Choosing a picture is two steps now: it is picked, then the part that ends up in the middle
     * is chosen from it. This holds the picked picture between the two.
     */
    val centreImport: StateFlow<CentreImport?> = _centreImport.asStateFlow()

    // Which wheels' videos the user has stopped. Held here rather than in the composable because a
    // pager destroys a page's state when it scrolls away, so a `remember` in the page forgot the stop
    // the moment you looked at another wheel and started playing again when you came back.
    private val _centreVideoStopped = MutableStateFlow<Set<Int>>(emptySet())
    val centreVideoStopped: StateFlow<Set<Int>> = _centreVideoStopped.asStateFlow()

    // Where each wheel's video had got to, in milliseconds. The player is rebuilt for whichever wheel is
    // on screen and released when you look away, so without this a video that was halfway through starts
    // again from the beginning every time you come back to it.
    private val _centreVideoPositions = MutableStateFlow<Map<Int, Long>>(emptyMap())

    /** Remembers where a wheel's video had got to, so it can be picked up again. */
    fun rememberCentreVideoPosition(index: Int, positionMs: Long) {
        if (positionMs <= 0L) return
        _centreVideoPositions.value = _centreVideoPositions.value + (index to positionMs)
    }

    /** Where that wheel's video should start, if it has been watched before. */
    fun centreVideoPosition(index: Int): Long = _centreVideoPositions.value[index] ?: 0L

    private val _centreVideos = MutableStateFlow<List<CentreVideoSource?>>(emptyList())

    /** One video source per home wheel. A missing entry means that wheel is not showing a video. */
    val centreVideos: StateFlow<List<CentreVideoSource?>> = _centreVideos.asStateFlow()

    /** Stops this wheel's video, or starts it again. Its own wheel only. */
    fun toggleCentreVideoStopped(index: Int) {
        _centreVideoStopped.value = if (index in _centreVideoStopped.value) {
            _centreVideoStopped.value - index
        } else {
            _centreVideoStopped.value + index
        }
    }

    private val _hiddenWallpaper = MutableStateFlow<ImageBitmap?>(null)

    /**
     * The hidden wallpaper, decoded and ready to draw.
     *
     * Read from storage rather than derived from a setting, so it cannot be produced inside the
     * settings flow's own map.
     */
    val hiddenWallpaper: StateFlow<ImageBitmap?> = _hiddenWallpaper.asStateFlow()

    /** Whether the picture has been read once already, so an unchanged emission does not re-read it. */
    private var hiddenWallpaperRead = false

    /** What the pictures were last loaded for, so an unchanged setting does not reload them. */
    private var loadedCentreHome: List<CentreChoice?>? = null
    private var loadedCentreFloating: CentreChoice? = null

    /** Whether the settings panel has asked the service for a preview. */
    private var previewRequested = false

    /** The last package scan, so a no-op resume does not rescan. */
    private var lastScan: List<InstalledApp> = emptyList()

    /** The stored settings, kept so the derived lists can be recomputed. */
    private var settings: HomeSettings = HomeSettings()

    init {
        viewModelScope.launch {
            preferences.settings.catch { t ->
                // Anything the flow could not recover from must not leave the screen blank for ever.
                Diagnostics.warn("settings collector failed", t)
                _state.update { it.copy(settingsLoaded = true) }
            }.collect { s ->
                settings = s
                // A picture has to be read from storage, so it is refreshed when the choice
                // actually changes rather than on every emission of the settings flow.
                if (s.centreHome != loadedCentreHome || s.centreFloating != loadedCentreFloating) {
                    refreshCentrePictures(s)
                }
                // The picture is read from storage rather than derived from the setting, so it is
                // read once and again whenever it is replaced — not on every emission.
                if (!hiddenWallpaperRead) {
                    hiddenWallpaperRead = true
                    refreshHiddenWallpaper()
                }
                _state.update {
                    it.copy(
                        drawerGrid = s.drawerGrid,
                        hapticsEnabled = s.hapticsEnabled,
                        centreHome = s.centreHome,
                        centreFloating = s.centreFloating,
                        wheelMatchHintSeen = s.wheelMatchHintSeen,
                        shortcutEnabled = s.shortcutEnabled,
                        shortcutApps = s.shortcutApps,
                        shortcutInitialized = s.shortcutInitialized,
                        shortcutLook = s.shortcutLook,
                        shortcutOffsetY = s.shortcutOffsetY,
                        settingsLoaded = true,
                        hiddenWallpaperShowing = s.hiddenWallpaperShowing,
            clockColour = s.clockColour,
            calcDark = s.calcDark,
            clockX = s.clockX,
            clockY = s.clockY,
            clockTextScale = s.clockTextScale,
            clockWidthScale = s.clockWidthScale,
                        homeGrid = s.homeGrid,
                        gridOrder = s.gridOrder,
                        homeApps = s.homeApps,
                        gridFolders = s.gridFolders,
                        homeInitialized = s.homeInitialized
                    ).withDerived()
                }
            }
        }
        refresh()
    }

    /** Rescans the installed apps and re-resolves the wheel and dock. */
    fun refresh() {
        viewModelScope.launch {
            val apps = repository.loadLaunchableApps(getApplication<Application>().packageName)
            lastScan = apps
            _state.update { it.copy(allApps = apps, loading = false).withDerived() }
            seedDockIfNeeded(apps)
        }
    }

    /** Rescans only when the installed set actually changed. A named package broadcast also
     * invalidates its icon, because an app update can change only the icon and leave the inventory
     * (id, label and activity) otherwise identical.
     */
    fun refreshIfPackagesChanged(changedPackage: String? = null) {
        changedPackage?.takeIf { it.isNotBlank() }?.let { iconLoader.evictPackages(setOf(it)) }
        // One scan at a time. A request that arrives mid-scan is remembered and run afterwards, so a
        // change that landed after the running scan read the packages is not lost.
        if (scanJob?.isActive == true) {
            rescanQueued = true
            return
        }
        scanJob = viewModelScope.launch {
            do {
                rescanQueued = false
                scanOnce()
            } while (rescanQueued)
        }
    }

    private var scanJob: Job? = null
    private var rescanQueued = false

    private suspend fun scanOnce() {
        run {
            val apps = repository.loadLaunchableApps(getApplication<Application>().packageName)
            // Compared by what would be drawn, not by id alone. An app can update and change its name
            // while its launch component stays exactly the same, and then the id list is identical and
            // the screen keeps showing the old name.
            if (inventoryChanged(lastScan, apps)) {
                // Only the packages that actually changed lose their icons: an app that appeared or was
                // updated, and one that is gone. Clearing all of them on an ordinary resume would
                // re-read every icon on the phone for nothing.
                val stale = changedPackages(lastScan, apps)
                if (stale.isNotEmpty()) iconLoader.evictPackages(stale)
                lastScan = apps
                // Only the inventory is replaced. Everything the user chose - the home screen's apps and
                // their cells, the dock, the wheels, the shortcuts, the home mode - is stored separately
                // and is not touched here, so an install or an uninstall cannot rearrange a screen the
                // user arranged. An app that is gone leaves its cell empty rather than shifting the rest.
                _state.update { it.copy(allApps = apps, loading = false).withDerived() }
            }
        }
    }

    /**
     * Seeds the dock once, from the system's own defaults, and only when the scan
     * found something. A first-run scan that came back empty used to leave the dock
     * permanently empty (P0-02).
     */
    private suspend fun seedDockIfNeeded(apps: List<InstalledApp>) {
        if (settings.dockInitialized || apps.isEmpty()) return
        val installed = apps.map { it.id }.toSet()
        val own = getApplication<Application>().packageName
        // A new dock starts with four apps. The named and system picks go first; if the phone
        // has fewer of those than that, it is filled up from what is installed.
        val seeded = DockDefaults.fillTo(
            chosen = DockDefaults.resolve(getApplication()).filter { it in installed },
            candidates = apps.filter { it.packageName != own }.map { it.id }
        )
        // One edit that decides against what is actually stored. The in-memory copy of the settings
        // is still the default until the first emission arrives, so it cannot be trusted here.
        preferences.seedDockIfNeeded(seeded)
    }

    // --- The wheel's controls ---

    /** Saves one wheel's shape. One write, so it can never land half-applied. */
    fun setWheelLook(index: Int, look: WheelLook) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setWheelLook(index, look) }
    }

    /** Moves one wheel up or down without touching its other settings. */
    fun setWheelOffsetY(index: Int, offsetY: Float) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setWheelOffsetY(index, offsetY) }
    }

    /** Gives every wheel the same shape, keeping each wheel's own apps. */
    fun matchAllWheelLooks(look: WheelLook) {
        viewModelScope.launch(Dispatchers.IO) { preferences.matchAllWheelLooks(look) }
    }

    /** Notes that the match-all explanation has been shown. */
    fun markWheelMatchHintSeen() {
        viewModelScope.launch(Dispatchers.IO) { preferences.setWheelMatchHintSeen() }
    }

    /** Saves which apps are on one wheel, in the order they were picked. */
    fun setWheelApps(index: Int, ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setWheelApps(index, ids) }
        setEditingApps(false)
    }

    /**
     * Switches the floating shortcut wheel on or off, and starts or stops its
     * service to match. The caller checks the overlay permission first: without it
     * the service would start and its window would simply never appear.
     */
    fun setShortcutEnabled(enabled: Boolean) {
        _state.update { it.copy(shortcutEnabled = enabled) }
        viewModelScope.launch(Dispatchers.IO) { preferences.setShortcutEnabled(enabled) }
        if (enabled) {
            ShortcutWheelService.start(getApplication())
        } else {
            ShortcutWheelService.stop(getApplication())
        }
    }

    /**
     * Brings the floating wheel back in line with what the saved switch says. Called every
     * time the launcher (or its setup screen) comes to the front.
     *
     * The switch is saved, but whether the wheel is actually on screen depends on two
     * things the saved value cannot track: a running service, and the permission. After a
     * reboot, an app update, a backup restore, the system killing the service or the
     * permission being revoked, the two used to disagree and nothing ever repaired it —
     * the switch said on and no wheel appeared.
     *
     *  - on, and allowed to draw: (re)start the service. Starting a running one is a no-op.
     *  - on, but no longer allowed: turn the switch off and stop the stale foreground service.
     *  - off: stop any stale service left over after process/service restoration.
     *
     * The permission-loss branch is reached only after reading the currently persisted switch;
     * the normal enable path still starts the service after the user's explicit action.
     */
    fun refreshShortcutWheel() {
        val app = getApplication<Application>()
        val can = Settings.canDrawOverlays(app)
        _state.update { if (it.canDrawOverlays == can) it else it.copy(canDrawOverlays = can) }
        viewModelScope.launch {
            val enabled = withContext(Dispatchers.IO) { preferences.current().shortcutEnabled }
            if (enabled && can) {
                ShortcutWheelService.start(app)
            } else {
                if (enabled) {
                    withContext(Dispatchers.IO) { preferences.setShortcutEnabled(false) }
                    _state.update { it.copy(shortcutEnabled = false) }
                }
                ShortcutWheelService.stop(app)
            }
        }
    }

    /** Saves the floating wheel's apps, in the order they were picked. */
    fun setShortcutApps(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setShortcutApps(ids) }
        setEditingShortcutApps(false)
    }

    fun setEditingShortcutApps(editing: Boolean) {
        _state.update { it.copy(editingShortcutApps = editing) }
    }

    /**
     * Persistence for the two floating-wheel settings.
     *
     * A slider drag emits continuously, so launching an independent write per event
     * leaves many overlapping requests whose completion order decides the stored
     * value. A conflating state flow keeps only the newest, and collecting it with
     * collectLatest means an older write cannot land after a newer one. The UI state
     * still updates on the spot — nothing here delays the preview.
     */
    private val lookWrites = MutableStateFlow<WheelLook?>(null)
    private val offsetWrites = MutableStateFlow<Float?>(null)

    init {
        viewModelScope.launch {
            lookWrites.filterNotNull().collectLatest { preferences.setShortcutLook(it) }
        }
        viewModelScope.launch {
            offsetWrites.filterNotNull().collectLatest { preferences.setShortcutOffsetY(it) }
        }
    }

    /** The floating wheel's shape. */
    fun setShortcutLook(look: WheelLook) {
        _state.update { it.copy(shortcutLook = look) }
        lookWrites.value = look
    }

    /** Where the floating wheel sits along the edge. */
    fun setShortcutOffsetY(fraction: Float) {
        _state.update { it.copy(shortcutOffsetY = fraction) }
        offsetWrites.value = fraction
    }

    /**
     * Opening the floating wheel's settings closes every other panel in the same
     * update. Without this the main customiser stays on screen underneath and both
     * are drawn at once.
     */
    fun setEditingShortcutWheel(editing: Boolean) {
        _state.update {
            if (editing) {
                it.copy(
                    editingShortcutWheel = true,
                        editingApps = false,
                    editingDock = false,
                    editingShortcutApps = false,
                    drawerOpen = false
                )
            } else {
                it.copy(editingShortcutWheel = false)
            }
        }

        // The overlay runs in the service, not in this screen's state, so opening the
        // panel has to tell it to come out — and closing it has to tell it to go back.
        if (editing == previewRequested) return
        previewRequested = editing
        val app = getApplication<Application>()
        if (editing) {
            if (Settings.canDrawOverlays(app)) {
                ShortcutWheelService.showSettingsPreview(app, _state.value.shortcutEnabled)
            } else {
                // Better to say so than to send the user to a system settings screen
                // they did not ask for, or to start an overlay that cannot be drawn.
                postMessage(app.getString(com.orbit.home.R.string.shortcut_preview_unavailable))
            }
        } else {
            ShortcutWheelService.closeSettingsPreview(app)
        }
    }

    /** Which wheel the home screen is showing. */
    fun setCurrentPage(index: Int) {
        _state.update { it.copy(currentPage = index.coerceAtLeast(0)) }
    }

    /**
     * Adds a wheel shaped like the one on [fromIndex] — normally the wheel being
     * viewed — and moves to it, so the thing just asked for is the thing on screen.
     */
    fun addWheel(fromIndex: Int) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                preferences.addWheel(fromIndex)
                preferences.current().wheels.size
            }
            _state.update { it.copy(currentPage = (count - 1).coerceAtLeast(0)) }
        }
    }

    /**
     * Removes a wheel. The first is the home screen's own and cannot go, and if the
     * one being viewed is removed the view falls back to the first.
     */
    fun removeWheel(index: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val countBefore = preferences.current().wheels.size
                preferences.removeWheel(index)
                val countAfter = preferences.current().wheels.size
                if (countAfter < countBefore && index in 1 until countBefore) {
                    centreStore.removeHomeWheelAndShift(index)
                }
            }
            // The DataStore emission may arrive while files are being shifted. Refresh once more
            // after the file moves so the in-memory pictures/sources match their new wheel indices.
            refreshCentrePictures(preferences.current())
            _state.update {
                // Removing the wheel being viewed leaves the view where it is, which
                // is now the next wheel; removing one before it shifts the view back.
                val next = if (it.currentPage > index) it.currentPage - 1 else it.currentPage
                it.copy(currentPage = next.coerceAtLeast(0))
            }
        }
    }

    /** Switches the drawer between a list and a grid, and remembers the choice. */
    /** Chooses what one wheel's centre shows. */
    fun setCentre(target: CentreTarget, choice: CentreChoice, wheelIndex: Int = 0) {
        viewModelScope.launch {
            preferences.setCentre(target, choice, wheelIndex)
            // Pictures are retained only for Imported and Video choices: the latter needs its
            // opening frame as a fallback if its file disappears or a player cannot start.
            if (choice != CentreChoice.Imported && choice !is CentreChoice.Video) {
                centreStore.remove(target, wheelIndex)
            }
            if (choice !is CentreChoice.Video) centreStore.removeVideo(target, wheelIndex)
            refreshCentrePictures(preferences.current())
        }
    }

    /**
     * Decodes a picked picture and holds it for the user to frame.
     *
     * Nothing is claimed as done unless it was: a picture that could not be read leaves the setting
     * exactly as it was and says so, rather than opening a framing screen with nothing in it.
     */
    fun beginCentreImport(target: CentreTarget, uri: Uri, wheelIndex: Int = 0) {
        viewModelScope.launch {
            // What was picked decides everything after this: a video is framed on its opening frame
            // and keeps its framing, a picture is framed and rendered once.
            val resolver = getApplication<Application>().contentResolver
            // A provider may report no type at all. Fall back to the file extension, and if that
            // is no help either, try the picture first and then the video, rather than reporting
            // a perfectly readable video as a broken picture.
            val mime = resolver.getType(uri)
                ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                )
            var isVideo = mime?.startsWith("video/") == true
            var still = if (isVideo) {
                centreStore.firstFrameOf(uri, ADJUST_PX)
            } else {
                centreStore.decodeForAdjust(uri)
            }
            if (still == null && mime == null) {
                still = centreStore.firstFrameOf(uri, ADJUST_PX)
                if (still != null) isVideo = true
            }
            if (still != null) {
                _centreImport.value = CentreImport(target, still, if (isVideo) uri else null, wheelIndex)
            } else {
                reportCentreProblem(
                    if (isVideo) R.string.centre_video_failed else R.string.centre_import_failed
                )
            }
        }
    }

    /**
     * Keeps what was framed, and makes it that wheel's centre.
     *
     * Both the rendered square and the framing are passed. A picture needs only the square, because
     * its framing is baked into it. A video needs only the framing, because it cannot be baked — so
     * the video is copied in and the numbers are stored beside it.
     */
    fun finishCentreImport(framed: ImageBitmap, transform: CentreTransform) {
        val pending = _centreImport.value ?: return
        _centreImport.value = null
        viewModelScope.launch {
            val video = pending.video
            if (video != null) {
                if (centreStore.saveVideo(pending.target, video, pending.wheelIndex)) {
                    // Keep the framed opening frame beside the video. It is normally covered by
                    // playback, but becomes the safe fallback if the file is missing or unreadable.
                    if (centreStore.save(pending.target, framed, pending.wheelIndex)) {
                        preferences.setCentre(
                            pending.target,
                            CentreChoice.Video(transform),
                            pending.wheelIndex
                        )
                        afterCentreChange()
                    } else {
                        // Do not mark a video as selected if we could not keep its fallback frame.
                        centreStore.removeVideo(pending.target, pending.wheelIndex)
                        reportCentreProblem(R.string.centre_video_failed)
                    }
                } else {
                    reportCentreProblem(R.string.centre_video_too_big)
                }
            } else {
                if (centreStore.save(pending.target, framed, pending.wheelIndex)) {
                    centreStore.removeVideo(pending.target, pending.wheelIndex)
                    preferences.setCentre(
                        pending.target,
                        CentreChoice.Imported,
                        pending.wheelIndex
                    )
                    afterCentreChange()
                } else {
                    reportCentreProblem(R.string.centre_import_failed)
                }
            }
        }
    }

    private suspend fun afterCentreChange() {
        refreshCentrePictures(preferences.current())
    }

    /**
     * Reads both centres out of storage.
     *
     * Also records which choices were loaded, which is what keeps an unchanged setting from
     * reloading them on the next emission.
     */
    /** Stores what the home screen holds. */
    /**
     * Stores the home screen's apps and its cells together.
     *
     * One coroutine and one write, so the pair cannot be split by anything. That is the whole reason
     * this method exists rather than the two it replaces being called one after the other.
     */
    /**
     * Puts an app on the home screen.
     *
     * Reads the state as it is now rather than as it was when the drawer was drawn, so an app that has
     * since been uninstalled, or already added from somewhere else, is not added twice. Nothing else on
     * the screen is touched: the app goes into the first cell with nothing in it, and the membership
     * gains one id at the end.
     *
     * Refused while the home screen has never been arranged. Writing a one-app layout there would mark
     * it as arranged and suppress the starting arrangement a new home screen gets - so the honest thing
     * is to say so rather than to quietly give the user a home screen with one app on it.
     */
    fun addAppToGridHome(appId: String) {
        val current = _state.value
        if (!current.homeInitialized) {
            postMessage(getApplication<Application>().getString(R.string.home_add_needs_grid))
            return
        }
        if (current.homeApps.contains(appId)) return
        if (current.allApps.none { it.id == appId }) {
            postMessage(getApplication<Application>().getString(R.string.home_add_gone))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read-modify-write inside one edit, against the stored layout rather than a
                // snapshot, so two quick adds cannot discard each other.
                preferences.addAppToHome(appId, ::addToCells)
                // The drawer covers the snackbar, so a toast is how the person knows it worked.
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication<Application>(),
                        R.string.drawer_added_to_home,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (t: Throwable) {
                Diagnostics.rethrowIfCancellation(t)
                postMessage(getApplication<Application>().getString(R.string.home_add_failed))
            }
        }
    }

    fun setGridLayout(homeIds: List<String>, cells: List<String>, folders: List<GridFolder>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                preferences.setGridLayout(homeIds, cells, folders)
            } catch (t: Throwable) {
                Diagnostics.rethrowIfCancellation(t)
                postMessage(getApplication<Application>().getString(R.string.home_save_failed))
            }
        }
    }

    /** Switches the home screen between the wheel and a grid of apps. */
    fun toggleHomeGrid() {
        val grid = settings.homeGrid
        viewModelScope.launch { preferences.setHomeGrid(!grid) }
    }

    /** Flips between the phone's wallpaper and the hidden one, and it stays flipped. */
    fun toggleHiddenWallpaper() {
        val showing = settings.hiddenWallpaperShowing
        viewModelScope.launch { preferences.setHiddenWallpaperShowing(!showing) }
    }

    /** The clock bar's background, by position in the palette. */
    fun setClockColour(index: Int) {
        viewModelScope.launch { preferences.setClockColour(index) }
    }

    /** The calculator's theme. */
    fun setCalcDark(dark: Boolean) {
        viewModelScope.launch { preferences.setCalcDark(dark) }
    }

    /** Where the time bar sits on the home screen, and how big and how long it is. */
    fun setClockFrame(x: Float, y: Float, textScale: Float, widthScale: Float) {
        viewModelScope.launch { preferences.setClockFrame(x, y, textScale, widthScale) }
    }

    /** Sets the hidden wallpaper from a picture the user picked, and switches to it. */
    fun setHiddenWallpaper(uri: Uri) {
        viewModelScope.launch {
            if (hiddenWallpaperStore.save(uri)) {
                // Setting it does not switch to it. The two are switched by the triple tap, and a
                // picture that took over the home screen the moment it was chosen would be one that
                // decided something the user had not asked for.
                refreshHiddenWallpaper()
            } else {
                reportCentreProblem(R.string.wallpaper_hidden_failed)
            }
        }
    }

    /** Forgets the hidden wallpaper and returns to the phone's own. */
    fun clearHiddenWallpaper() {
        viewModelScope.launch {
            hiddenWallpaperStore.remove()
            preferences.setHiddenWallpaperShowing(false)
            refreshHiddenWallpaper()
        }
    }

    /** Reads the hidden wallpaper out of storage. */
    private suspend fun refreshHiddenWallpaper() {
        _hiddenWallpaper.value = hiddenWallpaperStore.load()
    }

    /** Leaves the picture unframed and the setting untouched. */
    fun cancelCentreImport() {
        _centreImport.value = null
    }

    private fun reportCentreProblem(messageRes: Int) {
        postMessage(getApplication<Application>().getString(messageRes))
    }

    private suspend fun refreshCentrePictures(s: HomeSettings) {
        loadedCentreHome = s.centreHome
        loadedCentreFloating = s.centreFloating
        // Off the main thread: the built-in centre is drawn into a bitmap on whichever thread asks.
        val pictures = withContext(Dispatchers.Default) {
            CentrePictures(
                home = s.centreHome.mapIndexed { index, storedChoice ->
                    centreStore.pictureFor(storedChoice ?: CentreChoice.Default, CentreTarget.HOME, index)
                },
                floating = centreStore.pictureFor(s.centreFloating, CentreTarget.FLOATING)
            )
        }
        _centrePictures.value = pictures
        // Video files belong to an individual home wheel. Each player is constructed by the
        // composable for its own page, so one wheel can never borrow another wheel's surface.
        _centreVideos.value = s.centreHome.mapIndexed { index, storedChoice ->
            val video = storedChoice as? CentreChoice.Video
            val file = if (video != null) centreStore.videoFor(CentreTarget.HOME, index) else null
            if (video != null && file != null) CentreVideoSource(file, video.transform) else null
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setHapticsEnabled(enabled) }
    }

    fun setDrawerGrid(grid: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setDrawerGrid(grid) }
    }

    fun setEditingApps(editing: Boolean) {
        _state.update { it.copy(editingApps = editing) }
    }

    fun setEditingDock(editing: Boolean) {
        _state.update { it.copy(editingDock = editing) }
    }

    /** Saves the dock's order (or a dock with an app taken off it) without closing anything. */
    fun saveDockOrder(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.setDockApps(ids.take(HomeSettings.DOCK_MAX))
        }
    }

    /** Saves the dock, capped at [HomeSettings.DOCK_MAX] so the row cannot overflow. */
    fun setDockApps(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.setDockApps(ids.take(HomeSettings.DOCK_MAX))
        }
        setEditingDock(false)
    }

    /** Opens or closes the all-apps drawer. */
    fun setDrawerOpen(open: Boolean) {
        _state.update { it.copy(drawerOpen = open) }
    }

    /**
     * The system Home button was pressed while the launcher was already the screen
     * in front. Whatever was open closes, and the home screen is what is left — so
     * Home is a way out of any screen, not just a way back from another app.
     */
    /**
     * Opens Settings at its list.
     *
     * Everything behind it is closed first. A customiser or a picker left open underneath
     * would be invisible but still composed, still handling Back and still reachable by a
     * stray touch, which is how an invisible screen ends up swallowing the Back button.
     */
    fun openSettings() {
        _state.update {
            it.copy(
                settingsRoute = SettingsRoute.HOME,
                drawerOpen = false,
                editingApps = false,
                editingDock = false,
                editingShortcutApps = false
            )
        }
        // The floating wheel's panel lives in the service, not the state, so it has to be
        // told separately or it would stay pinned out behind the Settings page.
        setEditingShortcutWheel(false)
    }

    /** Which wheel the Settings page is showing. */
    fun setSettingsWheelIndex(index: Int) {
        _state.update { it.copy(settingsWheelIndex = index) }
    }

    /** Moves within Settings. */
    fun setSettingsRoute(route: SettingsRoute) {
        _state.update { it.copy(settingsRoute = route) }
    }

    /** Leaves Settings entirely, back to the launcher home screen. */
    fun closeSettings() {
        _state.update { it.copy(settingsRoute = null) }
    }

    fun returnHome() {
        _state.update {
            it.copy(
                // The Android Home button leaves Settings as well as everything else: an
                // invisible Settings route left active would swallow the next Back.
                settingsRoute = null,
                drawerOpen = false,
                editingApps = false,
                editingDock = false,
                editingShortcutApps = false,
                homePresses = it.homePresses + 1
            )
        }
        // Also the floating wheel's panel, which has to tell the overlay to go back.
        setEditingShortcutWheel(false)
    }

    // --- Apps ---

    /** Launches an app, and says so if the launch was refused. */
    fun launch(app: InstalledApp) {
        // Launching from the drawer closes it, so coming back to the launcher shows
        // the home screen rather than the list you just chose from.
        _state.update { it.copy(drawerOpen = false) }
        val result = AppLauncher.launch(getApplication(), app)
        if (result.isSuccess) {
            // The wheel arranges itself by what was used last.
            viewModelScope.launch(Dispatchers.IO) { preferences.recordRecentApp(app.id) }
        }
        if (result.isFailure) {
            Diagnostics.warn("launch refused: ${app.id}", result.exceptionOrNull())
            postMessage(getApplication<Application>().getString(com.orbit.home.R.string.launch_failed))
        }
    }

    /** Posts a one-shot message. Every call is a new event, even if the text is the same. */
    private fun postMessage(text: String) {
        _state.update { it.copy(message = text, messageId = it.messageId + 1) }
    }

    /** Clears the one-shot message once it has been shown. */
    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Re-reads the system's animator duration scale. */
    fun refreshMotionPreference() {
        val scale = try {
            Settings.Global.getFloat(
                getApplication<Application>().contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (t: Throwable) {
            // Cancellation must propagate (remediation 5.21).
            Diagnostics.rethrowIfCancellation(t)
            1f
        }
        val reduced = Motion.animationsDisabled(scale)
        if (_state.value.reducedMotion != reduced) {
            _state.update { it.copy(reducedMotion = reduced) }
        }
    }

    /** Recomputes every wheel and the dock against the current apps and settings. */
    private fun HomeUiState.withDerived(): HomeUiState {
        val byId = allApps.associateBy { it.id }
        val resolved = settings.wheels.map { page ->
            ResolvedWheel(
                apps = RecentApps.order(WheelApps.resolve(allApps, page), settings.recentApps),
                selection = page.apps,
                initialized = page.initialized,
                look = page.look
            )
        }
        return copy(
            wheels = resolved.ifEmpty { listOf(ResolvedWheel(apps = allApps)) },
            // A wheel removed while it was being viewed must not leave the screen on
            // a page that no longer exists.
            currentPage = currentPage.coerceIn(0, (resolved.size - 1).coerceAtLeast(0)),
            dockApps = settings.dockApps.mapNotNull { byId[it] }
        )
    }

    /**
     * The activity going away must not leave a temporary preview overlay behind. A
     * preview of a running feature is left alone — that overlay is the user's.
     */
    override fun onCleared() {
        if (previewRequested && !_state.value.shortcutEnabled) {
            ShortcutWheelService.closeSettingsPreview(getApplication())
        }
        previewRequested = false
        super.onCleared()
    }

}

/** The size a centre is rendered at, for both a picture and a video frame. */
private const val CENTRE_PX = 512

/** What a picked picture is decoded at, so there is something for the framing to crop from. */
private const val ADJUST_PX = 1440

/**
 * [cells] with [appId] in the first cell that has nothing in it.
 *
 * If every cell is taken, one more is added at the end. Nothing is moved to make room: an app arriving
 * should not rearrange a screen the user arranged, and a cell past the end simply becomes the first
 * cell of the next page when the grid works out how many pages it has.
 *
 * An id is never placed twice, because it is only ever called for an app the membership does not
 * already hold.
 */
internal fun addToCells(cells: List<String>, appId: String): List<String> {
    val free = cells.indexOfFirst { it.isEmpty() }
    if (free >= 0) return cells.toMutableList().also { it[free] = appId }
    return cells + appId
}
