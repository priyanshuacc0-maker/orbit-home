package com.orbit.home

import com.orbit.home.ui.settings.CropMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic of framing a picture.
 *
 * Worth testing rather than eyeballing, because every mistake it can make is silent: a scale the
 * frame does not offer, or an offset past the room it has, shows up as a gap at an edge or a
 * picture that has slid out of sight — neither of which says anything about why.
 */
class CentreCropTest {

    private val frame = 300f

    @Test
    fun `a square picture covers the frame at one scale and fits at the same one`() {
        assertEquals(0.5f, CropMath.coverScale(600, 600, frame), 0.0001f)
        assertEquals(0.5f, CropMath.fitScale(600, 600, frame), 0.0001f)
    }

    @Test
    fun `covering and fitting are the two sides of a wide picture`() {
        // 1200x600 in a square frame: the height decides covering, the width decides fitting.
        assertEquals(0.5f, CropMath.coverScale(1200, 600, frame), 0.0001f)
        assertEquals(0.25f, CropMath.fitScale(1200, 600, frame), 0.0001f)
    }

    @Test
    fun `a tall picture is bound by the other side`() {
        assertEquals(0.5f, CropMath.coverScale(600, 1200, frame), 0.0001f)
        assertEquals(0.25f, CropMath.fitScale(600, 1200, frame), 0.0001f)
    }

    @Test
    fun `the floor is below fitting, so the whole picture can be seen with room around it`() {
        val fit = CropMath.fitScale(1200, 600, frame)
        val floor = CropMath.floorScale(1200, 600, frame)
        assertTrue("the picture cannot be made smaller than fitting", floor < fit)
        assertEquals(fit * CropMath.MIN_ZOOM, floor, 0.0001f)
    }

    @Test
    fun `a scale the frame does not offer is brought back to one it does`() {
        val floor = CropMath.floorScale(1200, 600, frame)
        val ceiling = CropMath.coverScale(1200, 600, frame) * 6f
        assertEquals(floor, CropMath.clampScale(0.0001f, floor, ceiling), 0.0001f)
        assertEquals(ceiling, CropMath.clampScale(999f, floor, ceiling), 0.0001f)
        assertEquals(1f, CropMath.clampScale(1f, floor, ceiling), 0.0001f)
        assertEquals(floor, CropMath.clampScale(Float.NaN, floor, ceiling), 0.0001f)
    }

    @Test
    fun `every scale the frame offers is inside the range it offers`() {
        // The one rule left. Walked over a grid of picture shapes rather than checked once, because
        // covering and fitting swap sides as the shape changes and the floor has to follow both.
        for (w in listOf(1, 37, 600, 4000)) {
            for (h in listOf(1, 53, 600, 4000)) {
                val floor = CropMath.floorScale(w, h, frame)
                val ceiling = CropMath.coverScale(w, h, frame) * 6f
                assertTrue("the floor is above the ceiling for $w x $h", floor <= ceiling)
                for (step in -2..10) {
                    val asked = floor + (ceiling - floor) * step / 8f
                    val given = CropMath.clampScale(asked, floor, ceiling)
                    assertTrue("$given came out below the floor for $w x $h", given >= floor - 0.001f)
                    assertTrue("$given came out above the ceiling for $w x $h", given <= ceiling + 0.001f)
                    if (asked >= floor && asked <= ceiling) {
                        assertEquals("a scale inside the range was changed", asked, given, 0.001f)
                    }
                }
            }
        }
    }

    @Test
    fun `nothing sensible comes from nothing`() {
        assertEquals(1f, CropMath.coverScale(0, 600, frame), 0.0001f)
        assertEquals(1f, CropMath.coverScale(600, 600, 0f), 0.0001f)
        assertEquals(1f, CropMath.fitScale(0, 600, frame), 0.0001f)
        assertEquals(1f, CropMath.fitScale(600, 600, 0f), 0.0001f)
    }
}
