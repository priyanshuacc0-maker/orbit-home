package com.orbit.home.preferences


import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orbit.home.animation.WheelGeometry
import com.orbit.home.apps.DockDefaults
import com.orbit.home.diagnostics.Diagnostics
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "orbit_home")

/**
 * The wheel's shape, as the customiser's draft.
 *
 * Held as one value so the panel can hand back a complete, coherent set of numbers
 * in a single write rather than six independent ones.
 */
data class WheelLook(
    /** How many slots the ring draws. */
    val visibleCount: Int = DEFAULT_COUNT,
    /** Icon size multiplier. */
    val iconScale: Float = DEFAULT_ICON_SCALE,
    /** Orbital radius multiplier. */
    val radiusScale: Float = DEFAULT_RADIUS_SCALE,
    /** Centre (logo or focused app) size multiplier. */
    val centreScale: Float = DEFAULT_CENTRE_SCALE,
    /** Vertical position, as a fraction of the wheel area's height. */
    val offsetY: Float = 0f
) {
    companion object {
        // The shape the wheel ships with.
        const val DEFAULT_COUNT = 12
        const val DEFAULT_ICON_SCALE = 1.29f
        const val DEFAULT_RADIUS_SCALE = 1.06f
        const val DEFAULT_CENTRE_SCALE = 1.24f

        // Each range is centred on its default, so a slider starts in the middle
        // and there is as much room to go down as to go up. The count is the
        // exception: it keeps its own range and simply defaults to its maximum.
        val ICON_RANGE = 0.99f..1.59f
        val RADIUS_RANGE = 0.82f..1.30f
        val CENTRE_RANGE = 0.94f..1.54f
        val COUNT_RANGE = WheelGeometry.MIN_SLOTS..WheelGeometry.MAX_SLOTS

        /** How far the wheel may be moved up or down. Zero is the middle. */
        const val MAX_OFFSET = 0.18f
    }
}

/**
 * The settings Orbit Home keeps.
 *
 * Everything here is reachable from Settings in the three-dot menu, and most of it also
 * from the panel that opens when you hold the middle of the wheel.
 */
data class HomeSettings(
    /**
     * The wheels, in page order. Never empty: there is always at least the home
     * screen's own wheel, and that first one cannot be removed.
     */
    val wheels: List<WheelPage> = listOf(WheelPage()),
    /** Whether the all-apps drawer shows a grid rather than a list. */
    val drawerGrid: Boolean = true,
    /**
     * Whether the "match all wheels" explanation has been shown once already.
     *
     * Stored rather than kept in memory, so it is genuinely a first time: showing it again
     * after every restart would make it a nag rather than an explanation.
     */
    val wheelMatchHintSeen: Boolean = false,
    /**
     * What each wheel's centre shows.
     *
     * Two separate choices on purpose: the home screen's middle and the floating wheel's are seen
     * in different places, and there is no reason a picture chosen for one should take over the
     * other.
     */
    /**
     * Whether the hidden wallpaper is the one showing.
     *
     * A switch rather than a gesture's side effect: the triple tap flips this, and it stays flipped
     * — across leaving the home screen, and across a restart — until it is flipped back. A wallpaper
     * that reverted on its own would be a wallpaper nobody could rely on.
     */
    val hiddenWallpaperShowing: Boolean = false,
    val clockColour: Int = 3,
    val clockX: Float = 0.5f,
    val clockY: Float = 0.06f,
    val clockTextScale: Float = 1f,
    val clockWidthScale: Float = 1f,
    /**
     * Whether the home screen is a grid of apps rather than the wheel.
     *
     * A stored choice, not a mode that resets: a layout somebody picked is a layout they want, and
     * one that reverted on the next launch would be one they had to keep re-picking.
     */
    val homeGrid: Boolean = false,
    /**
     * The order the grid home shows apps in, as app ids.
     *
     * Only what the user has arranged: apps that are not here follow in their own order, so one
     * installed later does not get pushed into the middle of an arrangement. Ids that are no longer
     * installed are ignored rather than removed, because an app can be uninstalled and put back, and
     * the place somebody gave it should survive that.
     */
    val gridOrder: List<String> = emptyList(),
    /**
     * The apps the home screen holds, as app ids, and whether it has ever been arranged.
     *
     * A chosen set, like a phone's home screen - which is what makes taking an app off it mean
     * something. Everything not here is still in the drawer. Before [homeInitialized] the grid is
     * seeded with a starting arrangement rather than being left empty.
     */
    val homeApps: List<String> = emptyList(),
    val homeInitialized: Boolean = false,
    /**
     * The folders on the grid home. A folder's apps are in [homeApps] like any other, but have no cell
     * of their own: the folder's id is what sits in the cell. See [GridFolder].
     */
    val gridFolders: List<GridFolder> = emptyList(),
    /** Sparse, index-aligned centre choices for the home wheels. Null means never chosen. */
    val centreHome: List<CentreChoice?> = emptyList(),
    /** The floating wheel remains a single, independent centre. */
    val centreFloating: CentreChoice = CentreChoice.Default,
    /** Ordered dock apps, as stable `package/activity` ids. */
    val dockApps: List<String> = emptyList(),
    /** True once the dock has been seeded, so a later empty scan cannot re-seed it. */
    val dockInitialized: Boolean = false,
    /** Whether the floating shortcut wheel is on. */
    val shortcutEnabled: Boolean = false,
    /** The apps on the floating wheel, in the order they were picked. */
    val shortcutApps: List<String> = emptyList(),
    /**
     * The shortcuts chosen for the floating wheel's shortcut mode, as `package#shortcutId`.
     *
     * Deliberately separate from [shortcutApps], which is the floating wheel's list of
     * applications. They are two different things and one must never overwrite the other.
     * Only stable identifiers are stored — never a Drawable or a ShortcutInfo, both of which
     * belong to the moment they were read.
     */
    val floatingShortcuts: List<String> = emptyList(),
    /**
     * Which mode the floating wheel is in.
     *
     * A plain string rather than an enum so an unknown stored value can fall back safely.
     * Defaults to app mode, so a fresh install and every existing install start exactly as
     * the wheel behaves today.
     */
    val floatingMode: String = MODE_APP,
    /** True once the user has chosen for the floating wheel. */
    val shortcutInitialized: Boolean = false,
    /**
     * Where the docked wheel sits along its edge, as a fraction of that edge's travel.
     *
     * Still called "offset Y" because that is what it was while the wheel could only live on
     * the right edge, and the stored key keeps that name so existing installs carry on from
     * where they were. On the left and right edges it runs top to bottom; on the top and
     * bottom edges it runs left to right.
     */
    val shortcutOffsetY: Float = HomeSettings.DEFAULT_SHORTCUT_OFFSET,
    /**
     * Which screen edge the floating wheel is docked to: 0 left, 1 top, 2 right, 3 bottom.
     *
     * A plain number so an unknown stored value can fall back safely, and so this file does
     * not depend on the overlay package. Defaults to the right edge, where the wheel has
     * always been.
     */
    val shortcutEdge: Int = HomeSettings.DEFAULT_SHORTCUT_EDGE,
    /** The floating wheel's own shape. Editable from its own customiser. */
    val shortcutLook: WheelLook = HomeSettings.DEFAULT_SHORTCUT_LOOK,
    /**
     * Whether Orbit plays haptic feedback at all. On by default; the one switch for every
     * surface (home screen, drawer, floating wheel). Stored, so it survives restarts.
     */
    val hapticsEnabled: Boolean = true
) {
    /** The wheel on [index], or the first one when the index is out of range. */
    fun wheelAt(index: Int): WheelPage =
        wheels.getOrElse(index) { wheels.firstOrNull() ?: WheelPage() }

    companion object {
        /** One source of truth: the seeder and the stored list must agree. */
        const val DOCK_MAX = DockDefaults.MAX_DOCK

        /** Enough wheels for anyone, without letting the list grow without limit. */
        const val MAX_WHEELS = 8

        /**
         * The floating wheel's starting shape.
         *
         * Eight slots rather than the pages' twelve: at this size twelve leaves each
         * icon three quarters of its arc, which reads as a wall of icons. Eight puts
         * them at half, and shows a column of five — which is what the wheel is for.
         * The icon size is the smallest the app-size control offers, because a small
         * wheel is exactly where a small icon is wanted.
         */
        val DEFAULT_SHORTCUT_LOOK = WheelLook(
            visibleCount = 8,
            iconScale = WheelLook.ICON_RANGE.endInclusive,
            radiusScale = 0.97f,
            centreScale = WheelLook.CENTRE_RANGE.endInclusive
        )

        /** The floating wheel starts in the middle of the screen edge. */
        /** The floating wheel showing the chosen applications. The default, and today's behaviour. */
        const val MODE_APP = "app"

        /** The floating wheel showing the chosen app shortcuts. */
        const val MODE_SHORTCUT = "shortcut"

        const val DEFAULT_SHORTCUT_OFFSET = 0.83f

        /** The right edge, which is where the floating wheel started out. */
        const val DEFAULT_SHORTCUT_EDGE = 2
        const val SHORTCUT_EDGE_MIN = 0
        const val SHORTCUT_EDGE_MAX = 3

        /**
         * The whole travel of the bar along the edge, as a fraction of its available
         * height: 0 is the top of the screen and 1 the bottom.
         *
         * Absolute, not an offset around the default. It used to be
         * `DEFAULT ± RANGE`, which was fine while the default sat in the middle —
         * but moving the default to 0.83 moved the window with it, so the bar could
         * only reach 0.43 to 1.0, the bottom half of the screen. A range that depends
         * on the default will always do that.
         */
        const val SHORTCUT_OFFSET_MIN = 0f
        const val SHORTCUT_OFFSET_MAX = 1f
    }
}

/**
 * Local persistence. Nothing leaves the device, and it is included in Android's
 * backup and device transfer so the layout survives a new phone.
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        /** The wheels, one encoded line each. See [WheelPagesCodec]. */
        val WHEELS = stringPreferencesKey("wheels")
        // Read-only now: these held the single wheel before there could be more
        // than one, and an existing install's choices still live here.
        val WHEEL_APPS = stringPreferencesKey("wheel_apps")
        val WHEEL_INITIALIZED = booleanPreferencesKey("wheel_initialized")
        val WHEEL_COUNT = intPreferencesKey("wheel_count")
        val WHEEL_ICON_SCALE = floatPreferencesKey("wheel_icon_scale")
        val WHEEL_RADIUS_SCALE = floatPreferencesKey("wheel_radius_scale")
        val WHEEL_CENTRE_SCALE = floatPreferencesKey("wheel_centre_scale")
        val WHEEL_OFFSET_Y = floatPreferencesKey("wheel_offset_y")
        val DRAWER_GRID = booleanPreferencesKey("drawer_grid")
        val WHEEL_MATCH_HINT = booleanPreferencesKey("wheel_match_hint_seen")
        val DOCK_APPS = stringPreferencesKey("dock_apps")
        val DOCK_INITIALIZED = booleanPreferencesKey("dock_initialized")
        val SHORTCUT_ENABLED = booleanPreferencesKey("shortcut_enabled")
        val SHORTCUT_APPS = stringPreferencesKey("shortcut_apps")
        val FLOATING_SHORTCUTS = stringPreferencesKey("floating_shortcuts")
        val FLOATING_MODE = stringPreferencesKey("floating_mode")
        val SHORTCUT_INITIALIZED = booleanPreferencesKey("shortcut_initialized")
        val SHORTCUT_OFFSET_Y = floatPreferencesKey("shortcut_offset_y")
        val SHORTCUT_EDGE = intPreferencesKey("shortcut_edge")
        val SHORTCUT_LOOK = stringPreferencesKey("shortcut_look")
        // Legacy single-home-wheel key. Read only during the one-time migration below.
        val CENTRE_HOME = stringPreferencesKey("centre_home")
        val CENTRE_HOME_WHEELS = stringPreferencesKey("centre_home_wheels")
        val HIDDEN_WALLPAPER_SHOWING = booleanPreferencesKey("hidden_wallpaper_showing")

    /** Which of the clock bar's colours is chosen, by position in the palette. */
    val CLOCK_COLOUR = intPreferencesKey("clock_colour")

    // Where the time bar sits, as a share of the screen so it holds on any size of phone, and how big
    // and how long it is. All four are set by dragging and resizing the bar on the home screen.
    val CLOCK_X = floatPreferencesKey("clock_x")
    val CLOCK_Y = floatPreferencesKey("clock_y")
    val CLOCK_TEXT_SCALE = floatPreferencesKey("clock_text_scale")
    val CLOCK_WIDTH_SCALE = floatPreferencesKey("clock_width_scale")
        val HOME_GRID = booleanPreferencesKey("home_grid")
        val GRID_ORDER = stringPreferencesKey("grid_order")
        val HOME_APPS = stringPreferencesKey("home_apps")
        val HOME_FOLDERS = stringPreferencesKey("home_folders")
        val HOME_INITIALIZED = booleanPreferencesKey("home_initialized")
        val CENTRE_FLOATING = stringPreferencesKey("centre_floating")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }

    /**
     * Lenient: an unreadable or corrupt store yields the defaults, so no collector ever dies on it
     * and the home screen can always draw. Anything that is not an I/O failure still propagates.
     */
    val settings: Flow<HomeSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) {
                Diagnostics.warn("settings unreadable, using defaults", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it.toSettings() }

    /**
     * Just the haptics switch, for the haptics engine, which lives in both the Activity and the
     * overlay service and must not decode the whole settings object on every unrelated change.
     * Lenient like [settings]: an unreadable store means the default (on).
     */
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            if (e is IOException) {
                Diagnostics.warn("haptics setting unreadable, using default", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[Keys.HAPTICS_ENABLED] ?: true }
        .distinctUntilChanged()

    private fun Preferences.toSettings(): HomeSettings {
        val p = this
        return HomeSettings(
            wheels = p.wheels(),
            drawerGrid = p[Keys.DRAWER_GRID] ?: true,
            wheelMatchHintSeen = p[Keys.WHEEL_MATCH_HINT] ?: false,
            hapticsEnabled = p[Keys.HAPTICS_ENABLED] ?: true,
            hiddenWallpaperShowing = p[Keys.HIDDEN_WALLPAPER_SHOWING] ?: false,
            clockColour = p[Keys.CLOCK_COLOUR] ?: 3,
            clockX = p[Keys.CLOCK_X] ?: 0.5f,
            clockY = p[Keys.CLOCK_Y] ?: 0.06f,
            clockTextScale = p[Keys.CLOCK_TEXT_SCALE] ?: 1f,
            clockWidthScale = p[Keys.CLOCK_WIDTH_SCALE] ?: 1f,
            homeGrid = p[Keys.HOME_GRID] ?: false,
            homeApps = p[Keys.HOME_APPS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
            homeInitialized = p[Keys.HOME_INITIALIZED] ?: false,
            gridFolders = GridFolderCodec.decode(p[Keys.HOME_FOLDERS]),
            gridOrder = p[Keys.GRID_ORDER]?.split("\n")?.let { written ->
                // An empty entry is a cell with nothing in it, so it is kept. The trailing ones are
                // only the string's own shape, not a gap at the end of the grid.
                written.dropLastWhile { it.isEmpty() }
            } ?: emptyList(),
            centreHome = p[Keys.CENTRE_HOME_WHEELS]?.let(CentreChoicesCodec::decode)
                ?: CentreChoicesCodec.migrateLegacy(p[Keys.CENTRE_HOME]),
            centreFloating = CentreCodec.decode(p[Keys.CENTRE_FLOATING]),
            dockApps = p[Keys.DOCK_APPS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
            dockInitialized = p[Keys.DOCK_INITIALIZED] ?: false,
            shortcutEnabled = p[Keys.SHORTCUT_ENABLED] ?: false,
            floatingShortcuts = p[Keys.FLOATING_SHORTCUTS]?.split("\n")?.filter { it.isNotBlank() }
                ?: emptyList(),
            floatingMode = p[Keys.FLOATING_MODE] ?: HomeSettings.MODE_APP,
            shortcutApps = p[Keys.SHORTCUT_APPS]?.split("\n")?.filter { it.isNotBlank() }
                ?: emptyList(),
            shortcutInitialized = p[Keys.SHORTCUT_INITIALIZED] ?: false,
            shortcutLook = WheelLookCodec.decode(p[Keys.SHORTCUT_LOOK], HomeSettings.DEFAULT_SHORTCUT_LOOK),
            // A stored value from before the range was absolute is still on the same
            // 0-to-1 scale, so it is clamped rather than discarded.
            shortcutOffsetY = safeFloat(p[Keys.SHORTCUT_OFFSET_Y], HomeSettings.DEFAULT_SHORTCUT_OFFSET)
                .coerceIn(HomeSettings.SHORTCUT_OFFSET_MIN, HomeSettings.SHORTCUT_OFFSET_MAX),
            // An install from before the wheel could change edge has no stored value, and a
            // value outside the four edges is not one of them: both mean the right edge.
            shortcutEdge = (p[Keys.SHORTCUT_EDGE] ?: HomeSettings.DEFAULT_SHORTCUT_EDGE)
                .takeIf { it in HomeSettings.SHORTCUT_EDGE_MIN..HomeSettings.SHORTCUT_EDGE_MAX }
                ?: HomeSettings.DEFAULT_SHORTCUT_EDGE
        )
    }

    /** The current settings, read once. Lenient, like [settings]. */
    suspend fun current(): HomeSettings = settings.first()

    /**
     * The current settings, read once, and strict: throws if the store cannot be read.
     * For screens that must not mistake a failed read for an empty selection and then save over it.
     */
    suspend fun currentStrict(): HomeSettings = context.dataStore.data.first().toSettings()

    /**
     * The stored wheels.
     *
     * An install that predates multiple wheels kept its one wheel in the older keys,
     * so those are read whenever the new key is absent. Doing it on read — rather
     * than with a one-off rewrite — means those choices survive whether or not
     * anything is ever written again.
     */
    private fun Preferences.wheels(): List<WheelPage> =
        WheelPagesCodec.decode(this[Keys.WHEELS]).ifEmpty { listOf(legacyWheel()) }

    /** The single wheel as it was stored before there could be more than one. */
    private fun Preferences.legacyWheel(): WheelPage = WheelPage(
        apps = this[Keys.WHEEL_APPS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
        initialized = this[Keys.WHEEL_INITIALIZED] ?: false,
        look = WheelLook(
            visibleCount = (this[Keys.WHEEL_COUNT] ?: WheelLook.DEFAULT_COUNT)
                .coerceIn(WheelGeometry.MIN_SLOTS, WheelGeometry.MAX_SLOTS),
            iconScale = safeFloat(this[Keys.WHEEL_ICON_SCALE], WheelLook.DEFAULT_ICON_SCALE)
                .coerceIn(WheelLook.ICON_RANGE.start, WheelLook.ICON_RANGE.endInclusive),
            radiusScale = safeFloat(this[Keys.WHEEL_RADIUS_SCALE], WheelLook.DEFAULT_RADIUS_SCALE)
                .coerceIn(WheelLook.RADIUS_RANGE.start, WheelLook.RADIUS_RANGE.endInclusive),
            centreScale = safeFloat(this[Keys.WHEEL_CENTRE_SCALE], WheelLook.DEFAULT_CENTRE_SCALE)
                .coerceIn(WheelLook.CENTRE_RANGE.start, WheelLook.CENTRE_RANGE.endInclusive),
            offsetY = safeFloat(this[Keys.WHEEL_OFFSET_Y], 0f)
                .coerceIn(-WheelLook.MAX_OFFSET, WheelLook.MAX_OFFSET)
        )
    )

    /** The apps chosen for one wheel, and whether the user has ever chosen. */
    suspend fun setWheelApps(index: Int, ids: List<String>) {
        context.dataStore.edit { p ->
            val wheels = p.wheels().toMutableList()
            if (index !in wheels.indices) return@edit
            wheels[index] = wheels[index].copy(apps = ids, initialized = true)
            p[Keys.WHEELS] = WheelPagesCodec.encode(wheels)
        }
    }

    /**
     * Persists one wheel's whole shape in a single write, so a change can never land
     * half-applied.
     */
    suspend fun setWheelLook(index: Int, look: WheelLook) {
        context.dataStore.edit { p ->
            val wheels = p.wheels().toMutableList()
            if (index !in wheels.indices) return@edit
            wheels[index] = wheels[index].copy(look = look)
            p[Keys.WHEELS] = WheelPagesCodec.encode(wheels)
        }
    }

    /** Moves one wheel vertically. Only offsetY changes; every other field keeps its stored value. */
    suspend fun setWheelOffsetY(index: Int, offsetY: Float) {
        context.dataStore.edit { p ->
            val wheels = p.wheels().toMutableList()
            if (index !in wheels.indices) return@edit
            val look = wheels[index].look
            wheels[index] = wheels[index].copy(
                look = look.copy(
                    offsetY = offsetY.coerceIn(-WheelLook.MAX_OFFSET, WheelLook.MAX_OFFSET)
                )
            )
            p[Keys.WHEELS] = WheelPagesCodec.encode(wheels)
        }
    }

    /**
     * Appends a wheel shaped like the one on [fromIndex].
     *
     * A new wheel starts from what the user has already set up rather than from the
     * factory defaults, so adding a second wheel does not mean re-doing the sizes.
     * Its apps are deliberately left unchosen: a clean wheel to curate, rather than
     * a duplicate of the one it was copied from.
     *
     * Refused once [HomeSettings.MAX_WHEELS] are already there.
     */
    suspend fun addWheel(fromIndex: Int) {
        context.dataStore.edit { p ->
            val next = WheelPages.appendLike(p.wheels(), fromIndex)
            if (next == p.wheels()) return@edit
            p[Keys.WHEELS] = WheelPagesCodec.encode(next)
        }
    }

    /**
     * Removes a wheel. The first is the home screen's own and is never removed, so
     * there is always something to come back to.
     */
    suspend fun removeWheel(index: Int) {
        context.dataStore.edit { p ->
            val wheels = p.wheels()
            val next = WheelPages.removeAt(wheels, index)
            if (next == wheels) return@edit
            p[Keys.WHEELS] = WheelPagesCodec.encode(next)
            val choices = p[Keys.CENTRE_HOME_WHEELS]?.let(CentreChoicesCodec::decode)
                ?: CentreChoicesCodec.migrateLegacy(p[Keys.CENTRE_HOME])
            // The removed wheel loses its own choice; higher-index choices move with their
            // wheel instead of being truncated or reassigned to the wrong page.
            p[Keys.CENTRE_HOME_WHEELS] = CentreChoicesCodec.encode(
                CentreChoicesCodec.removeAt(choices, index)
            )
        }
    }

    suspend fun setShortcutEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHORTCUT_ENABLED] = enabled }
    }

    /** The apps on the floating wheel, and whether the user has ever chosen. */
    /**
     * The floating wheel's chosen shortcuts, in the order they were picked.
     *
     * A failed or unavailable catalog must never call this with an empty list: that would
     * erase a real selection because a query happened to fail.
     */
    suspend fun setFloatingShortcuts(keys: List<String>) {
        context.dataStore.edit { it[Keys.FLOATING_SHORTCUTS] = keys.joinToString("\n") }
    }

    /** Which mode the floating wheel is in. */
    suspend fun setFloatingMode(mode: String) {
        context.dataStore.edit { it[Keys.FLOATING_MODE] = mode }
    }

    suspend fun setShortcutApps(ids: List<String>) {
        context.dataStore.edit {
            it[Keys.SHORTCUT_APPS] = ids.joinToString("\n")
            it[Keys.SHORTCUT_INITIALIZED] = true
        }
    }

    /** The floating wheel's shape. */
    suspend fun setShortcutLook(look: WheelLook) {
        context.dataStore.edit { it[Keys.SHORTCUT_LOOK] = WheelLookCodec.encode(look) }
    }

    /** Where the docked wheel sits vertically. */
    suspend fun setShortcutOffsetY(fraction: Float) {
        context.dataStore.edit {
            it[Keys.SHORTCUT_OFFSET_Y] = fraction.coerceIn(
                HomeSettings.SHORTCUT_OFFSET_MIN,
                HomeSettings.SHORTCUT_OFFSET_MAX
            )
        }
    }

    /**
     * Where the docked wheel sits: which edge, and how far along it.
     *
     * One write for both, so a reader can never see the new edge with the old position (which
     * would put the wheel somewhere the user never left it) or the other way round.
     */
    suspend fun setShortcutDock(edge: Int, fraction: Float) {
        context.dataStore.edit {
            it[Keys.SHORTCUT_EDGE] = edge.coerceIn(
                HomeSettings.SHORTCUT_EDGE_MIN,
                HomeSettings.SHORTCUT_EDGE_MAX
            )
            it[Keys.SHORTCUT_OFFSET_Y] = fraction.coerceIn(
                HomeSettings.SHORTCUT_OFFSET_MIN,
                HomeSettings.SHORTCUT_OFFSET_MAX
            )
        }
    }

    /** Remembers how the drawer was last left. */
    /** Records that the match-all explanation has been shown. */
    suspend fun setWheelMatchHintSeen() {
        context.dataStore.edit { it[Keys.WHEEL_MATCH_HINT] = true }
    }

    /**
     * Gives every wheel the same shape.
     *
     * The shape only — each wheel keeps its own apps. Matching the apps as well would be a
     * different and much more destructive thing, and the settings page is about how a wheel
     * looks, not what is on it.
     */
    suspend fun matchAllWheelLooks(look: WheelLook) {
        context.dataStore.edit { p ->
            val wheels = p.wheels()
            if (wheels.isEmpty()) return@edit
            p[Keys.WHEELS] = WheelPagesCodec.encode(wheels.map { it.copy(look = look) })
        }
    }

    /** Stores the order the grid home shows apps in. */
    /**
     * Stores the home screen's apps and its cells as one change.
     *
     * Membership and arrangement used to be written by two separate edits, and an interruption between
     * them could leave a home screen whose apps and whose cells disagreed - an app placed in a cell its
     * own membership no longer contains, or the other way round. One edit means one state: the screen
     * is either the one before the change or the one after it, never a mixture of the two.
     *
     * Both are normalised here rather than at the call sites, so every caller gets the same guarantees
     * without having to know them.
     */
    suspend fun setGridLayout(
        homeIds: List<String>,
        cells: List<String>,
        /** The folders, written in the same edit as the cells that hold their ids. Null leaves them as stored. */
        folders: List<GridFolder>? = null
    ) {
        val membership = normaliseHomeIds(homeIds)
        val arrangement = normaliseCells(cells)
        context.dataStore.edit {
            it[Keys.HOME_APPS] = membership.joinToString("\n")
            it[Keys.GRID_ORDER] = arrangement.joinToString("\n")
            if (folders != null) it[Keys.HOME_FOLDERS] = GridFolderCodec.encode(folders)
            it[Keys.HOME_INITIALIZED] = true
        }
    }

    /**
     * Adds one app to the home screen as a single edit that reads the *stored* membership and cells.
     * [placeInCells] decides which cell the app lands in. Does nothing if the app is already there.
     */
    suspend fun addAppToHome(
        appId: String,
        placeInCells: (cells: List<String>, appId: String) -> List<String>
    ) {
        context.dataStore.edit { p ->
            val ids = normaliseHomeIds(p[Keys.HOME_APPS]?.split("\n").orEmpty())
            if (appId in ids) return@edit
            val cells = p[Keys.GRID_ORDER]?.split("\n").orEmpty().dropLastWhile { it.isEmpty() }
            p[Keys.HOME_APPS] = normaliseHomeIds(ids + appId).joinToString("\n")
            p[Keys.GRID_ORDER] = normaliseCells(placeInCells(cells, appId)).joinToString("\n")
            p[Keys.HOME_INITIALIZED] = true
        }
    }

    /** Stores which home layout is in use. */
    suspend fun setHomeGrid(grid: Boolean) {
        context.dataStore.edit { it[Keys.HOME_GRID] = grid }
    }

    /** Stores which wallpaper is showing. */
    suspend fun setHiddenWallpaperShowing(showing: Boolean) {
        context.dataStore.edit { it[Keys.HIDDEN_WALLPAPER_SHOWING] = showing }
    }

    /** The clock bar's background, by position in the palette. */
    suspend fun setClockColour(index: Int) {
        context.dataStore.edit { it[Keys.CLOCK_COLOUR] = index }
    }

    /** Where the time bar sits on the home screen, and how big and how long it is. */
    suspend fun setClockFrame(x: Float, y: Float, textScale: Float, widthScale: Float) {
        context.dataStore.edit {
            it[Keys.CLOCK_X] = x
            it[Keys.CLOCK_Y] = y
            it[Keys.CLOCK_TEXT_SCALE] = textScale
            it[Keys.CLOCK_WIDTH_SCALE] = widthScale
        }
    }

    /** Stores what one wheel's centre shows. */
    suspend fun setCentre(
        target: CentreTarget,
        choice: CentreChoice,
        wheelIndex: Int = 0
    ) {
        context.dataStore.edit { p ->
            if (target == CentreTarget.HOME) {
                val choices = p[Keys.CENTRE_HOME_WHEELS]?.let(CentreChoicesCodec::decode)
                    ?: CentreChoicesCodec.migrateLegacy(p[Keys.CENTRE_HOME])
                p[Keys.CENTRE_HOME_WHEELS] = CentreChoicesCodec.encode(
                    CentreChoicesCodec.setAt(choices, wheelIndex, choice)
                )
            } else {
                p[Keys.CENTRE_FLOATING] = CentreCodec.encode(choice)
            }
        }
    }

    /** Stores whether haptic feedback is on. */
    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setDrawerGrid(grid: Boolean) {
        context.dataStore.edit { it[Keys.DRAWER_GRID] = grid }
    }

    suspend fun setDockApps(ids: List<String>) {
        context.dataStore.edit { it[Keys.DOCK_APPS] = ids.take(HomeSettings.DOCK_MAX).joinToString("\n") }
    }

    suspend fun setDockInitialized(initialized: Boolean) {
        context.dataStore.edit { it[Keys.DOCK_INITIALIZED] = initialized }
    }

    /**
     * Seeds the dock once. The check and both writes happen in one edit, against the stored value,
     * so a seed can never overwrite a dock the user already has, and the pair can never be split.
     */
    suspend fun seedDockIfNeeded(ids: List<String>) {
        context.dataStore.edit { p ->
            if (p[Keys.DOCK_INITIALIZED] == true) return@edit
            p[Keys.DOCK_APPS] = ids.take(HomeSettings.DOCK_MAX).joinToString("\n")
            p[Keys.DOCK_INITIALIZED] = true
        }
    }

    /** A corrupt stored float must never poison the geometry. */
    private fun safeFloat(value: Float?, fallback: Float): Float =
        if (value != null && value.isFinite()) value else fallback

    companion object {
        // Wide enough to hold ICON_RANGE, whose midpoint is the default icon size.
        val MIN_ICON_SCALE get() = WheelLook.ICON_RANGE.start
        val MAX_ICON_SCALE get() = WheelLook.ICON_RANGE.endInclusive
    }
}

/**
 * The membership list as it should be stored: no blanks, and no repeats.
 *
 * The first time an id appears is the one kept, not the last, so a duplicate arriving from somewhere
 * cannot reshuffle the order a user arranged. Blank ids are dropped rather than kept as an empty entry:
 * membership is a list of apps, and there is no such thing as an app called nothing.
 */
internal fun normaliseHomeIds(ids: List<String>): List<String> {
    val seen = LinkedHashSet<String>()
    for (id in ids) if (id.isNotBlank()) seen.add(id)
    return seen.toList()
}

/**
 * The cells as they should be stored.
 *
 * Interior empties are kept: an empty cell is one the user cleared, and it is where the next thing they
 * put down will go - losing it would silently close the gap they made. Only the trailing ones are
 * dropped, and only because the reader drops them on the way back in, so writing them would be writing
 * something that never survives a round trip.
 */
internal fun normaliseCells(cells: List<String>): List<String> =
    cells.dropLastWhile { it.isEmpty() }
