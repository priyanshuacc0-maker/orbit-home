package com.orbit.home.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process

/**
 * What a shortcut query produced.
 *
 * Three outcomes, not two, and that matters: "this device will not let Orbit Home read
 * shortcuts" and "these apps publish none" look identical if both come back as an empty
 * list, and the user is left thinking the feature is broken when it is only blocked.
 */
sealed interface ShortcutCatalogResult {
    /** The query ran. [items] may legitimately be empty — that is a real answer. */
    data class Available(val items: List<ResolvedShortcut>) : ShortcutCatalogResult

    /** Orbit Home is not the launcher, or does not hold shortcut access. */
    data object NoLauncherAccess : ShortcutCatalogResult

    /** The query ran and failed, for a reason worth showing. */
    data class Failed(val detail: String) : ShortcutCatalogResult
}

/**
 * The shortcuts installed apps actually publish.
 *
 * The same list a long-press in the app drawer shows, read through `LauncherApps` — the API
 * a launcher already uses, so no new broad permission is involved and `QUERY_ALL_PACKAGES`
 * is not needed.
 *
 * An app offers only what it declares. Nothing is invented for an app that publishes
 * nothing, and no activity names or deep links are guessed at: if an app has no "New chat"
 * shortcut, there is no "New chat" entry here.
 */
object ShortcutCatalog {

    /**
     * Every shortcut currently published.
     *
     * [hasAccess] is checked first. Reading shortcuts without it throws SecurityException on
     * some devices and returns an empty list on others — so without the check, the same
     * block would look like two different problems.
     */
    fun query(context: Context): ShortcutCatalogResult {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return ShortcutCatalogResult.NoLauncherAccess

        if (!hasAccess(context)) return ShortcutCatalogResult.NoLauncherAccess

        return try {
            val query = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            val infos = launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
            ShortcutCatalogResult.Available(
                infos.mapNotNull { resolve(context, launcherApps, it) }
                    // Same app, same label twice is noise on a ring of at most twelve.
                    .distinctBy { it.key }
                    .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.item.label.lowercase() }))
            )
        } catch (t: SecurityException) {
            // Access was withdrawn between the check and the query.
            ShortcutCatalogResult.NoLauncherAccess
        } catch (t: Throwable) {
            // A manufacturer's LauncherApps that refuses the call, a locked user, a package
            // that vanished mid-query. Reported rather than swallowed.
            ShortcutCatalogResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Only the shortcuts a saved list actually refers to.
     *
     * The ring shows at most about a dozen shortcuts, and [query] reads every shortcut of every
     * installed app to build a list it then throws away. This asks each package for just the ids
     * that were saved, which is a handful of binder calls instead of a few hundred — the
     * difference between the ring appearing at once and appearing a beat late.
     *
     * [query] is left exactly as it is: the picker needs the whole catalogue, because its job is
     * to show what is available.
     *
     * One package failing — uninstalled, mid-update — must not lose the rest, so failures are
     * counted and only a total failure is reported as one.
     */
    fun queryKeys(context: Context, keys: List<String>): ShortcutCatalogResult {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return ShortcutCatalogResult.NoLauncherAccess
        if (!hasAccess(context)) return ShortcutCatalogResult.NoLauncherAccess

        val wanted = ShortcutSelection.idsByPackage(keys)
        if (wanted.isEmpty()) return ShortcutCatalogResult.Available(emptyList())

        val found = ArrayList<ResolvedShortcut>()
        var failures = 0
        var firstFailure: String? = null
        for ((pkg, ids) in wanted) {
            try {
                val q = LauncherApps.ShortcutQuery()
                    .setPackage(pkg)
                    .setShortcutIds(ids)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
                launcherApps.getShortcuts(q, Process.myUserHandle()).orEmpty()
                    .mapNotNullTo(found) { resolve(context, launcherApps, it) }
            } catch (t: SecurityException) {
                return ShortcutCatalogResult.NoLauncherAccess
            } catch (t: Throwable) {
                failures++
                if (firstFailure == null) firstFailure = t.message ?: t.javaClass.simpleName
            }
        }
        if (failures == wanted.size) {
            return ShortcutCatalogResult.Failed(firstFailure ?: "shortcut query failed")
        }
        return ShortcutCatalogResult.Available(found.distinctBy { it.key })
    }

    /**
     * Whether Orbit Home may read and start shortcuts.
     *
     * True when it is the launcher, which is the normal case for this app. A non-launcher
     * install — the setup screen before the role is taken — is told plainly rather than
     * being shown an empty list.
     */
    fun hasAccess(context: Context): Boolean = runCatching {
        context.getSystemService(LauncherApps::class.java)?.hasShortcutHostPermission() == true
    }.getOrDefault(false)

    /** One ShortcutInfo into the live shape, or null when it has nothing usable to show. */
    private fun resolve(
        context: Context,
        launcherApps: LauncherApps,
        info: ShortcutInfo
    ): ResolvedShortcut? {
        val label = info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: info.longLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        val packageName = info.`package` ?: return null
        val shortcutId = info.id ?: return null

        val appIcon = runCatching {
            launcherApps.getApplicationInfo(packageName, 0, Process.myUserHandle())
                ?.loadIcon(context.packageManager)
        }.getOrNull()
        val appLabel = runCatching {
            launcherApps.getApplicationInfo(packageName, 0, Process.myUserHandle())
                ?.loadLabel(context.packageManager)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName

        return ResolvedShortcut(
            item = ShortcutItem(packageName, shortcutId, label),
            appLabel = appLabel,
            appIcon = appIcon,
            shortcutIcon = runCatching { launcherApps.getShortcutIconDrawable(info, 0) }.getOrNull()
        )
    }
}
