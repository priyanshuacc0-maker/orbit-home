package com.orbit.home

import com.orbit.home.apps.InstalledApp
import com.orbit.home.apps.WheelApps
import com.orbit.home.preferences.WheelPage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What one wheel shows, and in what order. Each wheel has its own page, so the same
 * rules now apply per wheel rather than once for the home screen.
 */
class WheelAppsTest {

    private fun app(id: String, label: String) = InstalledApp(
        id = id,
        packageName = id.substringBefore('/'),
        activityName = id.substringAfter('/'),
        label = label
    )

    private val installed = listOf(
        app("com.a/A", "Alpha"),
        app("com.b/B", "Beta"),
        app("com.c/C", "Gamma")
    )

    @Test
    fun `an untouched wheel shows every installed app`() {
        assertEquals(installed, WheelApps.resolve(installed, WheelPage()))
    }

    @Test
    fun `a chosen wheel shows exactly what was chosen`() {
        val page = WheelPage(apps = listOf("com.b/B"), initialized = true)
        assertEquals(listOf("Beta"), WheelApps.resolve(installed, page).map { it.label })
    }

    @Test
    fun `the chosen order is kept`() {
        val page = WheelPage(apps = listOf("com.c/C", "com.a/A"), initialized = true)
        assertEquals(listOf("Gamma", "Alpha"), WheelApps.resolve(installed, page).map { it.label })
    }

    @Test
    fun `an uninstalled app is skipped and the rest keep their order`() {
        val page = WheelPage(
            apps = listOf("com.c/C", "com.gone/Gone", "com.a/A"),
            initialized = true
        )
        assertEquals(listOf("Gamma", "Alpha"), WheelApps.resolve(installed, page).map { it.label })
    }

    @Test
    fun `choosing nothing is respected, not treated as unset`() {
        val page = WheelPage(apps = emptyList(), initialized = true)
        assertEquals(emptyList<InstalledApp>(), WheelApps.resolve(installed, page))
    }

    @Test
    fun `a newly installed app joins an untouched wheel`() {
        val withNew = installed + app("com.d/D", "Delta")
        assertEquals(4, WheelApps.resolve(withNew, WheelPage()).size)
    }

    @Test
    fun `a newly installed app does not join a curated wheel`() {
        val withNew = installed + app("com.d/D", "Delta")
        val page = WheelPage(apps = listOf("com.a/A"), initialized = true)
        assertEquals(listOf("Alpha"), WheelApps.resolve(withNew, page).map { it.label })
    }

    /**
     * The point of separate wheels: two of them can show entirely different apps.
     */
    @Test
    fun `two wheels resolve independently`() {
        val first = WheelPage(apps = listOf("com.a/A"), initialized = true)
        val second = WheelPage(apps = listOf("com.b/B", "com.c/C"), initialized = true)
        assertEquals(listOf("Alpha"), WheelApps.resolve(installed, first).map { it.label })
        assertEquals(listOf("Beta", "Gamma"), WheelApps.resolve(installed, second).map { it.label })
    }

    @Test
    fun `the picker starts from what is on that wheel`() {
        val page = WheelPage(apps = listOf("com.c/C"), initialized = true)
        assertEquals(listOf("com.c/C"), WheelApps.initialSelection(installed, page))
        // Untouched: everything, so the picker opens fully ticked.
        assertEquals(installed.map { it.id }, WheelApps.initialSelection(installed, WheelPage()))
    }
}
