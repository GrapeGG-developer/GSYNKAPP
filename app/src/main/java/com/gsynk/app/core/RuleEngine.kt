package com.gsynk.app.core

import android.content.ContentResolver
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

/** Применение значений яркости и громкости. */
object RuleEngine {

    data class Applied(
        val brightness: Boolean = false,
        val volume: Boolean = false,
        val silent: Boolean = false
    )

    // ——— чистая математика (покрыта юнит-тестами) ———

    /** Проценты → индекс ступени громкости (Android хранит громкость ступенями). */
    fun percentToVolumeIndex(percent: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        return (percent.coerceIn(0, 100) / 100f * maxIndex).roundToInt().coerceIn(0, maxIndex)
    }

    /** Индекс ступени → проценты (нужно, чтобы понять: значение уже выставлено или нет). */
    fun volumeIndexesToPercent(index: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        return (index * 100f / maxIndex).roundToInt().coerceIn(0, 100)
    }

    /** Проценты → системная яркость 1..255 (1 — чтобы экран не погас полностью). */
    fun percentToBrightnessRaw(percent: Int): Int =
        (percent.coerceIn(0, 100) / 100f * 255f).roundToInt().coerceIn(1, 255)

    // ——— громкость медиа ———

    fun setVolumePercent(context: Context, percent: Int): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = percentToVolumeIndex(percent, max)
        return runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            true
        }.getOrDefault(false)
    }

    fun volumePercent(context: Context): Int {
        val am = context.getSystemService(AudioManager::class.java) ?: return 0
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0
        return (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt().coerceIn(0, 100)
    }

    // ——— яркость экрана ———

    fun isAutoBrightnessOn(cr: ContentResolver): Boolean = runCatching {
        Settings.System.getInt(
            cr,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    /** Автояркость мешает правилам: GSYNK переключает режим на ручной. */
    fun setAutoBrightness(context: Context, enabled: Boolean): Boolean {
        if (!Permissions.canWriteSettings(context)) return false
        return runCatching {
            val mode = if (enabled) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            true
        }.getOrDefault(false)
    }

    fun setBrightnessPercent(context: Context, percent: Int): Boolean {
        if (!Permissions.canWriteSettings(context)) return false
        return runCatching {
            val cr = context.contentResolver
            if (isAutoBrightnessOn(cr)) {
                Settings.System.putInt(
                    cr,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            }
            Settings.System.putInt(
                cr,
                Settings.System.SCREEN_BRIGHTNESS,
                percentToBrightnessRaw(percent)
            )
            true
        }.getOrDefault(false)
    }

    fun brightnessPercent(context: Context): Int = runCatching {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        (raw * 100f / 255f).roundToInt().coerceIn(0, 100)
    }.getOrDefault(0)

    // ——— применение правила целиком ———

    fun apply(context: Context, rule: Rule): Applied {
        var brightnessDone = false
        var volumeDone = false

        if (rule.useVolume) {
            volumeDone = setVolumePercent(context, rule.volume)
        }
        if (rule.useBrightness) {
            brightnessDone = setBrightnessPercent(context, rule.brightness)
        }
        val silentDone = if (rule.silentMode == 0 || rule.silentMode == 1) {
            AutomationActions.setDnd(context, rule.silentMode == 1)
        } else {
            false
        }
        return Applied(brightnessDone, volumeDone, silentDone)
    }
}
