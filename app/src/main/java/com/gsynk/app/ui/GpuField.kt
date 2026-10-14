package com.gsynk.app.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.gsynk.app.core.RealtimeAi
import com.gsynk.app.core.TurboState
import kotlinx.coroutines.isActive

/**
 * Поле на видеоускорителе.
 *
 * На Android 13+ рисуется шейдером (AGSL): каждый пиксель считается на GPU
 * десятки раз за кадр, поэтому картинка живая, а нагрузка честная. Ниже — запасной
 * вариант из движущихся градиентов, он работает на любом устройстве.
 *
 * Поле само измеряет кадры в секунду и отдаёт их в [TurboState] — оттуда их берёт
 * экран нагрузки, чтобы показать, сколько вытягивает ускоритель.
 */
/** Программа поля: шейдер и кисть вместе, создаются только на Android 13+. */
private class GpuProgram(val shader: RuntimeShader, val brush: ShaderBrush)

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
private fun createGpuProgram(): GpuProgram {
    val shader = RuntimeShader(AGSL)
    return GpuProgram(shader, ShaderBrush(shader))
}

/** Все обращения к RuntimeShader живут здесь: система гарантирует Android 13+. */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShaderField(
    shader: RuntimeShader,
    brush: ShaderBrush,
    time: Float,
    iterations: Int,
    mode: Float
) {
    shader.setFloatUniform("uSize", size.width, size.height)
    shader.setFloatUniform("uTime", time)
    shader.setFloatUniform("uIterations", iterations.toFloat())
    shader.setFloatUniform("uMode", mode)
    drawRect(brush = brush, size = size)
}

/**
 * Доступен ли настоящий шейдерный стенд: платформа Android 13+ (нужны
 * RuntimeShader и рендер-эффекты) и разрешённая функция ускорителя в профиле.
 */
fun gpuFieldAvailable(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        com.gsynk.app.core.DeviceProfile
            .feature(com.gsynk.app.core.DeviceProfile.detect(context), com.gsynk.app.core.DeviceProfile.Feature.GPU)

@Composable
fun GpuField(
    modifier: Modifier = Modifier,
    iterations: Int = 44,
    reportFps: Boolean = false,
    speed: Float = 1f
) {
    // Программа шейдера создаётся один раз: и объект шейдера, и кисть.
    val program: GpuProgram? = remember(iterations) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { createGpuProgram() }.getOrNull()
        } else {
            null
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    var frames by remember { mutableFloatStateOf(0f) }

    // время и подсчёт кадров: одна корутина, кадр в кадр
    LaunchedEffect(reportFps, program != null) {
        var previous = 0L
        var lastReport = 0L
        var count = 0
        while (isActive) {
            withFrameNanos { now ->
                if (previous == 0L) {
                    previous = now
                    lastReport = now
                }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                time += seconds * speed
                count++
                if (now - lastReport > 1_000_000_000L) {
                    frames = count / ((now - lastReport) / 1_000_000_000f)
                    if (reportFps) TurboState.gpuFps.value = frames
                    count = 0
                    lastReport = now
                }
            }
        }
    }

    Canvas(modifier) {
        val mode = TurboState.aiMode.value.toFloat()
        if (program != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                drawShaderField(program.shader, program.brush, time, iterations, mode)
            }.onFailure {
                drawFallback(size, time, mode)
            }
        } else {
            drawFallback(size, time, mode)
        }
    }
}

/** Запасной вариант: два движущихся градиента. Работает везде и всегда. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFallback(
    size: Size,
    time: Float,
    mode: Float
) {
    val shift = (kotlin.math.sin(time * 0.6f) + 1f) / 2f
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF150E28),
                Color(0xFF2A1550).copy(alpha = 0.9f),
                Color(0xFF0A1A16)
            ),
            start = Offset(size.width * shift, 0f),
            end = Offset(size.width * (1f - shift), size.height)
        ),
        size = size
    )
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                RealtimeAi.modeName(mode.toInt()).let { Color(0xFF7C3AED) }.copy(alpha = 0.35f),
                Color.Transparent,
                Color(0xFF22C55E).copy(alpha = 0.28f)
            ),
            start = Offset(0f, size.height * shift),
            end = Offset(size.width, size.height * (0.4f + 0.4f * shift))
        ),
        size = size
    )
}

/**
 * Шейдер поля: сорок восемь слоёв мягких пятен на пиксель. Считается на GPU,
 * цикл фиксированный — так шейдер гарантированно компилируется на всех драйверах.
 */
private const val AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float uIterations;
uniform float uMode;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float3 color = float3(0.03, 0.02, 0.06);
    for (int i = 0; i < 48; i++) {
        float fi = float(i);
        float2 wave = float2(sin(uTime * 0.21 + fi * 0.13), cos(uTime * 0.17 + fi * 0.19)) * 0.18;
        float2 p = uv + wave;
        float2 center = float2(
            0.5 + 0.32 * sin(uTime * 0.11 + fi * 0.29 + uMode),
            0.5 + 0.32 * cos(uTime * 0.13 + fi * 0.23 + uMode * 0.7)
        );
        float d = length(p - center);
        float3 tint = float3(
            0.30 + 0.25 * sin(fi * 0.7 + uMode),
            0.14 + 0.22 * cos(fi * 0.5 + uTime * 0.3),
            0.55 + 0.35 * sin(fi * 0.3 + uMode * 2.0)
        );
        float weight = 0.14 * (uIterations / 48.0);
        color += tint * weight / (d * 34.0 + 1.0);
    }
    color = clamp(color, 0.0, 1.0);
    return half4(half3(color), 1.0);
}
"""
