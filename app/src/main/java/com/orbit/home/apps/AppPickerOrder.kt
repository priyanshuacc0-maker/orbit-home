package com.orbit.home.apps

/**
 * How the wheel's app picker is ordered.
 *
 * The apps already on the wheel come first, in ring order, so their numbers read
 * 1, 2, 3 down the list; everything else follows alphabetically. Pure, so the split
 * is testable rather than something you have to open the screen to check.
 */
object AppPickerOrder {

    /**
     * The picker's list.
     *
     * A chosen id that is no longer installed is skipped, and an app already chosen
     * never appears a second time in the alphabetical run.
     */
    fun ordered(allApps: List<InstalledApp>, chosen: List<String>): List<InstalledApp> {
        val byId = allApps.associateBy { it.id }
        val chosenApps = chosen.mapNotNull { byId[it] }
        val alreadyChosen = chosen.toSet()
        val rest = allApps
            .filterNot { it.id in alreadyChosen }
            .sortedWith(compareBy(AppLabels.comparator()) { it.label })
        return chosenApps + rest
    }
}
