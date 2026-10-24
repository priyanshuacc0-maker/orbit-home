package com.orbit.home.apps

import kotlin.math.roundToInt

/**
 * The dock's slot arithmetic and drop rules, kept pure so they can be unit-tested.
 *
 * The dock spreads its icons evenly: [count] icons of [icon] px across [width] px, with an equal gap
 * before, between and after them. Every number here is in that row's own coordinates (0 is the left
 * edge of the row's content area).
 */
object DockLayout {

    /** The gap between neighbouring icons (and between the ends and the first/last icon). */
    fun gap(count: Int, width: Float, icon: Float): Float = (width - count * icon) / (count + 1)

    /** The left edge of slot [slot] when the row holds [count] slots. */
    fun slotLeft(slot: Int, count: Int, width: Float, icon: Float): Float {
        val g = gap(count, width, icon)
        return g + slot * (g + icon)
    }

    /** The slot whose centre is nearest [x], among [count] slots. */
    fun nearestSlot(x: Float, count: Int, width: Float, icon: Float): Int {
        if (count <= 0) return 0
        val g = gap(count, width, icon)
        return ((x - g - icon / 2f) / (g + icon)).roundToInt().coerceIn(0, count - 1)
    }

    /** Where in a dock of [existing] apps a newcomer carried at [x] would go (0..existing). */
    fun insertionIndex(x: Float, existing: Int, width: Float, icon: Float): Int =
        nearestSlot(x, existing + 1, width, icon)

    /**
     * The slot the app at [position] occupies while a gap is held open at [hole] for an incoming app.
     * Everything at or after the hole is pushed one slot along.
     */
    fun slotFor(position: Int, hole: Int?): Int = if (hole != null && position >= hole) position + 1 else position

    /** Whether the dock can take [id]: it is not already there and there is a free slot. */
    fun accepts(dock: List<String>, id: String, max: Int): Boolean = id !in dock && dock.size < max

    /** [dock] with [id] put in at [index], or null if the dock cannot take it. */
    fun insert(dock: List<String>, id: String, index: Int, max: Int): List<String>? {
        if (!accepts(dock, id, max)) return null
        return dock.toMutableList().apply { add(index.coerceIn(0, size), id) }
    }
}
