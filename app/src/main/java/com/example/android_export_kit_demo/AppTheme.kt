package com.example.android_export_kit_demo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────
// Brand Colors (mirrors Flutter AppTheme)
// ─────────────────────────────────────────────

object AppColors {
    val Primary      = Color(0xFF6C5CE7)
    val PrimaryLight = Color(0xFF9B8FF0)
    val Accent       = Color(0xFFFD79A8)
    val Surface      = Color(0xFFF8F9FA)
    val SurfaceDark  = Color(0xFF1E1E2E)
    val CardDark     = Color(0xFF2D2D3F)
}

// ─────────────────────────────────────────────
// Color Schemes
// ─────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    secondary = AppColors.Accent,
    surface = AppColors.Surface,
    background = AppColors.Surface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurface = Color(0xFF1C1B1F),
    onBackground = Color(0xFF1C1B1F)
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.PrimaryLight,
    secondary = AppColors.Accent,
    surface = AppColors.SurfaceDark,
    background = AppColors.SurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurface = Color.White,
    onBackground = Color.White
)

// ─────────────────────────────────────────────
// App Theme Composable
// ─────────────────────────────────────────────

@Composable
fun FrameEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
