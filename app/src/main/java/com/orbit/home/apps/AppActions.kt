package com.orbit.home.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.orbit.home.R
import com.orbit.home.diagnostics.Diagnostics

/**
 * The two things a phone lets you do to an app without opening it.
 *
 * Both hand over to the system rather than doing anything here. That is deliberate: the app's own
 * settings screen is the system's, and uninstalling is confirmed by Android before it happens. A
 * launcher that removed an app on a tap of its own would be a launcher that can delete your things
 * without asking.
 */
object AppActions {

    /** The system's screen for this app: its permissions, storage, notifications, and a way out. */
    fun openAppInfo(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Diagnostics.warn("app info", it)
            Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The system's uninstall, which asks before it does anything.
     *
     * [Intent.ACTION_DELETE] rather than the older uninstall-package action: this is the one that goes
     * through the same confirmation the settings screen uses, so nothing is removed without the user
     * agreeing to it on a screen that is not ours.
     */
    fun uninstall(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_DELETE,
                    Uri.fromParts("package", packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Diagnostics.warn("uninstall", it)
            Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
