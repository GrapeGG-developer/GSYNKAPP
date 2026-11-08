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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsynk.app.core.Blade

/**
 * Чистый Blade Dashboard без GREAL-авроры и XMB-волн.
 *
 * Тёмно-зелёный градиент, angled «лезвия», белые иконки, лаймовая рамка
 * фокуса и мягкий световой пробег. Всё рисуется Canvas/Box без серых линий,
 * кругов кассеты и внешних картинок.
 */
object BladePalette {
    val Lime = Color(Blade.LIME)
    val Green = Color(Blade.GREEN)
    val Deep = Color(Blade.DEEP)
    val Ink = Color(Blade.INK)
    val White = Color.White
    val DimWhite = Color.White.copy(alpha = 0.68f)
}

@Composable
fun BladeBackground(
    modifier: Modifier = Modifier,
    speedScale: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "bladeSweep")
    val safeSpeed = speedScale.coerceIn(0.5f, 2f)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((11_000f / safeSpeed).toInt().coerceIn(4_000, 20_000), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bladePhase"
    )
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF061A0E), BladePalette.Deep, BladePalette.Ink)
            ),
            size = size
        )
        val count = 5
        val width = size.width / count
        for (index in 0 until count) {
            val left = index * width
            val skew = width * 0.16f
            val top = size.height * (0.04f + 0.018f * index)
            val bottom = size.height * (0.98f - 0.018f * index)
            val path = Path().apply {
                moveTo(left + skew, top)
                lineTo(left + width - skew * 0.25f, top)
                lineTo(left + width + skew * 0.25f, bottom)
                lineTo(left - skew * 0.25f, bottom)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BladePalette.Green.copy(alpha = 0.10f + index * 0.012f),
                        Color(0xFF0A2F18).copy(alpha = 0.18f),
                        BladePalette.Ink.copy(alpha = 0.34f)
                    ),
                    startY = top,
                    endY = bottom
                )
            )
        }
        for (layer in 0 until 3) {
            val sweep = Blade.sweepHeight(phase, layer * 0.31f, layer)
            val top = size.height * (0.16f + layer * 0.29f) + sweep * size.height * 0.035f
            val height = size.height * (0.08f + layer * 0.012f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        BladePalette.Lime.copy(alpha = 0.045f + layer * 0.012f),
                        Color.Transparent
                    ),
                    startX = -size.width * 0.1f,
                    endX = size.width * 1.1f
                ),
                topLeft = Offset(-size.width * 0.08f, top),
                size = Size(size.width * 1.16f, height),
                cornerRadius = CornerRadius(height / 2f, height / 2f)
            )
        }
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(BladePalette.Lime.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.50f, size.height * 0.12f),
                radius = size.maxDimension * 0.78f
            ),
            size = size
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                startY = size.height * 0.62f,
                endY = size.height
            ),
            size = size
        )
    }
}

@Composable
fun BladeFocusFrame(
    modifier: Modifier = Modifier,
    strength: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "bladeFocus")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bladeFocusPhase"
    )
    Canvas(modifier) {
        val power = strength.coerceIn(0f, 1f)
        if (power <= 0.01f || size.width <= 2f || size.height <= 2f) return@Canvas
        val pulse = Blade.focusPulse(phase)
        val inset = 1.5.dp.toPx()
        val corner = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        val frameSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        drawRoundRect(
            color = BladePalette.Lime.copy(alpha = (0.22f + 0.42f * pulse) * power),
            topLeft = Offset(inset, inset),
            size = frameSize,
            cornerRadius = corner,
            style = Stroke(width = (1.4f + pulse * 1.2f).dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = (0.10f + 0.18f * pulse) * power),
            topLeft = Offset(inset * 2.2f, inset * 2.2f),
            size = Size(frameSize.width - inset * 2.4f, frameSize.height - inset * 2.4f),
            cornerRadius = corner,
            style = Stroke(width = 0.7.dp.toPx())
        )
    }
}

@Composable
fun BladeSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    focused: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.08f),
                        BladePalette.Green.copy(alpha = if (focused) 0.28f else 0.14f),
                        BladePalette.Ink.copy(alpha = 0.46f)
                    )
                )
            )
            .border(
                1.dp,
                if (focused) BladePalette.Lime.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.16f),
                shape
            )
    ) {
        BladeFocusFrame(Modifier.matchParentSize(), strength = if (focused) 0.85f else 0.10f)
        content()
    }
}

@Composable
fun BladeControlOrb(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = BladePalette.Lime,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        (if (danger) Color(0xFF7A1B1B) else accent).copy(alpha = 0.32f),
                        BladePalette.Ink.copy(alpha = 0.52f)
                    )
                )
            )
            .border(1.dp, if (danger) Color(0xFFFF5A5A) else accent.copy(alpha = 0.72f), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        BladeFocusFrame(Modifier.matchParentSize(), strength = 0.22f)
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BladeIcon(
    iconRes: Int,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        BladeFocusFrame(Modifier.matchParentSize(), strength = if (active) 0.65f else 0.12f)
        Icon(
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = null,
            tint = if (active) Color.White else BladePalette.DimWhite,
            modifier = Modifier.fillMaxSize(0.54f)
        )
    }
}
