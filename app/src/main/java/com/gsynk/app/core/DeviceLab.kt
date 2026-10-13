package com.gsynk.app.core

import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.os.Build
import android.provider.Settings
import kotlin.math.abs

/**
 * Низкоуровневая лаборатория: то, чего нет в магазинах приложений.
 *
 * Здесь только безопасные эксперименты с железом телефона — то, что можно проверить
 * и вернуть назад: экран, датчики, катушки, инфракрасный порт, скрытые настройки
 * Android и HyperOS. Ничего, что может превратить телефон в кирпич: загрузчик,
 * разделы прошивки, режимы загрузки и прошивка не трогаются вообще.
 */
object DeviceLab {

    // ————————————————————————————————————————————————
    //  Метка безопасности: показываем на каждом опасном экране
    // ————————————————————————————————————————————————

    const val SAFETY_NOTE =
        "Безопасно: только то, что возвращается назад. Загрузчик, прошивка, режимы " +
            "восстановления и разделы Android не трогаются — окирпичить телефон отсюда нельзя."

    // ————————————————————————————————————————————————
    //  Скрытые экраны Android и HyperOS
    // ————————————————————————————————————————————————

    val dialCodes: List<Pair<String, String>> = listOf(
        "*#*#6484#*#*" to "Тест железа (CIT): экран, датчики, динамики",
        "*#*#4636#*#*" to "Информация о телефоне и сети",
        "*#*#284#*#*" to "Журнал отладки (лог MIUI)",
        "*#*#426#*#*" to "Журнал Google Play Services",
        "*#*#759#*#*" to "Сервис Google-сертификации",
        "*#06#" to "IMEI и серийные номера"
    )

    fun openDialer(context: Context, code: String): Boolean = runCatching {
        val number = code.replace("#", "%23").replace("*", "%2A")
        context.startActivity(
            Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + number))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    // ————————————————————————————————————————————————
    //  Инфракрасный порт (есть у Poco X3 Pro и многих Xiaomi)
    // ————————————————————————————————————————————————

    /** Простая команда ИК-пульта: несущая и последовательность вспышек в микросекундах. */
    data class IrCommand(val carrier: Int, val pattern: IntArray)

    object Ir {
        /** Протокол NEC: 9 мс вспышка, 4.5 мс пауза, затем биты 560/1690 мкс. */
        fun nec(address: Int, command: Int): IrCommand {
            val pattern = ArrayList<Int>(70)
            pattern += 9000
            pattern += 4500
            fun bits(value: Int, count: Int) {
                for (index in 0 until count) {
                    pattern += 560
                    val bit = (value shr index) and 1
                    pattern += if (bit == 1) 1690 else 560
                }
            }
            bits(address, 8)
            bits(address.inv() and 0xFF, 8)
            bits(command, 8)
            bits(command.inv() and 0xFF, 8)
            pattern += 560
            return IrCommand(38_000, pattern.toIntArray())
        }
    }

    data class TvKey(val title: String, val nec: Pair<Int, Int>?)

    /** Наборы кодов для популярных бюджетных телевизоров. */
    val tvBrands: List<Pair<String, Int>> = listOf(
        "Xiaomi / Mi TV" to 0x4040,
        "Samsung" to 0x0707,
        "LG" to 0x20DF,
        "Philips" to 0x0018,
        "Sony" to 0x0A0A,
        "Haier / BBK" to 0x00FF
    )

    val tvKeys: List<TvKey> = listOf(
        TvKey("Питание", 0x0C to 0x0C),
        TvKey("Громче", 0x0C to 0x02),
        TvKey("Тише", 0x0C to 0x03),
        TvKey("Без звука", 0x0C to 0x09),
        TvKey("Канал +", 0x0C to 0x20),
        TvKey("Канал −", 0x0C to 0x21),
        TvKey("Меню", 0x0C to 0x40),
        TvKey("Домой", 0x0C to 0x7C)
    )

    fun hasIr(context: Context): Boolean =
        runCatching { context.packageManager.hasSystemFeature("android.hardware.consumerir") }
            .getOrDefault(false)

    fun transmit(context: Context, command: IrCommand): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            ?: return false
        if (!manager.hasIrEmitter()) return false
        manager.transmit(command.carrier, command.pattern)
        AppLog.event("Лаборатория", "ИК-команда отправлена, несущая " + command.carrier + " Гц")
        true
    }.getOrDefault(false)

    // ————————————————————————————————————————————————
    //  Катушки и вибрация
    // ————————————————————————————————————————————————

    /** Волновые шаблоны вибромотора — он и есть катушка индуктивности с сердечником. */
    enum class Buzz(val label: String, val hint: String) {
        TICK("Щелчок", "короткий удар, как у кнопки"),
        HEARTBEAT("Сердце", "двойной удар в ритме сердца"),
        ENGINE("Мотор", "частота растёт, как у разгоняющегося двигателя"),
        MORSE_SOS("SOS", "точки и тире азбукой Морзе"),
        WAVE("Волна", "плавное нарастание и спад"),
        PURGE("Продувка", "быстрый треск для чистки вибромотора")
    }

    fun buzzPattern(kind: Buzz): LongArray = when (kind) {
        Buzz.TICK -> longArrayOf(0, 28)
        Buzz.HEARTBEAT -> longArrayOf(0, 90, 120, 60, 380, 90, 120, 60, 900)
        Buzz.ENGINE -> longArrayOf(0, 40, 30, 60, 30, 90, 30, 130, 30, 180, 30, 240, 30, 320)
        Buzz.MORSE_SOS -> {
            // ··· ——— ···
            val pattern = ArrayList<Long>()
            pattern += 0
            repeat(3) { pattern += 90; pattern += 90 }
            repeat(3) { pattern += 260; pattern += 90 }
            repeat(3) { pattern += 90; pattern += 90 }
            pattern.toLongArray()
        }

        Buzz.WAVE -> longArrayOf(0, 24, 24, 36, 24, 56, 24, 84, 24, 120, 40, 120, 24, 84, 24, 56, 24, 36, 24)
        Buzz.PURGE -> LongArray(41) { index -> if (index == 0) 0L else if (index % 2 == 1) 12L else 9L }
    }

    /**
     * «Музыка» на вибромотора: от 1 до 16 ударов в секунду.
     * Медленно — постукивание, быстро — жужжание, на 16 Гц слышно как бас через стол.
     */
    fun buzzRhythm(hertz: Int, seconds: Int): LongArray {
        val rate = hertz.coerceIn(1, 16)
        val period = 1000L / rate
        val pulses = rate * seconds.coerceIn(1, 20)
        val pattern = ArrayList<Long>(pulses * 2 + 1)
        pattern += 0
        repeat(pulses) {
            pattern += (period / 2).coerceAtLeast(4)
            pattern += (period - period / 2).coerceAtLeast(4)
        }
        return pattern.toLongArray()
    }

    // ————————————————————————————————————————————————
    //  Экран
    // ————————————————————————————————————————————————

    /** Пресеты стробоскопа: частота и цвета. */
    data class Strobe(val label: String, val hertz: Float, val hint: String)

    val strobes: List<Strobe> = listOf(
        Strobe("Медленный", 2f, "2 вспышки в секунду — спокойно"),
        Strobe("Строб", 8f, "8 в секунду — как на дискотеке"),
        Strobe("Быстрый", 15f, "15 в секунду — уже мелькание"),
        Strobe("Пиксель-буря", 25f, "25 в секунду — максимум панели")
    )

    fun strobeClamp(hertz: Float): Float = hertz.coerceIn(0.2f, 30f)

    /** Палитры для мигания: от мягких до максимально злых. */
    val strobeColors: List<Pair<String, IntArray>> = listOf(
        "Белый — чёрный" to intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
        "Фиолетовый — чёрный" to intArrayOf(0xFF7C3AED.toInt(), 0xFF0A0713.toInt()),
        "Красный — чёрный" to intArrayOf(0xFFEF4444.toInt(), 0xFF000000.toInt()),
        "Радуга" to intArrayOf(
            0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF22C55E.toInt(),
            0xFF22D3EE.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt()
        )
    )

    /** Проверки экрана: то, что обычно ищут через веб-тесты на весь экран. */
    enum class ScreenTest(val label: String, val hint: String) {
        DEAD_PIXELS("Битые пиксели", "сплошные цвета: заметно мёртвые точки и засветы"),
        BURN_IN("Выгорание AMOLED", "шахматка: проверка остаточного изображения"),
        CLEAN("Очистка пикселей", "быстрая смена цветов разгоняет люминофоры"),
        GRADIENT("Ровность градиента", "плавный переход: видны полосы и бандинг"),
        PWM("ШИМ и мерцание", "низкая яркость + полоса: оцените мерцание глазом"),
        TOUCH("Мультитач", "рисуйте пальцами — видно, сколько касаний слышит панель"),
        GHOST("Остаточный след", "медленное движение серых полос — тест времени отклика");

        companion object {
            fun titles(): List<String> = entries.map { it.label }
        }
    }

    /** Плоские поля для теста битых пикселей. */
    val flatFields: List<Pair<String, Int>> = listOf(
        "Белый" to 0xFFFFFFFF.toInt(),
        "Красный" to 0xFFFF0000.toInt(),
        "Зелёный" to 0xFF00FF00.toInt(),
        "Синий" to 0xFF0000FF.toInt(),
        "Голубой" to 0xFF00FFFF.toInt(),
        "Пурпурный" to 0xFFFF00FF.toInt(),
        "Жёлтый" to 0xFFFFFF00.toInt(),
        "Чёрный" to 0xFF000000.toInt(),
        "Серый 50 проц." to 0xFF808080.toInt()
    )

    // ————————————————————————————————————————————————
    //  Датчики и индукция
    // ————————————————————————————————————————————————

    /** Насколько сильно поле — «металлоискатель» на магнитном датчике. */
    fun magneticStrength(x: Float, y: Float, z: Float): Float =
        kotlin.math.sqrt(x * x + y * y + z * z)

    /** Отклонение от фонового уровня: разница и есть «металл рядом». */
    fun metalDelta(current: Float, baseline: Float): Float = abs(current - baseline)

    /** Азимут компаса по магнитометру и гравитации. */
    fun compassHeading(mx: Float, my: Float): Float {
        val angle = Math.toDegrees(kotlin.math.atan2(-my.toDouble(), mx.toDouble())).toFloat()
        return (angle + 360f) % 360f
    }

    /** Уровень: наклон в градусах по осям. */
    fun tiltDegrees(x: Float, y: Float): Pair<Float, Float> {
        // наклон в градусах: лежит ровно — ноль, на боку — плюс-минус 90
        val pitch = Math.toDegrees(kotlin.math.atan2(-x.toDouble(), 9.81)).toFloat()
        val roll = Math.toDegrees(kotlin.math.atan2(y.toDouble(), 9.81)).toFloat()
        return pitch.coerceIn(-90f, 90f) to roll.coerceIn(-90f, 90f)
    }

    /** Высота по давлению: барометрическая формула, море на 1013.25 гПа. */
    fun altitudeMeters(pressureHpa: Float): Float {
        if (pressureHpa <= 0f) return 0f
        return 44330f * (1f - Math.pow(pressureHpa / 1013.25, 0.1903).toFloat())
    }

    /** Громкость звука в децибелах по амплитуде микрофона (0..32767). */
    fun decibelsFromAmplitude(amplitude: Float): Float {
        if (amplitude <= 1f) return 0f
        return (20f * kotlin.math.log10(amplitude.toDouble())).toFloat()
    }

    /**
     * Какие датчики приложение имеет право слушать.
     *
     * Android 16 завёл «always-on» датчики (AON): их регистрация идёт через системную
     * службу и требует прав системы — обычному приложению это запрещено, и попытка
     * роняет процесс с `SecurityException: ... registerAonListener ... asks to run as
     * user -2`. Поэтому слушаем только публичные типы датчиков и никогда — те, что
     * помечены как AON или отданы производителем (тип от 0x10000).
     */
    private val readableTypes = setOf(
        android.hardware.Sensor.TYPE_ACCELEROMETER,
        android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE,
        android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR,
        android.hardware.Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
        android.hardware.Sensor.TYPE_GRAVITY,
        android.hardware.Sensor.TYPE_GYROSCOPE,
        android.hardware.Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
        android.hardware.Sensor.TYPE_HEART_RATE,
        android.hardware.Sensor.TYPE_LIGHT,
        android.hardware.Sensor.TYPE_LINEAR_ACCELERATION,
        android.hardware.Sensor.TYPE_MAGNETIC_FIELD,
        android.hardware.Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
        android.hardware.Sensor.TYPE_PRESSURE,
        android.hardware.Sensor.TYPE_PROXIMITY,
        android.hardware.Sensor.TYPE_RELATIVE_HUMIDITY,
        android.hardware.Sensor.TYPE_ROTATION_VECTOR,
        android.hardware.Sensor.TYPE_STEP_COUNTER,
        android.hardware.Sensor.TYPE_STEP_DETECTOR
    )

    /** Крупный шаг для «особых» датчиков производителя: всё, что выше, — системное. */
    const val VENDOR_SENSOR_BASE = 0x10000

    fun canReadSensorType(type: Int): Boolean = type in readableTypes

    /** Датчик, который система не пустит слушать простое приложение. */
    fun isSystemOnlySensor(type: Int, name: String, stringType: String): Boolean {
        if (type >= VENDOR_SENSOR_BASE) return true
        val haystack = (name + " " + stringType).lowercase()
        if (haystack.contains("aon")) return true
        if (haystack.contains("always on") || haystack.contains("always-on")) return true
        return !canReadSensorType(type)
    }

    fun hyperOs(context: Context): Boolean = runCatching {
        val version = Build.VERSION.RELEASE.orEmpty()
        val brand = (Build.BRAND ?: "").lowercase()
        val isMi = brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
        val hasMiui = context.packageManager.getPackageInfo("com.miui.securitycenter", 0) != null
        isMi && hasMiui && version.isNotBlank()
    }.getOrDefault(false)

    /** Крупная вибрация-предупреждение перед «опасными» экранами. */
    fun shakeWarning(context: Context) = AutomationActions.vibrate(context, 120L)
}
