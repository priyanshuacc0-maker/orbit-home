package com.orbit.home.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

/**
 * A picture or a video frame, rendered as the framing screen showed it.
 *
 * One renderer for both, so a video cannot end up framed differently from the picture that was
 * framed with the same numbers on the same screen.
 *
 * Rendered rather than cut out of the original: the frame can be showing background as well as
 * picture — the picture may be smaller than it, or moved off it — and cutting a rectangle out of
 * the original could only ever produce picture.
 */
fun renderFramed(
    source: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    quarterTurns: Int,
    sidePx: Int
): ImageBitmap? {
    if (sidePx <= 0 || source.width <= 0 || source.height <= 0) return null
    val side = sidePx.coerceAtLeast(1)
    return runCatching {
        val turned = turn(source, quarterTurns)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(
                side / 2f + offsetX - turned.width * scale / 2f,
                side / 2f + offsetY - turned.height * scale / 2f
            )
        }
        canvas.drawBitmap(turned, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        out.asImageBitmap()
    }.getOrNull()
}

/**
 * The picture turned by whole quarters.
 *
 * Taken from the original every time rather than turned again from the last result: turning a
 * bitmap repeatedly loses a little each time, and four presses would show it.
 */
fun turn(source: Bitmap, quarterTurns: Int): Bitmap {
    val turns = ((quarterTurns % 4) + 4) % 4
    if (turns == 0) return source
    return runCatching {
        val matrix = Matrix().apply { postRotate(90f * turns) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }.getOrDefault(source)
}

/** A bitmap scaled down to fit [maxPx], for the platforms that cannot scale while decoding. */
fun scaledTo(bitmap: Bitmap, maxPx: Int): Bitmap {
    val widest = maxOf(bitmap.width, bitmap.height)
    if (widest <= maxPx) return bitmap
    val ratio = maxPx.toFloat() / widest
    return runCatching {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }.getOrDefault(bitmap)
}
