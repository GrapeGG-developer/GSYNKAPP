package com.gsynk.app.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.TurboState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.isActive

/**
 * GREAL — Grape Realism Engine.
 *
 * Новый визуальный язык GSYNK: интерфейс собран не из плоских прямоугольников,
 * а из физических материалов. Под панелями живёт северное сияние, которое на
 * Android 13+ считается попиксельно AGSL-шейдером на видеоускорителе; поверх
 * него — полупрозрачные слои с мягкой текстурой, фасками, зерном и настоящим
 * направленным светом. Плитки стали объёмными сферами, а лента времени —
 * глубокой 3D-сценой без кассетных линий и кругов.
 */
/** Уже нарисованная родительская аврора: вложенные экраны второй GPU-шейдер не запускают. */
val LocalGrealAuroraActive = staticCompositionLocalOf { false }

object Greal {
    const val NAME = "GREAL"
    const val VERSION = "GREAL — Grape Realism Engine"

    val AuroraViolet = Color(0xFF7C3AED)
    val AuroraCyan = Color(0xFF22D3EE)
    val AuroraGreen = Color(0xFF34D399)
    val AuroraPink = Color(0xFFF472B6)
    val AuroraAmber = Color(0xFFFBBF24)
    val DeepSpace = Color(0xFF05030F)
    val DeepSea = Color(0xFF041511)
}

internal data class GrealDepthTransform(
    val rotationX: Float,
    val rotationY: Float,
    val scale: Float,
    val alpha: Float,
    val translationXD: Float,
    val translationYDp: Float,
    val pivotY: Float,
    val shadowElevation: Float
)

/**
 * Панель в глубокой 3D-сцене GREAL. [offsetFraction] — расстояние центра
 * панели от центра экрана в долях половины высоты: центр смотрит прямо,
 * прошлое и будущее сильно поворачиваются, уходят в перспективу и темнеют.
 * Никакой кассетной графики: только объём, свет и тень.
 */
internal fun grealDepthTransform(offsetFraction: Float): GrealDepthTransform {
    val offset = offsetFraction.coerceIn(-1.25f, 1.25f)
    val distance = abs(offset)
    val curved = distance.toDouble().pow(1.28).toFloat()
    return GrealDepthTransform(
        rotationX = -offset * 39f,
        rotationY = offset * 7.5f,
        scale = (1f - 0.165f * curved).coerceIn(0.78f, 1f),
        alpha = (1f - 0.27f * distance).coerceIn(0.58f, 1f),
        translationXD = -offset * 10f,
        translationYDp = offset * 15f,
        pivotY = (0.5f - offset * 0.29f).coerceIn(0.15f, 0.85f),
        shadowElevation = (36f * (1f - distance) + 8f).coerceAtLeast(0f)
    )
}

/** Размытие летящего окна: максимум в точке плитки/рулона, ноль — в покое. */
internal fun grealWindowBlurDp(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return (1f - p).toDouble().pow(1.35).toFloat() * 22f
}

/** Дополнительный «рулонный» наклон окна на середине morph-а. */
internal fun grealWindowRollDegrees(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return sin(p * PI.toFloat()) * 16f + (1f - p) * 9f
}

/** Диаметр сферы-плитки: сфера + подпись всегда целиком внутри ячейки. */
internal fun grealOrbDiameterDp(cellWidthDp: Int, cellHeightDp: Int, labelSpaceDp: Int = 27): Int {
    val byWidth = (cellWidthDp.coerceAtLeast(1) * 68 + 50) / 100
    val byHeight = (cellHeightDp.coerceAtLeast(1) - labelSpaceDp.coerceAtLeast(0)).coerceAtLeast(28)
    return min(byWidth, byHeight).coerceIn(30, 96)
}

private fun grealHash01(seed: Int, index: Int, salt: Int): Float {
    var x = seed xor ((index + 1) * 0x45d9f3b) xor (salt * 0x119de1f3)
    x = x xor (x ushr 16)
    x *= 0x45d9f3b
    x = x xor (x ushr 16)
    return ((x ushr 8) and 0xFFFF) / 65535f
}

private class GrealProgram(val shader: RuntimeShader, val brush: ShaderBrush)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createGrealProgram(): GrealProgram {
    val shader = RuntimeShader(GREAL_AURORA_AGSL)
    return GrealProgram(shader, ShaderBrush(shader))
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawGrealShader(
    program: GrealProgram,
    time: Float,
    intensity: Float,
    accent: Color
) {
    program.shader.setFloatUniform("uSize", size.width, size.height)
    program.shader.setFloatUniform("uTime", time)
    program.shader.setFloatUniform("uIntensity", intensity)
    program.shader.setFloatUniform("uAccent", accent.red, accent.green, accent.blue)
    drawRect(brush = program.brush, size = size)
}

/**
 * Северное сияние GREAL. На полном уровне эффектов и Android 13+ каждый
 * пиксель считается на GPU: шумовые потоки, четыре занавеса сияния, звёзды,
 * зерно и виньетка. Ниже — честный градиентный запасной вариант.
 */
@Composable
fun GrealAuroraField(
    modifier: Modifier = Modifier,
    speedScale: Float = 1f
) {
    val effects = LocalGiouiEffects.current
    val skin = LocalSkinValues.current
    val turboActive by TurboState.active.collectAsState()
    // Вложенные превью не запускают второй попиксельный GPU-шейдер поверх родительской авроры.
    val parentAuroraActive = LocalGrealAuroraActive.current
    val program: GrealProgram? = remember(effects, parentAuroraActive) {
        if (!parentAuroraActive && effects == Gioui.Effects.FULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { createGrealProgram() }.getOrNull()
        } else {
            null
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(effects, speedScale, skin.prgMotion) {
        if (effects == Gioui.Effects.PLAIN) return@LaunchedEffect
        var previous = 0L
        val motion = speedScale.coerceIn(0.25f, 2.4f) * (skin.prgMotion.coerceIn(40, 200) / 100f)
        while (isActive) {
            withFrameNanos { now ->
                if (previous == 0L) previous = now
                val delta = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previous = now
                time += delta * motion
            }
        }
    }
    val accent = UiTweaks.accentStart
    val intensity = when {
        turboActive -> 1.18f
        effects == Gioui.Effects.PLAIN -> 0.72f
        else -> 0.94f
    }
    Canvas(modifier) {
        if (program != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { drawGrealShader(program, time, intensity, accent) }
                .onFailure { drawGrealAuroraFallback(time, accent, intensity) }
        } else {
            drawGrealAuroraFallback(time, accent, intensity)
        }
    }
}

private fun DrawScope.drawGrealAuroraFallback(time: Float, accent: Color, intensity: Float) {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Greal.DeepSpace, Color(0xFF12082C), Greal.DeepSea),
            start = Offset(size.width * (0.16f + 0.05f * sin(time * 0.11f)), 0f),
            end = Offset(size.width * 0.84f, size.height)
        ),
        size = size
    )
    val palette = listOf(Greal.AuroraViolet, Greal.AuroraCyan, Greal.AuroraGreen, Greal.AuroraPink, accent)
    palette.forEachIndexed { index, color ->
        val centerX = size.width * (0.18f + index * 0.17f + 0.045f * sin(time * 0.13f + index * 1.7f))
        val bandWidth = size.width * (0.20f + 0.025f * index)
        rotate(-13f + index * 2.5f, Offset(centerX, size.height * 0.5f)) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = 0.15f * intensity), Color.Transparent),
                    startX = centerX - bandWidth / 2f,
                    endX = centerX + bandWidth / 2f
                ),
                topLeft = Offset(centerX - bandWidth / 2f, -size.height * 0.14f),
                size = Size(bandWidth, size.height * 1.28f)
            )
        }
    }
    fun glow(center: Offset, radius: Float, color: Color, alpha: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
    }
    glow(Offset(size.width * 0.20f, size.height * 0.14f), size.minDimension * 0.92f, accent, 0.26f * intensity)
    glow(Offset(size.width * 0.86f, size.height * 0.62f), size.minDimension * 0.74f, Greal.AuroraCyan, 0.17f * intensity)
    glow(Offset(size.width * 0.72f, size.height * 0.94f), size.minDimension * 0.80f, Greal.AuroraGreen, 0.18f * intensity)
    for (star in 0 until 24) {
        val fx = grealHash01(9137, star, 11)
        val fy = grealHash01(9137, star, 29)
        val twinkle = 0.5f + 0.5f * sin(time * 1.3f + star * 1.61f)
        drawCircle(
            color = Color.White.copy(alpha = 0.05f + 0.12f * twinkle),
            radius = 1f + 1.7f * twinkle,
            center = Offset(size.width * fx, size.height * fy + 4f * sin(time * 0.4f + star))
        )
    }
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f)),
            center = Offset(size.width * 0.5f, size.height * 0.46f),
            radius = size.maxDimension * 0.72f
        ),
        size = size
    )
}

/**
 * Физическая текстура GREAL: мягкие цветные облака материала, микрозерно
 * и объёмные фаски света без серых линий. Рисунок детерминирован seed-ом
 * поверхности — при перерисовке не мерцает.
 */
@Composable
fun GrealTexture(
    modifier: Modifier = Modifier,
    accent: Color = Greal.AuroraViolet,
    seed: Int = 0,
    density: Float = 0.38f
) {
    val effects = LocalGiouiEffects.current
    val skin = LocalSkinValues.current
    val textureStrength = if (skin.grid) 1f else 0.58f
    Canvas(modifier) {
        if (size.width <= 2f || size.height <= 2f) return@Canvas
        val material = density.coerceIn(0f, 1f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f * (1f - material * 0.45f)),
                    accent.copy(alpha = 0.05f + 0.08f * material),
                    Color.Black.copy(alpha = 0.10f + 0.12f * material)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.42f, size.height)
            ),
            size = size
        )

        if (effects != Gioui.Effects.PLAIN) {
            // Мягкие цветные облака материала вместо серых царапин и швов.
            val materialColors = listOf(
                accent,
                Greal.AuroraCyan,
                Greal.AuroraGreen,
                Greal.AuroraPink,
                Color.White
            )
            for (patch in 0 until 22) {
                val patchWidth = (20f + grealHash01(seed, patch, 19) * 58f).dp.toPx()
                val patchHeight = (6f + grealHash01(seed, patch, 23) * 17f).dp.toPx()
                val patchX = grealHash01(seed, patch, 29) * (size.width - patchWidth).coerceAtLeast(1f)
                val patchY = grealHash01(seed, patch, 31) * (size.height - patchHeight).coerceAtLeast(1f)
                val patchColor = materialColors[patch % materialColors.size]
                val patchAlpha = (0.030f + 0.055f * grealHash01(seed, patch, 37)) * textureStrength
                rotate(-16f + grealHash01(seed, patch, 41) * 32f, Offset(patchX + patchWidth / 2f, patchY + patchHeight / 2f)) {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, patchColor.copy(alpha = patchAlpha), Color.Transparent),
                            start = Offset(patchX, patchY + patchHeight / 2f),
                            end = Offset(patchX + patchWidth, patchY + patchHeight / 2f)
                        ),
                        topLeft = Offset(patchX, patchY),
                        size = Size(patchWidth, patchHeight),
                        cornerRadius = CornerRadius(patchHeight / 2f, patchHeight / 2f)
                    )
                }
            }
            for (grain in 0 until 64) {
                val grainSize = (0.8f + grealHash01(seed, grain, 43) * 2.2f).dp.toPx().coerceAtLeast(1f)
                val grainX = grealHash01(seed, grain, 47) * (size.width - grainSize).coerceAtLeast(1f)
                val grainY = grealHash01(seed, grain, 53) * (size.height - grainSize).coerceAtLeast(1f)
                val grainAlpha = (0.025f + 0.05f * grealHash01(seed, grain, 59)) * textureStrength
                drawRoundRect(
                    color = if (grain % 3 == 0) Color.White.copy(alpha = grainAlpha) else accent.copy(alpha = grainAlpha * 0.85f),
                    topLeft = Offset(grainX, grainY),
                    size = Size(grainSize, grainSize * 0.68f),
                    cornerRadius = CornerRadius(grainSize / 2f, grainSize / 2f)
                )
            }
        }

        // Объёмные фаски мягкими световыми полосами, без контурных линий.
        val bevel = 12.dp.toPx().coerceAtMost(size.minDimension * 0.22f)
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent), 0f, bevel),
            size = Size(size.width, bevel)
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), size.height - bevel, size.height),
            topLeft = Offset(0f, size.height - bevel),
            size = Size(size.width, bevel)
        )
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 0f, bevel),
            size = Size(bevel, size.height)
        )
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)), size.width - bevel, size.width),
            topLeft = Offset(size.width - bevel, 0f),
            size = Size(bevel, size.height)
        )
    }
}

/** Активные 3D-углы окна: светящиеся физические скобки и винты по краям. */
@Composable
fun GrealActiveCorners(
    modifier: Modifier = Modifier,
    accent: Color = Greal.AuroraViolet,
    strength: Float = 0.5f
) {
    Canvas(modifier) {
        if (size.width <= 8f || size.height <= 8f) return@Canvas
        val power = strength.coerceIn(0f, 1f)
        if (power <= 0.01f) return@Canvas
        val inset = 7.dp.toPx()
        val len = (size.minDimension * 0.075f).coerceIn(14.dp.toPx(), 32.dp.toPx())
        val light = lerp(Color.White, accent, 0.30f)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(light.copy(alpha = 0.16f * power), Offset(x, y), Offset(x + dx * len, y), 5.dp.toPx(), StrokeCap.Round)
            drawLine(light.copy(alpha = 0.16f * power), Offset(x, y), Offset(x, y + dy * len), 5.dp.toPx(), StrokeCap.Round)
            drawLine(light.copy(alpha = 0.72f * power), Offset(x, y), Offset(x + dx * len, y), 1.5.dp.toPx(), StrokeCap.Round)
            drawLine(light.copy(alpha = 0.72f * power), Offset(x, y), Offset(x, y + dy * len), 1.5.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.Black.copy(alpha = 0.34f * power), 3.4.dp.toPx(), Offset(x + dx * 4.dp.toPx(), y + dy * 4.dp.toPx()))
            drawCircle(light.copy(alpha = 0.82f * power), 2.0.dp.toPx(), Offset(x + dx * 4.dp.toPx(), y + dy * 4.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.9f * power), 0.8.dp.toPx(), Offset(x + dx * 3.2.dp.toPx(), y + dy * 3.2.dp.toPx()))
        }
        corner(inset, inset, 1f, 1f)
        corner(size.width - inset, inset, -1f, 1f)
        corner(inset, size.height - inset, 1f, -1f)
        corner(size.width - inset, size.height - inset, -1f, -1f)
    }
}

/**
 * Глубокий задний план «Пуска»: только мягкие цветные объёмные плиты
 * авроры, уходящие в перспективу от скролла. Никаких серых линий,
 * перфорации, катушек и кассетных кругов.
 */
@Composable
fun GrealDepthBackdrop(
    modifier: Modifier = Modifier,
    scrollPixels: Float = 0f
) {
    Canvas(modifier) {
        if (size.width <= 4f || size.height <= 4f) return@Canvas
        val colors = listOf(
            Greal.AuroraViolet,
            Greal.AuroraCyan,
            Greal.AuroraGreen,
            Greal.AuroraPink,
            Greal.AuroraAmber
        )
        val slabStep = 190.dp.toPx().coerceAtLeast(80f)
        val shift = -(abs(scrollPixels) % slabStep)
        var index = 0
        var y = shift - slabStep
        while (y < size.height + slabStep) {
            val centerDistance = abs(((y + slabStep * 0.28f) / size.height.coerceAtLeast(1f)) - 0.5f).coerceIn(0f, 0.75f)
            val depthScale = 1f - centerDistance * 0.34f
            val slabWidth = size.width * (0.70f + 0.18f * grealHash01(571, index, 11)) * depthScale
            val slabHeight = (54f + 24f * grealHash01(571, index, 13)).dp.toPx() * depthScale
            val drift = sin(scrollPixels / 420f + index * 1.37f) * size.width * 0.035f
            val slabX = (size.width - slabWidth) / 2f + drift
            val color = colors[index % colors.size]
            val alpha = 0.105f * (1f - centerDistance * 0.72f)
            rotate(-5f + grealHash01(571, index, 17) * 10f, Offset(slabX + slabWidth / 2f, y + slabHeight / 2f)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.White.copy(alpha = alpha * 0.34f), Color.Transparent),
                        start = Offset(slabX, y),
                        end = Offset(slabX + slabWidth, y + slabHeight)
                    ),
                    topLeft = Offset(slabX, y),
                    size = Size(slabWidth, slabHeight),
                    cornerRadius = CornerRadius(slabHeight * 0.48f, slabHeight * 0.48f)
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Black.copy(alpha = alpha * 0.55f), Color.Transparent),
                        start = Offset(slabX, y + slabHeight * 0.55f),
                        end = Offset(slabX + slabWidth, y + slabHeight * 1.45f)
                    ),
                    topLeft = Offset(slabX + slabWidth * 0.06f, y + slabHeight * 0.58f),
                    size = Size(slabWidth * 0.88f, slabHeight * 0.44f),
                    cornerRadius = CornerRadius(slabHeight * 0.40f, slabHeight * 0.40f)
                )
            }
            y += slabStep
            index++
        }
    }
}

/**
 * Объёмная сфера GREAL: несколько слоёв стекла/металла, орбитальное кольцо,
 * живые спутники и тиснёная ультрадетальная иконка из трёх световых слоёв.
 */
@Composable
fun GrealOrbFace(
    modifier: Modifier = Modifier,
    accent: Color,
    iconRes: Int,
    seed: Int,
    active: Boolean = false,
    iconScale: Float = 0.48f
) {
    val phaseState = rememberPrgPhaseState(seed)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val phase = phaseState.value
            val minDim = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minDim * 0.465f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(center.x, center.y + radius * 0.82f),
                    radius = radius * 1.05f
                ),
                topLeft = Offset(center.x - radius * 1.02f, center.y + radius * 0.48f),
                size = Size(radius * 2.04f, radius * 0.62f)
            )
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.62f), accent.copy(alpha = 0.48f), Color.Black.copy(alpha = 0.55f)),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f),
                        lerp(accent, Color.White, 0.46f).copy(alpha = 0.92f),
                        accent.copy(alpha = 0.88f),
                        lerp(accent, Color.Black, 0.58f).copy(alpha = 0.96f)
                    ),
                    center = Offset(center.x - radius * 0.30f, center.y - radius * 0.34f),
                    radius = radius * 1.08f
                ),
                radius = radius * 0.94f,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.035f), Color.Black.copy(alpha = 0.16f)),
                    center = Offset(center.x - radius * 0.18f, center.y - radius * 0.20f),
                    radius = radius * 0.82f
                ),
                radius = radius * 0.78f,
                center = center
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(center.x + radius * 0.10f, center.y + radius * 0.62f),
                    radius = radius * 0.78f
                ),
                topLeft = Offset(center.x - radius * 0.74f, center.y + radius * 0.20f),
                size = Size(radius * 1.48f, radius * 0.72f)
            )
            drawCircle(Color.White.copy(alpha = 0.30f), radius * 0.94f, center, style = Stroke(width = 1.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.20f), radius * 0.80f, center, style = Stroke(width = 0.7.dp.toPx()))

            val capCenter = Offset(center.x - radius * 0.34f, center.y - radius * 0.48f)
            rotate(-24f, capCenter) {
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.50f), Color.Transparent),
                        center = capCenter,
                        radius = radius * 0.46f
                    ),
                    topLeft = Offset(capCenter.x - radius * 0.46f, capCenter.y - radius * 0.18f),
                    size = Size(radius * 0.92f, radius * 0.36f)
                )
            }

            val orbitTopLeft = Offset(center.x - radius * 0.98f, center.y - radius * 0.38f)
            val orbitSize = Size(radius * 1.96f, radius * 0.76f)
            drawArc(
                color = Color.White.copy(alpha = 0.26f),
                startAngle = phase * 360f,
                sweepAngle = 128f,
                useCenter = false,
                topLeft = orbitTopLeft,
                size = orbitSize,
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = lerp(accent, Color.White, 0.35f).copy(alpha = 0.22f),
                startAngle = phase * 360f + 188f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = orbitTopLeft,
                size = orbitSize,
                style = Stroke(width = 0.9.dp.toPx(), cap = StrokeCap.Round)
            )
            // Вторая наклонная орбита усиливает объём сферы без серых направляющих.
            drawArc(
                color = lerp(accent, Greal.AuroraGreen, 0.48f).copy(alpha = 0.16f),
                startAngle = -phase * 540f + 25f,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.86f, center.y - radius * 0.54f),
                size = Size(radius * 1.72f, radius * 1.08f),
                style = Stroke(width = 0.8.dp.toPx(), cap = StrokeCap.Round)
            )
            for (satellite in 0 until 6) {
                val angle = phase * 2f * PI.toFloat() + satellite * PI.toFloat() / 3f
                val pos = Offset(
                    center.x + cos(angle) * radius * 0.98f,
                    center.y + sin(angle) * radius * 0.38f
                )
                drawCircle(lerp(accent, Color.White, 0.52f).copy(alpha = 0.62f), radius * 0.055f, pos)
                drawCircle(Color.White.copy(alpha = 0.9f), radius * 0.022f, pos + Offset(-radius * 0.018f, -radius * 0.018f))
            }
            if (active) {
                val pulse = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
                drawCircle(
                    color = Color.White.copy(alpha = 0.24f + 0.30f * pulse),
                    radius = radius * (1.005f + 0.025f * pulse),
                    center = center,
                    style = Stroke(width = (1.4f + pulse).dp.toPx())
                )
            }
        }
        if (iconRes != 0) {
            val iconSize = (minOf(maxWidth, maxHeight) * iconScale.coerceIn(0.25f, 0.72f)).coerceAtLeast(12.dp)
            Icon(
                imageVector = ImageVector.vectorResource(iconRes),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.38f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 0.8.dp, y = 1.4.dp)
                    .size(iconSize)
            )
            Icon(
                imageVector = ImageVector.vectorResource(iconRes),
                contentDescription = null,
                tint = lerp(accent, Color.White, 0.72f).copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-0.7).dp, y = (-0.7).dp)
                    .size(iconSize)
            )
            Icon(
                imageVector = ImageVector.vectorResource(iconRes),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.97f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(iconSize)
            )
        }
    }
}

private const val GREAL_AURORA_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float uIntensity;
uniform float3 uAccent;

float grealHash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float grealNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(grealHash(i), grealHash(i + float2(1.0, 0.0)), f.x),
        mix(grealHash(i + float2(0.0, 1.0)), grealHash(i + float2(1.0, 1.0)), f.x),
        f.y
    );
}

float grealFbm(float2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        value += amplitude * grealNoise(p);
        p = p * 2.03 + float2(17.3, 9.1);
        amplitude *= 0.52;
    }
    return value;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float2 p = float2(uv.x * 2.4, uv.y * 3.4);
    float t = uTime * 0.045;
    float flow = grealFbm(p + float2(t * 1.7, -t));
    float flow2 = grealFbm(p * 1.7 + float2(-t * 1.3, t * 0.7) + flow);
    float curtain = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float center = 0.18 + 0.19 * fi + 0.10 * sin(uTime * 0.11 + fi * 1.7 + uv.x * 2.2);
        float width = 0.10 + 0.035 * fi + 0.05 * flow;
        float z = (uv.y - center + 0.16 * (flow - 0.5)) / width;
        float band = exp(-z * z);
        curtain += band * (0.62 + 0.38 * sin(uTime * 0.13 + fi * 2.1 + uv.x * 5.0 + flow2 * 3.0));
    }
    float hue = fract(0.72 + uv.y * 0.55 + flow * 0.28 + uTime * 0.008);
    float3 palette = 0.5 + 0.5 * cos(6.28318 * (hue + float3(0.0, 0.33, 0.67)));
    float3 deep = mix(float3(0.015, 0.008, 0.045), float3(0.020, 0.050, 0.055), uv.y);
    float3 color = deep + palette * curtain * (0.16 + 0.25 * flow2) * uIntensity;
    color += uAccent * (0.08 + 0.10 * flow) * (1.0 - uv.y) * uIntensity;
    float starCell = grealHash(floor(fragCoord / 3.0));
    float stars = step(0.997, starCell) * (0.45 + 0.55 * sin(uTime * 1.7 + starCell * 40.0));
    color += float3(stars) * 0.08;
    color += (grealHash(fragCoord + uTime) - 0.5) * 0.025;
    // Призматические каустики и хроматические рёбра: ещё один слой живого света.
    float caustic = sin((uv.x + flow * 0.20) * 19.0 + uTime * 0.34) *
                    sin((uv.y - flow2 * 0.16) * 13.0 - uTime * 0.27);
    caustic = pow(max(caustic, 0.0), 3.0);
    float3 chroma = float3(palette.r * 0.55, palette.g * 0.85, palette.b);
    color += chroma * caustic * 0.075 * uIntensity;
    float prism = smoothstep(0.72, 0.98, sin(uv.x * 7.0 + flow2 * 3.0 + uTime * 0.09) * 0.5 + 0.5);
    color += uAccent * prism * 0.035 * uIntensity;
    float vignette = smoothstep(1.05, 0.25, distance(uv, float2(0.5, 0.46)));
    color *= 0.72 + 0.28 * vignette;
    return half4(color, 1.0);
}
"""
