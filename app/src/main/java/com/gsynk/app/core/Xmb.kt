package com.gsynk.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

/**
 * Чистая геометрия и физика XrossMediaBar для GSYNK.
 *
 * После 5.8.0 навигация больше не дёргается дискретными шагами: фаза категории
 * и фаза пункта — непрерывные числа. Каждый элемент знает своё циклическое
 * расстояние до фокуса, а инерция и кратчайший путь считаются здесь, без Android/Compose.
 */
object Xmb {
    const val VERSION = "GREAL XMB Motion — инерционный XrossMediaBar на физическом GREAL"
    const val CATEGORY_STEP_DP = 92
    const val VISIBLE_ITEMS = 7
    const val WAVE_LAYERS = 5
    const val GESTURE_SLOP_DP = 12

    private fun finite(value: Float, fallback: Float = 0f): Float =
        if (value.isNaN() || value.isInfinite()) fallback else value

    private fun smoothstep01(value: Float): Float {
        val t = finite(value).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(from: Float, to: Float, progress: Float): Float =
        from + (to - from) * smoothstep01(progress)

    /** Бесконечное кольцо категорий и пунктов: дошёл до края — вышел с другой стороны. */
    fun wrap(index: Int, size: Int): Int {
        if (size <= 0) return 0
        return ((index % size) + size) % size
    }

    fun move(current: Int, delta: Int, size: Int): Int = wrap(current + delta, size)

    /** Нормализует непрерывную фазу в [0, size), не ломая отрицательные значения. */
    fun normalizePhase(phase: Float, size: Int): Float {
        if (size <= 0) return 0f
        val value = finite(phase)
        val sizeF = size.toFloat()
        return value - floor(value / sizeF) * sizeF
    }

    /**
     * Кратчайшее знаковое расстояние от элемента до фокуса на кольце.
     * Положительное — элемент справа/ниже, отрицательное — слева/выше.
     */
    fun cyclicDistance(index: Int, phase: Float, size: Int): Float {
        if (size <= 0) return 0f
        val sizeF = size.toFloat()
        val normalized = normalizePhase(phase, size)
        var distance = index - normalized
        if (distance > sizeF / 2f) distance -= sizeF
        if (distance < -sizeF / 2f) distance += sizeF
        return distance.coerceIn(-sizeF / 2f, sizeF / 2f)
    }

    /** Ближайшая фаза, соответствующая нужному индексу, чтобы анимация не шла через весь круг. */
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

    /** Сколько ещё целых пунктов добавить после свайпа, исходя из скорости в индексах/секунду. */
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

    /** Целевая фаза после отпускания: ближайший целый пункт плюс инерция. */
    fun settleTarget(phase: Float, velocityIndexPerSecond: Float, maxSteps: Int = 2): Float =
        round(finite(phase)) + flingSteps(velocityIndexPerSecond, maxSteps)

    /** Выбранная категория крупнее и ярче; соседи плавно уходят в глубину. */
    fun categoryScale(distance: Float): Float = mix(1.16f, 0.66f, abs(finite(distance)) / 3f)
    fun categoryAlpha(distance: Float): Float = mix(1f, 0.24f, abs(finite(distance)) / 3f)
    fun categoryRotationY(distance: Float): Float = (finite(distance) * 8f).coerceIn(-24f, 24f)

    fun categoryScale(distance: Int): Float = categoryScale(distance.toFloat())
    fun categoryAlpha(distance: Int): Float = categoryAlpha(distance.toFloat())
    fun categoryRotationY(distance: Int): Float = categoryRotationY(distance.toFloat())

    /** Фокусный пункт выдвинут вправо и увеличен, остальные плавно уходят влево и в тень. */
    fun itemScale(distance: Float): Float = mix(1.06f, 0.78f, abs(finite(distance)) / 3f)
    fun itemAlpha(distance: Float): Float = mix(1f, 0.30f, abs(finite(distance)) / 3f)
    fun itemTranslationX(distance: Float): Float {
        val value = finite(distance)
        return (26f - abs(value) * 18f - maxOf(0f, value) * 8f).coerceIn(-160f, 30f)
    }
    fun itemRotationX(distance: Float): Float = (finite(distance) * 3.2f).coerceIn(-10f, 10f)

    fun itemScale(distance: Int): Float = itemScale(distance.toFloat())
    fun itemAlpha(distance: Int): Float = itemAlpha(distance.toFloat())
    fun itemTranslationX(distance: Int): Float = itemTranslationX(distance.toFloat())
    fun itemRotationX(distance: Int): Float = itemRotationX(distance.toFloat())

    /**
     * Окно из нескольких пунктов вокруг фокуса. Строки не скроллятся отдельным
     * списком: фаза ходит по всем элементам, на экране всегда фиксированное число.
     */
    fun visibleRange(selected: Int, size: Int, visible: Int = VISIBLE_ITEMS): IntRange {
        if (size <= 0 || visible <= 0) return IntRange.EMPTY
        val count = minOf(visible, size)
        val safeSelected = selected.coerceIn(0, size - 1)
        val start = (safeSelected - count / 2).coerceIn(0, size - count)
        return start until (start + count)
    }

    /** Адаптивное число цветных волн: слабый телефон и лёгкие эффекты рисуют меньше слоёв. */
    fun waveLayers(level: Int, fullEffects: Boolean): Int {
        val base = when (level.coerceIn(0, 2)) {
            0 -> 3
            1 -> 4
            else -> WAVE_LAYERS
        }
        return if (fullEffects) base else maxOf(2, base - 1)
    }

    /** Мягкая цветная волна XMB вместо серых направляющих линий. Нормирована в [-1, 1]. */
    fun waveHeight(phase: Float, x: Float, layer: Int): Float {
        val safePhase = finite(phase)
        val safeX = finite(x)
        val drift = safePhase * 2f * PI.toFloat()
        val primary = sin(safeX * 5.2f + drift * (0.55f + layer * 0.08f) + layer * 1.7f)
        val secondary = cos(safeX * 2.1f - drift * 0.32f + layer * 0.91f)
        return (primary * 0.72f + secondary * 0.28f).coerceIn(-1f, 1f)
    }

    fun hash01(seed: Int, index: Int, salt: Int): Float {
        var x = seed xor ((index + 1) * 0x45d9f3b) xor (salt * 0x119de1f3)
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return ((x ushr 8) and 0xFFFF) / 65535f
    }
}
