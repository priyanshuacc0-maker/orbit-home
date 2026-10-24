package com.orbit.home

import com.orbit.home.apps.fitWithin
import com.orbit.home.apps.orientationFix
import com.orbit.home.apps.sampleSizeFor
import com.orbit.home.overlay.DockEdge
import com.orbit.home.ui.settings.CropMath
import com.orbit.home.ui.settings.coverCrop
import com.orbit.home.ui.settings.previewFrameSize
import com.orbit.home.ui.settings.visibleArcStartAngle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The arithmetic that decides whether a preview is the same picture as the result.
 *
 * Each of these is a place where the preview and the saved thing used to be able to disagree without
 * anything crashing: a photograph on its side, a preview cropped differently from the wallpaper, a
 * pinch that slid away from the fingers, a guide for the wrong half of an edge wheel.
 */
class PreviewAccuracyTest {

    @Test
    fun `an upright or unknown orientation changes nothing`() {
        for (value in listOf(0, 1, 9, -3, 255)) {
            assertTrue("orientation $value", orientationFix(value).isIdentity)
        }
    }

    @Test
    fun `the portrait phone orientation is a clockwise quarter turn`() {
        val fix = orientationFix(6)
        assertEquals(90, fix.rotationDegrees)
        assertFalse(fix.mirrored)
        assertTrue(fix.swapsAxes)
        assertEquals(270, orientationFix(8).rotationDegrees)
        assertEquals(180, orientationFix(3).rotationDegrees)
        assertFalse(orientationFix(3).swapsAxes)
    }

    @Test
    fun `mirrored orientations are mirrored and the transposes swap axes`() {
        assertTrue(orientationFix(2).mirrored)
        assertEquals(0, orientationFix(2).rotationDegrees)
        assertTrue(orientationFix(4).mirrored)
        assertEquals(180, orientationFix(4).rotationDegrees)
        assertTrue(orientationFix(5).mirrored && orientationFix(5).swapsAxes)
        assertTrue(orientationFix(7).mirrored && orientationFix(7).swapsAxes)
    }

    @Test
    fun `sample size halves only while the result is still big enough`() {
        // 4000x3000 into a 1000x1000 minimum: 2000x1500, then 1000x750 is too short, so one halving.
        assertEquals(2, sampleSizeFor(4000, 3000) { w, h -> w >= 1000 && h >= 1000 })
        // Already no bigger than needed.
        assertEquals(1, sampleSizeFor(800, 800) { w, h -> w >= 1000 && h >= 1000 })
        // A predicate that always agrees cannot loop for ever, and the answer is a power of two.
        val capped = sampleSizeFor(1 shl 20, 1 shl 20) { _, _ -> true }
        assertEquals(0, capped and (capped - 1))
    }

    @Test
    fun `preview frame keeps the phone's shape and never exceeds the frame`() {
        val (w, h) = previewFrameSize(1080, 2400, 1200)
        assertEquals(1200, h)
        assertEquals(540, w)
        assertEquals(1080 to 1200, previewFrameSize(1080, 1200, 1280))
        assertEquals(1 to 1, previewFrameSize(0, 2400))
    }

    @Test
    fun `the preview crop and the applied crop cut the same part of a picture`() {
        // The preview is rendered at a smaller frame of the same shape. What matters is that the
        // fraction of the picture kept is the same, within the rounding of whole pixels.
        val src = 4032 to 3024
        val full = coverCrop(src.first, src.second, 1080, 2400)
        val (pw, ph) = previewFrameSize(1080, 2400, 1200)
        val small = coverCrop(src.first, src.second, pw, ph)
        assertTrue(abs(full.width - small.width) <= 2)
        assertTrue(abs(full.left - small.left) <= 2)
        assertEquals(full.height, small.height)
    }

    @Test
    fun `a video still keeps the video's shape`() {
        assertEquals(1440 to 810, fitWithin(3840, 2160, 1440))
        assertEquals(810 to 1440, fitWithin(2160, 3840, 1440))
        assertEquals(640 to 480, fitWithin(640, 480, 1440))
        assertEquals(1 to 1440, fitWithin(1, 100000, 1440))
    }

    @Test
    fun `zooming about a point keeps the point under the fingers where it was`() {
        // A picture centred 30,-20 from the frame centre, held at 100,50, scaled up by 1.5.
        val offset = 30f to -20f
        val focus = 100f to 50f
        val ratio = 1.5f
        val moved = CropMath.zoomAbout(offset.first, offset.second, focus.first, focus.second, ratio, 0f, 0f)
        // A point of the picture at screen position `focus` before is at `focus` after: its distance
        // from the picture's centre grew by exactly the ratio.
        val before = focus.first - offset.first
        val after = focus.first - moved.first
        assertEquals(before * ratio, after, 0.001f)
        val beforeY = focus.second - offset.second
        val afterY = focus.second - moved.second
        assertEquals(beforeY * ratio, afterY, 0.001f)
    }

    @Test
    fun `zooming about the frame centre with no zoom only pans`() {
        val moved = CropMath.zoomAbout(10f, 20f, 55f, -5f, 1f, 3f, -4f)
        assertEquals(13f, moved.first, 0.0001f)
        assertEquals(16f, moved.second, 0.0001f)
    }

    @Test
    fun `the visible half of an edge centre faces into the screen`() {
        assertEquals(90f, visibleArcStartAngle(DockEdge.RIGHT), 0f)   // left half
        assertEquals(270f, visibleArcStartAngle(DockEdge.LEFT), 0f)   // right half
        assertEquals(0f, visibleArcStartAngle(DockEdge.TOP), 0f)      // lower half
        assertEquals(180f, visibleArcStartAngle(DockEdge.BOTTOM), 0f) // upper half
    }
}
