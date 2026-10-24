package com.orbit.home.preferences

/**
 * Adding and removing wheels.
 *
 * Pure, so the two rules that decide what a new wheel looks like and which wheels
 * may go are testable without a device or a running store.
 */
object WheelPages {

    /**
     * The wheels with one appended, shaped like the wheel on [fromIndex].
     *
     * A new wheel copies the *shape* — slot count, sizes, position — so adding one
     * does not mean re-doing the setup. It deliberately does not copy the apps: a
     * fresh wheel to curate is more useful than a duplicate of the one it came from.
     *
     * Returns [wheels] unchanged once [max] are already there.
     */
    fun appendLike(
        wheels: List<WheelPage>,
        fromIndex: Int,
        max: Int = HomeSettings.MAX_WHEELS
    ): List<WheelPage> {
        if (wheels.size >= max) return wheels
        val shape = wheels.getOrNull(fromIndex)?.look ?: WheelLook()
        return wheels + WheelPage(look = shape)
    }

    /**
     * The wheels with [index] removed.
     *
     * The first wheel is the home screen's own and is never removed, so there is
     * always something to come back to. An index that is not there changes nothing.
     */
    fun removeAt(wheels: List<WheelPage>, index: Int): List<WheelPage> {
        if (index <= 0 || index !in wheels.indices) return wheels
        return wheels.filterIndexed { i, _ -> i != index }
    }
}
