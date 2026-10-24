package com.orbit.home.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.ui.rememberOrbitHaptics
import com.orbit.home.ui.theme.OrbitColors
import androidx.annotation.DrawableRes

/** One row height and one icon box, so the list reads evenly. */
private val ROW_HEIGHT = 72.dp
private val ICON_BOX = 44.dp

/**
 * The Settings list.
 *
 * Six entries, in a fixed order, each with an icon, a title, a one-line explanation and a
 * chevron. The whole row is the touch target rather than the icon or the words, and the row
 * carries a button role so a screen reader announces it as something to activate rather than
 * as text.
 *
 * The explanations are there because a settings list of bare labels makes the reader guess
 * what each one does. "Apps wheel" and "Floating apps wheel" in particular are two different
 * things that sound the same.
 */
@Composable
fun SettingsHomePage(
    onOpen: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    onHapticsChange: (Boolean) -> Unit = {}
) {
    val haptics = rememberOrbitHaptics()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_title), onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
        ) {
            item {
                SettingsRow(
                    painter = R.drawable.ic_set_apps,
                    title = stringResource(R.string.settings_apps),
                    onClick = { onOpen(SettingsRoute.APPS) }
                )
            }
            item {
                SettingsRow(
                    painter = R.drawable.ic_set_dock,
                    title = stringResource(R.string.settings_dock),
                    onClick = { onOpen(SettingsRoute.DOCK) }
                )
            }
            item {
                SettingsRow(
                    painter = R.drawable.ic_set_wheel,
                    title = stringResource(R.string.settings_wheel),
                    onClick = { onOpen(SettingsRoute.APPS_WHEEL) }
                )
            }
            item {
                SettingsRow(
                    painter = R.drawable.ic_set_floating,
                    title = stringResource(R.string.settings_floating),
                    onClick = { onOpen(SettingsRoute.FLOATING_WHEEL) }
                )
            }
            item {
                SettingsRow(
                    painter = R.drawable.ic_set_centre,
                    title = stringResource(R.string.settings_centre),
                    onClick = { onOpen(SettingsRoute.CENTRE) }
                )
                SettingsRow(
                    painter = R.drawable.ic_set_wallpaper,
                    title = stringResource(R.string.settings_wallpaper),
                    onClick = { onOpen(SettingsRoute.WALLPAPER) }
                )
            }
            item {
                SettingsSwitchRow(
                    painter = R.drawable.ic_set_haptics,
                    title = stringResource(R.string.settings_haptics),
                    summary = stringResource(R.string.settings_haptics_summary),
                    checked = hapticsEnabled,
                    onCheckedChange = { want ->
                        // Plays the switch's own haptic (the last one when turning off, the
                        // first one when turning on), then stores the choice.
                        haptics.masterToggled(want)
                        onHapticsChange(want)
                    }
                )
            }
        }
    }
}

/**
 * The bar every Settings page wears.
 *
 * The title sits next to the back arrow rather than centred, so a long title has room and
 * does not collide with anything on the right.
 */
@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Without this the row draws under the status bar and Back cannot be tapped.
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = Color.White
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        trailing?.invoke()
    }
}

/**
 * One entry in the Settings list.
 *
 * Takes either a Material icon or a drawn one, since the core icon set does not cover every
 * row. Only one is ever supplied.
 */
@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    @DrawableRes painter: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Each icon is a finished drawing: the white disc is part of the artwork, so
        // nothing is drawn behind it here.
        Icon(
            painter = painterResource(painter),
            contentDescription = null,
            // Unspecified on purpose: anything else tints the disc and the glyph alike.
            tint = Color.Unspecified,
            modifier = Modifier.size(ICON_BOX)
        )
        Text(
            text = title,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = OrbitColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * A labelled switch.
 *
 * The title is the touch target as well as the switch, so the row can be used without
 * aiming at the control itself.
 */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = rememberOrbitHaptics()
    // One path for a tap on the row and a tap on the switch itself, so both give the haptic.
    val change: (Boolean) -> Unit = { want ->
        haptics.toggle(want)
        onCheckedChange(want)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Switch) { change(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = change)
    }
}

/**
 * A Settings-list row with a switch instead of a chevron: same icon, height and corner as
 * [SettingsRow], with a one-line explanation. The whole row toggles, and the switch itself only
 * displays, so a screen reader sees one control rather than two.
 */
@Composable
private fun SettingsSwitchRow(
    @DrawableRes painter: Int,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(painter),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(ICON_BOX)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OrbitColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                color = OrbitColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A Settings page that has not been built yet.
 *
 * Deliberately says so. A row that opened an empty page would look broken, and the honest
 * state while the pages are being built in order is to say which step is coming.
 */
@Composable
fun SettingsPlaceholderPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
    ) {
        SettingsTopBar(title = title, onBack = onBack)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.settings_page_coming),
                color = OrbitColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * The Settings area, by route.
 *
 * One entry point so the caller does not have to know which pages exist. The pages that are
 * still being built say so rather than opening empty.
 */
@Composable
fun SettingsArea(
    route: SettingsRoute,
    onOpen: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    onHapticsChange: (Boolean) -> Unit = {}
) {
    when (route) {
        SettingsRoute.HOME -> SettingsHomePage(
            onOpen = onOpen,
            onBack = onBack,
            modifier = modifier,
            hapticsEnabled = hapticsEnabled,
            onHapticsChange = onHapticsChange
        )

        SettingsRoute.APPS -> SettingsPlaceholderPage(
            title = stringResource(R.string.settings_apps), onBack = onBack, modifier = modifier
        )

        SettingsRoute.DOCK -> SettingsPlaceholderPage(
            title = stringResource(R.string.settings_dock), onBack = onBack, modifier = modifier
        )

        SettingsRoute.APPS_WHEEL -> SettingsPlaceholderPage(
            title = stringResource(R.string.settings_wheel), onBack = onBack, modifier = modifier
        )

        SettingsRoute.FLOATING_WHEEL -> SettingsPlaceholderPage(
            title = stringResource(R.string.settings_floating), onBack = onBack, modifier = modifier
        )

        SettingsRoute.WALLPAPER -> SettingsPlaceholderPage(
            title = stringResource(R.string.settings_wallpaper), onBack = onBack, modifier = modifier
        )

        // Reached only if this area is ever handed the route directly. The real page needs the
        // view model, so the home screen draws it; this is what keeps the two in step.
        SettingsRoute.CENTRE -> SettingsPlaceholderPage(
            title = stringResource(R.string.settings_centre), onBack = onBack, modifier = modifier
        )
    }
}
