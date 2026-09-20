package com.sb.attendance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E93),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Color(0xFF0A1F33),
    secondary = Color(0xFF4A6072),
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC2EC),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF1B5E93),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFB2C8DA),
    background = Color(0xFF101418),
    surface = Color(0xFF181C20),
    error = Color(0xFFF2B8B5)
)

@Composable
fun AttendanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
