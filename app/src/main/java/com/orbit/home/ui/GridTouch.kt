package com.orbit.home.ui

/**
 * What a touch on a grid item is doing, as a state machine rather than a handful of flags.
 *
 * The behaviour was already correct but it lived in five separate booleans spread across a gesture
 * handler, so "a long press must not launch the app" was true only for as long as nobody edited any of
 * them. Writing the transitions down - and testing them - is what makes them a rule instead of a habit.
 *
 * The four states, and what each one means:
 *
 * - [TouchPhase.IDLE] - nothing is being held. A short tap here opens the app.
 * - [TouchPhase.HELD] - the finger has been down long enough to count, and has not moved.
 * - [TouchPhase.DRAGGING] - it has moved. The app is being carried.
 * - [TouchPhase.MENU] - it was let go without being carried anywhere, so its menu is open.
 */
internal enum class TouchPhase { IDLE, HELD, DRAGGING, MENU }

/** What a finger has just done. */
internal sealed interface TouchEvent {
    /** The finger went down. */
    data object Down : TouchEvent

    /** The press has been held long enough to count. */
    data object LongPress : TouchEvent

    /** The finger moved. */
    data object Move : TouchEvent

    /** The finger lifted. */
    data object Up : TouchEvent

    /** The gesture was taken away - another window, a system gesture, a state change. */
    data object Cancel : TouchEvent
}

/**
 * [phase] is what is happening; [longPressed] records that this particular touch has already been a long
 * press, so lifting it can never be mistaken for a tap.
 */
internal data class TouchState(
    val phase: TouchPhase = TouchPhase.IDLE,
    val longPressed: Boolean = false,
    /**
     * Whether a finger is actually down.
     *
     * Without this, a lift with nothing behind it - a stream that started mid-gesture, an event arriving
     * after an interruption - would look exactly like a tap and open an app nobody touched.
     */
    val pressed: Boolean = false
)

/**
 * The state after [event].
 *
 * Cancel always returns to idle, whatever was happening: an interrupted gesture must leave nothing
 * behind - no carried app, no open menu, no waiting page turn - because there is no finger left to
 * finish it and the next touch has to start clean.
 */
internal fun touchReduce(state: TouchState, event: TouchEvent): TouchState = when (event) {
    TouchEvent.Down -> TouchState(TouchPhase.IDLE, longPressed = false, pressed = true)
    TouchEvent.LongPress -> TouchState(TouchPhase.HELD, longPressed = true, pressed = true)
    TouchEvent.Move -> when (state.phase) {
        TouchPhase.HELD, TouchPhase.DRAGGING ->
            TouchState(TouchPhase.DRAGGING, longPressed = true, pressed = true)
        else -> state
    }
    TouchEvent.Up -> when (state.phase) {
        // Let go without moving: its menu opens, and the app is not launched.
        TouchPhase.HELD -> TouchState(TouchPhase.MENU, longPressed = true, pressed = false)
        TouchPhase.DRAGGING -> TouchState(TouchPhase.IDLE, longPressed = true, pressed = false)
        else -> TouchState(TouchPhase.IDLE, longPressed = false, pressed = false)
    }
    TouchEvent.Cancel -> TouchState(TouchPhase.IDLE, longPressed = false, pressed = false)
}

/**
 * Whether [event] should open the app.
 *
 * Only a touch that went down and came straight back up, and was never held long enough to count. This
 * is the one place that answers it, so a long press, a drag, a drop, an interruption and a menu
 * dismissal are all incapable of launching by construction rather than by remembering.
 */
internal fun launchesApp(state: TouchState, event: TouchEvent): Boolean =
    event == TouchEvent.Up && state.phase == TouchPhase.IDLE && state.pressed && !state.longPressed
