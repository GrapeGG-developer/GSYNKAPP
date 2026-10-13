package com.gsynk.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
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
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.TileBrush

/**
 * Живые плитки в духе Metro.
 *
 * Плитка «дышит»: по фону медленно едет градиент, содержимое (цифры, подписи)
 * меняется с анимацией, нажатие отдаёт пружиной и вибрацией. Именно это отличает
 * живую плитку от обычной кнопки.
 */
@Composable
fun LiveTile(
    title: String,
    subtitle: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    height: Dp = 106.dp,
    iconRes: Int? = null,
    badge: String? = null,
    liveText: String? = null,
    accent: Color = Color.White,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileScale"
    )

    // медленно плывущий градиент — «живой» фон плитки
    val drift = rememberInfiniteTransition(label = "drift")
    val shift by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift"
    )

    Box(
        modifier
            .height(height)
            .scale(scale)
            .clip(RectangleShape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        // движущийся блик
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f * (1f - shift)),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f * shift)
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(1f + shift * 400f, 900f)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Icon(
                        imageVector = ImageVector.vectorResource(iconRes),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(RectangleShape)
                            .background(Color.Black.copy(alpha = 0.22f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (liveText != null) {
                AnimatedContent(
                    targetState = liveText,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "tileLive"
                ) { text ->
                    Text(
                        text = text,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Ряд плиток: дети получают weight(1f) автоматически. */
@Composable
fun TileRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        content = content
    )
}

/** Плитка-переключатель: цвет меняется вместе с состоянием. */
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
        brush = if (on) TileBrush.Green else TileBrush.Ink,
        iconRes = iconRes,
        badge = if (on) "вкл" else null,
        liveText = if (on) "Активно" else "Выключено",
        modifier = modifier,
        onClick = onClick
    )
}

/** Плитка перехода в раздел. */
@Composable
fun NavTile(
    title: String,
    subtitle: String,
    iconRes: Int,
    brush: Brush,
    modifier: Modifier = Modifier,
    badge: String? = null,
    liveText: String? = null,
    onClick: () -> Unit
) {
    LiveTile(
        title = title,
        subtitle = subtitle,
        brush = brush,
        iconRes = iconRes,
        badge = badge,
        liveText = liveText,
        modifier = modifier,
        onClick = onClick
    )
}

/** Плитка автопилота с живым отсчётом до следующего правила. */
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
        brush = if (enabled) TileBrush.Violet else TileBrush.Ink,
        iconRes = com.gsynk.app.R.drawable.ic_bolt,
        badge = if (enabled) "работает" else null,
        liveText = if (enabled) countdown else "Пауза",
        modifier = modifier,
        height = 118.dp,
        onClick = onClick
    )
}

/** Плитка устройства: просто показывает профиль, без ручных переключателей. */
@Composable
fun DeviceTile(
    model: String,
    tierLabel: String,
    isFull: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiveTile(
        title = model.take(18),
        subtitle = tierLabel,
        brush = if (isFull) TileBrush.Cyan else TileBrush.Ink,
        iconRes = com.gsynk.app.R.drawable.ic_shield,
        liveText = if (isFull) "Все функции" else "Базовый набор",
        modifier = modifier,
        onClick = onClick
    )
}

/** Плитка «новое правило» — пунктирная, в стиле остальных плиток. */
@Composable
fun AddRuleTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(96.dp)
            .clip(RectangleShape)
            .background(CardBg)
            .border(1.dp, CardBorder, RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", color = com.gsynk.app.ui.theme.GrapeVioletSoft, style = MaterialTheme.typography.headlineSmall)
            Text("Новое правило", color = com.gsynk.app.ui.theme.TextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Общая «плитка-контейнер» для экранов: без клика, просто фон. */
@Composable
fun TilePanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(CardBg)
            .border(1.dp, CardBorder, RectangleShape)
            .padding(16.dp),
        content = content
    )
}
