package com.orbit.home.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers launchable applications via [PackageManager].
 *
 * This deliberately loads **labels only** and never touches icons: a launcher's
 * first frame must appear fast, so icons are fetched later, on demand and at the
 * size actually needed, by [AppIconLoader].
 */
class AppRepository(private val context: Context) {

    /**
     * Returns the sorted list of user-facing, launchable apps. The app's own
     * package is excluded so it never appears as a redundant self-launch entry.
     */
    suspend fun loadLaunchableApps(selfPackage: String): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

            val seen = HashSet<String>()
            val result = ArrayList<InstalledApp>(resolveInfos.size)

            for (info in resolveInfos) {
                val activity = info.activityInfo ?: continue
                val pkg = activity.packageName ?: continue
                if (pkg == selfPackage) continue

                val activityName = activity.name
                val id = "$pkg/$activityName"
                // Guard against repeated query results for the same component.
                if (!seen.add(id)) continue

                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg

                result.add(
                    InstalledApp(
                        id = id,
                        packageName = pkg,
                        activityName = activityName,
                        label = label,
                        icon = null
                    )
                )
            }

            // Locale-aware, so accents, case and non-Latin scripts order the way a
            // reader expects instead of by UTF-16 code unit (P0-12).
            result.sortWith(compareBy(AppLabels.comparator()) { it.label })
            result
        }
}
