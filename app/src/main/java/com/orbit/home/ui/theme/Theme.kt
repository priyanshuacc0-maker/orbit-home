package com.orbit.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Orbit's restrained dark palette. */
object OrbitColors {
    val Background = Color(0xFF050505)
    val Surface = Color(0xFF151519)
    val SurfaceHigh = Color(0xFF1E1E24)
    val Guide = Color(0xFF292930)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA1A1AA)
    val Accent = Color(0xFF7C5CFF)
    /** Destructive actions, such as uninstall. */
    val Danger = Color(0xFFD64545)
}

private val OrbitColorScheme = darkColorScheme(
    primary = OrbitColors.Accent,
    onPrimary = OrbitColors.TextPrimary,
    background = OrbitColors.Background,
    onBackground = OrbitColors.TextPrimary,
    surface = OrbitColors.Background,
    onSurface = OrbitColors.TextPrimary,
    surfaceVariant = OrbitColors.Surface,
    onSurfaceVariant = OrbitColors.TextSecondary,
    secondary = OrbitColors.TextSecondary,
    onSecondary = OrbitColors.Background,
    outline = OrbitColors.Guide
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OrbitColorScheme, content = content)
}
