package com.orbit.home.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process

/**
 * What attempting to start a shortcut produced.
 *
 * Four outcomes, because they call for four different things: a launch that worked, a shortcut
 * that is no longer published, an access problem, and a failure worth showing.
 */
sealed interface ShortcutLaunchResult {
    data object Started : ShortcutLaunchResult

    /** The app has withdrawn the shortcut, or been uninstalled since it was saved. */
    data object Gone : ShortcutLaunchResult

    /** Orbit Home is not the launcher, or has lost shortcut access. */
    data object NoAccess : ShortcutLaunchResult

    data class Failed(val detail: String) : ShortcutLaunchResult
}

/**
 * Starting an app shortcut.
 *
 * The native action the app published, through `LauncherApps.startShortcut` — never a guessed
 * activity name and never a deep link standing in for a shortcut that does not exist. If an app
 * publishes no "New chat", there is no "New chat" to start, and this says so rather than opening
 * the app's main screen and pretending.
 *
 * Nothing here can bring down the service: every failure is a value, not a throw.
 */
object ShortcutLauncher {

    /**
     * Starts the shortcut a stored key refers to.
     *
     * The key is validated before anything is attempted, so a malformed one cannot be handed to
     * the system as though it were real.
     */
    fun start(context: Context, key: String): ShortcutLaunchResult {
        val (packageName, shortcutId) = ShortcutItem.split(key)
            ?: return ShortcutLaunchResult.Failed("that is not a shortcut key")

        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return ShortcutLaunchResult.NoAccess
        if (!ShortcutCatalog.hasAccess(context)) return ShortcutLaunchResult.NoAccess

        return try {
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            ShortcutLaunchResult.Started
        } catch (t: IllegalStateException) {
            // The user is locked, or the profile is not available.
            ShortcutLaunchResult.Failed(t.message ?: "shortcuts are not available right now")
        } catch (t: SecurityException) {
            ShortcutLaunchResult.NoAccess
        } catch (t: Throwable) {
            // Android throws ActivityNotFoundException for a shortcut that has been withdrawn
            // since it was saved. It is not a crash and not a silent success: the entry is gone.
            // Both of these are matched by name because neither can be caught directly:
            // ShortcutLaunchException is a hidden system class, and ActivityNotFoundException is
            // what Android throws for a shortcut the app has withdrawn since it was saved.
            val name = t.javaClass.name
            when {
                name.contains("ActivityNotFound") -> ShortcutLaunchResult.Gone
                name.contains("ShortcutLaunchException") ->
                    ShortcutLaunchResult.Failed(t.message ?: "the app would not open that shortcut")
                else -> ShortcutLaunchResult.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }
}
