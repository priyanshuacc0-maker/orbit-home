package com.orbit.home

import androidx.compose.animation.core.exponentialDecay
import com.orbit.home.animation.WheelMotionState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The wheel's haptic detent hook: it follows motion, and only motion. */
class WheelSlotCrossingTest {

    private fun wheel() = WheelMotionState(exponentialDecay()).also { it.stepRadians = 1f }

    @Test
    fun dragWithinOneSlotFiresNothing() {
        val w = wheel()
        var crossed = 0
        w.onSlotCrossed = { crossed++ }
        w.applyDrag(-0.2f)
        w.applyDrag(-0.2f)
        assertEquals(0, crossed)
    }

    @Test
    fun dragAcrossASlotBoundaryFiresOnce() {
        val w = wheel()
        var crossed = 0
        w.onSlotCrossed = { crossed++ }
        w.applyDrag(-0.4f)
        w.applyDrag(-0.2f) // offset 0.6 rounds to slot 1
        assertEquals(1, crossed)
        w.applyDrag(-0.2f) // 0.8, still slot 1
        assertEquals(1, crossed)
    }

    @Test
    fun dragAcrossSeveralSlotsInOneStepFiresOncePerSample() {
        val w = wheel()
        var crossed = 0
        w.onSlotCrossed = { crossed++ }
        w.applyDrag(-3f)
        assertEquals(1, crossed)
    }

    @Test
    fun programmaticJumpsNeverFire() {
        val w = wheel()
        w.applyDrag(-2f)
        var crossed = 0
        w.onSlotCrossed = { crossed++ }
        w.reset()
        w.focusOnIds(listOf("a", "b", "c"))
        assertEquals(0, crossed)
    }

    @Test
    fun noListenerIsFine() {
        val w = wheel()
        w.applyDrag(-5f)
        assertEquals(5f, w.offset, 0.0001f)
    }
}
