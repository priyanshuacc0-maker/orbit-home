package com.orbit.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.orbit.home.apps.CircleIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.orbit.home.preferences.AppPreferences
import com.orbit.home.shortcuts.ResolvedShortcut
import com.orbit.home.shortcuts.ShortcutCatalog
import com.orbit.home.shortcuts.ShortcutCatalogResult
import com.orbit.home.shortcuts.ShortcutPickerModel
import com.orbit.home.shortcuts.ShortcutSelection
import com.orbit.home.ui.OrbitSearchBar
import com.orbit.home.ui.theme.OrbitColors
import android.widget.Toast
import com.orbit.home.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The shortcut picker.
 *
 * A real activity rather than a panel over the wheel, and that is forced rather than chosen: the
 * wheel's windows are `FLAG_NOT_FOCUSABLE`, so nothing drawn there can take the keyboard, and a
 * search field with no keyboard is worse than no search field.
 *
 * It talks to the service through DataStore alone — no binder, no broadcast — so the wheel's
 * window code stays untouched and the two halves stay unaware of each other.
 */
class ShortcutPickerActivity : ComponentActivity() {

    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(applicationContext)

        setContent {
            MaterialTheme {
                PickerScreen(
                    load = { loadState() },
                    save = { keys ->
                        lifecycleScope.launch {
                            // NonCancellable: the write must finish even if the activity is torn
                            // down, otherwise the selection is silently lost.
                            val saved = try {
                                withContext(NonCancellable + Dispatchers.IO) {
                                    preferences.setFloatingShortcuts(keys)
                                }
                                true
                            } catch (t: Throwable) {
                                Diagnostics.rethrowIfCancellation(t)
                                Diagnostics.warn("shortcut selection not saved", t)
                                false
                            }
                            if (saved) {
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
                    onClose = { finish() }
                )
            }
        }
    }

    /**
     * Reads the saved selection first, then the catalog.
     *
     * The order matters: the selection must be in hand before anything can fail, so a failed query
     * can never be mistaken for an empty selection and erase it.
     */
    private suspend fun loadState(): PickerState {
        val saved = try {
            withContext(Dispatchers.IO) { preferences.currentStrict().floatingShortcuts }
        } catch (t: Throwable) {
            Diagnostics.rethrowIfCancellation(t)
            Diagnostics.warn("picker could not read saved shortcuts", t)
            return PickerState.Failed(emptyList(), t.message ?: "settings unreadable")
        }
        // this@ShortcutPickerActivity, not this: inside withContext, `this` is the coroutine
        // scope, and ShortcutCatalog wants a Context.
        return when (val r = withContext(Dispatchers.IO) {
            ShortcutCatalog.query(this@ShortcutPickerActivity)
        }) {
            is ShortcutCatalogResult.Available -> PickerState.Ready(saved, r.items)
            ShortcutCatalogResult.NoLauncherAccess -> PickerState.NoAccess(saved)
            is ShortcutCatalogResult.Failed -> PickerState.Failed(saved, r.detail)
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

/** What the picker is showing. [saved] is always the user's own list, whatever happened. */
private sealed interface PickerState {
    val saved: List<String>

    data object Loading : PickerState {
        override val saved: List<String> get() = emptyList()
    }

    /** Nothing can be chosen right now, but the saved selection is still theirs. */
    data class NoAccess(override val saved: List<String>) : PickerState

    data class Failed(override val saved: List<String>, val detail: String) : PickerState

    /** [items] may legitimately be empty — that is a real answer, not a failure. */
    data class Ready(override val saved: List<String>, val items: List<ResolvedShortcut>) : PickerState
}

@Composable
private fun PickerScreen(
    load: suspend () -> PickerState,
    save: (List<String>) -> Unit,
    onClose: () -> Unit
) {
    var reload by remember { mutableStateOf(0) }
    val state by produceState<PickerState>(PickerState.Loading, reload) {
        value = PickerState.Loading
        value = load()
    }
    var selected by remember(state) { mutableStateOf(state.saved) }
    var query by remember { mutableStateOf("") }

    // Save is offered only when the catalog actually loaded, so Loading, NoAccess and Failed can
    // never write — a failed query must not be able to erase a selection.
    val canSave = state is PickerState.Ready

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.cancel), color = OrbitColors.TextSecondary)
            }
            Text(
                text = stringResource(R.string.shortcut_picker_title),
                color = OrbitColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { save(selected.distinct()) }, enabled = canSave) {
                Text(
                    text = stringResource(R.string.save),
                    color = if (canSave) OrbitColors.Accent else OrbitColors.TextSecondary
                )
            }
        }

        when (val s = state) {
            PickerState.Loading -> Centre { CircularProgressIndicator(color = OrbitColors.Accent) }

            is PickerState.NoAccess -> Centre {
                Message(
                    title = stringResource(R.string.shortcut_picker_no_access_title),
                    body = stringResource(R.string.shortcut_picker_no_access_body)
                )
            }

            is PickerState.Failed -> Centre {
                Message(
                    title = stringResource(R.string.shortcut_picker_failed_title),
                    body = s.detail,
                    action = stringResource(R.string.retry),
                    onAction = { reload += 1 }
                )
            }

            is PickerState.Ready -> {
                OrbitSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" }
                )
                val matches = ShortcutPickerModel.filter(s.items, query)
                if (matches.isEmpty()) {
                    Centre {
                        Message(
                            title = stringResource(R.string.shortcut_picker_empty_title),
                            body = stringResource(R.string.shortcut_picker_empty_body)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                        ShortcutPickerModel.group(matches).forEach { group ->
                            // A plain item, not a sticky header: that API is experimental.
                            item(key = "h:" + group.packageName) {
                                Text(
                                    text = group.appLabel,
                                    color = OrbitColors.TextSecondary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                                )
                            }
                            items(group.items, key = { it.key }) { entry ->
                                PickRow(
                                    entry = entry,
                                    number = selected.indexOf(entry.key).takeIf { it >= 0 }?.plus(1),
                                    onToggle = {
                                        selected = ShortcutSelection.toggle(selected, entry.key)
                                    }
                                )
                            }
                        }

                        // Saved shortcuts that are not available right now. Shown, not silently
                        // dropped: they stay saved and return if the app republishes them.
                        val missing = ShortcutSelection.missing(selected, s.items)
                        if (missing.isNotEmpty()) {
                            item(key = "missing") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.shortcut_missing_note, missing.size
                                        ),
                                        color = OrbitColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        selected = selected.filterNot { it in missing }
                                    }) {
                                        Text(stringResource(R.string.remove), color = OrbitColors.Accent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One shortcut: its icon, its label, and a numbered circle when chosen. */
@Composable
private fun PickRow(entry: ResolvedShortcut, number: Int?, onToggle: () -> Unit) {
    val icon by produceState<ImageBitmap?>(null, entry.key) {
        // Decoded off the main thread, and only for rows the list actually composes.
        value = withContext(Dispatchers.Default) {
            runCatching {
                CircleIcon.render(entry.icon, ICON_PX)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            val bitmap = icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = entry.item.label.take(1),
                    color = OrbitColors.TextSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Text(
            text = entry.item.label,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (number != null) OrbitColors.Accent else OrbitColors.Guide),
            contentAlignment = Alignment.Center
        ) {
            // The number rather than a tick: it shows the order they were chosen in, and makes
            // chosen and not-chosen unmistakable at a glance.
            if (number != null) {
                Text(
                    text = number.toString(),
                    color = OrbitColors.Background,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun Centre(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Message(
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title, color = OrbitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Text(text = body, color = OrbitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = OrbitColors.Accent) }
        }
    }
}

private const val ICON_PX = 96
