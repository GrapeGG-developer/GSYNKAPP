package com.gsynk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.GrealPlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Бесконечная 3D-графика GREAL без кассетных линий и кругов.
 *
 * Все эффекты рисуются одним Canvas на слой и читают общий часовой State прямо
 * в фазе отрисовки. Поэтому бесконечное вращение не вызывает рекомпозицию
 * каждой плитки, а число осколков, пузырей и рябей подстраивается под телефон.
 */

private val INFINITE_PALETTE = listOf(
    Greal.AuroraViolet,
    Greal.AuroraCyan,
    Greal.AuroraGreen,
    Greal.AuroraPink,
    Greal.AuroraAmber
)

private data class GrealShard(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val depth: Float,
    val rotation: Float,
    val speed: Float,
    val phase: Float,
    val tone: Int
)

private fun clampTo(value: Float, min: Float, max: Float): Float =
    if (max < min) min else value.coerceIn(min, max)

/** Параллаксные объёмные осколки света: новый слой глубины под лентой «Пуска». */
@Composable
fun GrealParallaxShards(
    modifier: Modifier = Modifier,
    accent: Color = Greal.AuroraViolet
) {
    val skin = LocalSkinValues.current
    val effects = LocalGiouiEffects.current
    val budget = GrealPlay.effectBudget(skin.level, effects == Gioui.Effects.FULL)
    val phaseState = rememberPrgPhaseState(0x5EED)
    val shards = remember(budget) {
        List(budget) { index ->
            GrealShard(
                x = GrealPlay.hash01(9137, index, 11),
                y = GrealPlay.hash01(9137, index, 23),
                width = 0.10f + GrealPlay.hash01(9137, index, 31) * 0.19f,
                height = 0.018f + GrealPlay.hash01(9137, index, 41) * 0.045f,
                depth = 0.35f + GrealPlay.hash01(9137, index, 53) * 0.65f,
                rotation = -18f + GrealPlay.hash01(9137, index, 61) * 36f,
                speed = 0.35f + GrealPlay.hash01(9137, index, 71) * 0.95f,
                phase = GrealPlay.hash01(9137, index, 83),
                tone = index % INFINITE_PALETTE.size
            )
        }
    }
    Canvas(modifier) {
        if (size.width <= 4f || size.height <= 4f) return@Canvas
        val phase = phaseState.value
        val minDim = size.minDimension
        shards.forEach { shard ->
            val w = (minDim * shard.width * shard.depth).coerceAtMost(size.width * 0.72f)
            val h = (minDim * shard.height * shard.depth).coerceAtMost(size.height * 0.22f)
            val drift = sin(phase * 2f * PI.toFloat() * shard.speed + shard.phase * 6.2f) *
                minDim * 0.035f * shard.depth
            val left = clampTo(size.width * shard.x + drift - w / 2f, 2f, size.width - w - 2f)
            val top = clampTo(size.height * shard.y + cos(phase * 4.1f + shard.phase) * minDim * 0.02f - h / 2f, 2f, size.height - h - 2f)
            val color = lerp(INFINITE_PALETTE[shard.tone], accent, 0.18f)
            val alpha = (0.045f + 0.085f * shard.depth) * (if (effects == Gioui.Effects.PLAIN) 0.45f else 1f)
            val center = Offset(left + w / 2f, top + h / 2f)
            rotate(shard.rotation + sin(phase * 2f + shard.phase) * 3f, center) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = alpha * 0.44f),
                            color.copy(alpha = alpha * 0.55f),
                            Color.Transparent
                        ),
                        start = Offset(left, top + h),
                        end = Offset(left + w, top)
                    ),
                    topLeft = Offset(left + w * 0.06f, top + h * 0.52f),
                    size = Size(w * 0.94f, h * 0.72f),
                    cornerRadius = CornerRadius(h * 0.5f, h * 0.5f)
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = alpha * 0.50f),
                            color.copy(alpha = alpha),
                            Color.Transparent
                        ),
                        start = Offset(left, top),
                        end = Offset(left + w, top + h)
                    ),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h * 0.5f, h * 0.5f)
                )
            }
        }
    }
}

/**
 * Маленькое бесконечное кольцо из 3D-плиток вокруг сферы «Пуска».
 * Название и иконка не крутятся, зато орбита never останавливается.
 */
@Composable
fun GrealInfiniteOrbit(
    modifier: Modifier = Modifier,
    accent: Color,
    seed: Int,
    active: Boolean = false
) {
    val phaseState = rememberPrgPhaseState(seed)
    Canvas(modifier) {
        if (size.width <= 6f || size.height <= 6f) return@Canvas
        val angle = phaseState.value * 360f
        val poses = GrealPlay.carouselPoses(8, angle, 12f)
        val minDim = size.minDimension
        val radiusX = minDim * 0.38f
        val radiusY = minDim * 0.21f
        val tileW = minDim * 0.15f
        val tileH = tileW * 0.62f
        val power = if (active) 1f else 0.76f
        poses.forEach { pose ->
            val color = lerp(accent, INFINITE_PALETTE[pose.index % INFINITE_PALETTE.size], 0.45f)
            val center = Offset(
                size.width / 2f + pose.x * radiusX,
                size.height / 2f + pose.y * radiusY
            )
            val w = tileW * pose.scale
            val h = tileH * pose.scale
            val left = clampTo(center.x - w / 2f, 0f, (size.width - w).coerceAtLeast(0f))
            val top = clampTo(center.y - h / 2f, 0f, (size.height - h).coerceAtLeast(0f))
            rotate(pose.rotation, center) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Color.Black.copy(alpha = 0.20f * pose.alpha * power), Color.Transparent)
                    ),
                    topLeft = Offset(left + w * 0.07f, top + h * 0.58f),
                    size = Size(w * 0.90f, h * 0.48f),
                    cornerRadius = CornerRadius(h * 0.45f, h * 0.45f)
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.55f * pose.alpha * power),
                            color.copy(alpha = 0.82f * pose.alpha * power),
                            Color.Black.copy(alpha = 0.34f * pose.alpha * power)
                        ),
                        start = Offset(left, top),
                        end = Offset(left + w, top + h)
                    ),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h * 0.42f, h * 0.42f)
                )
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.52f * pose.alpha * power), Color.Transparent),
                        center = Offset(left + w * 0.28f, top + h * 0.26f),
                        radius = w * 0.42f
                    ),
                    topLeft = Offset(left + w * 0.10f, top + h * 0.12f),
                    size = Size(w * 0.80f, h * 0.76f),
                    cornerRadius = CornerRadius(h * 0.38f, h * 0.38f)
                )
            }
        }
    }
}

/** Главная бесконечная карусель антистресса: 3D-плитки уходят вглубь и возвращаются без конца. */
@Composable
fun GrealInfiniteCarousel(
    modifier: Modifier = Modifier,
    angle: State<Float>,
    tilt: State<Float>,
    accent: Color = Greal.AuroraViolet,
    count: Int = GrealPlay.CAROUSEL_TILES
) {
    val shimmer = rememberPrgPhaseState(0xC0FFEE)
    Canvas(modifier) {
        if (size.width <= 8f || size.height <= 8f) return@Canvas
        val minDim = size.minDimension
        val spin = angle.value
        val tiltDegrees = tilt.value
        val phase = shimmer.value

        // Мягкая бесконечная лента под кольцом: только цветные объёмные плиты.
        val ribbonHeight = minDim * 0.16f
        val ribbonWidth = size.width * 0.94f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    accent.copy(alpha = 0.13f),
                    Color.White.copy(alpha = 0.09f),
                    Greal.AuroraCyan.copy(alpha = 0.11f),
                    Color.Transparent
                ),
                start = Offset((size.width - ribbonWidth) / 2f, size.height * 0.50f),
                end = Offset((size.width + ribbonWidth) / 2f, size.height * 0.50f + ribbonHeight)
            ),
            topLeft = Offset((size.width - ribbonWidth) / 2f, size.height * 0.50f - ribbonHeight / 2f),
            size = Size(ribbonWidth, ribbonHeight),
            cornerRadius = CornerRadius(ribbonHeight / 2f, ribbonHeight / 2f)
        )

        val poses = GrealPlay.carouselPoses(count, spin, tiltDegrees)
        val tileW = minDim * 0.30f
        val tileH = tileW * 0.70f
        poses.forEach { pose ->
            val color = lerp(accent, INFINITE_PALETTE[pose.index % INFINITE_PALETTE.size], 0.40f)
            val center = Offset(
                size.width / 2f + pose.x * size.width * 0.40f,
                size.height / 2f + pose.y * size.height * 0.32f
            )
            drawSpinTile(
                pose = pose,
                center = center,
                width = tileW * pose.scale,
                height = tileH * pose.scale,
                color = color,
                accent = accent,
                shimmer = phase
            )
        }
    }
}

private fun DrawScope.drawSpinTile(
    pose: GrealPlay.TilePose,
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
    accent: Color,
    shimmer: Float
) {
    if (width <= 1f || height <= 1f) return
    val left = clampTo(center.x - width / 2f, 4f, (size.width - width - 4f).coerceAtLeast(4f))
    val top = clampTo(center.y - height / 2f, 4f, (size.height - height - 4f).coerceAtLeast(4f))
    val alpha = pose.alpha
    val corner = CornerRadius(height * 0.24f, height * 0.24f)
    rotate(pose.rotation, center) {
        // Нижняя физическая грань и тень.
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.Black.copy(alpha = 0.34f * alpha), color.copy(alpha = 0.20f * alpha), Color.Transparent)
            ),
            topLeft = Offset(left + width * 0.04f, top + height * 0.56f),
            size = Size(width * 0.94f, height * 0.52f),
            cornerRadius = corner
        )
        // Стеклянная лицевая плита.
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.50f * alpha),
                    color.copy(alpha = 0.86f * alpha),
                    Color.Black.copy(alpha = 0.36f * alpha)
                ),
                start = Offset(left, top),
                end = Offset(left + width, top + height)
            ),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = corner
        )
        // Слоёная сфера внутри плитки.
        val orbRadius = minOf(width, height) * 0.36f
        val orbCenter = Offset(left + width * 0.50f, top + height * 0.48f)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.92f * alpha),
                    lerp(color, Color.White, 0.42f).copy(alpha = 0.88f * alpha),
                    color.copy(alpha = 0.80f * alpha),
                    Color.Black.copy(alpha = 0.46f * alpha)
                ),
                center = Offset(orbCenter.x - orbRadius * 0.28f, orbCenter.y - orbRadius * 0.30f),
                radius = orbRadius * 1.08f
            ),
            topLeft = Offset(orbCenter.x - orbRadius, orbCenter.y - orbRadius),
            size = Size(orbRadius * 2f, orbRadius * 2f)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f * alpha), Color.Transparent),
                center = Offset(orbCenter.x - orbRadius * 0.34f, orbCenter.y - orbRadius * 0.44f),
                radius = orbRadius * 0.70f
            ),
            topLeft = Offset(orbCenter.x - orbRadius * 0.58f, orbCenter.y - orbRadius * 0.58f),
            size = Size(orbRadius * 1.16f, orbRadius * 0.72f)
        )
        // Краевой блик: короткий, случайный по фазе и всегда у кромки.
        val glint = 0.5f + 0.5f * sin(shimmer * 2f * PI.toFloat() + pose.index)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = (0.22f + 0.28f * glint) * alpha), Color.Transparent)
            ),
            topLeft = Offset(left + width * 0.06f, top + height * 0.06f),
            size = Size(width * 0.88f, height * 0.10f),
            cornerRadius = CornerRadius(height * 0.06f, height * 0.06f)
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, accent.copy(alpha = 0.26f * alpha), Color.Transparent)
            ),
            topLeft = Offset(left + width * 0.08f, top + height * 0.88f),
            size = Size(width * 0.84f, height * 0.08f),
            cornerRadius = CornerRadius(height * 0.05f, height * 0.05f)
        )
    }
}

/** Объёмные пузыри: касание лопает сферу, через секунду она возвращается. */
@Composable
fun GrealBubbleField(
    modifier: Modifier = Modifier,
    seconds: State<Float>,
    popped: State<Map<Int, Float>>,
    accent: Color = Greal.AuroraViolet,
    seed: Int = 7331,
    count: Int = GrealPlay.BUBBLE_COUNT
) {
    Canvas(modifier) {
        if (size.width <= 8f || size.height <= 8f) return@Canvas
        val now = seconds.value
        val minDim = size.minDimension
        for (index in 0 until count) {
            val pose = GrealPlay.bubblePose(index, count, now, seed)
            val age = popped.value[index]?.let { now - it } ?: -1f
            val scale = GrealPlay.bubblePopScale(age)
            val alpha = GrealPlay.bubblePopAlpha(age)
            val radius = pose.radius * minDim * scale
            val center = Offset(pose.x * size.width, pose.y * size.height)
            val color = lerp(accent, INFINITE_PALETTE[pose.tone], 0.48f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.30f * alpha), Color.Transparent),
                    center = Offset(center.x, center.y + radius * 0.84f),
                    radius = radius * 1.06f
                ),
                topLeft = Offset(center.x - radius, center.y + radius * 0.46f),
                size = Size(radius * 2f, radius * 0.66f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f * alpha),
                        lerp(color, Color.White, 0.36f).copy(alpha = 0.90f * alpha),
                        color.copy(alpha = 0.86f * alpha),
                        Color.Black.copy(alpha = 0.44f * alpha)
                    ),
                    center = Offset(center.x - radius * 0.30f, center.y - radius * 0.34f),
                    radius = radius * 1.10f
                ),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.70f * alpha), Color.Transparent),
                    center = Offset(center.x - radius * 0.36f, center.y - radius * 0.46f),
                    radius = radius * 0.46f
                ),
                topLeft = Offset(center.x - radius * 0.70f, center.y - radius * 0.70f),
                size = Size(radius * 0.92f, radius * 0.52f)
            )
            if (age >= 0f && age < GrealPlay.POP_LIFETIME) {
                val burst = GrealPlay.rippleRadius(age, radius * 2.2f, GrealPlay.POP_LIFETIME)
                drawCircle(
                    color = Color.White.copy(alpha = GrealPlay.rippleAlpha(age, GrealPlay.POP_LIFETIME) * 0.42f),
                    radius = burst,
                    center = center,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

/** Стеклянные волны для режима «рябь»: мягкий фон без контурных линий. */
@Composable
fun GrealRippleField(
    modifier: Modifier = Modifier,
    seconds: State<Float>,
    accent: Color = Greal.AuroraViolet
) {
    val phaseState = rememberPrgPhaseState(0x9171)
    Canvas(modifier) {
        if (size.width <= 8f || size.height <= 8f) return@Canvas
        val phase = phaseState.value
        val now = seconds.value
        val bandHeight = size.height * 0.12f
        for (index in 0 until 5) {
            val color = lerp(accent, INFINITE_PALETTE[index], 0.55f)
            val drift = sin(phase * 2f * PI.toFloat() + index * 1.3f) * size.width * 0.05f
            val top = size.height * (0.14f + index * 0.17f) + cos(now * 0.42f + index) * size.height * 0.025f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.06f),
                        color.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    startX = -size.width * 0.10f + drift,
                    endX = size.width * 1.10f + drift
                ),
                topLeft = Offset(-size.width * 0.08f, top),
                size = Size(size.width * 1.16f, bandHeight),
                cornerRadius = CornerRadius(bandHeight * 0.5f, bandHeight * 0.5f)
            )
        }
    }
}

/** Цветные круги от касаний. Это антистресс-рябь, а не серые круги кассеты. */
@Composable
fun GrealRippleOverlay(
    modifier: Modifier = Modifier,
    seconds: State<Float>,
    ripples: State<List<GrealPlay.Ripple>>,
    accent: Color = Greal.AuroraViolet
) {
    Canvas(modifier) {
        val now = seconds.value
        val maxRadius = size.minDimension * 0.30f
        ripples.value.forEach { ripple ->
            val age = now - ripple.born
            val alpha = GrealPlay.rippleAlpha(age)
            if (alpha <= 0f) return@forEach
            val radius = GrealPlay.rippleRadius(age, maxRadius)
            val center = Offset(ripple.x * size.width, ripple.y * size.height)
            val color = lerp(accent, INFINITE_PALETTE[ripple.tone % INFINITE_PALETTE.size], 0.45f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha * 0.16f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.46f),
                radius = radius,
                center = center,
                style = Stroke(width = (1.2f + 2.6f * alpha).dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
