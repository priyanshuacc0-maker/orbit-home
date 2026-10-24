package com.orbit.home

import com.orbit.home.animation.WheelGeometry
import com.orbit.home.preferences.AppPreferences
import com.orbit.home.preferences.WheelLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wheel's ranges and defaults.
 *
 * Every slider starts in the middle and the middle *is* the default, so each range
 * has to be built symmetrically around it. If someone later changes a default
 * without moving the range, the slider would no longer start centred — which is
 * exactly the sort of quiet drift these tests are here to catch.
 */
class WheelLookTest {

    private fun midpoint(range: ClosedFloatingPointRange<Float>) =
        (range.start + range.endInclusive) / 2f

    @Test
    fun `the icon size slider starts at its default`() {
        assertEquals(WheelLook.DEFAULT_ICON_SCALE, midpoint(WheelLook.ICON_RANGE), 0.0001f)
    }

    @Test
    fun `the wheel size slider starts at its default`() {
        assertEquals(WheelLook.DEFAULT_RADIUS_SCALE, midpoint(WheelLook.RADIUS_RANGE), 0.0001f)
    }

    @Test
    fun `the centre size slider starts at its default`() {
        assertEquals(WheelLook.DEFAULT_CENTRE_SCALE, midpoint(WheelLook.CENTRE_RANGE), 0.0001f)
    }

    @Test
    fun `the move slider starts at the wheel's natural position`() {
        assertEquals(0f, midpoint(-WheelLook.MAX_OFFSET..WheelLook.MAX_OFFSET), 0.0001f)
    }

    @Test
    fun `the count keeps its own range and defaults to its maximum`() {
        assertEquals(WheelGeometry.MIN_SLOTS, WheelLook.COUNT_RANGE.start)
        assertEquals(WheelGeometry.MAX_SLOTS, WheelLook.COUNT_RANGE.endInclusive)
        assertEquals(WheelLook.COUNT_RANGE.endInclusive, WheelLook.DEFAULT_COUNT)
    }

    /**
     * A range wider than the wheel can actually apply would leave dead travel at the
     * ends of the slider: the thumb would move and nothing would change.
     */
    @Test
    fun `no range exceeds what the wheel can apply`() {
        assertTrue(WheelLook.RADIUS_RANGE.start >= WheelGeometry.MIN_RADIUS_SCALE)
        assertTrue(WheelLook.RADIUS_RANGE.endInclusive <= WheelGeometry.MAX_RADIUS_SCALE)
        assertTrue(WheelLook.CENTRE_RANGE.start >= WheelGeometry.MIN_CENTRE_SCALE)
        assertTrue(WheelLook.CENTRE_RANGE.endInclusive <= WheelGeometry.MAX_CENTRE_SCALE)
        assertTrue(WheelLook.ICON_RANGE.start >= AppPreferences.MIN_ICON_SCALE)
        assertTrue(WheelLook.ICON_RANGE.endInclusive <= AppPreferences.MAX_ICON_SCALE)
    }

    /** The defaults must themselves be inside their ranges, or they get clamped. */
    @Test
    fun `every default sits inside its range`() {
        assertTrue(WheelLook.DEFAULT_ICON_SCALE in WheelLook.ICON_RANGE)
        assertTrue(WheelLook.DEFAULT_RADIUS_SCALE in WheelLook.RADIUS_RANGE)
        assertTrue(WheelLook.DEFAULT_CENTRE_SCALE in WheelLook.CENTRE_RANGE)
        assertTrue(WheelLook.DEFAULT_COUNT in WheelLook.COUNT_RANGE)
        assertTrue(0f in -WheelLook.MAX_OFFSET..WheelLook.MAX_OFFSET)
    }

    /** A fresh install must show exactly the shipped shape. */
    @Test
    fun `an untouched look is the shipped shape`() {
        val look = WheelLook()
        assertEquals(12, look.visibleCount)
        assertEquals(1.29f, look.iconScale, 0.0001f)
        assertEquals(1.06f, look.radiusScale, 0.0001f)
        assertEquals(1.24f, look.centreScale, 0.0001f)
        assertEquals(0f, look.offsetY, 0.0001f)
    }
}
