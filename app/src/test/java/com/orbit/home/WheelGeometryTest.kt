package com.orbit.home

import com.orbit.home.animation.WheelGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wheel's ring and icon sizes.
 *
 * The one that matters here is the first: the wheel-size control used to make the icons *smaller*
 * once it was pushed past about 115%, because the ring grew until an icon on it no longer fitted
 * and the edge cap shrank the icons to compensate. The control said one thing and did another.
 */
class WheelGeometryTest {

    private fun wheel(
        radiusScale: Float,
        iconScale: Float = 1f,
        slots: Int = 8,
        size: Float = 1000f
    ) = WheelGeometry(
        size = size,
        iconScale = iconScale,
        count = slots,
        requestedCount = slots,
        radiusScale = radiusScale
    )

    @Test
    fun `a larger wheel never shrinks its icons`() {
        val scales = listOf(
            0.65f, 0.7f, 0.8f, 0.9f, 1.0f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f
        )
        val sizes = scales.map { wheel(it).iconPx }
        for (i in 1 until sizes.size) {
            assertTrue(
                "icons shrank going from ${scales[i - 1]} to ${scales[i]}: " +
                    "${sizes[i - 1]} then ${sizes[i]}",
                sizes[i] >= sizes[i - 1] - 0.001f
            )
        }
    }

    @Test
    fun `every position on the wheel-size control changes the wheel`() {
        // The control used to reach its largest ring at about 110% and do nothing for the rest
        // of its travel. Now the whole of it is live, so the ring must keep growing from the
        // bottom of the range to the top.
        val lo = WheelGeometry.MIN_RADIUS_SCALE
        val hi = WheelGeometry.MAX_RADIUS_SCALE
        val steps = 24
        var previous = wheel(lo).radius
        for (i in 1..steps) {
            val scale = lo + (hi - lo) * i / steps
            val here = wheel(scale).radius
            assertTrue(
                "the ring did not grow from ${lo + (hi - lo) * (i - 1) / steps} to $scale: " +
                    "$previous then $here",
                here > previous
            )
            previous = here
        }
    }

    @Test
    fun `the bottom of the control is the smallest ring`() {
        assertEquals(
            "the control's floor must be its smallest ring",
            wheel(WheelGeometry.MIN_RADIUS_SCALE).radius,
            wheel(WheelGeometry.MIN_RADIUS_SCALE - 0.2f).radius,
            0.001f
        )
    }

    @Test
    fun `icons always stay inside the drawable`() {
        val size = 1000f
        for (slots in listOf(4, 6, 8, 10, 12)) {
            for (radiusScale in listOf(0.65f, 0.8f, 1.0f, 1.15f, 1.3f)) {
                for (iconScale in listOf(0.5f, 1.0f, 1.6f)) {
                    val g = wheel(radiusScale, iconScale, slots, size)
                    val outer = g.radius + g.iconPx / 2f
                    assertTrue(
                        "out of bounds with slots=$slots radiusScale=$radiusScale " +
                            "iconScale=$iconScale: reaches $outer of ${size / 2f}",
                        outer <= size / 2f + 0.001f
                    )
                }
            }
        }
    }

    @Test
    fun `the icon control still has an effect at every wheel size`() {
        for (radiusScale in listOf(0.65f, 1.0f, 1.2f, 1.3f)) {
            val small = wheel(radiusScale, iconScale = 1.0f).iconPx
            val large = wheel(radiusScale, iconScale = 1.4f).iconPx
            assertTrue(
                "icon size did nothing at wheel size $radiusScale: $small then $large",
                large > small
            )
        }
    }

    @Test
    fun `neighbours cannot overlap`() {
        val slots = 12
        val g = wheel(1.0f, slots = slots)
        val arcPerSlot = (2 * Math.PI * g.radius / slots).toFloat()
        assertTrue(
            "icons are wider than their arc: ${g.iconPx} against $arcPerSlot",
            g.iconPx <= arcPerSlot
        )
    }
}
