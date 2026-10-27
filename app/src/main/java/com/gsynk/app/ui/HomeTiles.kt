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
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GioBrush

/**
 * Живые плитки в духе Gioui.
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

    val tileContext = LocalContext.current
    val tweakRevision by UiTweaks.revision.collectAsStateWithLifecycle()
    val scaledHeight = if (tweakRevision >= 0) (height.value * UiTweaks.titleScale).dp else height

    Box(
        modifier
            .height(scaledHeight)
            .scale(scale)
            .clip(skinShape("card"))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.14f), skinShape("card"))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Отклик по настройкам: вибрация и/или щелчок (в «Кастомизации»).
                UiTweaks.tap(tileContext)
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
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(skinShape("card"))
                            .background(Color.Black.copy(alpha = 0.22f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (liveText != null && UiTweaks.tileCounters) {
                AnimatedContent(
                    targetState = liveText,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "tileLive"
                ) { text ->
                    Text(
                        text = text,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
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
        brush = if (on) GioBrush.Green else GioBrush.Ink,
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
        brush = if (enabled) GioBrush.Violet else GioBrush.Ink,
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
        brush = if (isFull) GioBrush.Cyan else GioBrush.Ink,
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
            .clip(skinShape("card"))
            .background(CardBg)
            .border(1.dp, CardBorder, skinShape("card"))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", color = com.gsynk.app.ui.theme.GrapeVioletSoft, style = MaterialTheme.typography.headlineSmall.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light))
            Text("Новое правило", color = com.gsynk.app.ui.theme.TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
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
            .clip(skinShape("card"))
            .background(CardBg)
            .border(1.dp, CardBorder, skinShape("card"))
            .padding(16.dp),
        content = content
    )
}

// ————————————————————————————————————————————————
//  Карточки GIOUI переменного размера:
//  плоский цвет, подпись снизу слева, живой оборот обратной стороны.
// ————————————————————————————————————————————————

/** Размер плитки по сетке GIOUI: 1×1, 2×2 и во всю ширину. */
enum class TileSize { Small, Medium, Wide }

/**
 * Живая плитка Gioui.
 *
 * Лицевая сторона — иконка и подпись снизу слева (подписи строчными, как в WP).
 * Каждые несколько секунд плитка переворачивается вокруг вертикальной оси и
 * показывает обратную сторону с живым значением: это и есть «live tile».
 */
@Composable
fun GioHomeCard(
    title: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    iconRes: Int? = null,
    badge: String? = null,
    liveText: String? = null,
    liveLabel: String? = null,
    size: TileSize = TileSize.Medium,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "giouiPress"
    )
    val height = when (size) {
        TileSize.Small -> 60.dp
        TileSize.Medium -> 132.dp
        TileSize.Wide -> 96.dp
    }
    val iconSize = when (size) {
        TileSize.Small -> 17.dp
        TileSize.Medium -> 26.dp
        TileSize.Wide -> 22.dp
    }

    // оборот: 9 секунд лицом, затем 3,5 секунды обратной стороной
    val flip = rememberInfiniteTransition(label = "flip")
    val angle by flip.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 13_000
                0f at 0
                0f at 8_600
                180f at 9_100
                180f at 12_100
                360f at 12_600
                360f at 13_000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "flipAngle"
    )
    val showBack = liveText != null && angle > 90f && angle < 270f

    val tileContext = LocalContext.current
    val tweakRevision by UiTweaks.revision.collectAsStateWithLifecycle()

    Box(
        modifier
            .height(height)
            .scale(press)
            .graphicsLayer {
                rotationY = if (liveText == null) 0f else angle
                cameraDistance = 24f * density
            }
            .clip(skinShape("card"))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.12f), skinShape("card"))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Отклик по настройкам: вибрация и/или щелчок (в «Кастомизации»).
                UiTweaks.tap(tileContext)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        if (!showBack) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(if (size == TileSize.Small) 8.dp else 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconRes != null) {
                        Icon(
                            imageVector = ImageVector.vectorResource(iconRes),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    if (badge != null && size != TileSize.Small) {
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .background(Color.Black.copy(alpha = 0.28f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(badge, color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = title.lowercase(),
                    color = Color.White,
                    style = if (size == TileSize.Small) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Light,
                    maxLines = 2
                )
                if (subtitle.isNotEmpty() && size != TileSize.Small) {
                    Text(
                        text = subtitle.lowercase(),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal),
                        maxLines = 1
                    )
                }
            }
        } else {
            // обратная сторона: крупное живое значение
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .padding(12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (liveLabel != null) {
                    Text(
                        text = liveLabel.lowercase(),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                    )
                }
                Text(
                    text = liveText.orEmpty(),
                    color = Color.White,
                    style = if (size == TileSize.Small) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Light,
                    maxLines = 2
                )
            }
        }
    }
}

/** Плитка автопилота в стиле GIOUI: 2×2, обратная сторона — отсчёт до правила. */
@Composable
fun GioAutopilotCard(
    enabled: Boolean,
    countdown: String,
    rulesCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GioHomeCard(
        title = "автопилот",
        subtitle = if (enabled) "правил: $rulesCount" else "выключен",
        brush = if (enabled) GioBrush.Violet else GioBrush.Ink,
        iconRes = com.gsynk.app.R.drawable.ic_bolt,
        badge = if (enabled) "вкл" else null,
        liveText = if (enabled) countdown else "пауза",
        liveLabel = "следующее правило",
        size = TileSize.Medium,
        modifier = modifier,
        onClick = onClick
    )
}
