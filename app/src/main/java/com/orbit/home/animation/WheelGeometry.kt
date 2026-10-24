package com.orbit.home.animation

import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure-Kotlin wheel geometry for the Compose wheel. No Compose or Android
 * imports, so it can be unit-tested on the JVM.
 *
 * Coordinates are relative to a [size] x [size] square whose origin is
 * top-left. The ring is always centred in that square: the wheel's vertical
 * placement is applied by the renderer and by the gesture layer, never here.
 * Both must use the *same* effective centre — that shared centre is what the
 * brief's coordinate repair is about.
 */
class WheelGeometry(
    val size: Float,
    iconScale: Float = 1f,
    val count: Int,
    /**
     * Screen-space angle (radians) of slot 0. Defaults to -HALF_PI, i.e. 12
     * o'clock. Pass `Math.toRadians(focusAngleDegrees - 90)` to place the focus
     * slot at a user-chosen direction (0 deg = Top, clockwise).
     */
    val focusAngle: Float = -HALF_PI,
    /** How many slots around the ring the user asked for. */
    requestedCount: Int = DEFAULT_SLOTS,
    /** The user's orbital-radius preference. Not the icon size. */
    radiusScale: Float = 1f,
    /** The user's centre visual size preference. */
    centreScale: Float = 1f,
    /** Space kept clear between the ring and the drawable edge, in px. */
    edgeMarginPx: Float = 0f
) {
    private val half: Float = size / 2f
    private val usableHalf: Float = (half - edgeMarginPx).coerceAtLeast(1f)

    /** The requested count, clamped to the supported range. */
    val requested: Int = requestedCount.coerceIn(MIN_SLOTS, MAX_SLOTS)

    /**
     * Slots actually rendered: zero when there are no apps, otherwise never more
     * than the apps available, and never more than the user asked for.
     */
    val visibleCount: Int = if (count <= 0) 0 else minOf(count, requested)

    /** The ring icon size the user asked for. */
    private val preferredIconPx: Float =
        (size * 0.15f * iconScale.finiteOr(1f)).coerceAtLeast(size * MIN_ICON_FRACTION)

    /**
     * Orbital radius: the user's scale applied, then clamped so the ring and its
     * icons stay inside the drawable area whatever the container shape.
     *
     * The clamp leaves room for an icon's own half outside the ring. It used to be
     * [usableHalf], which let the ring grow until an icon on it no longer fitted — and
     * then [iconPx]'s edge cap shrank the icons to compensate. The effect was that past
     * about 115% the wheel-size control made the icons *smaller* the further it was
     * pushed, which is the opposite of what it says. Making room here instead means the
     * ring grows until the space is used up and then stops, and the icons keep the size
     * they were given.
     */
    val radius: Float = run {
        val scale = radiusScale.finiteOr(1f).coerceIn(MIN_RADIUS_SCALE, MAX_RADIUS_SCALE)
        // The largest the ring may be before an icon sitting on it stops fitting.
        val cap = (usableHalf - preferredIconPx / 2f).coerceAtLeast(size * MIN_ICON_FRACTION)
        // The smallest the control offers. Capped by [cap] so a wheel too small to hold both
        // cannot produce a control that runs backwards.
        val floor = (size * BASE_RADIUS_FRACTION * MIN_RADIUS_SCALE).coerceAtMost(cap)
        // The control's travel is mapped onto the space that actually exists, so that every
        // position on it moves the ring. Scaling the radius directly left the top of the
        // control doing nothing once the ring had reached [cap] — at the default icon size
        // that was the last two fifths of the slider, which read as a broken control.
        val t = ((scale - MIN_RADIUS_SCALE) / (MAX_RADIUS_SCALE - MIN_RADIUS_SCALE))
            .coerceIn(0f, 1f)
        floor + (cap - floor) * t
    }

    /**
     * Rendered ring icon size: the preference, capped by the arc spacing so
     * neighbours cannot overlap and by the space outside the ring, so moving a
     * slider to an extreme can never push icons out of bounds.
     */
    val iconPx: Float = run {
        val slots = visibleCount.coerceAtLeast(1)
        val bySpacing = ((TWO_PI * radius) / slots) / ICON_SPACING_SAFETY
        // Still here as a guarantee rather than as a working cap: the radius clamp above
        // already keeps half an icon clear, so this can only bite if the two ever disagree.
        val byEdge = (usableHalf - radius) * 2f
        preferredIconPx
            .coerceAtMost(bySpacing)
            .coerceAtMost(byEdge)
            .coerceAtLeast(size * MIN_ICON_FRACTION)
    }

    /** Centre visual (the logo, or the focused app icon), scaled by the user. */
    val centerIconPx: Float =
        size * 0.17f * centreScale.finiteOr(1f).coerceIn(MIN_CENTRE_SCALE, MAX_CENTRE_SCALE)

    /** The Orbit logo is drawn a little larger than a focused app icon. */
    val logoPx: Float = centerIconPx * (LOGO_FRACTION / 0.17f)

    val step: Float = if (visibleCount > 0) TWO_PI / visibleCount else TWO_PI

    /** Centre (x, y) of slot [j]; slot 0 sits at [focusAngle]. */
    fun slotCenter(j: Int, frac: Float = 0f): Pair<Float, Float> {
        val angle = focusAngle + (j - frac) * step
        return (half + radius * cos(angle)) to (half + radius * sin(angle))
    }

    /** A corrupt or non-finite preference must never poison the geometry. */
    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

    companion object {
        /**
         * The icon scale the ring can actually show, given how many slots it draws.
         *
         * Above a point the icon-size control stops doing anything, because the arc
         * spacing or the edge cap takes over. The slider looked dead, and nothing
         * said why (P1-07). Returns the requested scale unchanged when nothing is
         * capping.
         */
        fun effectiveIconScale(
            size: Float,
            iconScale: Float,
            count: Int,
            requestedCount: Int,
            radiusScale: Float,
            edgeMarginPx: Float
        ): Float {
            if (size <= 0f) return iconScale
            fun iconPxAt(scale: Float): Float = WheelGeometry(
                size = size,
                iconScale = scale,
                count = count,
                requestedCount = requestedCount,
                radiusScale = radiusScale,
                edgeMarginPx = edgeMarginPx
            ).iconPx
            val base = iconPxAt(1f)
            return if (base <= 0f) iconScale else iconPxAt(iconScale) / base
        }

        /**
         * How far the wheel may be moved vertically, as a fraction of the area
         * height.
         *
         * The old clamp was an arbitrary +/-0.3, which on a short screen — or with
         * a large icon size, or a long label — let the ring and its labels leave the
         * viewport (remediation 3.13). This derives the travel from what the wheel
         * actually occupies, so supported settings can never push interactive
         * content off-screen.
         *
         * @param areaHeightPx the height of the area the wheel lives in.
         * @param wheelContentPx the vertical extent the wheel needs: ring, icons
         *   and labels together.
         * @param topInsetPx / @param bottomInsetPx system bars and cutout, which the
         *   wheel must stay clear of.
         */
        fun maxOffsetFraction(
            areaHeightPx: Float,
            wheelContentPx: Float,
            topInsetPx: Float = 0f,
            bottomInsetPx: Float = 0f
        ): Float {
            if (!areaHeightPx.isFinite() || areaHeightPx <= 0f) return 0f
            val usable = areaHeightPx - topInsetPx.coerceAtLeast(0f) - bottomInsetPx.coerceAtLeast(0f)
            val spare = usable - wheelContentPx.coerceAtLeast(0f)
            if (!spare.isFinite() || spare <= 0f) return 0f
            // Half the spare at each end, expressed against the area height because
            // that is the unit the renderer applies the offset in.
            return (spare / 2f) / areaHeightPx
        }

        const val MIN_SLOTS = 4
        const val MAX_SLOTS = 12
        const val DEFAULT_SLOTS = 8

        /**
         * The smallest wheel size the control offers.
         *
         * This is the bottom of the wheel-size slider, and the two must agree: the control's
         * travel is mapped onto the space the ring actually has, so a bottom below what the
         * control can set would waste the first part of the travel. [WheelLookTest] holds the
         * two together.
         */
        const val MIN_RADIUS_SCALE = 0.82f
        const val MAX_RADIUS_SCALE = 1.30f
        const val DEFAULT_RADIUS_SCALE = 1f

        const val MIN_CENTRE_SCALE = 0.70f
        const val MAX_CENTRE_SCALE = 1.60f
        const val DEFAULT_CENTRE_SCALE = 1f

        private const val BASE_RADIUS_FRACTION = 0.355f
        // Icons may use most of the arc before the cap bites, so the icon-size
        // control stays effective rather than being silently clamped.
        private const val ICON_SPACING_SAFETY = 1.12f
        private const val MIN_ICON_FRACTION = 0.06f
        private const val LOGO_FRACTION = 0.22f
    }
}
