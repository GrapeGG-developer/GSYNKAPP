package com.gsynk.app.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Подключение по NFC прямо у фронтальной камеры.
 *
 * Анимация крупная и понятная: широкая полоса от верхней кромки экрана (там, где
 * камера), рамка-видоискатель, набегающие прямоугольные волны, сканирующая линия,
 * сегментная полоса загрузки и короткая вспышка в конце.
 *
 * Строго прямые линии: ни кругов, ни частиц, ни разноцветных точек — только
 * фиолетовый и белый.
 */
class NfcBurstOverlay(private val context: Context) {

    private var view: View? = null
    private var windowManager: WindowManager? = null

    fun show(durationMs: Long = 3_400L) {
        if (!RegionSupport.overlayAllowed(context)) return
        hide()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val burst = NfcBurstView(context, durationMs)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            screenWidth,
            (context.resources.displayMetrics.heightPixels * 0.40f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
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

    /** Сам рисунок: перерисовывается каждый кадр, пока идёт показ. */
    private class NfcBurstView(context: Context, private val durationMs: Long) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val startedAt = System.currentTimeMillis()
        private var backdrop: LinearGradient? = null

        fun start(onEnd: () -> Unit) {
            postInvalidateOnAnimation()
            postDelayed(onEnd, durationMs)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val elapsed = (System.currentTimeMillis() - startedAt).toFloat()
            val t = (elapsed / durationMs).coerceIn(0f, 1f)
            val unit = w / 40f

            // ————— фазы —————
            // 0.00–0.12 мягкий выезд полосы, 0.12–0.72 сканирование,
            // 0.72–0.82 вспышка, 0.82–1.00 результат
            val reveal = (t / 0.12f).coerceIn(0f, 1f)
            val scanning = t in 0.10f..0.74f
            val flash = if (t in 0.72f..0.84f) 1f - ((t - 0.72f) / 0.12f) else 0f
            val done = t > 0.78f

            // ————— подложка: тёмная прямоугольная полоса от кромки экрана —————
            if (backdrop == null) {
                backdrop = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xF20B0817.toInt(), 0xE60B0817.toInt(), 0x000B0817),
                    floatArrayOf(0f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            paint.style = Paint.Style.FILL
            paint.shader = backdrop
            val shown = h * reveal
            canvas.drawRect(0f, 0f, w, shown, paint)
            paint.shader = null

            // ————— рамка-видоискатель по фронтальной камере —————
            val frameTop = unit * 1.6f
            val frameBottom = h * 0.62f
            val bracket = unit * 2.6f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = unit * 0.28f
            paint.color = Color.argb((180 * reveal).toInt(), 201, 168, 255)
            // уголки, как у камеры: четыре прямые скобки
            listOf(
                floatArrayOf(0f, frameTop, bracket, frameTop),
                floatArrayOf(0f, frameTop, 0f, frameTop + bracket),
                floatArrayOf(w - bracket, frameTop, w, frameTop),
                floatArrayOf(w, frameTop, w, frameTop + bracket),
                floatArrayOf(0f, frameBottom, bracket, frameBottom),
                floatArrayOf(0f, frameBottom - bracket, 0f, frameBottom),
                floatArrayOf(w - bracket, frameBottom, w, frameBottom),
                floatArrayOf(w, frameBottom - bracket, w, frameBottom)
            ).forEach { line ->
                canvas.drawLine(line[0], line[1], line[2], line[3], paint)
            }

            // ————— набегающие прямоугольные волны от центра камеры —————
            if (scanning) {
                val local = ((t - 0.10f) / 0.64f).coerceIn(0f, 1f)
                repeat(4) { index ->
                    val phase = (local * 2.2f + index * 0.25f) % 1f
                    val grow = unit * (2f + phase * 10f)
                    val alpha = ((1f - phase) * 0.5f).coerceIn(0f, 1f)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = unit * 0.2f
                    paint.color = Color.argb((alpha * 255).toInt(), 168, 85, 247)
                    val cx = w / 2f
                    val cy = frameTop + unit * 1.4f
                    canvas.drawRect(cx - grow, cy - grow * 0.55f, cx + grow, cy + grow * 0.55f, paint)
                }
            }

            // ————— сканирующая линия: ходит вниз-вверх по полосе —————
            if (scanning) {
                val sweep = ((t - 0.10f) / 0.64f * 2f) % 1f
                val y = frameTop + (frameBottom - frameTop) * (if (sweep < 0.5f) sweep * 2f else (1f - sweep) * 2f)
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(200, 233, 226, 255)
                canvas.drawRect(unit * 1.2f, y - unit * 0.09f, w - unit * 1.2f, y + unit * 0.09f, paint)
                paint.color = Color.argb(60, 168, 85, 247)
                canvas.drawRect(unit * 1.2f, y + unit * 0.09f, w - unit * 1.2f, y + unit * 0.5f, paint)
            }

            // ————— сегментная полоса загрузки —————
            val segments = 14
            val segWidth = (w - unit * 3f) / segments
            val filled = if (done) segments else (t / 0.72f * segments).toInt().coerceIn(0, segments)
            repeat(segments) { index ->
                val x = unit * 1.5f + index * segWidth
                paint.style = Paint.Style.FILL
                paint.color = if (index < filled) {
                    Color.argb(230, 201, 168, 255)
                } else {
                    Color.argb(70, 255, 255, 255)
                }
                canvas.drawRect(x, h * 0.70f, x + segWidth - unit * 0.5f, h * 0.70f + unit * 0.7f, paint)
            }

            // ————— подписи —————
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            paint.textSize = unit * 2.3f
            paint.color = Color.argb((235 * reveal).toInt(), 243, 240, 255)
            val headline = when {
                done -> "СОЕДИНЕНО"
                scanning -> "ПОДКЛЮЧЕНИЕ"
                else -> "NFC"
            }
            canvas.drawText(headline, w / 2f, h * 0.80f, paint)

            paint.textSize = unit * 1.25f
            paint.color = Color.argb((170 * reveal).toInt(), 156, 143, 181)
            canvas.drawText(
                if (done) "обмен завершён · можно убирать телефон" else "держите телефон у фронтальной камеры",
                w / 2f,
                h * 0.86f,
                paint
            )

            // метка камеры сверху по центру
            paint.textSize = unit * 1.05f
            paint.color = Color.argb((120 * reveal).toInt(), 201, 168, 255)
            canvas.drawText("фронтальная камера", w / 2f, unit * 1.15f, paint)
            paint.textAlign = Paint.Align.LEFT

            // ————— отметка в центре: галочка «готово» —————
            if (done) {
                val cx = w / 2f
                val cy = frameTop + (frameBottom - frameTop) * 0.5f
                val size = unit * 3.2f
                val appear = ((t - 0.78f) / 0.12f).coerceIn(0f, 1f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.55f
                paint.color = Color.argb((255 * appear).toInt(), 233, 226, 255)
                canvas.drawLine(cx - size, cy, cx - size * 0.2f, cy + size * 0.7f, paint)
                canvas.drawLine(cx - size * 0.2f, cy + size * 0.7f, cx + size, cy - size * 0.8f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.22f
                paint.color = Color.argb((200 * appear).toInt(), 201, 168, 255)
                canvas.drawRect(cx - size * 1.6f, cy - size * 1.5f, cx + size * 1.6f, cy + size * 1.5f, paint)
            }

            // ————— вспышка на переходе —————
            if (flash > 0f) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((flash * 150).toInt(), 255, 255, 255)
                canvas.drawRect(0f, 0f, w, h, paint)
            }

            // тонкая бегущая линия по нижней кромке полосы — «работа идёт»
            paint.style = Paint.Style.FILL
            val edge = ((t * 3f) % 1f) * w
            paint.color = Color.argb(200, 74, 222, 128)
            canvas.drawRect(edge, h - unit * 0.5f, (edge + w * 0.25f).coerceAtMost(w), h, paint)

            if (t < 1f) postInvalidateOnAnimation()
        }
    }
}
