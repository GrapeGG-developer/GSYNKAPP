package com.gsynk.app.core

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.launch
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Быстрые действия мультитула: фонарик, «Не беспокоить», сирена, вибрация.
 * Все методы безопасны: при отсутствии доступа просто возвращают ошибку, не падая.
 */
object AutomationActions {

    // ————— фонарик —————

    fun setTorch(context: Context, on: Boolean): Boolean = runCatching {
        val cm = context.getSystemService(CameraManager::class.java) ?: return false
        val id = cm.cameraIdList.firstOrNull { cameraId ->
            cm.getCameraCharacteristics(cameraId)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        cm.setTorchMode(id, on)
        true
    }.getOrDefault(false)

    // ————— «Не беспокоить» —————

    fun dndGranted(context: Context): Boolean = RegionSupport.dndAccess(context)

    fun setDnd(context: Context, on: Boolean): Boolean {
        if (!dndGranted(context)) return false
        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.setInterruptionFilter(
                if (on) {
                    android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                }
            )
            true
        }.getOrDefault(false)
    }

    fun isDndOn(context: Context): Boolean = runCatching {
        if (!dndGranted(context)) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrDefault(false)

    fun openDndSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ————— звук и вибрация —————

    private var tone: ToneGenerator? = null

    /** Сирена «найди телефон»: громкий сигнал, пока не остановят. */
    fun startSiren(): Boolean = runCatching {
        stopSiren()
        val generator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 20 * 60 * 1000)
        tone = generator
        true
    }.getOrDefault(false)

    /** Крутится ли сирена прямо сейчас. */
    fun isSirenRunning(): Boolean = tone != null

    fun stopSiren() {
        runCatching {
            tone?.stopTone()
            tone?.release()
        }
        tone = null
    }

    fun vibrate(context: Context, ms: Long = 60) {
        // Через Haptics: HyperOS глушит вибрацию без явного назначения, а нам нужно,
        // чтобы «Вибрация» в инструментах и отклик на логотип чувствовались всегда.
        Haptics.play(context, longArrayOf(0, ms.coerceIn(10L, 10_000L)), Haptics.Use.ALARM)
    }

    /** Открыть системный экран по ключу: звук, батарея, приложения, дисплей. */
    fun openSettings(context: Context, key: String) {
        val action = when (key) {
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "data" -> Settings.ACTION_DATA_USAGE_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openAppSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ————— тоны для лаборатории: динамик телефона как катушка —————

    private var sweepJob: kotlinx.coroutines.Job? = null
    private var toneTrack: android.media.AudioTrack? = null
    private val sweepScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob()
    )
    private val scope = sweepScope

    /** Чистый тон на заданной частоте: длительность в миллисекундах. */
    fun playTone(hz: Int, durationMs: Int = 2_000) {
        stopTone()
        runCatching {
            // ToneGenerator умеет только готовые тоны, поэтому для произвольной частоты
            // строим короткую синусоиду и отдаём её динамику через трек.
            val sampleRate = 44_100
            val samples = sampleRate * (durationMs / 1000f).toInt().coerceAtLeast(1)
            val buffer = ShortArray(samples)
            for (index in 0 until samples) {
                val angle = 2.0 * Math.PI * hz * index / sampleRate
                buffer[index] = (kotlin.math.sin(angle) * 12_000).toInt().toShort()
            }
            val track = android.media.AudioTrack.Builder()
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buffer.size * 2)
                .build()
            track.write(buffer, 0, buffer.size)
            track.setVolume(0.6f)
            track.play()
            toneTrack = track
            scope.launch {
                kotlinx.coroutines.delay(durationMs.toLong())
                runCatching { track.stop() }
                runCatching { track.release() }
                if (toneTrack === track) toneTrack = null
            }
        }
    }

    fun stopTone() {
        runCatching { toneTrack?.stop() }
        runCatching { toneTrack?.release() }
        toneTrack = null
    }

    /** Развёртка 20 Гц → 20 кГц: слышно, как динамик проходит всю полосу. */
    fun playSweep() {
        runCatching {
            sweepJob?.cancel()
            stopTone()
            sweepJob = scope.launch {
                val total = 4_000
                val steps = 40
                for (step in 0 until steps) {
                    val hz = (20 * Math.pow(1000.0, step.toDouble() / steps)).toInt().coerceIn(20, 20_000)
                    playTone(hz, total / steps)
                    kotlinx.coroutines.delay((total / steps).toLong())
                }
                stopTone()
            }
        }
    }
}
