package com.orbit.home

import com.orbit.home.preferences.HomeSettings
import com.orbit.home.preferences.WheelLook
import com.orbit.home.preferences.WheelPage
import com.orbit.home.preferences.WheelPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adding and removing wheels, and what a new wheel starts from. */
class WheelPagesTest {

    private val custom = WheelPage(
        apps = listOf("com.a/A"),
        initialized = true,
        look = WheelLook(visibleCount = 7, iconScale = 1.4f, radiusScale = 0.9f, centreScale = 1.1f, offsetY = 0.05f)
    )

    @Test
    fun `a new wheel copies the shape of the one it came from`() {
        val next = WheelPages.appendLike(listOf(custom), fromIndex = 0)
        assertEquals(2, next.size)
        assertEquals(custom.look, next[1].look)
    }

    @Test
    fun `a new wheel starts with no apps chosen, not a copy of the old ones`() {
        val next = WheelPages.appendLike(listOf(custom), fromIndex = 0)
        assertEquals(emptyList<String>(), next[1].apps)
        // Not initialized, so it shows every installed app until it is curated.
        assertEquals(false, next[1].initialized)
    }

    @Test
    fun `a new wheel copies the shape of whichever wheel was being viewed`() {
        val other = WheelPage(look = WheelLook(visibleCount = 5))
        val next = WheelPages.appendLike(listOf(custom, other), fromIndex = 1)
        assertEquals(other.look, next.last().look)
    }

    @Test
    fun `an out-of-range source falls back to the default shape`() {
        val next = WheelPages.appendLike(listOf(custom), fromIndex = 9)
        assertEquals(WheelLook(), next.last().look)
    }

    @Test
    fun `adding is refused at the cap`() {
        val full = List(HomeSettings.MAX_WHEELS) { WheelPage() }
        assertEquals(full, WheelPages.appendLike(full, fromIndex = 0))
    }

    @Test
    fun `one below the cap can still be added`() {
        val almost = List(HomeSettings.MAX_WHEELS - 1) { WheelPage() }
        assertEquals(HomeSettings.MAX_WHEELS, WheelPages.appendLike(almost, fromIndex = 0).size)
    }

    @Test
    fun `the first wheel is never removed`() {
        val wheels = listOf(WheelPage(), custom)
        assertEquals(wheels, WheelPages.removeAt(wheels, 0))
        assertEquals(wheels, WheelPages.removeAt(wheels, -1))
    }

    @Test
    fun `a later wheel is removed and the rest keep their order`() {
        val a = WheelPage(look = WheelLook(visibleCount = 5))
        val b = WheelPage(look = WheelLook(visibleCount = 6))
        val c = WheelPage(look = WheelLook(visibleCount = 7))
        val next = WheelPages.removeAt(listOf(a, b, c), 1)
        assertEquals(listOf(a, c), next)
    }

    @Test
    fun `an index that is not there changes nothing`() {
        val wheels = listOf(WheelPage(), custom)
        assertEquals(wheels, WheelPages.removeAt(wheels, 5))
    }

    @Test
    fun `removing then adding gives back a wheel with the same shape`() {
        val wheels = listOf(WheelPage(), custom)
        val removed = WheelPages.removeAt(wheels, 1)
        val added = WheelPages.appendLike(removed, fromIndex = 0)
        assertEquals(2, added.size)
        assertEquals(WheelLook(), added.last().look)
    }

    @Test
    fun `the cap is a real number of wheels`() {
        assertTrue(HomeSettings.MAX_WHEELS in 2..16)
    }
}
