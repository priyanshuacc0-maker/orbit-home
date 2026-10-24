package com.orbit.home

import com.orbit.home.apps.InstalledApp
import com.orbit.home.apps.changedPackages
import com.orbit.home.apps.inventoryChanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Noticing when an app has changed without anything being installed or uninstalled.
 *
 * This is the case that a comparison by id alone cannot see: an app updates, its name changes, and its
 * launch component stays exactly the same. The screen then goes on showing the old name for ever, which
 * looks like the launcher is broken rather than out of date.
 */
class InventoryRefreshTest {

    private fun app(id: String, label: String, pkg: String = "com.example", act: String? = "Main") =
        InstalledApp(id = id, packageName = pkg, activityName = act, label = label)

    // ---- what counts as a change ----

    @Test
    fun `the same app with the same name is not a change`() {
        assertFalse(inventoryChanged(listOf(app("a", "Alpha")), listOf(app("a", "Alpha"))))
    }

    @Test
    fun `the same id with a different name IS a change`() {
        assertTrue(inventoryChanged(listOf(app("a", "Alpha")), listOf(app("a", "Alpha Renamed"))))
    }

    @Test
    fun `a different activity under the same id is a change`() {
        assertTrue(inventoryChanged(listOf(app("a", "Alpha")), listOf(app("a", "Alpha", act = "Other"))))
    }

    @Test
    fun `an app appearing is a change`() {
        assertTrue(inventoryChanged(listOf(app("a", "Alpha")), listOf(app("a", "Alpha"), app("b", "Beta"))))
    }

    @Test
    fun `an app going is a change`() {
        assertTrue(inventoryChanged(listOf(app("a", "Alpha"), app("b", "Beta")), listOf(app("a", "Alpha"))))
    }

    @Test
    fun `a renamed app among unchanged ones is still caught`() {
        val before = listOf(app("a", "Alpha"), app("b", "Beta"), app("c", "Gamma"))
        val after = listOf(app("a", "Alpha"), app("b", "Beta Renamed"), app("c", "Gamma"))
        assertTrue(inventoryChanged(before, after))
    }

    @Test
    fun `the order it came back in is not a change`() {
        val before = listOf(app("a", "Alpha"), app("b", "Beta"))
        assertFalse(inventoryChanged(before, listOf(app("b", "Beta"), app("a", "Alpha"))))
    }

    // ---- which icons are stale ----

    @Test
    fun `only the package that changed loses its icon`() {
        val before = listOf(app("a", "Alpha", pkg = "one"), app("b", "Beta", pkg = "two"))
        val after = listOf(app("a", "Alpha", pkg = "one"), app("b", "Beta Renamed", pkg = "two"))
        assertEquals(setOf("two"), changedPackages(before, after))
    }

    @Test
    fun `an app that is gone loses its icon`() {
        val before = listOf(app("a", "Alpha", pkg = "one"), app("b", "Beta", pkg = "two"))
        assertEquals(setOf("two"), changedPackages(before, listOf(app("a", "Alpha", pkg = "one"))))
    }

    @Test
    fun `nothing changed means no icons are thrown away`() {
        val same = listOf(app("a", "Alpha", pkg = "one"), app("b", "Beta", pkg = "two"))
        assertTrue(changedPackages(same, same).isEmpty())
    }

    @Test
    fun `an app that appeared gets its icon read`() {
        assertEquals(
            setOf("two"),
            changedPackages(listOf(app("a", "Alpha", pkg = "one")),
                            listOf(app("a", "Alpha", pkg = "one"), app("b", "Beta", pkg = "two")))
        )
    }
}
