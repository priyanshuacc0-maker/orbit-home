package com.orbit.home.apps

import java.text.Collator
import java.util.Locale

/**
 * Label ordering and drawer sectioning.
 *
 * `String.CASE_INSENSITIVE_ORDER` compares UTF-16 code units, so "Émile" sorted
 * after "Zoe", digits came before letters, and non-Latin scripts landed in
 * code-point order rather than anything a reader recognises. A [Collator] for the
 * device locale handles accents, case and script properly (P0-12).
 *
 * Pure and Android-free, so the ordering is unit-testable.
 */
object AppLabels {

    /** Orders labels the way the device's locale would. */
    fun comparator(locale: Locale = Locale.getDefault()): Comparator<String> {
        val collator = Collator.getInstance(locale).apply {
            // Accents and case are secondary: "Émile" sorts with "Emile".
            strength = Collator.SECONDARY
        }
        return Comparator { a, b ->
            val primary = collator.compare(a, b)
            // A stable tie-break, so equal-looking labels never swap between runs.
            if (primary != 0) primary else a.compareTo(b)
        }
    }

    /** Sorts a list of labels in place-equivalent order. */
    fun sorted(labels: List<String>, locale: Locale = Locale.getDefault()): List<String> =
        labels.sortedWith(comparator(locale))

    /**
     * The letter a label files under in the drawer. Anything that is not a letter
     * — a digit, punctuation, an empty label — files under "#".
     */
    fun sectionKey(label: String): String {
        val first = label.trim().firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }
}
