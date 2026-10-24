package com.orbit.home

import com.orbit.home.ui.TripleTapCounter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Three taps in a row, and not three taps spread over a minute.
 *
 * The arithmetic is where it goes wrong: a counter that never resets fires on the second tap of one
 * burst and the first of the next, and one that resets too eagerly never fires at all.
 */
class TripleTapTest {

    private val window = TripleTapCounter.DEFAULT_WINDOW_MILLIS

    @Test
    fun `three taps in a row fire, and the first two do not`() {
        val counter = TripleTapCounter()
        assertFalse(counter.tap(0))
        assertFalse(counter.tap(100))
        assertTrue(counter.tap(200))
    }

    @Test
    fun `a fourth tap does not fire straight away`() {
        val counter = TripleTapCounter()
        counter.tap(0); counter.tap(100)
        assertTrue(counter.tap(200))
        assertFalse("a fourth tap completed another three", counter.tap(300))
        assertFalse(counter.tap(400))
        assertTrue(counter.tap(500))
    }

    @Test
    fun `a gap longer than the window starts the count again`() {
        val counter = TripleTapCounter()
        assertFalse(counter.tap(0))
        assertFalse(counter.tap(100))
        // A long pause: this is the first of a new burst, not the third of the old one.
        assertFalse(counter.tap(window + 101))
        assertFalse(counter.tap(window + 200))
        assertTrue(counter.tap(window + 300))
    }

    @Test
    fun `a tap exactly one window later still counts`() {
        val counter = TripleTapCounter()
        counter.tap(0)
        counter.tap(window)
        assertTrue(counter.tap(window * 2))
    }

    @Test
    fun `a tap just past the window does not`() {
        val counter = TripleTapCounter()
        counter.tap(0)
        counter.tap(window + 1)
        assertFalse("a tap outside the window continued the burst", counter.tap(window * 2 + 2))
    }

    @Test
    fun `reset forgets everything`() {
        val counter = TripleTapCounter()
        counter.tap(0); counter.tap(100)
        counter.reset()
        assertFalse(counter.tap(200))
        assertFalse(counter.tap(300))
        assertTrue(counter.tap(400))
    }

    @Test
    fun `two taps never fire, however many times they are repeated`() {
        val counter = TripleTapCounter()
        for (round in 0..20) {
            assertFalse(counter.tap(round * 1000L))
            assertFalse(counter.tap(round * 1000L + 100))
        }
    }
}
