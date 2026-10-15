package com.gsynk.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Эхо-линейка: измерение расстояния звуком.
 *
 * Телефон щёлкает динамиком и слушает микрофоном. Если рядом стена или стол, через
 * несколько миллисекунд вернётся эхо — по времени задержки и скорости звука считается
 * расстояние. Скорость звука честно зависит от температуры воздуха: 331,3 м/с при нуле
 * плюс 0,606 м/с на каждый градус. При 20 °C это 343,4 м/с — почти всегда об этом
 * забывают, а на трёх метрах ошибка выходит в сантиметры.
 *
 * Никакой магии: если эхо не нашлось, приложение так и скажет, вместо выдуманного числа.
 */
object Echo {

    /** Скорость звука при 0 °C, м/с. */
    const val SPEED_AT_ZERO = 331.3f

    /** Прибавка скорости на градус, м/с. */
    const val SPEED_PER_DEGREE = 0.606f

    /** Частота щелчка: чем выше, тем короче импульс и точнее задержка. */
    const val CLICK_HZ = 3_200.0

    /** Длительность щелчка, мс: короткий импульс — эхо от близких предметов не тонет в нём. */
    const val CLICK_MS = 8

    /** Сколько слушаем после щелчка, мс: 200 мс — это больше 30 метров пути с запасом. */
    const val RECORD_MS = 200

    /** Ближе этого времени эхо не отделить от самого щелчка: около 34 см. */
    const val MIN_DELAY_MS = 2

    /** Скорость звука в воздухе, м/с: 331,3 + 0,606·t. При 20 °C — 343,4. */
    fun soundSpeed(tempC: Int): Float {
        val temp = tempC.coerceIn(-40, 60)
        return SPEED_AT_ZERO + SPEED_PER_DEGREE * temp
    }

    /** Сантиметров на миллисекунду хода туда-обратно: путь делим на два. */
    fun cmPerMilli(tempC: Int): Float = soundSpeed(tempC) / 2f / 10f

    /** Расстояние до препятствия по времени прихода эха. */
    fun distanceCm(echoMs: Float, tempC: Int): Float = cmPerMilli(tempC) * echoMs

    /**
     * Щелчок для динамика: короткая посылка с плавными краями, иначе на выходе
     * получается щёлкающий по всему спектру «стук» вместо чистого импульса.
     */
    fun chirp(sampleRate: Int, hz: Double = CLICK_HZ, millis: Int = CLICK_MS): ShortArray {
        val frames = (sampleRate * millis / 1000).coerceAtLeast(16)
        val data = ShortArray(frames)
        val ramp = (frames / 8).coerceAtLeast(1)
        for (index in 0 until frames) {
            val phase = 2.0 * PI * hz * index / sampleRate
            val edge = when {
                index < ramp -> index.toDouble() / ramp
                index > frames - ramp -> (frames - index).toDouble() / ramp
                else -> 1.0
            }
            data[index] = (sin(phase) * edge * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
        return data
    }

    /** Огибающая сигнала: модуль со скользящим сглаживанием. По ней видно эхо. */
    fun envelope(samples: ShortArray, window: Int = 32): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        val w = window.coerceIn(2, 512)
        val out = FloatArray(samples.size)
        var sum = 0f
        for (index in samples.indices) {
            sum += abs(samples[index].toInt())
            if (index >= w) sum -= abs(samples[index - w].toInt())
            out[index] = sum / w.coerceAtMost(index + 1)
        }
        return out
    }

    /** Найденная пара: где в записи щелчок, где эхо и сколько между ними. */
    data class Hit(val clickIndex: Int, val echoIndex: Int, val delayMs: Int)

    /**
     * Найти щелчок и его отражение.
     *
     * Хитрость в том, что момент выхода звука из динамика неизвестен: у каждого телефона
     * своя задержка воспроизведения. Поэтому опорой служит сам щелчок — его прямой путь
     * из динамика в микрофон. Находим его в записи, потом ищем следующий заметный пик
     * и меряем разницу. Так задержка усилителя сама собой вычитается.
     */
    fun findHit(
        env: FloatArray,
        sampleRate: Int,
        minDelayMs: Int = MIN_DELAY_MS,
        noiseRatio: Float = 2.2f
    ): Hit? {
        if (env.isEmpty() || sampleRate <= 0) return null
        val toIndex = { ms: Int -> (sampleRate.toLong() * ms / 1000L).toInt().coerceIn(0, env.size - 1) }

        // Фон: хвост записи, там уже ни щелчка, ни эха.
        val tailFrom = (env.size * 3 / 4).coerceAtMost(env.size - 1)
        var noiseSum = 0f
        var noiseCount = 0
        for (index in tailFrom until env.size) {
            noiseSum += env[index]
            noiseCount++
        }
        val floor = if (noiseCount == 0) 0f else noiseSum / noiseCount
        val threshold = maxOf(floor * noiseRatio, 80f)

        // Первый пик — это щелчок из динамика: точка отсчёта.
        val clickWindow = toIndex(60)
        var clickIndex = -1
        var index = 0
        while (index <= clickWindow) {
            if (env[index] >= threshold) {
                clickIndex = index
                break
            }
            index++
        }
        if (clickIndex < 0) return null

        // Ищем эхо: самый заметный пик после щелчка, не ближе minDelayMs.
        val from = (clickIndex + toIndex(minDelayMs)).coerceAtMost(env.size - 1)
        var echoIndex = -1
        var best = threshold
        index = from
        while (index < env.size) {
            if (env[index] > best) {
                best = env[index]
                echoIndex = index
            }
            index++
        }
        if (echoIndex < 0) return null
        // Округляем, а не отбрасываем: 5,99 мс — это 6 мс, а не 5.
        val delayMs = ((echoIndex - clickIndex) * 1000.0 / sampleRate).roundToInt().coerceAtLeast(1)
        return Hit(clickIndex, echoIndex, delayMs)
    }

    /** Задержка первого эха, мс — то, что нужно большинству вызовов. */
    fun echoDelayMs(env: FloatArray, sampleRate: Int, minDelayMs: Int = MIN_DELAY_MS): Int? =
        findHit(env, sampleRate, minDelayMs)?.delayMs

    data class Estimate(val delayMs: Int, val cm: Float, val level: Float)

    /** Полная оценка: задержка, расстояние и уровень самого эха. */
    fun estimate(env: FloatArray, sampleRate: Int, tempC: Int, minDelayMs: Int = MIN_DELAY_MS): Estimate? {
        val hit = findHit(env, sampleRate, minDelayMs) ?: return null
        return Estimate(
            delayMs = hit.delayMs,
            cm = distanceCm(hit.delayMs.toFloat(), tempC),
            level = env[hit.echoIndex.coerceIn(0, env.size - 1)]
        )
    }

    /** Как читать результат: короткое человеческое объяснение. */
    fun note(cm: Float, delayMs: Int): String = when {
        cm < 35f -> "так близко эхо сливается со щелчком — верьте только «на слух»"
        cm < 60f -> "очень близко: телефон почти упёрся в предмет"
        cm < 800f -> "замер похож на правду"
        else -> "далеко: возможно, поймали эхо от дальней стены, а не от того предмета"
    }
}
