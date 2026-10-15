package com.gsynk.app.core

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Проверка глаз: острота и цвет.
 *
 * Острота — кольцо Ландольта. Правило простое и настоящее: весь знак должен занимать
 * 5 угловых минут, а прорезь — одну. Значит, размер знака на экране считается от
 * расстояния до глаз, а не «на глазок»: при 40 см строка 1,0 получает кольцо 0,58 мм.
 * Приложение рисует кольцо точно в миллиметрах и спрашивает, куда смотрит прорезь.
 *
 * Цвет — пластины из точек, собранные по геометрии семисегментного знака. Внутри цифры
 * точки одного оттенка, снаружи другого: человек с обычным зрением видит цифру,
 * при цветовой слабости она теряется.
 */
object Probe {

    /** Направление прорези в кольце Ландольта. */
    enum class Dir(val label: String, val degrees: Float) {
        RIGHT("вправо", 0f),
        DOWN("вниз", 90f),
        LEFT("влево", 180f),
        UP("вверх", 270f)
    }

    /** Строки проверки остроты: десятичные значения привычной таблицы. */
    val LINES: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.8f, 1.0f, 1.2f, 1.5f)

    /**
     * Размер знака в миллиметрах для строки [acuity] на расстоянии [distanceMm].
     * Пять угловых минут на знак: размер = 2·d·tg(2,5′).
     */
    fun optotypeMm(acuity: Float, distanceMm: Float): Float {
        if (acuity <= 0f || distanceMm <= 0f) return 0f
        val minutes = 5f / acuity
        val radians = minutes * PI.toFloat() / (180f * 60f)
        return 2f * distanceMm * kotlin.math.tan(radians / 2f)
    }

    /** Прорезь кольца — ровно пятая часть знака. */
    fun gapMm(acuity: Float, distanceMm: Float): Float = optotypeMm(acuity, distanceMm) / 5f

    /** Подпись строки: «0,5» вместо «0.5». */
    fun lineLabel(acuity: Float): String = when {
        acuity >= 1f -> "%.1f".format(acuity).replace('.', ',')
        else -> "%.1f".format(acuity).replace('.', ',')
    }

    /** Размер строки в пикселях при плотности экрана [densityDpi]. */
    fun optotypePx(acuity: Float, distanceMm: Float, densityDpi: Int): Int =
        (optotypeMm(acuity, distanceMm) / 25.4f * densityDpi).roundToInt().coerceAtLeast(2)

    /** Очередь направлений: одинаковая для одного и того же зерна, но не подряд. */
    fun directions(seed: Int, count: Int): List<Dir> {
        val random = Random(seed)
        val out = ArrayList<Dir>(count)
        var previous: Dir? = null
        while (out.size < count) {
            val next = Dir.entries[random.nextInt(Dir.entries.size)]
            if (next == previous) continue
            out.add(next)
            previous = next
        }
        return out
    }

    // ————— цвет: контраст по WCAG —————

    /** Относительная яркость канала (0…255) по формуле WCAG. */
    fun channel(value: Int): Float {
        val c = value.coerceIn(0, 255) / 255f
        return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    /** Относительная яркость цвета, 0…1. */
    fun luminance(r: Int, g: Int, b: Int): Float =
        0.2126f * channel(r) + 0.7152f * channel(g) + 0.0722f * channel(b)

    /** Коэффициент контраста двух цветов: от 1 (не видно) до 21 (чёрное на белом). */
    fun contrast(
        foreground: Triple<Int, Int, Int>,
        background: Triple<Int, Int, Int>
    ): Float {
        val a = luminance(foreground.first, foreground.second, foreground.third)
        val b = luminance(background.first, background.second, background.third)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** Человеческий вывод по контрасту: где это читается, а где нет. */
    fun contrastVerdict(ratio: Float): String = when {
        ratio >= 7f -> "читается даже на солнце и мелким шрифтом"
        ratio >= 4.5f -> "нормальный текст читается"
        ratio >= 3f -> "только крупный текст и кнопки"
        ratio >= 2f -> "плохо: глаза напрягаются"
        else -> "почти не видно — считай, что цвета нет"
    }

    // ————— цветовые пластины —————

    data class Dot(val x: Float, val y: Float, val radius: Float, val inside: Boolean)

    /** Геометрия семисегментного знака: семь полосок в квадрате 0…1. */
    private val segments: List<FloatArray> = listOf(
        floatArrayOf(0.18f, 0.08f, 0.82f, 0.20f), // a — верх
        floatArrayOf(0.72f, 0.13f, 0.84f, 0.52f), // b — право верх
        floatArrayOf(0.72f, 0.48f, 0.84f, 0.87f), // c — право низ
        floatArrayOf(0.18f, 0.80f, 0.82f, 0.92f), // d — низ
        floatArrayOf(0.16f, 0.48f, 0.28f, 0.87f), // e — лево низ
        floatArrayOf(0.16f, 0.13f, 0.28f, 0.52f), // f — лево верх
        floatArrayOf(0.20f, 0.44f, 0.80f, 0.56f)  // g — середина
    )

    private val digitSegments: Map<Int, Set<Int>> = mapOf(
        0 to setOf(0, 1, 2, 3, 4, 5),
        1 to setOf(1, 2),
        2 to setOf(0, 1, 6, 4, 3),
        3 to setOf(0, 1, 6, 2, 3),
        4 to setOf(5, 6, 1, 2),
        5 to setOf(0, 5, 6, 2, 3),
        6 to setOf(0, 5, 6, 4, 2, 3),
        7 to setOf(0, 1, 2),
        8 to setOf(0, 1, 2, 3, 4, 5, 6),
        9 to setOf(0, 1, 2, 3, 5, 6)
    )

    fun segmentsOf(digit: Int): Set<Int> = digitSegments[digit] ?: emptySet()

    /** Внутри ли точка полосок знака (знак занимает одну цифру в квадрате). */
    fun insideDigit(x: Float, y: Float, digit: Int): Boolean =
        segmentsOf(digit).any { index ->
            val box = segments[index]
            x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3]
        }

    /**
     * Пластина из точек: одинаковое зерно даёт одну и ту же картинку.
     * Точки внутри знака и фон различаются только цветом — форма остаётся «шумом».
     */
    fun plate(digit: Int, seed: Int, dots: Int = 260): List<Dot> {
        val random = Random(seed)
        val count = dots.coerceIn(40, 1_200)
        val out = ArrayList<Dot>(count)
        var guard = 0
        while (out.size < count && guard < count * 12) {
            guard++
            // Раскладываем точки по кругу: так пластина читается глазом как у зрительных таблиц.
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val radius = kotlin.math.sqrt(random.nextFloat()) * 0.47f
            val x = 0.5f + radius * kotlin.math.cos(angle)
            val y = 0.5f + radius * kotlin.math.sin(angle)
            val inside = insideNumber(x, y, digit)
            out.add(Dot(x = x, y = y, radius = 0.012f + random.nextFloat() * 0.02f, inside = inside))
        }
        return out
    }

    /** Попала ли точка в знак: одна цифра по центру, две — половинками слева и справа. */
    fun insideNumber(x: Float, y: Float, number: Int): Boolean {
        val vertical = (y - 0.14f) / 0.72f
        if (number < 10) return insideDigit((x - 0.28f) / 0.44f, vertical, number)
        val left = number / 10
        val right = number % 10
        return if (x < 0.5f) {
            insideDigit((x - 0.10f) / 0.36f, vertical, left)
        } else {
            insideDigit((x - 0.54f) / 0.36f, vertical, right)
        }
    }

    /** Сколько точек попало в знак — по этому тесты видят, что цифра вообще есть. */
    fun figureDots(plate: List<Dot>): Int = plate.count { it.inside }

    /** Насколько знак различим по площади: доля точек внутри. */
    fun figureShare(plate: List<Dot>): Float =
        if (plate.isEmpty()) 0f else figureDots(plate).toFloat() / plate.size

    /** Случайное число для пластины: двузначные и однозначные знаки по очереди. */
    fun numberFor(seed: Int): Int {
        val random = Random(seed * 31 + 7)
        return if (random.nextBoolean()) random.nextInt(10, 100) else random.nextInt(1, 10)
    }
}
