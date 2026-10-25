package com.orbit.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.preferences.WheelLook
import com.orbit.home.ui.theme.OrbitColors
import kotlin.math.roundToInt

/** Which control the finger is on, so the rest can get out of the way. */
private enum class Active { NONE, COUNT, ICON, WHEEL, CENTRE }

/**
 * The wheel's controls: a compact panel in the top-right corner.
 *
 * There is no Move control. The wheel's position is set on the home screen by holding its
 * centre, where the wheel being moved is the one you can see; a slider that shifted a wheel
 * you are not looking at was a control with no feedback.
 *
 * Two things shape the design. First, the panel is short and carries no numbers —
 * each control is a label and a slider, and every slider starts in the middle, so
 * left is less and right is more without anything having to say so. Second, while a
 * finger is on any slider, every *other* control in the panel disappears, so the
 * wheel is unobstructed for the one thing being adjusted. The dock and the rest of
 * the home screen are never touched.
 *
 * @param look the values being edited. The caller owns them, so the wheel previews
 *   a change before it is written.
 */
@Composable
fun WheelCustomizer(
    look: WheelLook,
    onLookChange: (WheelLook) -> Unit,
    onEditApps: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The floating shortcut wheel: one global wheel, switched on from here. */
    shortcutEnabled: Boolean = false,
    onShortcutToggle: (Boolean) -> Unit = {},
    onEditShortcutApps: () -> Unit = {},
    /** False for the floating wheel's own customiser, where apps are edited separately. */
    showEditApps: Boolean = true,
    /** False for the floating wheel, which has no shortcut row of its own. */
    showShortcutRow: Boolean = true
) {
    var active by remember { mutableStateOf(Active.NONE) }
    val editing = active != Active.NONE

    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = OrbitColors.SurfaceHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            // Narrow enough that the whole thing fits a 320dp-wide screen.
            modifier = Modifier.width(256.dp)
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)) {
                val closeLabel = stringResource(R.string.close)
    val shortcutLabel = stringResource(R.string.customizer_add_shortcut)
    val haptics = rememberOrbitHaptics()

                // The header and the app picker step aside with the sliders, so a
                // finger on a control leaves nothing on screen but that control.
                if (!editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.customizer_title),
                            color = OrbitColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(44.dp)
                                .semantics { contentDescription = closeLabel }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = OrbitColors.TextSecondary
                            )
                        }
                    }

                    // Hidden in the floating wheel's own customiser: its apps are
                    // edited from the Edit choice, not from inside here.
                    if (showEditApps) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onEditApps)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.customizer_edit_apps),
                            color = OrbitColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "\u203A",
                            color = OrbitColors.TextSecondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    }
                }

                // The floating shortcut wheel. It is a single global wheel rather
                // than one per page, but its switch sits here, beside the wheel it
                // is a smaller version of.
                if (!editing && showShortcutRow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = shortcutEnabled,
                            onCheckedChange = { want ->
                                haptics.toggle(want)
                                onShortcutToggle(want)
                            },
                            modifier = Modifier.semantics {
                                contentDescription = shortcutLabel
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = shortcutLabel,
                            color = OrbitColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onEditShortcutApps) {
                            Text(
                                text = stringResource(R.string.edit),
                                color = OrbitColors.Accent,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                if (!editing || active == Active.COUNT) {
                    WheelSlider(
                        label = stringResource(R.string.customizer_count),
                        value = look.visibleCount.toFloat(),
                        range = WheelLook.COUNT_RANGE.start.toFloat()..
                            WheelLook.COUNT_RANGE.endInclusive.toFloat(),
                        steps = (WheelLook.COUNT_RANGE.endInclusive - WheelLook.COUNT_RANGE.start) - 1,
                        onChange = { onLookChange(look.copy(visibleCount = it.roundToInt())) },
                        onTouchingChange = { touching ->
                            active = if (touching) Active.COUNT else Active.NONE
                        }
                    )
                }

                if (!editing || active == Active.ICON) {
                    WheelSlider(
                        label = stringResource(R.string.customizer_icon_size),
                        value = look.iconScale,
                        range = WheelLook.ICON_RANGE,
                        steps = 0,
                        onChange = { onLookChange(look.copy(iconScale = it)) },
                        onTouchingChange = { touching ->
                            active = if (touching) Active.ICON else Active.NONE
                        }
                    )
                }

                if (!editing || active == Active.WHEEL) {
                    WheelSlider(
                        label = stringResource(R.string.customizer_wheel_size),
                        value = look.radiusScale,
                        range = WheelLook.RADIUS_RANGE,
                        steps = 0,
                        onChange = { onLookChange(look.copy(radiusScale = it)) },
                        onTouchingChange = { touching ->
                            active = if (touching) Active.WHEEL else Active.NONE
                        }
                    )
                }

                if (!editing || active == Active.CENTRE) {
                    WheelSlider(
                        label = stringResource(R.string.customizer_centre_size),
                        value = look.centreScale,
                        range = WheelLook.CENTRE_RANGE,
                        steps = 0,
                        onChange = { onLookChange(look.copy(centreScale = it)) },
                        onTouchingChange = { touching ->
                            active = if (touching) Active.CENTRE else Active.NONE
                        }
                    )
                }

            }
        }
    }
}

/**
 * One control: its name, then the slider. No value is printed — every slider starts
 * in the middle, so less and more are the two directions and nothing needs to say so.
 */
@Composable
private fun WheelSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onTouchingChange: (Boolean) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberOrbitHaptics()
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press, is DragInteraction.Start -> onTouchingChange(true)
                is PressInteraction.Release,
                is PressInteraction.Cancel,
                is DragInteraction.Stop,
                is DragInteraction.Cancel -> onTouchingChange(false)
                else -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = label,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = { next ->
                haptics.slider(value, next, range, steps)
                onChange(next)
            },
            valueRange = range,
            steps = steps,
            interactionSource = source,
            // Additive semantics: the Slider keeps its own adjustable range and
            // SetProgress action, so a screen reader can move it. The label is what
            // is spoken, since there is no visible value to read out.
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

