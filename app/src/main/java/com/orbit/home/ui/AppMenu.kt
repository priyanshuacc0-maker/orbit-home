package com.orbit.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.apps.InstalledApp
import com.orbit.home.ui.theme.OrbitColors

/**
 * What you can do to an app without opening it: see its settings, or take it off the phone.
 *
 * Two rows, each an icon and its name, on one small surface. The app's own name is not repeated here
 * because the icon it belongs to is right underneath - and a menu that says the name of the thing you
 * are already holding is a menu that has not understood why it is there.
 */
@Composable
internal fun AppMenu(
    app: InstalledApp,
    onAppInfo: () -> Unit,
    onRemove: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(OrbitColors.SurfaceHigh)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MenuRow(
            icon = Icons.Filled.Info,
            label = stringResource(R.string.app_info),
            onClick = onAppInfo
        )
        MenuRow(
            icon = Icons.Filled.Close,
            label = stringResource(R.string.app_remove),
            onClick = onRemove
        )
        MenuRow(
            icon = Icons.Filled.Warning,
            label = stringResource(R.string.app_uninstall),
            onClick = onUninstall
        )
    }
}

/** One line of the menu. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OrbitColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Where an app is taken to be got rid of.
 *
 * A cross and a word, at the top of the grid, and only while something is being carried — so it never
 * sits over the grid asking to be pressed. It lights up when the app is over it, because a target that
 * does not say when it has you is a target you are guessing at.
 */
@Composable
internal fun DropTarget(
    armed: Boolean,
    label: String,
    icon: ImageVector,
    destructive: Boolean,
    modifier: Modifier = Modifier
) {
    val armedColor = if (destructive) OrbitColors.Danger else OrbitColors.Accent
    val armedText = stringResource(R.string.drop_target_armed, label)
    Row(
        modifier = modifier
            .graphicsLayer {
                val s = if (armed) 1.06f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(percent = 50))
            .background(if (armed) armedColor else OrbitColors.SurfaceHigh)
            .semantics { if (armed) stateDescription = armedText }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OrbitColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
