package com.gsynk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gsynk.app.R

/**
 * Единый шрифт приложения — Inter с тонкими начертаниями.
 *
 * Это свободный аналог Segoe UI: тот же гуманистический гротеск, та же плотность
 * букв, но с полноценной кириллицей и открытой лицензией. Крупные заголовки идут
 * в Thin (как Segoe UI Thin), подписи — Light, текст — Regular/Medium.
 */
val GsynkFont = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

private val GsynkColors = darkColorScheme(
    primary = GrapeViolet,
    onPrimary = Color(0xFF12081F),
    primaryContainer = GrapeVioletDeep,
    onPrimaryContainer = Color(0xFFF3EAFF),
    secondary = GrapeGreen,
    onSecondary = Color(0xFF04231A),
    secondaryContainer = GrapeGreenDeep,
    onSecondaryContainer = Color(0xFFE4FFF5),
    background = BgTopA,
    onBackground = TextPrimary,
    surface = CardBg,
    onSurface = TextPrimary,
    surfaceVariant = CardBgAlt,
    onSurfaceVariant = TextSecondary,
    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x1FFFFFFF),
    error = GrapeRed,
    onError = Color(0xFF2A0505)
)

private val GsynkTypography = Typography().let { base ->
    base.copy(
        // крупные заголовки — тонкие, как в Segoe UI Thin
        displaySmall = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Thin, fontSize = 42.sp, letterSpacing = (-0.8).sp
        ),
        displayMedium = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Thin, fontSize = 54.sp, letterSpacing = (-1.2).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Thin, fontSize = 34.sp, letterSpacing = (-0.6).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Light, fontSize = 27.sp, letterSpacing = (-0.4).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Light, fontSize = 22.sp, letterSpacing = (-0.2).sp
        ),
        titleLarge = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Light, fontSize = 19.sp, letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, letterSpacing = 0.1.sp
        ),
        titleSmall = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, letterSpacing = 0.2.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp
        ),
        bodySmall = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Light, fontSize = 12.sp, lineHeight = 16.sp
        ),
        labelLarge = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp
        ),
        labelMedium = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Light, fontSize = 10.5.sp, letterSpacing = 0.6.sp
        )
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
