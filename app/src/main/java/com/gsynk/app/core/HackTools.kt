package com.gsynk.app.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.random.Random

/** Морзянка: превращает текст в последовательность «точка/тире/пауза». */
object Morse {

    private val table = mapOf(
        'А' to ".-", 'Б' to "-...", 'В' to ".--", 'Г' to "--.", 'Д' to "-..", 'Е' to ".",
        'Ж' to "...-", 'З' to "--..", 'И' to "..", 'Й' to ".---", 'К' to "-.-", 'Л' to ".-..",
        'М' to "--", 'Н' to "-.", 'О' to "---", 'П' to ".--.", 'Р' to ".-.", 'С' to "...",
        'Т' to "-", 'У' to "..-", 'Ф' to "..-.", 'Х' to "....", 'Ц' to "-.-.", 'Ч' to "---.",
        'Ш' to "----", 'Щ' to "--.-", 'Ъ' to "--.--", 'Ы' to "-.--", 'Ь' to "-..-",
        'Э' to "..-..", 'Ю' to "..--", 'Я' to ".-.-",
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----."
    )

    /** Один шаг: «мигание» (true) или пауза (false) и длительность в единицах. */
    data class Step(val light: Boolean, val units: Int)

    fun encode(text: String): List<Step> {
        val steps = mutableListOf<Step>()
        text.uppercase().forEach { symbol ->
            val code = table[symbol]
            when {
                code != null -> {
                    code.forEachIndexed { index, part ->
                        steps.add(Step(true, if (part == '-') 3 else 1))
                        if (index != code.lastIndex) steps.add(Step(false, 1))
                    }
                    steps.add(Step(false, 3)) // пауза между буквами
                }

                symbol == ' ' -> steps.add(Step(false, 3))
            }
        }
        return steps
    }

    fun toText(text: String): String = table[text.uppercase().firstOrNull()] ?: ""
}

/** Генератор паролей и ключей. */
object Passwords {

    private const val LOWERCASE = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{}"

    data class Options(
        val length: Int = 16,
        val letters: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true
    )

    fun generate(options: Options = Options(), random: Random = Random.Default): String {
        val alphabet = buildString {
            if (options.letters) append(LOWERCASE).append(UPPERCASE)
            if (options.digits) append(DIGITS)
            if (options.symbols) append(SYMBOLS)
        }.ifEmpty { LOWERCASE + DIGITS }

        val length = options.length.coerceIn(6, 64)
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    /** Простая оценка стойкости — показываем пользователю. */
    fun strength(password: String): Int {
        if (password.isEmpty()) return 0
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 14) score++
        if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return score.coerceIn(0, 4)
    }
}

/** Показатели телефона для «хакерского» дашборда. */
object DeviceStats {

    data class Snapshot(
        val ramUsedMb: Long,
        val ramTotalMb: Long,
        val storageFreeGb: Double,
        val storageTotalGb: Double,
        val batteryPercent: Int,
        val batteryTempC: Double,
        val uptimeHours: Long,
        val cores: Int,
        val androidVersion: String,
        val model: String
    ) {
        val ramPercent: Int
            get() = if (ramTotalMb <= 0) 0 else ((ramUsedMb * 100) / ramTotalMb).toInt()

        val storageUsedPercent: Int
            get() = if (storageTotalGb <= 0) 0
            else (((storageTotalGb - storageFreeGb) / storageTotalGb) * 100).toInt()
    }

    fun snapshot(context: Context): Snapshot {
        val runtime = Runtime.getRuntime()
        val usedHeap = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val (totalRam, _) = readRamInfo()

        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val freeGb = stat?.let { it.availableBytes / 1024.0 / 1024 / 1024 } ?: 0.0
        val totalGb = stat?.let { it.totalBytes / 1024.0 / 1024 / 1024 } ?: 0.0

        val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0

        return Snapshot(
            ramUsedMb = if (totalRam > 0) totalRam - freeRamMb() else usedHeap,
            ramTotalMb = if (totalRam > 0) totalRam else runtime.maxMemory() / (1024 * 1024),
            storageFreeGb = freeGb,
            storageTotalGb = totalGb,
            batteryPercent = if (level < 0 || scale <= 0) 0 else level * 100 / scale,
            batteryTempC = temp,
            uptimeHours = SystemClock.elapsedRealtime() / 3_600_000,
            cores = Runtime.getRuntime().availableProcessors(),
            androidVersion = Build.VERSION.RELEASE ?: "?",
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )
    }

    private fun readRamInfo(): Pair<Long, Long> = runCatching {
        val lines = java.io.File("/proc/meminfo").readLines()
        val total = lines.firstOrNull { it.startsWith("MemTotal") }
            ?.filter { it.isDigit() }?.toLongOrNull()?.div(1024) ?: 0L
        val available = lines.firstOrNull { it.startsWith("MemAvailable") }
            ?.filter { it.isDigit() }?.toLongOrNull()?.div(1024) ?: 0L
        total to available
    }.getOrDefault(0L to 0L)

    private fun freeRamMb(): Long = readRamInfo().second
}

/** Датчики: магнитометр (поиск металла), уровень, шумомер. */
object Sensors {

    /** Магнитометр: сила поля в микротеслах. */
    class Compass(context: Context) {
        private val manager = context.getSystemService(SensorManager::class.java)
        private var listener: SensorEventListener? = null

        val supported: Boolean get() = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

        fun start(onChange: (Float) -> Unit) {
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) ?: return
            stop()
            val newListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    onChange(sqrt(x * x + y * y + z * z))
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            listener = newListener
            manager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        fun stop() {
            listener?.let { manager?.unregisterListener(it) }
            listener = null
        }
    }

    /** Уровень: наклон телефона в градусах по двум осям. */
    class Level(context: Context) {
        private val manager = context.getSystemService(SensorManager::class.java)
        private var listener: SensorEventListener? = null

        val supported: Boolean get() = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

        fun start(onChange: (Float, Float) -> Unit) {
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            stop()
            val newListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val tiltX = Math.toDegrees(kotlin.math.atan2(x.toDouble(), 9.81)).toFloat()
                    val tiltY = Math.toDegrees(kotlin.math.atan2(y.toDouble(), 9.81)).toFloat()
                    onChange(tiltX, tiltY)
                }

                override fun onAccuracyChanged(sensor: Sensor? , accuracy: Int) = Unit
            }
            listener = newListener
            manager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        fun stop() {
            listener?.let { manager?.unregisterListener(it) }
            listener = null
        }
    }

    /** Перевод амплитуды микрофона в примерные децибелы. */
    fun amplitudeToDb(amplitude: Double): Int {
        if (amplitude <= 0.0) return 0
        val db = 20 * log10(amplitude / 32767.0) + 90.0
        return db.toInt().coerceIn(0, 120)
    }
}
