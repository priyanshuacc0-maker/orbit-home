package com.orbit.home.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import com.orbit.home.apps.AppActions
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.AppLabels
import com.orbit.home.apps.AppSearch
import com.orbit.home.apps.InstalledApp
import com.orbit.home.ui.theme.OrbitColors

/**
 * The narrowest a grid column may be. Adaptive, so a phone gets four icons across and a wider screen
 * gets five or more, the way Quickstep scales its grid, rather than four stretched ever further apart.
 */
private val GRID_MIN_CELL = 80.dp

/** Icon sizes in the drawer: a touch larger than the home grid's, so the drawer reads as the roomy one. */
private val GRID_ICON = 58.dp
private val ROW_ICON = 48.dp

/**
 * The all-apps drawer: every installed app, searchable, as a list or a grid.
 *
 * A small button beside the search field flips between the two. It shows the mode
 * you would switch *to*, which is the usual convention for a toggle of this kind.
 */
@Composable
fun AllAppsOverlay(
    allApps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    onLaunch: (InstalledApp) -> Unit,
    onClose: () -> Unit,
    grid: Boolean,
    onToggleGrid: () -> Unit,
    /**
     * Puts an app on the home screen, or null when this drawer is not the home screen's own.
     *
     * Null by default and left null by Settings, which uses this same list for a different purpose:
     * choosing what a wheel or the dock holds. An Add to home there would mean nothing.
     */
    onAddToHome: ((InstalledApp) -> Unit)? = null,
    /** The apps the home screen already holds, so an app that is on it is not offered again. */
    homeAppIds: Set<String> = emptySet(),
    /**
     * When set, a downward pull that the list cannot use is handed to the host with the raw
     * distance in px, so the host can move the whole drawer with the finger and let it settle
     * on a spring. Null keeps the drawer's own pull-and-close behaviour (used by Settings).
     */
    onDismissDrag: ((pullPx: Float) -> Unit)? = null,
    /** Finger lifted after a dismiss pull: distance in px and downward velocity in px/s. */
    onDismissRelease: ((pullPx: Float, velocityY: Float) -> Unit)? = null,
    /**
     * How far up the drawer is, 0 to 1, read inside a graphics layer so it costs no recomposition.
     * The contents settle in a beat behind the panel: they fade up and rise a little as it arrives,
     * and ease away as it leaves. Left at 1 (Settings) the contents simply sit there.
     */
    progress: () -> Float = { 1f },
    /**
     * Drag an app out of the drawer onto the home screen, the way Quickstep does. Null where dragging
     * out makes no sense (Settings, or a home that is a wheel). Positions are in root coordinates.
     * A hold that is not carried anywhere still opens the app's menu.
     */
    onAppDragStart: ((InstalledApp, Offset) -> Unit)? = null,
    onAppDragMove: (Offset) -> Unit = {},
    onAppDragEnd: (Offset) -> Unit = {},
    onAppDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }

    // Alphabetical, using the locale-aware collator so accents and non-Latin
    // scripts order the way a reader expects.
    val ordered = remember(allApps) {
        allApps.sortedWith(compareBy(AppLabels.comparator()) { it.label })
    }
    // Ranked rather than a plain filter: an exact name beats a prefix, which beats a
    // word match, an initialism, a substring, and finally a package name.
    val filtered = remember(ordered, query) { AppSearch.filter(ordered, query) }

    // Section headers and the A-Z strip only make sense in alphabetical order. A search ranks by
    // relevance, so while one is typed the list is shown as ranked, with neither.
    val browsing = query.isBlank()
    val sections = remember(filtered, browsing) {
        if (browsing) DrawerIndex.sections(filtered) { it.label } else emptyList()
    }
    // Grouped by section so what is drawn and what the strip scrolls to are the same order.
    val shown = remember(filtered, sections, browsing) {
        if (browsing) sections.flatMap { it.items } else filtered
    }
    val showRail = browsing && sections.size in 2..DrawerIndex.MAX_RAIL_KEYS
    val railKeys = remember(sections) { sections.map { it.key } }
    val railPad = if (showRail) 20.dp else 0.dp
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val closeThreshold = with(density) { 64.dp.toPx() }
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnDismissDrag by rememberUpdatedState(onDismissDrag)
    val currentOnDismissRelease by rememberUpdatedState(onDismissRelease)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var pullDown by remember { mutableFloatStateOf(0f) }
    // True from the first move of a pull the host is following until its release is delivered. The release
    // is keyed on this and not on how far the pull got: a pull that was taken back up to nothing still has
    // to be released, or the host is left holding the drawer wherever the finger last was.
    var dragActive by remember { mutableStateOf(false) }
    // True while the list is flinging, so its momentum running into the top is not taken for a pull.
    var inFling by remember { mutableStateOf(false) }
    // Once a pull has closed the drawer, its offset is left where it is so the panel carries on down
    // from the finger instead of jumping back up while the close animation runs.
    var closing by remember { mutableStateOf(false) }
    val haptics = rememberOrbitHaptics()
    // The app whose menu is open, from a long press.
    var menuFor by remember { mutableStateOf<String?>(null) }
    // Shown with no lag while the finger is down, and sprung back when it lets go without closing.
    val pullShown by animateFloatAsState(
        targetValue = pullDown,
        animationSpec = if (pullDown > 0f) snap() else spring(stiffness = Spring.StiffnessMedium),
        label = "drawerPull"
    )

    // True once the list has used any of this gesture. A gesture that scrolled the
    // list is a scroll, not a dismiss, even after the list runs out of room.
    var scrolledThisGesture by remember { mutableStateOf(false) }

    // Moves a pull the host is following by [delta] px (down is positive). Never below nothing.
    fun pullBy(delta: Float) {
        val drag = currentOnDismissDrag ?: return
        if (!dragActive && delta <= 0f) return
        pullDown = (pullDown + delta).coerceAtLeast(0f)
        dragActive = true
        drag(pullDown)
    }
    // Delivers the release of a pull, once. Returns whether there was a pull to release.
    fun endPull(velocityY: Float): Boolean {
        val release = currentOnDismissRelease
        val was = dragActive
        dragActive = false
        if (was && release != null) {
            val pulled = pullDown
            pullDown = 0f
            release(pulled, velocityY)
        }
        return was && release != null
    }
    // The header and the empty state have no list under them, so a drag there is handled directly.
    val dismissDragState = rememberDraggableState { delta -> pullBy(delta) }
    val dismissDrag = if (onDismissDrag != null && onDismissRelease != null) {
        Modifier.draggable(
            orientation = Orientation.Vertical,
            state = dismissDragState,
            onDragStopped = { velocity -> endPull(velocity) }
        )
    } else Modifier

    // Downward movement the list could not use, from a gesture that was already at the
    // top when it began.
    val dismissConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // The finger coming back up gives the pull back first, the way a sheet does, so the
                // drawer follows the finger both ways instead of staying where it was dragged to.
                if (currentOnDismissDrag != null && dragActive && available.y < 0f) {
                    val take = maxOf(available.y, -pullDown)
                    if (take != 0f) {
                        pullBy(take)
                        return Offset(0f, take)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // The list used some of this gesture, so it is a scroll. Latching that
                // is what stops a scroll-to-the-top from carrying straight on into a
                // close the moment the list runs out of room — you scroll up, it stops
                // at the top, and only a further deliberate pull closes it.
                if (consumed.y != 0f) scrolledThisGesture = true
                if (available.y <= 0f) return Offset.Zero
                if (scrolledThisGesture) return Offset.Zero
                if (currentOnDismissDrag != null) {
                    // The host moves the drawer; this just reports how far the finger has gone.
                    // Momentum from a fling is not a pull.
                    if (inFling) return Offset.Zero
                    pullBy(available.y)
                    return Offset(0f, available.y)
                }
                pullDown += available.y
                if (pullDown > closeThreshold && !closing) {
                    closing = true
                    currentOnClose()
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentOnDismissRelease != null) {
                    scrolledThisGesture = false
                    // Whatever the finger did, a pull in progress ends here. The list is not also
                    // thrown by the same flick, so the velocity is taken.
                    if (endPull(available.y)) return Velocity(0f, available.y)
                    inFling = true
                    return Velocity.Zero
                }
                // A quick downward throw closes it too, as long as the list had not scrolled.
                val thrown = pullDown > 0f && !scrolledThisGesture && available.y > FLING_CLOSE_VELOCITY
                scrolledThisGesture = false
                if (thrown && !closing) {
                    closing = true
                    currentOnClose()
                }
                if (!closing) pullDown = 0f
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Belt and braces: some paths end without a pre-fling.
                inFling = false
                scrolledThisGesture = false
                if (!closing) pullDown = 0f
                return Velocity.Zero
            }
        }
    }

    // The drawer opens with no keyboard. It used to focus the field and raise the
    // keyboard on open, which covered half the list before the user had asked to
    // search. Tapping the field still focuses it and brings the keyboard up.
    // A new query restarts the list at the top, so the best match is on screen
    // instead of wherever the user had scrolled to.
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            // Only the layout that is actually composed has a state to scroll.
            if (grid) gridState.scrollToItem(0) else listState.scrollToItem(0)
        }
    }

    // Scrolling the list puts the keyboard away, the way every search list does, so it is not left
    // covering half of what is being scrolled.
    LaunchedEffect(grid) {
        snapshotFlow { if (grid) gridState.isScrollInProgress else listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) keyboard?.hide() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Nearly opaque: the drawer must be readable over any wallpaper.
            .background(OrbitColors.Background)
            // An overlay must never leak touches to the home screen behind it.
            .blockTouchesBehind()
            // A new touch starts from clean: a fling cancelled by it never reports its end, and a
            // scroll by wheel or keyboard never reports a fling at all, so either could otherwise
            // leave a latch set from before that stops the next pull from working.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    inFling = false
                    scrolledThisGesture = false
                    if (!dragActive) pullDown = 0f
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress().coerceIn(0f, 1f)
                    alpha = ((p - 0.3f) / 0.5f).coerceIn(0f, 1f)
                    translationY = (1f - p) * 28.dp.toPx()
                }
                .offset {
                    // When the host drives the drawer, it moves the whole panel; no extra offset.
                    if (onDismissDrag != null) IntOffset.Zero
                    else IntOffset(0, pullShown.roundToInt().coerceAtLeast(0))
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                // Nested scroll rather than a draggable. A draggable on the parent
                // takes the vertical gesture away from the list underneath, so the
                // list could not be scrolled and the drawer could not tell a dismiss
                // from a scroll. With nested scroll the list consumes first and only
                // what it cannot use arrives here — which is exactly the case where it
                // is already at the top and a downward swipe should close the drawer.
                .nestedScroll(dismissConnection)
        ) {
            if (onDismissDrag != null) {
                // The grab handle every sheet has. It is also a drag target, so the drawer can be
                // pulled down from the very top of it as well as from the list.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dismissDrag)
                        .padding(top = 10.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(OrbitColors.TextSecondary.copy(alpha = 0.45f))
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dismissDrag)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OrbitSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                    // The keyboard's Search key opens the best match.
                    onSearch = { filtered.firstOrNull()?.let(onLaunch) },
                    focusRequester = focusRequester,
                    // A full pill with no outline until it is focused: the search field of a
                    // Pixel or iOS drawer, not a form input.
                    shape = RoundedCornerShape(28.dp),
                    quietBorder = true,
                    modifier = Modifier.weight(1f)
                )
                DrawerModeButton(
                    // Show where tapping will take you, not where you are.
                    toGrid = !grid,
                    onClick = onToggleGrid
                )
            }
            Spacer(Modifier.height(8.dp))

            // The content fills what is left under the header, and the A-Z strip floats over its
            // right edge.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (filtered.isEmpty()) {
                AppEmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.drawer_empty_title),
                    body = stringResource(R.string.drawer_empty_body),
                    modifier = Modifier.fillMaxSize().then(dismissDrag)
                )
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp + railPad,
                        top = 4.dp,
                        bottom = 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(items = shown, key = { it.id }) { app ->
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            AppCell(
                                app = app,
                                iconLoader = iconLoader,
                                iconSize = GRID_ICON,
                                labelStyle = MaterialTheme.typography.bodySmall,
                                onLaunch = {
                                    keyboard?.hide()
                                    onLaunch(app)
                                },
                                // Press and hold, the way every launcher does it: a menu for the app,
                                // with Add to home in it. No badge sitting on every icon.
                                onLongPress = if (onAddToHome != null && onAppDragStart == null) {
                                    {
                                        haptics.press()
                                        menuFor = app.id
                                    }
                                } else null,
                                dragSource = if (onAddToHome != null && onAppDragStart != null) {
                                    AppDragCallbacks(
                                        onStart = { at -> onAppDragStart(app, at) },
                                        onMove = onAppDragMove,
                                        onEnd = onAppDragEnd,
                                        onCancel = onAppDragCancel,
                                        onHold = { menuFor = app.id }
                                    )
                                } else null
                            )
                            if (menuFor == app.id) DrawerAppMenu(
                                app = app,
                                onDismiss = { menuFor = null },
                                onAddToHome = if (onAddToHome != null && app.id !in homeAppIds) {
                                    { onAddToHome.invoke(app) }
                                } else null
                            )
                        }
                    }
                }
            } else {
                // One row, written once and used both under a section header and in a ranked search.
                val listRow: @Composable (InstalledApp) -> Unit = { app ->
                    Box {
                            AppRow(
                                app = app,
                                iconLoader = iconLoader,
                                onLaunch = {
                                    keyboard?.hide()
                                    onLaunch(app)
                                },
                                onLongPress = if (onAddToHome != null && onAppDragStart == null) {
                                    {
                                        haptics.press()
                                        menuFor = app.id
                                    }
                                } else null,
                                dragSource = if (onAddToHome != null && onAppDragStart != null) {
                                    AppDragCallbacks(
                                        onStart = { at -> onAppDragStart(app, at) },
                                        onMove = onAppDragMove,
                                        onEnd = onAppDragEnd,
                                        onCancel = onAppDragCancel,
                                        onHold = { menuFor = app.id }
                                    )
                                } else null
                            )
                            if (menuFor == app.id) DrawerAppMenu(
                                app = app,
                                onDismiss = { menuFor = null },
                                onAddToHome = if (onAddToHome != null && app.id !in homeAppIds) {
                                    { onAddToHome.invoke(app) }
                                } else null
                            )
                        }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(end = railPad, bottom = 24.dp)
                ) {
                    if (browsing) {
                        sections.forEach { section ->
                            item(key = "section-${section.key}") { SectionHeader(section.key) }
                            items(items = section.items, key = { it.id }) { app -> listRow(app) }
                        }
                    } else {
                        items(items = filtered, key = { it.id }) { app -> listRow(app) }
                    }
                }
            }
            if (showRail) {
                DrawerLetterRail(
                    keys = railKeys,
                    onScrub = { key ->
                        scope.launch {
                            if (grid) gridState.scrollToItem(DrawerIndex.gridIndex(sections, key))
                            else listState.scrollToItem(DrawerIndex.listIndex(sections, key))
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            }
        }
    }
}

/** The letter above each run of apps in the list, quiet enough to sit behind the names. */
@Composable
private fun SectionHeader(key: String) {
    Text(
        text = key,
        color = OrbitColors.TextSecondary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
            .semantics { heading() }
    )
}

/** One drawer row: icon, label, and the whole row is the touch target. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AppRow(
    app: InstalledApp,
    iconLoader: AppIconLoader,
    onLaunch: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    dragSource: AppDragCallbacks? = null
) {
    val openLabel = stringResource(R.string.app_open_label, app.label)
    val optionsLabel = stringResource(R.string.drawer_app_options)
    val px = with(LocalDensity.current) { ROW_ICON.roundToPx() }
    val icon = rememberAppIcon(app.id, px, iconLoader)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        onClickLabel = openLabel,
                        role = Role.Button,
                        onLongClickLabel = optionsLabel,
                        onLongClick = onLongPress,
                        onClick = onLaunch
                    )
                } else {
                    Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onLaunch)
                }
            )
            // After clickable on purpose: it sees the touch first and consumes a hold, so the hold
            // can never also be a tap.
            .appDragSource(dragSource)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(ROW_ICON), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.label,
            color = OrbitColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * What a press and hold on an app in the drawer offers. It is the home screen's own [AppMenu], so the two
 * look and animate the same: below the app (above it when there is no room), growing out of the app.
 *
 * [onAddToHome] is null for an app that is already on the home screen, so it is not offered twice.
 */
@Composable
internal fun DrawerAppMenu(
    app: InstalledApp,
    onDismiss: () -> Unit,
    onAddToHome: (() -> Unit)?,
    /** Offered where the app can be taken off the place it is in (the dock). */
    onRemove: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var below by remember { mutableStateOf(true) }
    var originX by remember { mutableStateOf(0.5f) }
    var placed by remember { mutableStateOf(false) }
    val provider = remember(density) {
        val gap = with(density) { 6.dp.roundToPx() }
        val margin = with(density) { 16.dp.roundToPx() }
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, maxX)
                val fitsBelow = anchorBounds.bottom + gap + popupContentSize.height <= windowSize.height - margin
                val y = if (fitsBelow) anchorBounds.bottom + gap
                else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
                below = fitsBelow
                originX = ((anchorBounds.center.x - x).toFloat() / popupContentSize.width.coerceAtLeast(1))
                    .coerceIn(0.1f, 0.9f)
                placed = true
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AppMenu(
            app = app,
            fromTop = below,
            originX = originX,
            ready = placed,
            onAddToHome = onAddToHome?.let { add -> { onDismiss(); add() } },
            onAppInfo = {
                onDismiss()
                AppActions.openAppInfo(context, app.packageName)
            },
            onRemove = onRemove?.let { remove -> { onDismiss(); remove() } },
            onUninstall = {
                onDismiss()
                AppActions.uninstall(context, app.packageName)
            }
        )
    }
}

/** How fast a downward throw, in px/s, closes the drawer without reaching the distance threshold. */
private const val FLING_CLOSE_VELOCITY = 1500f

/** One grid cell: icon above its label. Shared with the grid home, so the two cannot drift. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun AppCell(
    app: InstalledApp,
    iconLoader: AppIconLoader,
    onLaunch: () -> Unit,
    /** Press and hold. Null where the caller handles the hold itself, as the grid home does. */
    onLongPress: (() -> Unit)? = null,
    /** Hold then drag out of the drawer. Null everywhere but the drawer. */
    dragSource: AppDragCallbacks? = null,
    /** The icon's size. The home grid keeps the default; the drawer asks for a larger one. */
    iconSize: Dp = 52.dp,
    /** The label's type. Null is the small label the home grid has always used. */
    labelStyle: androidx.compose.ui.text.TextStyle? = null
) {
    val openLabel = stringResource(R.string.app_open_label, app.label)
    val optionsLabel = stringResource(R.string.drawer_app_options)
    val px = with(LocalDensity.current) { iconSize.roundToPx() }
    val icon = rememberAppIcon(app.id, px, iconLoader)

    Column(
        modifier = Modifier
            // The whole width the grid gives it. Without this the cell is only as wide as its icon or
            // its label, whichever is wider, so in the drawer's grid each one sat at the left of its
            // column and a long name pushed its neighbours out of line.
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        onClickLabel = openLabel,
                        role = Role.Button,
                        onLongClickLabel = optionsLabel,
                        onLongClick = onLongPress,
                        onClick = onLaunch
                    )
                } else {
                    Modifier.clickable(
                        onClickLabel = openLabel,
                        role = Role.Button,
                        onClick = onLaunch
                    )
                }
            )
            .appDragSource(dragSource)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = OrbitColors.TextPrimary,
            // Two lines, reserved whether the name needs them or not. The cell is a fixed height, so a
            // name that wraps used to push its icon up while its neighbours stayed put - which is what
            // made the rows look ragged. Reserving both lines costs a little space and buys a grid
            // whose icons are on a line.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = (labelStyle ?: MaterialTheme.typography.labelSmall).copy(shadow = LABEL_SHADOW)
        )
    }
}

/**
 * A whisper of shadow under app names, so white text stays readable over a bright wallpaper.
 * Soft and low enough that on a dark one it simply is not noticed.
 */
internal val LABEL_SHADOW = Shadow(
    color = Color.Black.copy(alpha = 0.35f),
    offset = Offset(0f, 1.5f),
    blurRadius = 6f
)

/**
 * The list/grid switch.
 *
 * The two glyphs are drawn from primitives rather than pulled from an icon pack:
 * the core Material set has no grid icon, and one small shape is not worth a new
 * dependency.
 */
@Composable
private fun DrawerModeButton(toGrid: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        if (toGrid) R.string.drawer_show_grid else R.string.drawer_show_list
    )
    Box(
        modifier = Modifier
            // As tall as the search pill beside it, and as round.
            .size(56.dp)
            .clip(CircleShape)
            .background(OrbitColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (toGrid) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(OrbitColors.TextPrimary)
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OrbitColors.TextPrimary)
                    )
                }
            }
        }
    }
}
