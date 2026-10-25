package com.orbit.home.ui

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.TransformOrigin
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

/** Every colour the calculator draws with, so a theme is one value and switching is a blend between two. */
private class CalcPalette(
    val card: Color, val cardBorder: Color,
    val panel: Color, val panelBorder: Color,
    val digit: Color, val op: Color, val equalsKey: Color, val equalsInk: Color, val back: Color,
    val inkDigit: Color, val inkOp: Color, val result: Color, val inkSoft: Color, val inkFaint: Color,
    val keyShadow: Color, val keyBorder: Color,
    val switchTrack: Color, val switchBorder: Color, val switchThumb: Color,
    val switchActive: Color, val switchInactive: Color
)

private val LightPalette = CalcPalette(
    card = Color(0xFFEAE9FC), cardBorder = Color(0x99FFFFFF),
    panel = Color(0xFFE5E4F9), panelBorder = Color(0x8CFFFFFF),
    digit = Color(0xFFECEBFD), op = Color(0xFFE1E0F8), equalsKey = Color(0xFF524F85),
    equalsInk = Color.White, back = Color(0xFFFFD6D3),
    inkDigit = Color(0xFF6D6B9A), inkOp = Color(0xFF3F3D6B), result = Color(0xFF3F3D6B),
    inkSoft = Color(0xFF6D6B9A), inkFaint = Color(0xFF8E8CB0),
    keyShadow = Color(0xFFB4B2DE), keyBorder = Color(0x00FFFFFF),
    switchTrack = Color(0xFFE1E0F8), switchBorder = Color(0xFFCFCDEE), switchThumb = Color.White,
    switchActive = Color(0xFF524F85), switchInactive = Color(0xFFA9A7CE)
)

private val DarkPalette = CalcPalette(
    card = Color(0xFF171828), cardBorder = Color(0xFF33344F),
    panel = Color(0xFF0F1019), panelBorder = Color(0xFF383A57),
    digit = Color(0xFF272839), op = Color(0xFF262738), equalsKey = Color(0xFF6C5CD1),
    equalsInk = Color.White, back = Color(0xFF5B2A38),
    inkDigit = Color(0xFFD8D3FF), inkOp = Color(0xFFE9E6FF), result = Color(0xFFB7A9FF),
    inkSoft = Color(0xFF9C94E0), inkFaint = Color(0xFF7D7BA6),
    keyShadow = Color(0x99000000), keyBorder = Color(0x14FFFFFF),
    switchTrack = Color(0xFF22233A), switchBorder = Color(0xFF3D3E5E), switchThumb = Color(0xFF3A3B5A),
    switchActive = Color(0xFFA99BFF), switchInactive = Color(0xFF6E6C96)
)

private fun lerpPalette(a: CalcPalette, b: CalcPalette, t: Float) = CalcPalette(
    lerp(a.card, b.card, t), lerp(a.cardBorder, b.cardBorder, t),
    lerp(a.panel, b.panel, t), lerp(a.panelBorder, b.panelBorder, t),
    lerp(a.digit, b.digit, t), lerp(a.op, b.op, t), lerp(a.equalsKey, b.equalsKey, t),
    lerp(a.equalsInk, b.equalsInk, t), lerp(a.back, b.back, t),
    lerp(a.inkDigit, b.inkDigit, t), lerp(a.inkOp, b.inkOp, t), lerp(a.result, b.result, t),
    lerp(a.inkSoft, b.inkSoft, t), lerp(a.inkFaint, b.inkFaint, t),
    lerp(a.keyShadow, b.keyShadow, t), lerp(a.keyBorder, b.keyBorder, t),
    lerp(a.switchTrack, b.switchTrack, t), lerp(a.switchBorder, b.switchBorder, t),
    lerp(a.switchThumb, b.switchThumb, t), lerp(a.switchActive, b.switchActive, t),
    lerp(a.switchInactive, b.switchInactive, t)
)

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
internal fun CalculatorDialog(
    /** The saved theme: true for dark. */
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // Held here as well as saved, so the switch answers at once instead of after the save comes back.
    var isDark by remember { mutableStateOf(dark) }
    LaunchedEffect(dark) { isDark = dark }
    val blend by animateFloatAsState(if (isDark) 1f else 0f, tween(320), label = "calcTheme")
    val pal = lerpPalette(LightPalette, DarkPalette, blend)
    var state by remember { mutableStateOf(CalcState()) }
    var closing by remember { mutableStateOf(false) }
    val progress = rememberPopProgress()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    
    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            progress.popOut()
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
                // No dimming: the home screen stays as it is, and a tap on it just closes the card.
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
            contentAlignment = Alignment.Center
        ) {
            val shape = RoundedCornerShape(36.dp)
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp)
                    .popTransform(
                        progress = { progress.value },
                        origin = TransformOrigin.Center,
                        from = 0.9f,
                        rise = 28.dp
                    )
                    .clip(shape)
                    .background(pal.card)
                    .border(1.dp, pal.cardBorder, shape)
                    // Taps on the card are the card's: they must not fall through and close it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(22.dp)
            ) {
                ThemeSwitch(
                    dark = isDark,
                    pal = pal,
                    onToggle = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isDark = !isDark
                        onDarkChange(isDark)
                    }
                )
                Spacer(Modifier.height(12.dp))
                Display(state, pal)
                Spacer(Modifier.height(16.dp))
                Keypad(pal = pal, onKey = { press(it) }, onClear = { press("C") })
            }
        }
    }
}

@Composable
private fun Display(state: CalcState, pal: CalcPalette) {
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
            .background(pal.panel)
            .border(1.dp, pal.panelBorder, panel)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = top,
                color = pal.inkFaint,
                fontSize = 20.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.horizontalScroll(scroll)
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showEquals) {
                Text(text = "=", color = pal.inkSoft, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                text = result,
                color = pal.result,
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
private fun Keypad(pal: CalcPalette, onKey: (String) -> Unit, onClear: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val colW = (maxWidth - KEY_GAP * 3) / 4

        @Composable
        fun K(key: String, kind: KeyKind, label: String = key, width: Dp = colW) {
            CalcKey(
                pal = pal,
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
                    pal = pal,
                    label = null,
                    kind = KeyKind.FN,
                    modifier = Modifier.width(colW).height(KEY_HEIGHT),
                    container = pal.back,
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
                    pal = pal,
                    label = "=",
                    kind = KeyKind.OP,
                    modifier = Modifier.width(colW).height(KEY_HEIGHT * 2 + KEY_GAP),
                    container = pal.equalsKey,
                    ink = pal.equalsInk,
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
    pal: CalcPalette,
    label: String?,
    kind: KeyKind,
    modifier: Modifier,
    onClick: () -> Unit,
    container: Color = when (kind) {
        KeyKind.DIGIT -> pal.digit
        else -> pal.op
    },
    ink: Color = if (kind == KeyKind.DIGIT) pal.inkDigit else pal.inkOp,
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
            .shadow(3.dp, shape, ambientColor = pal.keyShadow, spotColor = pal.keyShadow)
            .clip(shape)
            .background(container)
            .border(1.dp, pal.keyBorder, shape)
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

/**
 * The dark/light switch: a moon and a sun on one track, with a thumb that slides under whichever is on.
 */
@Composable
private fun ThemeSwitch(dark: Boolean, pal: CalcPalette, onToggle: () -> Unit) {
    val thumbX by animateDpAsState(
        targetValue = if (dark) 0.dp else 34.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "calcThumb"
    )
    val moonTint by animateColorAsState(if (dark) pal.switchActive else pal.switchInactive, label = "moon")
    val sunTint by animateColorAsState(if (dark) pal.switchInactive else pal.switchActive, label = "sun")
    val shape = RoundedCornerShape(50)
    val label = stringResource(R.string.calc_theme_switch)
    val stateText = stringResource(if (dark) R.string.calc_theme_dark else R.string.calc_theme_light)
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(38.dp)
            .clip(shape)
            .background(pal.switchTrack)
            .border(1.dp, pal.switchBorder, shape)
            .semantics {
                contentDescription = label
                stateDescription = stateText
            }
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .size(32.dp)
                .clip(shape)
                .background(pal.switchThumb)
        )
        Row {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_calc_moon),
                    contentDescription = null,
                    tint = moonTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(2.dp))
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_calc_sun),
                    contentDescription = null,
                    tint = sunTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
