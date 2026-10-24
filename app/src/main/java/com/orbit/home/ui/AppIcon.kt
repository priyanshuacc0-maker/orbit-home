package com.orbit.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.orbit.home.R
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.ui.theme.OrbitColors

/**
 * Loads an app's real icon lazily, at [px] pixels, keyed on the app id.
 *
 * The initial value comes from a synchronous cache peek, so an icon that has
 * already been decoded shows on the very first frame. This is what removes the
 * flicker while the wheel spins.
 */
@Composable
fun rememberAppIcon(id: String, px: Int, loader: AppIconLoader): ImageBitmap? {
    // The loader's generation is read as state, so a wholesale cache clear
    // re-requests every icon currently on screen.
    val generation = loader.generation.intValue
    var bitmap by remember(id, px) { mutableStateOf(loader.peek(id, px)) }
    LaunchedEffect(id, px, generation) {
        // The old bitmap stays until the new one is ready, so a refresh never
        // blanks or flickers the icon.
        loader.load(id, px)?.let { bitmap = it }
    }
    return bitmap
}

/**
 * One app icon, with accessible semantics.
 *
 * Tap opens the app. Every icon is drawn at its original size and full opacity, and nothing
 * moves or buzzes on the way down, so the tap is not separated from the app appearing.
 */
@Composable
fun AppIcon(
    label: String,
    icon: ImageBitmap?,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * An optional second gesture. Passing it makes Compose wait for a possible
     * second tap before firing [onClick], so the single tap lands a beat later —
     * which is why only the dock asks for it, and the wheel and the drawer do not.
     */
) {
    // Keeps the latest callbacks without restarting the gesture detector.
    val currentOnClick by rememberUpdatedState(onClick)

    val openLabel = stringResource(R.string.cd_open)

    Box(
        modifier = modifier
            .size(size)
            // mergeDescendants so the icon and its label are announced once, and an
            // explicit action so a screen reader can activate the app even though
            // the gesture is handled by hand (audit ORB-005).
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                onClick(label = openLabel) {
                    currentOnClick()
                    true
                }
            }
            // A plain tap, and nothing on the way down. The icon used to squash to 88% and
            // tick the moment a finger landed, which was meant to acknowledge the touch but
            // fired on every touch: it squashed and ticked when the press turned out to be a
            // spin of the wheel, or the start of a pull up from the dock. And because it was
            // a hard snap rather than an animated press, it read as the app hesitating before
            // it opened. The icon now goes straight from the tap to the app.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                // The parent node already carries the label: saying it twice makes
                // screen readers announce the app twice.
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // Fallback only until the real drawable has been decoded.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(OrbitColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.take(1).uppercase(),
                    color = OrbitColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
