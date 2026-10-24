package com.orbit.home.ui.settings

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.animation.WheelMotionState
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.ui.rememberOrbitHaptics
import com.orbit.home.apps.InstalledApp
import com.orbit.home.preferences.WheelLook
import com.orbit.home.ui.CentreMode
import com.orbit.home.ui.CircularAppWheel
import com.orbit.home.ui.theme.OrbitColors
import kotlin.math.roundToInt
import com.orbit.home.apps.CentrePicture

/**
 * Customising a home-screen app wheel.
 *
 * A live wheel at the top, its controls below in a normal vertical settings layout, and a
 * wheel selector when there is more than one.
 *
 * The preview is deliberately not the home screen's wheel. It has its own motion state, so
 * spinning it does not spin the real one; its taps do nothing, so tapping it does not launch
 * an app; and it cannot page the launcher, because it is not inside the pager. A preview
 * that shared any of that would let someone rearrange their home screen by accident while
 * changing an icon size.
 *
 * The draft is kept per wheel and keyed on the wheel's index, so switching between wheels
 * cannot carry one wheel's unsaved shape onto another. It is written once when the page is
 * left or the selection changes — not on every slider frame.
 */
@Composable
fun SettingsWheelPage(
    wheels: List<SettingsWheelEntry>,
    iconLoader: AppIconLoader,
    initialIndex: Int,
    /** True once the match-all explanation has been shown, so it is shown only once. */
    matchHintSeen: Boolean,
    onSaveLook: (Int, WheelLook) -> Unit,
    onMatchAll: (WheelLook) -> Unit,
    onMatchHintShown: () -> Unit,
    onEditApps: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The centre the user has chosen for this wheel, if any.
     *
     * The preview has to draw it, or the page would show the app icon in the middle of a wheel
     * whose middle is actually something else — the one place where a preview that disagrees with
     * the home screen is worse than no preview.
     */
    centrePictures: List<CentrePicture?> = emptyList()
) {
    // Clamped, because a wheel could have been removed while the page was open.
    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, (wheels.size - 1).coerceAtLeast(0))) }
    val entry = wheels.getOrNull(index)

    // One motion state for the preview, and only the preview.
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 0.8f) }
    val previewWheel = remember { WheelMotionState(decay) }

    // The draft, keyed on the wheel. Switching wheels makes a new draft from the newly
    // selected wheel's stored shape, so the two can never be confused.
    var draft by remember(index) { mutableStateOf(entry?.look ?: WheelLook()) }

    // The draft is written only when Save is pressed.
    //
    // It used to be written whenever the page was left, which sounded tidy and was not: it
    // saved on a Back the user may have meant as a discard, and it left no moment at which
    // the change had visibly landed. Save is a button now, and Back discards — which is what
    // every other settings screen does and what makes "did it apply" answerable.
    val save = { onSaveLook(index, draft) }

    // Leaving with an unsaved change should not silently keep it, and should not silently
    // lose it either, so the fact is stated rather than guessed at.
    val dirty = draft != (entry?.look ?: WheelLook())

    // Match all is a real change to every wheel, so the first time it is asked about rather
    // than done. Afterwards it just does it — the explanation has been read, and repeating
    // it would be a nag.
    var confirmingMatch by remember { mutableStateOf(false) }
    val matchAll = { onMatchAll(draft) }

    // Keep the preview pointing at the same apps when the selection changes.
    val ids = entry?.apps?.map { it.id } ?: emptyList()
    LaunchedEffect(ids) { previewWheel.focusOnIds(ids) }

    if (confirmingMatch) {
        AlertDialog(
            onDismissRequest = { confirmingMatch = false },
            title = { Text(stringResource(R.string.settings_wheel_match_all)) },
            text = { Text(stringResource(R.string.settings_wheel_match_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingMatch = false
                    // Remembered before it is done, so a crash in between cannot make the
                    // explanation appear a second time.
                    onMatchHintShown()
                    matchAll()
                }) {
                    Text(stringResource(R.string.settings_wheel_match), color = OrbitColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingMatch = false }) {
                    Text(stringResource(R.string.cancel), color = OrbitColors.TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
    ) {
        SettingsTopBar(
            title = stringResource(R.string.settings_wheel),
            onBack = onBack,
            trailing = {
                TextButton(
                    onClick = {
                        save()
                        // Back to the list, so the saved state is what is on screen and the
                        // home wheel behind it has already been told.
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
            if (wheels.size > 1) {
                WheelSelector(
                    count = wheels.size,
                    selected = index,
                    onSelect = { index = it }
                )
            }

            // The live preview. A bounded box, so the wheel is drawn inside the page and
            // cannot spill over the controls underneath it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (entry == null || entry.apps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_wheel_no_apps),
                        color = OrbitColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    CircularAppWheel(
                        apps = entry.apps,
                        wheel = previewWheel,
                        iconScale = draft.iconScale,
                        iconLoader = iconLoader,
                        // Nothing here launches, and nothing here holds: the preview is for
                        // looking at, and every gesture on it belongs to the sliders below.
                        onLaunch = {},
                        modifier = Modifier.fillMaxSize(),
                        labelColor = Color.White,
                        centreMode = CentreMode.APP,
                        centrePicture = centrePictures.getOrNull(index),
                        // The preview is centred: the saved offset is where the wheel sits on the home screen,
                        // which the preview cannot change and should not inherit.
                        verticalOffset = 0f,
                        radiusScale = draft.radiusScale,
                        centreScale = draft.centreScale,
                        visibleCount = draft.visibleCount,
                        onCentreTap = {},
                        passthroughOutsideRing = false
                    )
                }
            }

            // The controls, using the model's own ranges so the preview and the real wheel
            // cannot be told different stories.
            SliderRow(
                label = stringResource(R.string.customizer_count),
                value = draft.visibleCount.toFloat(),
                range = WheelLook.COUNT_RANGE.start.toFloat()..WheelLook.COUNT_RANGE.endInclusive.toFloat(),
                steps = (WheelLook.COUNT_RANGE.endInclusive - WheelLook.COUNT_RANGE.start) - 1
            ) { draft = draft.copy(visibleCount = it.roundToInt()) }

            SliderRow(
                label = stringResource(R.string.customizer_icon_size),
                value = draft.iconScale,
                range = WheelLook.ICON_RANGE
            ) { draft = draft.copy(iconScale = it) }

            SliderRow(
                label = stringResource(R.string.customizer_wheel_size),
                value = draft.radiusScale,
                range = WheelLook.RADIUS_RANGE
            ) { draft = draft.copy(radiusScale = it) }

            SliderRow(
                label = stringResource(R.string.customizer_centre_size),
                value = draft.centreScale,
                range = WheelLook.CENTRE_RANGE
            ) { draft = draft.copy(centreScale = it) }

            // The same picker the home screen uses, against this wheel's index.
            SettingsActionRow(
                title = stringResource(R.string.customizer_edit_apps),
                onClick = { onEditApps(index) }
            )

            // Copies this wheel's shape onto every wheel. Only shown when there is more
            // than one to match — with a single wheel it would do nothing.
            if (wheels.size > 1) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_wheel_match_all),
                    onClick = {
                        if (matchHintSeen) matchAll() else confirmingMatch = true
                    }
                )
            }
        }
    }
}

/** One wheel on the Settings page: what it holds, and the shape it is currently stored with. */
data class SettingsWheelEntry(
    val apps: List<InstalledApp>,
    val look: WheelLook
)

/** Which wheel is being edited. Only shown when there is a choice to make. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WheelSelector(count: Int, selected: Int, onSelect: (Int) -> Unit) {
    // A wrapping row, not a plain one. With three or more wheels a plain Row pushed the
    // later chips off the right edge, where they could not be tapped — which is why only
    // the first two wheels ever appeared to be editable. This shows every wheel, however
    // many there are, and wraps onto another line rather than hiding them.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until count) {
            val here = i == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (here) OrbitColors.Accent else OrbitColors.SurfaceHigh)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    // Numbered from one: "Wheel 1" is what a person expects to see, and the
                    // stored index is an implementation detail.
                    text = stringResource(R.string.settings_wheel_number, i + 1),
                    color = if (here) Color.White else OrbitColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** A labelled slider. The label sits above so a long one has the full width. */
@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    val haptics = rememberOrbitHaptics()
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
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
            steps = steps.coerceAtLeast(0)
        )
    }
}

/** A plain tappable row, for actions that are not sliders. */
@Composable
internal fun SettingsActionRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = OrbitColors.Accent,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
