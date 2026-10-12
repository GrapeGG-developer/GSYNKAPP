package com.gsynk.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gsynk.app.R
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenDeep
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletDeep
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary

/** Цветовые схемы плиток. */
enum class TileTint(val brush: Brush, val accent: Color) {
    Violet(Brush.linearGradient(listOf(Color(0xFF5B21B6), Color(0xFF7C3AED))), GrapeVioletSoft),
    Green(Brush.linearGradient(listOf(Color(0xFF166534), Color(0xFF22C55E))), GrapeGreenSoft),
    Deep(Brush.linearGradient(listOf(Color(0xFF2A1B4D), Color(0xFF3B2668))), GrapeVioletSoft),
    Dark(Brush.linearGradient(listOf(Color(0xFF1C1430), Color(0xFF241A3E))), TextSecondary),
    Mixed(Brush.linearGradient(listOf(GrapeVioletDeep, GrapeGreenDeep)), Color(0xFFEFE6FF))
}

/**
 * Живая плитка в стиле Metro: нажатие отдаёт лёгкой пружиной,
 * содержимое может обновляться в реальном времени.
 */
@Composable
fun LiveTile(
    title: String,
    subtitle: String,
    tint: TileTint,
    modifier: Modifier = Modifier,
    height: Dp = 104.dp,
    icon: ImageVector? = null,
    badge: String? = null,
    liveText: String? = null,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileScale"
    )

    Box(
        modifier
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(tint.brush)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(14.dp)
    ) {
        // лёгкий «глянец» по диагонали, но непрозрачный фон под ним
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent)
                    )
                )
        )
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = tint.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(badge, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (liveText != null) {
                AnimatedContent(
                    targetState = liveText,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "tileLive"
                ) { text ->
                    Text(
                        text = text,
                        color = Color.White.copy(alpha = 0.94f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Сетка плиток 2×N (внутри LazyColumn — без вложенной прокрутки). */
@Composable
fun TileRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

/** Плитка быстрого действия с индикацией включено/выключено. */
@Composable
fun ToggleTile(
    title: String,
    on: Boolean,
    iconRes: Int,
    onText: String,
    offText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiveTile(
        title = title,
        subtitle = if (on) onText else offText,
        tint = if (on) TileTint.Mixed else TileTint.Dark,
        icon = ImageVector.vectorResource(iconRes),
        badge = if (on) "вкл" else null,
        liveText = if (on) "Включено" else "Выключено",
        modifier = modifier,
        onClick = onClick
    )
}

/** Плитка с иконкой и названием (навигация в раздел). */
@Composable
fun NavTile(
    title: String,
    subtitle: String,
    iconRes: Int,
    tint: TileTint,
    modifier: Modifier = Modifier,
    badge: String? = null,
    liveText: String? = null,
    onClick: () -> Unit
) {
    LiveTile(
        title = title,
        subtitle = subtitle,
        tint = tint,
        icon = ImageVector.vectorResource(iconRes),
        badge = badge,
        liveText = liveText,
        modifier = modifier,
        onClick = onClick
    )
}

/** Плитка «Автопилот» с живым отсчётом до следующего правила. */
@Composable
fun AutopilotTile(
    enabled: Boolean,
    countdown: String,
    rulesCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiveTile(
        title = "Автопилот",
        subtitle = if (enabled) "правил: $rulesCount" else "выключен — нажмите",
        tint = if (enabled) TileTint.Green else TileTint.Dark,
        icon = ImageVector.vectorResource(R.drawable.ic_bolt),
        badge = if (enabled) "работает" else null,
        liveText = if (enabled) countdown else "Пауза",
        modifier = modifier,
        height = 118.dp,
        onClick = onClick
    )
}

/** Плитка статуса устройства (MAIN-режим). */
@Composable
fun DeviceTile(
    model: String,
    tierSummary: String,
    isFull: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiveTile(
        title = model,
        subtitle = if (isFull) "полный набор функций" else "базовый набор функций",
        tint = if (isFull) TileTint.Violet else TileTint.Dark,
        icon = ImageVector.vectorResource(R.drawable.ic_shield),
        liveText = tierSummary,
        modifier = modifier,
        onClick = onClick
    )
}

/** Плитка «Правило расписания» — открывает редактор. */
@Composable
fun AddRuleTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .border(1.dp, ChipBorder, RoundedCornerShape(22.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", color = GrapeVioletSoft, style = MaterialTheme.typography.headlineMedium)
            Text("Новое правило", color = TextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}
