package com.orbit.home

import com.orbit.home.ui.settings.coverCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cutting a picture to the shape of the screen.
 *
 * This is where the wallpaper either reaches the edges or does not, and getting it wrong is silent:
 * a crop that is one step short still looks like a wallpaper, just one with a strip down the side.
 */
class WallpaperFramingTest {

    private val screenW = 1080
    private val screenH = 2400

    @Test
    fun `a picture wider than the frame keeps its full height and loses both sides equally`() {
        // 4000x2000 is much wider than 1080x2400, so the sides go.
        val crop = coverCrop(4000, 2000, screenW, screenH)
        assertEquals(0, crop.top)
        assertEquals(2000, crop.height)
        assertEquals("the sides came off unequally", 4000 - crop.width, crop.left * 2)
    }

    @Test
    fun `a picture taller than the frame keeps its full width and loses top and bottom equally`() {
        val crop = coverCrop(1080, 4000, screenW, screenH)
        assertEquals(0, crop.left)
        assertEquals(1080, crop.width)
        assertEquals("the top and bottom came off unequally", 4000 - crop.height, crop.top * 2)
    }

    @Test
    fun `a picture already the frame's shape is kept whole`() {
        val crop = coverCrop(1080, 2400, screenW, screenH)
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(1080, crop.width)
        assertEquals(2400, crop.height)
    }

    @Test
    fun `the result always has the frame's shape, whatever the picture's`() {
        val frameRatio = screenW.toDouble() / screenH
        for (width in listOf(100, 1080, 4000, 12000)) {
            for (height in listOf(100, 1080, 2400, 9000)) {
                val crop = coverCrop(width, height, screenW, screenH)
                val ratio = crop.width.toDouble() / crop.height
                assertTrue(
                    "a ${width}x$height picture came out at $ratio, not $frameRatio",
                    kotlin.math.abs(ratio - frameRatio) < 0.01
                )
            }
        }
    }

    @Test
    fun `a tiny picture comes out as close to the frame's shape as whole pixels allow`() {
        // A one-pixel-wide picture cannot be cut to an exact ratio, because the crop is a whole number
        // of pixels. What it must be is within one pixel of right, either way.
        val frameRatio = screenW.toDouble() / screenH
        for (width in listOf(1, 2, 7, 13)) {
            for (height in listOf(1, 3, 11, 97)) {
                val crop = coverCrop(width, height, screenW, screenH)
                val ratio = crop.width.toDouble() / crop.height
                val slack = maxOf(1.0 / crop.width, 1.0 / crop.height)
                assertTrue(
                    "a ${width}x$height picture came out at $ratio, not $frameRatio",
                    kotlin.math.abs(ratio - frameRatio) <= slack
                )
            }
        }
    }

    @Test
    fun `the crop never reaches outside the picture`() {
        for (width in listOf(1, 7, 1080, 3333)) {
            for (height in listOf(1, 11, 2400, 4444)) {
                val crop = coverCrop(width, height, screenW, screenH)
                assertTrue("a $width x $height picture was cut past its left", crop.left >= 0)
                assertTrue("a $width x $height picture was cut past its top", crop.top >= 0)
                assertTrue("a $width x $height picture was cut too wide", crop.left + crop.width <= width)
                assertTrue("a $width x $height picture was cut too tall", crop.top + crop.height <= height)
                assertTrue(crop.width > 0 && crop.height > 0)
            }
        }
    }

    @Test
    fun `a picture narrower than the frame still ends up filling it`() {
        // 500x2000 is narrower than the screen. Covering means keeping the width and trimming the
        // height, never padding the sides - a wallpaper with bars down it is not a wallpaper.
        val crop = coverCrop(500, 2000, screenW, screenH)
        assertEquals(0, crop.left)
        assertEquals(500, crop.width)
        assertTrue(crop.height < 2000)
    }

    @Test
    fun `a degenerate size is returned rather than divided by`() {
        assertEquals(0, coverCrop(0, 100, screenW, screenH).width)
        assertEquals(0, coverCrop(100, 0, screenW, screenH).height)
        assertEquals(100, coverCrop(100, 100, 0, screenH).width)
        assertEquals(100, coverCrop(100, 100, screenW, 0).height)
    }
}
