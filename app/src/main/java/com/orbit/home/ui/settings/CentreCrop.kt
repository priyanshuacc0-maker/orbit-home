package com.orbit.home.ui.settings

import com.orbit.home.overlay.DockEdge

/**
 * Where the picture sits behind the frame, and how big it is.
 *
 * [offsetX] and [offsetY] are the picture's centre measured from the frame's centre, in view
 * pixels; [scale] is view pixels per picture pixel. Pure numbers, so the arithmetic that decides
 * which part of a picture ends up in the centre can be tested without a screen.
 */
data class CropTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

/**
 * The arithmetic of framing a picture.
 *
 * One rule is left, and it is about size: the picture may be scaled between the smallest the frame
 * offers and the largest. Everything else is free.
 *
 * There used to be two more, and both are gone. The picture could not be smaller than the frame,
 * which made the frame unable to show a small mark in the middle of a large circle — it could only
 * ever be cropped, never placed. And it could not be moved past its own edge, which stopped it
 * being put where it was wanted. Where a picture goes is the user's business, and the frame is only
 * a window onto it.
 */
object CropMath {

    /**
     * How much smaller than fitting the picture may be made.
     *
     * Not a hair below fitting: enough room that the whole picture can be seen comfortably inside
     * the frame with space around it, which is what placing a small mark needs.
     */
    const val MIN_ZOOM = 0.35f

    /**
     * The smallest scale at which the picture still covers the frame.
     *
     * Whichever side is relatively shorter decides: a wide picture is bound by its height and a
     * tall one by its width, and the larger of the two ratios is what leaves no gap.
     */
    fun coverScale(imageW: Int, imageH: Int, framePx: Float): Float {
        if (imageW <= 0 || imageH <= 0 || framePx <= 0f) return 1f
        return maxOf(framePx / imageW, framePx / imageH)
    }

    /** The largest scale at which the whole picture still fits inside the frame. */
    fun fitScale(imageW: Int, imageH: Int, framePx: Float): Float {
        if (imageW <= 0 || imageH <= 0 || framePx <= 0f) return 1f
        return minOf(framePx / imageW, framePx / imageH)
    }

    /** The smallest scale the frame offers. */
    fun floorScale(imageW: Int, imageH: Int, framePx: Float): Float =
        fitScale(imageW, imageH, framePx) * MIN_ZOOM

    /** A scale the frame can use. */
    fun clampScale(scale: Float, floor: Float, ceiling: Float): Float {
        if (!scale.isFinite()) return floor
        return scale.coerceIn(floor, maxOf(floor, ceiling))
    }

    /**
     * Where the picture's centre ends up when it is scaled by [ratio] about the point
     * ([focusX], [focusY]) and then moved by ([panX], [panY]).
     *
     * All measured from the frame's centre. The point under the fingers stays under the fingers
     * while pinching, which is what makes the framing land where it was aimed; zooming about the
     * picture's own centre instead makes the picture slide away from the fingers as it grows.
     */
    fun zoomAbout(
        offsetX: Float,
        offsetY: Float,
        focusX: Float,
        focusY: Float,
        ratio: Float,
        panX: Float,
        panY: Float
    ): Pair<Float, Float> = Pair(
        focusX + (offsetX - focusX) * ratio + panX,
        focusY + (offsetY - focusY) * ratio + panY
    )
}

/**
 * Where the arc of the half of a centre that stays on screen starts, in Compose's degrees
 * (0 at three o'clock, clockwise), with a sweep of 180.
 *
 * The floating wheel's centre sits on the screen edge it is docked to, so only the half facing into
 * the screen is ever seen — which half depends on the edge: the left half on the right edge, the
 * right half on the left edge, the lower half on the top edge and the upper half on the bottom edge.
 */
internal fun visibleArcStartAngle(edge: DockEdge): Float = when (edge) {
    DockEdge.RIGHT -> 90f   // bottom, round the left, to the top
    DockEdge.LEFT -> 270f   // top, round the right, to the bottom
    DockEdge.TOP -> 0f      // right, round the bottom, to the left
    DockEdge.BOTTOM -> 180f // left, round the top, to the right
}
