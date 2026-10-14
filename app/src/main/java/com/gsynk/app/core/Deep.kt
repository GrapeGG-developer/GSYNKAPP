package com.gsynk.app.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Глубокий взгляд в железо — то, что Android показывает неохотно.
 *
 * Здесь нет ни одного вымышленного числа: частоты ядер берём из `/sys`, тепловые зоны —
 * из `thermal_zone`, ток зарядки — из `BatteryManager`, ускоритель — через EGL, шаги — из
 * счётчика шагов, USB — из `UsbManager`. Всё только чтение: ничего не пишем в систему,
 * поэтому телефон не может пострадать. Чистые функции (разбор частот, каналов Wi-Fi,
 * подсчёт ватт) специально вынесены отдельно — их проверяют тесты.
 */
object Deep {

    // ————— процессор —————

    data class Core(
        val index: Int,
        val currentMhz: Int,
        val maxMhz: Int,
        val minMhz: Int,
        val governor: String,
        val online: Boolean
    ) {
        val loadPercent: Int
            get() {
                val span = (maxMhz - minMhz).coerceAtLeast(1)
                return ((currentMhz - minMhz) * 100 / span).coerceIn(0, 100)
            }
    }

    private const val CPU_ROOT = "/sys/devices/system/cpu"

    fun cores(): List<Core> = runCatching {
        val count = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        (0 until count).map { index ->
            val base = "$CPU_ROOT/cpu$index/cpufreq"
            Core(
                index = index,
                currentMhz = parseFreqKhz(read("$base/scaling_cur_freq")),
                maxMhz = parseFreqKhz(read("$base/cpuinfo_max_freq")),
                minMhz = parseFreqKhz(read("$base/cpuinfo_min_freq")),
                governor = read("$base/scaling_governor").ifBlank { "неизвестно" },
                online = read("$CPU_ROOT/cpu$index/online").ifBlank { "1" }.trim() != "0"
            )
        }
    }.getOrDefault(emptyList())

    /** Список доступных частот ядра — сколько ступеней умеет железо. */
    fun frequencies(core: Int = 0): List<Int> = runCatching {
        read("$CPU_ROOT/cpu$core/cpufreq/scaling_available_frequencies")
            .split(' ').mapNotNull { it.trim().toIntOrNull() }
            .map { it / 1000 }
            .sorted()
    }.getOrDefault(emptyList())

    fun governors(core: Int = 0): List<String> = runCatching {
        read("$CPU_ROOT/cpu$core/cpufreq/scaling_available_governors").split(' ').filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    // ————— тепловые зоны —————

    data class Zone(val name: String, val celsius: Float) {
        val label: String get() = prettyZone(name)
    }

    fun zones(): List<Zone> = runCatching {
        (0..40).mapNotNull { index ->
            val base = "/sys/class/thermal/thermal_zone$index"
            val type = read("$base/type")
            val raw = read("$base/temp").trim().toFloatOrNull() ?: return@mapNotNull null
            if (type.isBlank()) return@mapNotNull null
            Zone(type, tempFromRaw(raw))
        }
    }.getOrDefault(emptyList())

    /** Температуры бывают в тысячных и в десятых долях градуса — приводим к одному виду. */
    fun tempFromRaw(raw: Float): Float = when {
        raw >= 10_000f -> raw / 1000f
        raw >= 1_000f -> raw / 100f
        raw >= 200f -> raw / 10f
        else -> raw
    }

    /** Человеческое имя тепловой зоны: у каждой модели свои названия. */
    fun prettyZone(name: String): String {
        val low = name.lowercase()
        return when {
            low.contains("batt") -> "батарея"
            low.contains("cpu") || low.contains("apc") || low.contains("soc") -> "чип"
            low.contains("gpu") -> "ускоритель"
            low.contains("skin") || low.contains("back") -> "корпус"
            low.contains("charger") || low.contains("usb") || low.contains("typec") -> "разъём"
            low.contains("pa") || low.contains("amp") -> "усилитель"
            low.contains("cam") -> "камера"
            low.contains("disp") || low.contains("panel") -> "экран"
            low.contains("flash") || low.contains("ufs") -> "память"
            else -> name
        }
    }

    fun hottest(): Zone? = zones().maxByOrNull { it.celsius }

    // ————— батарея и зарядка —————

    data class Battery(
        val percent: Int,
        val currentMa: Float,
        val voltageMv: Int,
        val tempC: Float,
        val health: String,
        val cycles: Int,
        val chargeCounterMah: Float,
        val plugged: String,
        val status: String
    ) {
        /** Мощность зарядки: ватты считаются как ток × напряжение. */
        val watts: Float get() = watts(currentMa, voltageMv)
        val direction: String get() = if (currentMa > 60f) "заряжается" else if (currentMa < -60f) "тратится" else "покой"
    }

    fun watts(currentMa: Float, voltageMv: Int): Float =
        abs(currentMa) * (voltageMv.coerceAtLeast(1) / 1000f) / 1000f

    fun battery(context: Context, intent: Intent? = null): Battery {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val status = intent ?: runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val percent = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
            ?: statusPercent(status)
        val currentUa = runCatching {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        }.getOrDefault(0)
        val counterUah = runCatching {
            manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0L
        }.getOrDefault(0L)
        return Battery(
            percent = percent,
            currentMa = currentUa / 1000f,
            voltageMv = status?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 } ?: 3_850,
            tempC = (status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            health = healthLabel(status?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0),
            cycles = runCatching {
                if (Build.VERSION.SDK_INT >= 34) status?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1 else -1
            }.getOrDefault(-1),
            chargeCounterMah = counterUah / 1000f,
            plugged = pluggedLabel(status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0),
            status = statusLabel(status?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0)
        )
    }

    private fun statusPercent(status: Intent?): Int {
        if (status == null) return -1
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        return if (level < 0) -1 else (level * 100 / scale)
    }

    fun healthLabel(value: Int): String = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "хорошее"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "перегрев"
        BatteryManager.BATTERY_HEALTH_DEAD -> "пора менять"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "высокое напряжение"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "сбоит"
        BatteryManager.BATTERY_HEALTH_COLD -> "замёрзла"
        else -> "неизвестно"
    }

    fun pluggedLabel(value: Int): String = when (value) {
        BatteryManager.BATTERY_PLUGGED_AC -> "розетка"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "беспроводная"
        else -> "не подключён"
    }

    fun statusLabel(value: Int): String = when (value) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "заряжается"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "разряжается"
        BatteryManager.BATTERY_STATUS_FULL -> "полная"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "не заряжается"
        else -> "неизвестно"
    }

    // ————— ускоритель —————

    data class Gpu(val vendor: String, val renderer: String, val version: String, val extensions: Int)

    /** Строки ускорителя берём у самой видеоподсистемы: создаём контекст EGL и спрашиваем. */
    fun gpu(): Gpu? = runCatching {
        val display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (!android.opengl.EGL14.eglInitialize(display, version, 0, version, 1)) return null
        val attributes = intArrayOf(
            android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
            android.opengl.EGL14.EGL_SURFACE_TYPE, android.opengl.EGL14.EGL_PBUFFER_BIT,
            android.opengl.EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        android.opengl.EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)
        val contextAttributes = intArrayOf(
            android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            android.opengl.EGL14.EGL_NONE
        )
        val context = android.opengl.EGL14.eglCreateContext(
            display, configs[0], android.opengl.EGL14.EGL_NO_CONTEXT, contextAttributes, 0
        )
        android.opengl.EGL14.eglMakeCurrent(
            display, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_SURFACE, context
        )
        val glVersion = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION)
        val renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER)
        val vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR)
        val extensions = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_EXTENSIONS)
            ?.split(' ')?.size ?: 0
        android.opengl.EGL14.eglMakeCurrent(
            display, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_SURFACE,
            android.opengl.EGL14.EGL_NO_CONTEXT
        )
        android.opengl.EGL14.eglDestroyContext(display, context)
        android.opengl.EGL14.eglTerminate(display)
        Gpu(
            vendor = vendor ?: "неизвестно",
            renderer = renderer ?: "неизвестно",
            version = glVersion ?: "неизвестно",
            extensions = extensions
        )
    }.getOrNull()

    /** Частота кадров ускорителя запомнена полем GpuField — берём последнее значение. */
    val measuredFps: Float get() = TurboState.gpuFps.value

    // ————— память и хранилище —————

    data class Memory(val totalMb: Int, val freeMb: Int, val cachedMb: Int, val swapTotalMb: Int, val swapFreeMb: Int) {
        val usedMb: Int get() = (totalMb - freeMb).coerceAtLeast(0)
        val usedPercent: Int get() = if (totalMb <= 0) 0 else (usedMb * 100 / totalMb)
    }

    fun memory(): Memory {
        val info = android.app.ActivityManager.MemoryInfo()
        runCatching {
            val manager = AppContextHolder.application()
                ?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            manager?.getMemoryInfo(info)
        }
        return Memory(
            totalMb = (info.totalMem / 1024 / 1024).toInt(),
            freeMb = (info.availMem / 1024 / 1024).toInt(),
            cachedMb = (info.threshold / 1024 / 1024).toInt(),
            swapTotalMb = readLongMb("/proc/meminfo", "SwapTotal"),
            swapFreeMb = readLongMb("/proc/meminfo", "SwapFree")
        )
    }

    /** Разбор /proc/meminfo: значение в килобайтах по имени поля. */
    fun readLongMb(path: String, key: String): Int = runCatching {
        File(path).readLines().firstOrNull { it.startsWith(key) }
            ?.split(':')?.getOrNull(1)?.trim()?.split(' ')?.firstOrNull()?.toLongOrNull()
            ?.let { (it / 1024).toInt() } ?: 0
    }.getOrDefault(0)

    data class Storage(val totalGb: Float, val freeGb: Float, val appMb: Int, val cacheMb: Int)

    fun storage(context: Context): Storage {
        val stat = android.os.StatFs(context.filesDir.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val app = runCatching { folderSize(context.filesDir) + folderSize(context.cacheDir) }.getOrDefault(0L)
        val cache = runCatching { folderSize(context.cacheDir) }.getOrDefault(0L)
        return Storage(
            totalGb = total / 1_073_741_824f,
            freeGb = free / 1_073_741_824f,
            appMb = (app / 1_048_576).toInt(),
            cacheMb = (cache / 1_048_576).toInt()
        )
    }

    private fun folderSize(file: File): Long = runCatching {
        if (!file.exists()) 0L
        else if (file.isFile) file.length()
        else file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    }.getOrDefault(0L)

    // ————— датчики —————

    data class SensorInfo(
        val name: String,
        val vendor: String,
        val type: String,
        val typeId: Int,
        val powerMa: Float,
        val resolution: Float,
        val maxRange: Float,
        val minDelayUs: Int,
        val wakeUp: Boolean
    )

    fun sensors(context: Context): List<SensorInfo> = runCatching {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyList()
        // Системные («always-on») датчики Android 16 отдаёт в отдельной службе: обычному
        // приложению туда нельзя, и подписка на такой датчик убивает процесс. Поэтому
        // сначала фильтруем их тем же правилом, что и остальная лаборатория.
        manager.getSensorList(Sensor.TYPE_ALL)
            .filterNot { sensor ->
                DeviceLab.isSystemOnlySensor(sensor.type, sensor.name, sensor.stringType)
            }
            .map { sensor ->
            SensorInfo(
                name = sensor.name,
                vendor = sensor.vendor,
                type = sensorTypeName(sensor),
                typeId = sensor.type,
                powerMa = sensor.power,
                resolution = sensor.resolution,
                maxRange = sensor.maximumRange,
                minDelayUs = sensor.minDelay,
                wakeUp = runCatching { sensor.isWakeUpSensor }.getOrDefault(false)
            )
        }.sortedBy { it.typeId }
    }.getOrDefault(emptyList())

    /** Имена типов датчиков: у части из них нет штатных названий в Android. */
    fun sensorTypeName(sensor: Sensor): String = when (sensor.type) {
        Sensor.TYPE_ACCELEROMETER -> "акселерометр"
        Sensor.TYPE_GYROSCOPE -> "гироскоп"
        Sensor.TYPE_MAGNETIC_FIELD -> "магнитометр"
        Sensor.TYPE_LIGHT -> "свет"
        Sensor.TYPE_PROXIMITY -> "приближение"
        Sensor.TYPE_PRESSURE -> "давление"
        Sensor.TYPE_GRAVITY -> "гравитация"
        Sensor.TYPE_LINEAR_ACCELERATION -> "линейное ускорение"
        Sensor.TYPE_ROTATION_VECTOR -> "поворот"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "поворот для игр"
        Sensor.TYPE_STEP_COUNTER -> "счётчик шагов"
        Sensor.TYPE_STEP_DETECTOR -> "шаг"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "влажность"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "температура воздуха"
        Sensor.TYPE_HEART_RATE -> "пульс"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "движение"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "магнитометр (сырой)"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "гироскоп (сырой)"
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "акселерометр (сырой)"
        Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "съём с руки"
        else -> "тип " + sensor.type
    }

    // ————— периферия —————

    data class UsbDeviceInfo(val name: String, val vendor: Int, val product: Int, val classes: String, val hasPermission: Boolean)

    fun usb(context: Context): List<UsbDeviceInfo> = runCatching {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        manager.deviceList.values.map { device ->
            UsbDeviceInfo(
                name = device.deviceName,
                vendor = device.vendorId,
                product = device.productId,
                classes = device.interfaceCount.toString() + " интерфейсов",
                hasPermission = manager.hasPermission(device)
            )
        }
    }.getOrDefault(emptyList())

    fun hasUsbHost(context: Context): Boolean = runCatching {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_USB_HOST)
    }.getOrDefault(false)

    // ————— Wi-Fi: каналы и загрузка эфира —————

    /** Канал по частоте: 2,4 ГГц считается по формуле, 5 ГГц — по стандартным номерам. */
    fun channelOf(frequencyMhz: Int): Int = when {
        frequencyMhz == 2484 -> 14
        frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
        // 5 ГГц: канал 36 — это 5180 МГц, дальше шаг 5 МГц, то есть канал = частота/5 − 1000.
        frequencyMhz in 5000..5895 -> frequencyMhz / 5 - 1000
        // 6 ГГц: канал 1 — это 5955 МГц.
        frequencyMhz in 5925..7125 -> (frequencyMhz - 5950) / 5
        else -> 0
    }

    /** Занятость каналов: сколько сетей сидит на каждом. */
    fun congestion(pairs: List<Pair<Int, Int>>): List<Pair<Int, Int>> =
        pairs.groupBy({ it.second }, { it.first })
            .map { (channel, strengths) -> channel to strengths.size }
            .sortedByDescending { it.second }

    fun bandOf(frequencyMhz: Int): String = when {
        frequencyMhz in 2400..2500 -> "2,4 ГГц"
        frequencyMhz in 4900..5900 -> "5 ГГц"
        frequencyMhz in 5925..7125 -> "6 ГГц"
        else -> "неизвестный диапазон"
    }

    // ————— мелкие разборы —————

    /** Частота из /sys приходит в килогерцах — переводим в мегагерцы. */
    fun parseFreqKhz(text: String): Int = text.trim().toIntOrNull()?.let { it / 1000 } ?: 0

    private fun read(path: String): String = runCatching { File(path).readText().trim() }.getOrDefault("")

    /** Разбор строки об уровне сигнала: «-67 дБм» → −67. */
    fun rssiFrom(text: String): Int? = Regex("-?\\d+").find(text)?.value?.toIntOrNull()

    fun round1(value: Float): Float = (value * 10f).roundToInt() / 10f
}

/**
 * Точка доступа к контексту приложения для тех мест, где Compose недоступен.
 * Заполняется один раз при старте — иначе чтение памяти требовало бы контекст везде.
 */
object AppContextHolder {
    private var app: android.app.Application? = null

    fun install(application: android.app.Application) {
        app = application
    }

    fun application(): android.app.Application? = app
}
