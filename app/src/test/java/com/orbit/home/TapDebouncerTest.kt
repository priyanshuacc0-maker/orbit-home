package com.orbit.home

import com.orbit.home.ui.TapDebouncer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-04: a double-tap used to start the app twice and record two launches.
 */
class TapDebouncerTest {

    @Test
    fun `the first tap is always allowed`() {
        assertTrue(TapDebouncer(400L).allow(1_000L))
    }

    @Test
    fun `a second tap inside the window is dropped`() {
        val debouncer = TapDebouncer(400L)
        assertTrue(debouncer.allow(1_000L))
        assertFalse(debouncer.allow(1_100L))
        assertFalse(debouncer.allow(1_399L))
    }

    @Test
    fun `a tap after the window is allowed`() {
        val debouncer = TapDebouncer(400L)
        assertTrue(debouncer.allow(1_000L))
        assertTrue(debouncer.allow(1_400L))
    }

    @Test
    fun `a dropped tap does not extend the window`() {
        val debouncer = TapDebouncer(400L)
        assertTrue(debouncer.allow(1_000L))
        assertFalse(debouncer.allow(1_300L))
        // Counted from the tap that was acted on, not the one that was dropped.
        assertTrue(debouncer.allow(1_400L))
    }
}
