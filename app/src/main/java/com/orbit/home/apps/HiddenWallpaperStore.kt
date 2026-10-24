package com.orbit.home.apps

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.orbit.home.ui.settings.frameImage
import com.orbit.home.ui.settings.screenFrameSize
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

    /**
     * Copies [uri] in, the right way up and cut to the screen's own shape and size.
     *
     * Framed here, with the same pipeline as the phone wallpaper and the settings previews, so what
     * the preview showed is what is stored and what is drawn: there is no second crop at draw time to
     * disagree with it. False when it could not be read.
     */
    suspend fun save(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (width, height) = screenFrameSize(context)
            val bitmap = frameImage(context, uri, width, height) ?: return@runCatching false
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

    private companion object {
        const val DIRECTORY = "wallpapers"
    }
}
