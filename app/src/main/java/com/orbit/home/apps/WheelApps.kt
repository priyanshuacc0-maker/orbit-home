package com.orbit.home.apps

import com.orbit.home.preferences.WheelPage

/**
 * Which apps the wheel shows.
 *
 * Kept pure and Android-free so the rule is unit-testable, and applied in one place
 * so the wheel and the picker can never disagree about what is on the ring.
 */
object WheelApps {

    /**
     * The wheel's contents.
     *
     * Until the user has chosen, the wheel shows **every** installed app — so a new
     * install appears without anyone having to curate anything. Once they have
     * chosen, it shows exactly those apps, in the order they were picked, minus any
     * that are no longer installed. An app that is missing is simply skipped; the
     * rest keep their order, so uninstalling one does not shuffle the ring.
     */
    fun resolve(allApps: List<InstalledApp>, page: WheelPage): List<InstalledApp> {
        if (!page.initialized) return allApps
        val byId = allApps.associateBy { it.id }
        return page.apps.mapNotNull { byId[it] }
    }

    /**
     * The picker's starting selection: everything currently on the wheel.
     */
    fun initialSelection(allApps: List<InstalledApp>, page: WheelPage): List<String> =
        resolve(allApps, page).map { it.id }
}
