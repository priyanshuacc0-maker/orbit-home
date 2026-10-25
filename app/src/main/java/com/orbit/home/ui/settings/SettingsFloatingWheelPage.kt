package com.orbit.home.ui.settings

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.animation.WheelMotionState
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.InstalledApp
import com.orbit.home.preferences.WheelLook
import com.orbit.home.ui.CentreMode
import com.orbit.home.ui.CircularAppWheel
import com.orbit.home.ui.theme.OrbitColors
import kotlin.math.roundToInt
import com.orbit.home.apps.CentrePicture

/**
 * Customising the floating edge wheel.
 *
 * The demo shows the wheel where it actually lives: mounted on the right edge of a mock
 * screen, deliberately half outside it. Drawing it as a complete circle floating in the
 * middle of the page would be a picture of something the user never sees.
 *
 * The demo does not start the overlay service, and deliberately does not call
 * setEditingShortcutWheel(true). That path asks the real ShortcutWheelService to bring its
 * window out over everything — which would put a live overlay on top of the very controls
 * being used to adjust it. The page draws its own copy instead, with its own motion state,
 * inside a clipped box so it cannot escape the page.
 *
 * The position slider covers the wheel's whole 0-to-1 edge range. A narrower range would
 * confine the wheel to part of the screen, which is a fault this has already had once.
 */
@Composable
fun SettingsFloatingWheelPage(
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    look: WheelLook,
    offsetY: Float,
    enabled: Boolean,
    canDrawOverlays: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onSave: (WheelLook, Float) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEditApps: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The centre the user has chosen for the floating wheel, if any.
     *
     * Without it the demo would show the mark while the real wheel showed something else.
     */
    centrePicture: CentrePicture? = null
) {
    // Keyed on the saved look so a value that arrives after the page opens reaches the draft.
    // The position is not edited here, so it is passed through as it is now, never a stale copy.
    var draftLook by remember(look) { mutableStateOf(look) }

    val dirty = draftLook != look

    // The demo's own motion state, and only the demo's.
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 0.8f) }
    val previewWheel = remember { WheelMotionState(decay) }
    val ids = apps.map { it.id }
    LaunchedEffect(ids) { previewWheel.focusOnIds(ids) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
    ) {
        SettingsTopBar(
            title = stringResource(R.string.settings_floating),
            onBack = onBack,
            trailing = {
                TextButton(
                    onClick = {
                        onSave(draftLook, offsetY)
                        onBack()
                    },
                    enabled = dirty
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        color = if (dirty) OrbitColors.Accent else OrbitColors.TextSecondary
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            FloatingPreview(
                apps = apps,
                wheel = previewWheel,
                iconLoader = iconLoader,
                look = draftLook,
                offsetY = offsetY,
                centrePicture = centrePicture
            )

            SwitchRow(
                title = stringResource(R.string.settings_floating_enable),
                checked = enabled,
                onCheckedChange = { want ->
                    // Turning it on needs permission to draw over other apps. Asking for it
                    // is the honest response; showing the switch as on when it is not would
                    // be a lie the user would discover later.
                    if (want && !canDrawOverlays) onRequestOverlayPermission() else onToggleEnabled(want)
                }
            )
            if (!canDrawOverlays) {
                Text(
                    text = stringResource(R.string.settings_floating_needs_permission),
                    color = OrbitColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            SliderRow(
                label = stringResource(R.string.customizer_count),
                value = draftLook.visibleCount.toFloat(),
                range = WheelLook.COUNT_RANGE.start.toFloat()..WheelLook.COUNT_RANGE.endInclusive.toFloat(),
                steps = (WheelLook.COUNT_RANGE.endInclusive - WheelLook.COUNT_RANGE.start) - 1
            ) { draftLook = draftLook.copy(visibleCount = it.roundToInt()) }

            SliderRow(
                label = stringResource(R.string.customizer_icon_size),
                value = draftLook.iconScale,
                range = WheelLook.ICON_RANGE
            ) { draftLook = draftLook.copy(iconScale = it) }

            SliderRow(
                label = stringResource(R.string.customizer_wheel_size),
                value = draftLook.radiusScale,
                range = WheelLook.RADIUS_RANGE
            ) { draftLook = draftLook.copy(radiusScale = it) }

            SliderRow(
                label = stringResource(R.string.customizer_centre_size),
                value = draftLook.centreScale,
                range = WheelLook.CENTRE_RANGE
            ) { draftLook = draftLook.copy(centreScale = it) }

            // The whole edge range, straight from the settings that define it. Not a range
            // invented here, and not one that stops partway down the screen.

            SettingsActionRow(
                title = stringResource(R.string.customizer_edit_apps),
                onClick = onEditApps
            )
        }
    }
}

/**
 * Where the floating wheel's icons sit, in degrees.
 *
 * The same value ShortcutWheelService uses. The home wheel faces a different way, so
 * leaving this at the default made the demo look like a home wheel with its edge cut off
 * rather than the floating wheel.
 */
private const val FLOATING_FOCUS_ANGLE = 270f

/**
 * The mock screen.
 *
 * A rounded, bounded panel standing in for the display, with the wheel mounted on its right
 * edge and clipped there. clipToBounds is what does the clipping: the wheel is positioned
 * so half of it falls outside the panel, and the panel simply does not draw that half —
 * exactly what the real overlay does against the real screen edge.
 */
@Composable
internal fun FloatingPreview(
    apps: List<InstalledApp>,
    wheel: WheelMotionState,
    iconLoader: AppIconLoader,
    look: WheelLook,
    offsetY: Float,
    /** The centre the real wheel has, so the demo and the edge of the screen agree. */
    centrePicture: CentrePicture?
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(OrbitColors.Surface)
            .clipToBounds()
    ) {
        if (apps.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_wheel_no_apps),
                color = OrbitColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
            return@Box
        }

        // The wheel is square and sized to the panel's height, so it reads as edge-mounted
        // rather than as a wheel that happens to be near an edge.
        val size = 200.dp
        val travelPx = with(density) { (240.dp - size).toPx() }
        val yOffset = with(density) { (offsetY * travelPx).toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Half of it outside the panel, which is where the real one lives.
                .offset(x = size / 2, y = yOffset)
                .size(size)
        ) {
            CircularAppWheel(
                apps = apps,
                wheel = wheel,
                iconScale = look.iconScale,
                iconLoader = iconLoader,
                onLaunch = {},
                modifier = Modifier.fillMaxSize(),
                // The two things that make this the floating wheel rather than a copy of
                // the home one, both taken from how ShortcutWheelService draws it.
                //
                // The centre is the mark, not an app: on the floating wheel the centre is the
                // thing you press, and an app icon there made it read as a home wheel. A centre
                // the user has chosen stands in the mark's place, exactly as it does on the real
                // wheel, so this demo and the edge of the screen agree.
                focusAngleDegrees = FLOATING_FOCUS_ANGLE,
                labelColor = Color.White,
                centreMode = CentreMode.LOGO,
                centrePicture = centrePicture,
                verticalOffset = 0f,
                radiusScale = look.radiusScale,
                centreScale = look.centreScale,
                visibleCount = look.visibleCount,
                onCentreTap = {},
                passthroughOutsideRing = false
            )
        }
    }
}
