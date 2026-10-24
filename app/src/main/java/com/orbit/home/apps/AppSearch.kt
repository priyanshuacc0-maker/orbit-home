package com.orbit.home.apps

import java.text.Normalizer

/**
 * Search over the installed-app list.
 *
 * Matching is case-insensitive, accent-insensitive and partial, but *ranked*: an
 * exact name beats a prefix, a prefix beats a match on a later word, an initials
 * match ("gm" for Gmail Maps) comes next, then a mid-string match, and finally a
 * match on the package name.
 *
 * Pure and Android-free, so the ordering is unit-testable (directive Phase 6).
 */
object AppSearch {

    /** [apps] filtered to matches for [query], best matches first. */
    fun filter(apps: List<InstalledApp>, query: String): List<InstalledApp> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return apps
        return apps
            .mapNotNull { app -> rank(app, trimmed)?.let { app to it } }
            .sortedWith(compareBy({ it.second }, { it.first.label.lowercase() }))
            .map { it.first }
    }

    /** Lower is better. Null means the app does not match at all. */
    fun rank(app: InstalledApp, query: String): Int? {
        val needle = fold(query)
        if (needle.isEmpty()) return 0
        val label = fold(app.label)
        return when {
            label == needle -> 0
            label.startsWith(needle) -> 1
            words(label).any { it.startsWith(needle) } -> 2
            // "gm" finds "Gmail Maps", and "sc" finds "Sound Cloud" (P2).
            initials(label) == needle -> 3
            label.contains(needle) -> 4
            // The package name, so "com.spotify" or "spotify" both find it (P2).
            app.packageName.lowercase().contains(needle) -> 5
            else -> null
        }
    }

    /** The label-only form, kept for callers that have no package name. */
    fun rank(label: String, query: String): Int? {
        val needle = fold(query)
        if (needle.isEmpty()) return 0
        val haystack = fold(label)
        return when {
            haystack == needle -> 0
            haystack.startsWith(needle) -> 1
            words(haystack).any { it.startsWith(needle) } -> 2
            initials(haystack) == needle -> 3
            haystack.contains(needle) -> 4
            else -> null
        }
    }

    /**
     * Lowercases and strips diacritics, so "emile" finds "Émile" and "uber" finds
     * "Über" (P2).
     */
    fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(COMBINING_MARKS, "")
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    private fun initials(label: String): String =
        words(label).mapNotNull { it.firstOrNull() }.joinToString("")

    private fun words(label: String): List<String> =
        label.split(' ', '-', '_', '.', '/').filter { it.isNotEmpty() }
}
