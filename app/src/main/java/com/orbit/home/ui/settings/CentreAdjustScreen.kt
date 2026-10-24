package com.orbit.home.ui.settings

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.home.R
import com.orbit.home.ui.theme.OrbitColors
import kotlin.math.roundToInt
import com.orbit.home.apps.renderFramed
import com.orbit.home.overlay.DockEdge
import com.orbit.home.preferences.CentreTransform

/** How much bigger than the smallest covering size a picture may be zoomed. */
private const val MAX_ZOOM = 6f

/**
 * The grid over the picture.
 *
 * A grey rather than a white, and a solid one: a white hairline disappears over a bright photograph
 * and a faint one disappears over a dark one, and the grid is only useful if it is visible on both.
 */
private val GRID_COLOR = Color(0xFFB0B0B0)

/** The circle that will actually be visible in the centre, and the frame around it. */
private val GUIDE_COLOR = Color.White.copy(alpha = 0.9f)

/**
 * Framing a picture for a wheel's centre.
 *
 * The picture is picked, and then it is *chosen from*: this is the screen where the part that ends
 * up in the middle is decided, rather than left to whatever happens to be in the centre of the file.
 *
 * Three things are drawn over it. The square frame is what is cropped, and the thirds inside it are
 * the usual guide for composing by eye. The circle is the honest one: a centre is drawn round, so
 * the corners of the square are cut afterwards, and the circle shows exactly what survives. Without
 * it a picture could be framed perfectly and come out wrong.
 *
 * Cancel leaves everything as it was. The middle button turns the picture a quarter at a time,
 * because a picture framed the wrong way up cannot be fixed by moving it. Done keeps it.
 *
 * [dockEdge] is for a centre that lives on a screen edge. The floating wheel's centre is half
 * outside the display, so only half of it is ever seen — and a guide that promised a whole circle
 * would be promising something the screen cannot show. There the guide is a half circle, on the
 * side that survives for the edge the wheel is docked to, and everything that will not be seen is
 * dimmed, so the picture can be framed by what is left bright.
 */
@Composable
fun CentreAdjustScreen(
    image: ImageBitmap,
    onCancel: () -> Unit,
    /**
     * The framed square, and the framing that produced it.
     *
     * Both are handed on because the two kinds of centre need different ones: a picture is saved as
     * the square, its framing baked in, and a video is saved with the numbers because its frames
     * cannot be rendered once and kept.
     */
    onDone: (ImageBitmap, CentreTransform) -> Unit,
    modifier: Modifier = Modifier,
    dockEdge: DockEdge? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var quarterTurns by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var framePx by remember { mutableFloatStateOf(0f) }

    // The picture is turned itself rather than a rotation being carried through the crop
    // arithmetic. The frame is square, so a quarter turn only swaps the picture's sides — doing it
    // to the picture leaves every rule about covering the frame and staying inside it exactly as
    // it was, and turned from the original each time so four turns cost nothing in quality.
    // Turned off the main thread: a full-size bitmap copy is jank, and an ANR on a slow phone.
    // The turn count is carried with the result so Done can wait until they agree.
    val workingState = produceState(image to 0, image, quarterTurns) {
        value = withContext(Dispatchers.Default) { rotateQuarters(image, quarterTurns) } to quarterTurns
    }
    val working = workingState.value.first
    val workingReady = workingState.value.second == quarterTurns
    val scope = rememberCoroutineScope()
    var rendering by remember { mutableStateOf(false) }

    // Where the picture starts and how far it may be taken. Both are recomputed when the picture
    // changes shape, which a turn does, so a turned picture is re-fitted rather than left with a
    // gap at one side.
    val coverScale = remember(working.width, working.height, framePx) {
        CropMath.coverScale(working.width, working.height, framePx)
    }
    val floorScale = remember(working.width, working.height, framePx) {
        CropMath.floorScale(working.width, working.height, framePx)
    }

    // Arrives covering the frame and centred, so the first thing seen is the whole picture with
    // nothing cut off that did not have to be. From there it can be made smaller than the frame as
    // well as larger: a small mark in the middle of a large circle is a thing people want, and the
    // frame used to make it impossible.
    fun reset() {
        scale = coverScale
        offsetX = 0f
        offsetY = 0f
    }
    // Also on every turn: a quarter turn of a square picture leaves the cover scale as it was, and
    // the old offset would then be applied to a picture that is now facing another way.
    LaunchedEffect(coverScale, quarterTurns) { reset() }

    val transform = CropTransform(scale, offsetX, offsetY)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
            .systemBarsPadding()
            .navigationBarsPadding()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Square, and as large as the room allows, so the frame is the picture and nothing else.
            val side = minOf(maxWidth, maxHeight)
            Box(
                modifier = Modifier
                    .size(side)
                    .onSizeChanged { framePx = it.width.toFloat() }
                    .pointerInput(working, framePx, coverScale, floorScale) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val next = CropMath.clampScale(
                                scale * zoom,
                                floorScale,
                                coverScale * MAX_ZOOM
                            )
                            // The ratio actually applied, after the limits: using the requested zoom
                            // would let the picture drift while it is held at the largest or smallest.
                            val ratio = if (scale > 0f) next / scale else 1f
                            // Zoomed about the fingers and moved by exactly what they moved, to
                            // wherever it is taken. There is no limit: where the picture sits inside
                            // the frame is the user's business, and the frame is only a window onto it.
                            val moved = CropMath.zoomAbout(
                                offsetX, offsetY,
                                centroid.x - size.width / 2f, centroid.y - size.height / 2f,
                                ratio, pan.x, pan.y
                            )
                            scale = next
                            offsetX = moved.first
                            offsetY = moved.second
                        }
                    }
            ) {
                // Clipped to the frame: what is drawn outside it is not in the result, and a preview
                // that shows more than will be kept is not a preview of what will be kept.
                Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val frame = size.width
                    if (frame <= 0f) return@Canvas

                    // The same arithmetic renderFramed uses: scale about the picture's corner, then
                    // translate. Rounding the destination to whole pixels, as this used to, put the
                    // preview up to half a pixel away from the saved result at every edge.
                    withTransform({
                        translate(
                            left = frame / 2f + offsetX - working.width * scale / 2f,
                            top = frame / 2f + offsetY - working.height * scale / 2f
                        )
                        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                    }) {
                        drawImage(
                            image = working,
                            topLeft = Offset.Zero,
                            filterQuality = FilterQuality.Medium
                        )
                    }

                    // Everything the centre will not show is dimmed: the corners outside the circle
                    // and, on a wheel docked to an edge, the half beyond the edge.
                    val whole = Rect(0f, 0f, frame, frame)
                    val scrim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(whole)
                        if (dockEdge != null) {
                            addArc(whole, visibleArcStartAngle(dockEdge), 180f)
                            close()
                        } else {
                            addOval(whole)
                        }
                    }
                    drawPath(scrim, Color.Black.copy(alpha = 0.55f), style = Fill)

                    // Thirds, for composing by eye.
                    val hairline = 1.dp.toPx()
                    for (i in 1..2) {
                        val at = frame * i / 3f
                        drawLine(GRID_COLOR, Offset(at, 0f), Offset(at, frame), hairline)
                        drawLine(GRID_COLOR, Offset(0f, at), Offset(frame, at), hairline)
                    }

                    // What the centre will actually show, and the frame it is cropped from.
                    val guide = Stroke(width = 1.5.dp.toPx())
                    if (dockEdge != null) {
                        // A centre on a screen edge: only the half facing into the screen is ever
                        // seen, and which half depends on the edge the wheel is docked to.
                        drawArc(
                            color = GUIDE_COLOR,
                            startAngle = visibleArcStartAngle(dockEdge),
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset.Zero,
                            size = Size(frame, frame),
                            style = guide
                        )
                        // The edge itself, so the half reads as cut rather than as a shape.
                        if (dockEdge.isSide) {
                            drawLine(
                                color = GUIDE_COLOR,
                                start = Offset(frame / 2f, 0f),
                                end = Offset(frame / 2f, frame),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        } else {
                            drawLine(
                                color = GUIDE_COLOR,
                                start = Offset(0f, frame / 2f),
                                end = Offset(frame, frame / 2f),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    } else {
                        drawCircle(
                            color = GUIDE_COLOR,
                            radius = frame / 2f,
                            center = Offset(frame / 2f, frame / 2f),
                            style = guide
                        )
                    }
                    drawRect(
                        color = GUIDE_COLOR,
                        size = Size(frame, frame),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = OrbitColors.TextSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            IconButton(onClick = { quarterTurns += 1 }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.centre_adjust_rotate),
                    tint = OrbitColors.TextSecondary
                )
            }

            TextButton(enabled = workingReady && !rendering, onClick = {
                val side = framePx
                if (side <= 0f) return@TextButton
                // The framing is handed on twice over: once as the numbers a video needs, in
                // fractions of the frame so they mean the same thing at any size, and once as the
                // rendered square a picture is kept as.
                val framing = CentreTransform(
                    // The *turned* picture's width: scale was solved against `working`, the preview
                    // drew `working`, and everything that reads this number expects the turned width.
                    widthFraction = working.width * scale / side,
                    offsetXFraction = offsetX / side,
                    offsetYFraction = offsetY / side,
                    quarterTurns = quarterTurns
                )
                val chosenScale = scale
                val chosenX = offsetX
                val chosenY = offsetY
                val chosenTurns = quarterTurns
                rendering = true
                scope.launch {
                    val framed = try {
                        withContext(Dispatchers.Default) {
                            renderFramed(
                                image.asAndroidBitmap(), chosenScale, chosenX, chosenY, chosenTurns,
                                side.roundToInt()
                            )
                        }
                    } finally {
                        rendering = false
                    }
                    if (framed != null) onDone(framed, framing)
                }
            }) {
                Text(
                    text = stringResource(R.string.centre_adjust_done),
                    color = OrbitColors.Accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * The picture turned by whole quarters.
 *
 * Taken from the original every time rather than turned again from the last result: turning a
 * bitmap repeatedly loses a little each time, and four presses would show it.
 */
private fun rotateQuarters(image: ImageBitmap, quarterTurns: Int): ImageBitmap {
    val turns = ((quarterTurns % 4) + 4) % 4
    if (turns == 0) return image
    return runCatching {
        val source = image.asAndroidBitmap()
        val matrix = Matrix().apply { postRotate(90f * turns) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            .asImageBitmap()
    }.getOrDefault(image)
}
