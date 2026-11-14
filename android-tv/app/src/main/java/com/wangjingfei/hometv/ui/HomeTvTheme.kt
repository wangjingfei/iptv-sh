package com.wangjingfei.hometv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0E10)
val Panel = Color(0xFF151A1D)
val PanelRaised = Color(0xFF20272B)
val Mint = Color(0xFF62DCA9)
val Warm = Color(0xFFFFC857)
val TextPrimary = Color(0xFFF4F7F5)
val TextMuted = Color(0xFFAEB9B4)
val ErrorRed = Color(0xFFFF6B6B)

private val HomeTvColors = darkColorScheme(
    primary = Mint,
    secondary = Warm,
    background = Ink,
    surface = Panel,
    surfaceVariant = PanelRaised,
    onPrimary = Ink,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    error = ErrorRed,
)

@Composable
fun HomeTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HomeTvColors, content = content)
}
