package com.orbit.home.diagnostics

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Actionable diagnostics, and nothing else (directive Phase 10).
 *
 * Only failures that were handled are recorded — never the user's app usage,
 * their installed-app inventory, folder names or anything else personal. A
 * launcher's log should be useful to debug and dull to read.
 */
object Diagnostics {

    private const val TAG = "OrbitHome"

    /**
     * Cancellation is not a failure.
     *
     * A broad `catch (Throwable)` that swallows [CancellationException] breaks
     * structured concurrency: the coroutine looks like it finished normally, so the
     * caller keeps waiting on work that will never complete. Every broad catch in
     * this app calls this first (remediation 5.21).
     */
    fun rethrowIfCancellation(t: Throwable) {
        if (t is CancellationException) throw t
    }

    /** Records a handled failure. Never throws, whatever the logging layer does. */
    fun warn(area: String, throwable: Throwable? = null) {
        try {
            if (throwable == null) Log.w(TAG, area) else Log.w(TAG, area, throwable)
        } catch (t: Throwable) {
            // Logging must never be the thing that breaks the launcher.
        }
    }
}
