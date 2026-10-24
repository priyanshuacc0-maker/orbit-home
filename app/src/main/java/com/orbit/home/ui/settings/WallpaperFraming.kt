package com.orbit.home.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.WindowManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The part of a picture to keep, in the picture's own pixels.
 *
 * A plain data class rather than a `Rect`, because this is the arithmetic that decides whether a
 * wallpaper reaches the edges of the screen, and arithmetic that decides something should be
 * testable without a device.
 */
internal data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * The largest centred part of a [srcWidth] x [srcHeight] picture that has the shape of the frame.
 *
 * This is "cover": the picture is never letterboxed, so the result always has the frame's shape and
 * the frame is always filled. A picture wider than the frame loses its sides; one taller loses its
 * top and bottom. Either way what is cut comes off both sides equally, so the middle of the picture
 * stays the middle of the wallpaper.
 *
 * A degenerate size is returned unchanged rather than divided by, because a wallpaper that is merely
 * the wrong shape is a much better outcome than a crash while setting one.
 */
internal fun coverCrop(srcWidth: Int, srcHeight: Int, frameWidth: Int, frameHeight: Int): Crop {
    val whole = Crop(0, 0, srcWidth.coerceAtLeast(0), srcHeight.coerceAtLeast(0))
    if (srcWidth <= 0 || srcHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) return whole

    val frameRatio = frameWidth.toFloat() / frameHeight
    val srcRatio = srcWidth.toFloat() / srcHeight

    return if (srcRatio > frameRatio) {
        // Wider than the frame: keep the full height and trim both sides.
        val width = (srcHeight * frameRatio).roundToInt().coerceIn(1, srcWidth)
        Crop((srcWidth - width) / 2, 0, width, srcHeight)
    } else {
        // Taller than the frame: keep the full width and trim top and bottom.
        val height = (srcWidth / frameRatio).roundToInt().coerceIn(1, srcHeight)
        Crop(0, (srcHeight - height) / 2, srcWidth, height)
    }
}

/**
 * The real size of the screen in pixels, portrait: system bars included, because the wallpaper is
 * drawn behind them. Width is always the shorter side, since this launcher is portrait-only.
 *
 * This, and not WallpaperManager.desiredMinimumWidth, is what the wallpaper has to fit. That value is
 * whatever the previously installed launcher asked for — often twice the screen's width, so its
 * wallpaper can scroll — and framing to it makes a picture wider than the screen.
 */
internal fun screenFrameSize(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
        val b = wm.maximumWindowMetrics.bounds
        b.width() to b.height()
    } else {
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        m.widthPixels to m.heightPixels
    }
    return minOf(w, h).coerceAtLeast(1) to maxOf(w, h).coerceAtLeast(1)
}

/**
 * The picture, cut to the shape and size the wallpaper is asked for.
 *
 * This is the whole of what was missing. The picture used to be handed to the system as it arrived,
 * with no crop hint and nothing said about its size, so a photograph whose proportions did not match
 * the screen came out short of the edges — and the home screen, which draws the wallpaper through its
 * own window with a transparent background, showed whatever was underneath in the gap.
 *
 * The size asked for is the screen's own, so the picture's centre is the screen's centre.
 */
internal suspend fun frameForWallpaper(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val (frameWidth, frameHeight) = screenFrameSize(context)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        // Never decoded larger than the wallpaper can use: a modern camera's photograph at full size
        // is many times this, and decoding it whole is how setting a wallpaper runs out of memory.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= frameWidth && bounds.outHeight / (sample * 2) >= frameHeight) {
            sample *= 2
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } ?: return@runCatching null

        val crop = coverCrop(decoded.width, decoded.height, frameWidth, frameHeight)
        val cut = Bitmap.createBitmap(decoded, crop.left, crop.top, crop.width, crop.height)
        if (cut != decoded) decoded.recycle()

        // Exactly the size asked for, so nothing about the framing is left to anyone else's judgement.
        val framed = Bitmap.createScaledBitmap(cut, frameWidth, frameHeight, true)
        if (framed != cut) cut.recycle()
        framed
    }.getOrNull()
}
