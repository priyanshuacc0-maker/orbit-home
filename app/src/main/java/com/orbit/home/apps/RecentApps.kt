package com.orbit.home.apps

/**
 * Most-recently-used ordering for the home wheel.
 *
 * Pure and Android-free. The launcher records every launch it makes; the wheel then shows the apps it
 * would have shown, but with the ones used most recently first.
 */
object RecentApps {

    /** How many launches are remembered. Older ones fall off; those apps keep their normal place. */
    const val MAX_TRACKED = 60

    /** [recents] with [id] moved (or added) to the front, capped at [max]. */
    fun push(recents: List<String>, id: String, max: Int = MAX_TRACKED): List<String> =
        (listOf(id) + recents.filter { it != id }).take(max)

    /**
     * [apps] with the recently used ones first, most recent first. Apps that have never been launched
     * (or fell off the list) follow in the order they came in, so the result is stable and nothing is
     * ever dropped or duplicated.
     */
    fun order(apps: List<InstalledApp>, recents: List<String>): List<InstalledApp> {
        if (recents.isEmpty() || apps.size < 2) return apps
        val rank = HashMap<String, Int>(recents.size * 2)
        recents.forEachIndexed { i, id -> rank.putIfAbsent(id, i) }
        // sortedBy is stable: equal keys (everything unranked) keep their incoming order.
        return apps.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }
}
