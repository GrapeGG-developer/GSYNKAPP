package com.gsynk.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ——— Виноградная палитра GSYNK ———
val GrapeViolet = Color(0xFFA855F7)
val GrapeVioletSoft = Color(0xFFC9A8FF)
val GrapeVioletDeep = Color(0xFF6D28D9)
val GrapeGreen = Color(0xFF4ADE80)
val GrapeGreenSoft = Color(0xFF8CF0B4)
val GrapeGreenDeep = Color(0xFF15803D)

// ——— Непрозрачный фон (две палитры для анимации перехода) ———
val BgTopA = Color(0xFF0B0616)
val BgMidA = Color(0xFF170A2A)
val BgBottomA = Color(0xFF08170F)

val BgTopB = Color(0xFF140827)
val BgMidB = Color(0xFF221142)
val BgBottomB = Color(0xFF0A2416)

val GlowViolet = Color(0xFF7C3AED)
val GlowGreen = Color(0xFF22C55E)

// ——— Непрозрачные поверхности (вместо стекла) ———
val CardBg = Color(0xFF1A1030)
val CardBgAlt = Color(0xFF211540)
val CardBorder = Color(0xFF33224A)
val ChipBg = Color(0xFF261838)
val ChipBorder = Color(0xFF3E2C5C)
val TrackBg = Color(0xFF2A1E40)
val DividerColor = Color(0xFF2E2145)

// ——— Текст ———
val TextPrimary = Color(0xFFF7F3FF)
val TextSecondary = Color(0xFFC6B9DD)
val TextDim = Color(0xFF9C8FB5)

// ——— Градиенты ———
val GrapeGradient = Brush.linearGradient(listOf(GrapeViolet, GrapeGreen))
val GrapeGradientSoft = Brush.linearGradient(listOf(GrapeVioletSoft, GrapeGreenSoft))
val GrapeDiagonalSolid = Brush.linearGradient(
    colors = listOf(GrapeVioletDeep, Color(0xFF7C4DE0), GrapeGreenDeep),
    start = Offset(0f, 0f),
    end = Offset(900f, 900f)
)
