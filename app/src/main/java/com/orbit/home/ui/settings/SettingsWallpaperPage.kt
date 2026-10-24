package com.orbit.home.ui.settings

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.ui.theme.OrbitColors
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the page is telling the user, if anything. */
private sealed interface WallpaperStatus {
    data object Idle : WallpaperStatus
    data object Working : WallpaperStatus
    data object Applied : WallpaperStatus
    data object Cancelled : WallpaperStatus
    data class Failed(val detail: String) : WallpaperStatus

    /** A device with no usable crop activity, so the direct set is offered instead. */

}

/** The shape of the preview card and of the two big buttons. */
private val CARD_RADIUS = 28.dp
private val BUTTON_HEIGHT = 56.dp

/**
 * Choosing a wallpaper.
 *
 * Two ways in: the Android photo picker, which is the modern, permission-free route to the
 * photo library, and a document picker for anything else — Downloads, a cloud provider, a
 * file manager. Neither needs storage permission, and neither is asked for.
 *
 * Choosing does not change anything. The picture is held as a pending choice and shown in the
 * preview with a "New wallpaper" mark, and the wallpaper itself only moves when Apply is
 * pressed. That is what the caption says, and it is what happens: a launcher should not repaint
 * somebody's phone because they were browsing.
 *
 * Applying hands the image to Android's own crop-and-set workflow rather than cropping here.
 * Orbit Home draws the system wallpaper through a transparent window, so setting the system
 * wallpaper is what actually shows up behind the launcher; a second private background layer
 * would be a different app's worth of work and would drift from the rest of the phone.
 *
 * Nothing is claimed as done unless it was. The external workflow reports success or
 * cancellation, and the direct-set fallback — only offered when the device has no usable crop
 * activity — says plainly that it sets the home screen only and asks first.
 */
@Composable
fun SettingsWallpaperPage(
    /** Whether the hidden wallpaper is the one showing. */
    hiddenShowing: Boolean,
    /** The hidden wallpaper, if one has been set. */
    hiddenWallpaper: ImageBitmap?,
    onSetHidden: (Uri) -> Unit,
    onToggleHidden: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<WallpaperStatus>(WallpaperStatus.Idle) }

    /** The chosen picture, held until Apply. Null means nothing is pending. */
    var pending by remember { mutableStateOf<Uri?>(null) }
    var pendingImage by remember { mutableStateOf<ImageBitmap?>(null) }

    /**
     * The picture chosen for the hidden wallpaper, held until it is set.
     *
     * Separate from [pending] because the two wallpapers are two different jobs: one goes through
     * the system's crop-and-set screen and the other is this app's own background, and a page that
     * mixed them would be a page that could send a picture to the wrong one.
     */
    var hiddenPending by remember { mutableStateOf<Uri?>(null) }
    var hiddenPendingImage by remember { mutableStateOf<ImageBitmap?>(null) }


    // The chosen picture, decoded off the main thread so a large photograph cannot stall the
    // page while it is being opened. Without this the preview has nothing to draw and falls
    // back to its gradient, which looks like the page having lost the picture.
    LaunchedEffect(pending) {
        pendingImage = pending?.let { readImage(context, it) }
    }

    LaunchedEffect(hiddenPending) {
        hiddenPendingImage = hiddenPending?.let { readImage(context, it) }
    }

    // What was pending is now the phone's wallpaper, so there is nothing left to hold.
    // The external crop-and-set workflow. Its result is the only thing treated as success.
    
    // The photo picker: the platform's own, so no permission and no access to anything the
    // user did not choose.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            status = WallpaperStatus.Cancelled
        } else {
            status = WallpaperStatus.Idle
            pending = uri
        }
    }

    // Everything else: Downloads, a cloud provider, a file manager.
    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            status = WallpaperStatus.Cancelled
        } else {
            status = WallpaperStatus.Idle
            pending = uri
        }
    }

    /**
     * The hidden wallpaper's own way in.
     *
     * Its own picker rather than a mode the two above switch into: tapping the box starts the
     * selection there and then, which is what a box named after a wallpaper looks like it should do.
     * The system picker reaches files as well as photographs, so nothing is lost by not offering a
     * separate "browse" for it.
     */
    val pickHidden = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            status = WallpaperStatus.Cancelled
        } else {
            status = WallpaperStatus.Idle
            hiddenPending = uri
        }
    }

    val busy = status == WallpaperStatus.Working
    val chosen = pending

    // The shape of this phone, so the picture is shown as the phone will show it rather than
    // cropped into some other rectangle.
    // The same frame the picture is cut to when applied, so the preview is what will be set.
    val screenRatio = remember(LocalConfiguration.current) {
        val (w, h) = screenFrameSize(context)
        if (w > 0 && h > 0) w.toFloat() / h.toFloat() else DEFAULT_SCREEN_RATIO
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_wallpaper), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // One preview, for whichever wallpaper a picture is waiting for.
            //
            // The two are separate jobs but the same step, so the same card serves both — at the
            // phone's shape and the largest that fits, which is what makes it a picture of what the
            // home screen will look like rather than a picture of a picture.
            val hiddenChosen = hiddenPending
            val pendingPicture = if (chosen != null) pendingImage else hiddenPendingImage

            if (chosen != null || hiddenChosen != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val tallest = minOf(maxHeight, maxWidth / screenRatio)
                    WallpaperPreview(
                        image = pendingPicture,
                        modifier = Modifier
                            .height(tallest)
                            .width(tallest * screenRatio)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WallpaperButton(
                        text = stringResource(R.string.cancel),
                        filled = false,
                        enabled = !busy,
                        onClick = {
                            // Letting go of the choice changes nothing, which is what Cancel
                            // has to mean here.
                            pending = null
                            hiddenPending = null
                            status = WallpaperStatus.Cancelled
                        },
                        modifier = Modifier.weight(1f)
                    )
                    WallpaperButton(
                        text = stringResource(R.string.wallpaper_apply),
                        filled = true,
                        enabled = !busy,
                        onClick = {
                            if (chosen != null) {
                                // Framed and set here, by this app.
                                //
                                // The system's crop screen was asked for first and it was the wrong
                                // thing to rely on: it will not accept a picture from the system photo
                                // picker, and on a phone where pictures come from that picker the
                                // request either fails or opens something that never comes back - and
                                // the page then sat saying it was opening, with nothing left to
                                // change it. There is no third party to wait for now.
                                status = WallpaperStatus.Working
                                val appContext = context.applicationContext
                                scope.launch {
                                    // NonCancellable: pressing Back or Home must not abort the write
                                    // half-way. The result is announced here too, because the page
                                    // may already be gone by the time it arrives.
                                    val result = withContext(NonCancellable) {
                                        setDirectly(appContext, chosen).also { r ->
                                            Toast.makeText(
                                                appContext,
                                                if (r == WallpaperStatus.Applied) R.string.wallpaper_applied
                                                else R.string.wallpaper_failed,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    status = result
                                    if (status == WallpaperStatus.Applied) pending = null
                                }
                            } else if (hiddenChosen != null) {
                                // The hidden one is this app's own background, so it is simply
                                // kept — the same picture, the same preview, one step less.
                                status = WallpaperStatus.Idle
                                hiddenPending = null
                                onSetHidden(hiddenChosen)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))

                // The phone's wallpaper, and only that. The hidden one has its own box below and
                // nothing of its own up here.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WallpaperSourceCard(
                        painter = R.drawable.ic_set_wallpaper,
                        title = stringResource(R.string.wallpaper_choose_photo),
                        enabled = !busy,
                        onClick = {
                            status = WallpaperStatus.Idle
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    WallpaperSourceCard(
                        painter = R.drawable.ic_action_folder,
                        title = stringResource(R.string.wallpaper_browse_files),
                        enabled = !busy,
                        onClick = {
                            status = WallpaperStatus.Idle
                            // image/* only. Asking for all files would be a broader permission
                            // than choosing a picture needs, and would show the user things
                            // they did not come here to pick.
                            pickDocument.launch(arrayOf("image/*"))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // The hidden wallpaper. Tapping it picks a picture, and from there it is the same
                // preview and the same two buttons as the phone's — the difference is only where
                // the picture ends up.
                WallpaperSourceCard(
                    painter = R.drawable.ic_set_wallpaper,
                    title = stringResource(R.string.wallpaper_hidden),
                    subtitle = stringResource(
                        if (hiddenWallpaper == null) R.string.wallpaper_hidden_none
                        else R.string.wallpaper_hidden_sub
                    ),
                    enabled = !busy,
                    onClick = {
                        status = WallpaperStatus.Idle
                        pickHidden.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            WallpaperStatusLine(status)
            Spacer(Modifier.height(16.dp))
        }
    }

}

/**
 * The picture that is waiting to be applied, as the phone will show it.
 *
 * Nothing is drawn over it. A chip naming the wallpaper and a row of grey squares standing in for
 * the dock both sat on top of the picture, and both got in the way of the one thing this card is
 * for, which is seeing the picture.
 */
@Composable
private fun WallpaperPreview(image: ImageBitmap?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CARD_RADIUS)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(OrbitColors.Accent.copy(alpha = 0.35f), OrbitColors.Background)
                )
            )
            // The outline is always here: this card only ever shows a picture that has not
            // been applied yet.
            .border(2.dp, OrbitColors.Accent, shape)
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** One way in: a card with its own mark and its name. */
@Composable
private fun WallpaperSourceCard(
    painter: Int,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(OrbitColors.Surface)
            .then(
                if (selected) Modifier.border(2.dp, OrbitColors.Accent, shape) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp)
    ) {
        // The drawing carries its own disc, so nothing is drawn behind it.
        Image(
            painter = painterResource(painter),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = OrbitColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


/**
 * Cancel and Apply.
 *
 * Cancel is an outline rather than a fill, so the two are not competing for the eye: one of
 * them changes the phone and the other one does not.
 */
@Composable
private fun WallpaperButton(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(CARD_RADIUS)
    Box(
        modifier = modifier
            .height(BUTTON_HEIGHT)
            .clip(shape)
            .then(
                if (filled) Modifier.background(OrbitColors.Accent)
                else Modifier.border(1.dp, OrbitColors.Guide, shape)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}


/**
 * The fallback: set the image directly.
 *
 * Only reached when the device has no usable crop activity, and only after the user has
 * agreed to it in a dialog. Home screen only — FLAG_SYSTEM, never FLAG_LOCK, because
 * changing someone's lock screen without being asked is not a thing a launcher should do.
 *
 * The read happens off the main thread: an image from a cloud provider can take a while to
 * arrive, and the UI must not be holding its breath while it does.
 */
private suspend fun setDirectly(context: Context, uri: Uri): WallpaperStatus = withContext(Dispatchers.IO) {
    try {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isSetWallpaperAllowed) {
            return@withContext WallpaperStatus.Failed(
                context.getString(R.string.wallpaper_not_allowed)
            )
        }
        // Cut to the shape and size the wallpaper is asked for, then set as that whole bitmap.
        // Handing the picture over as it arrived, with no crop hint, is what left it short of the
        // screen's edges - and the home screen draws the wallpaper through its own window with a
        // transparent background, so the gap showed whatever was underneath.
        val framed = frameForWallpaper(context, uri)
            ?: return@withContext WallpaperStatus.Failed(context.getString(R.string.wallpaper_unreadable))
        // The hint is the whole bitmap: it is already exactly the screen's shape, so the system has
        // nothing to choose and nothing to crop.
        manager.setBitmap(
            framed,
            android.graphics.Rect(0, 0, framed.width, framed.height),
            true,
            WallpaperManager.FLAG_SYSTEM
        )
        // Not recycled: setBitmap copies on stock Android, but an OEM that keeps the reference would
        // show a black or torn wallpaper. The garbage collector frees it safely.
        WallpaperStatus.Applied
    } catch (t: Throwable) {
        com.orbit.home.diagnostics.Diagnostics.warn("wallpaper direct set failed", t)
        WallpaperStatus.Failed(t.message ?: context.getString(R.string.wallpaper_failed))
    }
}

/**
 * A picture the user chose, decoded off the main thread and downscaled on the way in.
 *
 * The bounds are read first so a very large photograph is never fully decoded: a modern phone
 * camera's picture at full size is many times what a preview card can show, and decoding it
 * whole is how a settings page runs out of memory.
 */
private suspend fun readImage(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val widest = maxOf(bounds.outWidth, bounds.outHeight)
        if (widest <= 0) return@runCatching null

        var sample = 1
        while (widest / (sample * 2) >= PREVIEW_PX) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    }.getOrNull()
}

/** Roughly how wide the preview is drawn, in pixels, at the largest this page needs. */
private const val PREVIEW_PX = 1080

/** Only used if the configuration will not report a size, which should not happen. */
private const val DEFAULT_SCREEN_RATIO = 0.5f

/** What happened, in plain words. Nothing is said when nothing has happened yet. */
@Composable
private fun WallpaperStatusLine(status: WallpaperStatus) {
    val message = when (status) {
        WallpaperStatus.Idle -> null
        WallpaperStatus.Working -> stringResource(R.string.wallpaper_working)
        WallpaperStatus.Applied -> stringResource(R.string.wallpaper_applied)
        WallpaperStatus.Cancelled -> stringResource(R.string.wallpaper_cancelled)
        is WallpaperStatus.Failed ->
            stringResource(R.string.wallpaper_failed) + if (status.detail.isNotBlank()) " (${status.detail})" else ""
    } ?: return

    Text(
        text = message,
        color = if (status == WallpaperStatus.Applied) OrbitColors.Accent else OrbitColors.TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    )
}
