package com.orbit.home.ui.settings

import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.key
import com.orbit.home.R
import com.orbit.home.animation.WheelMotionState
import com.orbit.home.apps.AppIconLoader
import com.orbit.home.apps.InstalledApp
import com.orbit.home.preferences.CentreChoice
import com.orbit.home.preferences.CentreLibrary
import com.orbit.home.preferences.CentreTarget
import com.orbit.home.preferences.CentreTransform
import com.orbit.home.preferences.WheelLook
import com.orbit.home.ui.CentreMode
import com.orbit.home.ui.CircularAppWheel
import com.orbit.home.ui.theme.OrbitColors
import com.orbit.home.viewmodel.CentrePictures
import com.orbit.home.viewmodel.CentreVideoSource

/**
 * Choosing what the wheels' centres show.
 *
 * One entry per home wheel plus the floating wheel. The preview follows the selected entry,
 * using that wheel's own app list, shape and centre choice.
 *
 * The preview's middle is drawn the way that wheel draws it — the app's own icon in the middle of
 * the home screen's wheel, the mark in the middle of the floating one — which is what makes the
 * preview honest about what is being replaced.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCentrePage(
    pictures: CentrePictures,
    homeChoices: List<CentreChoice?>,
    floatingChoice: CentreChoice,
    /** Each wheel's real apps and shape, in the same order as the home pager. */
    homeWheels: List<SettingsWheelEntry>,
    /** The wheel visible when the user opened Settings. */
    initialHomeWheelIndex: Int,
    /** The floating wheel's own contents and shape. */
    floatingApps: List<InstalledApp>,
    floatingLook: WheelLook,
    floatingOffsetY: Float,
    /** Video sources are indexed like home wheels; the preview owns its player independently. */
    homeVideos: List<CentreVideoSource?>,
    reducedMotion: Boolean,
    iconLoader: AppIconLoader,
    onChoose: (CentreTarget, Int, CentreChoice) -> Unit,
    onImport: (CentreTarget, Int, Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var target by remember { mutableStateOf(CentreTarget.HOME) }
    var homeWheelIndex by remember {
        mutableIntStateOf(initialHomeWheelIndex.coerceIn(0, (homeWheels.size - 1).coerceAtLeast(0)))
    }
    var pickingLibrary by remember { mutableStateOf(false) }

    val onHome = target == CentreTarget.HOME
    val homeWheel = homeWheels.getOrNull(homeWheelIndex)
    val choice = if (onHome) {
        homeChoices.getOrNull(homeWheelIndex) ?: CentreChoice.Default
    } else {
        floatingChoice
    }
    val picture = if (onHome) pictures.home.getOrNull(homeWheelIndex) else pictures.floating
    val previewApps = if (onHome) homeWheel?.apps.orEmpty() else floatingApps
    val previewLook = if (onHome) homeWheel?.look ?: WheelLook() else floatingLook
    val previewVideoSource = if (onHome && choice is CentreChoice.Video) {
        homeVideos.getOrNull(homeWheelIndex)
    } else {
        null
    }
    val context = LocalContext.current
    var videoFailed by remember(previewVideoSource?.file) { mutableStateOf(false) }
    var videoHeld by remember(previewVideoSource?.file) { mutableStateOf(false) }
    val previewPlayer = remember(previewVideoSource?.file) {
        val file = previewVideoSource?.file ?: return@remember null
        if (!file.isFile || file.length() <= 0L) return@remember null
        val player = runCatching { ExoPlayer.Builder(context).build() }.getOrNull()
            ?: return@remember null
        try {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.volume = 0f
            player.prepare()
            player
        } catch (_: Exception) {
            player.release()
            null
        }
    }
    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer?.release() }
    }
    DisposableEffect(previewPlayer) {
        val player = previewPlayer
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    videoFailed = true
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    // A settings preview is allowed to animate only while the app is on screen and reduced motion
    // is off. It owns this player instead of borrowing the home page's deliberately paused player.
    val lifecycleOwner = LocalLifecycleOwner.current
    var appVisible by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appVisible = true
                Lifecycle.Event.ON_STOP -> appVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(previewPlayer, reducedMotion, appVisible, videoHeld, videoFailed) {
        val player = previewPlayer ?: return@LaunchedEffect
        when {
            videoFailed -> player.pause()
            !appVisible || reducedMotion -> {
                player.pause()
                player.seekTo(0L)
            }
            videoHeld -> player.pause()
            else -> player.play()
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // Nothing is said about a cancelled pick: nothing happened, so there is nothing to say.
        if (uri != null) onImport(target, homeWheelIndex, uri)
    }

    // A preview of its own, with its own motion, so nothing here can spin or open anything on the
    // home screen behind it.
    val decay = remember {
        exponentialDecay<Float>(frictionMultiplier = WheelMotionState.SPIN_FRICTION)
    }
    val previewWheel = remember { WheelMotionState(decay) }
    val ids = previewApps.map { it.id }
    LaunchedEffect(ids) { previewWheel.focusOnIds(ids) }

    // The floating preview has its own motion and its own ring, because it shows a different
    // wheel rather than the same one in another shape.
    val floatingPreviewWheel = remember { WheelMotionState(decay) }
    val floatingIds = floatingApps.map { it.id }
    LaunchedEffect(floatingIds) { floatingPreviewWheel.focusOnIds(floatingIds) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_centre), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.centre_preview),
                color = OrbitColors.TextSecondary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            if (onHome) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    CircularAppWheel(
                        apps = previewApps,
                        wheel = previewWheel,
                        iconScale = previewLook.iconScale,
                        iconLoader = iconLoader,
                        onLaunch = {},
                        modifier = Modifier.fillMaxSize(),
                        centreMode = CentreMode.APP,
                        centrePicture = picture,
                        centreVideo = if (previewPlayer != null && previewVideoSource != null && !videoFailed) {
                            { m -> CentrePreviewVideoView(previewPlayer, previewVideoSource.transform, m) }
                        } else {
                            null
                        },
                        onCentreVideoTap = if (previewPlayer != null && previewVideoSource != null && !videoFailed) {
                            { videoHeld = !videoHeld }
                        } else {
                            null
                        },
                        radiusScale = previewLook.radiusScale,
                        centreScale = previewLook.centreScale,
                        visibleCount = previewLook.visibleCount,
                        // The preview is centred: the saved offset is where the wheel sits on the home screen,
                        // which the preview cannot change and should not inherit.
                        verticalOffset = 0f,
                        passthroughOutsideRing = false
                    )
                }
            } else {
                // The same mock the floating wheel's own page uses, with the same shape and the
                // same apps, so the two pages cannot show the floating wheel differently.
                FloatingPreview(
                    apps = floatingApps,
                    wheel = floatingPreviewWheel,
                    iconLoader = iconLoader,
                    look = floatingLook,
                    offsetY = floatingOffsetY,
                    centrePicture = picture
                )
            }

            Spacer(Modifier.height(12.dp))

            // A wrapping row of pills, not a scrolling row of cards.
            //
            // With three or more wheels a scrolling row pushed the later ones off the right edge, where
            // they could not be tapped at all - the same bug the Apps wheel page had, and the reason it
            // wraps too. And a pill says which wheel you are editing without taking a third of the
            // screen to say it, which is what the boxes were doing.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                homeWheels.indices.forEach { index ->
                    CentrePill(
                        text = stringResource(R.string.settings_wheel_number, index + 1),
                        selected = onHome && homeWheelIndex == index,
                        onClick = {
                            target = CentreTarget.HOME
                            homeWheelIndex = index
                        }
                    )
                }
                CentrePill(
                    text = stringResource(R.string.centre_target_floating),
                    selected = !onHome,
                    onClick = { target = CentreTarget.FLOATING }
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CentreCard(
                    painter = R.drawable.ic_set_apps,
                    title = stringResource(R.string.centre_library),
                    selected = choice != CentreChoice.Imported && choice !is CentreChoice.Video,
                    onClick = { pickingLibrary = true },
                    modifier = Modifier.weight(1f)
                )
                CentreCard(
                    painter = R.drawable.ic_set_wallpaper,
                    title = stringResource(R.string.centre_import),
                    selected = choice == CentreChoice.Imported || choice is CentreChoice.Video,
                    onClick = {
                        // The home wheel is offered pictures and videos; the floating wheel is
                        // offered pictures only.
                        pickImage.launch(
                            PickVisualMediaRequest(
                                if (target == CentreTarget.FLOATING) {
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                } else {
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                }
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

        }
    }

    if (pickingLibrary) {
        AlertDialog(
            onDismissRequest = { pickingLibrary = false },
            title = { Text(stringResource(R.string.centre_library)) },
            text = {
                Column {
                    CentreLibrary.entries.forEach { entry ->
                        val chosen = entry.choice() == choice
                        Text(
                            text = stringResource(libraryLabel(entry)),
                            color = if (chosen) OrbitColors.Accent else OrbitColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChoose(target, homeWheelIndex, entry.choice())
                                    pickingLibrary = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingLibrary = false }) {
                    Text(stringResource(R.string.cancel), color = OrbitColors.TextSecondary)
                }
            }
        )
    }
}

/**
 * The preview's own video surface. Keep it keyed to the player: AndroidView's factory runs only
 * once, and a new media file needs a new TextureView attached to the new player. Updating the view
 * and clearing it on release also prevents a detached preview from retaining the player surface.
 */
@Composable
private fun CentrePreviewVideoView(
    player: ExoPlayer,
    transform: CentreTransform,
    modifier: Modifier
) {
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = maxWidth
        val turned = transform.quarterTurns % 2 != 0
        val across = if (turned) videoHeight else videoWidth
        val down = if (turned) videoWidth else videoHeight
        val drawnWidth = side * transform.widthFraction
        val drawnHeight = if (across > 0 && down > 0) {
            drawnWidth * (down.toFloat() / across)
        } else {
            drawnWidth
        }

        key(player) {
            AndroidView(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = side * transform.offsetXFraction, y = side * transform.offsetYFraction)
                    .size(width = drawnWidth, height = drawnHeight)
                    .graphicsLayer { rotationZ = 90f * transform.quarterTurns },
                factory = { ctx -> TextureView(ctx) },
                update = { view -> player.setVideoTextureView(view) },
                onRelease = { view -> player.clearVideoTextureView(view) }
            )
        }
    }
}

/** One of the page's choices: a mark, a name, and a ring when it is the one in use. */
@Composable
private fun CentreCard(
    painter: Int,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(OrbitColors.Surface)
            .then(
                if (selected) Modifier.border(2.dp, OrbitColors.Accent, shape) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Image(
            painter = painterResource(painter),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = OrbitColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * One wheel, as a pill.
 *
 * The same shape the Apps wheel page uses for the same job, so two screens that both mean "which wheel"
 * look like they mean the same thing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CentrePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) OrbitColors.Accent else OrbitColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else OrbitColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** What the library calls one of its entries. */
private fun libraryLabel(entry: CentreLibrary): Int = when (entry) {
    CentreLibrary.DEFAULT -> R.string.centre_default
    CentreLibrary.ORBIT -> R.string.centre_orbit
}

