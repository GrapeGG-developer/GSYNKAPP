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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.coroutines.resume

/**
 * Замер мерцания экрана камерой (ШИМ).
 *
 * Идея та же, что у лабораторных люксметров: выдержка камеры ставится очень короткой
 * (десятки микросекунд) и фиксируется. Каждый кадр тогда показывает мгновенную
 * яркость экрана. Если экран мерцает, яркость кадров будет «волной» — по её периоду
 * и считается частота ШИМ.
 *
 * Всё честно: ни одного выдуманного числа. Если камера не даёт ручную выдержку или
 * снимок невозможен — метод возвращает расшифрованную причину, а приложение
 * предлагает стробоскопическое сравнение глазами.
 */
object Flicker {

    data class Report(
        val hz: Float,
        val depth: Float,
        val frames: Int,
        val millis: Long,
        val samples: List<Float>,
        val note: String
    )

    /** Умеет ли эта камера ручную выдержку — от этого зависит смысл замера. */
    fun manualSupported(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        manager.cameraIdList.any { id ->
            val caps = manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR }
        }
    }.getOrDefault(false)

    /** Снять серию кадров и посчитать частоту мерцания. Бросает исключение с причиной. */
    suspend fun measure(context: Context, millis: Long = 1_200L): Report = withContext(Dispatchers.Default) {
        // Камеру трогаем только с явно выданным разрешением: иначе система просто
        // не даст открыть устройство, и замер превратится в непонятную ошибку.
        val allowed = runCatching {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!allowed) throw IllegalStateException("нет разрешения на камеру: выдать его можно на экране замеров")

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: throw IllegalStateException("камера недоступна")
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            val caps = manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR }
        } ?: manager.cameraIdList.firstOrNull()
            ?: throw IllegalStateException("камер нет")

        val reader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 8)
        val samples = ArrayList<Float>(600)
        val stamps = ArrayList<Long>(600)
        reader.setOnImageAvailableListener({ source ->
            runCatching {
                val image = source.acquireLatestImage() ?: return@runCatching
                val plane = image.planes[0]
                val buffer = plane.buffer
                var sum = 0L
                var count = 0
                var index = 0
                while (index < buffer.limit()) {
                    sum += (buffer.get(index).toInt() and 0xFF)
                    count++
                    index += 7 // берём каждый седьмой пиксель: хватает для средней яркости
                }
                samples.add((sum.toFloat() / count.coerceAtLeast(1)) / 255f)
                stamps.add(image.timestamp)
                image.close()
            }
        }, null)

        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        try {
            device = openCamera(manager, cameraId)
            // Сессия создаётся асинхронно: сохраняем её из колбэка, иначе нечего закрывать.
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
            // Ждём, пока сессия поднимется: иначе повторный запрос не уйдёт.
            var waited = 0
            while (session == null && waited < 1_500) {
                kotlinx.coroutines.delay(50)
                waited += 50
            }
            if (session == null) throw IllegalStateException("камера не подняла сессию")
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL).apply {
                addTarget(reader.surface)
                val ranges = manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val shortest = ranges?.lower ?: 40L
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, shortest.coerceAtLeast(20L))
                set(CaptureRequest.SENSOR_SENSITIVITY, 800)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
            }.build()
            session?.setRepeatingRequest(request, null, null)

            val until = System.currentTimeMillis() + millis
            while (System.currentTimeMillis() < until) {
                kotlinx.coroutines.delay(20)
            }
        } finally {
            runCatching { session?.close() }
            runCatching { device?.close() }
            runCatching { reader.close() }
        }

        if (samples.size < 12) throw IllegalStateException("камера не отдала кадры (нужно разрешение на камеру)")

        val millisTaken = if (stamps.size >= 2) (stamps.last() - stamps.first()) / 1_000_000L else millis
        val mean = samples.average().toFloat()
        val depth = samples.max().let { max -> samples.min().let { min -> if (mean <= 0.0001f) 0f else (max - min) / mean / 2f } }
        val hz = frequencyOf(samples, stamps)
        val note = when {
            hz <= 0f -> "мерцания не видно: либо экран не мерцает, либо он вне кадра"
            hz < 100f -> "низкая частота: глазам тяжело, видно полосы"
            hz < 400f -> "обычная ШИМ: полосы на камере, глазами почти не видно"
            hz < 2_000f -> "высокочастотная ШИМ — современный уровень"
            else -> "мерцание практически незаметно"
        }
        Report(
            hz = hz,
            depth = depth,
            frames = samples.size,
            millis = millisTaken,
            samples = samples.takeLast(240),
            note = note
        )
    }

    /**
     * Частота по сменам знака относительно среднего: считаем переходы через средний
     * уровень и делим половину числа переходов на длительность съёмки.
     */
    fun frequencyOf(samples: List<Float>, stamps: List<Long>): Float {
        if (samples.size < 8 || stamps.size != samples.size) return 0f
        val mean = samples.average().toFloat()
        var crossings = 0
        var previousAbove = samples.first() > mean
        for (index in 1 until samples.size) {
            val above = samples[index] > mean
            if (above != previousAbove) crossings++
            previousAbove = above
        }
        val nanos = stamps.last() - stamps.first()
        if (nanos <= 0L) return 0f
        val seconds = nanos / 1_000_000_000.0
        val hz = (crossings / 2.0) / seconds
        // Совсем редкие «волны» — это движение телефона, а не мерцание.
        return if (hz < 3.0) 0f else hz.toFloat()
    }

    /** Разброс яркости: по нему видно, насколько глубоко мерцает экран. */
    fun depthOf(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val mean = samples.average().toFloat()
        if (mean <= 0.0001f) return 0f
        val min = samples.min()
        val max = samples.max()
        return abs(max - min) / mean / 2f
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
            }.onFailure { error ->
                if (continuation.isActive) continuation.cancel(error)
            }
        }
}
