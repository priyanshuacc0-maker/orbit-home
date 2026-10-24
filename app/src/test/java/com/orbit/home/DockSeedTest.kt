package com.orbit.home

import com.orbit.home.apps.DockDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class DockSeedTest {
    @Test
    fun `a new dock starts with four apps`() {
        assertEquals(4, DockDefaults.SEED_COUNT)
    }

    @Test
    fun `the picks come first and the rest make up the numbers`() {
        assertEquals(
            listOf("a", "b", "c", "d"),
            DockDefaults.fillTo(listOf("a", "b", "c"), listOf("b", "d", "e"))
        )
    }

    @Test
    fun `more picks than four are cut to four`() {
        assertEquals(listOf("a", "b", "c", "d"), DockDefaults.fillTo(listOf("a", "b", "c", "d", "e"), emptyList()))
    }

    @Test
    fun `a phone with fewer than four apps gets what it has`() {
        assertEquals(listOf("a", "b"), DockDefaults.fillTo(listOf("a"), listOf("a", "b")))
    }
}
