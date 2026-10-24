package com.orbit.home.ui.settings

/**
 * Where the Settings area is, in one value.
 *
 * A single route rather than a collection of unrelated Booleans. The launcher home screen is
 * [null] — no Settings destination at all — and every Settings page is a named member. That
 * makes "which page am I on" one question with one answer, which is what Back, the Android
 * Home button and the resume path all need.
 *
 * [HOME] is the Settings list itself; the rest are the pages it opens.
 */
enum class SettingsRoute {
    /** The Settings list: Apps, Dock apps, Apps wheel, Floating apps wheel, Wallpaper. */
    HOME,

    /** The installed-app drawer, opened from Settings. */
    APPS,

    /** Choosing which apps sit in the bottom dock. */
    DOCK,

    /** Customising a home-screen app wheel. */
    APPS_WHEEL,

    /** Customising the floating edge wheel. */
    FLOATING_WHEEL,

    /** Choosing what the wheels' centres show. */
    CENTRE,

    /** Choosing a wallpaper. */
    WALLPAPER;

    /** True for everything except the Settings list itself. */
    val isSubPage: Boolean get() = this != HOME
}
