package com.orbit.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.AppRepository
import com.orbit.home.apps.InstalledApp
import com.orbit.home.preferences.AppPreferences
import com.orbit.home.ui.EditAppsScreen
import android.widget.Toast
import com.orbit.home.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The floating wheel's app picker.
 *
 * A real activity for the same reason the shortcut picker is one: the wheel's windows are
 * `FLAG_NOT_FOCUSABLE`, so nothing drawn there can take the keyboard, and a search field with no
 * keyboard is worse than no search field.
 *
 * It edits the same list the floating wheel shows in app mode — `shortcutApps` — so a single tap
 * on the wheel's centre in app mode lands here, exactly as it lands on the shortcut picker in
 * shortcut mode.
 *
 * The saved list is read before anything can fail, so a failed load can never be mistaken for an
 * empty selection and erase what was there.
 */
class FloatingAppsPickerActivity : ComponentActivity() {

    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(applicationContext)

        setContent {
            MaterialTheme {
                val context = applicationContext
                val iconLoader = remember { AppIconLoader(context) }
                val repository = remember { AppRepository(context) }

                var saved by remember { mutableStateOf<List<String>>(emptyList()) }
                var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }

                LaunchedEffect(Unit) {
                    val all = withContext(Dispatchers.IO) {
                        repository.loadLaunchableApps(context.packageName)
                    }
                    val settings = withContext(Dispatchers.IO) { preferences.current() }
                    // An untouched wheel shows every app, so that is what is selected; an empty
                    // list here would read as "nothing" and saving would empty the wheel.
                    saved = if (settings.shortcutInitialized) settings.shortcutApps
                    else all.map { it.id }
                    apps = all
                }

                val loaded = apps
                if (loaded == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else {
                    EditAppsScreen(
                        allApps = loaded,
                        initialSelection = saved,
                        iconLoader = iconLoader,
                        title = stringResource(R.string.settings_floating),
                        onSave = { ids ->
                            lifecycleScope.launch {
                                // NonCancellable: see ShortcutPickerActivity.
                                val ok = try {
                                    withContext(NonCancellable + Dispatchers.IO) {
                                        preferences.setShortcutApps(ids)
                                    }
                                    true
                                } catch (t: Throwable) {
                                    Diagnostics.rethrowIfCancellation(t)
                                    Diagnostics.warn("floating apps not saved", t)
                                    false
                                }
                                if (ok) {
                                    finish()
                                } else {
                                    Toast.makeText(
                                        applicationContext,
                                        R.string.settings_save_failed,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    /**
     * A picker is a short detour, not a destination. If the user leaves it by any route other than
     * Save or Cancel (Home, Recents, another app) it is removed instead of lingering as a task that
     * Recents cannot show, which is what made the overview skip or reorder cards.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) finishAndRemoveTask()
    }
}
