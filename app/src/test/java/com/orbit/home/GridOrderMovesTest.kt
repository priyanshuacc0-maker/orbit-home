package com.orbit.home

import com.orbit.home.ui.GridOrder
import com.orbit.home.ui.GridOrder.EMPTY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which apps slide out of the way while another is carried over them.
 *
 * The preview is the drop's own result, so these also pin that the two cannot disagree.
 */
class GridOrderMovesTest {

    private val page = 0..3

    @Test
    fun `over an empty cell nothing moves`() {
        val cells = listOf("a", "b", EMPTY, EMPTY)
        val after = GridOrder.place(cells, 0, 2, page)
        assertTrue(GridOrder.moves(cells, after, "a").isEmpty())
    }

    @Test
    fun `over an app, that app slides to the free cell the drop would give it`() {
        val cells = listOf("a", "b", "c", EMPTY)
        val after = GridOrder.place(cells, 0, 1, page)
        // "b" is pushed to the nearest free cell; the carried app is not listed.
        assertEquals(mapOf("b" to 3), GridOrder.moves(cells, after, "a"))
    }

    @Test
    fun `on a full page the two swap, so the other app slides to where the carried one came from`() {
        val cells = listOf("a", "b", "c", "d")
        val after = GridOrder.place(cells, 0, 2, page)
        assertEquals(mapOf("c" to 0), GridOrder.moves(cells, after, "a"))
    }

    @Test
    fun `an app from outside the grid pushes the one it lands on`() {
        val cells = listOf("a", "b", EMPTY, "d")
        val after = GridOrder.insert(cells, "new", 1, page)
        assertEquals(mapOf("b" to 2), GridOrder.moves(cells, after, "new"))
    }

    @Test
    fun `the carried app itself is never reported`() {
        val cells = listOf("a", "b", "c", "d")
        for (from in cells.indices) for (to in cells.indices) {
            val moves = GridOrder.moves(cells, GridOrder.place(cells, from, to, page), cells[from])
            assertTrue(cells[from] !in moves)
        }
    }

    @Test
    fun `no app that is not displaced is reported`() {
        val cells = listOf("a", "b", "c", EMPTY)
        val moves = GridOrder.moves(cells, GridOrder.place(cells, 0, 1, page), "a")
        assertTrue("c" !in moves)
    }
}
