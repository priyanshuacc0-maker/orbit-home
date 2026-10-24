package com.orbit.home.ui

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
import com.orbit.home.R
import com.orbit.home.apps.AppActions
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.AppLabels
import com.orbit.home.apps.InstalledApp
import com.orbit.home.ui.theme.OrbitColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
     * Called once with the whole layout - which apps this screen holds, and where they are.
     *
     * One callback and not two: membership and arrangement are one thing, and handing them over
     * separately is what allowed a half-applied screen to be written down.
     */
    onSaveLayout: (List<String>, List<String>) -> Unit,
    order: List<String>,
    iconLoader: AppIconLoader,
    onLaunch: (InstalledApp) -> Unit,
    /**
     * An app being carried in from the drawer, or null. Its position is in root coordinates. While it
     * is set the grid shows where it would land and turns pages when it is held at an edge; when
     * [ExternalAppDrag.released] is set the app is dropped there and [onExternalDragDone] is called.
     */
    externalDrag: ExternalAppDrag? = null,
    onExternalDragDone: () -> Unit = {},
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
    LaunchedEffect(order, dropped) {
        if (dropped != null && order == dropped) dropped = null
    }
    // If the write fails, the stored order never matches. Fall back to what is really stored rather than
    // showing an arrangement that will vanish on restart.
    LaunchedEffect(dropped) {
        if (dropped != null) {
            delay(3000)
            dropped = null
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
        val rows = ((maxHeight - PAGE_DOTS_HEIGHT) / CELL_HEIGHT).toInt().coerceAtLeast(1)
        // Which apps this screen holds, and where. Declared here because the page count below is
        // worked out from them, and the seeding below needs the row count this line just produced.
        val homeIds = if (homeInitialized) homeApps else (seeded ?: emptyList())


        val cells = remember(sorted, order, dropped, homeIds) {
            GridOrder.cells(homeIds, dropped ?: order)
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
            onSaveLayout(
                homeIds.filterNot { it == id },
                cells.map { if (it == id) GridOrder.EMPTY else it }
            )
        }
        val perPage = (GRID_COLUMNS * rows).coerceAtLeast(1)
        val storedPageCount = GridOrder.pageCount(cells.size, perPage)
        val pageCount = maxOf(storedPageCount, expandedPageCount)
        // Gesture coroutines must see the latest count without being restarted when a new
        // temporary page is added. Restarting pointerInput mid-drag cancels the carried item.
        val livePageCount = rememberUpdatedState(pageCount)
        val liveExternalDrag = rememberUpdatedState(externalDrag)
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
                onSaveLayout(start.filter { it.isNotEmpty() }, start)
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

        /** Finish the drop only after any committed page turn has reached a real page. */
        fun finishHeldDrop(current: HeldApp?) {
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

            if (current != null && !carried) {
                expandedPageCount = 0
                menuFor = current.id
                menuAt = Offset(current.x, current.y)
                carried = false
                return
            }

            carried = false
            val landed = current?.let { cellAt(pager.currentPage, it.x, it.y) }
            if (current != null && landed != null && current.from != landed) {
                val page = pager.currentPage
                val placed = GridOrder.place(
                    cells,
                    current.from,
                    landed,
                    firstCellOn(page)..(firstCellOn(page) + perPage - 1)
                )
                // Show the new position at once. Because the destination may be on a newly
                // created page, keep this list until persistence catches up.
                dropped = placed
                onSaveLayout(homeIds, placed)
            }
            expandedPageCount = 0
        }

        // ---- An app carried in from the drawer -------------------------------------------------
        val ext = externalDrag
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
            val finalX = e.x - origin.x
            val finalY = e.y - origin.y
            val to = cellAt(pager.currentPage, finalX, finalY)
            if (to != null) {
                val first = firstCellOn(pager.currentPage)
                val placed = GridOrder.insert(cells, e.id, to, first..(first + perPage - 1))
                // The carried app has landed: a completed action, softer than the pick-up.
                haptics.confirm()
                dropped = placed
                onSaveLayout(if (e.id in homeIds) homeIds else homeIds + e.id, placed)
            }
            expandedPageCount = 0
            onExternalDragDone()
        }

        // ---- Getting out of the way ---------------------------------------------------------------
        // The cell a carried app is over, or null if it is over none, or over a drop target at the top.
        // Held and drawer-carried apps are treated the same: both are about to be dropped on a cell.
        val hoverHeld = held
        val hovering: Int? = when {
            hoverHeld != null ->
                if (targetAt(hoverHeld.x, hoverHeld.y) != null) null
                else cellAt(pager.currentPage, hoverHeld.x, hoverHeld.y)
            ext != null -> cellAt(pager.currentPage, ext.x - origin.x, ext.y - origin.y)
            else -> null
        }
        // The hover only counts once it has stayed on one cell for a moment. Without that, a finger
        // passing over four apps on its way somewhere would shove all four aside in turn. Launcher3
        // waits too. Moving to another cell keeps the last preview until the new one has settled, so
        // the grid does not flicker back and forth in between.
        var previewTo by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(hovering) {
            if (hovering == null) {
                previewTo = null
            } else {
                delay(PREVIEW_DWELL_MS)
                previewTo = hovering
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
                                val cell = cellAt(pager.currentPage, start.x, start.y)
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
                                held = null
                                if (pageTurnInProgress) {
                                    // Do not interrupt animateScrollToPage on lift. Waiting here
                                    // avoids saving the drop against a half-settled currentPage.
                                    scope.launch {
                                        snapshotFlow { pageTurnInProgress }.first { !it }
                                        finishHeldDrop(current)
                                    }
                                } else {
                                    turning?.cancel()
                                    turning = null
                                    finishHeldDrop(current)
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
                    userScrollEnabled = held == null && externalDrag == null && !pageTurnInProgress,
                    modifier = Modifier
                        .fillMaxSize()
                        // The page draws back a little while an app is being carried, the way a
                        // phone's home screen shrinks when you pick something up. It says the grid is
                        // no longer the thing being touched, and gives the app room to be seen over
                        // it. Nothing else moves: the finger is still over the same cell.
                        .graphicsLayer {
                            val drawn = if (held != null || externalDrag != null) DRAG_PAGE_SHRINK else 1f
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
                                    Box(
                                        modifier = Modifier
                                            .width(cellWidth)
                                            .height(rowHeight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // A cell with nothing in it is a cell with nothing in it. An
                                        // app being carried leaves its cell empty, so the place it
                                        // came from is visibly free while it is in the air.
                                        if (app != null && held?.id != app.id) {
                                            // Keyed on the app so that when a drop rearranges the cells,
                                            // each app starts at rest in its new cell instead of carrying
                                            // the old cell's slide with it.
                                            key(app.id) {
                                                // Where this app is being pushed to, in cells from where it
                                                // sits. Only ever within this page; an app is never slid
                                                // off the edge of the page the user is looking at.
                                                val slot = moves[app.id]
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
                                                    AppCell(
                                                        app = app,
                                                        iconLoader = iconLoader,
                                                        onLaunch = { onLaunch(app) }
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

                // The cell it will land in.
                val landing = held?.let { cellAt(pager.currentPage, it.x, it.y) }
                    ?: ext?.let { cellAt(pager.currentPage, it.x - origin.x, it.y - origin.y) }
                if (landing != null) {
                    val page = pager.currentPage
                    val local = landing - firstCellOn(page)
                    if (local in 0 until perPage) {
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = cellWidth * (local % GRID_COLUMNS),
                                    y = rowHeight * (local / GRID_COLUMNS)
                                )
                                .size(cellWidth, rowHeight)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(OrbitColors.Accent.copy(alpha = 0.22f))
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
                    AppMenu(
                        app = menuApp,
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
                            .offset {
                                menuPosition(
                                    wanted = menuWhere,
                                    menu = menuSize,
                                    bounds = menuBounds,
                                    cellWidth = cellWidthPx
                                )
                            }
                    )
                }

                // The app itself, under the finger.
                val carried = held
                val carriedApp = carried?.let { byId[it.id] }
                if (carried != null && carriedApp != null) {
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
                        AppCell(app = carriedApp, iconLoader = iconLoader, onLaunch = {})
                    }
                }
            }

            // Where you are, and how much there is left. Only shown when there is more than one page,
            // because a single dot is not information.
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PAGE_DOTS_HEIGHT),
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
 * Short enough to feel like it is following the finger, long enough that sweeping across the grid
 * does not make every app it passes jump aside.
 */
private const val PREVIEW_DWELL_MS = 140L

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

/** How far above the app the menu sits. */
private val MENU_LIFT = 8.dp

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

/**
 * Where the app menu goes, kept inside the space it is drawn in.
 *
 * The menu belongs beside the app it is about, so it starts where that app is. But an app in the last
 * column or the bottom row would put it half off the screen, and half a menu is a menu whose rows you
 * cannot press. It is nudged back inside instead - towards the middle, never off the other edge, and
 * never smaller than the space it has.
 */
private fun menuPosition(
    wanted: Offset,
    menu: IntSize,
    bounds: IntSize,
    cellWidth: Float
): IntOffset {
    val x = (wanted.x - cellWidth / 2f).roundToInt().coerceIn(0, (bounds.width - menu.width).coerceAtLeast(0))
    val y = wanted.y.roundToInt().coerceIn(0, (bounds.height - menu.height).coerceAtLeast(0))
    return IntOffset(x, y)
}
