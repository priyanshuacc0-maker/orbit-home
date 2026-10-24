package com.orbit.home.shortcuts

/**
 * The picker's two pure rules: what matches a search, and how entries group by app.
 *
 * Kept out of the UI so both can be tested without a device — they decide what the user sees, and
 * a mistake in either is invisible until someone cannot find a shortcut they know exists.
 */
object ShortcutPickerModel {

    /** One app's shortcuts, in the order the catalog gave them. */
    data class Group(
        val packageName: String,
        val appLabel: String,
        val items: List<ResolvedShortcut>
    )

    /**
     * Entries matching a search, by shortcut label or by app name.
     *
     * Matching the app name matters: someone looking for "Maps" expects every Maps shortcut, not
     * only the ones with "Maps" in their own label.
     */
    fun filter(all: List<ResolvedShortcut>, query: String): List<ResolvedShortcut> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.item.label.lowercase().contains(q) || it.appLabel.lowercase().contains(q)
        }
    }

    /** Groups by source app, preserving the order the catalog produced. */
    fun group(items: List<ResolvedShortcut>): List<Group> =
        items.groupBy { it.item.packageName }
            .map { (pkg, list) -> Group(pkg, list.first().appLabel, list) }
}
