package com.gsynk.app.core

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.abs

/**
 * Вибрация с запасными дорожками.
 *
 * На Poco X8 Pro Max (HyperOS, Android 16) вибро-шаблоны молчали: система глушит
 * вибрацию с «неизвестным» назначением, если в настройках выключена тактильная
 * отдача. Поэтому играем с явным назначением USAGE_ALARM (это разрешено всегда),
 * а если и оно не сработало — откатываемся на самый простой вызов.
 *
 * Все расчёты (амплитуды, укорачивание шаблона) вынесены в чистые функции — их
 * проверяют тесты, ведь сам мотор в песочнице не потрогать.
 */
object Haptics {

    /** Зачем вибрируем: назначение решает, не заглушит ли система вибрацию. */
    enum class Use(val label: String, val usage: Int) {
        TOUCH("отклик", VibrationAttributes.USAGE_TOUCH),
        ALARM("сигнал", VibrationAttributes.USAGE_ALARM),
        NOTIFICATION("уведомление", VibrationAttributes.USAGE_NOTIFICATION),
        MEDIA("медиа", VibrationAttributes.USAGE_MEDIA)
    }

    fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /** Есть ли вообще мотор и разрешение. */
    fun available(context: Context): Boolean = runCatching {
        val v = vibrator(context)
        v != null && v.hasVibrator()
    }.getOrDefault(false)

    /** Амплитуда с учётом того, умеет ли мотор её менять. */
    fun amplitude(percent: Int, hasControl: Boolean): Int {
        if (!hasControl) return VibrationEffect.DEFAULT_AMPLITUDE
        val scaled = (255.0 * percent.coerceIn(0, 100) / 100.0).toInt()
        // Ноль всё равно должен ощущаться: полностью выключенная вибрация выглядит как поломка.
        return scaled.coerceIn(1, 255)
    }

    /** Укорачивает шаблон до [limitMs], чтобы длинные последовательности не висели. */
    fun fit(pattern: LongArray, limitMs: Long): LongArray {
        if (pattern.isEmpty()) return longArrayOf(0, 1)
        var total = 0L
        val out = ArrayList<Long>(pattern.size)
        for ((index, value) in pattern.withIndex()) {
            total += value
            if (total > limitMs && index > 1) break
            out += value
        }
        return out.toLongArray()
    }

    /** Сколько миллисекунд займёт шаблон (без повторов). */
    fun duration(pattern: LongArray): Long = pattern.fold(0L) { acc, value -> acc + abs(value) }

    /**
     * Играет шаблон. [pattern] — как в VibrationEffect: пауза, вибрация, пауза, …
     * Возвращает true, если хоть одна дорожка сработала.
     */
    fun play(
        context: Context,
        pattern: LongArray,
        use: Use = Use.ALARM,
        repeat: Int = -1,
        percent: Int = 100
    ): Boolean {
        val vibrator = vibrator(context) ?: return false
        if (!runCatching { vibrator.hasVibrator() }.getOrDefault(false)) return false

        val effect = effect(vibrator, pattern, repeat, percent)
        if (effect != null) {
            val attributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { VibrationAttributes.createForUsage(use.usage) }.getOrNull()
            } else {
                null
            }
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && attributes != null) {
                runCatching { vibrator.vibrate(effect, attributes) }.isSuccess
            } else {
                runCatching { vibrator.vibrate(effect) }.isSuccess
            }
            if (ok) return true
        }

        // Последняя дорожка: старый API без эффектов — работает даже на капризных прошивках.
        return runCatching {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, repeat)
        }.isSuccess
    }

    /** Короткий отклик на касание. */
    fun tick(context: Context, ms: Long = 24L, use: Use = Use.TOUCH): Boolean =
        play(context, longArrayOf(0, ms), use)

    private fun effect(vibrator: Vibrator, pattern: LongArray, repeat: Int, percent: Int): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            val hasControl = vibrator.hasAmplitudeControl()
            val amplitudes = IntArray(pattern.size) { index ->
                if (index == 0) 0 else amplitude(percent, hasControl)
            }
            if (pattern.size == 2) {
                VibrationEffect.createWaveform(pattern, amplitudes, repeat)
            } else {
                VibrationEffect.createWaveform(pattern, amplitudes, repeat)
            }
        }.getOrNull()
    }
}
