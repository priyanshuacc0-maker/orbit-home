package com.orbit.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.orbit.home.R
import kotlinx.coroutines.launch

private val CardColour = Color(0xFFEAE9FC)
private val PanelColour = Color(0xFFE5E4F9)
private val KeyColour = Color(0xFFECEBFD)
private val OpColour = Color(0xFFE1E0F8)
private val EqualsColour = Color(0xFF524F85)
private val BackColour = Color(0xFFFFD6D3)
private val InkStrong = Color(0xFF3F3D6B)
private val InkSoft = Color(0xFF6D6B9A)
private val InkFaint = Color(0xFF8E8CB0)
private val KeyShadow = Color(0xFFB4B2DE)

private val KEY_HEIGHT = 56.dp
private val KEY_GAP = 8.dp

/**
 * The calculator that opens from the clock bar: a lavender card over a dimmed home screen that eases in
 * and out.
 *
 * Closing plays the exit before telling the caller, otherwise the card would simply vanish.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CalculatorDialog(onDismiss: () -> Unit) {
    var state by remember { mutableStateOf(CalcState()) }
    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) { progress.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(180, easing = FastOutLinearInEasing))
            onDismiss()
        }
    }

    fun press(key: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        state = Calc.press(state, key)
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // The card draws its own, softer scrim, so the window's own dim is turned off.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * progress.value))
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
            contentAlignment = Alignment.Center
        ) {
            val shape = RoundedCornerShape(36.dp)
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp)
                    .graphicsLayer {
                        val p = progress.value
                        alpha = p
                        scaleX = 0.88f + 0.12f * p
                        scaleY = 0.88f + 0.12f * p
                        translationY = (1f - p) * 48.dp.toPx()
                    }
                    .shadow(20.dp, shape, ambientColor = Color(0x66000000), spotColor = Color(0x66000000))
                    .clip(shape)
                    .background(CardColour)
                    // Taps on the card are the card's: they must not fall through and close it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(22.dp)
            ) {
                Display(state)
                Spacer(Modifier.height(16.dp))
                Keypad(onKey = { press(it) }, onClear = { press("C") })
            }
        }
    }
}

@Composable
private fun Display(state: CalcState) {
    val top = Calc.display(state.previous ?: state.expr)
    val result = if (state.expr.isEmpty()) "0" else Calc.preview(state.expr) ?: ""
    val showEquals = state.expr.isNotEmpty() && !state.expr.matches(Regex("[0-9.]+"))
    val panel = RoundedCornerShape(26.dp)
    val scroll = rememberScrollState()
    LaunchedEffect(top) { scroll.scrollTo(scroll.maxValue) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(panel)
            .background(PanelColour)
            .border(1.dp, Color.White.copy(alpha = 0.55f), panel)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = top,
                color = InkFaint,
                fontSize = 20.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.horizontalScroll(scroll)
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showEquals) {
                Text(text = "=", color = InkSoft, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                text = result,
                color = InkStrong,
                fontSize = when {
                    result.length <= 9 -> 44.sp
                    result.length <= 12 -> 36.sp
                    else -> 28.sp
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private enum class KeyKind { DIGIT, OP, FN }

@Composable
private fun Keypad(onKey: (String) -> Unit, onClear: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val colW = (maxWidth - KEY_GAP * 3) / 4

        @Composable
        fun K(key: String, kind: KeyKind, label: String = key, width: Dp = colW) {
            CalcKey(
                label = label,
                kind = kind,
                modifier = Modifier.width(width).height(KEY_HEIGHT),
                onClick = { onKey(key) }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                K("(", KeyKind.FN); K(")", KeyKind.FN)
                K("\u221A", KeyKind.FN); K("\u00B2", KeyKind.FN, label = "x\u00B2")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                K("%", KeyKind.FN); K("\u00F7", KeyKind.FN); K("\u00D7", KeyKind.FN)
                CalcKey(
                    label = null,
                    kind = KeyKind.FN,
                    modifier = Modifier.width(colW).height(KEY_HEIGHT),
                    container = BackColour,
                    description = stringResource(R.string.calc_backspace),
                    onClick = { onKey("\u232B") },
                    onLongClick = onClear
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                K("7", KeyKind.DIGIT); K("8", KeyKind.DIGIT); K("9", KeyKind.DIGIT)
                K("\u2212", KeyKind.OP)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                K("4", KeyKind.DIGIT); K("5", KeyKind.DIGIT); K("6", KeyKind.DIGIT)
                K("+", KeyKind.OP)
            }
            // The last two rows share the tall equals key on the right.
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                        K("1", KeyKind.DIGIT); K("2", KeyKind.DIGIT); K("3", KeyKind.DIGIT)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                        K("0", KeyKind.DIGIT, width = colW * 2 + KEY_GAP)
                        K(".", KeyKind.DIGIT)
                    }
                }
                CalcKey(
                    label = "=",
                    kind = KeyKind.OP,
                    modifier = Modifier.width(colW).height(KEY_HEIGHT * 2 + KEY_GAP),
                    container = EqualsColour,
                    ink = Color.White,
                    description = stringResource(R.string.calc_equals),
                    onClick = { onKey("=") }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalcKey(
    label: String?,
    kind: KeyKind,
    modifier: Modifier,
    onClick: () -> Unit,
    container: Color = when (kind) {
        KeyKind.DIGIT -> KeyColour
        else -> OpColour
    },
    ink: Color = if (kind == KeyKind.DIGIT) InkSoft else InkStrong,
    description: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(90), label = "keyScale")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(3.dp, shape, ambientColor = KeyShadow, spotColor = KeyShadow)
            .clip(shape)
            .background(container)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (label == null) {
            Image(
                painter = painterResource(R.drawable.ic_calc_backspace),
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
        } else {
            Text(
                text = label,
                color = ink,
                fontSize = if (kind == KeyKind.DIGIT) 30.sp else 26.sp,
                fontWeight = if (kind == KeyKind.DIGIT) FontWeight.Normal else FontWeight.Medium
            )
        }
    }
}
