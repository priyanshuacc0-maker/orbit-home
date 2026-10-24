package com.orbit.home

import com.orbit.home.ui.TouchEvent
import com.orbit.home.ui.TouchPhase
import com.orbit.home.ui.TouchState
import com.orbit.home.ui.launchesApp
import com.orbit.home.ui.touchReduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transitions a touch on a grid item is allowed to make.
 *
 * This is the definition the gesture handler is held to. Every case here is one that has gone wrong
 * somewhere in a launcher before: an app opening when the finger was only held down, a menu opening
 * during a drag, a page turning after the gesture was cancelled.
 *
 * NOT COVERED HERE, and needing a device: that these transitions are what the running gesture handler
 * actually does. The machine can be proven; the wiring to the pointer stream cannot be proven from a
 * unit test, and the project has no instrumented test setup. That is stated rather than assumed.
 */
class GridTouchTest {

    private fun after(vararg events: TouchEvent): TouchState {
        var s = TouchState()
        for (e in events) s = touchReduce(s, e)
        return s
    }

    // ---- a short tap launches, exactly once ----

    @Test
    fun `a short tap launches the app`() {
        val down = touchReduce(TouchState(), TouchEvent.Down)
        assertTrue(launchesApp(down, TouchEvent.Up))
    }

    @Test
    fun `a tap launches once, not on the way down as well`() {
        val down = touchReduce(TouchState(), TouchEvent.Down)
        assertFalse("the press itself must not launch", launchesApp(TouchState(), TouchEvent.Down))
        assertTrue(launchesApp(down, TouchEvent.Up))
        // And the state it leaves behind cannot launch again.
        assertFalse(launchesApp(touchReduce(down, TouchEvent.Up), TouchEvent.Up))
    }

    // ---- a long press opens the menu, and does not launch ----

    @Test
    fun `a long press that does not move opens the menu`() {
        val s = after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Up)
        assertEquals(TouchPhase.MENU, s.phase)
    }

    @Test
    fun `a long press does not launch`() {
        val held = after(TouchEvent.Down, TouchEvent.LongPress)
        assertFalse(launchesApp(held, TouchEvent.Up))
    }

    @Test
    fun `a long press with no movement is never mistaken for a tap`() {
        val s = after(TouchEvent.Down, TouchEvent.LongPress)
        assertTrue(s.longPressed)
        assertFalse(launchesApp(s, TouchEvent.Up))
    }

    // ---- a long press with movement drags, and does not launch ----

    @Test
    fun `a long press that moves starts a drag`() {
        assertEquals(TouchPhase.DRAGGING, after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Move).phase)
    }

    @Test
    fun `a drag does not launch`() {
        val dragging = after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Move)
        assertFalse(launchesApp(dragging, TouchEvent.Up))
    }

    @Test
    fun `a drag does not open a menu`() {
        assertEquals(TouchPhase.IDLE, after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Move, TouchEvent.Up).phase)
    }

    @Test
    fun `movement before the long press is not a drag`() {
        // A finger that wobbles on the way to a long press has not started carrying anything.
        assertEquals(TouchPhase.IDLE, after(TouchEvent.Down, TouchEvent.Move).phase)
    }

    // ---- interruption leaves nothing behind ----

    @Test
    fun `cancelling a drag returns to idle`() {
        assertEquals(TouchPhase.IDLE, after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Move, TouchEvent.Cancel).phase)
    }

    @Test
    fun `cancelling a hold returns to idle`() {
        assertEquals(TouchPhase.IDLE, after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Cancel).phase)
    }

    @Test
    fun `cancelling an open menu returns to idle`() {
        assertEquals(TouchPhase.IDLE, after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Up, TouchEvent.Cancel).phase)
    }

    @Test
    fun `a cancelled gesture cannot launch on the way out`() {
        val held = after(TouchEvent.Down, TouchEvent.LongPress)
        assertFalse(launchesApp(held, TouchEvent.Cancel))
    }

    @Test
    fun `the touch after a cancellation behaves like a fresh one`() {
        val cancelled = after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Move, TouchEvent.Cancel)
        val down = touchReduce(cancelled, TouchEvent.Down)
        assertTrue(launchesApp(down, TouchEvent.Up))
    }

    // ---- dismissing the menu ----

    @Test
    fun `touching elsewhere closes the menu and does not launch`() {
        val open = after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Up)
        assertEquals(TouchPhase.IDLE, touchReduce(open, TouchEvent.Down).phase)
        assertFalse(launchesApp(open, TouchEvent.Down))
    }

    // ---- the whole cycle, from idle back to idle ----

    @Test
    fun `every gesture ends where it should`() {
        val toIdle = listOf(
            listOf(TouchEvent.Down, TouchEvent.Up),
            listOf(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Move, TouchEvent.Up),
            listOf(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Move, TouchEvent.Cancel),
            listOf(TouchEvent.Down, TouchEvent.Cancel)
        )
        for (gesture in toIdle) {
            assertEquals("$gesture did not end at idle", TouchPhase.IDLE, after(*gesture.toTypedArray()).phase)
        }
        // The one that deliberately does not: a hold that is let go leaves its menu open, and touching
        // elsewhere is what closes it.
        val held = after(TouchEvent.Down, TouchEvent.LongPress, TouchEvent.Up)
        assertEquals(TouchPhase.MENU, held.phase)
        assertEquals(TouchPhase.IDLE, touchReduce(held, TouchEvent.Down).phase)
    }

    @Test
    fun `a lift with no press behind it does not launch`() {
        // An event stream that starts mid-gesture, or one arriving after an interruption, must not look
        // like a tap on something nobody touched.
        assertFalse(launchesApp(TouchState(), TouchEvent.Up))
        val afterCancel = after(TouchEvent.Down, TouchEvent.Cancel)
        assertFalse(launchesApp(afterCancel, TouchEvent.Up))
    }
}
