package com.gsynk.app.core

import android.content.Context
import android.media.AudioManager

/**
 * Быстрые системные действия для бота и лаборатории: яркость и громкость.
 * Всё идёт через те же проверки, что и правила расписания, — без своих путей.
 */
object SystemTools {

    fun setBrightness(context: Context, percent: Int): Boolean =
        RuleEngine.setBrightnessPercent(context, percent)

    fun setVolume(context: Context, percent: Int): Boolean =
        RuleEngine.setVolumePercent(context, percent)

    fun brightness(context: Context): Int = RuleEngine.brightnessPercent(context)

    fun volume(context: Context): Int = RuleEngine.volumePercent(context)

    /** Шаг яркости на 10 процентов вверх или вниз. */
    fun brightnessStep(context: Context, direction: Int): Boolean =
        setBrightness(context, (brightness(context) + direction * 10).coerceIn(0, 100))

    /** Шаг громкости на одну ступень — как кнопкой на корпусе. */
    fun volumeStep(context: Context, direction: Int): Boolean = runCatching {
        val manager = context.getSystemService(AudioManager::class.java) ?: return false
        if (manager.isVolumeFixed) return false
        manager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0
        )
        true
    }.getOrDefault(false)

    /** Режимы звука: обычный, вибрация, полная тишина. */
    fun setRinger(context: Context, mode: Int): Boolean = runCatching {
        val manager = context.getSystemService(AudioManager::class.java) ?: return false
        manager.ringerMode = mode
        true
    }.getOrDefault(false)
}
