package com.orbit.home

import com.orbit.home.overlay.DockEdge
import com.orbit.home.overlay.EdgeDockGeometry
import com.orbit.home.overlay.EdgeDockGeometry.Insets
import com.orbit.home.overlay.EdgeDockGeometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The floating wheel on all four edges.
 *
 * What has to hold on every edge, not just the right one: the bar sits flush against the usable
 * area (never under the status bar or on the navigation buttons), it can reach both ends of its
 * edge without leaving the area, the wheel comes out of the edge it is on, and a drag only hops
 * edges when it is carried into another edge's zone.
 */
class EdgeDockEdgesTest {

    private val screenW = 1080
    private val screenH = 2400
    private val wheel = 630          // 210dp at density 3
    private val half = wheel / 2
    private val handleThickness = 108 // 36dp touch width
    private val handleLength = 120   // 40dp tab length
    private val snapZone = 168f      // 56dp

    private val statusBar = 72
    private val navBar = 144

    /** A portrait phone with a status bar and three-button navigation. */
    private val area = EdgeDockGeometry.usableArea(screenW, screenH, Insets(0, statusBar, 0, navBar))

    private val fractions = listOf(0f, 0.25f, 0.5f, 0.83f, 1f)

    private fun wheelWindow(edge: DockEdge, f: Float = 0.5f) =
        EdgeDockGeometry.wheelWindow(edge, area, wheel, f)

    private fun handleWindow(edge: DockEdge, f: Float = 0.5f) =
        EdgeDockGeometry.handleWindow(edge, area, wheel, handleThickness, handleLength, f)

    /** The wheel's whole square on screen, for a window and how far out the wheel is. */
    private fun square(edge: DockEdge, w: Rect, progress: Float) = Rect(
        x = (w.x + EdgeDockGeometry.wheelOriginX(edge, wheel) +
            EdgeDockGeometry.travelX(edge, progress, wheel)).toInt(),
        y = (w.y + EdgeDockGeometry.wheelOriginY(edge, wheel) +
            EdgeDockGeometry.travelY(edge, progress, wheel)).toInt(),
        width = wheel,
        height = wheel
    )

    private fun insideArea(r: Rect) =
        r.x >= area.x && r.right <= area.right && r.y >= area.y && r.bottom <= area.bottom

    // --- the edge itself ---

    @Test
    fun `the stored numbers never change`() {
        assertEquals(0, DockEdge.LEFT.id)
        assertEquals(1, DockEdge.TOP.id)
        assertEquals(2, DockEdge.RIGHT.id)
        assertEquals(3, DockEdge.BOTTOM.id)
    }

    @Test
    fun `an unknown or missing stored edge falls back to the right`() {
        assertEquals(DockEdge.RIGHT, DockEdge.fromId(null))
        assertEquals(DockEdge.RIGHT, DockEdge.fromId(-1))
        assertEquals(DockEdge.RIGHT, DockEdge.fromId(9))
        for (edge in DockEdge.entries) assertEquals(edge, DockEdge.fromId(edge.id))
    }

    @Test
    fun `the side edges slide up and down and the others slide across`() {
        assertTrue(DockEdge.LEFT.isSide && DockEdge.RIGHT.isSide)
        assertFalse(DockEdge.TOP.isSide || DockEdge.BOTTOM.isSide)
    }

    // --- the usable area ---

    @Test
    fun `the usable area is the screen less the bars`() {
        assertEquals(Rect(0, statusBar, screenW, screenH - statusBar - navBar), area)
    }

    @Test
    fun `insets bigger than the screen leave an empty area, not a negative one`() {
        val r = EdgeDockGeometry.usableArea(100, 100, Insets(80, 80, 80, 80))
        assertTrue(r.width >= 0 && r.height >= 0)
    }

    // --- the windows ---

    @Test
    fun `each edge gets the half of the wheel that faces into the screen`() {
        assertEquals(Rect(765, 849, half, wheel), wheelWindow(DockEdge.RIGHT))
        assertEquals(Rect(0, 849, half, wheel), wheelWindow(DockEdge.LEFT))
        assertEquals(Rect(225, statusBar, wheel, half), wheelWindow(DockEdge.TOP))
        assertEquals(Rect(225, 1941, wheel, half), wheelWindow(DockEdge.BOTTOM))
    }

    @Test
    fun `the handle is flush to its edge of the usable area`() {
        for (f in fractions) {
            assertEquals(area.right, handleWindow(DockEdge.RIGHT, f).right)
            assertEquals(area.x, handleWindow(DockEdge.LEFT, f).x)
            assertEquals(area.y, handleWindow(DockEdge.TOP, f).y)
            assertEquals(area.bottom, handleWindow(DockEdge.BOTTOM, f).bottom)
        }
    }

    @Test
    fun `the handle lies along its edge`() {
        val side = handleWindow(DockEdge.LEFT)
        assertEquals(handleThickness, side.width)
        assertEquals(handleLength, side.height)
        val across = handleWindow(DockEdge.TOP)
        assertEquals(handleLength, across.width)
        assertEquals(handleThickness, across.height)
    }

    @Test
    fun `neither window ever leaves the usable area`() {
        for (edge in DockEdge.entries) for (f in fractions) {
            assertTrue("wheel window, $edge at $f", insideArea(wheelWindow(edge, f)))
            assertTrue("handle window, $edge at $f", insideArea(handleWindow(edge, f)))
        }
    }

    @Test
    fun `the bottom edge stays clear of the navigation buttons and the top clear of the status bar`() {
        for (f in fractions) {
            assertTrue(wheelWindow(DockEdge.BOTTOM, f).bottom <= screenH - navBar)
            assertTrue(handleWindow(DockEdge.BOTTOM, f).bottom <= screenH - navBar)
            assertTrue(wheelWindow(DockEdge.TOP, f).y >= statusBar)
            assertTrue(handleWindow(DockEdge.TOP, f).y >= statusBar)
            // And the side edges do not slide under either.
            assertTrue(wheelWindow(DockEdge.RIGHT, f).bottom <= screenH - navBar)
            assertTrue(wheelWindow(DockEdge.LEFT, f).y >= statusBar)
        }
    }

    @Test
    fun `the bar reaches both ends of every edge`() {
        for (edge in DockEdge.entries) {
            val start = EdgeDockGeometry.centreAlong(edge, area, wheel, 0f)
            val end = EdgeDockGeometry.centreAlong(edge, area, wheel, 1f)
            val length = if (edge.isSide) area.height else area.width
            val origin = if (edge.isSide) area.y else area.x
            assertEquals("$edge start", origin + half.toFloat(), start, 0.5f)
            assertEquals("$edge end", origin + length - half.toFloat(), end, 0.5f)
        }
    }

    @Test
    fun `a position outside the range is clamped on every edge`() {
        for (edge in DockEdge.entries) {
            assertEquals(
                EdgeDockGeometry.centreAlong(edge, area, wheel, 0f),
                EdgeDockGeometry.centreAlong(edge, area, wheel, -4f), 0f
            )
            assertEquals(
                EdgeDockGeometry.centreAlong(edge, area, wheel, 1f),
                EdgeDockGeometry.centreAlong(edge, area, wheel, 9f), 0f
            )
        }
    }

    @Test
    fun `centre and fraction are inverses on every edge`() {
        for (edge in DockEdge.entries) for (i in 0..10) {
            val f = i / 10f
            val c = EdgeDockGeometry.centreAlong(edge, area, wheel, f)
            assertEquals("$edge", f, EdgeDockGeometry.fractionForCentreAlong(c, edge, area, wheel), 0.001f)
        }
    }

    @Test
    fun `an edge shorter than the wheel does not produce an empty window`() {
        val narrow = EdgeDockGeometry.usableArea(400, 1080, Insets.NONE)
        val w = EdgeDockGeometry.wheelWindow(DockEdge.TOP, narrow, wheel, 0.5f)
        val h = EdgeDockGeometry.handleWindow(DockEdge.TOP, narrow, wheel, handleThickness, handleLength, 0.5f)
        assertTrue(w.width > 0 && w.height > 0)
        assertTrue(h.width > 0 && h.height > 0)
    }

    @Test
    fun `a bar on the right docks beside a navigation bar at the side`() {
        // A phone on its side: the buttons are at the right.
        val landscape = EdgeDockGeometry.usableArea(2400, 1080, Insets(0, statusBar, navBar, 0))
        val w = EdgeDockGeometry.wheelWindow(DockEdge.RIGHT, landscape, wheel, 0.5f)
        assertEquals(2400 - navBar, w.right)
    }

    // --- the wheel inside its window ---

    @Test
    fun `fully out, the wheel's centre is exactly on the edge it is docked to`() {
        for (f in fractions) {
            val r = square(DockEdge.RIGHT, wheelWindow(DockEdge.RIGHT, f), 1f)
            assertEquals(area.right, r.x + half)
            val l = square(DockEdge.LEFT, wheelWindow(DockEdge.LEFT, f), 1f)
            assertEquals(area.x, l.x + half)
            val t = square(DockEdge.TOP, wheelWindow(DockEdge.TOP, f), 1f)
            assertEquals(area.y, t.y + half)
            val b = square(DockEdge.BOTTOM, wheelWindow(DockEdge.BOTTOM, f), 1f)
            assertEquals(area.bottom, b.y + half)
        }
    }

    @Test
    fun `fully out, the wheel fills its window`() {
        for (edge in DockEdge.entries) for (f in fractions) {
            val w = wheelWindow(edge, f)
            val s = square(edge, w, 1f)
            assertTrue(
                "$edge at $f",
                s.x <= w.x && s.y <= w.y && s.right >= w.right && s.bottom >= w.bottom
            )
        }
    }

    @Test
    fun `docked, none of the wheel is inside its window`() {
        for (edge in DockEdge.entries) for (f in fractions) {
            val w = wheelWindow(edge, f)
            val s = square(edge, w, 0f)
            val clear = when (edge) {
                DockEdge.RIGHT -> s.x >= w.right
                DockEdge.LEFT -> s.right <= w.x
                DockEdge.TOP -> s.bottom <= w.y
                DockEdge.BOTTOM -> s.y >= w.bottom
            }
            assertTrue("$edge at $f", clear)
        }
    }

    @Test
    fun `the wheel only travels across its edge, never along it`() {
        for (p in listOf(0f, 0.3f, 1f)) {
            assertEquals(0f, EdgeDockGeometry.travelY(DockEdge.LEFT, p, wheel), 0f)
            assertEquals(0f, EdgeDockGeometry.travelY(DockEdge.RIGHT, p, wheel), 0f)
            assertEquals(0f, EdgeDockGeometry.travelX(DockEdge.TOP, p, wheel), 0f)
            assertEquals(0f, EdgeDockGeometry.travelX(DockEdge.BOTTOM, p, wheel), 0f)
        }
    }

    @Test
    fun `the wheel leaves towards the outside on every edge`() {
        assertTrue(EdgeDockGeometry.travelX(DockEdge.RIGHT, 0f, wheel) > 0f)
        assertTrue(EdgeDockGeometry.travelX(DockEdge.LEFT, 0f, wheel) < 0f)
        assertTrue(EdgeDockGeometry.travelY(DockEdge.BOTTOM, 0f, wheel) > 0f)
        assertTrue(EdgeDockGeometry.travelY(DockEdge.TOP, 0f, wheel) < 0f)
    }

    @Test
    fun `the travel is the same size on every edge`() {
        for (p in listOf(0f, 0.25f, 0.5f, 1f)) {
            val expected = EdgeDockGeometry.travelPx(p, wheel)
            assertEquals(expected, EdgeDockGeometry.travelX(DockEdge.RIGHT, p, wheel), 0f)
            assertEquals(expected, -EdgeDockGeometry.travelX(DockEdge.LEFT, p, wheel), 0f)
            assertEquals(expected, EdgeDockGeometry.travelY(DockEdge.BOTTOM, p, wheel), 0f)
            assertEquals(expected, -EdgeDockGeometry.travelY(DockEdge.TOP, p, wheel), 0f)
        }
    }

    @Test
    fun `the focus slot rests in the middle of the visible half`() {
        // 0 degrees is the top and angles run clockwise; the wheel subtracts 90 to get screen space.
        val inward = mapOf(
            DockEdge.RIGHT to (-1f to 0f),
            DockEdge.LEFT to (1f to 0f),
            DockEdge.TOP to (0f to 1f),
            DockEdge.BOTTOM to (0f to -1f)
        )
        for ((edge, want) in inward) {
            val angle = Math.toRadians((EdgeDockGeometry.focusAngleDegrees(edge) - 90f).toDouble())
            assertEquals("$edge x", want.first, cos(angle).toFloat(), 0.001f)
            assertEquals("$edge y", want.second, sin(angle).toFloat(), 0.001f)
        }
    }

    // --- dragging between edges ---

    @Test
    fun `the right edge's old behaviour is unchanged`() {
        val whole = Rect(0, 0, screenW, screenH)
        for (f in fractions) {
            assertEquals(
                EdgeDockGeometry.wheelWindow(DockEdge.RIGHT, whole, wheel, f),
                EdgeDockGeometry.wheelWindow(screenW, screenH, wheel, f)
            )
            assertEquals(
                EdgeDockGeometry.handleWindow(DockEdge.RIGHT, whole, wheel, 84, 120, f),
                EdgeDockGeometry.handleWindow(screenW, screenH, wheel, 84, 120, f)
            )
        }
    }

    @Test
    fun `a finger in the middle of the screen leaves the bar where it is`() {
        for (edge in DockEdge.entries) {
            assertEquals(edge, EdgeDockGeometry.edgeForDrag(edge, area, 540f, 1200f, snapZone))
        }
    }

    @Test
    fun `a finger carried into another edge's zone moves the bar there`() {
        assertEquals(DockEdge.LEFT, EdgeDockGeometry.edgeForDrag(DockEdge.RIGHT, area, 100f, 1200f, snapZone))
        assertEquals(DockEdge.TOP, EdgeDockGeometry.edgeForDrag(DockEdge.RIGHT, area, 540f, statusBar + 100f, snapZone))
        assertEquals(DockEdge.BOTTOM, EdgeDockGeometry.edgeForDrag(DockEdge.LEFT, area, 540f, area.bottom - 50f, snapZone))
        assertEquals(DockEdge.RIGHT, EdgeDockGeometry.edgeForDrag(DockEdge.TOP, area, screenW - 60f, 1200f, snapZone))
    }

    @Test
    fun `the nearest edge wins in a corner`() {
        // 100px from the right edge, 40px from the top: the top is nearer.
        assertEquals(
            DockEdge.TOP,
            EdgeDockGeometry.edgeForDrag(DockEdge.RIGHT, area, screenW - 100f, statusBar + 40f, snapZone)
        )
    }

    @Test
    fun `the margin applies only to the edge just left`() {
        // A bar on the right edge, the finger 50px from that edge and 40px from the top.
        val x = screenW - 50f
        val y = statusBar + 40f
        // A fresh hop needs no margin: the top is nearer, so it wins.
        assertEquals(
            DockEdge.TOP,
            EdgeDockGeometry.edgeForDrag(DockEdge.RIGHT, area, x, y, snapZone, 16f, null)
        )
        // Coming straight back to the edge just left needs the margin, which 10px does not give.
        assertEquals(
            DockEdge.RIGHT,
            EdgeDockGeometry.edgeForDrag(DockEdge.RIGHT, area, x, y, snapZone, 16f, DockEdge.TOP)
        )
        // Well clear of it, the margin is met.
        assertEquals(
            DockEdge.TOP,
            EdgeDockGeometry.edgeForDrag(DockEdge.RIGHT, area, screenW - 100f, y, snapZone, 16f, DockEdge.TOP)
        )
    }

    @Test
    fun `a tie stays on the current edge`() {
        assertEquals(
            DockEdge.RIGHT,
            EdgeDockGeometry.edgeForDrag(DockEdge.RIGHT, area, screenW - 50f, statusBar + 50f, snapZone)
        )
    }

    @Test
    fun `sliding to the end of an edge does not hop off it`() {
        // The bar stops with the whole wheel inside the area, which leaves the finger half a wheel
        // from the end. The snap zone has to be smaller than that or every slide to the end would
        // carry the bar onto the next edge.
        assertTrue(snapZone < half)
        val fingerOnEdge = 20f
        for (edge in DockEdge.entries) for (f in listOf(0f, 1f)) {
            val c = EdgeDockGeometry.centreAlong(edge, area, wheel, f)
            val (x, y) = when (edge) {
                DockEdge.RIGHT -> (area.right - fingerOnEdge) to c
                DockEdge.LEFT -> (area.x + fingerOnEdge) to c
                DockEdge.TOP -> c to (area.y + fingerOnEdge)
                DockEdge.BOTTOM -> c to (area.bottom - fingerOnEdge)
            }
            assertEquals("$edge at $f", edge, EdgeDockGeometry.edgeForDrag(edge, area, x, y, snapZone))
        }
    }

    @Test
    fun `a finger past the screen edge still counts as that edge`() {
        assertTrue(EdgeDockGeometry.distanceToEdge(DockEdge.LEFT, area, -10f, 500f) < 0f)
        assertEquals(DockEdge.LEFT, EdgeDockGeometry.edgeForDrag(DockEdge.RIGHT, area, -10f, 500f, snapZone))
    }

    @Test
    fun `distance is measured from the usable area, not the raw screen`() {
        assertEquals(40f, EdgeDockGeometry.distanceToEdge(DockEdge.TOP, area, 500f, statusBar + 40f), 0f)
        assertEquals(40f, EdgeDockGeometry.distanceToEdge(DockEdge.BOTTOM, area, 500f, area.bottom - 40f), 0f)
        assertTrue(abs(EdgeDockGeometry.distanceToEdge(DockEdge.RIGHT, area, screenW - 25f, 0f) - 25f) < 0.001f)
    }
}
