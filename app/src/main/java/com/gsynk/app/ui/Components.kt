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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ————————————————————————————————————————————————
//  Живой фон: плавно дышащий градиент + медленные засветки
//  (без размытия и прозрачных слоёв — только градиенты)
// ————————————————————————————————————————————————

@Composable
fun AnimatedGrapeBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Акцент и скорость читаем состоянием: изменил настройку — фон сразу перекрасился.
    val tweakRevision by UiTweaks.revision.collectAsStateWithLifecycle()
    val skin = LocalSkinValues.current
    val turboActive by TurboState.active.collectAsState()
    val aiMode by TurboState.aiMode.collectAsState()
    if (tweakRevision < 0) return

    // Одна круговая фаза 0→1. Все координаты и цвета ниже построены на sin/cos(2π·phase),
    // поэтому последний кадр цикла совпадает с первым: нет прыгающей полосы и «шва».
    val transition = rememberInfiniteTransition(label = "bg")
    val rawPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (UiTweaks.breathMs * 100 / skin.prgMotion.coerceIn(40, 200)).coerceIn(6_000, 60_000), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bgPhase"
    )

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val angle = (rawPhase * 2f * PI).toFloat()
                val wave = (sin(angle) + 1f) * 0.5f
                val wave2 = (sin(angle * 2f + PI.toFloat() * 0.35f) + 1f) * 0.5f
                val driftX = cos(angle)
                val driftY = sin(angle)

                val top = lerp(BgTopA, BgTopB, wave)
                val mid = lerp(BgMidA, BgMidB, wave2)
                val bottom = lerp(BgBottomA, BgBottomB, 1f - wave)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(top, mid, bottom),
                        start = Offset(size.width * (0.18f + 0.08f * driftX), 0f),
                        end = Offset(size.width * (0.82f - 0.08f * driftX), size.height)
                    )
                )

                val accent = if (turboActive) {
                    when (aiMode) {
                        RealtimeAi.MODE_ACTIVE -> Color(0xFF7C3AED)
                        RealtimeAi.MODE_NIGHT -> Color(0xFF2563EB)
                        else -> Color(0xFF22C55E)
                    }
                } else {
                    lerp(UiTweaks.accentStart, Color(0xFF22D3EE), wave)
                }
                val glowAlpha = if (turboActive) 0.42f else 0.32f

                fun glow(center: Offset, radius: Float, color: Color, alpha: Float) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                glow(
                    center = Offset(
                        size.width * (0.18f + 0.055f * driftX),
                        size.height * (0.12f + 0.03f * driftY)
                    ),
                    radius = size.minDimension * (0.92f + 0.08f * wave),
                    color = accent,
                    alpha = glowAlpha
                )
                glow(
                    center = Offset(
                        size.width * (0.84f - 0.05f * driftX),
                        size.height * (0.54f + 0.06f * cos(angle * 2f))
                    ),
                    radius = size.minDimension * (0.72f + 0.07f * wave2),
                    color = lerp(accent, Color(0xFF22D3EE), 0.55f),
                    alpha = glowAlpha * 0.72f
                )
                glow(
                    center = Offset(
                        size.width * (0.78f + 0.04f * sin(angle * 2f)),
                        size.height * (0.9f - 0.025f * driftY)
                    ),
                    radius = size.minDimension * (0.86f + 0.08f * (1f - wave)),
                    color = lerp(accent, Color(0xFF22C55E), 0.58f),
                    alpha = glowAlpha * 0.82f
                )

                // GSYNKPRG: диагональные авроры, тонкая сетка и искры поверх дыхания фона.
                val motionAlpha = if (skin.level == 0) 0.45f else 1f
                for (band in 0..2) {
                    val centerX = size.width * (0.22f + band * 0.28f + 0.07f * sin(angle + band * 1.7f))
                    val bandWidth = size.width * (0.16f + band * 0.035f)
                    rotate(-14f, Offset(centerX, size.height * 0.5f)) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, accent.copy(alpha = 0.10f * motionAlpha), Color.Transparent),
                                startX = centerX - bandWidth / 2f,
                                endX = centerX + bandWidth / 2f
                            ),
                            topLeft = Offset(centerX - bandWidth / 2f, -size.height * 0.12f),
                            size = Size(bandWidth, size.height * 1.24f)
                        )
                    }
                }
                if (skin.grid) {
                    val stepPx = 30.dp.toPx()
                    var gx = (driftX * 5f) % stepPx
                    while (gx < size.width) {
                        drawLine(Color.White.copy(alpha = 0.026f), Offset(gx, 0f), Offset(gx, size.height), 1f)
                        gx += stepPx
                    }
                    var gy = (driftY * 5f) % stepPx
                    while (gy < size.height) {
                        drawLine(Color.White.copy(alpha = 0.022f), Offset(0f, gy), Offset(size.width, gy), 1f)
                        gy += stepPx
                    }
                }
                if (skin.level > 0) {
                    for (spark in 0 until 18) {
                        val fx = ((spark * 37 + 13) % 100) / 100f
                        val fy = ((spark * 53 + 7) % 100) / 100f
                        val twinkle = (sin(angle * 2f + spark * 1.31f) + 1f) * 0.5f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f + 0.13f * twinkle),
                            radius = 1.1f + 1.8f * twinkle,
                            center = Offset(size.width * fx, size.height * fy + 5f * sin(angle + spark))
                        )
                    }
                }
            }
    ) {
        content()
    }
}

/** Общая фаза GSYNKPRG: блики и контурное свечение движутся в одном ритме. */
@Composable
fun rememberPrgPhase(seed: Int = 0): Float {
    val skin = LocalSkinValues.current
    val effects = LocalGiouiEffects.current
    val transition = rememberInfiniteTransition(label = "gsynkprg")
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
        label = "prgPhase"
    )
    if (skin.level == 0 || effects == Gioui.Effects.PLAIN) return 0.38f
    val shift = ((seed and 0x3ff).toFloat() / 4096f)
    return (raw + shift) % 1f
}

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
 * Блики GSYNKPRG живут только на краях поверхности.
 * Каждый короткий сегмент случайно выбирает сторону, позицию, длину, скорость и оттенок.
 */
@Composable
fun PrgGlassSheen(
    modifier: Modifier = Modifier,
    accent: Color = GrapeViolet,
    seed: Int = 0
) {
    val skin = LocalSkinValues.current
    val phase = rememberPrgPhase(seed)
    val glints = remember(seed) { prgEdgeGlints(seed) }
    val glare = (skin.prgGlare.coerceIn(0, 100) / 100f) * if (skin.level == 0 || LocalGiouiEffects.current == Gioui.Effects.PLAIN) 0.35f else 1f
    if (glare <= 0.01f) return
    Canvas(modifier) {
        val inset = 1.15.dp.toPx().coerceAtMost(size.minDimension * 0.20f).coerceAtLeast(0.6f)
        glints.forEach { glint ->
            val localPhase = (phase * glint.speed + glint.phase) % 1f
            val wave = 0.5f + 0.5f * sin(localPhase * (2f * PI).toFloat())
            val pulse = 0.38f + 0.62f * wave
            val glintStart = (glint.start + sin(localPhase * (2f * PI).toFloat()) * glint.drift)
                .coerceIn(0.025f, (0.975f - glint.length).coerceAtLeast(0.03f))
            val glintEnd = glintStart + glint.length
            val startPoint: Offset
            val endPoint: Offset
            when (glint.edge) {
                0 -> {
                    startPoint = Offset(size.width * glintStart, inset)
                    endPoint = Offset(size.width * glintEnd, inset)
                }
                1 -> {
                    startPoint = Offset(size.width - inset, size.height * glintStart)
                    endPoint = Offset(size.width - inset, size.height * glintEnd)
                }
                2 -> {
                    startPoint = Offset(size.width * glintEnd, size.height - inset)
                    endPoint = Offset(size.width * glintStart, size.height - inset)
                }
                else -> {
                    startPoint = Offset(inset, size.height * glintEnd)
                    endPoint = Offset(inset, size.height * glintStart)
                }
            }
            val color = lerp(Color.White, accent, glint.accentMix * 0.55f)
            drawLine(
                color = color.copy(alpha = 0.12f * glare * glint.alpha * pulse),
                start = startPoint,
                end = endPoint,
                strokeWidth = 3.4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = color.copy(alpha = 0.52f * glare * glint.alpha * pulse),
                start = startPoint,
                end = endPoint,
                strokeWidth = 1.15.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color.copy(alpha = 0.34f * glare * glint.alpha * pulse),
                radius = 1.55.dp.toPx(),
                center = if (glint.accentMix >= 0.5f) endPoint else startPoint
            )
        }
    }
}

/** Единственная поверхность приложения: прозрачное realtime-стекло GSYNKPRG. */
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
    val skin = LocalSkinValues.current
    val phase = rememberPrgPhase(accent.hashCode())
    val density = skin.prgOpacity.coerceIn(5, 90) / 100f
    val glow = skin.prgGlow.coerceIn(0, 100) / 100f
    val pulse = (sin((phase * 2f * PI).toFloat()) + 1f) * 0.5f
    val borderBrush = Brush.linearGradient(
        listOf(
            lerp(borderColor, Color.White, 0.20f + 0.18f * pulse).copy(alpha = 0.55f + 0.30f * glow * pulse),
            accent.copy(alpha = 0.28f + 0.34f * glow * (1f - pulse)),
            lerp(borderColor, accent, 0.45f).copy(alpha = 0.62f)
        )
    )
    Box(
        modifier
            .let { base ->
                if (skin.level > 0 && glow > 0.02f) {
                    base.shadow(
                        elevation = (3f + 9f * glow * (0.55f + 0.45f * pulse)).dp,
                        shape = shape,
                        ambientColor = accent.copy(alpha = 0.28f * glow),
                        spotColor = accent.copy(alpha = 0.42f * glow)
                    )
                } else {
                    base
                }
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.11f + 0.07f * (1f - density)),
                        accent.copy(alpha = 0.07f + 0.28f * density),
                        Color(0xFF090614).copy(alpha = 0.17f + 0.47f * density)
                    )
                )
            )
            .border(1.dp, borderBrush, shape)
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
        PrgGlassSheen(Modifier.matchParentSize(), accent = accent, seed = borderColor.hashCode())
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
    // Кнопка GIOUI: плотная заливка, своё скругление, отклик масштабом.
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
            .background(if (enabled) UiTweaks.accentBrush else Brush.linearGradient(listOf(ChipBg, ChipBg)))
            .border(1.dp, if (enabled) Color.White.copy(alpha = 0.34f) else ChipBorder, shape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) PrgGlassSheen(Modifier.matchParentSize(), accent = Color.White, seed = text.hashCode())
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
    tint: Color = TextPrimary,
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
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), CardBgAlt.copy(alpha = 0.72f))))
            .border(1.dp, ChipBorder.copy(alpha = 0.9f), shape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        PrgGlassSheen(Modifier.matchParentSize(), accent = UiTweaks.accentStart, seed = text.hashCode())
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
                .height(14.dp)
                .clip(skinShape("card"))
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), TrackBg)))
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
                .clip(skinShape("card"))
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.92f), if (enabled) GrapeVioletSoft.copy(alpha = 0.72f) else Color(0xFF8E82A8))))
                .border(1.dp, if (enabled) GrapeViolet else ChipBorder, skinShape("card"))
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
        targetValue = if (selected) GrapeVioletDeep.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.07f),
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
            .background(if (selected) GrapeVioletDeep.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, if (selected) GrapeViolet else ChipBorder, shape)
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
            .clip(skinShape("card"))
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    // GIOUI: заголовок группы — строчными буквами, с разрядкой, без капса.
    Text(
        text = text.lowercase(),
        modifier = modifier.padding(top = Gioui.unit, bottom = Gioui.unit / 2f),
        color = TextPrimary.copy(alpha = 0.88f),
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
            focusedBorderColor = GrapeViolet,
            unfocusedBorderColor = ChipBorder,
            focusedContainerColor = Color.White.copy(alpha = 0.09f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.055f),
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
                Box(
                    Modifier
                        .size(width = 36.dp, height = 30.dp)
                        .clip(RectangleShape)
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.04f))))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RectangleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title.lowercase(),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Light,
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
