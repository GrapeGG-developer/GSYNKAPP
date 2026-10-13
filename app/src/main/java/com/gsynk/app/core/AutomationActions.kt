package com.gsynk.app.core

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
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
        runCatching {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
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
}
