package com.porteiro.server.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF0B6B4F)
private val GreenDark = Color(0xFF06402F)
private val Amber = Color(0xFFF2A900)
private val Surface = Color(0xFFF6F8F5)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDD),
    onPrimaryContainer = GreenDark,
    secondary = Amber,
    onSecondary = Color(0xFF2A1D00),
    background = Surface,
    onBackground = Color(0xFF15201B),
    surface = Color.White,
    onSurface = Color(0xFF15201B),
    surfaceVariant = Color(0xFFE3E9E4),
    onSurfaceVariant = Color(0xFF444C47),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD6B1),
    onPrimary = GreenDark,
    primaryContainer = GreenDark,
    onPrimaryContainer = Color(0xFFCDEBDD),
    secondary = Amber,
    onSecondary = Color(0xFF2A1D00),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE2E9E4),
    surface = Color(0xFF16201B),
    onSurface = Color(0xFFE2E9E4),
    surfaceVariant = Color(0xFF2B3630),
    onSurfaceVariant = Color(0xFFC0C9C3)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun PorteiroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
