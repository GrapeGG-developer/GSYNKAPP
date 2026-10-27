package com.gsynk.app.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.hardware.SensorManager
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * GIOUI: управление железом, а не только чтение датчиков.
 *
 * Здесь собраны узлы ядра, до которых может дотянуться обычное приложение, и
 * честные попытки дотянуться до тех, что закрыты системой. Приложение никогда
 * не врёт: если запись не прошла — так и пишет, вместо «сделано».
 *
 * Границы: загрузчик, прошивка и разделы не трогаются никогда (см. `DeviceLab.SAFETY_NOTE`).
 * Всё, что пишется в `/sys`, пишется только по нажатию и с возвратом прежнего значения.
 */
object ChipLab {

    // ————————————————————— чистая часть: её проверяют тесты —————————————————————

    /**
     * Разбор hex-строки в байты. Понимает «60», «30 04», «30-04», «0x30,0x04» —
     * люди пишут по-разному, и это не повод отказывать.
     */
    fun parseHex(text: String): ByteArray? {
        val clean = text
            .replace("0x", " ")
            .replace("0X", " ")
            .replace(",", " ")
            .replace("-", " ")
            .replace(":", " ")
            .replace(".", " ")
            .trim()
        if (clean.isEmpty()) return null
        val parts = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.any { it.length > 2 }) return null
        val bytes = ArrayList<Byte>(parts.size)
        for (part in parts) {
            val value = part.toIntOrNull(16) ?: return null
            if (value !in 0..255) return null
            bytes.add(value.toByte())
        }
        return bytes.toByteArray()
    }

    /** Обратный перевод: байты в привычную строку «60 4F 00 FF». */
    fun formatHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { String.format(Locale.ROOT, "%02X", it) }

    /** Износ батареи: сколько процентов ёмкости осталось от заводской. */
    fun wearPercent(fullUah: Long, designUah: Long): Int? {
        if (fullUah <= 0L || designUah <= 0L) return null
        return (fullUah * 100.0 / designUah).toInt().coerceIn(1, 130)
    }

    /**
     * Значение для записи в узел ядра. Пускаем только буквы, цифры, `_`, `-` и `,`:
     * это отсекает любую попытку подставить команду через пробел или кавычку.
     */
    fun cleanValue(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 40) return null
        return if (clean.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == ',' }) clean else null
    }

    /** Ток батареи: одни ядра пишут микроамперы, другие миллиамперы — разбираем по величине. */
    fun currentMaFrom(raw: Long): Float {
        val value = if (raw > 20_000L || raw < -20_000L) raw / 1000f else raw.toFloat()
        return value
    }

    /**
     * Температура батареи. Часть ядер пишет десятые доли градуса (315 = 31.5 °C),
     * часть — сразу градусы (28 = 28 °C). Различаем по величине: 200 и выше —
     * это уже точно десятые доли, ниже — живые градусы.
     */
    fun tempCelsiusFrom(raw: Long): Float =
        if (raw > 200L) raw / 10f else raw.toFloat()

    /** Вердикт по смене режима частот — человеческим языком, до попытки. */
    fun governorVerdict(current: String, target: String, allowed: List<String>): String = when {
        target.isBlank() -> "режим не выбран"
        target !in allowed -> "прошивка не знает режима «" + target + "»"
        current.isBlank() -> "текущий режим неизвестен, но попробовать можно"
        current == target -> "уже включён"
        else -> "можно попробовать: " + current + " → " + target
    }

    // ————————————————————— чтение узлов —————————————————————

    private const val CPU_ROOT = "/sys/devices/system/cpu"
    private val BATTERY_ROOTS = listOf(
        "/sys/class/power_supply/battery",
        "/sys/class/power_supply/bms",
        "/sys/class/power_supply/maxfg"
    )

    fun readNode(path: String): String = runCatching { File(path).readText().trim() }.getOrDefault("")

    fun readLong(path: String): Long = readNode(path).substringBefore(' ').toLongOrNull() ?: 0L

    data class Node(val title: String, val path: String, val value: String, val note: String)

    /** Узлы частот: то, чем действительно управляет ядро. */
    fun cpuNodes(core: Int = 0): List<Node> = listOf(
        Node("текущий режим", "$CPU_ROOT/cpu$core/cpufreq/scaling_governor", readNode("$CPU_ROOT/cpu$core/cpufreq/scaling_governor"), "как ядро решает, когда поднимать частоту"),
        Node("доступные режимы", "$CPU_ROOT/cpu$core/cpufreq/scaling_available_governors", readNode("$CPU_ROOT/cpu$core/cpufreq/scaling_available_governors"), "список того, что разрешила прошивка"),
        Node("потолок сейчас", "$CPU_ROOT/cpu$core/cpufreq/scaling_max_freq", readNode("$CPU_ROOT/cpu$core/cpufreq/scaling_max_freq"), "кГц"),
        Node("пол сейчас", "$CPU_ROOT/cpu$core/cpufreq/scaling_min_freq", readNode("$CPU_ROOT/cpu$core/cpufreq/scaling_min_freq"), "кГц"),
        Node("сколько ступеней", "$CPU_ROOT/cpu$core/cpufreq/scaling_available_frequencies", readNode("$CPU_ROOT/cpu$core/cpufreq/scaling_available_frequencies"), "весь набор частот ядра"),
        Node("планировщик ввода-вывода", "/sys/block/sda/queue/scheduler", readNode("/sys/block/sda/queue/scheduler"), "кто решает порядок чтения с накопителя")
    )

    // ————————————————————— батарея изнутри —————————————————————

    data class BatteryDeep(
        val cycles: Int,
        val wearPercent: Int?,
        val fullUah: Long,
        val designUah: Long,
        val currentMa: Float,
        val voltageMv: Float,
        val celsius: Float,
        val healthy: String,
        val note: String
    ) {
        val hasData: Boolean get() = cycles > 0 || fullUah > 0L || currentMa != 0f || celsius > 0f
    }

    private fun batteryPath(vararg names: String): Pair<String, Long>? {
        for (root in BATTERY_ROOTS) {
            for (name in names) {
                val file = File(root, name)
                if (!file.exists()) continue
                val value = readLong(file.path)
                if (value != 0L) return file.path to value
            }
        }
        return null
    }

    fun batteryDeep(context: Context): BatteryDeep {
        val cycles = batteryPath("cycle_count", "battery_cycle_count", "charge_cycles")?.second ?: 0L
        val full = batteryPath("charge_full", "charge_full_uah")?.second ?: 0L
        val design = batteryPath("charge_full_design")?.second ?: 0L
        val current = batteryPath("current_now", "current_avg", "current_now_uA")?.second ?: 0L
        val voltage = batteryPath("voltage_now", "voltage_avg")?.second ?: 0L
        val temp = batteryPath("temp", "battery_temp")?.second ?: 0L
        val health = readNode(BATTERY_ROOTS.first() + "/health").ifBlank { "не сообщает" }
        return BatteryDeep(
            cycles = cycles.toInt(),
            wearPercent = wearPercent(full, design),
            fullUah = full,
            designUah = design,
            currentMa = currentMaFrom(current),
            voltageMv = if (voltage > 10_000L) voltage / 1000f else voltage.toFloat(),
            celsius = tempCelsiusFrom(temp),
            healthy = health,
            note = if (cycles.toInt() == 0 && full == 0L) {
                "производитель закрыл счётчик циклов: на новых ядрах это обычное дело, " +
                    "зато ток, напряжение и температура читаются"
            } else {
                "данные взяты прямо из контроллера питания"
            }
        )
    }

    // ————————————————————— root: попытка и честный ответ —————————————————————

    private val ROOT_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/magisk/.core/bin/su", "/debug_ramdisk/su", "/system/bin/magisk"
    )

    /** Быстрая проверка: есть ли в системе чем получить root. */
    fun rootAvailable(): Boolean = ROOT_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) }

    /** Полная проверка: пробуем выполнить команду и смотрим на ответ. */
    suspend fun probeRoot(): String = withContext(Dispatchers.IO) {
        if (!rootAvailable()) return@withContext "root нет: ни одной su-оболочки в системе"
        runCatching {
            val process = ProcessBuilder(listOf("su", "-c", "id"))
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(1500, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                return@runCatching "su не ответил за полторы секунды — запрос отклонён"
            }
            val text = process.inputStream.bufferedReader().readText().trim()
            if (text.contains("uid=0")) "доступ есть: " + text.take(90) else "su ответил, но без прав: " + text.take(90)
        }.getOrElse { "не удалось запустить su: " + (it.message ?: "неизвестная ошибка") }
    }

    /**
     * Запись в узел ядра. Возвращает человеческий ответ: получилось или почему нет.
     * Значение чистится до букв и цифр — подстановка команд невозможна.
     */
    suspend fun writeNode(path: String, value: String): String = withContext(Dispatchers.IO) {
        val safe = cleanValue(value) ?: return@withContext "значение не годится: пусто или есть лишние символы"
        if (!path.startsWith("/sys/")) return@withContext "писать можно только в /sys"
        if (!rootAvailable()) return@withContext "без root система закрывает запись — покажем, что было бы"
        runCatching {
            val process = ProcessBuilder(listOf("su", "-c", "echo $safe > $path"))
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(2500, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                return@runCatching "запись не ответила за 2.5 секунды"
            }
            val text = process.inputStream.bufferedReader().readText().trim()
            val after = readNode(path)
            when {
                text.contains("denied", ignoreCase = true) -> "система отказала: " + text.take(80)
                after.isNotEmpty() && after.contains(safe) -> "записано: теперь " + after
                text.isNotEmpty() -> "ответ системы: " + text.take(80)
                else -> "команда прошла, но узел всё равно показывает прежнее значение"
            }
        }.getOrElse { "ошибка записи: " + (it.message ?: "неизвестно") }
    }

    // ————————————————————— мотор: резонанс и амплитуда —————————————————————

    data class Motor(
        val exists: Boolean,
        val amplitudeControl: Boolean,
        val resonantHz: Float?,
        val qFactor: Float?,
        val resolutionHz: Float?,
        val maxAmplitude: Int
    )

    private fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator ?: manager?.getVibrator(manager.vibratorIds.firstOrNull() ?: 0)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private fun byReflection(target: Any, method: String): Float? = runCatching {
        val value = target.javaClass.getMethod(method).invoke(target)
        (value as? Number)?.toFloat()
    }.getOrNull()

    fun motor(context: Context): Motor = runCatching {
        val vib = vibrator(context)
        if (vib == null || !vib.hasVibrator()) return@runCatching Motor(false, false, null, null, null, 0)
        Motor(
            exists = true,
            amplitudeControl = vib.hasAmplitudeControl(),
            // Резонанс и добротность мотор отдаёт не на всех Android — спрашиваем мягко.
            resonantHz = byReflection(vib, "getResonantFrequency"),
            qFactor = byReflection(vib, "getQFactor"),
            resolutionHz = byReflection(vib, "getFrequencyResolution"),
            maxAmplitude = 255
        )
    }.getOrElse { Motor(false, false, null, null, null, 0) }

    /**
     * Точки на резонансном мотор: частота в герцах и амплитуда в процентах.
     * Ниже 20 Гц мотор стучит, выше 300 Гц уже не успевает за сигналом.
     */
    fun hapticPattern(hz: Int, amplitude: Int, millis: Int): LongArray {
        val period = (1000f / hz.coerceIn(20, 300)).toLong().coerceAtLeast(2L)
        val on = (period * amplitude.coerceIn(5, 100) / 100f).toLong().coerceAtLeast(1L)
        val off = (period - on).coerceAtLeast(1L)
        val steps = (millis / period).toInt().coerceIn(1, 400)
        val pattern = LongArray(steps * 2 + 1)
        pattern[0] = 0L
        for (i in 0 until steps) {
            pattern[i * 2 + 1] = on
            pattern[i * 2 + 2] = off
        }
        return pattern
    }

    // ————————————————————— светодиод вспышки: это настоящий драйвер —————————————————————

    private fun flashCamera(context: Context): Pair<CameraManager, String>? = runCatching {
        val manager = context.getSystemService(CameraManager::class.java) ?: return@runCatching null
        val id = manager.cameraIdList.firstOrNull { cameraId ->
            manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return@runCatching null
        manager to id
    }.getOrNull()

    /** Максимальный уровень яркости вспышки: 0 — драйвер не умеет ступени. */
    fun torchMaxLevel(context: Context): Int = runCatching {
        if (Build.VERSION.SDK_INT < 33) return@runCatching 0
        val (manager, id) = flashCamera(context) ?: return@runCatching 0
        manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 0
    }.getOrDefault(0)

    fun torchLevel(context: Context): Int = runCatching {
        if (Build.VERSION.SDK_INT < 33) return@runCatching 0
        val (manager, id) = flashCamera(context) ?: return@runCatching 0
        manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 0
    }.getOrDefault(0)

    /** Включить вспышку на конкретной ступени — управление током светодиода. */
    fun setTorchLevel(context: Context, level: Int): Boolean = runCatching {
        val (manager, id) = flashCamera(context) ?: return false
        if (Build.VERSION.SDK_INT >= 33 && level > 0) {
            manager.turnOnTorchWithStrengthLevel(id, level.coerceAtLeast(1))
        } else {
            manager.setTorchMode(id, level > 0)
        }
        true
    }.getOrDefault(false)

    fun setTorchOff(context: Context): Boolean = runCatching {
        val (manager, id) = flashCamera(context) ?: return false
        manager.setTorchMode(id, false)
        true
    }.getOrDefault(false)

    // ————————————————————— магнитное эхо: слышим поле мотора —————————————————————

    /**
     * Слушаем магнитометр, пока работает мотор: катушка вибрации создаёт поле,
     * и его видно как всплеск. Простой способ увидеть, что мотор живой.
     */
    fun magneticEcho(context: Context, onValue: (Float) -> Unit): () -> Unit {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return {}
        // Только публичный датчик: «always-on» сенсоры на Android 16 роняют процесс
        // при подписке (`registerAonListener`), поэтому фильтр обязателен — как в HackTools.
        val sensor = manager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)
            ?.takeIf { !DeviceLab.isSystemOnlySensor(it.type, it.name, it.stringType.orEmpty()) }
            ?: return {}
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val x = event.values.getOrNull(0) ?: 0f
                val y = event.values.getOrNull(1) ?: 0f
                val z = event.values.getOrNull(2) ?: 0f
                onValue(kotlin.math.sqrt(x * x + y * y + z * z))
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        runCatching { manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) }
        return { runCatching { manager.unregisterListener(listener) } }
    }

    // ————————————————————— энергия: сколько ядро тратит сейчас —————————————————————

    /** Питание в ваттах — то, что реально потребляет плата в этот момент. */
    fun watts(currentMa: Float, voltageMv: Float): Float =
        kotlin.math.abs(currentMa) * voltageMv / 1_000_000f

    /** Узлы планировщика и памяти: то, что можно показать, не имея root. */
    fun memoryNodes(): List<Node> = listOf(
        Node("свободно в ОЗУ", "/proc/meminfo", readNode("/proc/meminfo").lineSequence()
            .firstOrNull { it.startsWith("MemAvailable") }?.trim().orEmpty(), "живая память"),
        Node("давление памяти", "/proc/pressure/memory", readNode("/proc/pressure/memory"), "как система ждёт освобождения памяти")
    )
}
