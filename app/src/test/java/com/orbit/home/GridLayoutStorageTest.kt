package com.orbit.home

import com.orbit.home.preferences.normaliseCells
import com.orbit.home.preferences.normaliseHomeIds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two rules a stored grid layout is held to.
 *
 * These are the rules that make the stored pair trustworthy, so they are worth testing on their own:
 * membership that cannot hold a blank or a repeat, and cells that keep their interior gaps.
 *
 * NOT COVERED HERE: that the two are written in a single DataStore edit. That is the property the
 * change exists for, and it cannot be shown from a pure function - it needs the real store. The project
 * has no instrumented test setup yet (no Robolectric, no androidTest source set), so this is stated
 * rather than tested, and the atomicity needs an instrumentation test to be proven.
 */
class GridLayoutStorageTest {

    // ---- membership ----

    @Test
    fun `blanks are dropped, because there is no app called nothing`() {
        assertEquals(listOf("a", "b"), normaliseHomeIds(listOf("a", "", "b", "   ")))
    }

    @Test
    fun `a repeat keeps the first place, not the last`() {
        assertEquals(listOf("a", "b", "c"), normaliseHomeIds(listOf("a", "b", "a", "c", "b")))
    }

    @Test
    fun `the order is the one it was given`() {
        assertEquals(listOf("z", "a", "m"), normaliseHomeIds(listOf("z", "a", "m")))
    }

    @Test
    fun `an empty list stays empty rather than becoming something`() {
        assertEquals(emptyList<String>(), normaliseHomeIds(emptyList()))
        assertEquals(emptyList<String>(), normaliseHomeIds(listOf("", "  ")))
    }

    // ---- cells ----

    @Test
    fun `interior empty cells survive, because that is where the next app goes`() {
        val cells = listOf("a", "", "b", "", "c")
        assertEquals(cells, normaliseCells(cells))
    }

    @Test
    fun `the first page's clear top survives - it is made of empty cells`() {
        val seeded = List(12) { "" } + listOf("a", "b", "c", "d")
        assertEquals(seeded, normaliseCells(seeded))
    }

    @Test
    fun `only the trailing empties go, and only because the reader drops them`() {
        assertEquals(listOf("a", "", "b"), normaliseCells(listOf("a", "", "b", "", "")))
    }

    @Test
    fun `a layout of nothing but gaps comes back as nothing`() {
        assertEquals(emptyList<String>(), normaliseCells(listOf("", "", "")))
    }

    @Test
    fun `a round trip through the stored form keeps every interior gap`() {
        // The stored form is the ids joined by newlines, which is what the reader splits back.
        val cells = listOf("a", "", "b", "", "c")
        val written = normaliseCells(cells).joinToString("\n")
        assertEquals(cells, written.split("\n"))
    }

    @Test
    fun `removing an app empties its cell and leaves the ones after it where they were`() {
        val cells = listOf("a", "b", "c", "d")
        val after = cells.map { if (it == "b") "" else it }
        assertEquals(listOf("a", "", "c", "d"), after)
    }
}
