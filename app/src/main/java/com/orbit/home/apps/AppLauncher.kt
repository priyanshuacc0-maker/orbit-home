package com.orbit.home.apps

import android.content.ComponentName
import android.content.Context
import com.orbit.home.diagnostics.Diagnostics
import android.content.Intent
import android.os.Bundle

/**
 * Launches a discovered application using its exact launcher component. No app
 * names are hardcoded and no package names are guessed from labels.
 */
object AppLauncher {

    /**
     * Launches directly from a stable id (`pkg/activity`) with no repository
     * scan. Used where a tap must be instant.
     */
    fun launchById(context: Context, id: String): Result<Unit> {
        val parts = id.split("/")
        if (parts.size != 2) return Result.failure(IllegalArgumentException("bad app id: $id"))
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(parts[0], parts[1])
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (t: Throwable) {
            // Cancellation must propagate (remediation 5.21).
            Diagnostics.rethrowIfCancellation(t)
            Result.failure(t)
        }
    }

    /**
     * Attempts to launch [app]. Returns a [Result] so the caller can surface a
     * concise message and refresh the inventory instead of crashing.
     */
    fun launch(context: Context, app: InstalledApp, options: Bundle? = null): Result<Unit> = try {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (!app.activityName.isNullOrBlank()) {
                component = ComponentName(app.packageName, app.activityName)
            } else {
                setPackage(app.packageName)
            }
        }
        // The options give the launch a scale-up from the tapped icon instead of
        // the app appearing out of nowhere (P1-39).
        if (options != null) context.startActivity(intent, options) else context.startActivity(intent)
        Result.success(Unit)
    } catch (t: Throwable) {
            // Cancellation must propagate (remediation 5.21).
            Diagnostics.rethrowIfCancellation(t)
        // ActivityNotFoundException, SecurityException and similar are all
        // recoverable here: refresh + tell the user, never crash.
        Result.failure(t)
    }
}
