package com.orbit.home.apps

import android.graphics.Bitmap
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Writes a replacement PNG without truncating the previous known-good file first. */
internal object AtomicPngWriter {
    fun write(destination: File, bitmap: Bitmap): Boolean {
        val parent = destination.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) return false

        val atomic = AtomicFile(destination)
        var output: FileOutputStream? = null
        return try {
            output = atomic.startWrite()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Bitmap compression failed")
            }
            output.fd.sync()
            atomic.finishWrite(output)
            output = null
            true
        } catch (_: Exception) {
            output?.let { stream -> runCatching { atomic.failWrite(stream) } }
            false
        }
    }
}
