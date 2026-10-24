package com.orbit.home

import com.orbit.home.overlay.EdgeDockGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The floating wheel's two windows and how far the wheel is pushed out of the wheel
 * window.
 *
 * The property that matters most is structural rather than numeric: **neither window's
 * bounds depend on the pull progress**. Android animates a window whose bounds change,
 * and that animation stacks on ours, so opening and closing may only change the wheel's
 * travel inside a window that stays put. The window functions take no progress, and
 * these tests check what the travel does with it.
 */
class EdgeDockGeometryTest {

    private val screenW = 1080
    private val screenH = 2400
    private val wheel = 630      // 210dp at density 3
    private val handleW = 84     // 28dp touch width
    private val handleH = 120    // 40dp tab height

    private fun wheelWindow(fraction: Float = 0.5f) =
        EdgeDockGeometry.wheelWindow(screenW, screenH, wheel, fraction)

    private fun handleWindow(fraction: Float = 0.5f) =
        EdgeDockGeometry.handleWindow(screenW, screenH, wheel, handleW, handleH, fraction)

    /** Where the wheel's centre is on screen: window origin + half the wheel + travel. */
    private fun centreX(progress: Float) =
        wheelWindow().x + wheel / 2f + EdgeDockGeometry.travelX(progress, wheel)

    // --- the wheel window ---

    @Test
    fun `the wheel window is the left half of the wheel, flush to the right edge`() {
        val w = wheelWindow()
        assertEquals(screenW, w.right)
        assertEquals(wheel / 2, w.width)
        assertEquals(wheel, w.height)
    }

    @Test
    fun `out puts the wheel's centre exactly on the screen edge`() {
        assertEquals(screenW.toFloat(), centreX(1f), 0f)
    }

    @Test
    fun `out leaves the wheel at the window's origin, with no travel`() {
        assertEquals(0f, EdgeDockGeometry.travelX(1f, wheel), 0f)
    }

    @Test
    fun `docked, none of the wheel is inside the window`() {
        // Travel equals the window's width, so the wheel's left edge is the window's
        // right edge, which is the screen edge.
        val w = wheelWindow()
        assertEquals(w.width.toFloat(), EdgeDockGeometry.travelX(0f, wheel), 0f)
        assertEquals(screenW.toFloat(), w.x + EdgeDockGeometry.travelX(0f, wheel), 0f)
    }

    @Test
    fun `the travel moves steadily from docked to out`() {
        val step = EdgeDockGeometry.travelX(0f, wheel) - EdgeDockGeometry.travelX(0.1f, wheel)
        for (i in 0 until 10) {
            val a = EdgeDockGeometry.travelX(i / 10f, wheel)
            val b = EdgeDockGeometry.travelX((i + 1) / 10f, wheel)
            assertEquals("uneven step at $i", step, a - b, 0.01f)
        }
    }

    @Test
    fun `the wheel never moves back at frame-sized steps`() {
        var last = Float.MAX_VALUE
        for (i in 0..60) {
            val x = EdgeDockGeometry.travelX(i / 60f, wheel)
            assertTrue("reversed at step $i", x <= last)
            last = x
        }
    }

    @Test
    fun `progress outside nought to one is clamped`() {
        assertEquals(EdgeDockGeometry.travelX(0f, wheel), EdgeDockGeometry.travelX(-3f, wheel), 0f)
        assertEquals(EdgeDockGeometry.travelX(1f, wheel), EdgeDockGeometry.travelX(7f, wheel), 0f)
    }

    // --- the handle window ---

    @Test
    fun `the handle window is wide enough to hit and flush to the edge`() {
        val h = handleWindow()
        assertEquals(screenW, h.right)
        assertEquals(handleW, h.width)
        assertEquals(handleH, h.height)
    }

    @Test
    fun `the handle and the wheel share a vertical centre at every position`() {
        for (i in 0..20) {
            val f = i / 20f
            val w = wheelWindow(f)
            val h = handleWindow(f)
            val wheelCentre = w.y + w.height / 2f
            val handleCentre = h.y + h.height / 2f
            assertTrue("centres differ at $f", abs(wheelCentre - handleCentre) <= 1f)
        }
    }

    @Test
    fun `the handle window takes far less of the screen than the wheel window`() {
        val h = handleWindow()
        val w = wheelWindow()
        assertTrue(h.width * h.height * 10 < w.width * w.height)
    }

    // --- vertical position ---

    @Test
    fun `the wheel starts in the middle of its travel`() {
        val centre = wheelWindow(0.5f).y + wheel / 2f
        assertEquals(screenH / 2f, centre, 1f)
    }

    @Test
    fun `the bar can reach both ends of its travel`() {
        // The travel is absolute: 0 at the top of the screen and 1 at the bottom. It
        // used to be the default plus or minus a margin, so moving the default moved
        // the window with it and the bar could only reach part of the screen.
        val top = EdgeDockGeometry.centreY(screenH, wheel, 0f)
        val bottom = EdgeDockGeometry.centreY(screenH, wheel, 1f)
        assertEquals(wheel / 2f, top, 1f)
        assertEquals(screenH - wheel / 2f, bottom, 1f)
        assertTrue("the two ends must be far apart", bottom - top > screenH * 0.5f)
    }

    @Test
    fun `the bar stays wholly on screen at both ends`() {
        for (fraction in listOf(0f, 0.5f, 1f)) {
            val centre = EdgeDockGeometry.centreY(screenH, wheel, fraction)
            assertTrue("the wheel runs off the top at $fraction", centre - wheel / 2f >= 0f)
            assertTrue("the wheel runs off the bottom at $fraction", centre + wheel / 2f <= screenH)
        }
    }

    @Test
    fun `a position outside the range is clamped, not discarded`() {
        assertEquals(wheel / 2f, EdgeDockGeometry.centreY(screenH, wheel, -3f), 1f)
        assertEquals(screenH - wheel / 2f, EdgeDockGeometry.centreY(screenH, wheel, 4f), 1f)
    }

    @Test
    fun `the vertical position is clamped to the screen`() {
        val top = wheelWindow(-5f)
        val bottom = wheelWindow(5f)
        assertEquals(0, top.y)
        assertEquals(screenH, bottom.bottom)
    }

    @Test
    fun `centre and fraction are inverses`() {
        for (i in 0..10) {
            val f = i / 10f
            val c = EdgeDockGeometry.centreY(screenH, wheel, f)
            assertEquals(f, EdgeDockGeometry.fractionForCentre(c, screenH, wheel), 0.001f)
        }
    }

    @Test
    fun `a screen shorter than the wheel does not produce a negative window`() {
        val w = EdgeDockGeometry.wheelWindow(1080, 400, wheel, 0.5f)
        val h = EdgeDockGeometry.handleWindow(1080, 400, wheel, handleW, handleH, 0.5f)
        assertTrue(w.width > 0 && w.height > 0)
        assertTrue(h.width > 0 && h.height > 0)
    }

    // --- pixel rounding ---

    @Test
    fun `an odd size is made even, so half the wheel is a whole number of pixels`() {
        assertEquals(630, EdgeDockGeometry.evenSize(631))
        assertEquals(630, EdgeDockGeometry.evenSize(630))
        for (size in 600..700) {
            val even = EdgeDockGeometry.evenSize(size)
            assertEquals(0, even % 2)
            assertEquals(even / 2f, EdgeDockGeometry.halfWheelPx(even).toFloat(), 0f)
        }
    }

    @Test
    fun `the centre is exactly on the edge for every even wheel size`() {
        for (size in 600..700 step 2) {
            val w = EdgeDockGeometry.wheelWindow(screenW, screenH, size, 0.5f)
            val centre = w.x + size / 2f + EdgeDockGeometry.travelX(1f, size)
            assertEquals("size $size", screenW.toFloat(), centre, 0f)
        }
    }
}
