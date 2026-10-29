package com.gsynk.app

import com.gsynk.app.core.DeviceLab
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Сторож датчиков.
 *
 * Отчёт с телефона 13.10.2026, 19:29 (Xiaomi, Android 16) показал вылет:
 *
 * ```
 * SecurityException: Permission Denial: service from com.gsynk.app.debug asks to run as
 * user -2 ... requires INTERACT_ACROSS_USERS_FULL
 *   at SystemSensorManagerImpl.registerAonListener
 * ```
 *
 * Android 16 держит «always-on» датчики (AON) в отдельной системной службе: обычному
 * приложению туда нельзя, и подписка на такой датчик убивает процесс. Исключение летит
 * из обработчика главного потока, поэтому `runCatching` вокруг `registerListener` не
 * спасает — спасает только одно: на системный датчик не подписываться вовсе.
 *
 * Тест читает исходники и требует, чтобы каждая точка, где приложение берёт датчик
 * (`getSensorList`, `getDefaultSensor`, `registerListener`), шла через фильтр
 * `DeviceLab.isSystemOnlySensor`.
 */
class SensorGuardTest {

    private val ui = File("src/main/java/com/gsynk/app/ui")
    private val core = File("src/main/java/com/gsynk/app/core")

    private fun sources(dir: File): List<File> =
        dir.listFiles { f: File -> f.name.endsWith(".kt") }?.toList().orEmpty()

    private fun sensorFiles(): List<File> = (sources(ui) + sources(core)).filter { file ->
        val text = file.readText()
        text.contains("getSensorList(") ||
            text.contains("getDefaultSensor(") ||
            text.contains("registerListener(")
    }

    /** Окно перед вызовом: там должен быть виден фильтр. */
    private fun guarded(text: String, index: Int, window: Int = 1200): Boolean {
        val from = (index - window).coerceAtLeast(0)
        val before = text.substring(from, index)
        return before.contains("isSystemOnlySensor") || before.contains("pick()")
    }

    @Test
    fun everySensorFileKnowsTheFilter() {
        val files = sensorFiles()
        assertTrue("датчики должны где-то использоваться", files.isNotEmpty())
        files.forEach { file ->
            assertTrue(
                "«${file.name}» трогает датчики, но не проверяет их через " +
                    "DeviceLab.isSystemOnlySensor — на Android 16 это вылет",
                file.readText().contains("isSystemOnlySensor")
            )
        }
    }

    @Test
    fun everySubscriptionIsBehindTheFilter() {
        var checked = 0
        sensorFiles().forEach { file ->
            val text = file.readText()
            Regex("registerListener\\(").findAll(text).forEach { match ->
                checked++
                assertTrue(
                    "в «${file.name}» подписка на датчик без фильтра — так процесс и падал",
                    guarded(text, match.range.first)
                )
            }
        }
        assertTrue("должна быть хотя бы одна подписка (проверено $checked)", checked > 0)
    }

    @Test
    fun bulkSensorListIsFilteredBeforeRegister() {
        val text = File(ui, "HardwareScreens.kt").readText()
        assertTrue(
            "список всех датчиков обязан фильтроваться до подписки",
            text.contains("getSensorList(Sensor.TYPE_ALL)") &&
                text.contains("filterNot") &&
                text.contains("isSystemOnlySensor")
        )
        assertTrue(
            "пропущенные датчики обязаны попадать в журнал: иначе следующий отчёт не расскажет, кто виноват",
            text.contains("пропущено системных")
        )
    }

    @Test
    fun magnetometerAndAccelerometerUseGuard() {
        val text = File(core, "HackTools.kt").readText()
        assertTrue("магнитометр на месте", text.contains("getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)"))
        assertTrue("акселерометр на месте", text.contains("getDefaultSensor(Sensor.TYPE_ACCELEROMETER)"))
        val guards = Regex("isSystemOnlySensor").findAll(text).count()
        assertTrue("оба датчика прикрыты фильтром (найдено $guards)", guards >= 2)
    }

    @Test
    fun filterKnowsAonSensors() {
        // Самопроверка фильтра: имя с AON и тип производителя отсекаются, публичный — нет.
        assertTrue(DeviceLab.isSystemOnlySensor(1, "AML AON sensor", ""))
        assertTrue(DeviceLab.isSystemOnlySensor(DeviceLab.VENDOR_SENSOR_BASE + 3, "Xiaomi", ""))
        assertTrue(!DeviceLab.isSystemOnlySensor(android.hardware.Sensor.TYPE_ACCELEROMETER, "Accelerometer", ""))
    }
}
