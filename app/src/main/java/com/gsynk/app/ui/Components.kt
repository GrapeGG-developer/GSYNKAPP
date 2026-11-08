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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.gsynk.app.core.TurboMode
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
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TrackBg
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ————————————————————————————————————————————————
//  Живой фон: плавно дышащий градиент + медленные засветки
//  (без размытия и прозрачных слоёв — только градиенты)
// ————————————————————————————————————————————————

/**
 * Один общий часовой State SCRIPT на всё приложение.
 *
 * Раньше каждая плитка, блик и сфера заводили собственный
 * `rememberInfiniteTransition`; теперь они читают один State в фазе рисования.
 */
private val StaticGrealPhase = mutableStateOf(0.38f)
val LocalGrealPhase = staticCompositionLocalOf<State<Float>> { StaticGrealPhase }

@Composable
private fun rememberSharedGrealPhase(): State<Float> {
    val skin = LocalSkinValues.current
    val effects = LocalGiouiEffects.current
    val transition = rememberInfiniteTransition(label = "grealSharedClock")
    val raw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ((9_500 * 100 / skin.prgMotion.coerceIn(40, 200)) * if (effects == Gioui.Effects.LIGHT) 3 / 2 else 1).coerceIn(4_000, 30_000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "grealSharedPhase"
    )
    return remember(skin.level, effects, skin.prgMotion) {
        derivedStateOf {
            if (skin.level == 0 || effects == Gioui.Effects.PLAIN) 0.38f else raw
        }
    }
}

@Composable
fun AnimatedGrapeBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tweakRevision by UiTweaks.revision.collectAsStateWithLifecycle()
    val sharedPhase = rememberSharedGrealPhase()
    val speedScale = (24_000f / UiTweaks.breathMs.toFloat()).coerceIn(0.5f, 2f)
    if (tweakRevision < 0) return

    // Script UI: общий зелёно-чёрный фон со световым пробегом. SCRIPT-аврора
    // больше не запускается; общий phase сохраняется для совместимых бликов.
    Box(modifier.fillMaxSize()) {
        ScriptBackground(Modifier.matchParentSize(), speedScale = speedScale)
        CompositionLocalProvider(
            LocalGrealAuroraActive provides true,
            LocalGrealPhase provides sharedPhase
        ) {
            Box(Modifier.matchParentSize()) { content() }
        }
    }
}

/**
 * Фаза SCRIPT со сдвигом для конкретной поверхности. Возвращает State: тяжёлые
 * Canvas'ы читают его внутри `draw`, а не перестраивают композицию каждый кадр.
 */
@Composable
fun rememberPrgPhaseState(seed: Int = 0): State<Float> {
    val shared = LocalGrealPhase.current
    val shift = ((seed and 0x3ff).toFloat() / 4096f)
    return remember(shared, shift) {
        derivedStateOf { (shared.value + shift) % 1f }
    }
}

/** Совместимая фаза для лёгких поверхностей, которым удобно читать Float сразу. */
@Composable
fun rememberPrgPhase(seed: Int = 0): Float = rememberPrgPhaseState(seed).value

/** Один случайный блик на краю: сторона, позиция, длина и собственная фаза заданы seed-ом. */
internal data class PrgEdgeGlint(
    val edge: Int,
    val start: Float,
    val length: Float,
    val speed: Float,
    val phase: Float,
    val drift: Float,
    val alpha: Float,
    val accentMix: Float
)

private fun prgHash01(seed: Int, index: Int, salt: Int): Float {
    var x = seed xor ((index + 1) * 0x45d9f3b) xor (salt * 0x119de1f3)
    x = x xor (x ushr 16)
    x *= 0x45d9f3b
    x = x xor (x ushr 16)
    return ((x ushr 8) and 0xFFFF) / 65535f
}

/** Стабильный набор случайных бликов: у каждой поверхности свой рисунок, без мерцания при рекомпозиции. */
internal fun prgEdgeGlints(seed: Int): List<PrgEdgeGlint> {
    val count = 2 + (prgHash01(seed, 17, 91) * 3f).toInt().coerceIn(0, 2)
    val firstEdge = (prgHash01(seed, 17, 17) * 4f).toInt().coerceIn(0, 3)
    return List(count) { index ->
        val length = 0.08f + prgHash01(seed, index, 43) * 0.20f
        PrgEdgeGlint(
            edge = (firstEdge + index) % 4,
            start = 0.04f + prgHash01(seed, index, 29) * (0.88f - length),
            length = length,
            speed = 0.55f + prgHash01(seed, index, 71) * 0.95f,
            phase = prgHash01(seed, index, 83),
            drift = 0.015f + prgHash01(seed, index, 101) * 0.055f,
            alpha = 0.48f + prgHash01(seed, index, 113) * 0.52f,
            accentMix = prgHash01(seed, index, 127)
        )
    }
}

/**
 * Блики SCRIPT живут только на краях поверхности.
 * Каждый короткий сегмент случайно выбирает сторону, позицию, длину, скорость и оттенок.
 */
@Composable
fun PrgGlassSheen(
    modifier: Modifier = Modifier,
    accent: Color = ScriptPalette.Accent,
    seed: Int = 0
) {
    ScriptEdgeMark(modifier, accent, seed)
}

/** Единая плоская поверхность Script UI: 2D-градиент без стекла. */
@Composable
fun PrgGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = skinShape("card"),
    accent: Color = UiTweaks.accentStart,
    borderColor: Color = CardBorder,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val focused = borderColor != CardBorder
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.08f),
                        ScriptPalette.Green.copy(alpha = if (focused) 0.26f else 0.14f),
                        ScriptPalette.Ink.copy(alpha = 0.46f)
                    )
                )
            )
            .border(
                1.dp,
                if (focused) ScriptPalette.Lime.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.16f),
                shape
            )
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
    ) {
        ScriptFocusBar(Modifier.matchParentSize(), strength = if (focused) 0.55f else 0.08f)
        Column(Modifier.padding(contentPadding), content = content)
    }
}

// ————————————————————————————————————————————————
//  Карточка: полупрозрачная стеклянная поверхность с тонкой рамкой
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
    val shape: androidx.compose.ui.graphics.Shape =
        if (corner > 0.dp) androidx.compose.foundation.shape.RoundedCornerShape(corner) else skinShape("card")
    PrgGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        accent = if (borderColor == CardBorder) UiTweaks.accentStart else borderColor,
        borderColor = borderColor,
        onClick = onClick,
        contentPadding = contentPadding,
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
    val shape = skinShape("button")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!enabled) 0.99f else if (pressed) 0.965f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "btnScale"
    )
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(ScriptPalette.Lime, ScriptPalette.Green, ScriptPalette.Ink)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.42f), shape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        ScriptFocusBar(Modifier.matchParentSize(), strength = if (enabled) 0.38f else 0.08f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF06200D),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color(0xFF06200D),
                fontSize = Gioui.Type.body,
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
    tint: Color = ScriptPalette.White,
    enabled: Boolean = true
) {
    val shape = skinShape("button")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "secondaryScale"
    )
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.09f), ScriptPalette.Green.copy(alpha = 0.24f), ScriptPalette.Ink.copy(alpha = 0.50f))
                )
            )
            .border(1.dp, ScriptPalette.Lime.copy(alpha = 0.42f), shape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        ScriptFocusBar(Modifier.matchParentSize(), strength = if (enabled) 0.22f else 0.06f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = tint, fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
        }
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
                .height(16.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.26f), Color.White.copy(alpha = 0.10f), TrackBg)))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedFill)
                    .fillMaxHeight()
                    .clip(skinShape("card"))
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
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = 0.98f),
                            if (enabled) ScriptPalette.Lime.copy(alpha = 0.86f) else Color(0xFF8E82A8),
                            if (enabled) ScriptPalette.Lime.copy(alpha = 0.88f) else Color(0xFF554D6E)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.42f), androidx.compose.foundation.shape.CircleShape)
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
        targetValue = if (selected) ScriptPalette.Green.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.07f),
        animationSpec = tween(220),
        label = "dayBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) ScriptPalette.Lime else ChipBorder,
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
            style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal),
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
    val shape = skinShape("card")
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) ScriptPalette.Green.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, if (selected) ScriptPalette.Lime else ChipBorder, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal),
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
            .clip(skinShape("card"))
            .background(TrackBg)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(skinShape("card"))
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
            .clip(skinShape("card"))
            .background(ChipBg)
            .border(1.dp, ChipBorder, skinShape("card"))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
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
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), color.copy(alpha = alpha))))
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    // Script UI: подпись группы лаймовым акцентом.
    Text(
        text = text.lowercase(),
        modifier = modifier.padding(top = Gioui.unit, bottom = Gioui.unit / 2f),
        color = ScriptPalette.Lime,
        fontSize = Gioui.Type.groupHeader,
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
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, ScriptPalette.Lime.copy(alpha = 0.42f), Color.Transparent)
                )
            )
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
        enter = fadeIn(tween(300)) + slideInVertically(tween(340)) { it / 5 } + scaleIn(tween(340), initialScale = 0.965f),
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
            .clip(skinShape("card"))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.34f), ChipBorder.copy(alpha = 0.72f))))
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
        shape = skinShape("sheet"),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ScriptPalette.Lime,
            unfocusedBorderColor = ChipBorder,
            focusedContainerColor = Color.White.copy(alpha = 0.09f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.055f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = GrapeGreen,
            focusedLabelColor = ScriptPalette.Lime,
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
        // GIOUI Metro 5.2: GrapeScreen больше не рисует собственный большой
        // «старый» заголовок. Полноэкранной плиткой управляет MetroAppShell
        // (название, свернуть, закрыть), а здесь остаётся только компактный
        // внутренний возврат для вложенных страниц одного приложения.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 10.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScriptControlButton(
                    text = "←",
                    modifier = Modifier.size(38.dp),
                    onClick = onBack
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title.lowercase(),
                        color = ScriptPalette.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = TextDim,
                            fontSize = Gioui.Type.caption,
                            fontWeight = FontWeight.Light,
                            maxLines = 1
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
                Spacer(Modifier.height(12.dp))
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
    color: Color = ScriptPalette.Lime
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
        targetValue = if (selected) ScriptPalette.Green else ScriptPalette.Ink,
        animationSpec = tween(180),
        label = "sortBg"
    )
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) ScriptPalette.Lime else Color.White.copy(alpha = 0.18f), shape)
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) ScriptPalette.White else ScriptPalette.DimWhite,
            fontSize = Gioui.Type.caption,
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
            fontSize = Gioui.Type.caption,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = accent,
            fontSize = Gioui.Type.body,
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
            .clip(skinShape("card"))
            .background(TrackBg)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated.coerceAtLeast(0.01f))
                .clip(skinShape("card"))
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
            .clip(skinShape("card"))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), CardBg)))
            .border(1.dp, ChipBorder, skinShape("card"))
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
