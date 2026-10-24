package com.orbit.home

import com.orbit.home.overlay.DockEdge
import com.orbit.home.overlay.EdgeDockGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeDockCarryTest {
    private val area = EdgeDockGeometry.Rect(0, 0, 1080, 2400)

    @Test
    fun `past the halfway line the bar crosses to the opposite edge`() {
        assertEquals(DockEdge.LEFT, EdgeDockGeometry.edgeForCarry(DockEdge.RIGHT, area, 400f, 1200f, 60f))
    }

    @Test
    fun `near its own edge the bar stays put`() {
        assertEquals(DockEdge.RIGHT, EdgeDockGeometry.edgeForCarry(DockEdge.RIGHT, area, 1000f, 1200f, 60f))
    }

    @Test
    fun `the middle of the screen does not flip the bar`() {
        assertEquals(DockEdge.RIGHT, EdgeDockGeometry.edgeForCarry(DockEdge.RIGHT, area, 540f, 1200f, 60f))
    }

    @Test
    fun `going straight back needs the extra margin`() {
        // Just inside the margin for the left edge, but not past the sticky extra.
        assertEquals(
            DockEdge.RIGHT,
            EdgeDockGeometry.edgeForCarry(DockEdge.RIGHT, area, 500f, 1200f, 20f, DockEdge.LEFT, 60f)
        )
    }
}
