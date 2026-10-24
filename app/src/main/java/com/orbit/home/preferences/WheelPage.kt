package com.orbit.home.preferences

/**
 * One wheel on the home screen.
 *
 * Every wheel is independent: its own apps, its own slot count, its own sizes and
 * its own position. A second wheel can therefore be a completely different ring
 * from the first, which is the whole point of being able to add one.
 */
data class WheelPage(
    /** Apps chosen for this wheel, in the order they were picked. */
    val apps: List<String> = emptyList(),
    /** True once the user has chosen, so an untouched wheel still shows everything. */
    val initialized: Boolean = false,
    /** This wheel's own shape. */
    val look: WheelLook = WheelLook()
)
