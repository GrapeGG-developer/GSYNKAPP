package com.gsynk.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gsynk.app.ui.theme.BgBottomA
import com.gsynk.app.ui.theme.BgBottomB
import com.gsynk.app.ui.theme.BgMidA
import com.gsynk.app.ui.theme.BgMidB
import com.gsynk.app.ui.theme.BgTopA
import com.gsynk.app.ui.theme.BgTopB
import com.gsynk.app.ui.theme.CardBg
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
    val transition = rememberInfiniteTransition(label = "bg")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val tilt by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
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

                // виноградная засветка сверху
                val radius = size.minDimension * (0.95f + 0.12f * drift)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GlowViolet.copy(alpha = 0.50f), Color.Transparent),
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

                // зелёная засветка снизу
                val radius2 = size.minDimension * (0.85f + 0.15f * (1f - drift))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GlowGreen.copy(alpha = 0.40f), Color.Transparent),
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
    corner: Dp = 26.dp,
    borderColor: Color = CardBorder,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
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
    brush: Brush = GrapeGradient,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(brush = brush),
        textAlign = textAlign
    )
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
    val shape = RoundedCornerShape(18.dp)
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
            .padding(horizontal = 22.dp, vertical = 14.dp),
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
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
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .clip(shape)
            .background(CardBgAlt)
            .border(1.dp, ChipBorder, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tint, style = MaterialTheme.typography.titleSmall)
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
                .clip(RoundedCornerShape(50))
                .background(TrackBg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedFill)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
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
                .clip(CircleShape)
                .background(if (enabled) Color(0xFFF3ECFF) else Color(0xFF8E82A8))
                .border(1.dp, if (enabled) GrapeViolet else ChipBorder, CircleShape)
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
    val shape = RoundedCornerShape(14.dp)
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
    val shape = RoundedCornerShape(50)
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
            .clip(RoundedCornerShape(50))
            .background(TrackBg)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
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
            .clip(RoundedCornerShape(50))
            .background(ChipBg)
            .border(1.dp, ChipBorder, RoundedCornerShape(50))
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
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = TextDim,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
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
            .clip(RoundedCornerShape(50))
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
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
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
