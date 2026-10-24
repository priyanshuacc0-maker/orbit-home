package com.orbit.home

import com.orbit.home.animation.WheelGeometry
import com.orbit.home.animation.angleDelta
import com.orbit.home.animation.normalizeAngle
import com.orbit.home.animation.wrapIndex
import com.orbit.home.preferences.HomeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Pure-logic tests for the wheel math. These run on the JVM (no device needed)
 * and pin down index wrapping, angle normalization and shortest-path deltas.
 */
class WheelMathTest {

    @Test
    fun wrapIndex_handlesNegativeAndOverflow() {
        assertEquals(0, wrapIndex(0, 5))
        assertEquals(2, wrapIndex(2, 5))
        assertEquals(0, wrapIndex(5, 5))
        assertEquals(1, wrapIndex(11, 5))
        assertEquals(4, wrapIndex(-1, 5))
        assertEquals(3, wrapIndex(-7, 5))
        assertEquals(0, wrapIndex(3, 0))
    }

    @Test
    fun normalizeAngle_staysWithinRange() {
        assertEquals(0f, normalizeAngle(0f), 1e-4f)
        assertEquals(1f, normalizeAngle(1f), 1e-4f)
        // A full turn collapses to zero.
        assertEquals(0f, normalizeAngle(6.2831855f), 1e-3f)
        // Values just above PI wrap to just above -PI.
        val wrapped = normalizeAngle(3.5f)
        assertEquals(-2.7831855f, wrapped, 1e-3f)
    }

    @Test
    fun angleDelta_takesShortestPathAcrossWrapBoundary() {
        // Crossing the +/-PI seam must not produce a near-full-circle jump.
        val across = angleDelta(3.0f, -3.0f)
        assertEquals(0.2831853f, across, 1e-3f)

        val backAcross = angleDelta(-3.0f, 3.0f)
        assertEquals(-0.2831853f, backAcross, 1e-3f)

        // Ordinary movement is unchanged.
        assertEquals(0.5f, angleDelta(1.0f, 1.5f), 1e-4f)
    }

    @Test
    fun angleDelta_isAntisymmetric() {
        val a = 2.7f
        val b = -2.9f
        assertEquals(-angleDelta(b, a), angleDelta(a, b), 1e-4f)
    }

    // --- shared geometry ---

    @Test
    fun wheelGeometry_slotZeroAtTopAndEvenlySpaced() {
        val g = WheelGeometry(size = 400f, iconScale = 1f, count = 12)
        val (x0, y0) = g.slotCenter(0)
        assertEquals(200f, x0, 0.5f)          // centred horizontally
        assertTrue(y0 < 200f)                  // above centre => 12 o'clock

        val (x1, y1) = g.slotCenter(1)
        val a0 = atan2(y0 - 200f, x0 - 200f)
        val a1 = atan2(y1 - 200f, x1 - 200f)
        assertEquals(g.step, normalizeAngle(a1 - a0), 1e-3f)
    }

    @Test
    fun wheelGeometry_visibleCountNeverExceedsTheAppsAvailable() {
        // Plenty of apps: the user's requested count is what shows.
        assertEquals(WheelGeometry.DEFAULT_SLOTS, WheelGeometry(400f, 1f, 40).visibleCount)
        assertEquals(WheelGeometry.DEFAULT_SLOTS, WheelGeometry(120f, 1f, 40).visibleCount)
        // Fewer apps than requested: only what exists is shown.
        assertEquals(3, WheelGeometry(400f, 1f, 3).visibleCount)
        assertEquals(1, WheelGeometry(400f, 1f, 1).visibleCount)
    }

    @Test
    fun dock_cap_isFive() {
        assertEquals(5, HomeSettings.DOCK_MAX)
    }

    // --- Phase 12: empty / single / odd / even counts ---

    @Test
    fun wheelGeometry_emptyAndSingleCounts() {
        assertEquals(0, WheelGeometry(400f, 1f, 0).visibleCount)
        assertEquals(1, WheelGeometry(400f, 1f, 1).visibleCount)
        assertEquals(2, WheelGeometry(400f, 1f, 2).visibleCount)
        // An empty wheel still yields a finite, positive step.
        assertTrue(WheelGeometry(400f, 1f, 0).step > 0f)
    }

    @Test
    fun wheelGeometry_oddAndEvenCountsSpacedEvenly() {
        for (n in intArrayOf(6, 7, 12)) {
            val g = WheelGeometry(400f, 1f, n)
            // Visible slots are capped by the user's request, never by geometry.
            assertEquals(minOf(n, g.requested), g.visibleCount)
            val (x0, y0) = g.slotCenter(0)
            val (x1, y1) = g.slotCenter(1)
            val a0 = atan2(y0 - 200f, x0 - 200f)
            val a1 = atan2(y1 - 200f, x1 - 200f)
            assertEquals(g.step, normalizeAngle(a1 - a0), 1e-3f)
        }
    }

    // --- brief, Phase 9: user-controlled slot count and scales ---

    @Test
    fun requestedSlotCountIsHonouredUpToAvailableApps() {
        // Ten asked for, six available: show six, honestly.
        assertEquals(6, WheelGeometry(400f, 1f, 6, requestedCount = 10).visibleCount)
        assertEquals(10, WheelGeometry(400f, 1f, 20, requestedCount = 10).visibleCount)
    }

    @Test
    fun everySupportedSlotCountYieldsThatCountWhenAppsAllow() {
        for (n in WheelGeometry.MIN_SLOTS..WheelGeometry.MAX_SLOTS) {
            val g = WheelGeometry(400f, 1f, 40, requestedCount = n)
            assertEquals(n, g.visibleCount)
            assertEquals((2.0 * PI).toFloat() / n, g.step, 1e-4f)
        }
    }

    @Test
    fun radiusScaleChangesRadiusOnly() {
        val base = WheelGeometry(400f, 1f, 12, requestedCount = 8)
        val bigger = WheelGeometry(400f, 1f, 12, requestedCount = 8, radiusScale = 1.18f)
        assertTrue(bigger.radius > base.radius)
        assertEquals(base.visibleCount, bigger.visibleCount)
        assertEquals(base.centerIconPx, bigger.centerIconPx, 1e-3f)
    }

    @Test
    fun centreScaleChangesCentreOnly() {
        val base = WheelGeometry(400f, 1f, 12, requestedCount = 8)
        val bigger = WheelGeometry(400f, 1f, 12, requestedCount = 8, centreScale = 1.6f)
        assertTrue(bigger.centerIconPx > base.centerIconPx)
        assertEquals(base.radius, bigger.radius, 1e-3f)
        assertEquals(base.visibleCount, bigger.visibleCount)
    }

    @Test
    fun extremeValuesStayFiniteAndInsideTheDrawableArea() {
        val g = WheelGeometry(
            size = 400f,
            iconScale = 1.35f,
            count = 40,
            requestedCount = WheelGeometry.MAX_SLOTS,
            radiusScale = WheelGeometry.MAX_RADIUS_SCALE,
            centreScale = WheelGeometry.MAX_CENTRE_SCALE,
            edgeMarginPx = 8f
        )
        assertTrue(g.radius.isFinite() && g.radius > 0f)
        assertTrue(g.iconPx.isFinite() && g.iconPx > 0f)
        assertTrue(g.centerIconPx.isFinite() && g.centerIconPx > 0f)
        assertTrue(g.radius + g.iconPx / 2f <= 200f)
    }

    @Test
    fun nonFinitePreferencesFallBackToDefaults() {
        val g = WheelGeometry(
            size = 400f,
            iconScale = 1f,
            count = 12,
            requestedCount = 8,
            radiusScale = Float.NaN,
            centreScale = Float.NaN
        )
        assertTrue(g.radius.isFinite())
        assertTrue(g.centerIconPx.isFinite())
    }

    @Test
    fun shortContainerStillKeepsTheRingInside() {
        val g = WheelGeometry(
            size = 180f,
            iconScale = 1f,
            count = 12,
            requestedCount = 12,
            radiusScale = 1.18f,
            edgeMarginPx = 8f
        )
        assertTrue(g.radius + g.iconPx / 2f <= 90f)
    }

    // --- Phase 12: wraparound at extremes ---

    @Test
    fun angleDelta_extremeValuesStayBounded() {
        val d = angleDelta(0f, 100f)
        assertTrue(d >= -PI.toFloat() && d <= PI.toFloat())
        assertEquals(0f, angleDelta(6.2831855f, 0f), 1e-3f)
    }

    @Test
    fun wrapIndex_largeAndNegativeValues() {
        assertEquals(3, wrapIndex(1_000_003, 10))
        assertEquals(7, wrapIndex(-1_000_003, 10))
    }

    @Test
    fun `the effective icon scale reports the spacing cap`() {
        // At 12 slots the arc spacing caps the icons, so asking for 135% does not
        // give 135% — which made the slider look dead (P1-07).
        val capped = WheelGeometry.effectiveIconScale(
            size = 1080f, iconScale = 1.35f, count = 20,
            requestedCount = 12, radiusScale = 1f, edgeMarginPx = 0f
        )
        assertTrue("expected a cap, got $capped", capped < 1.35f)

        // With room on the ring, the request is honoured exactly.
        val honoured = WheelGeometry.effectiveIconScale(
            size = 1080f, iconScale = 1.35f, count = 20,
            requestedCount = 4, radiusScale = 1f, edgeMarginPx = 0f
        )
        assertEquals(1.35f, honoured, 0.02f)
    }

    @Test
    fun `the widened ring range stays inside the drawable area`() {
        // The range was widened to 65-130%; the geometry still clamps (P1-07).
        for (scale in listOf(0.65f, 1.0f, 1.30f)) {
            val geo = WheelGeometry(size = 200f, iconScale = 1f, count = 12, radiusScale = scale)
            assertTrue(geo.radius + geo.iconPx / 2f <= 200f)
        }
    }
}
