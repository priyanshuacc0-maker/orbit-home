package com.orbit.home.ui

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.orbit.home.R
import com.orbit.home.apps.AppActions
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.AppLabels
import com.orbit.home.apps.InstalledApp
import com.orbit.home.preferences.GridFolder
import com.orbit.home.ui.theme.OrbitColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The home screen as a grid of apps, in pages, and a placement the user can drag into shape.
 *
 * Cells, not a sequence. A sequence has no room in it, which is why an app could not be dropped into
 * empty space before — there was no empty space to drop it into. Here a cell can hold nothing, and
 * empty is somewhere an app can go.
 *
 * The grid holds every app, so dragging does not add or remove anything: it changes which cell an
 * app sits in. Dropped on an empty cell an app moves there and leaves its own cell empty; dropped on
 * an occupied one the two change places, so there is never a hole.
 */
@Composable
fun GridHome(
    allApps: List<InstalledApp>,
    homeApps: List<String>,
    homeInitialized: Boolean,
    /**
     * Called once with the whole layout - which apps this screen holds, where they are, and the folders.
     *
     * One callback and not two: membership and arrangement are one thing, and handing them over
     * separately is what allowed a half-applied screen to be written down.
     */
    onSaveLayout: (List<String>, List<String>, List<GridFolder>) -> Unit,
    order: List<String>,
    /**
     * The folders. A folder is a cell like an app is: its id sits in the layout, and the apps inside it
     * have no cell of their own. They are saved with the cells in the one [onSaveLayout] call.
     */
    folders: List<GridFolder> = emptyList(),
    iconLoader: AppIconLoader,
    onLaunch: (InstalledApp) -> Unit,
    /**
     * An app being carried in from the drawer, or null. Its position is in root coordinates. While it
     * is set the grid shows where it would land and turns pages when it is held at an edge; when
     * [ExternalAppDrag.released] is set the app is dropped there and [onExternalDragDone] is called.
     */
    externalDrag: ExternalAppDrag? = null,
    /** The carried app's live position, read here so only the grid recomposes as the finger moves. */
    externalPosition: (() -> Offset)? = null,
    /** Called when a carried app has been let go; [landed] is true if it was put into a cell. */
    onExternalDragDone: (landed: Boolean) -> Unit = {},
    /**
     * Offered an app that was let go (root coordinates) outside the grid's cells. Returns true if the
     * dock took it, in which case the app leaves the home grid. Null when there is no dock.
     */
    onDropOnDock: ((id: String, at: Offset) -> Boolean)? = null,
    /**
     * The app being carried inside the grid (or out of a folder) and where it is, in root coordinates;
     * both null when nothing is. Lets the dock open a gap under it.
     */
    onCarryMove: (id: String?, at: Offset?) -> Unit = { _, _ -> },
    /** With motion off, apps still get out of the way - they just arrive in one frame. */
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Alphabetical, using the locale-aware collator so accents and non-Latin scripts order the way a
    // reader expects — the same order the drawer uses.
    val sorted = remember(allApps) {
        allApps.sortedWith(compareBy(AppLabels.comparator()) { it.label })
    }
    val byId = remember(sorted) { sorted.associateBy { it.id } }

    // What was just dropped, held on screen until the stored arrangement catches up. Writing to
    // storage and drawing from it means a moment of the old arrangement, which reads as the app
    // going back where it was and then jumping.
    var dropped by remember { mutableStateOf<List<String>?>(null) }
    // The folders that go with `dropped`: the cells name folders, so the two are held together or a
    // new folder's cell would be empty until the stored folders caught up.
    var droppedFolders by remember { mutableStateOf<List<GridFolder>?>(null) }
    LaunchedEffect(order, dropped, folders, droppedFolders) {
        if (dropped != null && order == dropped && (droppedFolders == null || folders == droppedFolders)) {
            dropped = null
            droppedFolders = null
        }
    }
    // If the write fails, the stored order never matches. Fall back to what is really stored rather than
    // showing an arrangement that will vanish on restart.
    LaunchedEffect(dropped) {
        if (dropped != null) {
            delay(3000)
            dropped = null
            droppedFolders = null
        }
    }
    // The apps this screen holds, not every app on the phone: a home screen is a chosen set, and
    // everything else lives in the drawer. Before it has ever been arranged it is shown with a phone's
    // own starting arrangement - and written down, so from then on it is the user's and not mine.
    var seeded by remember { mutableStateOf<List<String>?>(null) }
    // Set the moment a seed is written, so a change in `rows` or `sorted` before the stored flag lands
    // cannot write a second, different seed over the first.
    var seedWritten by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberOrbitHaptics()
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf<HeldApp?>(null) }
    // An app being carried out of an open folder. It is handled as one arriving from outside the grid -
    // the same as an app dragged in from the drawer - so everything that drop already knows applies.
    var folderDrag by remember { mutableStateOf<ExternalAppDrag?>(null) }
    // The drawer's drag if there is one, otherwise the one out of a folder.
    val activeDrag = externalDrag?.let { d ->
        externalPosition?.invoke()?.let { p -> d.copy(x = p.x, y = p.y) } ?: d
    } ?: folderDrag
    // The folder that is open, if any.
    var openFolderId by remember { mutableStateOf<String?>(null) }
    // The cell a carried app has been held over long enough that dropping it will make or fill a
    // folder there, rather than reorder.
    var folderTarget by remember { mutableStateOf<Int?>(null) }
    // The app whose menu is showing, and where to put it. Cleared the moment a drag begins.
    var menuFor by remember { mutableStateOf<String?>(null) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }

    // The space the menu is drawn in, and the menu's own size. The menu is placed at the app it
    // belongs to, and in the last column or the bottom row that put it half off the screen - so it
    // is nudged back inside once both sizes are known.
    var menuBounds by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    // Whether this hold has been carried anywhere, and so will not be offered a menu.
    var carried by remember { mutableStateOf(false) }
    // The page turn that is waiting to happen, if the app is being held over an edge.
    var turning by remember { mutableStateOf<Job?>(null) }
    // True from the moment an auto-turn is committed until the pager has fully settled.
    // Keep user scrolling disabled during this interval, including after the finger lifts.
    var pageTurnInProgress by remember { mutableStateOf(false) }
    // Number of pages temporarily exposed while an icon is being carried beyond the last page.
    // Empty pages are not stored by GridOrder, so they must exist here until an app is dropped there.
    var expandedPageCount by remember { mutableStateOf(0) }

    // Where this grid sits on the screen, to turn a root-space finger position into a grid position.
    var origin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        // One grid, and the same one on every page.
        //
        // The first page used to get a shorter grid, with its two rows pinned to the bottom so the
        // apps sat low and the top was left clear. It looked like a phone's home screen and was not
        // one: the top of that page was not a grid at all, so nothing could be put there, and it was
        // the only page whose rows did not match the others.
        //
        // Every page of a phone's home screen is the same grid. Launcher3 gives each page the same
        // CellLayout with one row and column count, and its own workspace format has a single
        // rows/columns for the whole thing with apps placed per page. It looks emptier at the top
        // only because the user has not put anything there yet, and every cell of it can be used.
        //
        // Rows are worked out from the height rather than fixed, because a taller screen should hold
        // more of them.
        // The strip the page dots sit in is only taken when there will be dots in it. With a single
        // page it used to be reserved all the same, which left a band of nothing along the bottom of
        // the grid and rows shorter than the height allowed.
        //
        // The count behind that is an upper bound - it counts apps a folder holds, which have no cell
        // of their own - because that is the safe direction. Taking the strip and not needing it costs
        // the band; not taking it and then needing it would draw the dots over the last row.
        val cellCount = if (homeInitialized) homeApps.size else allApps.size
        val rowsWithoutStrip = (maxHeight / CELL_HEIGHT).toInt().coerceAtLeast(1)
        val rowsWithStrip = ((maxHeight - PAGE_DOTS_HEIGHT) / CELL_HEIGHT).toInt().coerceAtLeast(1)
        val dotsExpected = cellCount > GRID_COLUMNS * rowsWithoutStrip
        val rows = if (dotsExpected) rowsWithStrip else rowsWithoutStrip
        // Which apps this screen holds, and where. Declared here because the page count below is
        // worked out from them, and the seeding below needs the row count this line just produced.
        val homeIds = if (homeInitialized) homeApps else (seeded ?: emptyList())


        val liveFolders = droppedFolders ?: folders
        val folderById = remember(liveFolders) { liveFolders.associateBy { it.id } }
        // What has a cell: the apps that are not in a folder, and the folders. An app in a folder is
        // somewhere else, and listing it here as well would put it on the screen twice.
        val cellIds = remember(homeIds, liveFolders, byId) {
            GridFolders.cellIds(homeIds, liveFolders, byId.keys)
        }
        val cells = remember(sorted, order, dropped, cellIds) {
            GridOrder.cells(cellIds, dropped ?: order)
        }

        /** Saves the layout, and holds it on screen until what was stored catches up. */
        fun commit(ids: List<String>, newCells: List<String>, newFolders: List<GridFolder>) {
            dropped = newCells
            droppedFolders = newFolders
            onSaveLayout(ids, newCells, newFolders)
        }

        /**
         * Takes an app off this screen.
         *
         * Its cell is emptied rather than the ones after it moved up, so nothing else on the screen
         * moves - the space it was in stays where it was, free to be used again. And it leaves the set
         * of apps this screen holds, which is the thing that makes it gone: everything not in that set
         * is in the drawer, where it still is.
         */
        fun removeFromHome(id: String) {
            val folder = folderById[id]
            if (folder != null) {
                // A folder goes with everything in it. They are all still in the drawer.
                val out = GridFolders.remove(FolderLayout(cells, liveFolders), id)
                commit(homeIds.filterNot { it in folder.apps }, out.cells, out.folders)
                return
            }
            onSaveLayout(
                homeIds.filterNot { it == id },
                cells.map { if (it == id) GridOrder.EMPTY else it },
                liveFolders
            )
        }
        val perPage = (GRID_COLUMNS * rows).coerceAtLeast(1)
        val storedPageCount = GridOrder.pageCount(cells.size, perPage)
        val pageCount = maxOf(storedPageCount, expandedPageCount)
        // Gesture coroutines must see the latest count without being restarted when a new
        // temporary page is added. Restarting pointerInput mid-drag cancels the carried item.
        val livePageCount = rememberUpdatedState(pageCount)
        val liveExternalDrag = rememberUpdatedState(activeDrag)
        val latestDropOnDock = rememberUpdatedState(onDropOnDock)
        val latestCarryMove = rememberUpdatedState(onCarryMove)
        val liveOrigin = rememberUpdatedState(origin)
        val pager = rememberPagerState(pageCount = { pageCount })
        // A light detent each time the grid turns to another page, whether the finger swiped it
        // or a carried app held at the edge pulled it round. The page the grid opened on is not
        // a page turn, so the first value is only recorded.
        var lastPageHaptic by remember { mutableIntStateOf(-1) }
        LaunchedEffect(pager.currentPage) {
            val page = pager.currentPage
            if (lastPageHaptic >= 0 && lastPageHaptic != page) haptics.pageSnap()
            lastPageHaptic = page
        }

        // A new home screen starts with apps on it rather than empty. It has to be done here, where
        // the row count is known: how many empty cells sit above the first page's apps depends on how
        // many rows fit, and that is only known once the screen has been measured. A phone's own
        // starting layout is per grid size for the same reason.
        LaunchedEffect(homeInitialized, rows, sorted) {
            if (!homeInitialized && !seedWritten && sorted.isNotEmpty()) {
                seedWritten = true
                val start = HomeSeed.cells(sorted.map { it.id }, GRID_COLUMNS, rows)
                seeded = start
                // Membership and arrangement together, once. The gaps ARE the arrangement - the clear
                // top of the first page is made of empty cells - so they have to be written with it or
                // the grid fills them with the next app that comes along.
                onSaveLayout(start.filter { it.isNotEmpty() }, start, emptyList())
            }
        }
        val cellWidth = maxWidth / GRID_COLUMNS
        val cellWidthPx = with(density) { cellWidth.toPx() }
        // The height a row actually gets: what is left after the dots strip, divided by how many
        // rows there are. Sizing the rows against the height they wanted left whatever did not
        // divide evenly as a bare strip between the last row and the dock.
        val rowHeight = (maxHeight - PAGE_DOTS_HEIGHT) / rows
        val cellHeightPx = with(density) { rowHeight.toPx() }
        val liveCellWidthPx = rememberUpdatedState(cellWidthPx)
        val liveGridWidthPx = rememberUpdatedState(with(density) { maxWidth.toPx() })

        fun firstCellOn(page: Int) = page * perPage

        fun sliceOf(page: Int): List<String> {
            val from = firstCellOn(page)
            if (from >= cells.size) return emptyList()
            return cells.subList(from, (from + perPage).coerceAtMost(cells.size))
        }

        /**
         * Which piece of the target a carried app is over, or null if it is over neither.
         *
         * Tested per piece rather than as one band: two things that do different work have to know
         * which of them the app was put on, and a single band can only answer "somewhere up there".
         */
        fun targetAt(x: Float, y: Float): DropAction? = dropActionAt(
            x = x,
            y = y,
            widthPx = with(density) { maxWidth.toPx() },
            bottomPx = with(density) { (TARGET_TOP + TARGET_HEIGHT).toPx() },
            marginPx = with(density) { TARGET_MARGIN.toPx() },
            gapPx = with(density) { TARGET_GAP.toPx() }
        )

        // The cell a point falls on. Every cell of the page is one, whether or not anything is in it,
        // because that is what a grid is — so dragging into empty space means exactly what it looks
        // like and the grid grows into it.
        fun cellAt(page: Int, x: Float, y: Float): Int? {
            if (x < 0f || y < 0f) return null
            val column = (x / cellWidthPx).toInt()
            val row = (y / cellHeightPx).toInt()
            if (column !in 0 until GRID_COLUMNS) return null
            if (row !in 0 until rows) return null
            return firstCellOn(page) + row * GRID_COLUMNS + column
        }

        /**
         * Finish the drop only after any committed page turn has reached a real page.
         *
         * [armed] is the cell the app had been held over long enough to make or fill a folder there,
         * taken at the moment of letting go: by the time a page turn has settled it would be gone.
         */
        fun finishHeldDrop(current: HeldApp?, armed: Int?) {
            val action = current?.let { targetAt(it.x, it.y) }
            if (current != null && action != null) {
                expandedPageCount = 0
                carried = false
                when (action) {
                    DropAction.REMOVE -> removeFromHome(current.id)
                    DropAction.UNINSTALL ->
                        byId[current.id]?.let { AppActions.uninstall(context, it.packageName) }
                }
                return
            }

            // Carried down onto the dock and let go: the dock takes it, and it leaves the home grid.
            if (current != null && carried && !GridFolders.isFolderId(current.id) &&
                latestDropOnDock.value?.invoke(current.id, Offset(current.x + origin.x, current.y + origin.y)) == true
            ) {
                expandedPageCount = 0
                carried = false
                removeFromHome(current.id)
                return
            }

            if (current != null && !carried) {
                expandedPageCount = 0
                if (GridFolders.isFolderId(current.id)) {
                    // A folder has no menu of its own to open, so holding one and letting go opens it.
                    openFolderId = current.id
                } else {
                    menuFor = current.id
                    menuAt = Offset(current.x, current.y)
                }
                carried = false
                return
            }

            carried = false
            val landed = current?.let { cellAt(pager.currentPage, it.x, it.y) }
            if (current != null && landed != null && current.from != landed) {
                val page = pager.currentPage
                val target = cells.getOrNull(landed).orEmpty()
                if (armed == landed && target.isNotEmpty() && target != current.id) {
                    // Held over it long enough: this is a folder, not a reorder.
                    val base = FolderLayout(cells, liveFolders)
                    val out = if (GridFolders.isFolderId(target)) {
                        GridFolders.putInto(base, target, current.id)
                    } else {
                        GridFolders.make(
                            base, landed, current.id,
                            GridFolders.newId(liveFolders.mapTo(HashSet()) { it.id }),
                            NEW_FOLDER_NAME
                        )
                    }
                    haptics.confirm()
                    commit(homeIds, out.cells, out.folders)
                } else {
                    val placed = GridOrder.place(
                        cells,
                        current.from,
                        landed,
                        firstCellOn(page)..(firstCellOn(page) + perPage - 1)
                    )
                    // Show the new position at once. Because the destination may be on a newly
                    // created page, keep this list until persistence catches up.
                    commit(homeIds, placed, liveFolders)
                }
            }
            expandedPageCount = 0
        }

        // ---- An app carried in from the drawer -------------------------------------------------
        val ext = activeDrag
        // One stable coroutine owns edge-turns for a drawer app. It reads the latest drag position
        // rather than being keyed to currentPage/current offset: cancelling an animateScrollToPage
        // at the mid-point is what leaves two half-pages visible. Releasing the app does not cancel
        // an already-committed turn; the drop waits for that turn to settle.
        LaunchedEffect(Unit) {
            while (true) {
                val firstDrag = snapshotFlow { liveExternalDrag.value }
                    .first { it != null && !it.released } ?: continue
                val dragId = firstDrag.id
                while (true) {
                    val active = liveExternalDrag.value
                    if (active == null || active.id != dragId || active.released) break

                    val width = liveGridWidthPx.value
                    val reach = liveCellWidthPx.value / 2f
                    val x = active.x - liveOrigin.value.x
                    val direction = when {
                        x + reach > width -> 1
                        x - reach < 0f && pager.currentPage > 0 -> -1
                        else -> 0
                    }
                    if (direction == 0) {
                        delay(40L)
                        continue
                    }

                    delay(EDGE_DWELL_MS)
                    val afterDelay = liveExternalDrag.value
                    if (afterDelay == null || afterDelay.id != dragId || afterDelay.released) break
                    val afterX = afterDelay.x - liveOrigin.value.x
                    val liveDirection = when {
                        afterX + liveCellWidthPx.value / 2f > liveGridWidthPx.value -> 1
                        afterX - liveCellWidthPx.value / 2f < 0f && pager.currentPage > 0 -> -1
                        else -> 0
                    }
                    if (liveDirection == 0 || liveDirection != direction) continue

                    pageTurnInProgress = true
                    try {
                        if (liveDirection > 0 && pager.currentPage >= livePageCount.value - 1) {
                            expandedPageCount = maxOf(expandedPageCount, pager.currentPage + 2)
                            val hasDestination = withTimeoutOrNull(1000L) {
                                snapshotFlow { livePageCount.value }
                                    .first { it > pager.currentPage }
                            }
                            if (hasDestination == null) {
                                expandedPageCount = 0
                                break
                            }
                        }
                        val target = (pager.currentPage + liveDirection)
                            .coerceIn(0, livePageCount.value - 1)
                        if (target != pager.currentPage) pager.animateScrollToPage(target)
                    } finally {
                        pageTurnInProgress = false
                    }
                }
                // Do not start another edge dwell for the just-released drag. Wait until it has
                // been removed (or replaced) before accepting another drag of the same app.
                snapshotFlow { liveExternalDrag.value }
                    .first { it == null || it.id != dragId }
            }
        }

        // Tell the home screen where a carried app is, so the dock can open a gap when it is over it.
        LaunchedEffect(Unit) {
            snapshotFlow {
                val h = held
                val f = folderDrag
                when {
                    h != null && carried -> h.id to Offset(h.x + origin.x, h.y + origin.y)
                    f != null && !f.released -> f.id to Offset(f.x, f.y)
                    else -> null
                }
            }.collect { latestCarryMove.value(it?.first, it?.second) }
        }

        LaunchedEffect(ext?.id, ext?.released) {
            val e = ext
            if (e == null) {
                snapshotFlow { pageTurnInProgress }.first { !it }
                val lastStoredPage = GridOrder.pageCount(cells.size, perPage) - 1
                if (pager.currentPage > lastStoredPage) {
                    pageTurnInProgress = true
                    try {
                        pager.animateScrollToPage(lastStoredPage)
                    } finally {
                        pageTurnInProgress = false
                    }
                }
                expandedPageCount = 0
                return@LaunchedEffect
            }
            if (!e.released) {
                expandedPageCount = 0
                return@LaunchedEffect
            }

            // A release can arrive while the pager is crossing the mid-point. Let the committed
            // turn finish before resolving the drop cell; otherwise the app can be saved to the
            // page that is no longer on screen.
            snapshotFlow { pageTurnInProgress }.first { !it }
            // Carried out of a folder and let go on the dock: it goes there, and leaves the folder.
            if (folderDrag != null && !GridFolders.isFolderId(e.id) &&
                latestDropOnDock.value?.invoke(e.id, Offset(e.x, e.y)) == true
            ) {
                val taken = GridFolders.takeOut(FolderLayout(cells, liveFolders), e.id)
                commit(homeIds.filterNot { it == e.id }, taken.cells, taken.folders)
                expandedPageCount = 0
                folderDrag = null
                openFolderId = null
                return@LaunchedEffect
            }
            val finalX = e.x - origin.x
            val finalY = e.y - origin.y
            val to = cellAt(pager.currentPage, finalX, finalY)
            val target = to?.let { cells.getOrNull(it) }.orEmpty()
            // Put back on the folder it came out of: nothing to do, it never left.
            val backWhereItWas = folderById[target]?.apps?.contains(e.id) == true
            if (to != null && !backWhereItWas) {
                val first = firstCellOn(pager.currentPage)
                val base = FolderLayout(cells, liveFolders)
                val out = if (folderTarget == to && target.isNotEmpty() && target != e.id) {
                    // Held over it long enough: make or fill a folder.
                    if (GridFolders.isFolderId(target)) {
                        GridFolders.putInto(base, target, e.id)
                    } else {
                        GridFolders.make(
                            base, to, e.id,
                            GridFolders.newId(liveFolders.mapTo(HashSet()) { it.id }),
                            NEW_FOLDER_NAME
                        )
                    }
                } else {
                    // An app carried out of a folder leaves it first; one from the drawer is in none.
                    val taken = GridFolders.takeOut(base, e.id)
                    FolderLayout(
                        GridOrder.insert(taken.cells, e.id, to, first..(first + perPage - 1)),
                        taken.folders
                    )
                }
                // The carried app has landed: a completed action, softer than the pick-up.
                haptics.confirm()
                commit(if (e.id in homeIds) homeIds else homeIds + e.id, out.cells, out.folders)
            }
            expandedPageCount = 0
            if (folderDrag != null) {
                // Out of a folder: the drag is ours to end, and so is the folder it came from.
                folderDrag = null
                openFolderId = null
            } else {
                onExternalDragDone(to != null && !backWhereItWas)
            }
        }

        // ---- Getting out of the way, and making folders --------------------------------------------
        // Where the carried app is, in this grid's own coordinates, or null if it is over a drop target
        // at the top. Held and drawer-carried apps are treated the same: both are about to be dropped.
        val hoverHeld = held
        val pointer: Offset? = when {
            hoverHeld != null ->
                if (targetAt(hoverHeld.x, hoverHeld.y) != null) null
                else Offset(hoverHeld.x, hoverHeld.y)
            ext != null -> Offset(ext.x - origin.x, ext.y - origin.y)
            else -> null
        }
        val hovering: Int? = pointer?.let { cellAt(pager.currentPage, it.x, it.y) }
        val carriedNow = held?.id ?: ext?.id

        // Whether the pointer is over the middle of the cell, which is where a folder is made. The edges
        // of a cell are for reordering; Launcher3 splits the two the same way, so an app can be put
        // between two others without making a folder of them.
        fun nearCentre(cell: Int, at: Offset): Boolean {
            val local = cell - firstCellOn(pager.currentPage)
            val cx = (local % GRID_COLUMNS + 0.5f) * cellWidthPx
            val cy = (local / GRID_COLUMNS + 0.5f) * cellHeightPx
            return abs(at.x - cx) < cellWidthPx * FOLDER_ZONE && abs(at.y - cy) < cellHeightPx * FOLDER_ZONE
        }

        // The cell that could become, or fill, a folder: an app over another app, or over a folder with
        // room. A folder is never carried into anything - they do not nest.
        val occupant = hovering?.let { cells.getOrNull(it) }.orEmpty()
        // The cell whose apps have already slid out of the way for the carried one. Like Launcher3, once
        // a reorder has happened the app under the finger is no longer really there, so that cell can no
        // longer become a folder until the finger moves on to another cell.
        var previewTo by remember { mutableStateOf<Int?>(null) }
        val canMerge = carriedNow != null && occupant.isNotEmpty() && occupant != carriedNow &&
            hovering != previewTo &&
            !GridFolders.isFolderId(carriedNow) &&
            (!GridFolders.isFolderId(occupant) ||
                folderById[occupant]?.let {
                    carriedNow !in it.apps && it.apps.size < GridFolders.MAX_APPS
                } == true)
        val mergeCell: Int? =
            if (canMerge && hovering != null && pointer != null && nearCentre(hovering, pointer)) hovering
            else null
        // Held there long enough to count. Short of that, passing over an app on the way to somewhere
        // else would offer to put the carried one in it.
        LaunchedEffect(mergeCell) {
            if (mergeCell == null) {
                folderTarget = null
            } else {
                delay(FOLDER_DWELL_MS)
                folderTarget = mergeCell
                haptics.pageSnap()
            }
        }

        // While a folder is being offered, nothing makes room: the app is going in, not between.
        val previewHover = if (mergeCell != null) null else hovering
        // The hover only counts once it has stayed on one cell for a moment. Without that, a finger
        // passing over four apps on its way somewhere would shove all four aside in turn. Launcher3
        // waits too. Moving to another cell keeps the last preview until the new one has settled, so
        // the grid does not flicker back and forth in between.
        LaunchedEffect(previewHover) {
            if (previewHover == null) {
                previewTo = null
            } else {
                delay(REORDER_DWELL_MS)
                previewTo = previewHover
            }
        }
        // Which apps would move if the carried one were dropped now, and into which cell. It is the
        // drop's own rule ([GridOrder.place] / [GridOrder.insert]), not a second one, so what slides
        // away is exactly what will be there when the finger lifts.
        val carriedId = held?.id ?: ext?.id
        val carriedFrom = held?.from
        val moves: Map<String, Int> = remember(
            cells, carriedId, carriedFrom, previewTo, pager.currentPage, perPage
        ) {
            val to = previewTo
            val id = carriedId
            val first = firstCellOn(pager.currentPage)
            val range = first..(first + perPage - 1)
            if (to == null || id == null || to !in range) {
                emptyMap()
            } else if (carriedFrom != null) {
                GridOrder.moves(cells, GridOrder.place(cells, carriedFrom, to, range), id)
            } else {
                GridOrder.moves(cells, GridOrder.insert(cells, id, to, range), id)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // A hold picks an app up; a plain drag still turns the page, because this only
                    // begins after the press has been held.
                    .pointerInput(cells, perPage, cellWidthPx, cellHeightPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                expandedPageCount = 0
                                // An open folder sits over the grid and has its own holds: whatever is
                                // under it must not be picked up through it.
                                val cell = if (openFolderId != null) null
                                else cellAt(pager.currentPage, start.x, start.y)
                                val picked = cell?.takeIf { cells.getOrNull(it)?.isNotEmpty() == true }
                                if (picked != null) {
                                    // The moment the app comes off the grid. Launcher3 fires a LONG_PRESS
                                    // haptic when a drag begins, and it is the one thing that tells the
                                    // hand an app is being carried rather than the page being scrolled -
                                    // without it the lift is only something you see.
                                    haptics.press()
                                }
                                held = picked?.let {
                                    HeldApp(cells[it], it, start.x, start.y, start.x, start.y)
                                }
                                // Nothing is shown yet. The app comes off the grid, and if it is put
                                // back down without being carried anywhere, its menu opens then.
                                //
                                // Showing it now would be showing a menu to a hand that is still
                                // holding the app: there is no finger left to press either of its
                                // buttons with, and letting go would take it away again before it
                                // could be read. It appears on release, which is when a hand is free.
                                menuAt = null
                            },
                            onDrag = { change, _ ->
                                val current = held
                                if (current != null) {
                                    change.consume()
                                    val x = change.position.x
                                    val y = change.position.y
                                    // The app is a cell wide and sits under the finger, so its
                                    // leading edge is half a cell ahead of the finger. The page is
                                    // armed when that edge crosses the screen's edge, which is the
                                    // moment some of the app is over the next page and not a moment
                                    // before. Any slack added on top of this made it fire while the
                                    // app was still short of the edge, which is not what the gesture
                                    // means and not what it looks like it should do.
                                    val reach = cellWidthPx / 2f
                                    val width = size.width.toFloat()
                                    val turn = when {
                                        // Reaching the edge of the last page creates a temporary next
                                        // page after the dwell, just like a launcher workspace.
                                        x + reach > width -> 1
                                        x - reach < 0f && pager.currentPage > 0 -> -1
                                        else -> 0
                                    }
                                    if (turn == 0) {
                                        // Off the edge again: cancel a waiting dwell, but never cancel
                                        // an animation after it has committed; that leaves a half-page.
                                        if (!pageTurnInProgress) {
                                            turning?.cancel()
                                            turning = null
                                        }
                                    } else if (turning == null) {
                                        // Keep one edge-turn coroutine for the whole carry. It uses the
                                        // latest finger position, creates a page only after the dwell,
                                        // and lets the pager finish each animation before starting the
                                        // next one. The old implementation could race the pager's own
                                        // horizontal gesture and leave a half-page offset behind.
                                        turning = scope.launch {
                                            try {
                                                while (true) {
                                                    delay(EDGE_DWELL_MS)
                                                    val active = held ?: break
                                                    val direction = when {
                                                        active.x + reach > width -> 1
                                                        active.x - reach < 0f && pager.currentPage > 0 -> -1
                                                        else -> 0
                                                    }
                                                    if (direction == 0) break

                                                    pageTurnInProgress = true
                                                    try {
                                                        if (direction > 0 && pager.currentPage >= livePageCount.value - 1) {
                                                            expandedPageCount = maxOf(expandedPageCount, pager.currentPage + 2)
                                                            val hasDestination = withTimeoutOrNull(1000L) {
                                                                snapshotFlow { livePageCount.value }
                                                                    .first { it > pager.currentPage }
                                                            }
                                                            if (hasDestination == null) {
                                                                expandedPageCount = 0
                                                                break
                                                            }
                                                        }
                                                        val target = (pager.currentPage + direction)
                                                            .coerceIn(0, livePageCount.value - 1)
                                                        if (target == pager.currentPage) break
                                                        // User paging stays disabled until this animation has settled.
                                                        pager.animateScrollToPage(target)
                                                    } finally {
                                                        pageTurnInProgress = false
                                                    }
                                                    if (held == null) break
                                                }
                                            } finally {
                                                turning = null
                                            }
                                        }
                                    }
                                    held = current.copy(x = x, y = y)
                                    // Carried far enough to be a drag rather than a hold, so it will
                                    // not be offered a menu when it is put down.
                                    val moveSlop = with(density) { MENU_MOVE.toPx() }
                                    if (kotlin.math.abs(x - current.startX) > moveSlop ||
                                        kotlin.math.abs(y - current.startY) > moveSlop
                                    ) {
                                        carried = true
                                    }
                                }
                            },
                            onDragEnd = {
                                val current = held
                                // Taken now: letting go clears it, and a page turn can still be settling.
                                val armed = folderTarget
                                held = null
                                if (pageTurnInProgress) {
                                    // Do not interrupt animateScrollToPage on lift. Waiting here
                                    // avoids saving the drop against a half-settled currentPage.
                                    scope.launch {
                                        snapshotFlow { pageTurnInProgress }.first { !it }
                                        finishHeldDrop(current, armed)
                                    }
                                } else {
                                    turning?.cancel()
                                    turning = null
                                    finishHeldDrop(current, armed)
                                }
                            },
                            onDragCancel = {
                                held = null
                                if (pageTurnInProgress) {
                                    // Let the committed animation settle, then return from a temporary
                                    // empty page if the gesture was cancelled instead of dropped.
                                    scope.launch {
                                        snapshotFlow { pageTurnInProgress }.first { !it }
                                        pageTurnInProgress = true
                                        try {
                                            val lastStoredPage = GridOrder.pageCount(cells.size, perPage) - 1
                                            if (pager.currentPage > lastStoredPage) {
                                                pager.animateScrollToPage(lastStoredPage)
                                            }
                                        } finally {
                                            expandedPageCount = 0
                                            pageTurnInProgress = false
                                        }
                                    }
                                } else {
                                    turning?.cancel()
                                    turning = null
                                    expandedPageCount = 0
                                }
                                carried = false
                                menuFor = null
                                menuAt = null
                            }
                        )
                    }
            ) {
                HorizontalPager(
                    state = pager,
                    // Auto-page turns handle an icon carry. Letting the pager's own horizontal
                    // drag recognizer run at the same time caused occasional partial-page stalls.
                    userScrollEnabled = held == null && activeDrag == null && !pageTurnInProgress,
                    modifier = Modifier
                        .fillMaxSize()
                        // The page draws back a little while an app is being carried, the way a
                        // phone's home screen shrinks when you pick something up. It says the grid is
                        // no longer the thing being touched, and gives the app room to be seen over
                        // it. Nothing else moves: the finger is still over the same cell.
                        .graphicsLayer {
                            val drawn = if (held != null || activeDrag != null) DRAG_PAGE_SHRINK else 1f
                            scaleX = drawn
                            scaleY = drawn
                        }
                ) { page ->
                    val slice = sliceOf(page)
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (row in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (column in 0 until GRID_COLUMNS) {
                                    val id = slice.getOrNull(row * GRID_COLUMNS + column)
                                    val app = id?.let { byId[it] }
                                    val folder = id?.let { folderById[it] }
                                    // An app or a folder: whatever is in the cell, and moves like one.
                                    val itemId = app?.id ?: folder?.id
                                    Box(
                                        modifier = Modifier
                                            .width(cellWidth)
                                            .height(rowHeight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // A cell with nothing in it is a cell with nothing in it. An
                                        // app being carried leaves its cell empty, so the place it
                                        // came from is visibly free while it is in the air.
                                        if (itemId != null && held?.id != itemId) {
                                            // Keyed on the item so that when a drop rearranges the cells,
                                            // each one starts at rest in its new cell instead of carrying
                                            // the old cell's slide with it.
                                            key(itemId) {
                                                // Where this app is being pushed to, in cells from where it
                                                // sits. Only ever within this page; an app is never slid
                                                // off the edge of the page the user is looking at.
                                                val slot = moves[itemId]
                                                    ?.minus(firstCellOn(page))
                                                    ?.takeIf { it in 0 until perPage }
                                                ShiftAway(
                                                    dx = if (slot != null) {
                                                        ((slot % GRID_COLUMNS) - column) * cellWidthPx
                                                    } else 0f,
                                                    dy = if (slot != null) {
                                                        ((slot / GRID_COLUMNS) - row) * cellHeightPx
                                                    } else 0f,
                                                    reducedMotion = reducedMotion
                                                ) {
                                                    if (app != null) {
                                                        AppCell(
                                                            app = app,
                                                            iconLoader = iconLoader,
                                                            onLaunch = { onLaunch(app) }
                                                        )
                                                    } else if (folder != null) {
                                                        FolderCell(
                                                            folder = folder,
                                                            apps = folder.apps.mapNotNull { byId[it] },
                                                            iconLoader = iconLoader,
                                                            armed = folderTarget ==
                                                                firstCellOn(page) + row * GRID_COLUMNS + column,
                                                            onOpen = { openFolderId = folder.id }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // The cell it will land in.
                val landing = held?.let { cellAt(pager.currentPage, it.x, it.y) }
                    ?: ext?.let { cellAt(pager.currentPage, it.x - origin.x, it.y - origin.y) }
                if (landing != null) {
                    val page = pager.currentPage
                    val local = landing - firstCellOn(page)
                    if (local in 0 until perPage) {
                        // Once a folder is on offer the cell is lit more strongly and rounder, so the
                        // difference between "goes beside" and "goes in" can be seen.
                        val folderOffered = folderTarget == landing
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = cellWidth * (local % GRID_COLUMNS),
                                    y = rowHeight * (local / GRID_COLUMNS)
                                )
                                .size(cellWidth, rowHeight)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(if (folderOffered) 30.dp else 18.dp))
                                // Neutral frosted white: it marks the spot without taking a colour of
                                // its own, so it sits well on any wallpaper.
                                .background(Color.White.copy(alpha = if (folderOffered) 0.30f else 0.18f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = if (folderOffered) 0.55f else 0.32f),
                                    RoundedCornerShape(if (folderOffered) 30.dp else 18.dp)
                                )
                        )
                    }
                }

                // Where an app is taken to be got rid of. It is only here while something is being
                // carried, so it never sits over the grid asking to be pressed.
                val showingTarget = held != null
                if (showingTarget) {
                    val over = held?.let { targetAt(it.x, it.y) }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = TARGET_MARGIN, vertical = TARGET_TOP),
                        horizontalArrangement = Arrangement.spacedBy(TARGET_GAP)
                    ) {
                        DropTarget(
                            armed = over == DropAction.REMOVE,
                            label = stringResource(R.string.app_remove),
                            icon = Icons.Filled.Close,
                            destructive = false,
                            modifier = Modifier.weight(1f)
                        )
                        DropTarget(
                            armed = over == DropAction.UNINSTALL,
                            label = stringResource(R.string.app_uninstall),
                            icon = Icons.Filled.Delete,
                            destructive = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // The app's menu, beside it. Small, and only while the app is being held and has
                // not been moved - the same shape a phone shows when you press and hold.
                val menuApp = menuFor?.let { byId[it] }
                val menuWhere = menuAt
                if (menuApp != null && menuWhere != null) {
                    // Anywhere else at all closes it, and takes the touch itself so whatever is
                    // underneath is not also opened by the tap that was meant to dismiss.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { menuBounds = it }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                menuFor = null
                                menuAt = null
                            }
                    )
                    val placement = menuPlacement(
                        wanted = menuWhere,
                        menu = menuSize,
                        bounds = menuBounds,
                        cellWidth = cellWidthPx,
                        cellHeight = cellHeightPx,
                        gap = with(density) { MENU_GAP.toPx() },
                        margin = with(density) { MENU_MARGIN.toPx() }
                    )
                    AppMenu(
                        app = menuApp,
                        fromTop = placement.below,
                        originX = placement.originX,
                        ready = menuSize != IntSize.Zero && menuBounds != IntSize.Zero,
                        onAppInfo = {
                            AppActions.openAppInfo(context, menuApp.packageName)
                            menuFor = null
                            menuAt = null
                            held = null
                        },
                        onRemove = {
                            removeFromHome(menuApp.id)
                            menuFor = null
                            menuAt = null
                            held = null
                        },
                        onUninstall = {
                            AppActions.uninstall(context, menuApp.packageName)
                            menuFor = null
                            menuAt = null
                            held = null
                        },
                        modifier = Modifier
                            .onGloballyPositioned { menuSize = it.size }
                            .offset { placement.offset }
                    )
                }

                // The app itself, under the finger.
                val carried = held
                val carriedApp = carried?.let { byId[it.id] }
                val carriedFolder = carried?.let { folderById[it.id] }
                if (carried != null && (carriedApp != null || carriedFolder != null)) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (carried.x - cellWidthPx / 2f).roundToInt(),
                                    (carried.y - cellHeightPx / 2f).roundToInt()
                                )
                            }
                            .width(cellWidth)
                            .height(rowHeight)
                            .graphicsLayer {
                                scaleX = 1.08f
                                scaleY = 1.08f
                                alpha = 0.92f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (carriedApp != null) {
                            AppCell(app = carriedApp, iconLoader = iconLoader, onLaunch = {})
                        } else if (carriedFolder != null) {
                            FolderCell(
                                folder = carriedFolder,
                                apps = carriedFolder.apps.mapNotNull { byId[it] },
                                iconLoader = iconLoader,
                                armed = false,
                                onOpen = {}
                            )
                        }
                    }
                }

                // An app being carried out of an open folder, under the finger. The drawer's carried app
                // is drawn by the home screen; this one is ours.
                val outOfFolder = folderDrag
                val outOfFolderApp = outOfFolder?.let { byId[it.id] }
                if (outOfFolder != null && outOfFolderApp != null && !outOfFolder.released) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (outOfFolder.x - origin.x - cellWidthPx / 2f).roundToInt(),
                                    (outOfFolder.y - origin.y - cellHeightPx / 2f).roundToInt()
                                )
                            }
                            .width(cellWidth)
                            .height(rowHeight)
                            .graphicsLayer {
                                scaleX = 1.08f
                                scaleY = 1.08f
                                alpha = 0.92f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AppCell(app = outOfFolderApp, iconLoader = iconLoader, onLaunch = {})
                    }
                }

                // The open folder. Above everything else in the grid, including the app menu.
                val openFolder = openFolderId?.let { folderById[it] }
                if (openFolder != null) {
                    FolderPopup(
                        folder = openFolder,
                        apps = openFolder.apps.mapNotNull { byId[it] },
                        iconLoader = iconLoader,
                        // Out of sight while one of its apps is being carried away, but still there:
                        // the touch belongs to an icon inside it.
                        hidden = folderDrag != null,
                        onLaunch = { app ->
                            openFolderId = null
                            onLaunch(app)
                        },
                        onRename = { name ->
                            commit(
                                homeIds,
                                cells,
                                liveFolders.map { if (it.id == openFolder.id) it.copy(name = name) else it }
                            )
                        },
                        onDismiss = { openFolderId = null },
                        dragFor = { app ->
                            AppDragCallbacks(
                                onStart = { at -> folderDrag = ExternalAppDrag(app.id, at.x, at.y) },
                                onMove = { at -> folderDrag = folderDrag?.copy(x = at.x, y = at.y) },
                                onEnd = { at ->
                                    folderDrag = folderDrag?.copy(x = at.x, y = at.y, released = true)
                                },
                                onCancel = { folderDrag = null },
                                // Held and let go in place: nothing to do, the folder stays open.
                                onHold = {}
                            )
                        }
                    )
                }
            }

            // Where you are, and how much there is left. Only shown when there is more than one page,
            // because a single dot is not information - and only when the strip for it was taken, so
            // that the dots can never be drawn over the last row.
            if (dotsExpected && pageCount > 1) {
                // The dots say where you are in colour alone, which a screen reader cannot see. This
                // is the same thing in words.
                val pageLabel = stringResource(R.string.grid_page_of, pager.currentPage + 1, pageCount)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PAGE_DOTS_HEIGHT)
                        .semantics { contentDescription = pageLabel },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pager.currentPage) {
                                        OrbitColors.TextPrimary
                                    } else {
                                        OrbitColors.TextSecondary.copy(alpha = 0.4f)
                                    }
                                )
                        )
                    }
                }
            }
        }

        // Nothing on the home screen at all: say so, and say how to put something on it. A grid of
        // empty cells with no word about it reads as a grid that failed rather than one waiting to
        // be filled.
        if (cells.isEmpty()) {
            AppEmptyState(
                icon = Icons.Filled.Add,
                title = stringResource(R.string.grid_empty_title),
                body = stringResource(R.string.grid_empty_body),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * [content], slid by ([dx], [dy]) pixels from where its cell is, and eased there.
 *
 * It is a draw-time translation and nothing else, so the cell keeps its place in the layout and
 * nothing is re-measured while apps slide. A spring rather than a fixed-time tween, so a target that
 * changes mid-slide (the finger moving on to the next cell) redirects the app smoothly instead of
 * restarting the animation.
 */
@Composable
private fun ShiftAway(
    dx: Float,
    dy: Float,
    reducedMotion: Boolean,
    content: @Composable () -> Unit
) {
    val spec: FiniteAnimationSpec<Float> = if (reducedMotion) {
        snap()
    } else {
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    val x by animateFloatAsState(dx, spec, label = "shiftX")
    val y by animateFloatAsState(dy, spec, label = "shiftY")
    Box(
        modifier = Modifier.graphicsLayer {
            translationX = x
            translationY = y
        }
    ) {
        content()
    }
}

/**
 * An app being carried in from the drawer: which one, where the finger is (root coordinates), and
 * whether it has been let go.
 */
data class ExternalAppDrag(
    val id: String,
    val x: Float,
    val y: Float,
    val released: Boolean = false
)

/** An app in the air: which one, which cell it came from, where the finger is, where it would land. */
private data class HeldApp(
    val id: String,
    val from: Int,
    val x: Float,
    val y: Float,
    /** Where the finger was when the app came off the grid, so a real drag can be told from a hold. */
    val startX: Float,
    val startY: Float
)

private const val GRID_COLUMNS = 4

/**
 * How much the page draws back while an app is being carried.
 *
 * Small on purpose: this is meant to be felt as much as seen, and a grid that visibly jumps is worse
 * than one that does not move at all.
 */
private const val DRAG_PAGE_SHRINK = 0.97f

/**
 * How long a carried app has to rest on a cell before the apps there make room for it.
 *
 * Deliberately longer than [FOLDER_DWELL_MS], as Launcher3's reorder timeout is longer than its folder
 * one: a finger settling onto an app must get the chance to offer a folder BEFORE the app slides away
 * from under it. If the apps moved first, a folder could never be reached.
 */
private const val REORDER_DWELL_MS = 650L

/**
 * A new folder has no name of its own: it is shown as "Folder" in whatever language the phone is in,
 * and keeps doing so until the user types one.
 */
private const val NEW_FOLDER_NAME = ""

/**
 * How long a carried app has to rest over the middle of another before dropping it makes a folder.
 *
 * Shorter than [REORDER_DWELL_MS] on purpose: the folder offer has to appear while the other app is
 * still under the finger, before the reorder preview slides it away.
 */
private const val FOLDER_DWELL_MS = 350L

/**
 * How much of a cell, either side of its middle, counts as "over the app" for making a folder. The
 * rest of the cell is for reordering. A quarter-cell either way is about the icon itself.
 */
private const val FOLDER_ZONE = 0.28f

/** How far the finger has to move before a hold becomes a drag, and the menu goes away. */
private val MENU_MOVE = 8.dp

/** How close to the top of the grid an app has to be put for the target to take it. */
private val TARGET_TOP = 12.dp

/**
 * How tall the target chips are (12 dp padding + 20 dp icon + 12 dp padding, rounded up), which is what
 * the drop is tested against. It was 96 dp, as deep as a whole row, so the first row could never receive
 * a drop.
 */
private val TARGET_HEIGHT = 48.dp

/** The chips' horizontal inset and the space between them. Must match the `Row` that draws them. */
private val TARGET_MARGIN = 12.dp
private val TARGET_GAP = 10.dp

/** The space between an app's cell and its menu, and the least the menu keeps from the screen's edges. */
private val MENU_GAP = 4.dp
private val MENU_MARGIN = 16.dp

/** The two things an app can be carried to the top to have done to it. */
internal enum class DropAction { REMOVE, UNINSTALL }

/**
 * Which drop target a point is over, or null.
 *
 * Mirrors the chips' layout: inset by [marginPx] on both sides, split at the middle with a [gapPx] dead
 * zone between them. Above the top of the grid (negative y) or below [bottomPx] is over neither.
 */
internal fun dropActionAt(
    x: Float,
    y: Float,
    widthPx: Float,
    bottomPx: Float,
    marginPx: Float,
    gapPx: Float
): DropAction? {
    if (y < 0f || y >= bottomPx) return null
    if (x < marginPx || x > widthPx - marginPx) return null
    val mid = widthPx / 2f
    return when {
        x < mid - gapPx / 2f -> DropAction.REMOVE
        x > mid + gapPx / 2f -> DropAction.UNINSTALL
        else -> null
    }
}

/** One row of apps: icon, label, and the space between them. */
private val CELL_HEIGHT = 96.dp

/** The strip the page dots sit in. */
private val PAGE_DOTS_HEIGHT = 28.dp

/**
 * How long the app has to be held over the edge before the page turns.
 *
 * Launcher3 waits half a second, and it is the whole reason a drop on the last column works: the page
 * turn is armed when the app reaches the edge and fires only if it is still there when the time is
 * up, so letting go in the meantime drops the app where it was put.
 */
private const val EDGE_DWELL_MS = 500L

/** Where the app menu goes, and which way it grows out of the app. */
private class MenuPlacement(val offset: IntOffset, val below: Boolean, val originX: Float)

/**
 * Where the app menu goes, kept inside the space it is drawn in.
 *
 * The menu sits just below the cell of the app it is about, never on top of it, so the icon stays in
 * view. If there is no room below (the bottom row) it goes above instead. Sideways it is centred on the
 * app and then nudged back inside the screen, keeping [margin] clear of the edges.
 */
private fun menuPlacement(
    wanted: Offset,
    menu: IntSize,
    bounds: IntSize,
    cellWidth: Float,
    cellHeight: Float,
    gap: Float,
    margin: Float
): MenuPlacement {
    val column = (wanted.x / cellWidth).toInt().coerceAtLeast(0)
    val row = (wanted.y / cellHeight).toInt().coerceAtLeast(0)
    val cellCentre = (column + 0.5f) * cellWidth
    val cellTop = row * cellHeight
    val cellBottom = cellTop + cellHeight

    val maxX = (bounds.width - menu.width - margin).coerceAtLeast(margin)
    val x = (cellCentre - menu.width / 2f).coerceIn(margin, maxX)

    val fitsBelow = cellBottom + gap + menu.height <= bounds.height - margin
    val y = if (fitsBelow) cellBottom + gap else (cellTop - gap - menu.height).coerceAtLeast(margin)

    val originX = if (menu.width > 0) ((cellCentre - x) / menu.width).coerceIn(0.1f, 0.9f) else 0.5f
    return MenuPlacement(IntOffset(x.roundToInt(), y.roundToInt()), fitsBelow, originX)
}
