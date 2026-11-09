package com.gsynk.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ————————————————————————————————————————————————
//  Палитра GIOUI (5.0): один тёмный виноградный мир на всё приложение.
//  Глубокий чернильный фон, стеклянные карточки одного тона и акценты
//  фиолет/циан/мята — единый стиль без остатков прошлых оболочек.
// ————————————————————————————————————————————————

val GrapeViolet = Color(0xFF8B5CF6)
val GrapeVioletSoft = Color(0xFFC4B5FD)
val GrapeVioletDeep = Color(0xFF5B21B6)

val GrapeGreen = Color(0xFF34D399)
val GrapeGreenSoft = Color(0xFF86EFAC)
val GrapeGreenDeep = Color(0xFF047857)

val GrapeCyan = Color(0xFF22D3EE)
val GrapePink = Color(0xFFF472B6)
val GrapeAmber = Color(0xFFFBBF24)
val GrapeRed = Color(0xFFF87171)
val GrapeBlue = Color(0xFF60A5FA)

// Фон приложения
val BgTopA = Color(0xFF070713)
val BgMidA = Color(0xFF0D0A1E)
val BgBottomA = Color(0xFF081410)

val BgTopB = Color(0xFF0A0716)
val BgMidB = Color(0xFF150E28)
val BgBottomB = Color(0xFF0A1A16)

val GlowViolet = Color(0xFF7C3AED)
val GlowGreen = Color(0xFF22C55E)

// Поверхности
val CardBg = Color(0x7A151327) // полупрозрачная: живой фон виден сквозь карточки
val CardBgAlt = Color(0x941E1A38)
val CardBorder = Color(0x99463D73)
val ChipBg = Color(0x701E1B33)
val ChipBorder = Color(0x994A4070)
val TrackBg = Color(0x66221E3A)
val DividerColor = Color(0xFF262140)

// Текст
val TextPrimary = Color(0xFFF6F4FF)
val TextSecondary = Color(0xFFC3BADB)
val TextDim = Color(0xFF8E86A8)

// Градиенты интерфейса
val GrapeGradient = Brush.linearGradient(listOf(GrapeViolet, GrapeCyan))
val GrapeGradientSoft = Brush.linearGradient(listOf(GrapeVioletSoft, GrapeGreenSoft))
val GrapeDiagonalSolid = Brush.linearGradient(
    listOf(Color(0xFF1A1230), Color(0xFF12101F))
)

/** Градиенты карточек GIOUI: один набор на все акценты главного экрана. */
object GioBrush {
    val Violet = Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFA855F7)))
    val Cyan = Brush.linearGradient(listOf(Color(0xFF0E7490), Color(0xFF22D3EE), Color(0xFF67E8F9)))
    val Green = Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF10B981), Color(0xFF34D399)))
    val Amber = Brush.linearGradient(listOf(Color(0xFF92400E), Color(0xFFF59E0B), Color(0xFFFBBF24)))
    val Pink = Brush.linearGradient(listOf(Color(0xFF9D174D), Color(0xFFEC4899), Color(0xFFF472B6)))
    val Blue = Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF60A5FA)))
    val Red = Brush.linearGradient(listOf(Color(0xFF7F1D1D), Color(0xFFDC2626), Color(0xFFF87171)))
    val Ink = Brush.linearGradient(listOf(Color(0xFF16132A), Color(0xFF221C3C)))
}
