package com.orbit.home.apps

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Immutable description of a single launchable application entry.
 *
 * [id] is a stable identity derived from the component (package + activity) so
 * that recents, the curated selection and list ordering survive refreshes.
 *
 * [icon] is optional: the repository returns labels only, and icons are loaded
 * lazily (see [AppIconLoader]) for just the slots and rows on screen.
 */
data class InstalledApp(
    val id: String,
    val packageName: String,
    val activityName: String?,
    val label: String,
    val icon: ImageBitmap? = null
)

/**
 * Whether what the launcher would draw has changed between two scans of what is installed.
 *
 * Comparing ids alone misses the case this exists for: an app updates, its name changes, and its launch
 * component stays exactly the same - so the id list is identical and the screen goes on showing the old
 * name. The fields compared are the ones that reach the screen: the id, the label, and the package and
 * activity that identify it.
 *
 * The icon is deliberately not compared. It is a decoded bitmap that the loader replaces in place, so
 * comparing it would report a change on every scan and make this say nothing.
 */
fun inventoryChanged(before: List<InstalledApp>, after: List<InstalledApp>): Boolean {
    if (before.size != after.size) return true
    val was = before.associateBy { it.id }
    for (app in after) {
        val old = was[app.id] ?: return true
        if (old.label != app.label) return true
        if (old.packageName != app.packageName) return true
        if (old.activityName != app.activityName) return true
    }
    return false
}

/**
 * Which packages no longer have the right icon, given what changed between two scans.
 *
 * An app that appeared or was updated needs its icon read again; an app that is gone needs the one held
 * for it dropped. Everything else keeps what it has, because re-reading every icon on the phone is what
 * makes a launcher feel slow for no reason.
 */
fun changedPackages(before: List<InstalledApp>, after: List<InstalledApp>): Set<String> {
    val out = LinkedHashSet<String>()
    val thenById = before.associateBy { it.id }
    val nowById = after.associateBy { it.id }
    for (app in after) {
        val old = thenById[app.id]
        if (old == null || old.label != app.label || old.activityName != app.activityName) {
            out.add(app.packageName)
        }
    }
    for (app in before) if (app.id !in nowById) out.add(app.packageName)
    return out
}
