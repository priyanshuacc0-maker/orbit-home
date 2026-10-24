package com.orbit.home.apps

/**
 * The rule for ticking apps in a picker.
 *
 * Pure, so the cap is testable: the dock takes five and the wheel takes as many as
 * you like, and "the selection is full" has to behave the same way in both.
 */
object SelectionRules {

    /**
     * [selected] with [id] ticked or unticked.
     *
     * Adding is refused when the selection is already at [max]. The picker shows
     * that as a row you cannot tick rather than letting a tap do nothing quietly.
     */
    fun toggle(selected: List<String>, id: String, max: Int? = null): List<String> = when {
        id in selected -> selected.filterNot { it == id }
        max != null && selected.size >= max -> selected
        else -> selected + id
    }

    /** True when [id] could be added right now: not chosen, and there is room. */
    fun canAdd(selected: List<String>, id: String, max: Int? = null): Boolean =
        id !in selected && (max == null || selected.size < max)

    /**
     * True when tapping [id] would do something.
     *
     * A chosen app is always tappable, because tapping it *removes* it — using
     * [canAdd] for this made selected rows unresponsive, so an app could be added
     * to the wheel or dock but never taken off again.
     */
    fun canToggle(selected: List<String>, id: String, max: Int? = null): Boolean =
        id in selected || canAdd(selected, id, max)
}
