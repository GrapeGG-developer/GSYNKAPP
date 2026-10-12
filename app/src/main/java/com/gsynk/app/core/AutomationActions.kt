package com.gsynk.app.core

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import androidx.core.net.toUri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Русский голос для синтеза речи (современный способ вместо конструктора Locale). */
private val RUSSIAN: Locale = Locale.forLanguageTag("ru-RU")

/**
 * Быстрые действия мультитула: фонарик, «Не беспокоить», звонок, синтез речи.
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

    // В Android 16 метод getTorchMode убран из публичного API, поэтому состояние
    // фонарика отслеживает интерфейс через CameraManager.registerTorchCallback.

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

    // ————— звонок —————

    /** Позвонить по номеру (нужно разрешение CALL_PHONE). */
    fun call(context: Context, number: String): Boolean {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.length < 4) return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_CALL, "tel:$clean".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    /** Открыть набор номера (без разрешения CALL_PHONE — пользователь жмёт «вызов» сам). */
    fun dial(context: Context, number: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, "tel:${number.filter { it.isDigit() || it == '+' }}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    // ————— синтез речи —————

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun speak(context: Context, phrase: String, forceSpeaker: Boolean = true) {
        if (phrase.isBlank()) return
        val app = context.applicationContext
        val engine = tts ?: TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = RUSSIAN
            }
        }.also { tts = it }

        if (forceSpeaker) {
            runCatching {
                val am = app.getSystemService(AudioManager::class.java)
                am?.mode = AudioManager.MODE_NORMAL
            }
        }
        // если движок ещё инициализируется — подождём немного и повторим
        if (!ttsReady) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (ttsReady) {
                    engine.language = RUSSIAN
                    engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "gsynk")
                }
            }, 900)
        } else {
            engine.language = RUSSIAN
            engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "gsynk")
        }
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
    }

    fun shutdownTts() {
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }
}
