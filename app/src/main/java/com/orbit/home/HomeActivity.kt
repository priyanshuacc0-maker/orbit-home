package com.orbit.home

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import com.orbit.home.diagnostics.Diagnostics
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.orbit.home.ui.HomeScreen
import com.orbit.home.ui.theme.OrbitTheme
import com.orbit.home.viewmodel.HomeViewModel
import java.util.Locale

/**
 * The real home screen. Android launches this when the user presses Home once
 * Orbit Home is the chosen home app.
 */
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    /** The locale the app labels were last read in (P1-37). */
    private var lastLocale: Locale? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Even if an update only changes an icon, the id/label/activity inventory may be
            // identical. Pass the package through so its cached icon is invalidated independently.
            viewModel.refreshIfPackagesChanged(intent?.data?.schemeSpecificPart)
        }
    }

    /**
     * This activity handles config changes itself, so nothing re-read app labels
     * after a language change — they stayed in the old language until the process
     * was restarted (P1-37).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val locale = newConfig.locales[0] ?: Locale.getDefault()
        if (locale != lastLocale) {
            lastLocale = locale
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastLocale = resources.configuration.locales[0] ?: Locale.getDefault()
        // Transparent system bars with NO scrim. The default enableEdgeToEdge()
        // navigation-bar style paints a translucent bar behind the navigation
        // bar, which shows up as a shadow band with 3-button navigation.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            OrbitTheme {
                HomeScreen(viewModel)
            }
        }
    }

    /**
     * Android re-sends HOME whenever the user returns to the home screen, including
     * a press of the Home button while an overlay is open. Treating every one of
     * those as "go home" means the button is a way out of any screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.returnHome()
    }

    override fun onStart() {
        super.onStart()
        // Re-read reduced-motion, in case it changed while we were away.
        viewModel.refreshMotionPreference()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // System broadcasts are still delivered with RECEIVER_NOT_EXPORTED.
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        // Another app, the recents screen or a locked phone: the floating wheel's panel
        // is no longer on screen to be used, so it must not leave the wheel pinned out.
        viewModel.setEditingShortcutWheel(false)
        try {
            unregisterReceiver(packageReceiver)
        } catch (t: Throwable) {
            // Cancellation must propagate (remediation 5.21).
            Diagnostics.rethrowIfCancellation(t)
            // Already unregistered: ignore.
        }
    }

    /**
     * Centre the phone's wallpaper on the screen.
     *
     * The system draws that wallpaper at whatever offset was last set for it, and that is on purpose:
     * it is how a launcher lets the wallpaper slide as you swipe between pages. This app never sets
     * that offset, so it keeps whatever the launcher before it left behind — and a wallpaper sitting
     * at an offset it was never given is drawn shifted, with a bare strip of screen down one side.
     *
     * The step is zero, so the wallpaper does not scroll, and the offset is 0.5, the middle. An offset
     * of 0 is the wallpaper's LEFT edge, not its middle: any wallpaper wider than the screen (which
     * is what a parallax-sized one is) would then show only its left part, with the picture's centre
     * pushed off to the right. At 0.5 whatever is wider than the screen is trimmed equally from both
     * sides, and a wallpaper that is exactly the screen's size is not moved at all.
     *
     * Only the phone's wallpaper. The hidden one is drawn by this app and asks the system nothing,
     * which is why it was never affected.
     *
     * Here rather than in onCreate, because setting a wallpaper happens in another app and the shift
     * shows the moment this one comes back to the front.
     */
    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        // Cheap on a plain return: only rescans if packages actually changed.
        viewModel.refreshIfPackagesChanged()
        // Brings the floating wheel back in line with its switch (reboot, update, restore,
        // killed service, revoked permission).
        viewModel.refreshShortcutWheel()
        runCatching {
            val manager = WallpaperManager.getInstance(this)
            manager.setWallpaperOffsetSteps(0f, 0f)
            manager.setWallpaperOffsets(window.decorView.windowToken, 0.5f, 0.5f)
        }.onFailure { Diagnostics.warn("wallpaper offset", it) }
    }
}
