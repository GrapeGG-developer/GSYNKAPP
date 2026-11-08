package com.gsynk.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Чистая геометрия XrossMediaBar для GSYNK.
 *
 * XMB живёт поверх физического GREAL: горизонтальный ряд подписанных категорий,
 * вертикальный ряд функций под выбранной категорией, один фокус и затухание
 * соседей по глубине. Здесь только математика без Android и Compose — её
 * проверяют unit-тесты, а UI рисует результат одним Canvas/Column.
 */
object Xmb {
    const val VERSION = "GREAL XMB — XrossMediaBar на физическом GREAL"
    const val CATEGORY_STEP_DP = 92
    const val VISIBLE_ITEMS = 7
    const val WAVE_LAYERS = 5

    /** Бесконечное кольцо категорий и пунктов: дошёл до края — вышел с другой стороны. */
    fun wrap(index: Int, size: Int): Int {
        if (size <= 0) return 0
        return ((index % size) + size) % size
    }

    fun move(current: Int, delta: Int, size: Int): Int = wrap(current + delta, size)

    /** Выбранная категория крупнее и ярче; соседи уходят в глубину, но остаются на экране. */
    fun categoryScale(distance: Int): Float = when (abs(distance)) {
        0 -> 1.16f
        1 -> 0.90f
        2 -> 0.78f
        else -> 0.66f
    }

    fun categoryAlpha(distance: Int): Float = when (abs(distance)) {
        0 -> 1f
        1 -> 0.72f
        2 -> 0.48f
        else -> 0.28f
    }

    fun categoryRotationY(distance: Int): Float = (distance * 8f).coerceIn(-24f, 24f)

    /** Фокусный пункт выдвинут вправо и увеличен, остальные уходят влево и в тень. */
    fun itemScale(distance: Int): Float = when (abs(distance)) {
        0 -> 1.06f
        1 -> 0.94f
        2 -> 0.86f
        else -> 0.78f
    }

    fun itemAlpha(distance: Int): Float = when (abs(distance)) {
        0 -> 1f
        1 -> 0.78f
        2 -> 0.55f
        else -> 0.32f
    }

    fun itemTranslationX(distance: Int): Float = when {
        distance == 0 -> 26f
        distance < 0 -> -8f * abs(distance)
        else -> -16f * distance
    }

    fun itemRotationX(distance: Int): Float = (distance * 3.2f).coerceIn(-10f, 10f)

    /**
     * Окно из нескольких пунктов вокруг фокуса. Список не скроллится сам:
     * фокус ходит по всем элементам, а на экране всегда видно фиксированное число строк.
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
        val safePhase = if (phase.isNaN() || phase.isInfinite()) 0f else phase
        val safeX = if (x.isNaN() || x.isInfinite()) 0f else x
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
