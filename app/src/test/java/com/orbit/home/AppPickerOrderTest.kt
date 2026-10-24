package com.orbit.home

import com.orbit.home.apps.AppPickerOrder
import com.orbit.home.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The wheel picker's ordering: chosen apps first, then the rest alphabetically. */
class AppPickerOrderTest {

    private fun app(id: String, label: String) = InstalledApp(
        id = id,
        packageName = id.substringBefore('/'),
        activityName = id.substringAfter('/'),
        label = label
    )

    private val installed = listOf(
        app("com.a/A", "Alpha"),
        app("com.b/B", "Bravo"),
        app("com.c/C", "Charlie"),
        app("com.d/D", "Delta")
    )

    @Test
    fun `chosen apps come first, in ring order`() {
        val result = AppPickerOrder.ordered(installed, listOf("com.d/D", "com.b/B"))
        assertEquals(
            listOf("Delta", "Bravo", "Alpha", "Charlie"),
            result.map { it.label }
        )
    }

    @Test
    fun `with nothing chosen everything is alphabetical`() {
        assertEquals(
            listOf("Alpha", "Bravo", "Charlie", "Delta"),
            AppPickerOrder.ordered(installed, emptyList()).map { it.label }
        )
    }

    @Test
    fun `a chosen app never appears twice`() {
        val result = AppPickerOrder.ordered(installed, listOf("com.a/A"))
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), result.map { it.label })
        assertEquals(installed.size, result.size)
    }

    @Test
    fun `an uninstalled chosen app is skipped, not shown blank`() {
        val result = AppPickerOrder.ordered(installed, listOf("com.gone/Gone", "com.c/C"))
        assertEquals(listOf("Charlie", "Alpha", "Bravo", "Delta"), result.map { it.label })
    }

    @Test
    fun `every installed app is present exactly once`() {
        val result = AppPickerOrder.ordered(installed, listOf("com.c/C", "com.a/A"))
        assertEquals(installed.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertEquals(installed.size, result.size)
    }

    @Test
    fun `an empty app list is handled`() {
        assertEquals(emptyList<InstalledApp>(), AppPickerOrder.ordered(emptyList(), listOf("x")))
    }
}
