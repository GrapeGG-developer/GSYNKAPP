package com.gsynk.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

/**
 * GSYNK Script UI — постоянная дизайн-система приложения.
 *
 * Script = типографика, нумерованные строки, собственные глифы и живые статусы.
 * Monolith = неизменная структура экрана: состояние сверху, активная строка в
 * центре, контекстные действия снизу; категории меняют содержимое, а не каркас.
 *
 * Здесь только чистая математика 2D-навигации: непрерывная фаза строк,
 * инерция, кратчайший циклический переход, асимметричные «изометричные»
 * радиусы и детерминированные палитры. Никакого 3D, стекла и Android-API.
 */
object Script {
    const val VERSION = "GSYNK Script UI — Monolith baseline 1.0"
    const val VISIBLE_LINES = 7
    const val LINE_STEP_DP = 62
    const val GESTURE_SLOP_DP = 12

    const val INK = 0xFF0B1020L
    const val SURFACE = 0xFF141B2FL
    const val SURFACE_ALT = 0xFF1D2742L
    const val ACCENT = 0xFF7C5CFFL
    const val ACCENT_2 = 0xFF22D3EEL
    const val ACCENT_3 = 0xFFFFB020L
    const val SUCCESS = 0xFF34D399L
    const val DANGER = 0xFFFF5C7AL
    const val TEXT = 0xFFF4F7FFL
    const val MUTED = 0xFF9AA7C2L
    const val BORDER = 0xFF2C3857L

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

    fun normalizePhase(phase: Float, size: Int): Float {
        if (size <= 0) return 0f
        val value = finite(phase)
        val sizeF = size.toFloat()
        return value - floor(value / sizeF) * sizeF
    }

    fun cyclicDistance(index: Int, phase: Float, size: Int): Float {
        if (size <= 0) return 0f
        val sizeF = size.toFloat()
        val normalized = normalizePhase(phase, size)
        var distance = index - normalized
        if (distance > sizeF / 2f) distance -= sizeF
        if (distance < -sizeF / 2f) distance += sizeF
        return distance.coerceIn(-sizeF / 2f, sizeF / 2f)
    }

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

    /** 2D-трансформы строк: только масштаб/прозрачность/сдвиг по Y. */
    fun lineScale(distance: Float): Float = mix(1.035f, 0.88f, abs(finite(distance)) / 3f)
    fun lineAlpha(distance: Float): Float = mix(1f, 0.38f, abs(finite(distance)) / 3f)
    fun lineTranslationY(distance: Float): Float = (finite(distance) * 30f).coerceIn(-110f, 110f)
    fun chapterAlpha(distance: Float): Float = mix(1f, 0.42f, abs(finite(distance)) / 4f)

    /** Мягкая 2D-пульсация активной строки, без стеклянных бликов. */
    fun focusPulse(phase: Float): Float = 0.5f + 0.5f * sin(finite(phase) * 2f * PI.toFloat())

    /**
     * Асимметричные радиусы «изометричного» скругления: diagonal акцент,
     * но всё остаётся плоской 2D-формой.
     */
    fun isoRadii(seed: Int): List<Float> {
        val base = 8f + hash01(seed, 0, 11) * 8f
        return listOf(
            base + 10f,
            base,
            base + 10f,
            base
        )
    }

    fun hash01(seed: Int, index: Int, salt: Int): Float {
        var x = seed xor ((index + 1) * 0x45d9f3b) xor (salt * 0x119de1f3)
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return ((x ushr 8) and 0xFFFF) / 65535f
    }
}
