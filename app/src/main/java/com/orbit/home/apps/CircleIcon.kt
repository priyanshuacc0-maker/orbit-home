package com.orbit.home.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Draws any app or shortcut icon as a circle, the same circle, on every phone.
 *
 * The system hands out icons it has already shaped. On a phone that forces squircles, rounded
 * squares or teardrops, the drawable returned by `PackageManager.getActivityIcon` is the *masked*
 * icon — the mask is applied before the app ever sees it — so drawing it inside a circle of our
 * own put a squircle inside a circle, and the icons looked different on every phone.
 *
 * The way out is not to ask for the finished icon. Reading the icon resource an app declares gives
 * the unmasked layers, and the shaping is then ours to do: one circle, at one size, whatever the
 * phone would otherwise have forced.
 *
 * The *size* is measured rather than assumed. An icon's artwork arrives with whatever padding its
 * author chose — some fill their square, some float a small mark in the middle — and no single
 * fixed inset can line those up, because it cannot tell how much padding was already there. So the
 * artwork is measured: it is drawn, the edges of what actually appeared are found, and it is grown
 * from there to a common size. An icon already at that size is left exactly as it is.
 */
object CircleIcon {

    /**
     * An adaptive icon is authored on a 108-unit canvas of which the middle 72 is the visible
     * area. These are the platform's own numbers, and they are why an adaptive icon drawn into a
     * plain square looks smaller than the square it was given.
     */
    const val ADAPTIVE_CANVAS = 108f
    const val ADAPTIVE_VISIBLE = 72f

    /**
     * How much of the circle the artwork's diagonal should fill once measured.
     *
     * Measured as a diagonal rather than as a width so that a wide logo and a square one read as
     * the same size, and so that neither is pushed out of the circle: a square whose diagonal is
     * this fraction of the circle sits inside it with room to spare.
     */
    const val ART_DIAGONAL = 0.92f

    /**
     * The most an icon may be enlarged.
     *
     * A glyph that is genuinely tiny would otherwise be stretched until it was soft and wrong.
     * Better that it stays a little small than that it looks broken.
     */
    const val MAX_GROWTH = 2f

    /**
     * How faint a pixel may be and still count as artwork when measuring.
     *
     * Above zero so that anti-aliasing at the very edge of a shape, and the faint full-canvas haze
     * some foreground layers carry, do not read as artwork and defeat the measurement.
     */
    private const val ALPHA_FLOOR = 10

    /** The square an adaptive icon's layers are drawn into, for a circle [px] across. */
    fun adaptiveCanvasPx(px: Int): Int = (px * ADAPTIVE_CANVAS / ADAPTIVE_VISIBLE).roundToInt()

    /** Where that square starts, so its visible middle lands exactly on the circle. */
    fun adaptiveOriginPx(px: Int): Int = -((adaptiveCanvasPx(px) - px) / 2)

    /** The diagonal of measured artwork. Uses both sides, so a wide icon is not read as small. */
    fun contentDiagonalPx(width: Int, height: Int): Float =
        if (width <= 0 || height <= 0) 0f else hypot(width.toFloat(), height.toFloat())

    /**
     * How much to enlarge measured artwork so it sits at the same size as everything else.
     *
     * Never below 1: an icon that already fills its circle is left exactly as it is, because
     * shrinking the ones that look right in order to make room for the ones that do not would be
     * fixing the wrong end.
     */
    fun growthFor(contentDiagonal: Float, circlePx: Int): Float {
        if (contentDiagonal <= 0f || circlePx <= 0) return 1f
        return ((circlePx * ART_DIAGONAL) / contentDiagonal).coerceIn(1f, MAX_GROWTH)
    }

    /**
     * Renders [drawable] as a circle [px] across, or null if there is nothing to draw.
     *
     * Fail-soft on purpose: an icon that cannot be drawn is a missing icon, never a crash.
     */
    fun render(drawable: Drawable?, px: Int): Bitmap? {
        if (drawable == null || px <= 0) return null
        return runCatching {
            val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.clipPath(
                Path().apply { addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW) }
            )
            when (drawable) {
                is AdaptiveIconDrawable -> drawAdaptive(drawable, canvas, px)
                else -> drawFlat(drawable, canvas, px)
            }
            out
        }.getOrNull()
    }

    /**
     * The two layers, drawn by hand rather than by the drawable.
     *
     * `AdaptiveIconDrawable.draw()` applies its own mask, which is the very thing being replaced
     * here, so the background and the foreground are taken separately and placed in the canvas the
     * platform defines for them.
     *
     * Only the foreground is measured. A background is meant to fill the circle, so growing it
     * would achieve nothing; the foreground is where the artwork lives.
     */
    private fun drawAdaptive(adaptive: AdaptiveIconDrawable, canvas: Canvas, px: Int) {
        val size = adaptiveCanvasPx(px)
        val origin = adaptiveOriginPx(px)
        val bounds = Rect(origin, origin, origin + size, origin + size)

        adaptive.background?.let {
            it.bounds = bounds
            it.draw(canvas)
        }

        val foreground = adaptive.foreground ?: return
        val growth = growthOf(foreground, boxPx = size, circlePx = px)
        if (growth <= 1f) {
            foreground.bounds = bounds
            foreground.draw(canvas)
            return
        }
        // Grown about the middle of the circle, which is also the middle of the canvas.
        canvas.save()
        canvas.scale(growth, growth, px / 2f, px / 2f)
        foreground.bounds = bounds
        foreground.draw(canvas)
        canvas.restore()
    }

    /** A flat picture — or a bare shortcut glyph — measured and grown the same way. */
    private fun drawFlat(drawable: Drawable, canvas: Canvas, px: Int) {
        val growth = growthOf(drawable, boxPx = px, circlePx = px)
        if (growth > 1f) {
            canvas.save()
            canvas.scale(growth, growth, px / 2f, px / 2f)
        }
        drawable.bounds = Rect(0, 0, px, px)
        drawable.draw(canvas)
        if (growth > 1f) canvas.restore()
    }

    /**
     * Draws [drawable] once into a throwaway square, finds what actually appeared, and works out
     * how far it should be grown.
     *
     * The probe is discarded. It costs one bitmap and one pass over its pixels, once per icon per
     * size — the result is cached by the loader, so this never runs while the wheel is turning.
     */
    private fun growthOf(drawable: Drawable, boxPx: Int, circlePx: Int): Float {
        if (boxPx <= 0) return 1f
        val probe = Bitmap.createBitmap(boxPx, boxPx, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(probe)
            drawable.bounds = Rect(0, 0, boxPx, boxPx)
            drawable.draw(canvas)
            val content = contentBounds(probe) ?: return 1f
            return growthFor(
                contentDiagonalPx(content.width(), content.height()),
                circlePx
            )
        } finally {
            probe.recycle()
        }
    }

    /**
     * The smallest rectangle containing anything that was drawn, or null if nothing was.
     *
     * A single pass over the pixels: the artwork's own extent is the only thing that can be
     * measured about it without knowing what it is.
     */
    private fun contentBounds(bitmap: Bitmap): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var top = -1
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((pixels[row + x] ushr 24) > ALPHA_FLOOR) {
                    if (top < 0) top = y
                    bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        if (right < left || bottom < top) return null
        return Rect(left, top, right + 1, bottom + 1)
    }
}
