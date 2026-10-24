package com.orbit.home.preferences


/**
 * Reads and writes the list of wheels.
 *
 * One wheel per line, fields separated by `|` and the app ids by `;` — neither
 * character can appear in a component id, so nothing needs escaping.
 *
 *     v1|count|icon|radius|centre|offset|initialized|id;id;id
 *
 * The leading `v1` is there so a future change can recognise an older line rather
 * than misread it. Decoding is deliberately forgiving: a line that does not make
 * sense is skipped and the rest still load, because losing every wheel to one bad
 * character would be far worse than losing one.
 */
object WheelPagesCodec {

    private const val VERSION = "v1"
    /**
     * The version that added a wheel kind and its entries.
     *
     * Read, but no longer written. Those were shortcut wheels, and the feature has been
     * removed, so a line carrying one is recognised and dropped rather than misread as an
     * app wheel with a strange set of ids.
     */
    private const val VERSION_2 = "v2"
    private const val FIELD = "|"
    private const val ID = ";"

    fun encode(wheels: List<WheelPage>): String = wheels.joinToString("\n") { w ->
        listOf(
            VERSION,
            w.look.visibleCount.toString(),
            w.look.iconScale.toString(),
            w.look.radiusScale.toString(),
            w.look.centreScale.toString(),
            w.look.offsetY.toString(),
            if (w.initialized) "1" else "0",
            w.apps.joinToString(ID)
        ).joinToString(FIELD)
    }

    /** The stored wheels, or an empty list when nothing usable is stored. */
    fun decode(raw: String?): List<WheelPage> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("\n").mapNotNull { decodeLine(it) }
    }

    private fun decodeLine(line: String): WheelPage? {
        if (line.isBlank()) return null
        val parts = line.split(FIELD)
        val version = parts.firstOrNull() ?: return null
        // A line without one of our version markers is not ours; do not guess at it.
        if (version != VERSION && version != VERSION_2) return null

        // The migration. A v2 line is a wheel kind that no longer exists, so it is dropped
        // here rather than being read as an app wheel — its ids were shortcut keys, not
        // apps, and reading them as apps would give an empty ring that looked like a bug.
        // Dropping the wheel is the clean removal the user asked for; if that leaves none,
        // the caller falls back to a default app wheel, so the home screen is never empty.
        if (version == VERSION_2) return null

        val appsAt = 7
        if (parts.size <= appsAt) return null
        val count = parts[1].toIntOrNull() ?: return null
        val icon = parts[2].toFloatOrNull() ?: return null
        val radius = parts[3].toFloatOrNull() ?: return null
        val centre = parts[4].toFloatOrNull() ?: return null
        val offset = parts[5].toFloatOrNull() ?: return null
        val initialized = parts[6] == "1"
        val apps = parts[appsAt].split(ID).filter { it.isNotBlank() }
        return WheelPage(
            apps = apps,
            initialized = initialized,
            look = WheelLook(
                visibleCount = count.coerceIn(
                    com.orbit.home.animation.WheelGeometry.MIN_SLOTS,
                    com.orbit.home.animation.WheelGeometry.MAX_SLOTS
                ),
                iconScale = icon.coerceIn(WheelLook.ICON_RANGE.start, WheelLook.ICON_RANGE.endInclusive),
                radiusScale = radius.coerceIn(
                    WheelLook.RADIUS_RANGE.start,
                    WheelLook.RADIUS_RANGE.endInclusive
                ),
                centreScale = centre.coerceIn(
                    WheelLook.CENTRE_RANGE.start,
                    WheelLook.CENTRE_RANGE.endInclusive
                ),
                offsetY = offset.coerceIn(-WheelLook.MAX_OFFSET, WheelLook.MAX_OFFSET)
            )
        )
    }
}
