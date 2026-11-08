package com.gsynk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.Xmb
import kotlin.math.PI
import kotlin.math.sin

/**
 * XMB-слой поверх GREAL.
 *
 * Вместо серых направляющих линий PS3-подобные волны собраны из цветных
 * объёмных лент: тот же ритм и параллакс, но в физической палитре GREAL.
 * Один общий часовой State и один Canvas на всю сцену.
 */
@Composable
fun XmbWaveField(
    modifier: Modifier = Modifier,
    accent: Color = Greal.AuroraViolet
) {
    val skin = LocalSkinValues.current
    val effects = LocalGiouiEffects.current
    val layers = Xmb.waveLayers(skin.level, effects == Gioui.Effects.FULL)
    val phaseState = rememberPrgPhaseState(0x58D)
    val palette = listOf(
        Greal.AuroraViolet,
        Greal.AuroraCyan,
        Greal.AuroraGreen,
        Greal.AuroraPink,
        Greal.AuroraAmber
    )
    Canvas(modifier) {
        if (size.width <= 4f || size.height <= 4f) return@Canvas
        val phase = phaseState.value
        val strength = if (effects == Gioui.Effects.PLAIN) 0.45f else 1f
        for (layer in 0 until layers) {
            val t = layer / maxOf(1, layers - 1).toFloat()
            val drift = Xmb.waveHeight(phase, layer * 0.37f, layer)
            val top = size.height * (0.14f + t * 0.68f) + drift * size.height * 0.026f
            val height = size.height * (0.055f + 0.025f * t)
            val color = lerp(accent, palette[layer % palette.size], 0.48f)
            val alpha = (0.05f + 0.075f * (1f - t)) * strength
            val tilt = -2.5f + sin(phase * 2f * PI.toFloat() + layer) * 2.2f
            val center = Offset(size.width / 2f, top + height / 2f)
            rotate(tilt, center) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            color.copy(alpha = alpha),
                            Color.White.copy(alpha = alpha * 0.42f),
                            color.copy(alpha = alpha * 0.72f),
                            Color.Transparent
                        ),
                        startX = -size.width * 0.08f,
                        endX = size.width * 1.08f
                    ),
                    topLeft = Offset(-size.width * 0.08f, top),
                    size = Size(size.width * 1.16f, height),
                    cornerRadius = CornerRadius(height / 2f, height / 2f)
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = alpha * 0.22f), Color.Transparent),
                        startY = top + height * 0.55f,
                        endY = top + height * 1.25f
                    ),
                    topLeft = Offset(-size.width * 0.04f, top + height * 0.58f),
                    size = Size(size.width * 1.08f, height * 0.50f),
                    cornerRadius = CornerRadius(height * 0.4f, height * 0.4f)
                )
            }
        }
    }
}

/** Световой столб фокуса XMB: объёмная цветная лента, а не контурная линия. */
@Composable
fun XmbFocusBeam(
    modifier: Modifier = Modifier,
    accent: Color = Greal.AuroraViolet
) {
    val phaseState = rememberPrgPhaseState(0x58E)
    Canvas(modifier) {
        if (size.width <= 2f || size.height <= 2f) return@Canvas
        val phase = phaseState.value
        val pulse = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
        val width = (3.dp.toPx() + pulse * 1.4.dp.toPx()).coerceAtMost(size.width * 0.10f)
        val left = (size.width - width) / 2f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.42f + pulse * 0.22f),
                    accent.copy(alpha = 0.30f + pulse * 0.20f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height
            ),
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = CornerRadius(width / 2f, width / 2f)
        )
    }
}
