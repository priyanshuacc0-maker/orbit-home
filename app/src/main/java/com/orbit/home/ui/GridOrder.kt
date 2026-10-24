package com.orbit.home.ui

/**
 * The cells the grid home puts apps in.
 *
 * Cells, not a sequence. A sequence has no room in it — index twelve either holds an app or the
 * sequence is shorter — and that is why an app could not be dropped into empty space: there was no
 * such thing. A phone's home grid is a set of cells, some of them empty, and empty is somewhere an
 * app can go.
 *
 * Pure, and separate, because the mistakes here are silent. A placement that drops an id takes an app
 * off the grid; one that mishandles a gap moves everything after it; a swap that is really a move
 * leaves a hole where the app was.
 */
object GridOrder {

    /** A cell that holds nothing. */
    const val EMPTY = ""

    /** At least one page exists, even when there are no apps; cell capacity belongs to each page. */
    fun pageCount(cellCount: Int, cellsPerPage: Int): Int {
        require(cellsPerPage > 0) { "cellsPerPage must be positive" }
        return ((cellCount.coerceAtLeast(0) + cellsPerPage - 1) / cellsPerPage).coerceAtLeast(1)
    }

    /**
     * The cells to draw: the placement as it was saved, then every app that has no cell of its own
     * into the first free ones.
     *
     * An id that is no longer installed leaves its cell **empty rather than closing the gap**, so the
     * rest of the grid does not shift under the user when an app is uninstalled, and reinstalling it
     * puts it back where it was. An id listed twice keeps its first cell and leaves the rest empty.
     *
     * A newly installed app goes into the first free cell, which is what a phone does — including a
     * gap that was left on purpose. That is the one place the arrangement gives way, and it gives way
     * the way everything else does.
     */
    fun cells(allIds: List<String>, saved: List<String>): List<String> {
        val present = allIds.toSet()
        val out = ArrayList<String>(maxOf(saved.size, allIds.size))
        val placed = HashSet<String>()

        for (id in saved) {
            val keep = id.isNotEmpty() && id in present && placed.add(id)
            out.add(if (keep) id else EMPTY)
        }

        for (id in allIds) {
            if (id in placed) continue
            placed.add(id)
            val free = out.indexOfFirst { it == EMPTY }
            if (free >= 0) out[free] = id else out.add(id)
        }

        // Trailing empties are not a gap, they are the grid being longer than it needs to be.
        while (out.isNotEmpty() && out.last() == EMPTY) out.removeAt(out.size - 1)
        return out
    }

    /**
     * [cells] with the app at [from] put into [to].
     *
     * Dropped on an empty cell, the app moves there and its old cell is left empty.
     *
     * Dropped on an occupied one, the app takes that cell and the app that was there goes to the
     * **nearest free cell on the page it is already on** — [page]. An app being replaced should not be
     * thrown onto a different page, which is what changing places does when the two cells are on
     * different pages: the displaced app would vanish off the page the user is looking at.
     *
     * If that page has no free cell, the two change places after all. Something has to give, and the
     * cell the other app came from is the one place that is certainly free.
     *
     * (On a phone, dropping an icon onto another makes a folder. That is the next thing we build.)
     */
    fun place(cells: List<String>, from: Int, to: Int, page: IntRange): List<String> {
        if (from == to) return cells
        if (from !in cells.indices) return cells
        if (to < 0) return cells
        if (cells.getOrNull(from) == EMPTY) return cells

        val out = cells.toMutableList()
        while (out.size <= to) out.add(EMPTY)
        val moved = out[from]
        val displaced = out[to]
        out[to] = moved

        if (displaced == EMPTY) {
            // Nothing was there to displace: the app simply left its old cell.
            out[from] = EMPTY
        } else {
            val free = nearestFree(out, to, page)
            if (free != null) {
                // The displaced app goes to a free cell on its page, and the app that was dragged
                // leaves the cell it came from. Forgetting that second half left the app in two
                // cells at once - which the tests caught.
                out[free] = displaced
                out[from] = EMPTY
            } else {
                // No free cell on that page: they change places, and the cell the other came from
                // is exactly where this one goes.
                out[from] = displaced
            }
        }

        while (out.isNotEmpty() && out.last() == EMPTY) out.removeAt(out.size - 1)
        return out
    }

    /**
     * [cells] with [id] dropped into cell [to], for an app that comes from OUTSIDE the grid (the drawer).
     *
     * An app already in the grid is simply moved, exactly as [place] does it. A new one takes the cell;
     * if something was there, that app goes to the nearest free cell on [page], and failing that to the
     * first free cell anywhere, and failing that to the end. Nothing is ever lost or doubled.
     */
    fun insert(cells: List<String>, id: String, to: Int, page: IntRange): List<String> {
        if (id.isEmpty() || to < 0) return cells
        val existing = cells.indexOf(id)
        if (existing >= 0) return place(cells, existing, to, page)

        val out = cells.toMutableList()
        while (out.size <= to) out.add(EMPTY)
        val displaced = out[to]
        out[to] = id
        if (displaced != EMPTY) {
            val free = nearestFree(out, to, page) ?: out.indexOfFirst { it == EMPTY }.takeIf { it >= 0 }
            if (free != null) out[free] = displaced else out.add(displaced)
        }
        while (out.isNotEmpty() && out.last() == EMPTY) out.removeAt(out.size - 1)
        return out
    }

    /**
     * Which apps change cell between [before] and [after], and the cell each one ends up in.
     *
     * This is what the grid draws while an app is carried: [after] is what dropping it right now
     * would produce, so every app listed here is one that has to get out of the way. [mover] is the
     * app being carried and is left out - it is under the finger, not sliding.
     */
    fun moves(before: List<String>, after: List<String>, mover: String): Map<String, Int> {
        val was = HashMap<String, Int>(before.size)
        before.forEachIndexed { index, id -> if (id != EMPTY) was.putIfAbsent(id, index) }
        val out = HashMap<String, Int>()
        after.forEachIndexed { index, id ->
            if (id != EMPTY && id != mover && was[id] != index) out[id] = index
        }
        return out
    }

    /**
     * The free cell on [page] nearest [to], or null if that page has none.
     *
     * Outward from [to] on both sides, so an app lands as close as possible to where the finger let go
     * of it, and one step to the left is preferred to one step to the right — the same way round as
     * reading.
     */
    private fun nearestFree(cells: List<String>, to: Int, page: IntRange): Int? {
        if (page.isEmpty()) return null
        var distance = 1
        val furthest = maxOf(to - page.first, page.last - to)
        while (distance <= furthest) {
            val before = to - distance
            if (before in page && cells.getOrNull(before) == EMPTY) return before
            val after = to + distance
            if (after in page && cells.getOrNull(after) == EMPTY) return after
            distance++
        }
        return null
    }

    /** How many cells hold an app. */
    fun occupied(cells: List<String>): Int = cells.count { it != EMPTY }
}
