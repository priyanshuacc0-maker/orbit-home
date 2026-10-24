package com.orbit.home.overlay

import kotlin.math.roundToInt

/**
 * Which edge of the screen the floating wheel is docked to.
 *
 * [id] is what gets stored, so the numbers must never be reused or reordered.
 */
enum class DockEdge(val id: Int) {
    LEFT(0),
    TOP(1),
    RIGHT(2),
    BOTTOM(3);

    /** True for the two side edges, where the bar slides up and down. */
    val isSide: Boolean get() = this == LEFT || this == RIGHT

    companion object {
        /** The default, and where the wheel has always lived. */
        val DEFAULT = RIGHT

        /** An unknown or missing stored value falls back rather than failing. */
        fun fromId(id: Int?): DockEdge = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Where the floating wheel's two windows sit, and how far the wheel is pushed out of
 * the wheel window.
 *
 * The rule this object exists to enforce: **opening and closing never change a
 * window's bounds.** Android animates a window whose x/y/size changes (a decelerating
 * glide from the old origin to the new one), and that animation runs on top of ours.
 * That was the "bar comes from somewhere else" on close and the snap-back on open.
 * So there are two windows, each with a frame that is constant for a given dock
 * position, and everything that moves during a transition moves *inside* them:
 *
 *  - **Wheel window** — the half of the wheel's square that faces into the screen,
 *    i.e. the part that can ever be visible. Visual and, while open, touchable.
 *    Collapsed, it is not touchable and fully transparent, so it never takes a touch
 *    from the app below.
 *  - **Handle window** — the narrow touch target flush to the edge, centred on the same
 *    point along it. It owns the pull / tap / reposition gesture while collapsed.
 *
 * Only a deliberate move (dragging the handle along or between edges) changes a frame.
 *
 * ## Edges and the usable area
 *
 * The wheel docks to any of the four edges. Every function here takes the *usable
 * area* — the screen minus the system bars and display cutout — and docks to the
 * edges of that, so the bar never sits under the status bar or on top of the
 * navigation buttons, and never slides under them either. The area is also what the
 * along-edge travel is measured in. For the left and right edges the bar slides along
 * the area's height; for the top and bottom edges it slides along its width.
 *
 * The functions that take no edge or area behave exactly as before: the right edge of
 * a whole screen.
 *
 * Pure, so all of this can be checked without a device.
 */
object EdgeDockGeometry {

    /** A rectangle in screen coordinates. */
    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
    }

    /** Pixels taken up by the system bars and cutout on each side of the screen. */
    data class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        companion object {
            val NONE = Insets(0, 0, 0, 0)
        }
    }

    /**
     * The part of the screen the bar may use: the whole screen less [insets].
     *
     * Never negative in size, so a tiny or oddly-insetted display cannot produce a
     * window with a negative width.
     */
    fun usableArea(screenWidthPx: Int, screenHeightPx: Int, insets: Insets): Rect {
        val left = insets.left.coerceIn(0, screenWidthPx)
        val top = insets.top.coerceIn(0, screenHeightPx)
        val right = (screenWidthPx - insets.right).coerceIn(left, screenWidthPx)
        val bottom = (screenHeightPx - insets.bottom).coerceIn(top, screenHeightPx)
        return Rect(left, top, right - left, bottom - top)
    }

    /** The wheel's square must be even so half of it is a whole number of pixels. */
    fun evenSize(sizePx: Int): Int = sizePx and 1.inv()

    /** Half the wheel: the wheel window's thickness and the wheel's full travel. */
    fun halfWheelPx(wheelSizePx: Int): Int = wheelSizePx / 2

    // --- Position along the edge ---

    /** The length of the edge the bar slides along. */
    private fun alongLength(edge: DockEdge, area: Rect): Int =
        if (edge.isSide) area.height else area.width

    /** Where the edge the bar slides along begins. */
    private fun alongStart(edge: DockEdge, area: Rect): Int =
        if (edge.isSide) area.y else area.x

    /**
     * The wheel's centre along the edge, in screen coordinates, from the docked
     * fraction. 0 puts the wheel against one end of the edge, 1 against the other;
     * the wheel stays wholly inside the area at both.
     */
    fun centreAlong(edge: DockEdge, area: Rect, wheelSizePx: Int, dockedFraction: Float): Float {
        val travel = (alongLength(edge, area) - wheelSizePx).coerceAtLeast(0)
        return alongStart(edge, area) + wheelSizePx / 2f + travel * dockedFraction.coerceIn(0f, 1f)
    }

    /** The docked fraction for a wheel centred at [centre], the inverse of [centreAlong]. */
    fun fractionForCentreAlong(centre: Float, edge: DockEdge, area: Rect, wheelSizePx: Int): Float {
        val travel = (alongLength(edge, area) - wheelSizePx).coerceAtLeast(1)
        return ((centre - alongStart(edge, area) - wheelSizePx / 2f) / travel).coerceIn(0f, 1f)
    }

    /** The wheel's centre vertically, from the docked fraction. */
    fun centreY(screenHeightPx: Int, wheelSizePx: Int, dockedFraction: Float): Float =
        centreAlong(
            DockEdge.RIGHT, Rect(0, 0, 0, screenHeightPx), wheelSizePx, dockedFraction
        )

    /** The docked fraction for a wheel centred at [centreY], the inverse of [centreY]. */
    fun fractionForCentre(centreY: Float, screenHeightPx: Int, wheelSizePx: Int): Float =
        fractionForCentreAlong(
            centreY, DockEdge.RIGHT, Rect(0, 0, 0, screenHeightPx), wheelSizePx
        )

    // --- The windows ---

    /**
     * The wheel window: the half of the wheel's square that faces into the screen,
     * flush to [edge] of [area]. With the wheel fully out its centre is exactly on
     * that edge, so this is precisely the part of the wheel that is on screen.
     */
    fun wheelWindow(
        edge: DockEdge,
        area: Rect,
        wheelSizePx: Int,
        dockedFraction: Float
    ): Rect {
        val half = halfWheelPx(wheelSizePx)
        val along = (centreAlong(edge, area, wheelSizePx, dockedFraction) - wheelSizePx / 2f).roundToInt()
        return when (edge) {
            DockEdge.RIGHT -> Rect(area.right - half, along, half, wheelSizePx)
            DockEdge.LEFT -> Rect(area.x, along, half, wheelSizePx)
            DockEdge.TOP -> Rect(along, area.y, wheelSizePx, half)
            DockEdge.BOTTOM -> Rect(along, area.bottom - half, wheelSizePx, half)
        }
    }

    /**
     * The handle window: flush to [edge] of [area] and centred on the wheel's centre.
     *
     * [handleThicknessPx] is how far it reaches into the screen (the touch target's
     * width on a side edge, its height on the top or bottom); [handleLengthPx] is how
     * long it is along the edge.
     */
    fun handleWindow(
        edge: DockEdge,
        area: Rect,
        wheelSizePx: Int,
        handleThicknessPx: Int,
        handleLengthPx: Int,
        dockedFraction: Float
    ): Rect {
        val centre = centreAlong(edge, area, wheelSizePx, dockedFraction)
        val along = (centre - handleLengthPx / 2f).roundToInt()
        return when (edge) {
            DockEdge.RIGHT -> Rect(area.right - handleThicknessPx, along, handleThicknessPx, handleLengthPx)
            DockEdge.LEFT -> Rect(area.x, along, handleThicknessPx, handleLengthPx)
            DockEdge.TOP -> Rect(along, area.y, handleLengthPx, handleThicknessPx)
            DockEdge.BOTTOM -> Rect(along, area.bottom - handleThicknessPx, handleLengthPx, handleThicknessPx)
        }
    }

    /**
     * The wheel window on the right edge of a whole screen. Kept for callers that
     * have no edge or insets to pass.
     */
    fun wheelWindow(
        screenWidthPx: Int,
        screenHeightPx: Int,
        wheelSizePx: Int,
        dockedFraction: Float
    ): Rect = wheelWindow(
        DockEdge.RIGHT, Rect(0, 0, screenWidthPx, screenHeightPx), wheelSizePx, dockedFraction
    )

    /** The handle window on the right edge of a whole screen. */
    fun handleWindow(
        screenWidthPx: Int,
        screenHeightPx: Int,
        wheelSizePx: Int,
        handleWidthPx: Int,
        handleHeightPx: Int,
        dockedFraction: Float
    ): Rect = handleWindow(
        DockEdge.RIGHT, Rect(0, 0, screenWidthPx, screenHeightPx),
        wheelSizePx, handleWidthPx, handleHeightPx, dockedFraction
    )

    // --- Where the wheel is drawn inside its window ---

    /**
     * How far the wheel sits outward from its fully-out place, inside the wheel
     * window. 0 when out; half the wheel when docked, which pushes all of it past the
     * window's outer edge. This is the only thing that changes during a transition.
     */
    fun travelPx(progress: Float, wheelSizePx: Int): Float =
        (1f - progress.coerceIn(0f, 1f)) * halfWheelPx(wheelSizePx)

    /** [travelPx] as a horizontal shift: positive towards the right, so outward on the right edge. */
    fun travelX(progress: Float, wheelSizePx: Int): Float = travelPx(progress, wheelSizePx)

    /** The horizontal shift of the wheel for [edge]; 0 on the top and bottom edges. */
    fun travelX(edge: DockEdge, progress: Float, wheelSizePx: Int): Float = when (edge) {
        DockEdge.RIGHT -> travelPx(progress, wheelSizePx)
        DockEdge.LEFT -> -travelPx(progress, wheelSizePx)
        DockEdge.TOP, DockEdge.BOTTOM -> 0f
    }

    /** The vertical shift of the wheel for [edge]; 0 on the left and right edges. */
    fun travelY(edge: DockEdge, progress: Float, wheelSizePx: Int): Float = when (edge) {
        DockEdge.BOTTOM -> travelPx(progress, wheelSizePx)
        DockEdge.TOP -> -travelPx(progress, wheelSizePx)
        DockEdge.LEFT, DockEdge.RIGHT -> 0f
    }

    /**
     * Where the wheel's square is placed inside its window, horizontally.
     *
     * The window is only half the square, so on the left edge (where the visible half is
     * the square's right half) the square starts half a wheel to the left of the window.
     */
    fun wheelOriginX(edge: DockEdge, wheelSizePx: Int): Int =
        if (edge == DockEdge.LEFT) -halfWheelPx(wheelSizePx) else 0

    /** Where the wheel's square is placed inside its window, vertically. */
    fun wheelOriginY(edge: DockEdge, wheelSizePx: Int): Int =
        if (edge == DockEdge.TOP) -halfWheelPx(wheelSizePx) else 0

    /**
     * The direction, in degrees clockwise from the top, the wheel's focus slot points
     * when at rest: into the screen from [edge], so it sits in the middle of the half
     * that can be seen.
     */
    fun focusAngleDegrees(edge: DockEdge): Float = when (edge) {
        DockEdge.RIGHT -> 270f   // pointing left
        DockEdge.LEFT -> 90f     // pointing right
        DockEdge.TOP -> 180f     // pointing down
        DockEdge.BOTTOM -> 0f    // pointing up
    }

    // --- Moving between edges ---

    /** How far a point is from [edge] of [area]. Negative when it is past it. */
    fun distanceToEdge(edge: DockEdge, area: Rect, x: Float, y: Float): Float = when (edge) {
        DockEdge.LEFT -> x - area.x
        DockEdge.RIGHT -> area.right - x
        DockEdge.TOP -> y - area.y
        DockEdge.BOTTOM -> area.bottom - y
    }

    /**
     * The edge the bar should be on while a finger drags it to ([x], [y]).
     *
     * The bar stays on [current] until the finger comes within [snapZonePx] of a
     * different edge (and is nearer to it than to the current one by [hysteresisPx]), and then
     * hops to the nearest one. In the middle of the screen
     * nothing is near, so the bar stays where it is instead of flicking about between
     * edges as the finger crosses the diagonals. A tie goes to [current].
     */
    fun edgeForDrag(
        current: DockEdge,
        area: Rect,
        x: Float,
        y: Float,
        snapZonePx: Float,
        hysteresisPx: Float = 0f,
        stickyEdge: DockEdge? = null
    ): DockEdge {
        var best = current
        var bestDistance = distanceToEdge(current, area, x, y)
        for (edge in DockEdge.entries) {
            if (edge == current) continue
            val d = distanceToEdge(edge, area, x, y)
            // [hysteresisPx] applies only to [stickyEdge], the edge the bar has just hopped
            // away from: going straight back has to win by a margin, so a finger near a corner
            // cannot make the bar flip back and forth, while a first hop needs no margin at all.
            val margin = if (edge == stickyEdge) hysteresisPx else 0f
            if (d + margin < bestDistance && d <= snapZonePx) {
                best = edge
                bestDistance = d
            }
        }
        return best
    }

    /**
     * One step of a fixed-fraction chase from [current] towards [target].
     *
     * The wheel's reveal is drawn by chasing the finger rather than jumping to it, which is what
     * keeps the pull smooth: touch samples arrive in bursts, a hand is not steady, and the first
     * sample lands after the touch slop, so drawing each sample directly made the wheel jump and
     * stutter. Because a still finger sends no movement, there is nothing left to catch up on
     * once it stops.
     *
     * @param fraction how much of the gap to close, 0 to 1. 1 lands exactly on the target.
     */
    fun chase(current: Float, target: Float, fraction: Float): Float =
        current + (target - current) * fraction.coerceIn(0f, 1f)
}
