package com.orbit.home

import com.orbit.home.ui.DrawerIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerIndexTest {

    private val labels = listOf("1Password", "Amazon", "Authy", "Banking", "Calendar", "Camera", "Zoom")
    private val sections = DrawerIndex.sections(labels) { it }

    @Test
    fun groupsByLetterInOrder() {
        assertEquals(listOf("#", "A", "B", "C", "Z"), sections.map { it.key })
        assertEquals(listOf("Amazon", "Authy"), sections[1].items)
    }

    @Test
    fun mergesARunThatTheCollatorSplit() {
        val split = DrawerIndex.sections(listOf("-a", "Alpha", "_b", "Beta")) { it }
        assertEquals(listOf("#", "A", "B"), split.map { it.key })
        assertEquals(listOf("-a", "_b"), split[0].items)
    }

    @Test
    fun gridIndexCountsAppsBeforeTheSection() {
        assertEquals(0, DrawerIndex.gridIndex(sections, "#"))
        assertEquals(1, DrawerIndex.gridIndex(sections, "A"))
        assertEquals(4, DrawerIndex.gridIndex(sections, "C"))
        assertEquals(6, DrawerIndex.gridIndex(sections, "Z"))
    }

    @Test
    fun listIndexCountsHeadersToo() {
        assertEquals(0, DrawerIndex.listIndex(sections, "#"))
        assertEquals(2, DrawerIndex.listIndex(sections, "A"))
        assertEquals(7, DrawerIndex.listIndex(sections, "C"))
        assertEquals(10, DrawerIndex.listIndex(sections, "Z"))
    }

    @Test
    fun anUnknownKeyScrollsToTheTop() {
        assertEquals(0, DrawerIndex.gridIndex(sections, "Q"))
        assertEquals(0, DrawerIndex.listIndex(sections, "Q"))
    }

    @Test
    fun slotAtClampsToTheRail() {
        assertEquals(-1, DrawerIndex.slotAt(0, 0.5f))
        assertEquals(0, DrawerIndex.slotAt(5, -0.3f))
        assertEquals(2, DrawerIndex.slotAt(5, 0.5f))
        assertEquals(4, DrawerIndex.slotAt(5, 1.4f))
    }
}
