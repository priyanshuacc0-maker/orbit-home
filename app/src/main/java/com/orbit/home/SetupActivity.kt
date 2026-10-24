package com.orbit.home

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.home.diagnostics.Diagnostics
import com.orbit.home.ui.SetupScreen
import com.orbit.home.ui.theme.OrbitTheme
import com.orbit.home.viewmodel.HomeViewModel

/**
 * The normal app-drawer entry: setup and settings. Explains what Orbit Home does
 * and lets the user become the home app (with confirmation) — or switch back.
 */
class SetupActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private var isHome by mutableStateOf(false)
    private lateinit var roleLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No navigation-bar scrim (see HomeActivity).
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
        roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isHome = isCurrentHome()
        }
        setContent {
            OrbitTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SetupScreen(
                    state = state,
                    isHome = isHome,
                    onSetHome = ::requestHome,
                    onSwitchBack = ::openHomeSettings,
                    onDone = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isHome = isCurrentHome()
        // Cheap on a plain return: only rescans if packages actually changed.
        viewModel.refreshIfPackagesChanged()
        viewModel.refreshShortcutWheel()
    }

    /** True when the system resolves Home to this app. */
    private fun isCurrentHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        } catch (t: Throwable) {
            // Cancellation must propagate (remediation 5.21).
            Diagnostics.rethrowIfCancellation(t)
            null
        }
        return resolved?.activityInfo?.packageName == packageName
    }

    /** Ask to become the home app. Never changes the default silently. */
    private fun requestHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        openHomeSettings()
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (t: Throwable) {
            // Cancellation must propagate (remediation 5.21).
            Diagnostics.rethrowIfCancellation(t)
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
