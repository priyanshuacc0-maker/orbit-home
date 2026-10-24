package com.orbit.home

import com.orbit.home.ui.GridOrder
import com.orbit.home.ui.GridOrder.EMPTY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid's cells: a placement with gaps, and a newly installed app filling the first free one.
 *
 * Both halves fail silently. A placement that drops an id takes an app off the grid; one that closes
 * a gap moves everything after it; a swap that is really a move leaves a hole where the app was.
 */
class GridOrderTest {

    private val installed = listOf("a", "b", "c", "d", "e")

    // ---------------- cells ----------------

    @Test
    fun `with nothing placed, the apps fill the cells in order`() {
        assertEquals(installed, GridOrder.cells(installed, emptyList()))
    }

    @Test
    fun `a placement is kept exactly, gaps and all`() {
        // Every app has a cell, so nothing needs one and the gap stays a gap.
        assertEquals(
            listOf("c", EMPTY, "a", "b", "d", "e"),
            GridOrder.cells(installed, listOf("c", EMPTY, "a", "b", "d", "e"))
        )
    }

    @Test
    fun `a gap left on purpose is filled by the next app that needs a cell`() {
        // This is the one place an arrangement gives way, and it gives way the way a phone does: a
        // newly installed app takes the first free cell, even one that was left free deliberately.
        assertEquals(
            listOf("c", "d", "a", "b", "e"),
            GridOrder.cells(installed, listOf("c", EMPTY, "a", "b"))
        )
    }

    @Test
    fun `an app that was never placed fills the first free cell`() {
        // "d" and "e" have no cell, and the gap at index 1 is the first free one.
        assertEquals(
            listOf("c", "d", "a", "b", "e"),
            GridOrder.cells(installed, listOf("c", EMPTY, "a", "b"))
        )
    }

    @Test
    fun `an app that was uninstalled does not shift the apps after it`() {
        // "b" is gone. Nothing closes up behind it: "a" is still first and "c" is still third, so
        // the grid does not rearrange itself under the user because something was uninstalled.
        val out = GridOrder.cells(listOf("a", "c", "d", "e"), listOf("a", "b", "c"))
        assertEquals("a", out[0])
        assertEquals("c", out[2])
    }

    @Test
    fun `an app that was uninstalled leaves its cell free for something else`() {
        // "d" had no cell, and the one "b" gave up is the first free one.
        assertEquals(
            listOf("a", "d", "c", "e"),
            GridOrder.cells(listOf("a", "c", "d", "e"), listOf("a", "b", "c"))
        )
    }

    @Test
    fun `an app listed twice appears once, in its first cell`() {
        val out = GridOrder.cells(installed, listOf("a", "b", "a", "b"))
        assertEquals(out.distinct().size, out.count { it != EMPTY })
        assertEquals("a", out[0])
        assertEquals("b", out[1])
        // and the cells the duplicates gave up were free for the rest
        assertEquals(listOf("a", "b", "c", "d", "e"), out)
    }

    @Test
    fun `trailing empties are trimmed, because that is not a gap`() {
        assertEquals(listOf("a", "b"), GridOrder.cells(listOf("a", "b"), listOf("a", "b", EMPTY, EMPTY)))
    }

    @Test
    fun `every installed app appears exactly once, whatever was placed`() {
        for (saved in listOf(
            emptyList(), listOf("e"), listOf("b", EMPTY, "d"), listOf("gone", "a"),
            listOf(EMPTY, EMPTY, EMPTY), listOf("c", "c", "c")
        )) {
            val out = GridOrder.cells(installed, saved)
            val held = out.filter { it != EMPTY }
            assertEquals("a placement of $saved lost or duplicated an app", installed.toSet(), held.toSet())
            assertEquals(installed.size, held.size)
            assertTrue("a placement of $saved ended with a trailing gap", out.lastOrNull() != EMPTY)
        }
    }

    // ---------------- place ----------------

    @Test
    fun `dropped on an empty cell, the app moves there and leaves its own cell empty`() {
        val cells = listOf("a", "b", "c", EMPTY)
        assertEquals(listOf(EMPTY, "b", "c", "a"), placeAll(cells, 0, 3))
    }

    @Test
    fun `dropped on an occupied cell, the two change places`() {
        val cells = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "b", "a", "d"), placeAll(cells, 0, 2))
        assertEquals(listOf("d", "b", "c", "a"), placeAll(cells, 3, 0))
    }

    @Test
    fun `a swap leaves no hole and no app behind`() {
        val cells = listOf("a", "b", "c", "d")
        for (from in cells.indices) {
            for (to in cells.indices) {
                val out = placeAll(cells, from, to)
                assertEquals("place $from to $to lost or duplicated", cells.toSet(), out.toSet())
                assertEquals(cells.size, out.size)
                assertEquals("the displaced app did not land where the other came from", cells[from], out[to])
                assertEquals(cells[to], out[from])
            }
        }
    }

    @Test
    fun `dropping past the end puts the app in the cell it was dropped on`() {
        assertEquals(listOf(EMPTY, "b", EMPTY, EMPTY, "a"), placeAll(listOf("a", "b"), 0, 4))
    }

    @Test
    fun `dropping on an empty cell never moves an app out of a cell that has one`() {
        // The other apps stay exactly where they were.
        val cells = listOf("a", "b", "c", EMPTY)
        val out = placeAll(cells, 1, 3)
        assertEquals("a", out[0])
        assertEquals(EMPTY, out[1])
        assertEquals("c", out[2])
        assertEquals("b", out[3])
    }

    @Test
    fun `a place that makes no sense changes nothing`() {
        val cells = listOf("a", "b", "c")
        assertEquals(cells, placeAll(cells, 1, 1))
        assertEquals(cells, placeAll(cells, -1, 1))
        assertEquals(cells, placeAll(cells, 5, 1))
        assertEquals(cells, placeAll(cells, 1, -1))
        // A cell with nothing in it cannot be picked up in the first place.
        val oneEmpty = listOf(EMPTY, "b")
        assertEquals(oneEmpty, placeAll(oneEmpty, 0, 1))
    }

    @Test
    fun `what a phone would call occupied is what we count`() {
        assertEquals(3, GridOrder.occupied(listOf("a", EMPTY, "b", "c", EMPTY)))
        assertEquals(0, GridOrder.occupied(emptyList()))
    }

    // ---------------- the shape of what was already stored ----------------

    @Test
    fun `an arrangement saved before cells existed reads as a full grid with no gaps`() {
        // The old format was the same list without any empties, so it is still a valid placement —
        // and it means exactly what it meant before: every app, packed, in that order.
        val old = listOf("c", "a", "b")
        assertEquals(listOf("c", "a", "b", "d", "e"), GridOrder.cells(installed, old))
    }
}

/**
 * A drop with the whole list as one page.
 *
 * The page rule only changes what happens when the two cells are on different pages, so these
 * expectations describe the same behaviour they always did.
 */
private fun placeAll(cells: List<String>, from: Int, to: Int) =
    GridOrder.place(cells, from, to, cells.indices)

/**
 * The page an app being replaced should stay on.
 *
 * This is the fault it fixes: changing places across pages threw the displaced app onto the page the
 * other one came from, so it vanished off the page the user was looking at.
 */
class GridOrderPageTest {

    // Two pages of four, laid out as one list: 0-3 and 4-7.
    private val first = 0..3
    private val second = 4..7

    @Test
    fun `the displaced app goes to the nearest free cell on its own page`() {
        val cells = listOf("a", "b", "c", "d", "e", "f", EMPTY, "h")
        val out = GridOrder.place(cells, 0, 4, second)
        assertEquals("a", out[4])          // the dragged app took the cell it was dropped on
        assertEquals("e", out[6])          // and "e" went to the free cell on its own page
        assertEquals(EMPTY, out[0])        // "a"'s old cell, on the other page, is left empty
        assertEquals(cells.toSet(), out.toSet())
    }

    @Test
    fun `the nearest free cell is the closest one, not the first one`() {
        // Free cells at 4 and 7; the drop is at 6, so 7 is nearer than 4.
        val cells = listOf("a", EMPTY, EMPTY, EMPTY, EMPTY, "f", "g", EMPTY)
        val out = GridOrder.place(cells, 0, 6, second)
        assertEquals("g", out[7])
        assertEquals(EMPTY, out[0])
    }

    @Test
    fun `a page with no free cell falls back to changing places`() {
        // Both pages full. Something has to give, and the cell the other app came from is certainly
        // free.
        val cells = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        val out = GridOrder.place(cells, 0, 4, second)
        assertEquals("a", out[4])
        assertEquals("e", out[0])
        assertEquals(cells.toSet(), out.toSet())
    }

    @Test
    fun `within one page the two still change places`() {
        val cells = listOf("a", "b", "c", "d")
        val out = GridOrder.place(cells, 0, 2, first)
        assertEquals(listOf("c", "b", "a", "d"), out)
    }

    @Test
    fun `an empty page range does not crash and does not lose an app`() {
        val cells = listOf("a", "b", "c", "d")
        val out = GridOrder.place(cells, 0, 2, IntRange.EMPTY)
        assertEquals(cells.toSet(), out.toSet())
        assertEquals(cells.size, out.size)
    }


    @Test fun pageCountAlwaysHasOnePageAndRoundsUpByCellCapacity() {
        assertEquals(1, GridOrder.pageCount(0, 12))
        assertEquals(1, GridOrder.pageCount(12, 12))
        assertEquals(2, GridOrder.pageCount(13, 12))
        assertEquals(3, GridOrder.pageCount(25, 12))
    }


    @Test fun droppingIntoAPreviouslyNonexistentPageExtendsTheStoredLayout() {
        val original = listOf("a", "b")
        val placed = GridOrder.place(original, from = 0, to = 4, page = 4..7)

        assertEquals(EMPTY, placed[0])
        assertEquals("a", placed[4])
        assertEquals(2, GridOrder.pageCount(placed.size, cellsPerPage = 4))
    }
}
