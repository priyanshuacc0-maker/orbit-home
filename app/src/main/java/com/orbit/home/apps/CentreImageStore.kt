package com.orbit.home.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.orbit.home.R
import com.orbit.home.preferences.CentreChoice
import com.orbit.home.preferences.CentreTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * The picture a wheel's centre shows, and how it should sit in the circle.
 *
 * [crop] is the difference between a photograph and the app's own mark. A photograph is cropped to
 * fill, the way a profile picture is; a mark is fitted, because it is drawn to sit inside its
 * square and cropping it to fill would cut the edge that makes it a mark.
 */
data class CentrePicture(val image: ImageBitmap, val crop: Boolean)

/**
 * Where a centre picture the user brought in lives.
 *
 * The picture is copied into the app's own storage rather than remembered as the address it came
 * from. A picked picture is readable only for as long as the permission lasts, and the file behind
 * it can be moved or deleted; a launcher that showed a blank middle a week later because the user
 * had tidied their gallery would be a launcher that lost their setting.
 */
class CentreImageStore(private val context: Context) {

    private fun fileFor(target: CentreTarget, wheelIndex: Int = 0): File =
        File(File(context.filesDir, DIRECTORY), "${target.storageId(wheelIndex)}.png")

    /**
     * Decodes a picked picture, ready for the user to frame.
     *
     * Downscaled on the way in, but not all the way: the picture is about to be cropped, and a
     * picture decoded at the size it will be stored at has nothing left to crop from.
     */
    suspend fun decodeForAdjust(uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching { decode(uri, ADJUST_PX)?.asImageBitmap() }.getOrNull()
    }

    /**
     * Stores an already-framed picture as [target]'s centre, replacing whatever was there.
     *
     * Re-encoded rather than kept as it arrived: it bounds the file, and it means that whatever
     * the source format was, what is stored is one this app can always read back. Returns false
     * when it could not be written, so the caller can say so rather than looking as though it
     * worked.
     */
    suspend fun save(
        target: CentreTarget,
        image: ImageBitmap,
        wheelIndex: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val framed = image.asAndroidBitmap()
            // Bounded again on the way out: the crop can be nearly the whole picture, and a centre
            // is drawn small enough that keeping it whole is memory spent for nothing.
            val widest = maxOf(framed.width, framed.height)
            val stored = if (widest > CENTRE_PX) {
                val ratio = CENTRE_PX.toFloat() / widest
                Bitmap.createScaledBitmap(
                    framed,
                    (framed.width * ratio).toInt().coerceAtLeast(1),
                    (framed.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                framed
            }
            AtomicPngWriter.write(fileFor(target, wheelIndex), stored)
        }.getOrDefault(false)
    }

    /** Where [target]'s video lives, if it has one. */
    private fun videoDirectory(): File = File(context.filesDir, DIRECTORY)

    /**
     * A fresh name for [target]'s video.
     *
     * A new name every time rather than overwriting one file, and it fixes two things at once.
     *
     * The player holds the file open while it plays, so writing over it in place would pull the
     * ground out from under a running decoder — the file it is reading changes underneath it. A new
     * file leaves the old one whole until the player that has it is released.
     *
     * And a name that never changed gave the screen no way to tell that the video was a different
     * one: the player was keyed on the file, the file compared by its path, and the path was always
     * the same — so choosing a second video changed nothing at all.
     */
    private fun freshVideoFile(target: CentreTarget, wheelIndex: Int = 0): File {
        val stem = if (target == CentreTarget.HOME && wheelIndex > 0) {
            "home-w$wheelIndex"
        } else {
            target.id
        }
        return File(videoDirectory(), "$stem-${System.currentTimeMillis()}.mp4")
    }

    /** Every video stored for this exact wheel. Index zero also recognises the legacy filenames. */
    private fun videoFiles(target: CentreTarget, wheelIndex: Int = 0): List<File> =
        videoDirectory().listFiles()?.filter { file ->
            val name = file.name
            if (!name.endsWith(".mp4")) return@filter false
            when {
                target == CentreTarget.HOME && wheelIndex == 0 ->
                    name.matches(Regex("home-[0-9]+\\.mp4")) || name.startsWith("home-w0-")
                target == CentreTarget.HOME -> name.startsWith("home-w$wheelIndex-")
                else -> name.startsWith("${target.id}-")
            }
        } ?: emptyList()

    /**
     * Copies [uri] in as [target]'s video.
     *
     * Refused when it is too large, rather than copied and discovered later: a video is hundreds of
     * times a picture, and a launcher holding a full-length film in its private storage to draw a
     * small circle is not a trade worth making silently. The caller says so when this returns false.
     */
    suspend fun saveVideo(
        target: CentreTarget,
        uri: Uri,
        wheelIndex: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        var created: File? = null
        val ok = runCatching {
            val declared = context.contentResolver
                .openAssetFileDescriptor(uri, "r")
                ?.use { it.length }
            if (declared != null && declared > MAX_VIDEO_BYTES) return@runCatching false


            val destination = freshVideoFile(target, wheelIndex)
            created = destination
            destination.parentFile?.mkdirs()
            // Copied through with a running count as well, because the length is not always
            // declared and a file descriptor that lies would otherwise fill the storage.
            var written = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        written += read
                        if (written > MAX_VIDEO_BYTES) return@runCatching false
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@runCatching false

            // Only once the new one is whole does the old one go. Deleting a file another process
            // has open is safe: it lives on until that process lets go of it.
            videoFiles(target, wheelIndex).filter { it != destination }.forEach { runCatching { it.delete() } }
            true
        }.getOrDefault(false)
        // A refused, oversized or interrupted copy must not leave a truncated file behind: videoFor
        // would otherwise hand it to the player as the centre, and it would never be cleaned up.
        if (!ok) created?.let { runCatching { it.delete() } }
        ok
    }

    /**
     * The first frame of a picked video, for the framing screen to show.
     *
     * A video is framed on a still like a picture is, and the still is its opening frame — which is
     * the only one that can be shown before the framing exists.
     */
    suspend fun firstFrameOf(uri: Uri, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                // The size is worked out here, keeping the video's own shape, rather than handing the
                // retriever a square to fill: a frame stretched to a square would be framed on a
                // picture that is not the one the player draws, and the framing would come out off.
                val shown = displayedVideoSize(retriever)?.let { (w, h) -> fitWithin(w, h, maxPx) }
                val frame = if (shown != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        shown.first,
                        shown.second
                    )
                } else {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { scaledTo(it, maxPx) }
                }
                frame?.asImageBitmap()
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
    }

    /** The video's width and height as it is shown, after any rotation recorded in the file. */
    private fun displayedVideoSize(retriever: MediaMetadataRetriever): Pair<Int, Int>? {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: return null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        if (width <= 0 || height <= 0) return null
        val turned = ((rotation % 180) + 180) % 180 == 90
        return if (turned) height to width else width to height
    }

    /** The stored video for [target], or null if there is none. The newest one is the one in use. */
    fun videoFor(target: CentreTarget, wheelIndex: Int = 0): File? =
        videoFiles(target, wheelIndex)
            .filter { it.isFile && it.length() > 0L }
            .maxByOrNull { it.lastModified() }

    /** Forgets the selected wheel's video. */
    suspend fun removeVideo(target: CentreTarget, wheelIndex: Int = 0) = withContext(Dispatchers.IO) {
        videoFiles(target, wheelIndex).forEach { runCatching { it.delete() } }
        Unit
    }

    /** The stored picture for this wheel, or null if there is none — including if it went missing. */
    suspend fun load(target: CentreTarget, wheelIndex: Int = 0): CentrePicture? = withContext(Dispatchers.IO) {
        runCatching {
            val file = fileFor(target, wheelIndex)
            if (!file.isFile) return@runCatching null
            BitmapFactory.decodeFile(file.absolutePath)
                ?.asImageBitmap()
                ?.let { CentrePicture(it, crop = true) }
        }.getOrNull()
    }

    /**
     * The picture [choice] stands for on [target], or null for the wheel's own middle.
     *
     * The one place a choice becomes something drawable, so the home screen's wheel and the
     * floating one cannot disagree about what a choice means.
     */
    suspend fun pictureFor(
        choice: CentreChoice,
        target: CentreTarget,
        wheelIndex: Int = 0
    ): CentrePicture? = when (choice) {
        CentreChoice.Default -> null
        is CentreChoice.Library -> libraryPicture(choice.id)
        CentreChoice.Imported -> load(target, wheelIndex)
        // Keep the video's opening frame as a local fallback. New imports write it alongside
        // the video. Older app versions removed the picture when a video was selected, so recover
        // and cache one while the legacy video still exists; a later missing-file error can then
        // fall back to the same still rather than losing the centre.
        is CentreChoice.Video -> load(target, wheelIndex)
            ?: recoverVideoFallback(choice, target, wheelIndex)
    }

    private suspend fun recoverVideoFallback(
        choice: CentreChoice.Video,
        target: CentreTarget,
        wheelIndex: Int
    ): CentrePicture? {
        val file = videoFor(target, wheelIndex) ?: return null
        val frame = firstFrameOf(Uri.fromFile(file), ADJUST_PX)?.asAndroidBitmap() ?: return null
        val transform = choice.transform
        val turns = ((transform.quarterTurns % 4) + 4) % 4
        val turnedWidth = if (turns % 2 == 0) frame.width else frame.height
        if (turnedWidth <= 0) return null
        val side = CENTRE_PX
        val framed = renderFramed(
            source = frame,
            scale = side * transform.widthFraction / turnedWidth,
            offsetX = side * transform.offsetXFraction,
            offsetY = side * transform.offsetYFraction,
            quarterTurns = turns,
            sidePx = side
        ) ?: return null
        if (!save(target, framed, wheelIndex)) return null
        return load(target, wheelIndex)
    }

    /** Forgets this wheel's picture. Failing to delete is not worth reporting. */
    suspend fun remove(target: CentreTarget, wheelIndex: Int = 0) = withContext(Dispatchers.IO) {
        runCatching { fileFor(target, wheelIndex).delete() }
        Unit
    }

    /**
     * Removes a deleted home wheel's media and shifts higher-index media down with the wheels.
     * Files are first moved to unique temporary names so no source can be overwritten while the
     * indices are being compacted. Wheel zero uses the legacy `home.*` names and never moves.
     */
    suspend fun removeHomeWheelAndShift(index: Int) = withContext(Dispatchers.IO) {
        if (index <= 0) return@withContext
        val directory = videoDirectory()
        val files = directory.listFiles()?.toList().orEmpty()
        val token = "shift-${System.nanoTime()}"
        val staged = mutableListOf<Pair<File, String>>()

        for (file in files) {
            val name = file.name
            val pictureMatch = Regex("home-([0-9]+)\\.png").matchEntire(name)
            val videoMatch = Regex("home-w([0-9]+)-(.+)\\.mp4").matchEntire(name)
            val mediaIndex = pictureMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: videoMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            if (mediaIndex < index) continue
            if (mediaIndex == index) {
                runCatching { file.delete() }
            } else {
                val suffix = if (pictureMatch != null) "picture-$mediaIndex.png" else name
                val temporary = File(directory, ".$token-$suffix")
                if (runCatching { file.renameTo(temporary) }.getOrDefault(false)) {
                    staged += temporary to name
                }
            }
        }

        for ((temporary, originalName) in staged) {
            val oldIndex = Regex("home-([0-9]+)\\.png").matchEntire(originalName)
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("home-w([0-9]+)-(.+)\\.mp4").matchEntire(originalName)
                    ?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            val destinationName = when {
                originalName.endsWith(".png") -> "home-${oldIndex - 1}.png"
                else -> originalName.replaceFirst("home-w$oldIndex-", "home-w${oldIndex - 1}-")
            }
            val destination = File(directory, destinationName)
            runCatching {
                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
            }
        }
        Unit
    }

    /**
     * A built-in centre, by id, or null if this build does not have one.
     *
     * Drawn once into a bitmap so the wheel has a single thing to draw, whether the middle is one
     * of ours or one the user brought in.
     */
    fun libraryPicture(id: String): CentrePicture? {
        val res = when (id) {
            CentreChoice.LIBRARY_ORBIT -> R.drawable.ic_orbit_logo
            else -> return null
        }
        return runCatching {
            val drawable = ContextCompat.getDrawable(context, res) ?: return null
            val bitmap = Bitmap.createBitmap(LIBRARY_PX, LIBRARY_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, LIBRARY_PX, LIBRARY_PX)
            drawable.draw(canvas)
            CentrePicture(bitmap.asImageBitmap(), crop = false)
        }.getOrNull()
    }

    /**
     * A picture from a picked address, downscaled on the way in.
     *
     * The bounds are read first so a full-size photograph is never decoded whole: a modern camera's
     * picture is many times what a centre can show, and decoding it whole is how a launcher runs out
     * of memory.
     */
    private fun decode(uri: Uri, maxPx: Int): Bitmap? =
        decodeUpright(context, uri) { w, h -> maxOf(w, h) >= maxPx }

    private companion object {
        const val DIRECTORY = "centres"

        /** Comfortably more than a centre is ever drawn at, and small enough to keep. */
        const val CENTRE_PX = 512

        /**
         * What a picture is decoded at for the user to frame.
         *
         * Larger than [CENTRE_PX] so there is something to crop from: a picture decoded at the
         * stored size would already have thrown away the detail the crop is choosing between.
         */
        const val ADJUST_PX = 1440

        /**
         * The largest video that may be brought in.
         *
         * Enough for a short looping clip, which is what a centre can show, and small enough that
         * the launcher's private storage is not quietly turned into a media library.
         */
        const val MAX_VIDEO_BYTES = 25L * 1024 * 1024

        /** The built-in marks are drawn at this size once, and scaled from there. */
        const val LIBRARY_PX = 256
    }
}
