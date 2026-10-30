package com.music.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MusicColors = darkColorScheme(
    primary = Color(0xFFC5F66A),
    onPrimary = Color(0xFF18210B),
    secondary = Color(0xFFB5A5FF),
    onSecondary = Color(0xFF211B39),
    background = Color(0xFF0A0B0F),
    onBackground = Color(0xFFF4F4F2),
    surface = Color(0xFF14161D),
    onSurface = Color(0xFFF4F4F2),
    surfaceVariant = Color(0xFF20232B),
    onSurfaceVariant = Color(0xFFB5B8C2),
    outline = Color(0xFF343843),
    error = Color(0xFFFF7777)
)

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusicColors,
        content = content
    )
}
