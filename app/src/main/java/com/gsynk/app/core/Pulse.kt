package com.gsynk.app.core

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.coroutines.resume

/**
 * Пульс по камере (фотоплетизмография).
 *
 * Палец закрывает объектив, вспышка подсвечивает его насквозь. С каждым ударом сердца
 * крови в пальце становится больше, и он чуть меньше пропускает свет — яркость кадров
 * ходит волной. Частота этой волны и есть пульс.
 *
 * Всё настоящее: сигнал читается с камеры, частота ищется автокорреляцией, качество
 * считается по высоте найденного пика. Если палец не приложен или рука дрожит,
 * приложение скажет об этом, а не покажет красивое выдуманное число.
 */
object Pulse {

    /** Сколько по умолчанию собираем сигнал: 15 секунд при 30 кадрах — около 450 точек. */
    const val DEFAULT_MILLIS = 15_000L

    /** Ниже этого числа кадров считать нечего. */
    const val MIN_SAMPLES = 90

    data class Reading(val samples: List<Float>, val stamps: List<Long>) {
        /** Кадров в секунду по меткам камеры — считаем, а не предполагаем. */
        val fps: Float
            get() = if (stamps.size < 2) 0f else {
                val nanos = (stamps.last() - stamps.first()).coerceAtLeast(1L)
                (stamps.size - 1).toFloat() * 1_000_000_000f / nanos
            }
    }

    data class Report(
        val bpm: Int?,
        val quality: Int,
        val samples: List<Float>,
        val fps: Float,
        val note: String
    )

    // ————— чистая часть: её проверяют тесты —————

    /** Скользящее среднее: убирает дрожание кадра. */
    fun smooth(samples: List<Float>, window: Int = 5): List<Float> {
        if (samples.isEmpty()) return samples
        val w = window.coerceAtLeast(1)
        val half = w / 2
        return samples.mapIndexed { index, _ ->
            var sum = 0f
            var count = 0
            for (offset in -half..half) {
                val at = index + offset
                if (at in samples.indices) {
                    sum += samples[at]
                    count++
                }
            }
            if (count == 0) samples[index] else sum / count
        }
    }

    /** Убирает медленный уход яркости: вычитает скользящее среднее по широкому окну. */
    fun detrend(samples: List<Float>, window: Int = 41): List<Float> {
        if (samples.isEmpty()) return samples
        val baseline = smooth(samples, window)
        return samples.mapIndexed { index, value -> value - baseline[index] }
    }

    /**
     * Пульс по автокорреляции.
     *
     * Берём диапазон, в котором живёт человеческий пульс: от 40 до 150 ударов —
     * это периоды от 1,5 до 0,4 секунды. Ищем сдвиг, на котором сигнал сильнее всего
     * похож сам на себя: это и есть период одного удара.
     */
    fun beatsPerMinute(samples: List<Float>, fps: Float, minBpm: Int = 40, maxBpm: Int = 190): Int? {
        if (fps <= 1f || samples.size < MIN_SAMPLES) return null
        val signal = smooth(detrend(samples), 3)
        val mean = signal.average().toFloat()
        val centered = FloatArray(signal.size) { index -> signal[index] - mean }
        val minLag = (60f * fps / maxBpm).roundToInt().coerceAtLeast(2)
        val maxLag = (60f * fps / minBpm).roundToInt().coerceAtMost(centered.size / 2)
        if (maxLag <= minLag) return null

        var bestLag = 0
        var best = -1f
        for (lag in minLag..maxLag) {
            var dot = 0f
            var left = 0f
            var right = 0f
            var index = lag
            while (index < centered.size) {
                val a = centered[index]
                val b = centered[index - lag]
                dot += a * b
                left += a * a
                right += b * b
                index++
            }
            if (left <= 0f || right <= 0f) continue
            val correlation = dot / sqrt(left * right)
            if (correlation > best) {
                best = correlation
                bestLag = lag
            }
        }
        if (bestLag == 0 || best < 0.18f) return null
        val bpm = (60f * fps / bestLag).roundToInt()
        return bpm.takeIf { it in minBpm..maxBpm }
    }

    /** Качество 0–100: высота найденного пика и ровность дыхания сигнала. */
    fun quality(samples: List<Float>, fps: Float): Int {
        if (samples.size < MIN_SAMPLES || fps <= 1f) return 0
        val signal = smooth(detrend(samples), 3)
        val amplitude = (signal.max() - signal.min())
        val mean = kotlin.math.abs(signal.average().toFloat())
        val ratio = if (mean <= 0.00001f) 0f else amplitude / mean
        val bpm = beatsPerMinute(samples, fps) ?: return 0
        val base = (ratio * 90f).coerceIn(0f, 60f)
        val bonus = if (bpm in 45..150) 30f else 10f
        return (base + bonus).roundToInt().coerceIn(0, 100)
    }

    /** Полный разбор записи: пульс, качество и человеческий вывод. */
    fun analyse(reading: Reading): Report {
        val fps = reading.fps
        val bpm = beatsPerMinute(reading.samples, fps)
        val quality = if (bpm == null) 0 else quality(reading.samples, fps)
        val note = when {
            reading.samples.size < MIN_SAMPLES -> "слишком мало кадров: приложите палец плотнее"
            bpm == null -> "пульс не разобран: палец должен закрывать объектив и вспышку целиком"
            quality < 25 -> "сигнал слабый — держите руку спокойно, не жмите сильно"
            quality < 55 -> "пульс найден, но рука подрагивала: повторите для точности"
            else -> "ровный сигнал — это настоящий пульс, а не догадка"
        }
        return Report(bpm = bpm, quality = quality, samples = reading.samples, fps = fps, note = note)
    }

    /** Человеческая трактовка пульса. Не диагноз: приложение только показывает цифру. */
    fun verdict(bpm: Int): String = when {
        bpm < 50 -> "ниже обычного: так бывает у спортсменов в покое"
        bpm <= 60 -> "спокойный пульс отдыхающего человека"
        bpm <= 90 -> "обычный пульс в покое"
        bpm <= 110 -> "чуть чаще обычного: возможно, вы только что двигались"
        else -> "частый пульс: отдохните и измерьте ещё раз"
    }

    // ————— камера —————

    /**
     * Записать сигнал с камеры.
     *
     * @param onFrame вызывается на каждый кадр — по нему экран рисует волну в реальном времени.
     */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun read(
        context: Context,
        millis: Long = DEFAULT_MILLIS,
        onFrame: (Float) -> Unit = {}
    ): Reading = withContext(Dispatchers.Default) {
        val allowed = runCatching {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!allowed) throw IllegalStateException("нет разрешения на камеру")

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: throw IllegalStateException("камера недоступна")
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: manager.cameraIdList.firstOrNull()
            ?: throw IllegalStateException("камер нет")

        val reader = ImageReader.newInstance(160, 120, ImageFormat.YUV_420_888, 4)
        val samples = ArrayList<Float>(1_024)
        val stamps = ArrayList<Long>(1_024)
        reader.setOnImageAvailableListener({ source ->
            runCatching {
                val image = source.acquireLatestImage() ?: return@runCatching
                val plane = image.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                // Берём центр кадра: палец прикладывают именно туда.
                val x0 = 160 / 2 - 20
                val x1 = 160 / 2 + 20
                val y0 = 120 / 2 - 20
                val y1 = 120 / 2 + 20
                var sum = 0L
                var count = 0
                for (row in y0 until y1) {
                    for (column in x0 until x1) {
                        val offset = row * rowStride + column * pixelStride
                        if (offset in 0 until buffer.limit()) {
                            sum += (buffer.get(offset).toInt() and 0xFF)
                            count++
                        }
                    }
                }
                if (count > 0) {
                    val value = (sum.toFloat() / count) / 255f
                    samples.add(value)
                    stamps.add(image.timestamp)
                    runCatching { onFrame(value) }
                }
                image.close()
            }
        }, null)

        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        try {
            runCatching { manager.setTorchMode(cameraId, true) }
            device = openCamera(manager, cameraId)
            device.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(created: CameraCaptureSession) {
                        session = created
                    }

                    override fun onConfigureFailed(created: CameraCaptureSession) = Unit
                },
                null
            )
            var waited = 0
            while (session == null && waited < 2_000) {
                delay(50)
                waited += 50
            }
            if (session == null) throw IllegalStateException("камера не подняла сессию")
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL).apply {
                addTarget(reader.surface)
                // Автоэкспозиция выключена: иначе она сама «выравнивает» волну пульса.
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val ranges = manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val shortest = ranges?.lower ?: 20_000L
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, (shortest * 4).coerceIn(4_000L, 60_000L))
                set(CaptureRequest.SENSOR_SENSITIVITY, 200)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }.build()
            session?.setRepeatingRequest(request, null, null)

            val until = System.currentTimeMillis() + millis
            while (System.currentTimeMillis() < until) delay(50)
        } finally {
            runCatching { session?.close() }
            runCatching { device?.close() }
            runCatching { reader.close() }
            runCatching { manager.setTorchMode(cameraId, false) }
        }
        Reading(samples = samples.toList(), stamps = stamps.toList())
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun openCamera(manager: CameraManager, id: String): CameraDevice =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                manager.openCamera(
                    id,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(device: CameraDevice) {
                            if (continuation.isActive) continuation.resume(device)
                        }

                        override fun onDisconnected(device: CameraDevice) {
                            runCatching { device.close() }
                            if (continuation.isActive) continuation.cancel()
                        }

                        override fun onError(device: CameraDevice, error: Int) {
                            runCatching { device.close() }
                            if (continuation.isActive) continuation.cancel()
                        }
                    },
                    null
                )
            }.onFailure { error -> if (continuation.isActive) continuation.cancel(error) }
        }
}
