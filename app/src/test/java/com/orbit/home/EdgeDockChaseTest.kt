package com.orbit.home

import com.orbit.home.overlay.EdgeDockGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chase the wheel's reveal uses to follow a finger.
 *
 * It has to close the gap without overshooting and without ever leaving the range between where it
 * started and where it is heading, because those two values are what the reveal is drawn from.
 */
class EdgeDockChaseTest {

    private val eps = 0.00001f

    @Test
    fun `a fraction of one lands exactly on the target`() {
        assertEquals(1f, EdgeDockGeometry.chase(0f, 1f, 1f), eps)
        assertEquals(0.25f, EdgeDockGeometry.chase(0.5f, 0.25f, 1f), eps)
    }

    @Test
    fun `a fraction of zero does not move`() {
        assertEquals(0.4f, EdgeDockGeometry.chase(0.4f, 1f, 0f), eps)
    }

    @Test
    fun `it closes the stated share of the gap`() {
        // Half the way from 0 to 1.
        assertEquals(0.5f, EdgeDockGeometry.chase(0f, 1f, 0.5f), eps)
        // Half of what is left.
        assertEquals(0.75f, EdgeDockGeometry.chase(0.5f, 1f, 0.5f), eps)
    }

    @Test
    fun `it works in both directions`() {
        assertEquals(0.5f, EdgeDockGeometry.chase(1f, 0f, 0.5f), eps)
        // Half of the way back down from 0.8 is 0.4, not 0.9: the fraction closes that
        // share of the gap, so the value moves towards zero.
        assertEquals(0.4f, EdgeDockGeometry.chase(0.8f, 0f, 0.5f), eps)
    }

    @Test
    fun `it never overshoots`() {
        var v = 0f
        repeat(50) {
            v = EdgeDockGeometry.chase(v, 1f, 0.45f)
            assertTrue("went past the target: $v", v <= 1f)
            assertTrue("went below the start: $v", v >= 0f)
        }
    }

    @Test
    fun `it approaches the target and stays inside the range`() {
        var v = 0f
        repeat(100) { v = EdgeDockGeometry.chase(v, 1f, 0.45f) }
        // Close enough that the difference cannot be seen on screen.
        assertTrue("should have converged, got $v", 1f - v < 0.001f)
        assertTrue(v <= 1f)
    }

    @Test
    fun `the reveal range is respected when chasing a finger target`() {
        // Whatever the finger does, the drawn value stays within 0..1, which is what
        // travelX expects.
        var v = 0f
        for (target in listOf(0.2f, 0.9f, 0f, 1f, 0.35f)) {
            repeat(20) { v = EdgeDockGeometry.chase(v, target, 0.45f) }
            assertTrue("out of range: $v", v in 0f..1f)
            assertTrue("did not reach $target, got $v", kotlin.math.abs(v - target) < 0.01f)
        }
    }

    @Test
    fun `a fraction outside the range is clamped`() {
        assertEquals(1f, EdgeDockGeometry.chase(0f, 1f, 2f), eps)
        assertEquals(0f, EdgeDockGeometry.chase(0f, 1f, -1f), eps)
    }
}
