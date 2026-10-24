package com.orbit.home.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The hidden wallpaper: a picture the launcher draws itself.
 *
 * Drawn by the launcher rather than set as the phone's wallpaper, which is what makes switching to
 * it instant, private to Orbit Home, and impossible to refuse. A wallpaper change is global and
 * slow and repaints every other app's background; a triple tap on the home screen should not do
 * that, and should not need anybody's permission to.
 */
class HiddenWallpaperStore(private val context: Context) {

    private fun file(): File = File(File(context.filesDir, DIRECTORY), "hidden.png")

    /** Copies [uri] in. False when it could not be read. */
    suspend fun save(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(uri, MAX_PX) ?: return@runCatching false
            try {
                AtomicPngWriter.write(file(), bitmap)
            } finally {
                bitmap.recycle()
            }
        }.getOrDefault(false)
    }

    /** Stores an already-decoded picture. Used by the framing screen's result. */
    suspend fun save(image: ImageBitmap): Boolean = withContext(Dispatchers.IO) {
        runCatching { AtomicPngWriter.write(file(), image.asAndroidBitmap()) }.getOrDefault(false)
    }

    /** The stored picture, or null if there is none — including if it went missing. */
    suspend fun load(): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file()
            if (!f.isFile) return@runCatching null
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    /** Forgets it. */
    suspend fun remove() = withContext(Dispatchers.IO) {
        runCatching { file().delete() }
        Unit
    }

    /** Whether one is set, without decoding it. */
    fun isSet(): Boolean = file().let { it.isFile && it.length() > 0L }

    private fun decode(uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val widest = maxOf(bounds.outWidth, bounds.outHeight)
        if (widest <= 0) return null
        var sample = 1
        while (widest / (sample * 2) >= maxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private companion object {
        const val DIRECTORY = "wallpapers"

        /**
         * Bounded on the way in. A phone wallpaper is drawn on a screen a few thousand pixels
         * across, so there is nothing to be gained from keeping a picture larger than that — and a
         * launcher holding a full-resolution camera frame to paint a background is how it runs out
         * of memory on the screen it is always showing.
         */
        const val MAX_PX = 2160
    }
}
