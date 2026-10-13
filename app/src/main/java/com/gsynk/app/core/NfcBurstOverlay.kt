package com.gsynk.app.core

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Вспышка NFC-подключения прямо у фронтальной камеры.
 *
 * Анимация тяжёлая и намеренно «дорогая»: светящееся ядро, три расходящиеся волны,
 * кольца, 140 частиц с затуханием, лучи и угольки. Живёт в оверлее поверх всех
 * приложений, поэтому её видно и вне GSYNK.
 */
class NfcBurstOverlay(private val context: Context) {

    private var view: View? = null
    private var windowManager: WindowManager? = null

    fun show(durationMs: Long = 3_200L) {
        if (!RegionSupport.overlayAllowed(context)) return
        hide()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val burst = NfcBurstView(context, durationMs)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            y = statusBarHeight() + dp(4)
        }
        runCatching { wm.addView(burst, params) }
        view = burst
        windowManager = wm
        burst.start { hide() }
    }

    fun hide() {
        val current = view ?: return
        runCatching { windowManager?.removeView(current) }
        view = null
        windowManager = null
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Сам рисунок: рисуется на Canvas каждый кадр, пока идёт анимация. */
    private class NfcBurstView(context: Context, private val durationMs: Long) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val startedAt = System.currentTimeMillis()
        private val particles = List(140) {
            Particle(
                angle = Random.nextFloat() * 6.283f,
                speed = 0.35f + Random.nextFloat() * 1.35f,
                radius = 1.6f + Random.nextFloat() * 3.6f,
                hue = listOf(268f, 150f, 200f, 320f).random() + Random.nextFloat() * 24f,
                delay = Random.nextFloat() * 0.35f
            )
        }

        private data class Particle(
            val angle: Float,
            val speed: Float,
            val radius: Float,
            val hue: Float,
            val delay: Float
        )

        fun start(onEnd: () -> Unit) {
            postInvalidateOnAnimation()
            postDelayed(onEnd, durationMs)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val elapsed = (System.currentTimeMillis() - startedAt).toFloat()
            val t = (elapsed / durationMs).coerceIn(0f, 1f)
            val fade = when {
                t < 0.12f -> t / 0.12f
                t > 0.75f -> (1f - t) / 0.25f
                else -> 1f
            }.coerceIn(0f, 1f)

            val cx = width / 2f
            val cy = height * 0.42f
            val unit = width / 26f

            // 1) светящееся ядро
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                cx, cy, unit * 5.2f,
                intArrayOf(
                    argb(0.85f * fade, 255, 255, 255),
                    argb(0.45f * fade, 160, 120, 255),
                    argb(0f, 120, 80, 255)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, unit * 5.2f, paint)
            paint.shader = null

            // 2) расходящиеся волны
            repeat(3) { index ->
                val phase = (t * 1.6f + index * 0.33f) % 1f
                val radius = unit * (1.2f + phase * 6.5f)
                val alpha = ((1f - phase) * 0.55f * fade).coerceIn(0f, 1f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.16f * (1f - phase * 0.5f).coerceAtLeast(0.25f)
                paint.color = argb(alpha, 190, 140, 255)
                canvas.drawCircle(cx, cy, radius, paint)
            }

            // 3) вращающиеся дуги
            repeat(3) { index ->
                val sweep = unit * (3.4f + index * 0.9f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.10f
                paint.color = argb(0.5f * fade, 120, 255, 190)
                val rotation = t * 360f * (if (index % 2 == 0) 1f else -1f) + index * 90f
                canvas.save()
                canvas.rotate(rotation, cx, cy)
                canvas.drawArc(cx - sweep, cy - sweep, cx + sweep, cy + sweep, 0f, 70f, false, paint)
                canvas.drawArc(cx - sweep, cy - sweep, cx + sweep, cy + sweep, 180f, 70f, false, paint)
                canvas.restore()
            }

            // 4) частицы
            paint.style = Paint.Style.FILL
            particles.forEach { particle ->
                val local = ((t - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val distance = unit * (1f + local * 9f * particle.speed)
                val x = cx + cos(particle.angle) * distance
                val y = cy + sin(particle.angle) * distance * 0.72f
                val alpha = ((1f - local) * 0.9f * fade).coerceIn(0f, 1f)
                paint.color = hsl(particle.hue, 0.9f, 0.7f, alpha)
                canvas.drawCircle(x, y, particle.radius * (1f - local * 0.55f) * unit * 0.16f, paint)
            }

            // 5) лучи
            repeat(12) { index ->
                val angle = index * (6.283f / 12f) + t * 1.2f
                val inner = unit * 1.4f
                val outer = unit * (2.6f + 2.2f * ((index % 3) / 2f) + t * 2f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.05f
                paint.color = argb(0.35f * fade * (1f - t), 255, 220, 255)
                canvas.drawLine(
                    cx + cos(angle) * inner, cy + sin(angle) * inner,
                    cx + cos(angle) * outer, cy + sin(angle) * outer,
                    paint
                )
            }

            if (t < 1f) postInvalidateOnAnimation()
        }

        private fun argb(alpha: Float, r: Int, g: Int, b: Int): Int =
            Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), r, g, b)

        /** Быстрый перевод HSL → ARGB, чтобы частицы были разноцветными. */
        private fun hsl(hue: Float, saturation: Float, lightness: Float, alpha: Float): Int {
            val c = (1f - kotlin.math.abs(2 * lightness - 1f)) * saturation
            val h = (hue % 360f + 360f) % 360f / 60f
            val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
            val (r1, g1, b1) = when (h.toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            val m = lightness - c / 2f
            return Color.argb(
                (alpha.coerceIn(0f, 1f) * 255).toInt(),
                ((r1 + m) * 255).toInt().coerceIn(0, 255),
                ((g1 + m) * 255).toInt().coerceIn(0, 255),
                ((b1 + m) * 255).toInt().coerceIn(0, 255)
            )
        }
    }
}
