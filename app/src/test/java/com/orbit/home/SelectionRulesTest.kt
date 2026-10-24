package com.orbit.home

import com.orbit.home.apps.SelectionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticking apps in a picker, including the dock's five-app cap. */
class SelectionRulesTest {

    @Test
    fun `ticking an unticked app adds it at the end`() {
        assertEquals(listOf("a", "b"), SelectionRules.toggle(listOf("a"), "b"))
    }

    @Test
    fun `ticking a ticked app removes it`() {
        assertEquals(listOf("a"), SelectionRules.toggle(listOf("a", "b"), "b"))
    }

    @Test
    fun `removing keeps the order of the rest`() {
        assertEquals(listOf("a", "c"), SelectionRules.toggle(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun `the cap refuses a sixth dock app`() {
        val five = listOf("a", "b", "c", "d", "e")
        assertEquals(five, SelectionRules.toggle(five, "f", max = 5))
        assertFalse(SelectionRules.canAdd(five, "f", max = 5))
    }

    @Test
    fun `the cap still allows unticking, so a full selection is not stuck`() {
        val five = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("b", "c", "d", "e"), SelectionRules.toggle(five, "a", max = 5))
    }

    @Test
    fun `below the cap adding is allowed`() {
        val four = listOf("a", "b", "c", "d")
        assertTrue(SelectionRules.canAdd(four, "e", max = 5))
        assertEquals(5, SelectionRules.toggle(four, "e", max = 5).size)
    }

    @Test
    fun `with no cap anything can be added`() {
        val many = (1..40).map { "app$it" }
        assertEquals(41, SelectionRules.toggle(many, "one-more").size)
        assertTrue(SelectionRules.canAdd(many, "one-more"))
    }

    @Test
    fun `a chosen app is never reported as addable`() {
        assertFalse(SelectionRules.canAdd(listOf("a"), "a", max = 5))
    }

    /**
     * The bug this exists for: selected rows used `canAdd` to decide whether they
     * were tappable, which is false for an app that is already chosen — so an app
     * could be added to the wheel or the dock but never removed.
     */
    @Test
    fun `a chosen app is always tappable, so it can be removed`() {
        assertTrue(SelectionRules.canToggle(listOf("a"), "a"))
        assertTrue(SelectionRules.canToggle(listOf("a"), "a", max = 5))
    }

    @Test
    fun `a full selection still lets you untick`() {
        val five = listOf("a", "b", "c", "d", "e")
        assertTrue(SelectionRules.canToggle(five, "a", max = 5))
        // ...but a sixth is still refused.
        assertFalse(SelectionRules.canToggle(five, "f", max = 5))
    }

    @Test
    fun `an unchosen app below the cap is tappable`() {
        assertTrue(SelectionRules.canToggle(listOf("a"), "b", max = 5))
    }

    @Test
    fun `tapping a chosen app really does remove it`() {
        // The two rules must agree: tappable means the toggle changes something.
        val selected = listOf("a", "b")
        assertTrue(SelectionRules.canToggle(selected, "a"))
        assertEquals(listOf("b"), SelectionRules.toggle(selected, "a"))
    }
}
