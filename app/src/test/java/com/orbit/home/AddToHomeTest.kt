package com.orbit.home

import com.orbit.home.viewmodel.addToCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where an app goes when it is added to the home screen.
 *
 * The rule is small and the ways it can be wrong are not obvious: an app placed twice, an app placed on
 * top of another, or a screen rearranged to make room for something arriving. Each of those is silent,
 * which is why each of them has a test.
 *
 * NOT COVERED HERE, and needing an instrumented test: that tapping an app still launches it and tapping
 * Add to home does not, that Settings > Apps offers no Add to home, and that the layout survives a
 * restart. None of those can be shown from a pure function.
 */
class AddToHomeTest {

    @Test
    fun `it goes in the first cell that has nothing in it`() {
        assertEquals(
            listOf("a", "new", "b", ""),
            addToCells(listOf("a", "", "b", ""), "new")
        )
    }

    @Test
    fun `the first gap, not the last`() {
        assertEquals(
            listOf("new", "", ""),
            addToCells(listOf("", "", ""), "new")
        )
    }

    @Test
    fun `it does not displace anything already there`() {
        val before = listOf("a", "", "b")
        val after = addToCells(before, "new")
        assertEquals(listOf("a", "b"), after.filter { it.isNotEmpty() && it != "new" })
        assertEquals(before.size, after.size)
    }

    @Test
    fun `with every cell taken, one more is added at the end`() {
        assertEquals(
            listOf("a", "b", "c", "new"),
            addToCells(listOf("a", "b", "c"), "new")
        )
    }

    @Test
    fun `a full page's worth of cells puts the app on the next one`() {
        // Four columns, five rows: twenty cells to a page, all taken.
        val full = (1..20).map { "app$it" }
        val after = addToCells(full, "new")
        assertEquals(21, after.size)
        assertEquals("new", after[20])          // the first cell of the page after
        assertTrue(full == after.take(20))      // and nothing before it moved
    }

    @Test
    fun `an empty screen gets the app in its first cell`() {
        assertEquals(listOf("new"), addToCells(emptyList(), "new"))
    }

    @Test
    fun `the app appears exactly once, whatever the screen looked like`() {
        for (before in listOf(
            emptyList(),
            listOf("", ""),
            listOf("a", "", "b"),
            listOf("a", "b", "c")
        )) {
            val after = addToCells(before, "new")
            assertEquals("a screen with $before placed it more than once", 1, after.count { it == "new" })
        }
    }
}
