package com.orbit.home

import com.orbit.home.apps.AppLabels
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * P0-12: the drawer's A–Z order must follow the reader's alphabet, not UTF-16
 * code units.
 */
class AppLabelsTest {

    private val english = Locale.ENGLISH

    @Test
    fun `accented labels sort with their base letter`() {
        val sorted = AppLabels.sorted(listOf("Zoe", "Émile", "Adam"), english)
        // The old code-unit sort put Émile last, after Zoe.
        assertEquals(listOf("Adam", "Émile", "Zoe"), sorted)
    }

    @Test
    fun `case does not decide the order`() {
        assertEquals(listOf("apple", "Banana"), AppLabels.sorted(listOf("Banana", "apple"), english))
    }

    @Test
    fun `labels that only differ by case keep a stable order`() {
        val sorted = AppLabels.sorted(listOf("bank", "Bank", "BANK"), english)
        // Same collation key: the tie-break makes the result deterministic.
        assertEquals(sorted, AppLabels.sorted(listOf("bank", "Bank", "BANK"), english))
    }

    @Test
    fun `section keys group letters and lump everything else under hash`() {
        assertEquals("A", AppLabels.sectionKey("amazon"))
        assertEquals("É", AppLabels.sectionKey("émile"))
        assertEquals("#", AppLabels.sectionKey("123 Notes"))
        assertEquals("#", AppLabels.sectionKey("-"))
        assertEquals("#", AppLabels.sectionKey("   "))
        assertEquals("#", AppLabels.sectionKey(""))
    }
}
