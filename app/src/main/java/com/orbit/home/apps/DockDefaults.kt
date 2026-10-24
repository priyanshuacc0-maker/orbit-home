package com.orbit.home.apps

import android.content.Context
import com.orbit.home.diagnostics.Diagnostics
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore

/**
 * Resolves the default dock: the apps most people want first, then the system's own picks.
 *
 * The named packages are tried first, and only if they are actually installed - so a phone without
 * Instagram gets four apps, not an empty slot. This is a starting arrangement the user is expected to
 * change, which is why it is a default and not a rule; the intent lookups below are the fallback for
 * everything it does not name.
 *
 * Anything that does not resolve is skipped. Results are component ids (`pkg/activity`).
 */
object DockDefaults {

    fun resolve(context: Context): List<String> {
        val pm = context.packageManager
        val out = ArrayList<String>(5)

        /** The app's own launcher entry, if that package is installed. */
        fun addPackage(pm: PackageManager, pkg: String) {
            val launch = try {
                pm.getLaunchIntentForPackage(pkg)
            } catch (t: Throwable) {
                Diagnostics.rethrowIfCancellation(t)
                null
            } ?: return
            val info = launch.resolveActivityInfo(pm, 0) ?: return
            val name = info.name ?: return
            out.add("$pkg/$name")
        }

        fun add(intent: Intent?) {
            if (intent == null) return
            val resolved = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }
            } catch (t: Throwable) {
            // Cancellation must propagate (remediation 5.21).
            Diagnostics.rethrowIfCancellation(t)
                null
            }
            val info = resolved?.activityInfo ?: return
            val pkg = info.packageName ?: return
            val name = info.name ?: return
            out.add("$pkg/$name")
        }

        // The four a new phone almost always has. Named on purpose: this is a default the user will
        // change, and an empty dock is worse than one with an app they did not pick.
        addPackage(pm, "com.whatsapp")
        addPackage(pm, "com.facebook.katana")
        addPackage(pm, "com.instagram.android")
        add(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

        add(Intent(Intent.ACTION_DIAL))
        add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))
        add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
        add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
        add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS))

        return out.distinct().take(MAX_DOCK)
    }

    const val MAX_DOCK = 5

    /** How many apps a brand-new dock starts with. */
    const val SEED_COUNT = 4

    /** [chosen] first, then [candidates] to make up the numbers, [count] apps at most and no repeats. */
    fun fillTo(chosen: List<String>, candidates: List<String>, count: Int = SEED_COUNT): List<String> =
        (chosen + candidates).distinct().take(count)
}
