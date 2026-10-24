package com.orbit.home.ui

/**
 * Counts taps that arrive close together, and says when three have.
 *
 * Pure, and separate, because the arithmetic of it is where the mistakes are: a counter that does
 * not reset fires on the second tap of one burst and the first of the next, and one that resets too
 * eagerly never fires at all. Time is passed in rather than read, so it can be tested without a
 * clock.
 */
class TripleTapCounter(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {

    private var taps = 0
    private var lastMillis = Long.MIN_VALUE

    /** Records a tap and reports whether it completed three. */
    fun tap(nowMillis: Long): Boolean {
        taps = if (nowMillis - lastMillis <= windowMillis) taps + 1 else 1
        lastMillis = nowMillis
        if (taps >= TAPS) {
            // Reset on firing, so a fourth tap starts a new count rather than completing another
            // three straight away.
            taps = 0
            lastMillis = Long.MIN_VALUE
            return true
        }
        return false
    }

    /** Forgets any taps counted so far. */
    fun reset() {
        taps = 0
        lastMillis = Long.MIN_VALUE
    }

    companion object {
        const val TAPS = 3

        /** Long enough for three deliberate taps, short enough that three stray ones do not count. */
        const val DEFAULT_WINDOW_MILLIS = 600L
    }
}
