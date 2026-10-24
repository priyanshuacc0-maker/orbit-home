package com.orbit.home.shortcuts

/**
 * One shortcut an installed app publishes, as stored.
 *
 * Deliberately only identifiers and the label: the package and the shortcut id are stable
 * across restarts, while an icon is a Drawable that belongs to a live query and must never
 * be persisted. See [ResolvedShortcut] for the live shape that carries icons.
 */
data class ShortcutItem(
    val packageName: String,
    val shortcutId: String,
    val label: String
) {
    /** Package and shortcut together, which is what starting one needs and what is stored. */
    val key: String get() = "$packageName#$shortcutId"

    companion object {
        /**
         * Splits a stored key of the form `package#shortcutId`.
         *
         * Returns null when the key is not in that shape, so a caller cannot accidentally
         * try to start something that was never a shortcut.
         */
        fun split(key: String): Pair<String, String>? {
            val at = key.indexOf('#')
            if (at <= 0 || at == key.length - 1) return null
            return key.substring(0, at) to key.substring(at + 1)
        }

        /** The stored key back into an item, or null if it is malformed. */
        fun fromKey(key: String, label: String = ""): ShortcutItem? {
            val (packageName, shortcutId) = split(key) ?: return null
            return ShortcutItem(packageName, shortcutId, label)
        }
    }
}

/**
 * A shortcut as it exists right now, with the icons a live query can give.
 *
 * Kept apart from [ShortcutItem] so nothing ever stores a Drawable or a ShortcutInfo: those
 * belong to the moment they were read and go stale the instant an app is updated.
 */
data class ResolvedShortcut(
    val item: ShortcutItem,
    /** The name of the app that published it, for grouping and for the picker's rows. */
    val appLabel: String,
    /** The app's own icon, the fallback when the shortcut has none. */
    val appIcon: android.graphics.drawable.Drawable?,
    /** The individual shortcut's icon, which is the one worth showing. */
    val shortcutIcon: android.graphics.drawable.Drawable?
) {
    val key: String get() = item.key
    /** Whichever icon is best: the shortcut's own, then the app's. */
    val icon: android.graphics.drawable.Drawable? get() = shortcutIcon ?: appIcon
}
