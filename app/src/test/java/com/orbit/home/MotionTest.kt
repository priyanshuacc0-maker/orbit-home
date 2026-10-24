package com.orbit.home

import com.orbit.home.animation.Motion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reduced-motion rule (directive Phase 9). Android expresses it as an
 * animator duration scale; only the policy is tested here, not the reading.
 */
class MotionTest {

    @Test
    fun zeroScaleMeansAnimationsAreOff() {
        assertTrue(Motion.animationsDisabled(0f))
    }

    @Test
    fun ordinaryScalesKeepAnimationsOn() {
        assertFalse(Motion.animationsDisabled(0.5f))
        assertFalse(Motion.animationsDisabled(1f))
        assertFalse(Motion.animationsDisabled(10f))
    }

    @Test
    fun aNegativeScaleIsTreatedAsOff() {
        assertTrue(Motion.animationsDisabled(-1f))
    }
}
