package com.gsynk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsynk.app.R

/**
 * Единый шрифт GIOUI — Inter с полноценной кириллицей.
 *
 * Характер у типографики новый: заголовки компактные и уверенные (Medium/SemiBold,
 * без гигантских «плакатов»), текст спокойный Regular, подписи Medium с лёгкой
 * разрядкой. Шкала совпадает с Gioui.Type, чтобы все экраны звучали одним голосом.
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
        // заголовки GIOUI — компактные и уверенные
        displaySmall = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.8).sp
        ),
        displayMedium = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = (-1.2).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = (-0.6).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 24.sp, letterSpacing = (-0.4).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = (-0.2).sp
        ),
        titleLarge = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 0.sp
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
            fontFamily = GsynkFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp
        ),
        labelLarge = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp
        ),
        labelMedium = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = GsynkFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp
        )
    )
}

@Composable
fun GSYNKTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GsynkColors,
        typography = GsynkTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}
