package com.gsynk.app.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

/**
 * Небольшая нейросеть, которая считается прямо на телефоне: восемь признаков,
 * два скрытых слоя, три выхода. Ничего не скачивается и никуда не отправляется.
 *
 * Задача у неё простая и полезная: по нагрузке, температуре, памяти, времени суток
 * и числу активных правил определить режим — спокойный, энергичный или ночной.
 * Выход сети управляет переливанием фона, а сама сеть дообучается на ходу
 * (один шаг градиентного спуска за вызов), поэтому её ответы меняются вместе
 * с телефоном.
 *
 * Всё на чистом Kotlin: матричные умножения на процессоре, покрыто тестами.
 */
class RealtimeAi(seed: Long = 20261013L) {

    companion object {
        const val INPUT = 8
        const val HIDDEN_ONE = 24
        const val HIDDEN_TWO = 16
        const val OUTPUT = 3

        const val MODE_CALM = 0
        const val MODE_ACTIVE = 1
        const val MODE_NIGHT = 2

        fun modeName(mode: Int): String = when (mode) {
            MODE_ACTIVE -> "энергичный"
            MODE_NIGHT -> "ночной"
            else -> "спокойный"
        }
    }

    private val random = Random(seed)
    private val w1 = Array(HIDDEN_ONE) { FloatArray(INPUT) { random.nextFloat() * 0.4f - 0.2f } }
    private val b1 = FloatArray(HIDDEN_ONE)
    private val w2 = Array(HIDDEN_TWO) { FloatArray(HIDDEN_ONE) { random.nextFloat() * 0.4f - 0.2f } }
    private val b2 = FloatArray(HIDDEN_TWO)
    private val w3 = Array(OUTPUT) { FloatArray(HIDDEN_TWO) { random.nextFloat() * 0.4f - 0.2f } }
    private val b3 = FloatArray(OUTPUT)

    private val hiddenOne = FloatArray(HIDDEN_ONE)
    private val hiddenTwo = FloatArray(HIDDEN_TWO)
    private val output = FloatArray(OUTPUT)

    var steps: Long = 0
        private set
    var lastLoss: Float = 0f
        private set

    /** Сколько умножений делает один прогон — для оценки нагрузки. */
    val operationsPerRun: Int
        get() = INPUT * HIDDEN_ONE + HIDDEN_ONE * HIDDEN_TWO + HIDDEN_TWO * OUTPUT

    /** Предсказание режима. Возвращает вероятности по трём режимам. */
    fun infer(input: FloatArray): FloatArray {
        val features = normalize(input)
        for (i in 0 until HIDDEN_ONE) {
            var sum = b1[i]
            val row = w1[i]
            for (j in 0 until INPUT) sum += row[j] * features[j]
            hiddenOne[i] = max(0f, sum) // ReLU
        }
        for (i in 0 until HIDDEN_TWO) {
            var sum = b2[i]
            val row = w2[i]
            for (j in 0 until HIDDEN_ONE) sum += row[j] * hiddenOne[j]
            hiddenTwo[i] = max(0f, sum)
        }
        for (i in 0 until OUTPUT) {
            var sum = b3[i]
            val row = w3[i]
            for (j in 0 until HIDDEN_TWO) sum += row[j] * hiddenTwo[j]
            output[i] = sum
        }
        softmax(output)
        return output.copyOf()
    }

    /**
     * Один шаг дообучения на телефоне.
     *
     * Учителя берём из простого правила: ночь, высокая нагрузка и мало памяти
     * переводят телефон в другой режим. Так сеть быстро перенимает поведение
     * и дальше предсказывает его сама.
     */
    fun learn(input: FloatArray, batteryPercent: Int = 100): FloatArray {
        val features = normalize(input)
        val prediction = infer(features)
        val target = teacher(features, batteryPercent)
        val rate = 0.05f

        val delta = FloatArray(OUTPUT) { prediction[it] - target[it] }
        var loss = 0f
        for (i in 0 until OUTPUT) loss += -target[i] * kotlin.math.ln(prediction[i].coerceAtLeast(1e-5f))

        // обратное распространение только по последнему слою — этого достаточно
        for (i in 0 until OUTPUT) {
            for (j in 0 until HIDDEN_TWO) w3[i][j] -= rate * delta[i] * hiddenTwo[j]
            b3[i] -= rate * delta[i]
        }
        steps++
        lastLoss = loss
        return prediction
    }

    /** Правило-учитель: к какому режиму стоит стремиться. */
    private fun teacher(features: FloatArray, batteryPercent: Int): FloatArray {
        val hour = features[4] * 24f
        val load = features[0]
        val battery = features[3]
        val night = hour >= 23f || hour < 7f
        return when {
            batteryPercent < 15 || night -> floatArrayOf(0f, 0f, 1f)
            load > 0.55f || battery > 0.7f -> floatArrayOf(0f, 1f, 0f)
            else -> floatArrayOf(1f, 0f, 0f)
        }
    }

    /** Приводим признаки к 0..1, чтобы сеть не «сходила с ума». */
    private fun normalize(input: FloatArray): FloatArray {
        val out = FloatArray(INPUT)
        for (i in 0 until INPUT) {
            val raw = input.getOrElse(i) { 0f }
            if (i == INPUT - 2) {
                out[i] = raw.coerceIn(0f, 1f)
            } else {
                out[i] = if (raw <= 1f) raw.coerceIn(0f, 1f) else (raw / 100f).coerceIn(0f, 1f)
            }
        }
        return out
    }

    private fun softmax(values: FloatArray): FloatArray {
        var maxValue = values[0]
        for (value in values) if (value > maxValue) maxValue = value
        var sum = 0f
        for (i in values.indices) {
            values[i] = exp((values[i] - maxValue).toDouble()).toFloat()
            sum += values[i]
        }
        for (i in values.indices) values[i] /= sum
        return values
    }
}
