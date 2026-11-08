package com.gsynk.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsynk.app.core.Script
import kotlin.math.PI
import kotlin.math.sin

/**
 * GSYNK Script UI — постоянная дизайн-система.
 *
 * Плоские 2D-градиенты, чёткая непрозрачная палитра, асимметричные
 * «изометричные» скругления, нумерованные строки и собственные глифы.
 * Здесь нет стекла, размытия, AGSL-шейдеров, 3D-перспективы, серых линий,
 * кассетных кругов и бликов-полос по центру.
 */
object ScriptPalette {
    val Background = Color(Script.INK)
    val Surface = Color(Script.SURFACE)
    val SurfaceAlt = Color(Script.SURFACE_ALT)
    val Accent = Color(Script.ACCENT)
    val Accent2 = Color(Script.ACCENT_2)
    val Accent3 = Color(Script.ACCENT_3)
    val Success = Color(Script.SUCCESS)
    val Danger = Color(Script.DANGER)
    val Text = Color(Script.TEXT)
    val Muted = Color(Script.MUTED)
    val Border = Color(Script.BORDER)

    // Совместимые имена для единого исходного кода общих компонентов.
    val Lime = Accent
    val Green = SurfaceAlt
    val Deep = Surface
    val Ink = Background
    val White = Text
    val DimWhite = Muted
}

object ScriptShapes {
    val isoCard = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 7.dp,
        bottomEnd = 22.dp,
        bottomStart = 7.dp
    )
    val isoPanel = RoundedCornerShape(
        topStart = 26.dp,
        topEnd = 8.dp,
        bottomEnd = 26.dp,
        bottomStart = 8.dp
    )
    val isoButton = RoundedCornerShape(
        topStart = 14.dp,
        topEnd = 5.dp,
        bottomEnd = 14.dp,
        bottomStart = 5.dp
    )
    val isoChip = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 4.dp,
        bottomEnd = 12.dp,
        bottomStart = 4.dp
    )
}

@Composable
fun ScriptBackground(
    modifier: Modifier = Modifier,
    speedScale: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "scriptSweep")
    val safeSpeed = speedScale.coerceIn(0.5f, 2f)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((12_000f / safeSpeed).toInt().coerceIn(5_000, 24_000), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scriptPhase"
    )
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(ScriptPalette.Background, ScriptPalette.Surface, ScriptPalette.Background)
            ),
            size = size
        )
        val bandHeight = size.height * 0.16f
        for (index in 0 until 4) {
            val drift = sin(phase * 2f * PI.toFloat() + index * 1.4f) * size.width * 0.045f
            val top = size.height * (0.08f + index * 0.23f) + drift * 0.18f
            val color = when (index % 3) {
                0 -> ScriptPalette.Accent
                1 -> ScriptPalette.Accent2
                else -> ScriptPalette.Accent3
            }
            rotate(-6f + index * 2.5f, Offset(size.width / 2f, top + bandHeight / 2f)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            color.copy(alpha = 0.10f),
                            ScriptPalette.SurfaceAlt.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        start = Offset(-size.width * 0.08f, top),
                        end = Offset(size.width * 1.08f, top + bandHeight)
                    ),
                    topLeft = Offset(-size.width * 0.06f, top),
                    size = Size(size.width * 1.12f, bandHeight),
                    cornerRadius = CornerRadius(bandHeight / 2f, bandHeight / 2f)
                )
            }
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, ScriptPalette.Background.copy(alpha = 0.34f)),
                startY = size.height * 0.72f,
                endY = size.height
            ),
            topLeft = Offset(0f, size.height * 0.72f),
            size = Size(size.width, size.height * 0.28f)
        )
    }
}

@Composable
fun ScriptFocusBar(
    modifier: Modifier = Modifier,
    strength: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "scriptFocus")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scriptFocusPhase"
    )
    Canvas(modifier) {
        val power = strength.coerceIn(0f, 1f)
        if (power <= 0.01f || size.width <= 2f || size.height <= 2f) return@Canvas
        val pulse = Script.focusPulse(phase)
        val barWidth = (4.dp.toPx() + pulse * 1.5.dp.toPx()).coerceAtMost(size.width * 0.10f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(ScriptPalette.Accent, ScriptPalette.Accent2)
            ),
            topLeft = Offset(0f, size.height * 0.10f),
            size = Size(barWidth, size.height * 0.80f),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(ScriptPalette.Accent.copy(alpha = 0.72f * power), Color.Transparent)
            ),
            topLeft = Offset(barWidth, size.height - 3.dp.toPx()),
            size = Size(size.width * (0.36f + pulse * 0.16f), 3.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

@Composable
fun ScriptSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ScriptShapes.isoCard,
    focused: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(ScriptPalette.Surface, ScriptPalette.SurfaceAlt, ScriptPalette.Surface)
                )
            )
            .border(
                1.dp,
                if (focused) ScriptPalette.Accent else ScriptPalette.Border,
                shape
            )
    ) {
        if (focused) ScriptFocusBar(Modifier.matchParentSize(), strength = 0.42f)
        content()
    }
}

@Composable
fun ScriptControlButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = ScriptPalette.Accent,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val shape = ScriptShapes.isoButton
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        ScriptPalette.SurfaceAlt,
                        if (danger) ScriptPalette.Danger else accent,
                        ScriptPalette.Surface
                    )
                )
            )
            .border(1.dp, if (danger) ScriptPalette.Danger else accent, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        ScriptFocusBar(Modifier.matchParentSize(), strength = 0.16f)
        Text(
            text = text,
            color = ScriptPalette.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ScriptIcon(
    iconRes: Int,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (active) ScriptFocusBar(Modifier.matchParentSize(), strength = 0.30f)
        Icon(
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = null,
            tint = if (active) ScriptPalette.White else ScriptPalette.Muted,
            modifier = Modifier.fillMaxSize(0.54f)
        )
    }
}

/** Совместимый «глиф» вместо прежней 3D-сферы: плоский знак, без стекла. */
@Composable
fun ScriptGlyph(
    modifier: Modifier = Modifier,
    accent: Color,
    iconRes: Int,
    seed: Int,
    active: Boolean = false,
    iconScale: Float = 0.48f
) {
    Box(
        modifier
            .clip(ScriptShapes.isoChip)
            .background(
                Brush.linearGradient(
                    listOf(ScriptPalette.SurfaceAlt, accent, ScriptPalette.Surface)
                )
            )
            .border(1.dp, if (active) ScriptPalette.Accent else ScriptPalette.Border, ScriptShapes.isoChip),
        contentAlignment = Alignment.Center
    ) {
        if (active) ScriptFocusBar(Modifier.matchParentSize(), strength = 0.28f)
        Icon(
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = null,
            tint = ScriptPalette.White,
            modifier = Modifier.fillMaxSize(iconScale.coerceIn(0.25f, 0.72f))
        )
    }
}

/** Плоский 2D-градиент вместо SCRIPT-текстуры: без зерна, бликов и стекла. */
@Composable
fun ScriptTexture(
    modifier: Modifier = Modifier,
    accent: Color = ScriptPalette.Accent,
    seed: Int = 0,
    density: Float = 0.38f
) {
    Canvas(modifier) {
        if (size.width <= 2f || size.height <= 2f) return@Canvas
        val strength = density.coerceIn(0f, 1f)
        for (band in 0 until 3) {
            val left = size.width * (0.04f + Script.hash01(seed, band, 11) * 0.10f)
            val top = size.height * (0.12f + band * 0.27f)
            val width = size.width * (0.55f + Script.hash01(seed, band, 23) * 0.35f)
            val height = size.height * (0.09f + strength * 0.06f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.10f + strength * 0.10f),
                        ScriptPalette.SurfaceAlt.copy(alpha = 0.10f + strength * 0.10f),
                        Color.Transparent
                    ),
                    start = Offset(left, top),
                    end = Offset(left + width, top + height)
                ),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(height / 2f, height / 2f)
            )
        }
    }
}

/** Короткая цветная метка на кромке, а не стеклянный блик. */
@Composable
fun ScriptEdgeMark(
    modifier: Modifier = Modifier,
    accent: Color = ScriptPalette.Accent,
    seed: Int = 0
) {
    Canvas(modifier) {
        if (size.width <= 2f || size.height <= 2f) return@Canvas
        val edge = (Script.hash01(seed, 0, 11) * 4f).toInt().coerceIn(0, 3)
        val length = size.minDimension * (0.18f + Script.hash01(seed, 1, 23) * 0.22f)
        val thickness = 3.dp.toPx()
        when (edge) {
            0 -> drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent)),
                topLeft = Offset(size.width * 0.10f, 2.dp.toPx()),
                size = Size(length, thickness),
                cornerRadius = CornerRadius(thickness / 2f, thickness / 2f)
            )
            1 -> drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, accent, Color.Transparent)),
                topLeft = Offset(size.width - thickness - 2.dp.toPx(), size.height * 0.10f),
                size = Size(thickness, length),
                cornerRadius = CornerRadius(thickness / 2f, thickness / 2f)
            )
            2 -> drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent)),
                topLeft = Offset(size.width - length - size.width * 0.10f, size.height - thickness - 2.dp.toPx()),
                size = Size(length, thickness),
                cornerRadius = CornerRadius(thickness / 2f, thickness / 2f)
            )
            else -> drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, accent, Color.Transparent)),
                topLeft = Offset(2.dp.toPx(), size.height - length - size.height * 0.10f),
                size = Size(thickness, length),
                cornerRadius = CornerRadius(thickness / 2f, thickness / 2f)
            )
        }
    }
}

/** Плоские изометричные уголки без контурных линий и кругов. */
@Composable
fun ScriptCorners(
    modifier: Modifier = Modifier,
    accent: Color = ScriptPalette.Accent,
    strength: Float = 0.5f
) {
    Canvas(modifier) {
        val power = strength.coerceIn(0f, 1f)
        if (power <= 0.01f || size.width <= 8f || size.height <= 8f) return@Canvas
        val w = 14.dp.toPx()
        val h = 5.dp.toPx()
        val inset = 6.dp.toPx()
        val color = accent.copy(alpha = 0.55f * power)
        drawRoundRect(color, Offset(inset, inset), Size(w, h), CornerRadius(h / 2f, h / 2f))
        drawRoundRect(color, Offset(size.width - inset - w, inset), Size(w, h), CornerRadius(h / 2f, h / 2f))
        drawRoundRect(color, Offset(inset, size.height - inset - h), Size(w, h), CornerRadius(h / 2f, h / 2f))
        drawRoundRect(color, Offset(size.width - inset - w, size.height - inset - h), Size(w, h), CornerRadius(h / 2f, h / 2f))
    }
}

/** Плоская 2D-орбита из цветных скруглённых меток вместо 3D-колец. */
@Composable
fun ScriptInfiniteOrbit(
    modifier: Modifier = Modifier,
    accent: Color = ScriptPalette.Accent,
    seed: Int = 0,
    active: Boolean = false
) {
    Canvas(modifier) {
        if (size.width <= 6f || size.height <= 6f) return@Canvas
        val power = if (active) 1f else 0.7f
        for (index in 0 until 8) {
            val distance = Script.cyclicDistance(index, 0f, 8)
            val x = size.width / 2f + distance * size.width * 0.36f
            val y = size.height / 2f + sin(index * 1.3f + seed * 0.01f) * size.height * 0.28f
            val w = size.minDimension * 0.13f
            val h = size.minDimension * 0.07f
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(ScriptPalette.Accent, accent, ScriptPalette.Accent2)
                ),
                topLeft = Offset(x - w / 2f, y - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
                alpha = (0.45f + 0.35f * Script.hash01(seed, index, 31)) * power
            )
        }
    }
}

/** Плоский задний план из градиентных плит, без кассетных линий/кругов. */
@Composable
fun ScriptDepthBackdrop(
    modifier: Modifier = Modifier,
    scrollPixels: Float = 0f
) {
    Canvas(modifier) {
        if (size.width <= 4f || size.height <= 4f) return@Canvas
        val step = 170.dp.toPx().coerceAtLeast(80f)
        val shift = -(kotlin.math.abs(scrollPixels) % step)
        var index = 0
        var y = shift - step
        while (y < size.height + step) {
            val color = when (index % 3) {
                0 -> ScriptPalette.Accent
                1 -> ScriptPalette.Accent2
                else -> ScriptPalette.Accent3
            }
            val width = size.width * (0.72f + Script.hash01(3107, index, 11) * 0.18f)
            val height = 46.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.08f), ScriptPalette.SurfaceAlt.copy(alpha = 0.10f), Color.Transparent)
                ),
                topLeft = Offset((size.width - width) / 2f, y),
                size = Size(width, height),
                cornerRadius = CornerRadius(height / 2f, height / 2f)
            )
            y += step
            index++
        }
    }
}

/** Плоские 2D-осколки вместо параллаксного стекла. */
@Composable
fun ScriptShards(
    modifier: Modifier = Modifier,
    accent: Color = ScriptPalette.Accent
) {
    Canvas(modifier) {
        if (size.width <= 4f || size.height <= 4f) return@Canvas
        for (index in 0 until 10) {
            val w = size.minDimension * (0.10f + Script.hash01(7103, index, 11) * 0.18f)
            val h = size.minDimension * (0.018f + Script.hash01(7103, index, 23) * 0.04f)
            val x = Script.hash01(7103, index, 31) * (size.width - w).coerceAtLeast(1f)
            val y = Script.hash01(7103, index, 41) * (size.height - h).coerceAtLeast(1f)
            val color = when (index % 3) {
                0 -> accent
                1 -> ScriptPalette.Accent2
                else -> ScriptPalette.Accent3
            }
            rotate(-10f + Script.hash01(7103, index, 53) * 20f, Offset(x + w / 2f, y + h / 2f)) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, color.copy(alpha = 0.10f), Color.Transparent)
                    ),
                    topLeft = Offset(x, y),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
            }
        }
    }
}
