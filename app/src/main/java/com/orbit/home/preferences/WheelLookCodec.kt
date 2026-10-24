package com.orbit.home.preferences

import com.orbit.home.animation.WheelGeometry

/**
 * Storing one wheel's shape on its own.
 *
 * The floating wheel keeps its shape separately from the pages' wheels, because it is
 * drawn small at a screen edge and needs its own count and sizes. Forgiving on read,
 * like the pages' codec: a value that cannot be used is clamped rather than thrown
 * away, so a stored shape never leaves a wheel unable to draw.
 */
object WheelLookCodec {

    fun encode(look: WheelLook): String = listOf(
        look.visibleCount.toString(),
        look.iconScale.toString(),
        look.radiusScale.toString(),
        look.centreScale.toString(),
        look.offsetY.toString()
    ).joinToString("|")

    /** [fallback] when nothing is stored or the stored value cannot be read. */
    fun decode(raw: String?, fallback: WheelLook): WheelLook {
        if (raw.isNullOrBlank()) return fallback
        val p = raw.split("|")
        if (p.size < 5) return fallback
        val count = p[0].toIntOrNull() ?: return fallback
        val icon = p[1].toFloatOrNull() ?: return fallback
        val radius = p[2].toFloatOrNull() ?: return fallback
        val centre = p[3].toFloatOrNull() ?: return fallback
        val offset = p[4].toFloatOrNull() ?: return fallback
        return WheelLook(
            visibleCount = count.coerceIn(WheelGeometry.MIN_SLOTS, WheelGeometry.MAX_SLOTS),
            iconScale = icon.coerceIn(WheelLook.ICON_RANGE.start, WheelLook.ICON_RANGE.endInclusive),
            radiusScale = radius.coerceIn(WheelLook.RADIUS_RANGE.start, WheelLook.RADIUS_RANGE.endInclusive),
            centreScale = centre.coerceIn(WheelLook.CENTRE_RANGE.start, WheelLook.CENTRE_RANGE.endInclusive),
            offsetY = offset.coerceIn(-WheelLook.MAX_OFFSET, WheelLook.MAX_OFFSET)
        )
    }
}
