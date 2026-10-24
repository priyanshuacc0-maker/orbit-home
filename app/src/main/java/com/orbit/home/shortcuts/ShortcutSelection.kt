package com.orbit.home.shortcuts

/**
 * Which of the saved shortcuts still exist.
 *
 * Kept pure and separate from the picker and the wheel, because it is the rule that decides
 * what the user sees and it is worth being able to test without a device.
 *
 * The governing principle, from the brief and from common sense: a failed or unavailable
 * catalog must never erase a selection. A shortcut that cannot be resolved right now might be
 * resolvable in a minute — the app may be updating, the profile may be locked — so this
 * reports what is missing rather than deciding to forget it.
 */
object ShortcutSelection {

    /**
     * The saved keys resolved against what is currently published, in the saved order.
     *
     * Order is the user's, not the catalog's: they chose these in a sequence and the ring
     * should show them that way. Keys with no match are left out of the result rather than
     * filled with a placeholder, because a ring of dead icons is worse than a shorter ring.
     */
    fun resolve(saved: List<String>, available: List<ResolvedShortcut>): List<ResolvedShortcut> {
        val byKey = available.associateBy { it.key }
        return saved.mapNotNull { byKey[it] }
    }

    /** The saved keys with no match right now, in the saved order. */
    fun missing(saved: List<String>, available: List<ResolvedShortcut>): List<String> {
        val present = available.mapTo(mutableSetOf()) { it.key }
        return saved.filterNot { it in present }
    }

    /**
     * Adds a key, keeping the order and refusing a duplicate.
     *
     * Returns the list unchanged when the key is already there, so a double tap on a row in the
     * picker cannot produce two identical entries on the ring.
     */
    fun add(saved: List<String>, key: String): List<String> =
        if (key in saved) saved else saved + key

    /** Removes a key, keeping the order of the rest. */
    fun remove(saved: List<String>, key: String): List<String> = saved.filterNot { it == key }

    /**
     * The saved keys grouped by package, with each package's shortcut ids in saved order.
     *
     * This is what lets the ring ask for the shortcuts it actually wants instead of reading every
     * shortcut of every installed app. Order is preserved and duplicates are dropped, and a key
     * that is not `package#id` is skipped rather than guessed at.
     */
    fun idsByPackage(saved: List<String>): Map<String, List<String>> {
        val grouped = LinkedHashMap<String, MutableList<String>>()
        for (key in saved) {
            val (pkg, id) = ShortcutItem.split(key) ?: continue
            val ids = grouped.getOrPut(pkg) { mutableListOf() }
            if (id !in ids) ids += id
        }
        return grouped
    }

    /** Flips one key, which is what a row in the picker does. */
    fun toggle(saved: List<String>, key: String): List<String> =
        if (key in saved) remove(saved, key) else add(saved, key)
}
