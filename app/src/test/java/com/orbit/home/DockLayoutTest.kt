package com.orbit.home

import com.orbit.home.apps.DockLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dock's slot arithmetic and the rules for dropping an app on it. */
class DockLayoutTest {

    // 4 slots, 100px icons, 600px wide: gap = (600 - 400) / 5 = 40.
    private val width = 600f
    private val icon = 100f

    @Test
    fun `slots are evenly spaced with an equal gap at both ends`() {
        assertEquals(40f, DockLayout.slotLeft(0, 4, width, icon), 0.001f)
        assertEquals(180f, DockLayout.slotLeft(1, 4, width, icon), 0.001f)
        // Last icon ends one gap short of the right edge.
        assertEquals(width - 40f, DockLayout.slotLeft(3, 4, width, icon) + icon, 0.001f)
    }

    @Test
    fun `nearest slot follows the finger and is clamped to the row`() {
        assertEquals(0, DockLayout.nearestSlot(-500f, 4, width, icon))
        assertEquals(0, DockLayout.nearestSlot(90f, 4, width, icon))
        assertEquals(1, DockLayout.nearestSlot(230f, 4, width, icon))
        assertEquals(3, DockLayout.nearestSlot(5000f, 4, width, icon))
    }

    @Test
    fun `insertion index runs from the front to one past the end`() {
        // 3 apps on the dock -> 4 slots to drop into.
        assertEquals(0, DockLayout.insertionIndex(0f, 3, width, icon))
        assertEquals(3, DockLayout.insertionIndex(width, 3, width, icon))
    }

    @Test
    fun `a hole pushes everything at or after it one slot along`() {
        assertEquals(0, DockLayout.slotFor(0, null))
        assertEquals(0, DockLayout.slotFor(0, 1))
        assertEquals(2, DockLayout.slotFor(1, 1))
        assertEquals(3, DockLayout.slotFor(2, 1))
    }

    @Test
    fun `insert puts the app where it was dropped`() {
        assertEquals(listOf("a", "x", "b"), DockLayout.insert(listOf("a", "b"), "x", 1, 5))
        assertEquals(listOf("x", "a", "b"), DockLayout.insert(listOf("a", "b"), "x", -3, 5))
        assertEquals(listOf("a", "b", "x"), DockLayout.insert(listOf("a", "b"), "x", 99, 5))
    }

    @Test
    fun `a full dock or an app already on it refuses`() {
        val full = listOf("a", "b", "c", "d", "e")
        assertFalse(DockLayout.accepts(full, "x", 5))
        assertNull(DockLayout.insert(full, "x", 0, 5))
        assertFalse(DockLayout.accepts(listOf("a"), "a", 5))
        assertNull(DockLayout.insert(listOf("a"), "a", 0, 5))
        assertTrue(DockLayout.accepts(listOf("a"), "b", 5))
    }
}
