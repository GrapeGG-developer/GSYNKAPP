package com.gsynk.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.ui.theme.BgBottomA
import com.gsynk.app.ui.theme.BgBottomB
import com.gsynk.app.ui.theme.BgMidA
import com.gsynk.app.ui.theme.BgMidB
import com.gsynk.app.core.RealtimeAi
import com.gsynk.app.core.TurboState
import com.gsynk.app.ui.theme.BgTopA
import com.gsynk.app.ui.theme.BgTopB
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBg
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.DividerColor
import com.gsynk.app.ui.theme.GlowGreen
import com.gsynk.app.ui.theme.GlowViolet
import com.gsynk.app.ui.theme.GrapeGradient
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.GrapeVioletDeep
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TrackBg
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ————————————————————————————————————————————————
//  Живой фон: плавно дышащий градиент + медленные засветки
//  (без размытия и прозрачных слоёв — только градиенты)
// ————————————————————————————————————————————————

@Composable
fun AnimatedGrapeBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Настройки внешнего вида (акцент, скорость) читаем как состояние: сменил — фон ожил.
    val tweakRevision by UiTweaks.revision.collectAsStateWithLifecycle()
    // Фон переливается ВСЕГДА — независимо от нейросети. Пока идёт нагрузка,
    // фазу дополнительно подгоняет «турбо», поэтому поток бежит быстрее.
    val turboActive by TurboState.active.collectAsState()
    val turboPhase by TurboState.progress.collectAsState()
    val aiMode by TurboState.aiMode.collectAsState()
    val transition = rememberInfiniteTransition(label = "bg")
    // Дыхание палитры: 24 секунды туда-обратно, с мягкой кривой. Раньше было 16 секунд
    // с резким разворотом — на глаз это читалось как «рваный» градиент.
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = UiTweaks.breathMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    // постоянное переливание: сквозная циклическая фаза для бегущей волны и полосы
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = UiTweaks.shimmerMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = UiTweaks.driftMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val tilt by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 34_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val top = lerp(BgTopA, BgTopB, breath)
                val mid = lerp(BgMidA, BgMidB, breath)
                val bottom = lerp(BgBottomA, BgBottomB, breath)

                // основной градиент — угол плавно гуляет
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(top, mid, bottom),
                        start = Offset(size.width * (0.1f + 0.25f * tilt), 0f),
                        end = Offset(size.width * (0.9f - 0.25f * tilt), size.height)
                    )
                )

                // Единый оттенок кадра: и круги, и волна берут один и тот же цвет,
                // поэтому переходы между слоями больше не выглядят ступеньками.
                val accent = if (turboActive) {
                    when (aiMode) {
                        RealtimeAi.MODE_ACTIVE -> Color(0xFF7C3AED)
                        RealtimeAi.MODE_NIGHT -> Color(0xFF1E3A8A)
                        else -> Color(0xFF22C55E)
                    }
                } else {
                    // Акцент из «Кастомизации»: фон подстраивается под выбранный оттенок.
                    lerp(UiTweaks.accentStart, Color(0xFF22D3EE), breath)
                }

                // виноградная засветка сверху
                val radius = size.minDimension * (0.95f + 0.12f * drift)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(
                            size.width * (0.16f + 0.06f * drift),
                            size.height * (0.10f + 0.03f * tilt)
                        ),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(
                        size.width * (0.16f + 0.06f * drift),
                        size.height * (0.10f + 0.03f * tilt)
                    )
                )

                // Переливание идёт всегда — фон должен быть живым сам по себе.
                // Нейросеть лишь ускоряет и подкрашивает поток, когда идёт нагрузка.
                run {
                    val phase = if (turboActive) turboPhase else shimmer
                    // край волны мягкий с обеих сторон — резкого «шва» по краю экрана нет
                    val travel = phase * size.height * 1.8f - size.height * 0.4f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                accent.copy(alpha = 0.30f),
                                Color(0xFFC4B5FD).copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            start = Offset(0f, travel),
                            end = Offset(size.width, travel + size.height * 0.55f)
                        )
                    )
                    val stripe = ((phase * 3f) % 1f) * size.width * 1.4f - size.width * 0.2f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.07f),
                                Color.Transparent
                            ),
                            start = Offset(stripe, 0f),
                            end = Offset(stripe + size.width * 0.35f, size.height)
                        )
                    )
                }

                // зелёная засветка снизу
                val radius2 = size.minDimension * (0.85f + 0.15f * (1f - drift))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(lerp(accent, Color(0xFF22C55E), 0.55f).copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(
                            size.width * (0.88f - 0.06f * drift),
                            size.height * (0.88f + 0.04f * tilt)
                        ),
                        radius = radius2
                    ),
                    radius = radius2,
                    center = Offset(
                        size.width * (0.88f - 0.06f * drift),
                        size.height * (0.88f + 0.04f * tilt)
                    )
                )
            }
    ) {
        content()
    }
}

// ————————————————————————————————————————————————
//  Карточка: полностью непрозрачная, с тонкой рамкой
// ————————————————————————————————————————————————

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
    borderColor: Color = CardBorder,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    // Скругление карточки берём из кожи: у каждого элемента своё, как в GSUI V4.
    val shape: androidx.compose.ui.graphics.Shape =
        if (corner > 0.dp) androidx.compose.foundation.shape.RoundedCornerShape(corner) else skinShape("card")
    val effectsLevel = LocalMetroEffects.current
    Column(
        modifier
            .fillMaxWidth()
            .let { base ->
                // Тень дорога на слабом железе: там её нет, границу держит рамка.
                if (effectsLevel == Metro.Effects.FULL) {
                    base.shadow(6.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                } else {
                    base
                }
            }
            .clip(shape)
            .background(CardBg)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content
    )
}

// ————————————————————————————————————————————————
//  Текст с градиентной заливкой
// ————————————————————————————————————————————————

@Composable
fun GradientText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    textAlign: TextAlign? = null
) {
    // Акцент из настроек: если явный градиент не задан (или это прежний «виноград» по
    // умолчанию), берём выбранный пользователем. revision заставляет перечитать цвет.
    val revision by UiTweaks.revision.collectAsStateWithLifecycle()
    val actual = when {
        brush == null -> UiTweaks.accentBrush
        brush === GrapeGradient -> UiTweaks.accentBrush
        else -> brush
    }
    if (revision >= 0) {
        Text(
            text = text,
            modifier = modifier,
            style = style.copy(brush = actual),
            textAlign = textAlign
        )
    }
}

// ————————————————————————————————————————————————
//  Кнопки
// ————————————————————————————————————————————————

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    // Кнопка Metro: плотная заливка, своё скругление, отклик масштабом.
    val shape = skinShape("button")
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.99f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btnScale"
    )
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(if (enabled) GrapeGradient else Brush.linearGradient(listOf(ChipBg, ChipBg)))
            .border(1.dp, if (enabled) Color.Transparent else ChipBorder, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF120720) else TextDim,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color(0xFF120720) else TextDim,
                fontSize = Metro.Type.body,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = TextPrimary,
    enabled: Boolean = true
) {
    val shape = skinShape("button")
    Row(
        modifier
            .clip(shape)
            .background(CardBgAlt)
            .border(1.dp, ChipBorder, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tint, fontSize = Metro.Type.body, fontWeight = FontWeight.Medium)
    }
}

// ————————————————————————————————————————————————
//  Слайдер: непрозрачная дорожка, градиентная заливка
// ————————————————————————————————————————————————

@Composable
fun NeoSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    brush: Brush,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val widthState = remember { mutableFloatStateOf(0f) }

    val thumbSize = 26.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    val targetFill = value.coerceIn(0, 100) / 100f
    val animatedFill by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fill"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .onSizeChanged { widthState.floatValue = it.width.toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val trackWidth = widthState.floatValue
                        if (trackWidth <= thumbPx * 2f) {
                            down.consume()
                            continue
                        }
                        val travel = trackWidth - thumbPx
                        var lastHaptic = latestValue

                        fun emit(x: Float) {
                            val next = ((x / travel).coerceIn(0f, 1f) * 100f).roundToInt()
                            if (next != latestValue) {
                                latestOnChange(next)
                                if (kotlin.math.abs(next - lastHaptic) >= 4) {
                                    lastHaptic = next
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }

                        emit(down.position.x)
                        down.consume()
                        var active = true
                        while (active) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                active = false
                            } else {
                                emit(change.position.x)
                                change.consume()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // дорожка
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RectangleShape)
                .background(TrackBg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedFill)
                    .fillMaxHeight()
                    .clip(RectangleShape)
                    .background(
                        if (enabled) {
                            brush
                        } else {
                            Brush.linearGradient(listOf(ChipBorder, ChipBorder))
                        }
                    )
            )
        }
        // ручка
        Box(
            Modifier
                .offset { IntOffset(((widthState.floatValue - thumbPx) * animatedFill).roundToInt(), 0) }
                .size(thumbSize)
                .clip(RectangleShape)
                .background(if (enabled) Color(0xFFF3ECFF) else Color(0xFF8E82A8))
                .border(1.dp, if (enabled) GrapeViolet else ChipBorder, RectangleShape)
        )
    }
}

// ————————————————————————————————————————————————
//  Чипы
// ————————————————————————————————————————————————

@Composable
fun DayChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = skinShape("toggle")
    val bg by animateColorAsState(
        targetValue = if (selected) GrapeVioletDeep else ChipBg,
        animationSpec = tween(220),
        label = "dayBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) GrapeViolet else ChipBorder,
        animationSpec = tween(220),
        label = "dayBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dayScale"
    )
    Box(
        modifier
            .height(42.dp)
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun TagChip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RectangleShape
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) GrapeVioletDeep else ChipBg)
            .border(1.dp, if (selected) GrapeViolet else ChipBorder, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// ————————————————————————————————————————————————
//  Анимированные значения и индикаторы
// ————————————————————————————————————————————————

@Composable
fun AnimatedPercent(
    percent: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animated by animateIntAsState(
        targetValue = percent.coerceIn(0, 100),
        animationSpec = tween(420),
        label = "percent"
    )
    Text(text = "$animated%", modifier = modifier, style = style, color = color)
}

@Composable
fun ProgressLine(
    percent: Int,
    brush: Brush,
    modifier: Modifier = Modifier,
    width: Dp = 84.dp
) {
    val fraction by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100) / 100f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    Box(
        modifier
            .width(width)
            .height(8.dp)
            .clip(RectangleShape)
            .background(TrackBg)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RectangleShape)
                .background(brush)
        )
    }
}

@Composable
fun MetricPill(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RectangleShape)
            .background(ChipBg)
            .border(1.dp, ChipBorder, RectangleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

/** Пульсирующая точка — маркер «следующее правило». */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier
            .size(10.dp)
            .scale(scale)
            .clip(RectangleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    // Metro: заголовок группы — строчными буквами, с разрядкой, без капса.
    Text(
        text = text.lowercase(),
        modifier = modifier.padding(top = Metro.unit, bottom = Metro.unit / 2f),
        color = TextPrimary.copy(alpha = 0.88f),
        fontSize = Metro.Type.groupHeader,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp
    )
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor)
    )
}

/** Появление блока с небольшой задержкой — «каскад» при открытии экрана. */
@Composable
fun Appear(
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(340)) { it / 5 },
        exit = fadeOut(tween(120)),
        modifier = modifier
    ) {
        content()
    }
}

/** Анимированная «шапка» шторки. */
@Composable
fun SheetGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(44.dp)
            .height(5.dp)
            .clip(RectangleShape)
            .background(ChipBorder)
    )
}


// ————————————————————————————————————————————————
//  Текстовое поле в стиле Grape (единое для всех экранов)
// ————————————————————————————————————————————————

@Composable
fun GrapeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    masked: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        visualTransformation = if (masked) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        shape = RectangleShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GrapeViolet,
            unfocusedBorderColor = ChipBorder,
            focusedContainerColor = CardBgAlt,
            unfocusedContainerColor = CardBgAlt,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = GrapeGreen,
            focusedLabelColor = GrapeVioletSoft,
            unfocusedLabelColor = TextDim
        ),
        modifier = modifier.fillMaxWidth()
    )
}


// ————————————————————————————————————————————————
//  Полноэкранный раздел: безопасные отступы, тонкий заголовок, свой фон
// ————————————————————————————————————————————————

@Composable
fun GrapeScreen(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedGrapeBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка возврата — как значок на панели приложения Metro: 44×44, без заливки.
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(skinShape("toggle"))
                        .border(1.dp, ChipBorder, skinShape("toggle"))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.width(Metro.unit * 1.5f))
                Column(Modifier.weight(1f)) {
                    // Тонкий крупный заголовок страницы: 42 pt в исходнике Metro.
                    GradientText(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = Metro.Type.pageTitle,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-0.6).sp
                        ),
                        brush = GrapeGradient
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = TextDim,
                            fontSize = Metro.Type.pageSubtitle,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
                actions()
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                content()
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}


/**
 * Анимация при нажатии на логотип GSYNK.
 *
 * Из логотипа разлетаются виноградные ягоды: кольца, ягоды и вспышка в цветах
 * приложения. Живёт ровно полторы секунды и сама убирает себя — на экране ничего
 * не остаётся, лишних слоёв нет.
 */
@Composable
fun GrapeLogoBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
    color: Color = GrapeViolet
) {
    if (trigger == 0) return
    var elapsed by remember(trigger) { mutableFloatStateOf(0f) }
    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
            if (elapsed > 1.6f) break
        }
    }
    Canvas(modifier) {
        val progress = (elapsed / 1.5f).coerceIn(0f, 1f)
        if (progress >= 1f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.9f

        // острые расходящиеся кольца — в духе прямоугольного интерфейса
        repeat(3) { ring ->
            val local = (progress - ring * 0.12f).coerceIn(0f, 1f)
            if (local > 0f) {
                val radius = maxRadius * local
                val alpha = (1f - local) * 0.35f
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(center.x - radius, center.y - radius * 0.35f),
                    size = Size(radius * 2f, radius * 0.7f),
                    style = Stroke(width = 3f + 4f * (1f - local))
                )
            }
        }

        // ягоды разлетаются по кругу
        val berries = 14
        for (index in 0 until berries) {
            val angle = (index.toFloat() / berries) * 6.2831855f + progress * 1.2f
            val distance = maxRadius * (0.35f + 0.65f * progress) * (0.7f + 0.3f * (index % 3))
            val position = Offset(
                center.x + kotlin.math.cos(angle) * distance,
                center.y + kotlin.math.sin(angle) * distance * 0.75f
            )
            val alpha = (1f - progress).coerceIn(0f, 1f)
            drawCircle(
                color = GrapeCyan.copy(alpha = alpha * 0.85f),
                radius = 4f + 6f * (1f - progress),
                center = position
            )
            drawCircle(
                color = color.copy(alpha = alpha * 0.6f),
                radius = 2f + 4f * (1f - progress),
                center = position
            )
        }

        if (progress < 0.35f) {
            val alpha = (1f - progress / 0.35f) * 0.5f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = size.minDimension * (0.15f + 0.5f * progress),
                center = center
            )
        }
    }
}

// ————————————————————————————————————————————————
//  Мелкие элементы нового стиля
// ————————————————————————————————————————————————

/** Чип выбора (сортировка, фильтр, режим). */
@Composable
fun SortChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = skinShape("toggle")
    val bg by animateColorAsState(
        targetValue = if (selected) GrapeVioletDeep else ChipBg,
        animationSpec = tween(180),
        label = "sortBg"
    )
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) GrapeViolet else ChipBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = Metro.Type.caption,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.4.sp
        )
    }
}

/** Строка «подпись — значение» для дашбордов и карточек. */
@Composable
fun InfoRow(label: String, value: String, accent: Color = TextPrimary) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextDim,
            fontSize = Metro.Type.caption,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = accent,
            fontSize = Metro.Type.body,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Полоса-индикатор заполнения (память, батарея, уровень сигнала). */
@Composable
fun BarIndicator(percent: Int, brush: Brush, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100) / 100f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bar"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RectangleShape)
            .background(TrackBg)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated.coerceAtLeast(0.01f))
                .clip(RectangleShape)
                .background(brush)
        )
    }
}

/** Иконка-кнопка круглой формы. */
@Composable
fun GlowIconButton(
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary
) {
    Box(
        modifier
            .size(40.dp)
            .clip(RectangleShape)
            .background(CardBg)
            .border(1.dp, ChipBorder, RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * Поток элементов с переносом (чипы, кнопки-капсулы).
 *
 * Своя раскладка вместо жёстких рядов: на узких экранах чипы переносятся на новую
 * строку, а не уезжают за правый край — именно из-за этого часть кнопок раньше
 * оказывалась недоступной.
 */
@Composable
fun ChipFlow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 7.dp,
    verticalGap: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(modifier = modifier, content = content) { measurables, constraints ->
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }

        val positions = mutableListOf<Pair<Int, Int>>()
        var x = 0
        var y = 0
        var lineHeight = 0
        var totalHeight = 0

        placeables.forEach { placeable ->
            if (x > 0 && x + placeable.width > constraints.maxWidth) {
                x = 0
                y += lineHeight + vGap
                lineHeight = 0
            }
            positions.add(x to y)
            x += placeable.width + hGap
            lineHeight = maxOf(lineHeight, placeable.height)
            totalHeight = maxOf(totalHeight, y + placeable.height)
        }

        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(positions[index].first, positions[index].second)
            }
        }
    }
}
