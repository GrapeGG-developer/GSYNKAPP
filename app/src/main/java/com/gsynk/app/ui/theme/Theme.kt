package com.gsynk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val GsynkColors = darkColorScheme(
    primary = GrapeViolet,
    onPrimary = Color(0xFF160822),
    primaryContainer = GrapeVioletDeep,
    onPrimaryContainer = Color(0xFFF3EAFF),
    secondary = GrapeGreen,
    onSecondary = Color(0xFF06230F),
    secondaryContainer = GrapeGreenDeep,
    onSecondaryContainer = Color(0xFFE4FFEC),
    background = BgTopA,
    onBackground = TextPrimary,
    surface = Color(0xFF160C26),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1F1236),
    onSurfaceVariant = TextSecondary,
    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x1FFFFFFF),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF2A0510)
)

private val GsynkTypography = Typography().let { base ->
    base.copy(
        displaySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Light,
            fontSize = 44.sp,
            letterSpacing = (-1).sp
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp
        ),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyMedium = base.bodyMedium.copy(letterSpacing = 0.15.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp)
    )
}

@Composable
fun GSYNKTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GsynkColors,
        typography = GsynkTypography,
        content = content
    )
}
