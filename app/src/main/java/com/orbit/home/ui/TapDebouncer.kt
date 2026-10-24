package com.orbit.home.ui

/**
 * Suppresses repeated taps inside a short window.
 *
 * A double-tap used to start the app twice and count two launches, which also
 * inflated the Task Hub's "frequently used" list. Pure and clock-driven, so it is
 * unit-testable.
 */
class TapDebouncer(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {

    private var lastMillis: Long? = null

    /** True when this tap should be acted on. */
    fun allow(nowMillis: Long): Boolean {
        val previous = lastMillis
        if (previous != null && nowMillis - previous < windowMillis) return false
        lastMillis = nowMillis
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 400L
    }
}
