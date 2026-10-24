package com.orbit.home

import com.orbit.home.ui.GridOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** An app dragged in from the drawer: it must land where dropped, and never lose or double anything. */
class GridOrderInsertTest {
    private val page = 0..3

    @Test
    fun `dropped on an empty cell it goes there`() {
        assertEquals(listOf("a", "", "new", "b"), GridOrder.insert(listOf("a", "", "", "b"), "new", 2, page))
    }

    @Test
    fun `dropped past the end the grid grows into the gap`() {
        assertEquals(listOf("a", "", "", "new"), GridOrder.insert(listOf("a"), "new", 3, page))
    }

    @Test
    fun `dropped on an app it takes the cell and the app moves to the nearest free one`() {
        assertEquals(listOf("a", "new", "b"), GridOrder.insert(listOf("a", "b", ""), "new", 1, page))
    }

    @Test
    fun `a full page pushes the displaced app to the end, nothing is lost`() {
        val full = listOf("a", "b", "c", "d")
        val out = GridOrder.insert(full, "new", 1, page)
        assertEquals(setOf("a", "new", "c", "d", "b"), out.toSet())
        assertEquals(5, out.size)
        assertEquals("new", out[1])
    }

    @Test
    fun `an app already on the grid is moved, not duplicated`() {
        val out = GridOrder.insert(listOf("a", "b", ""), "a", 2, page)
        assertEquals(1, out.count { it == "a" })
        assertEquals("a", out[2])
    }
}
