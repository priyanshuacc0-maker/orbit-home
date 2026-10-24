package com.orbit.home.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
 * What you can do to an app without opening it: see its settings, take it off the home screen, or take
 * it off the phone.
 *
 * It sits beside the app, never on it, and grows out of the edge that is nearest the app ([fromTop] says
 * which). The destructive row is the only one with colour, so the eye lands on it last and knowingly.
 */
@Composable
internal fun AppMenu(
    app: InstalledApp,
    onAppInfo: () -> Unit,
    onRemove: (() -> Unit)?,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
    /** Offered first, by the drawer only: put the app on the home screen. */
    onAddToHome: (() -> Unit)? = null,
    /** True when the menu is below the app, so it grows downwards from its top edge. */
    fromTop: Boolean = true,
    /** Where along the menu's width the app is, 0 to 1: the point the menu grows out of. */
    originX: Float = 0.5f,
    /** False until the menu has been measured and placed, so it never plays its entrance in the wrong spot. */
    ready: Boolean = true
) {
    val progress = rememberPopProgress(ready)
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .popTransform(
                progress = { if (ready) progress.value else 0f },
                origin = TransformOrigin(originX, if (fromTop) 0f else 1f),
                from = 0.8f,
                rise = if (fromTop) (-6).dp else 6.dp
            )
            .width(220.dp)
            .clip(shape)
            .background(OrbitColors.SurfaceHigh)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(vertical = 6.dp)
    ) {
        if (onAddToHome != null) {
            MenuRow(
                icon = Icons.Filled.Add,
                label = stringResource(R.string.drawer_add_to_home),
                onClick = onAddToHome
            )
            MenuDivider()
        }
        MenuRow(
            icon = Icons.Filled.Info,
            label = stringResource(R.string.app_info),
            onClick = onAppInfo
        )
        MenuDivider()
        if (onRemove != null) {
            MenuRow(
                icon = Icons.Filled.Close,
                label = stringResource(R.string.app_remove),
                onClick = onRemove
            )
            MenuDivider()
        }
        MenuRow(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.app_uninstall),
            onClick = onUninstall,
            tint = MENU_DESTRUCTIVE
        )
    }
}

private val MENU_DESTRUCTIVE = Color(0xFFFF6B66)

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f))
    )
}

/** One line of the menu. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = OrbitColors.TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
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
    val armedColor = if (destructive) OrbitColors.Danger else Color(0xFF55555F)
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
