package com.orbit.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.home.R
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.InstalledApp
import com.orbit.home.preferences.GridFolder
import com.orbit.home.preferences.GridFolderCodec
import com.orbit.home.ui.theme.OrbitColors

/** How many apps sit side by side in an open folder. */
private const val FOLDER_COLUMNS = 3

/**
 * A folder as it sits in the grid: a rounded tile with the first four of its apps showing, and its
 * name underneath, laid out exactly as [AppCell] is so a row of mixed apps and folders stays on a line.
 *
 * [armed] is the moment an app being carried is over it long enough that letting go will put the app
 * in. The tile swells a little to say so - the same signal a phone gives.
 */
@Composable
internal fun FolderCell(
    folder: GridFolder,
    /** The folder's apps that are installed, in the folder's order. */
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    armed: Boolean,
    onOpen: () -> Unit
) {
    val name = folder.name.ifBlank { stringResource(R.string.folder_default_name) }
    val openLabel = stringResource(R.string.folder_open_label, name)
    val scale by animateFloatAsState(
        targetValue = if (armed) 1.16f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "folderArmed"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(15.dp))
                .background(OrbitColors.SurfaceHigh)
                .then(
                    if (armed) Modifier.border(2.dp, OrbitColors.Accent, RoundedCornerShape(15.dp))
                    else Modifier
                )
                .padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                apps.take(4).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        pair.forEach { MiniIcon(it, iconLoader) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            color = OrbitColors.TextPrimary,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** One app's icon, small, for the folder's tile. */
@Composable
private fun MiniIcon(app: InstalledApp, iconLoader: AppIconLoader) {
    val px = with(LocalDensity.current) { 20.dp.roundToPx() }
    val icon = rememberAppIcon(app.id, px, iconLoader)
    Box(modifier = Modifier.size(20.dp)) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * An open folder: the grid dims, and a panel in the middle shows the folder's name - editable - and its
 * apps. Tap an app to open it, tap outside (or go back) to close.
 *
 * Holding an app and dragging carries it out of the folder: [dragFor] is the same hold-and-drag the
 * drawer uses, and it reports to the grid, which treats the app as one arriving from outside. The
 * panel is only hidden while that happens, not removed, because the touch belongs to the icon inside
 * it and removing the icon would cancel the drag.
 */
@Composable
internal fun FolderPopup(
    folder: GridFolder,
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    hidden: Boolean,
    onLaunch: (InstalledApp) -> Unit,
    /** Called with the final name once it has been changed. */
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    dragFor: (InstalledApp) -> AppDragCallbacks,
    modifier: Modifier = Modifier
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val focus = LocalFocusManager.current
    val defaultName = stringResource(R.string.folder_default_name)
    val nameLabel = stringResource(R.string.folder_name_label)
    val nameStyle = TextStyle(
        color = OrbitColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )

    /** Keep the name if it was changed. Called on every way out, so none of them loses an edit. */
    fun commitName() {
        val trimmed = name.trim()
        if (trimmed != folder.name) onRename(trimmed)
    }

    fun close() {
        commitName()
        focus.clearFocus()
        onDismiss()
    }

    BackHandler(enabled = !hidden) { close() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (hidden) 0f else 1f },
        contentAlignment = Alignment.Center
    ) {
        // Anywhere outside the panel closes it, and takes the touch so nothing underneath opens.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { close() }
                )
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(OrbitColors.SurfaceHigh)
                // Taps on the panel's own gaps must not fall through to the scrim and close it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(top = 18.dp, bottom = 10.dp)
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it.replace("\n", "").take(GridFolderCodec.MAX_NAME) },
                singleLine = true,
                textStyle = nameStyle,
                cursorBrush = SolidColor(OrbitColors.Accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    commitName()
                    focus.clearFocus()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .semantics { contentDescription = nameLabel },
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.Center) {
                        if (name.isEmpty()) {
                            Text(
                                text = defaultName,
                                style = nameStyle.copy(color = OrbitColors.TextSecondary)
                            )
                        }
                        field()
                    }
                }
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                apps.chunked(FOLDER_COLUMNS).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (app in row) {
                            Box(modifier = Modifier.weight(1f)) {
                                AppCell(
                                    app = app,
                                    iconLoader = iconLoader,
                                    onLaunch = {
                                        commitName()
                                        onLaunch(app)
                                    },
                                    dragSource = dragFor(app)
                                )
                            }
                        }
                        repeat(FOLDER_COLUMNS - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
