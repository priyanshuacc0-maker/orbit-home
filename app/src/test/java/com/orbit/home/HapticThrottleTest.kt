package com.orbit.home

import com.orbit.home.ui.HapticKind
import com.orbit.home.ui.HapticThrottle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The haptic rate limit: ticks cannot stack up, and a press is never lost to a tick. */
class HapticThrottleTest {

    @Test
    fun firstHapticAlwaysFires() {
        assertTrue(HapticThrottle().tryAcquire(HapticKind.SLOT_TICK, 0L))
    }

    @Test
    fun ticksInsideTheirGapAreDropped() {
        val t = HapticThrottle()
        assertTrue(t.tryAcquire(HapticKind.SLOT_TICK, 1_000L))
        assertFalse(t.tryAcquire(HapticKind.SLOT_TICK, 1_010L))
        assertFalse(t.tryAcquire(HapticKind.SLOT_TICK, 1_029L))
        assertTrue(t.tryAcquire(HapticKind.SLOT_TICK, 1_030L))
    }

    @Test
    fun aWeakKindRightAfterAnyHapticIsDropped() {
        val t = HapticThrottle(globalGapMs = 18L)
        assertTrue(t.tryAcquire(HapticKind.PAGE_SNAP, 1_000L))
        assertFalse(t.tryAcquire(HapticKind.SLOT_TICK, 1_010L))
        assertTrue(t.tryAcquire(HapticKind.SLOT_TICK, 1_018L))
    }

    @Test
    fun aStrongKindIsNotHeldBackByATickJustBeforeIt() {
        val t = HapticThrottle()
        assertTrue(t.tryAcquire(HapticKind.SLOT_TICK, 1_000L))
        assertTrue(t.tryAcquire(HapticKind.PRESS, 1_002L))
    }

    @Test
    fun aStrongKindStillObeysItsOwnGap() {
        val t = HapticThrottle()
        assertTrue(t.tryAcquire(HapticKind.PRESS, 1_000L))
        assertFalse(t.tryAcquire(HapticKind.PRESS, 1_100L))
        assertTrue(t.tryAcquire(HapticKind.PRESS, 1_120L))
    }

    @Test
    fun aDroppedHapticDoesNotExtendTheGap() {
        val t = HapticThrottle()
        assertTrue(t.tryAcquire(HapticKind.SLOT_TICK, 1_000L))
        assertFalse(t.tryAcquire(HapticKind.SLOT_TICK, 1_020L))
        // Measured from the last haptic that fired (1_000), not from the dropped one.
        assertTrue(t.tryAcquire(HapticKind.SLOT_TICK, 1_031L))
    }

    @Test
    fun aFastSpinIsCappedAtTheTickRate() {
        val t = HapticThrottle()
        // A tick offered every 8 ms for one second: at most one per 30 ms gets through.
        var fired = 0
        var now = 0L
        while (now < 1_000L) {
            if (t.tryAcquire(HapticKind.SLOT_TICK, now)) fired++
            now += 8L
        }
        assertTrue("fired $fired", fired in 25..34)
    }
}
