package com.orbit.home.ui

import com.orbit.home.apps.AppLabels

/** One lettered run of the drawer: every app whose name files under [key]. */
internal data class DrawerSection<T>(val key: String, val items: List<T>)

/**
 * The maths behind the drawer's section headers and A-Z scrubber, kept free of Compose so it can be
 * unit tested.
 *
 * The list is grouped by [AppLabels.sectionKey]. A group is by first appearance, so if the collator
 * ever files two runs of the same letter apart (punctuation is the usual culprit) they are merged
 * into one section rather than showing the same header twice.
 */
internal object DrawerIndex {

    /** Most letters the scrubber will carry. Beyond this (CJK names, say) the rail is not offered. */
    const val MAX_RAIL_KEYS = 32

    fun <T> sections(sorted: List<T>, labelOf: (T) -> String): List<DrawerSection<T>> =
        sorted.groupBy { AppLabels.sectionKey(labelOf(it)) }
            .map { (key, items) -> DrawerSection(key, items) }

    /** Which of [count] equal slots a touch [fraction] of the way down the rail is in; -1 if none. */
    fun slotAt(count: Int, fraction: Float): Int {
        if (count <= 0) return -1
        return (fraction * count).toInt().coerceIn(0, count - 1)
    }

    /** Where a section starts in a grid, which has no headers: the number of apps before it. */
    fun <T> gridIndex(sections: List<DrawerSection<T>>, key: String): Int {
        var at = 0
        for (s in sections) {
            if (s.key == key) return at
            at += s.items.size
        }
        return 0
    }

    /** Where a section's header sits in a list, which has one header per section. */
    fun <T> listIndex(sections: List<DrawerSection<T>>, key: String): Int {
        var at = 0
        for (s in sections) {
            if (s.key == key) return at
            at += 1 + s.items.size
        }
        return 0
    }
}
