package com.orbit.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.AppLabels
import com.orbit.home.apps.AppPickerOrder
import com.orbit.home.apps.AppSearch
import com.orbit.home.apps.SelectionRules
import com.orbit.home.apps.InstalledApp
import com.orbit.home.ui.theme.OrbitColors

/**
 * Picks which apps are on the wheel.
 *
 * The list is alphabetical and searchable; tapping a row ticks it. The order apps
 * are ticked in is the order they appear on the ring, which is why the selection is
 * kept as a list rather than a set.
 */
@Composable
fun EditAppsScreen(
    allApps: List<InstalledApp>,
    initialSelection: List<String>,
    iconLoader: AppIconLoader,
    title: String,
    onSave: (List<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, no more than this many apps may be chosen — the dock's five. */
    maxSelection: Int? = null
) {
    // Pick order is preserved: adding appends, removing drops.
    var selected by remember { mutableStateOf(initialSelection) }
    var query by remember { mutableStateOf("") }

    // The split is fixed when this screen opens: the apps already on the wheel
    // first, in ring order so their numbers read 1, 2, 3, then everything else
    // alphabetically. Ticking a row does **not** move it — the list would otherwise
    // reorder under the finger mid-tap — so it re-sorts next time this opens.
    val displayOrder = remember(allApps, initialSelection) {
        AppPickerOrder.ordered(allApps, initialSelection)
    }
    val filtered = remember(displayOrder, query) { AppSearch.filter(displayOrder, query) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
            .blockTouchesBehind()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = OrbitColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.selected_count, selected.size, selected.size
                    ),
                    color = OrbitColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Clear empties the working selection only. Nothing is written until
            // Save, so tapping it and then Cancel leaves the wheel untouched —
            // which is why it needs no confirmation of its own.
            TextButton(onClick = { selected = emptyList() }) {
                Text(stringResource(R.string.clear), color = OrbitColors.Accent)
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = OrbitColors.Accent)
            }
            TextButton(onClick = {
                // An app uninstalled while the picker was open must not be written back.
                val installed = allApps.mapTo(HashSet()) { it.id }
                onSave(selected.filter { it in installed })
            }) {
                Text(stringResource(R.string.save), color = OrbitColors.Accent)
            }
        }

        OrbitSearchBar(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (filtered.isEmpty()) {
            AppEmptyState(
                icon = Icons.Filled.Check,
                title = stringResource(R.string.drawer_empty_title),
                body = stringResource(R.string.drawer_empty_body),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(items = filtered, key = { it.id }) { app ->
                    val index = selected.indexOf(app.id)
                    PickRow(
                        app = app,
                        iconLoader = iconLoader,
                        // The number is the position in the ring or dock, so the
                        // order the taps happened in is visible rather than implied.
                        position = if (index >= 0) index + 1 else null,
                        // Tapping a chosen app removes it; tapping an unchosen one
                        // adds it, unless the cap is already reached. Only the last
                        // case is disabled, and it is shown as such rather than
                        // silently swallowing the tap.
                        enabled = SelectionRules.canToggle(selected, app.id, maxSelection),
                        onClick = {
                            selected = SelectionRules.toggle(selected, app.id, maxSelection)
                        }
                    )
                }
            }
        }
    }
}

/** One pickable row: icon, label, and either its ring position or an empty ring. */
@Composable
private fun PickRow(
    app: InstalledApp,
    iconLoader: AppIconLoader,
    position: Int?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val px = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon = rememberAppIcon(app.id, px, iconLoader)
    val ticked = position != null

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = app.label,
                color = if (enabled) OrbitColors.TextPrimary else OrbitColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (ticked) OrbitColors.Accent else OrbitColors.Guide.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (position != null) {
                    Text(
                        text = position.toString(),
                        color = Color.Black,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        HorizontalDivider(color = OrbitColors.Guide.copy(alpha = 0.4f))
    }
}
