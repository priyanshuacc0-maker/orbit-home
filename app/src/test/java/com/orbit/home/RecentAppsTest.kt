package com.orbit.home

import com.orbit.home.apps.InstalledApp
import com.orbit.home.apps.RecentApps
import org.junit.Assert.assertEquals
import org.junit.Test

/** The home wheel puts what was used last first. */
class RecentAppsTest {

    private fun app(id: String) = InstalledApp(
        id = id,
        packageName = id.substringBefore('/'),
        activityName = id.substringAfter('/'),
        label = id
    )

    private val apps = listOf(app("a/A"), app("b/B"), app("c/C"), app("d/D"))

    @Test
    fun `nothing launched keeps the original order`() {
        assertEquals(apps, RecentApps.order(apps, emptyList()))
    }

    @Test
    fun `recent apps come first, most recent first`() {
        val ordered = RecentApps.order(apps, listOf("c/C", "a/A"))
        assertEquals(listOf("c/C", "a/A", "b/B", "d/D"), ordered.map { it.id })
    }

    @Test
    fun `apps never launched keep their relative order`() {
        val ordered = RecentApps.order(apps, listOf("d/D"))
        assertEquals(listOf("d/D", "a/A", "b/B", "c/C"), ordered.map { it.id })
    }

    @Test
    fun `an uninstalled app in the history is ignored and nothing is duplicated`() {
        val ordered = RecentApps.order(apps, listOf("gone/G", "b/B", "b/B"))
        assertEquals(listOf("b/B", "a/A", "c/C", "d/D"), ordered.map { it.id })
    }

    @Test
    fun `launching moves an app to the front without repeating it`() {
        assertEquals(listOf("b", "a", "c"), RecentApps.push(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("z", "a", "b"), RecentApps.push(listOf("a", "b"), "z"))
    }

    @Test
    fun `history is capped`() {
        val history = (1..10).map { "app$it" }
        val pushed = RecentApps.push(history, "new", max = 5)
        assertEquals(listOf("new", "app1", "app2", "app3", "app4"), pushed)
    }
}
