package com.gsynk.app.core

import android.content.Context
import kotlin.math.sqrt

/**
 * Полноценный стенд нагрузки — свой, без чужих программ.
 *
 * Считает то же, что большие бенчмарки, но честно и прозрачно:
 *  * **процессор, одно ядро** — целочисленные операции, число с плавающей точкой, матрицы;
 *  * **процессор, все ядра** — то же самое в параллель, видно ускорение от многопотока;
 *  * **память** — скорость записи и чтения массива;
 *  * **накопитель** — запись и чтение файла;
 *  * **ускоритель** — счёт на графическом процессоре (через [TurboState]).
 *
 * Формула общего балла открытая и подобрана так, чтобы телефон среднего класса давал
 * около 250 тысяч, а флагман — около миллиона.
 */
object Bench {

    /** Что именно меряем. */
    enum class Stage(val title: String, val hint: String) {
        CPU_INT("Процессор: целые числа", "складывает и делит миллионы чисел"),
        CPU_FLOAT("Процессор: дроби", "считает синусы и корни"),
        CPU_MATRIX("Матрицы", "умножает матрицы 128×128"),
        MEMORY("Оперативная память", "пишет и читает большой массив"),
        STORAGE("Накопитель", "пишет файл и читает его назад"),
        MULTI("Все ядра", "то же в параллель — видно ускорение"),
        GPU("Ускоритель", "счёт на графическом процессоре")
    }

    data class Result(
        val cpuInt: Double,
        val cpuFloat: Double,
        val cpuMatrix: Double,
        val memory: Double,
        val storage: Double,
        val multi: Double,
        val gpu: Double,
        val cores: Int,
        val at: Long = System.currentTimeMillis()
    ) {
        /** Открытая формула: веса подобраны так, чтобы баллы были «человеческими». */
        val score: Int
            get() = (
                cpuInt * 1.0 + cpuFloat * 0.9 + cpuMatrix * 2.4 + memory * 0.4 +
                    storage * 0.8 + multi * 1.2 + gpu * 1.6
                ).toInt()

        val multithreadBonus: Double
            get() = if (cpuInt <= 0.0) 0.0 else multi / cpuInt

        /** Класс телефона по баллам — то же деление, что у больших стендов. */
        val tier: String
            get() = when {
                score >= 900_000 -> "флагман"
                score >= 500_000 -> "почти флагман"
                score >= 250_000 -> "средний класс"
                score >= 100_000 -> "крепкий середняк"
                else -> "бюджетный"
            }

        fun line(title: String, value: Double, unit: String = "оп/с"): String =
            title + ": " + "%.1f".format(value) + " млн " + unit + "·с"
    }

    /** Место в списке прошлых прогонов — «стало лучше или хуже». */
    fun compare(current: Result, previous: Result?): String {
        if (previous == null) return "первый прогон — сравнивать не с чем"
        val delta = (current.score - previous.score) * 100.0 / previous.score.coerceAtLeast(1)
        val sign = if (delta >= 0) "+" else "−"
        val verdict = when {
            delta > 8 -> "заметно быстрее"
            delta > 2 -> "чуть быстрее"
            delta < -8 -> "заметно медленнее — возможно, троттлинг"
            delta < -2 -> "чуть медленнее"
            else -> "как раньше"
        }
        return "$sign%.1f%% — %s".format(kotlin.math.abs(delta), verdict)
    }

    /** Сравнение двух телефонов — чтобы «передать другу» имело смысл. */
    fun versus(mine: Result, other: Result): String {
        val diff = mine.score - other.score
        val percent = kotlin.math.abs(diff) * 100.0 / other.score.coerceAtLeast(1)
        return when {
            diff > other.score * 0.05 -> "Ваш телефон быстрее на %.0f%%".format(percent)
            diff < -other.score * 0.05 -> "Телефон друга быстрее на %.0f%%".format(percent)
            else -> "Телефоны идут вровень (±5%)"
        }
    }

    // ————— измерения: чистые функции, их проверяют тесты —————

    /** Целочисленная нагрузка: миллион делений и сложений. */
    fun intOps(iterations: Int): Double {
        var acc = 1L
        var i = 0
        while (i < iterations) {
            acc = (acc * 31 + i) % 2_147_483_647L
            acc = acc xor (acc shr 3)
            i++
        }
        return acc.toDouble()
    }

    /** Дробная нагрузка: синус и корень — самые дорогие операции. */
    fun floatOps(iterations: Int): Double {
        var acc = 0.0
        var i = 0
        while (i < iterations) {
            acc += kotlin.math.sin(i * 0.001) * sqrt(1.0 + (i % 97))
            i++
        }
        return acc
    }

    /** Умножение матриц: так считают почти все тяжёлые задачи. */
    fun matrixMultiply(size: Int): Double {
        val a = Array(size) { i -> DoubleArray(size) { j -> ((i + j) % 7).toDouble() } }
        val b = Array(size) { i -> DoubleArray(size) { j -> ((i * j) % 5).toDouble() } }
        val c = Array(size) { DoubleArray(size) }
        for (i in 0 until size) {
            for (k in 0 until size) {
                val aik = a[i][k]
                for (j in 0 until size) c[i][j] += aik * b[k][j]
            }
        }
        return c[size - 1][size - 1]
    }

    /** Скорость памяти: мегабайты в секунду на запись и чтение. */
    fun memoryBandwidthMbPerSecond(bytes: Int, millis: Long): Double {
        if (millis <= 0L) return 0.0
        return bytes.toDouble() / 1_048_576.0 / (millis / 1000.0)
    }

    /** Накопитель: метры по тому же принципу, но медленнее — считаем мегабайтами. */
    fun storageMbPerSecond(bytes: Long, millis: Long): Double = memoryBandwidthMbPerSecond(
        bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        millis
    ).let { if (bytes > Int.MAX_VALUE) it * (bytes.toDouble() / Int.MAX_VALUE) else it }

    /** Итоговый балл из «сырых» скоростей — открытая формула стенда. */
    fun score(
        cpuInt: Double, cpuFloat: Double, cpuMatrix: Double,
        memory: Double, storage: Double, multi: Double, gpu: Double
    ): Int = Result(cpuInt, cpuFloat, cpuMatrix, memory, storage, multi, gpu, Runtime.getRuntime().availableProcessors()).score

    /** Сколько примерно идёт полный прогон — для честного прогресса. */
    fun estimateSeconds(cores: Int): Int = when {
        cores >= 8 -> 42
        cores >= 6 -> 55
        cores >= 4 -> 68
        else -> 85
    }

    // ————— история и передача другу —————

    private const val PREFS = "gsynk_bench"

    fun save(context: Context, result: Result): Boolean = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = load(context).toMutableList()
        list.add(0, result)
        while (list.size > 12) list.removeAt(list.lastIndex)
        prefs.edit().putString("history", encode(list)).apply()
        true
    }.getOrDefault(false)

    fun load(context: Context): List<Result> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        decode(prefs.getString("history", "").orEmpty())
    }.getOrDefault(emptyList())

    fun last(context: Context): Result? = load(context).firstOrNull()

    fun clear(context: Context) = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }.let { }

    fun encode(list: List<Result>): String = list.joinToString("\n") { result ->
        listOf(
            result.cpuInt, result.cpuFloat, result.cpuMatrix, result.memory,
            result.storage, result.multi, result.gpu, result.cores.toDouble(), result.at.toDouble()
        ).joinToString(";") { "%.3f".format(it) }
    }

    fun decode(text: String): List<Result> = text.split('\n').mapNotNull { line ->
        val parts = line.trim().split(';')
        if (parts.size < 9) return@mapNotNull null
        val numbers = parts.map { it.toDoubleOrNull() ?: return@mapNotNull null }
        Result(
            cpuInt = numbers[0], cpuFloat = numbers[1], cpuMatrix = numbers[2],
            memory = numbers[3], storage = numbers[4], multi = numbers[5], gpu = numbers[6],
            cores = numbers[7].toInt(), at = numbers[8].toLong()
        )
    }

    /** Короткая строка для передачи другу: умещается в метку и в сообщение. */
    fun shareBody(result: Result, model: String): String = listOf(
        model.replace(';', ' '),
        result.score.toString(),
        result.cpuInt.toInt().toString(),
        result.cpuFloat.toInt().toString(),
        result.cpuMatrix.toInt().toString(),
        result.memory.toInt().toString(),
        result.storage.toInt().toString(),
        result.multi.toInt().toString(),
        result.gpu.toInt().toString()
    ).joinToString(";")

    fun parseShare(body: String): Pair<String, Result>? {
        val parts = body.split(';')
        if (parts.size < 9) return null
        val numbers = parts.drop(1).map { it.toIntOrNull() ?: return null }
        return parts[0] to Result(
            cpuInt = numbers[1].toDouble(), cpuFloat = numbers[2].toDouble(),
            cpuMatrix = numbers[3].toDouble(), memory = numbers[4].toDouble(),
            storage = numbers[5].toDouble(), multi = numbers[6].toDouble(),
            gpu = numbers[7].toDouble(), cores = Runtime.getRuntime().availableProcessors()
        )
    }
}
