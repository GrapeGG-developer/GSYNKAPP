package com.gsynk.app.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Чистая математика бесконечной SCRIPT-карусели и антистресс-режимов.
 *
 * Здесь нет Android и Compose: геометрию, инерцию, адаптивный бюджет эффектов
 * и попадания по пузырям можно проверять обычными unit-тестами. UI только
 * читает эти значения и рисует один Canvas, поэтому сотни плиток не создают
 * сотни бесконечных анимаций.
 */
object GrealPlay {

    enum class Mode(val title: String, val hint: String) {
        CAROUSEL("бесконечная карусель", "тяни вбок, отпускай с разгоном, касайся для импульса"),
        BUBBLES("пузыри", "лопай объёмные сферы и смотри, как они появляются снова"),
        RIPPLE("рябь", "води пальцем по стеклу и оставляй мягкие световые круги")
    }

    const val DRAG_DEGREES_PER_PX = 0.28f
    const val MAX_FLING_DPS = 720f
    const val RIPPLE_LIFETIME = 1.65f
    const val POP_LIFETIME = 1.15f
    const val CAROUSEL_TILES = 14
    const val BUBBLE_COUNT = 12

    data class SpinState(val angle: Float, val velocity: Float)

    /** Поза одной плитки в кольце: всё нормировано, UI сам умножает на размер сцены. */
    data class TilePose(
        val index: Int,
        val x: Float,
        val y: Float,
        val scale: Float,
        val alpha: Float,
        val depth: Float,
        val rotation: Float
    )

    data class BubblePose(val x: Float, val y: Float, val radius: Float, val tone: Int)

    data class Ripple(val id: Long, val x: Float, val y: Float, val born: Float, val tone: Int)

    private fun finite(value: Float, fallback: Float = 0f): Float =
        if (value.isNaN() || value.isInfinite()) fallback else value

    fun hash01(seed: Int, index: Int, salt: Int): Float {
        var x = seed xor ((index + 1) * 0x45d9f3b) xor (salt * 0x119de1f3)
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return ((x ushr 8) and 0xFFFF) / 65535f
    }

    /** Угол живёт в [0, 360): карусель крутится бесконечно без переполнения float. */
    fun wrapDegrees(degrees: Float): Float {
        val value = finite(degrees) % 360f
        return if (value < 0f) value + 360f else value
    }

    /** Один кадр вращения: во время drag угол уже обновлён пальцем, после — инерция к фоновому ходу. */
    fun step(
        angle: Float,
        velocity: Float,
        dtSeconds: Float,
        idleDps: Float,
        dragging: Boolean
    ): SpinState {
        val dt = finite(dtSeconds).coerceIn(0f, 0.05f)
        val currentVelocity = finite(velocity).coerceIn(-MAX_FLING_DPS, MAX_FLING_DPS)
        if (dragging || dt <= 0f) return SpinState(wrapDegrees(angle), currentVelocity)
        val idle = finite(idleDps).coerceIn(-MAX_FLING_DPS, MAX_FLING_DPS)
        val relaxed = idle + (currentVelocity - idle) * exp(-3.2f * dt)
        return SpinState(wrapDegrees(angle + relaxed * dt), relaxed)
    }

    fun flingVelocity(deltaX: Float, dtSeconds: Float): Float {
        val dt = finite(dtSeconds, 0.016f).coerceIn(0.008f, 0.05f)
        return (finite(deltaX) / dt * 0.22f).coerceIn(-MAX_FLING_DPS, MAX_FLING_DPS)
    }

    fun relaxTilt(tilt: Float, dtSeconds: Float): Float {
        val dt = finite(dtSeconds).coerceIn(0f, 0.05f)
        return (finite(tilt) * exp(-2.4f * dt)).coerceIn(-28f, 28f)
    }

    fun idleSpeed(mode: Mode, motionPercent: Int, pace: Int): Float {
        val base = when (mode) {
            Mode.CAROUSEL -> 24f
            Mode.BUBBLES -> 10f
            Mode.RIPPLE -> 6f
        }
        val motion = motionPercent.coerceIn(40, 200) / 100f
        val paceFactor = when (pace.coerceIn(0, 2)) {
            0 -> 0.55f
            1 -> 1f
            else -> 1.65f
        }
        return base * motion * paceFactor
    }

    /**
     * Кольцо плиток в настоящей перспективе. Дальние плитки идут первыми,
     * чтобы ближние перекрывали их; всё остаётся внутри нормированной сцены.
     */
    fun carouselPoses(count: Int, angleDegrees: Float, tiltDegrees: Float = 0f): List<TilePose> {
        val safeCount = count.coerceIn(4, 24)
        val spin = Math.toRadians(finite(angleDegrees).toDouble()).toFloat()
        val tilt = finite(tiltDegrees).coerceIn(-28f, 28f) / 90f
        return List(safeCount) { index ->
            val theta = index * 2f * PI.toFloat() / safeCount + spin
            val depth = cos(theta)
            val side = sin(theta)
            val perspective = 1f / (1.42f - 0.42f * depth)
            val x = side * perspective * 0.86f
            val y = (sin(theta * 2f + index * 0.37f) * 0.12f + depth * tilt) * perspective
            val scale = (0.58f + 0.46f * perspective).coerceIn(0.45f, 1.12f)
            val alpha = (0.30f + 0.70f * ((depth + 1f) * 0.5f).pow(1.25f)).coerceIn(0f, 1f)
            val rotation = side * (10f + tilt * 18f)
            TilePose(index, x, y, scale, alpha, depth, rotation)
        }.sortedBy { it.depth }
    }

    /** Сколько объёмных осколков и рябей может рисовать устройство без просадок. */
    fun effectBudget(level: Int, fullEffects: Boolean): Int {
        val base = when (level.coerceIn(0, 2)) {
            0 -> 8
            1 -> 14
            else -> 22
        }
        return if (fullEffects) base else maxOf(6, base * 2 / 3)
    }

    fun rippleLimit(budget: Int): Int = budget.coerceIn(8, 28)

    fun carouselCount(budget: Int): Int = budget.coerceIn(8, 16)

    /** Детерминированные плавающие пузыри; после лопания позиция остаётся узнаваемой. */
    fun bubblePose(index: Int, count: Int, seconds: Float, seed: Int): BubblePose {
        val safeCount = count.coerceIn(1, 24)
        val i = index.coerceIn(0, safeCount - 1)
        val t = finite(seconds)
        val driftX = sin(t * 0.55f + i * 1.37f) * 0.025f
        val driftY = cos(t * 0.47f + i * 1.71f) * 0.025f
        val x = (0.14f + 0.72f * hash01(seed, i, 11) + driftX).coerceIn(0.10f, 0.90f)
        val y = (0.14f + 0.72f * hash01(seed, i, 23) + driftY).coerceIn(0.10f, 0.90f)
        val radius = 0.052f + 0.032f * hash01(seed, i, 37)
        val tone = ((i * 3 + (seed and 0x7fffffff)) and 0x7fffffff) % 5
        return BubblePose(x, y, radius, tone)
    }

    fun hitBubble(pose: BubblePose, x: Float, y: Float, width: Float, height: Float): Boolean {
        val w = finite(width, 1f).coerceAtLeast(1f)
        val h = finite(height, 1f).coerceAtLeast(1f)
        val minDim = minOf(w, h)
        val dx = (finite(x) - pose.x) * w
        val dy = (finite(y) - pose.y) * h
        val radius = pose.radius * minDim * 1.18f
        return dx * dx + dy * dy <= radius * radius
    }

    fun bubblePopScale(ageSeconds: Float): Float {
        val age = finite(ageSeconds, -1f)
        if (age < 0f || age > POP_LIFETIME) return 1f
        return 1f + sin(age / POP_LIFETIME * PI.toFloat()) * 0.38f
    }

    fun bubblePopAlpha(ageSeconds: Float): Float {
        val age = finite(ageSeconds, -1f)
        if (age < 0f || age > POP_LIFETIME) return 1f
        return (0.18f + 0.82f * (age / POP_LIFETIME)).coerceIn(0.18f, 1f)
    }

    fun rippleAlpha(ageSeconds: Float, lifetime: Float = RIPPLE_LIFETIME): Float {
        val age = finite(ageSeconds, -1f)
        val span = finite(lifetime, RIPPLE_LIFETIME).coerceIn(0.2f, 4f)
        return (1f - age / span).coerceIn(0f, 1f)
    }

    fun rippleRadius(ageSeconds: Float, maxRadius: Float, lifetime: Float = RIPPLE_LIFETIME): Float {
        val age = finite(ageSeconds, 0f)
        val span = finite(lifetime, RIPPLE_LIFETIME).coerceIn(0.2f, 4f)
        val progress = (age / span).coerceIn(0f, 1f)
        return finite(maxRadius).coerceAtLeast(0f) * (0.08f + 0.92f * progress)
    }

    fun decayRipples(
        ripples: List<Ripple>,
        now: Float,
        lifetime: Float = RIPPLE_LIFETIME
    ): List<Ripple> {
        val span = finite(lifetime, RIPPLE_LIFETIME).coerceIn(0.2f, 4f)
        return ripples.filter { finite(now - it.born) in 0f..span }
    }
}
