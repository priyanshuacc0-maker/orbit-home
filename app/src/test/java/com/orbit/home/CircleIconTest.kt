package com.orbit.home

import com.orbit.home.apps.CircleIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry that makes every icon the same circle.
 *
 * The renderer itself needs a canvas, but the arithmetic that places the artwork does not, and it
 * is the part that is easy to get wrong. Two mistakes it guards against: placing an adaptive icon's
 * layers by eye, when they live on a 108-unit canvas whose visible middle is only 72; and growing
 * measured artwork without a limit, which turns a tiny glyph into a blurry one.
 */
class CircleIconTest {

    @Test
    fun `the adaptive canvas keeps the platform's 108 to 72`() {
        assertEquals(150, CircleIcon.adaptiveCanvasPx(100))
        assertEquals(72, CircleIcon.adaptiveCanvasPx(48))
        assertEquals(288, CircleIcon.adaptiveCanvasPx(192))
    }

    @Test
    fun `the canvas is centred, so the visible middle lands on the circle`() {
        for (px in listOf(48, 96, 144, 192, 256)) {
            val canvas = CircleIcon.adaptiveCanvasPx(px)
            val origin = CircleIcon.adaptiveOriginPx(px)
            assertEquals("a $px circle is not centred in its $canvas canvas", 0, origin * 2 + canvas - px)
        }
    }

    @Test
    fun `the visible middle of the canvas is exactly the circle`() {
        for (px in listOf(48, 96, 144, 192, 256)) {
            val canvas = CircleIcon.adaptiveCanvasPx(px)
            val visible = canvas * CircleIcon.ADAPTIVE_VISIBLE / CircleIcon.ADAPTIVE_CANVAS
            assertEquals("the visible area of a $px circle came out $visible", px.toFloat(), visible, 1f)
        }
    }

    @Test
    fun `artwork already at the common size is left exactly as it is`() {
        val atSize = 100f * CircleIcon.ART_DIAGONAL
        assertEquals(1f, CircleIcon.growthFor(atSize, 100), 0.001f)
    }

    @Test
    fun `artwork larger than the circle is never shrunk`() {
        // A full-bleed square has a diagonal well past the target, and shrinking the icons that
        // already look right is fixing the wrong end.
        assertEquals(1f, CircleIcon.growthFor(141f, 100), 0.001f)
        assertEquals(1f, CircleIcon.growthFor(400f, 100), 0.001f)
    }

    @Test
    fun `padded artwork is grown to match`() {
        // Half the target diagonal, so twice the size brings it into line.
        val half = 100f * CircleIcon.ART_DIAGONAL / 2f
        assertEquals(2f, CircleIcon.growthFor(half, 100), 0.001f)
    }

    @Test
    fun `growth is capped, so a tiny glyph is not stretched into a blur`() {
        assertEquals(CircleIcon.MAX_GROWTH, CircleIcon.growthFor(4f, 100), 0.001f)
        assertTrue("the cap is too generous", CircleIcon.MAX_GROWTH <= 2.5f)
        assertTrue("the cap would leave small icons small", CircleIcon.MAX_GROWTH >= 1.5f)
    }

    @Test
    fun `the measurement uses both sides, so a wide icon is not read as small`() {
        assertTrue(CircleIcon.contentDiagonalPx(80, 20) > 80f)
        assertEquals(
            CircleIcon.contentDiagonalPx(30, 40),
            CircleIcon.contentDiagonalPx(40, 30),
            0.001f
        )
    }

    @Test
    fun `nothing measured means nothing changed`() {
        assertEquals(1f, CircleIcon.growthFor(0f, 100), 0.001f)
        assertEquals(1f, CircleIcon.growthFor(-5f, 100), 0.001f)
        assertEquals(1f, CircleIcon.growthFor(50f, 0), 0.001f)
        assertEquals(0f, CircleIcon.contentDiagonalPx(0, 40), 0.001f)
        assertEquals(0f, CircleIcon.contentDiagonalPx(40, 0), 0.001f)
    }

    @Test
    fun `nothing is drawn from nothing`() {
        assertEquals(null, CircleIcon.render(null, 96))
        assertEquals(null, CircleIcon.render(null, 0))
    }
}
