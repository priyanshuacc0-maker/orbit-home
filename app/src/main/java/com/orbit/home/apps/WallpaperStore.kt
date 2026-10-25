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
 * A wallpaper this app keeps for itself, in its own storage.
 *
 * Two pictures live here. The hidden wallpaper is one the launcher draws itself, which is what makes
 * switching to it instant, private to Orbit Home, and impossible to refuse: a wallpaper change is
 * global and slow and repaints every other app's background, and a triple tap should not do that.
 *
 * The other is a copy of the home screen wallpaper, kept because the phone's own cannot be read
 * back. Up to Android 12 reading it needed READ_EXTERNAL_STORAGE, from Android 13 the default
 * wallpaper is returned instead, and from Android 14 `WallpaperManager.getDrawable` always throws a
 * SecurityException unless the app holds MANAGE_EXTERNAL_STORAGE — which is a permission for file
 * managers and backup tools, and not one a launcher should ask for to draw a thumbnail. So the copy
 * written here, at the moment this app sets the wallpaper, is the only picture of it there is.
 */
class WallpaperStore(private val context: Context, private val fileName: String) {

    private fun file(): File = File(File(context.filesDir, DIRECTORY), fileName)

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

    companion object {
        /** The picture the launcher draws when the home screen is triple-tapped. */
        const val HIDDEN = "hidden.png"

        /** The home screen wallpaper, as this app last set it. */
        const val HOME = "home.png"

        private const val DIRECTORY = "wallpapers"
    }
}
