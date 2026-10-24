package com.orbit.home

import com.orbit.home.animation.WheelGeometry
import com.orbit.home.preferences.WheelLook
import com.orbit.home.preferences.WheelLookCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Storing the floating wheel's own shape. */
class WheelLookCodecTest {

    private val shape = WheelLook(
        visibleCount = 7,
        iconScale = 0.99f,
        radiusScale = 1.1f,
        centreScale = 1.0f,
        offsetY = -0.05f
    )

    @Test
    fun `a shape survives a round trip`() {
        assertEquals(shape, WheelLookCodec.decode(WheelLookCodec.encode(shape), WheelLook()))
    }

    @Test
    fun `nothing stored falls back`() {
        assertEquals(WheelLook(), WheelLookCodec.decode(null, WheelLook()))
        assertEquals(WheelLook(), WheelLookCodec.decode("", WheelLook()))
        assertEquals(WheelLook(), WheelLookCodec.decode("   ", WheelLook()))
    }

    @Test
    fun `a corrupt value falls back rather than throwing`() {
        assertEquals(WheelLook(), WheelLookCodec.decode("not|a|shape", WheelLook()))
        assertEquals(WheelLook(), WheelLookCodec.decode("x|y|z|w|v", WheelLook()))
        assertEquals(WheelLook(), WheelLookCodec.decode("7|0.8", WheelLook()))
    }

    @Test
    fun `values outside the usable range are clamped, not discarded`() {
        val decoded = WheelLookCodec.decode("999|9.0|9.0|9.0|9.0", WheelLook())
        assertEquals(WheelGeometry.MAX_SLOTS, decoded.visibleCount)
        assertTrue(decoded.iconScale in WheelLook.ICON_RANGE)
        assertTrue(decoded.radiusScale in WheelLook.RADIUS_RANGE)
        assertTrue(decoded.centreScale in WheelLook.CENTRE_RANGE)
        assertTrue(decoded.offsetY in -WheelLook.MAX_OFFSET..WheelLook.MAX_OFFSET)
    }

    @Test
    fun `too few slots is raised to the minimum, not left undrawable`() {
        val decoded = WheelLookCodec.decode("0|0.99|1.1|1.0|0.0", WheelLook())
        assertEquals(WheelGeometry.MIN_SLOTS, decoded.visibleCount)
    }
}
