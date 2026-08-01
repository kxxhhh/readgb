package com.dutongjian.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF28231F)
private val Paper = Color(0xFFF8F5F0)
private val Jade = Color(0xFF35645B)
private val Vermilion = Color(0xFFB34B32)

private val LightColors = lightColorScheme(
    primary = Jade,
    onPrimary = Color.White,
    secondary = Vermilion,
    background = Paper,
    surface = Color(0xFFFFFBF7),
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD4C5),
    secondary = Color(0xFFFFB59E),
    background = Color(0xFF1B1B18),
    surface = Color(0xFF262521),
)

@Composable
fun DutongjianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
