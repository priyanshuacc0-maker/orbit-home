package com.orbit.home

import com.orbit.home.ui.HomeSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arrangement a new home screen starts with.
 *
 * It fails silently in both directions: an empty home screen is a home screen nobody uses, and one that
 * starts full of everything is a drawer with extra steps.
 */
class HomeSeedTest {

    private val ids = (1..60).map { "app$it" }
    private val columns = 4
    private val rows = 5          // 20 a page

    @Test
    fun `the first page has its top clear and its apps at the bottom`() {
        val cells = HomeSeed.cells(ids, columns, rows)
        // 20 cells on the page, 8 for the apps, so 12 empty above them.
        assertEquals(12, cells.takeWhile { it.isEmpty() }.size)
        assertEquals("app1", cells[12])
        assertEquals("app8", cells[19])
    }

    @Test
    fun `the page after the first is full`() {
        val cells = HomeSeed.cells(ids, columns, rows)
        assertEquals(20, cells.drop(20).takeWhile { it.isNotEmpty() }.size)
    }

    @Test
    fun `it starts with two pages and no more`() {
        val cells = HomeSeed.cells(ids, columns, rows)
        assertEquals(8 + 20, cells.count { it.isNotEmpty() })
    }

    @Test
    fun `fewer apps than that are all placed, and no gap is left at the end`() {
        val cells = HomeSeed.cells(listOf("a", "b"), columns, rows)
        assertEquals(2, cells.count { it.isNotEmpty() })
        assertTrue("a gap was left at the end", cells.last().isNotEmpty())
    }

    @Test
    fun `the apps are kept in the order they were given`() {
        val cells = HomeSeed.cells(ids, columns, rows)
        assertEquals(ids.take(28), cells.filter { it.isNotEmpty() })
    }

    @Test
    fun `nothing is placed twice`() {
        val placed = HomeSeed.cells(ids, columns, rows).filter { it.isNotEmpty() }
        assertEquals(placed.distinct(), placed)
    }

    @Test
    fun `a grid with no room, or no apps, produces nothing rather than crashing`() {
        assertTrue(HomeSeed.cells(emptyList(), columns, rows).isEmpty())
        assertTrue(HomeSeed.cells(ids, 0, rows).isEmpty())
        assertTrue(HomeSeed.cells(ids, columns, 0).isEmpty())
    }
}
