package com.orbit.home.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * How to turn a decoded picture so it is the right way up: rotate clockwise first, then mirror
 * left-to-right.
 *
 * `BitmapFactory` ignores the EXIF orientation tag, so a portrait photograph from a phone camera
 * decodes lying on its side. Every picture this app brings in goes through here so the preview, the
 * framing screen and the stored result all agree with how the gallery shows the same file.
 */
internal data class OrientationFix(val rotationDegrees: Int, val mirrored: Boolean) {
    /** A quarter or three-quarter turn swaps the picture's width and height. */
    val swapsAxes: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270

    val isIdentity: Boolean get() = rotationDegrees == 0 && !mirrored
}

/**
 * The fix for an EXIF orientation value (1..8). Anything else, including a missing tag, is upright.
 *
 * Literal numbers rather than `ExifInterface` constants so this stays plain arithmetic that a JVM
 * test can check.
 */
internal fun orientationFix(exifOrientation: Int): OrientationFix = when (exifOrientation) {
    2 -> OrientationFix(0, true)     // mirrored
    3 -> OrientationFix(180, false)  // upside down
    4 -> OrientationFix(180, true)   // mirrored vertically
    5 -> OrientationFix(90, true)    // transpose
    6 -> OrientationFix(90, false)   // phone held upright, the common portrait case
    7 -> OrientationFix(270, true)   // transverse
    8 -> OrientationFix(270, false)
    else -> OrientationFix(0, false)
}

/**
 * How many times a picture may be halved while decoding.
 *
 * [mayHalve] is asked, with the size the picture *would* have after one more halving, whether that is
 * still big enough. The answer is a power of two, never zero, and capped so a predicate that always
 * says yes cannot loop forever.
 */
internal fun sampleSizeFor(width: Int, height: Int, mayHalve: (width: Int, height: Int) -> Boolean): Int {
    var sample = 1
    while (sample < MAX_SAMPLE && mayHalve(width / (sample * 2), height / (sample * 2))) sample *= 2
    return sample
}

private const val MAX_SAMPLE = 1 shl 12

/** Reads the EXIF orientation of [uri], or 1 (upright) when there is none or it cannot be read. */
internal fun readExifOrientation(context: Context, uri: Uri): Int =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

/**
 * [bitmap] turned by [fix]. Returns [bitmap] itself when nothing needs doing; otherwise the original
 * is recycled, so callers must not keep using it. Throws if the turned copy cannot be allocated,
 * because quietly returning a sideways picture is exactly the fault this exists to remove — callers
 * already treat a throw as "could not open that picture".
 */
internal fun applyOrientation(bitmap: Bitmap, fix: OrientationFix): Bitmap {
    if (fix.isIdentity) return bitmap
    val matrix = Matrix()
    if (fix.rotationDegrees != 0) matrix.postRotate(fix.rotationDegrees.toFloat())
    if (fix.mirrored) matrix.postScale(-1f, 1f)
    val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (turned != bitmap) bitmap.recycle()
    return turned
}

/**
 * Decodes [uri] the right way up, halved as far as [mayHalve] allows.
 *
 * The bounds are read first so a full-size camera photograph is never decoded whole, and the size
 * handed to [mayHalve] is the *upright* size, so a sideways file is judged by how big it will really
 * be. Null when the picture cannot be read.
 */
internal fun decodeUpright(
    context: Context,
    uri: Uri,
    mayHalve: (width: Int, height: Int) -> Boolean
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val fix = orientationFix(readExifOrientation(context, uri))
    val uprightWidth = if (fix.swapsAxes) bounds.outHeight else bounds.outWidth
    val uprightHeight = if (fix.swapsAxes) bounds.outWidth else bounds.outHeight
    val sample = sampleSizeFor(uprightWidth, uprightHeight, mayHalve)

    val decoded = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(
            stream,
            null,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } ?: return null
    return applyOrientation(decoded, fix)
}
