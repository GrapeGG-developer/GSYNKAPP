package com.gsynk.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

/**
 * Чистая геометрия Blade Dashboard.
 *
 * Классический Xbox 360 Blades: горизонтальные вкладки-лезвия, вертикальный
 * список функций внутри выбранного лезвия, один светящийся фокус. Математика
 * непрерывной фазы и инерции вынесена сюда, чтобы UI мог плавно доводить
 * лезвия без Android/Compose-зависимостей.
 */
object Blade {
    const val VERSION = "GSYNK Blades — Xbox 360 Blade Dashboard"
    const val TAB_STEP_DP = 88
    const val ITEM_STEP_DP = 64
    const val VISIBLE_ITEMS = 7
    const val GESTURE_SLOP_DP = 12
    const val LIME = 0xFFB6E24AL
    const val GREEN = 0xFF1E7A32L
    const val DEEP = 0xFF03140BL
    const val INK = 0xFF020805L

    private fun finite(value: Float, fallback: Float = 0f): Float =
        if (value.isNaN() || value.isInfinite()) fallback else value

    private fun smoothstep01(value: Float): Float {
        val t = finite(value).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(from: Float, to: Float, progress: Float): Float =
        from + (to - from) * smoothstep01(progress)

    fun wrap(index: Int, size: Int): Int {
        if (size <= 0) return 0
        return ((index % size) + size) % size
    }

    fun move(current: Int, delta: Int, size: Int): Int = wrap(current + delta, size)

    /** Нормализует фазу в [0, size), корректно обрабатывая отрицательные значения. */
    fun normalizePhase(phase: Float, size: Int): Float {
        if (size <= 0) return 0f
        val value = finite(phase)
        val sizeF = size.toFloat()
        return value - floor(value / sizeF) * sizeF
    }

    /** Кратчайшее знаковое расстояние элемента до фокуса на кольце вкладок/пунктов. */
    fun cyclicDistance(index: Int, phase: Float, size: Int): Float {
        if (size <= 0) return 0f
        val sizeF = size.toFloat()
        val normalized = normalizePhase(phase, size)
        var distance = index - normalized
        if (distance > sizeF / 2f) distance -= sizeF
        if (distance < -sizeF / 2f) distance += sizeF
        return distance.coerceIn(-sizeF / 2f, sizeF / 2f)
    }

    /** Ближайшая фаза нужного индекса: переход через край идёт коротким путём. */
    fun nearestPhase(current: Float, index: Int, size: Int): Float {
        if (size <= 0) return 0f
        val sizeF = size.toFloat()
        val normalized = normalizePhase(current, size)
        val target = wrap(index, size).toFloat()
        var delta = target - normalized
        if (delta > sizeF / 2f) delta -= sizeF
        if (delta < -sizeF / 2f) delta += sizeF
        return finite(current) + delta
    }

    /** Инерция после свайпа: скорость в индексах/секунду превращается в целые шаги. */
    fun flingSteps(velocityIndexPerSecond: Float, maxSteps: Int = 2): Int {
        val velocity = finite(velocityIndexPerSecond)
        val cap = maxSteps.coerceIn(0, 4)
        if (cap == 0) return 0
        return when {
            velocity > 5.2f -> minOf(2, cap)
            velocity > 1.25f -> 1
            velocity < -5.2f -> -minOf(2, cap)
            velocity < -1.25f -> -1
            else -> 0
        }
    }

    fun settleTarget(phase: Float, velocityIndexPerSecond: Float, maxSteps: Int = 2): Float =
        round(finite(phase)) + flingSteps(velocityIndexPerSecond, maxSteps)

    /** Выбранная вкладка-лезвие шире и ярче; соседи уходят в тень. */
    fun tabScale(distance: Float): Float = mix(1.14f, 0.72f, abs(finite(distance)) / 3f)
    fun tabAlpha(distance: Float): Float = mix(1f, 0.28f, abs(finite(distance)) / 3f)
    fun tabSkew(distance: Float): Float = (finite(distance) * 5f).coerceIn(-18f, 18f)

    /** Пункты внутри лезвия: фокус выдвинут вправо, соседи уходят в глубину. */
    fun itemScale(distance: Float): Float = mix(1.05f, 0.80f, abs(finite(distance)) / 3f)
    fun itemAlpha(distance: Float): Float = mix(1f, 0.34f, abs(finite(distance)) / 3f)
    fun itemTranslationX(distance: Float): Float {
        val value = finite(distance)
        return (22f - abs(value) * 16f - maxOf(0f, value) * 6f).coerceIn(-150f, 28f)
    }
    fun itemSkew(distance: Float): Float = (finite(distance) * 2.6f).coerceIn(-9f, 9f)

    /** Пульсация лаймовой рамки фокуса. */
    fun focusPulse(phase: Float): Float {
        val value = finite(phase)
        return 0.5f + 0.5f * sin(value * 2f * PI.toFloat())
    }

    /** Мягкий световой пробег по лезвиям, нормирован в [-1, 1]. */
    fun sweepHeight(phase: Float, x: Float, layer: Int): Float {
        val drift = finite(phase) * 2f * PI.toFloat()
        val primary = sin(finite(x) * 4.4f + drift * (0.5f + layer * 0.07f) + layer * 1.3f)
        val secondary = cos(finite(x) * 1.7f - drift * 0.28f + layer * 0.8f)
        return (primary * 0.74f + secondary * 0.26f).coerceIn(-1f, 1f)
    }

    fun hash01(seed: Int, index: Int, salt: Int): Float {
        var x = seed xor ((index + 1) * 0x45d9f3b) xor (salt * 0x119de1f3)
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return ((x ushr 8) and 0xFFFF) / 65535f
    }
}
